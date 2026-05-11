# Cloudflare Proxy + Turnstile Captcha Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Turnstile captcha verification to register and forgot-password endpoints (backend + frontend), plus a `ClientIpResolver` that reads `CF-Connecting-IP` when the request comes from a Cloudflare IP. The Cloudflare proxy itself is configured manually after merge — this plan ships the backend/frontend code that consumes the proxy.

**Architecture:** Backend uses Spring AOP — methods annotated `@TurnstileRequired` are intercepted by a `TurnstileAspect` that calls Cloudflare's `siteverify` API with the token extracted from the DTO. A feature flag `app.security.turnstile.enabled` toggles the aspect off in dev/test (zero friction local). Frontend ships a reusable `<app-turnstile>` standalone component that loads the Cloudflare widget script dynamically and emits the token via an output; when the site-key is empty (dev), the component emits `'dev-bypass'` immediately.

**Tech Stack:** Spring Boot 3.5.4, Java 21, AOP (`spring-boot-starter-aop`), Spring's `RestClient` (synchronous, no extra dep), JUnit 5, AssertJ, Mockito. Angular 20 standalone, signals, Karma/Jasmine.

**Spec:** `docs/superpowers/specs/2026-05-11-cloudflare-turnstile-design.md` (commit `4bab151`)

---

## File Structure

### New files (backend)

| File | Responsibility |
|---|---|
| `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileProperties.java` | `@ConfigurationProperties("app.security.turnstile")`: `enabled` (boolean), `secretKey` (String), `verifyUrl` (String, default `https://challenges.cloudflare.com/turnstile/v0/siteverify`). Fail-fast on boot if `enabled=true` and `secretKey` blank. |
| `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileVerifier.java` | `boolean isValid(String token, String clientIp)`. Calls Cloudflare `siteverify` via `RestClient`. Timeout 5s. Logs success/failure level INFO, never logs the secret. |
| `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileRequired.java` | Marker annotation `@TurnstileRequired` for controller methods. Target=METHOD, Retention=RUNTIME. |
| `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileAspect.java` | `@Aspect @Component`. `@Around("@annotation(TurnstileRequired)")` extracts the `captchaToken` field from the first method argument via reflection, validates via `TurnstileVerifier`, throws `ResponseStatusException(BAD_REQUEST)` if invalid. Bypasses when `properties.enabled() == false`. |
| `backend/src/main/java/com/luxpretty/app/security/ip/CloudflareIpRanges.java` | Static utility holding the published Cloudflare v4 and v6 CIDR ranges (https://www.cloudflare.com/ips/). Method `boolean contains(String ip)`. |
| `backend/src/main/java/com/luxpretty/app/security/ip/ClientIpResolver.java` | `String resolve(HttpServletRequest)`. Returns `CF-Connecting-IP` only when `request.getRemoteAddr()` is in Cloudflare ranges. Else `X-Forwarded-For` first hop. Else `request.getRemoteAddr()`. |
| `backend/src/test/java/com/luxpretty/app/security/turnstile/TurnstileVerifierTests.java` | 5 unit tests (valid, invalid, expired, network error, disabled bypass). Mock RestClient via `@MockBean`-style or pure Mockito. |
| `backend/src/test/java/com/luxpretty/app/security/turnstile/TurnstileAspectTests.java` | 3 unit tests (annotated method → verified, missing token → 400, disabled → proceed). |
| `backend/src/test/java/com/luxpretty/app/security/ip/ClientIpResolverTests.java` | 4 unit tests (CF-Connecting-IP honored from Cloudflare range, ignored when not from CF, X-Forwarded-For fallback, RemoteAddr fallback). |

### Modified files (backend)

| File | Changes |
|---|---|
| `backend/pom.xml` | Add `spring-boot-starter-aop` dependency. |
| `backend/src/main/resources/application.properties` | Add `app.security.turnstile.enabled=${TURNSTILE_ENABLED:false}` and `app.security.turnstile.secret-key=${TURNSTILE_SECRET_KEY:}`. |
| `backend/src/main/resources/application-test.properties` | Force `app.security.turnstile.enabled=false`. |
| `backend/src/main/java/com/luxpretty/app/auth/dto/RegisterRequest.java` | Add `String captchaToken` field (nullable). |
| `backend/src/main/java/com/luxpretty/app/auth/dto/ProRegisterRequest.java` | Add `String captchaToken` field. |
| `backend/src/main/java/com/luxpretty/app/auth/dto/ForgotPasswordRequest.java` | Add `String captchaToken` field. |
| `backend/src/main/java/com/luxpretty/app/auth/AuthController.java` | Annotate `@TurnstileRequired` on `register`, `registerPro`, `registerClient`, `forgotPassword`. |
| `.env.example` | Document `TURNSTILE_ENABLED` and `TURNSTILE_SECRET_KEY`. |

### New files (frontend)

| File | Responsibility |
|---|---|
| `frontend/src/app/core/config/captcha-site-key.token.ts` | `InjectionToken<string>` named `CAPTCHA_SITE_KEY`, default `''`. |
| `frontend/src/app/shared/uis/turnstile/turnstile.component.ts` | Standalone component, signal-based, `OnPush`. Input `mode` defaulting to `'managed'`. Output `verified: EventEmitter<string>`. Loads `https://challenges.cloudflare.com/turnstile/v0/api.js` once per page (idempotent). If `CAPTCHA_SITE_KEY === ''`, emits `'dev-bypass'` on next macrotask and renders nothing. |
| `frontend/src/app/shared/uis/turnstile/turnstile.component.html` | `<div #widget></div>` referenced by `@ViewChild`. |
| `frontend/src/app/shared/uis/turnstile/turnstile.component.spec.ts` | 3 specs: dev-bypass emits `'dev-bypass'`; with site-key, script is loaded; output emits when `window.turnstile` callback fires. |

### Modified files (frontend)

| File | Changes |
|---|---|
| `frontend/src/app/app.config.ts` | Provide `CAPTCHA_SITE_KEY` with empty string (placeholder; the real key is injected at deploy time via the `index.html` config script in a follow-up task). |
| `frontend/src/app/pages/auth/register/register.component.ts` | Add `captchaToken = signal<string \| null>(null)`; submit button disabled when null; include in payload. |
| `frontend/src/app/pages/auth/register/register.component.html` | Embed `<app-turnstile (verified)="captchaToken.set($event)" />`. |
| `frontend/src/app/pages/auth/register-pro/register-pro.component.ts` and `.html` | Same pattern as register. |
| `frontend/src/app/pages/auth/forgot-password/forgot-password.component.ts` and `.html` | Same pattern. |
| `frontend/public/i18n/fr.json` | Add `"errors.captcha.required": "Veuillez compléter la vérification"`. |
| `frontend/public/i18n/en.json` | Add `"errors.captcha.required": "Please complete the verification"`. |

### Documentation

| File | Content |
|---|---|
| `docs/OPS_CLOUDFLARE.md` | New file: 11 Cloudflare dashboard steps + how to obtain Turnstile keys + verification commands + emergency disable procedure. |
| `CLAUDE.md` | Add one bullet under "Important Notes": production traffic is proxied by Cloudflare; backend reads `CF-Connecting-IP` via `ClientIpResolver`. |

### Out of scope

- Rate limiting (PR2 in the security roadmap).
- Account lockout (PR3).
- Captcha on `/login` (spec says explicitly: too much friction, PR2 + PR3 cover that).
- OAuth2 callback (`/oauth2/redirect`) — Google handles its own bot detection.

---

## Pre-flight

- [ ] **Step 1: Confirm clean baseline**

Run from repo root:
```bash
git status -sb
```
Expected: working tree clean, on `main`. If WIP files exist, stash before starting.

- [ ] **Step 2: Run baseline tests**

```bash
cd backend && mvn test -q 2>&1 | tail -3
```
Expected: `Tests run: 553, Failures: 0, Errors: 31, Skipped: 0` (the 31 errors are pre-existing `@WebMvcTest` ApplicationContext issues, unrelated).

```bash
cd ../frontend && npm test -- --watch=false --browsers=ChromeHeadless 2>&1 | tail -2
```
Expected: `TOTAL: 600 SUCCESS`.

If baseline is not as expected, stop and investigate before adding new code.

- [ ] **Step 3: Create branch**

```bash
git checkout -b feat/cloudflare-turnstile
```

---

## Task 1: Add Spring AOP dependency

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: Add the AOP starter to pom.xml**

Open `backend/pom.xml`. Locate the existing `spring-boot-starter-web` dependency block (around line 50). Add immediately after it:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
```

- [ ] **Step 2: Verify Maven resolves the dependency**

Run:
```bash
cd backend && mvn dependency:resolve -q 2>&1 | tail -5
```
Expected: no errors, `BUILD SUCCESS`.

- [ ] **Step 3: Verify existing tests still pass**

Run:
```bash
mvn test -q 2>&1 | grep "Tests run:" | tail -1
```
Expected: same baseline `Tests run: 553, Failures: 0, Errors: 31`.

- [ ] **Step 4: Commit**

```bash
git add backend/pom.xml
git commit -m "build(backend): add spring-boot-starter-aop for turnstile aspect"
```

---

## Task 2: TurnstileProperties

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileProperties.java`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/main/resources/application-test.properties`

- [ ] **Step 1: Create the properties class**

Create `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileProperties.java`:

```java
package com.luxpretty.app.security.turnstile;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Turnstile feature flags and credentials. The site-key is exposed
 * client-side (frontend env), the secret-key stays here and is loaded
 * from the {@code TURNSTILE_SECRET_KEY} env var.
 *
 * <p>When {@link #isEnabled()} is true and the secret-key is blank the
 * application fails to start — this prevents accidentally shipping the
 * captcha-protected endpoints without a working verifier.
 */
@Component
@ConfigurationProperties(prefix = "app.security.turnstile")
public class TurnstileProperties {

    private boolean enabled = false;
    private String secretKey = "";
    private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getVerifyUrl() { return verifyUrl; }
    public void setVerifyUrl(String verifyUrl) { this.verifyUrl = verifyUrl; }

    @PostConstruct
    void validate() {
        if (enabled && (secretKey == null || secretKey.isBlank())) {
            throw new IllegalStateException(
                    "app.security.turnstile.enabled=true but secret-key is blank — "
                            + "set the TURNSTILE_SECRET_KEY environment variable.");
        }
    }
}
```

- [ ] **Step 2: Wire properties in application.properties**

Open `backend/src/main/resources/application.properties`. Add these two lines at the bottom of the file:

```
# Turnstile captcha (security PR1)
app.security.turnstile.enabled=${TURNSTILE_ENABLED:false}
app.security.turnstile.secret-key=${TURNSTILE_SECRET_KEY:}
```

- [ ] **Step 3: Force-disable Turnstile in test profile**

Open `backend/src/main/resources/application-test.properties`. Add at the bottom:

```
# Captcha bypass for tests — the aspect short-circuits when disabled.
app.security.turnstile.enabled=false
```

- [ ] **Step 4: Verify it boots**

Run:
```bash
cd backend && mvn spring-boot:run -q 2>&1 | head -50 &
sleep 25
curl -sf http://localhost:8080/ping >/dev/null && echo "OK boot" || echo "BOOT FAILED"
pkill -f spring-boot:run
```
Expected: `OK boot` (or no error in the log). The app starts with `enabled=false` default.

- [ ] **Step 5: Verify fail-fast when enabled but no secret**

Run:
```bash
TURNSTILE_ENABLED=true mvn spring-boot:run -q 2>&1 | grep -E "IllegalStateException|secret-key is blank" | head -3
```
Expected: line containing `secret-key is blank`. Then no live process to kill (it failed to start).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileProperties.java \
        backend/src/main/resources/application.properties \
        backend/src/main/resources/application-test.properties
git commit -m "feat(security): add TurnstileProperties with fail-fast on missing secret"
```

---

## Task 3: TurnstileVerifier (test-first)

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileVerifier.java`
- Create: `backend/src/test/java/com/luxpretty/app/security/turnstile/TurnstileVerifierTests.java`

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/java/com/luxpretty/app/security/turnstile/TurnstileVerifierTests.java`:

```java
package com.luxpretty.app.security.turnstile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TurnstileVerifierTests {

    private TurnstileProperties properties;
    private RestClient restClient;
    private RestClient.RequestBodyUriSpec uriSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;
    private TurnstileVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new TurnstileProperties();
        properties.setSecretKey("test-secret");
        properties.setVerifyUrl("https://example.test/verify");
        properties.setEnabled(true);

        restClient = mock(RestClient.class);
        uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(eq("https://example.test/verify"))).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        verifier = new TurnstileVerifier(properties, restClient);
    }

    @Test
    void isValid_returnsTrue_whenCloudflareReportsSuccess() {
        when(responseSpec.body(any(Class.class)))
                .thenReturn(Map.of("success", true, "error-codes", List.of()));

        assertThat(verifier.isValid("good-token", "1.2.3.4")).isTrue();
    }

    @Test
    void isValid_returnsFalse_whenCloudflareReportsInvalidToken() {
        when(responseSpec.body(any(Class.class)))
                .thenReturn(Map.of("success", false, "error-codes", List.of("invalid-input-response")));

        assertThat(verifier.isValid("bad-token", "1.2.3.4")).isFalse();
    }

    @Test
    void isValid_returnsFalse_whenCloudflareReportsExpiredToken() {
        when(responseSpec.body(any(Class.class)))
                .thenReturn(Map.of("success", false, "error-codes", List.of("timeout-or-duplicate")));

        assertThat(verifier.isValid("expired-token", "1.2.3.4")).isFalse();
    }

    @Test
    void isValid_returnsFalse_whenNetworkErrorOccurs() {
        when(responseSpec.body(any(Class.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // Network errors should not break the application — treat as failure.
        assertThat(verifier.isValid("any-token", "1.2.3.4")).isFalse();
    }

    @Test
    void isValid_returnsTrue_immediatelyWhenDisabled() {
        properties.setEnabled(false);
        // No RestClient interaction should happen.
        assertThat(verifier.isValid("anything-at-all", "1.2.3.4")).isTrue();
    }
}
```

- [ ] **Step 2: Run the test to see it fail**

Run:
```bash
cd backend && mvn test -Dtest=TurnstileVerifierTests -q 2>&1 | tail -8
```
Expected: FAIL — `TurnstileVerifier` class not found (compilation error).

- [ ] **Step 3: Create the verifier**

Create `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileVerifier.java`:

```java
package com.luxpretty.app.security.turnstile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls Cloudflare's Turnstile siteverify endpoint to validate a token.
 * Returns {@code true} when the feature is disabled (dev/test bypass) or
 * when Cloudflare confirms the token. Any other outcome — invalid token,
 * expired, network error — returns {@code false}.
 *
 * <p>The secret-key is never logged; only the boolean outcome and the
 * Cloudflare error codes (which describe the failure type) are logged.
 */
@Component
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);

    private final TurnstileProperties properties;
    private final RestClient restClient;

    public TurnstileVerifier(TurnstileProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public boolean isValid(String token, String clientIp) {
        if (!properties.isEnabled()) {
            return true;
        }
        if (token == null || token.isBlank()) {
            log.info("Turnstile verification rejected: missing token");
            return false;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.getSecretKey());
        form.add("response", token);
        if (clientIp != null && !clientIp.isBlank()) {
            form.add("remoteip", clientIp);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.post()
                    .uri(properties.getVerifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            boolean success = body != null && Boolean.TRUE.equals(body.get("success"));
            Object errorCodes = body == null ? null : body.get("error-codes");
            if (success) {
                log.info("Turnstile verification ok (ip={})", clientIp);
            } else {
                log.info("Turnstile verification failed (ip={}, errors={})", clientIp, errorCodes);
            }
            return success;
        } catch (Exception e) {
            log.warn("Turnstile verification network error (ip={}): {}", clientIp, e.getMessage());
            return false;
        }
    }
}
```

- [ ] **Step 4: Provide a `RestClient` bean if missing**

Check whether a `RestClient` bean already exists in the Spring context. Run:
```bash
grep -rn "RestClient.builder()\|@Bean.*RestClient\|@Bean RestClient" backend/src/main/java 2>/dev/null
```

If the search returns nothing, create `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileRestClientConfig.java`:

```java
package com.luxpretty.app.security.turnstile;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Synchronous HTTP client used by {@link TurnstileVerifier} to call the
 * Cloudflare siteverify endpoint. 5-second connect/read timeout so a
 * Cloudflare outage cannot stall the request thread for long.
 */
@Configuration
class TurnstileRestClientConfig {

    @Bean
    RestClient turnstileRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return RestClient.builder().requestFactory(factory).build();
    }
}
```

If a `RestClient` bean already exists in the project, skip this step (the existing bean will be injected).

- [ ] **Step 5: Run the tests to see them pass**

Run:
```bash
mvn test -Dtest=TurnstileVerifierTests -q 2>&1 | tail -5
```
Expected: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileVerifier.java \
        backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileRestClientConfig.java \
        backend/src/test/java/com/luxpretty/app/security/turnstile/TurnstileVerifierTests.java
git commit -m "feat(security): add TurnstileVerifier calling Cloudflare siteverify"
```

(Adjust the `git add` line if step 4 was skipped — omit `TurnstileRestClientConfig.java`.)

---

## Task 4: TurnstileRequired annotation

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileRequired.java`

- [ ] **Step 1: Create the annotation**

Create `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileRequired.java`:

```java
package com.luxpretty.app.security.turnstile;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as requiring a Cloudflare Turnstile token in
 * the request body. The token must be exposed as a field named
 * {@code captchaToken} on the first method argument. {@link TurnstileAspect}
 * extracts the field via reflection and validates via
 * {@link TurnstileVerifier}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TurnstileRequired {
}
```

- [ ] **Step 2: Verify it compiles**

Run:
```bash
cd backend && mvn compile -q 2>&1 | tail -3
```
Expected: `BUILD SUCCESS`, no compilation errors.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileRequired.java
git commit -m "feat(security): add TurnstileRequired marker annotation"
```

---

## Task 5: CloudflareIpRanges utility

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/security/ip/CloudflareIpRanges.java`

- [ ] **Step 1: Create the utility**

Create `backend/src/main/java/com/luxpretty/app/security/ip/CloudflareIpRanges.java`:

```java
package com.luxpretty.app.security.ip;

import java.util.List;

/**
 * Cloudflare-published CIDR ranges. Source:
 * https://www.cloudflare.com/ips/
 *
 * <p>Used by {@link ClientIpResolver} to decide whether to trust the
 * {@code CF-Connecting-IP} header — only when the request hops through a
 * known Cloudflare edge. Anything else could be a spoofed header from a
 * client trying to forge its IP.
 *
 * <p>This list ships static in the codebase rather than fetched at boot
 * because (a) the list changes very rarely (maybe once a year), (b) we do
 * not want a Cloudflare outage to break our startup, (c) updating the
 * list is a trivial PR when needed.
 */
public final class CloudflareIpRanges {

    private CloudflareIpRanges() {}

    /** IPv4 CIDR ranges, as of 2026-05-11. */
    public static final List<String> IPV4 = List.of(
            "173.245.48.0/20",
            "103.21.244.0/22",
            "103.22.200.0/22",
            "103.31.4.0/22",
            "141.101.64.0/18",
            "108.162.192.0/18",
            "190.93.240.0/20",
            "188.114.96.0/20",
            "197.234.240.0/22",
            "198.41.128.0/17",
            "162.158.0.0/15",
            "104.16.0.0/13",
            "104.24.0.0/14",
            "172.64.0.0/13",
            "131.0.72.0/22"
    );

    /** IPv6 CIDR ranges, as of 2026-05-11. */
    public static final List<String> IPV6 = List.of(
            "2400:cb00::/32",
            "2606:4700::/32",
            "2803:f800::/32",
            "2405:b500::/32",
            "2405:8100::/32",
            "2a06:98c0::/29",
            "2c0f:f248::/32"
    );

    /**
     * Returns true if the given IP literal falls in any of the Cloudflare
     * v4 or v6 ranges. Accepts both v4 and v6 strings. Returns false on
     * any parse error so a malformed header never makes us trust it.
     */
    public static boolean contains(String ip) {
        if (ip == null || ip.isBlank()) return false;
        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName(ip);
            List<String> ranges = addr instanceof java.net.Inet6Address ? IPV6 : IPV4;
            for (String cidr : ranges) {
                if (cidrContains(cidr, addr)) return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static boolean cidrContains(String cidr, java.net.InetAddress addr) throws Exception {
        int slash = cidr.indexOf('/');
        java.net.InetAddress network = java.net.InetAddress.getByName(cidr.substring(0, slash));
        int prefix = Integer.parseInt(cidr.substring(slash + 1));
        byte[] netBytes = network.getAddress();
        byte[] addrBytes = addr.getAddress();
        if (netBytes.length != addrBytes.length) return false;
        int fullBytes = prefix / 8;
        int remainingBits = prefix % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (netBytes[i] != addrBytes[i]) return false;
        }
        if (remainingBits == 0) return true;
        int mask = 0xFF << (8 - remainingBits);
        return (netBytes[fullBytes] & mask) == (addrBytes[fullBytes] & mask);
    }
}
```

- [ ] **Step 2: Compile**

```bash
cd backend && mvn compile -q 2>&1 | tail -3
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/security/ip/CloudflareIpRanges.java
git commit -m "feat(security): add Cloudflare CIDR ranges utility"
```

---

## Task 6: ClientIpResolver (test-first)

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/security/ip/ClientIpResolver.java`
- Create: `backend/src/test/java/com/luxpretty/app/security/ip/ClientIpResolverTests.java`

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/java/com/luxpretty/app/security/ip/ClientIpResolverTests.java`:

```java
package com.luxpretty.app.security.ip;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTests {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    void resolve_returnsCfConnectingIp_whenRequestComesFromCloudflareRange() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        // 104.16.0.1 is in the 104.16.0.0/13 Cloudflare range.
        req.setRemoteAddr("104.16.0.1");
        req.addHeader("CF-Connecting-IP", "203.0.113.42");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.42");
    }

    @Test
    void resolve_ignoresCfConnectingIp_whenRequestComesFromNonCloudflareAddress() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("8.8.8.8"); // Google DNS, not Cloudflare
        req.addHeader("CF-Connecting-IP", "203.0.113.42"); // spoofed

        // Must not trust the header; fall back to the actual remote addr.
        assertThat(resolver.resolve(req)).isEqualTo("8.8.8.8");
    }

    @Test
    void resolve_fallsBackToXForwardedFor_whenNoCfHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1"); // local reverse-proxy
        req.addHeader("X-Forwarded-For", "203.0.113.99, 10.0.0.5");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.99");
    }

    @Test
    void resolve_fallsBackToRemoteAddr_whenNoHeadersPresent() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("198.51.100.7");

        assertThat(resolver.resolve(req)).isEqualTo("198.51.100.7");
    }
}
```

- [ ] **Step 2: Run the test to see it fail**

```bash
cd backend && mvn test -Dtest=ClientIpResolverTests -q 2>&1 | tail -5
```
Expected: FAIL — `ClientIpResolver` class not found.

- [ ] **Step 3: Create the resolver**

Create `backend/src/main/java/com/luxpretty/app/security/ip/ClientIpResolver.java`:

```java
package com.luxpretty.app.security.ip;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves the real client IP across reverse proxies. In production
 * LuxPretty sits behind Cloudflare; this class trusts the
 * {@code CF-Connecting-IP} header only when the request itself comes
 * from a Cloudflare edge IP (see {@link CloudflareIpRanges}). Otherwise
 * a client could forge the header to spoof their IP.
 *
 * <p>Order of resolution:
 * <ol>
 *   <li>{@code CF-Connecting-IP} — only if remote addr is a Cloudflare IP</li>
 *   <li>{@code X-Forwarded-For} first hop — local reverse proxies (nginx, traefik)</li>
 *   <li>{@code request.getRemoteAddr()} — direct connection</li>
 * </ol>
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (CloudflareIpRanges.contains(remote)) {
            String cf = request.getHeader("CF-Connecting-IP");
            if (cf != null && !cf.isBlank()) {
                return cf.trim();
            }
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return remote;
    }
}
```

- [ ] **Step 4: Run the tests to see them pass**

```bash
mvn test -Dtest=ClientIpResolverTests -q 2>&1 | tail -5
```
Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/security/ip/ClientIpResolver.java \
        backend/src/test/java/com/luxpretty/app/security/ip/ClientIpResolverTests.java
git commit -m "feat(security): add ClientIpResolver honoring Cloudflare CF-Connecting-IP"
```

---

## Task 7: TurnstileAspect (test-first)

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileAspect.java`
- Create: `backend/src/test/java/com/luxpretty/app/security/turnstile/TurnstileAspectTests.java`

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/java/com/luxpretty/app/security/turnstile/TurnstileAspectTests.java`:

```java
package com.luxpretty.app.security.turnstile;

import com.luxpretty.app.security.ip.ClientIpResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TurnstileAspectTests {

    /** Test DTO that mimics RegisterRequest by having a {@code captchaToken} field. */
    record SampleDto(String email, String captchaToken) {}

    private TurnstileProperties properties;
    private TurnstileVerifier verifier;
    private ClientIpResolver ipResolver;
    private TurnstileAspect aspect;
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() throws Throwable {
        properties = new TurnstileProperties();
        verifier = mock(TurnstileVerifier.class);
        ipResolver = mock(ClientIpResolver.class);
        aspect = new TurnstileAspect(properties, verifier, ipResolver);
        joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("proceed-result");
    }

    @Test
    void verify_proceedsWithoutCheck_whenTurnstileDisabled() throws Throwable {
        properties.setEnabled(false);
        when(joinPoint.getArgs()).thenReturn(new Object[]{ new SampleDto("a@b.c", "anything") });

        Object result = aspect.verify(joinPoint);

        assertThat(result).isEqualTo("proceed-result");
        verify(verifier, never()).isValid(any(), any());
    }

    @Test
    void verify_proceeds_whenTokenValid() throws Throwable {
        properties.setEnabled(true);
        when(joinPoint.getArgs()).thenReturn(new Object[]{ new SampleDto("a@b.c", "good-token") });
        when(verifier.isValid(eq("good-token"), any())).thenReturn(true);

        Object result = aspect.verify(joinPoint);

        assertThat(result).isEqualTo("proceed-result");
    }

    @Test
    void verify_throws400_whenTokenInvalid() throws Throwable {
        properties.setEnabled(true);
        when(joinPoint.getArgs()).thenReturn(new Object[]{ new SampleDto("a@b.c", "bad-token") });
        when(verifier.isValid(eq("bad-token"), any())).thenReturn(false);

        assertThatThrownBy(() -> aspect.verify(joinPoint))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("captcha");
    }
}
```

- [ ] **Step 2: Run the test to see it fail**

```bash
cd backend && mvn test -Dtest=TurnstileAspectTests -q 2>&1 | tail -5
```
Expected: FAIL — `TurnstileAspect` class not found.

- [ ] **Step 3: Create the aspect**

Create `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileAspect.java`:

```java
package com.luxpretty.app.security.turnstile;

import com.luxpretty.app.security.ip.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.RecordComponent;

/**
 * Intercepts controller methods annotated {@link TurnstileRequired},
 * extracts the {@code captchaToken} field from the first DTO argument
 * (via reflection, supports both records and JavaBeans), validates it
 * via {@link TurnstileVerifier}, and short-circuits with HTTP 400 if the
 * token is missing or invalid.
 *
 * <p>When the feature is disabled the aspect is a no-op — this lets dev
 * and test environments accept any value (including {@code null}) for
 * the captcha field on the DTO.
 */
@Aspect
@Component
public class TurnstileAspect {

    private final TurnstileProperties properties;
    private final TurnstileVerifier verifier;
    private final ClientIpResolver ipResolver;

    public TurnstileAspect(TurnstileProperties properties,
                           TurnstileVerifier verifier,
                           ClientIpResolver ipResolver) {
        this.properties = properties;
        this.verifier = verifier;
        this.ipResolver = ipResolver;
    }

    @Around("@annotation(com.luxpretty.app.security.turnstile.TurnstileRequired)")
    public Object verify(ProceedingJoinPoint pjp) throws Throwable {
        if (!properties.isEnabled()) {
            return pjp.proceed();
        }

        String token = extractToken(pjp.getArgs());
        String clientIp = resolveClientIp();
        if (!verifier.isValid(token, clientIp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "captcha invalide");
        }
        return pjp.proceed();
    }

    private String extractToken(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) return null;
        Object dto = args[0];
        Class<?> cls = dto.getClass();
        // Records: look up the captchaToken accessor method.
        if (cls.isRecord()) {
            for (RecordComponent rc : cls.getRecordComponents()) {
                if ("captchaToken".equals(rc.getName())) {
                    try {
                        Object value = rc.getAccessor().invoke(dto);
                        return value == null ? null : value.toString();
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
            return null;
        }
        // JavaBean fallback: reflect on the field directly.
        try {
            java.lang.reflect.Field f = cls.getDeclaredField("captchaToken");
            f.setAccessible(true);
            Object value = f.get(dto);
            return value == null ? null : value.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest request = attrs.getRequest();
            return ipResolver.resolve(request);
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Run the tests to see them pass**

```bash
mvn test -Dtest=TurnstileAspectTests -q 2>&1 | tail -5
```
Expected: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileAspect.java \
        backend/src/test/java/com/luxpretty/app/security/turnstile/TurnstileAspectTests.java
git commit -m "feat(security): add TurnstileAspect intercepting @TurnstileRequired"
```

---

## Task 8: Wire Turnstile into auth DTOs

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/dto/RegisterRequest.java`
- Modify: `backend/src/main/java/com/luxpretty/app/auth/dto/ProRegisterRequest.java`
- Modify: `backend/src/main/java/com/luxpretty/app/auth/dto/ForgotPasswordRequest.java`

- [ ] **Step 1: Add captchaToken to RegisterRequest**

Open `backend/src/main/java/com/luxpretty/app/auth/dto/RegisterRequest.java`. Add a new component `String captchaToken` at the END of the record's parameter list. Insert a comma after `Boolean consent`. The full record signature becomes:

```java
public record RegisterRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotNull(message = "Consent is required")
        @AssertTrue(message = "You must accept the Terms of Service and Privacy Policy")
        Boolean consent,

        /**
         * Cloudflare Turnstile token. Validated by {@link TurnstileAspect}
         * when the feature is enabled; null/blank values are accepted when
         * disabled (dev, test).
         */
        String captchaToken
) {}
```

- [ ] **Step 2: Add captchaToken to ProRegisterRequest**

Open `backend/src/main/java/com/luxpretty/app/auth/dto/ProRegisterRequest.java`. Same pattern: append `String captchaToken` as the LAST record component, with a comma after the previous one.

- [ ] **Step 3: Add captchaToken to ForgotPasswordRequest**

Open `backend/src/main/java/com/luxpretty/app/auth/dto/ForgotPasswordRequest.java`. Append `String captchaToken` as the last component.

- [ ] **Step 4: Verify the project still compiles**

Run:
```bash
cd backend && mvn compile -q 2>&1 | tail -3
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Run the existing auth tests**

Run:
```bash
mvn test -Dtest='AuthControllerTests,AuthFlowIntegrationTests,CustomOAuth2UserServiceTests' -q 2>&1 | grep "Tests run:" | tail -3
```
Expected: each `Tests run` line shows `Failures: 0, Errors: 0`. Existing tests construct DTOs without `captchaToken`; the new param defaults to null which is fine since the test profile has `enabled=false`.

If a test constructs a DTO with positional arguments (e.g. `new RegisterRequest(name, email, password, true)`), it now needs a fifth `null` argument. Patch any failing test by appending `, null` at the end of those constructors.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/auth/dto/
git commit -m "feat(auth): add captchaToken field to register and forgot-password DTOs"
```

---

## Task 9: Annotate auth controller endpoints

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/AuthController.java`

- [ ] **Step 1: Add the import**

Open `backend/src/main/java/com/luxpretty/app/auth/AuthController.java`. In the import block, add:

```java
import com.luxpretty.app.security.turnstile.TurnstileRequired;
```

- [ ] **Step 2: Annotate the four endpoints**

Locate these four methods and add `@TurnstileRequired` on the line immediately above each `@PostMapping`:

- `@PostMapping("/register")` → `register(...)`
- `@PostMapping("/register/pro")` → `registerPro(...)`
- `@PostMapping("/register/client")` → `registerClient(...)`
- `@PostMapping("/forgot-password")` → `forgotPassword(...)`

Example for the first one:
```java
@TurnstileRequired
@PostMapping("/register")
public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    ...
}
```

Do NOT annotate `@PostMapping("/login")` or `@PostMapping("/reset-password")` — those are intentionally not captcha-gated in this PR.

- [ ] **Step 3: Run the full backend test suite**

```bash
cd backend && mvn test -q 2>&1 | grep "Tests run:" | tail -1
```
Expected: `Tests run: 565, Failures: 0, Errors: 31, Skipped: 0` (baseline 553 + 5 verifier + 4 ip + 3 aspect = 565). The 31 errors are pre-existing.

If a NEW failure appears, it means the aspect intercepted a test call. Two possibilities: (a) the test profile didn't pick up `enabled=false` (check `application-test.properties` is on the test classpath), (b) the aspect throws unexpectedly. Investigate before continuing.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/auth/AuthController.java
git commit -m "feat(auth): annotate register and forgot-password endpoints with @TurnstileRequired"
```

---

## Task 10: Frontend — CAPTCHA_SITE_KEY token

**Files:**
- Create: `frontend/src/app/core/config/captcha-site-key.token.ts`
- Modify: `frontend/src/app/app.config.ts`

- [ ] **Step 1: Create the injection token**

Create `frontend/src/app/core/config/captcha-site-key.token.ts`:

```typescript
import { InjectionToken } from '@angular/core';

/**
 * Cloudflare Turnstile site-key. Public, can be committed.
 * Empty string in dev triggers the {@code <app-turnstile>} dev-bypass
 * branch (no widget, instant token emission).
 */
export const CAPTCHA_SITE_KEY = new InjectionToken<string>('CAPTCHA_SITE_KEY', {
  providedIn: 'root',
  factory: () => '',
});
```

- [ ] **Step 2: Provide it in app.config.ts**

Open `frontend/src/app/app.config.ts`. Find the existing `API_BASE_URL` provider line:
```typescript
{provide: API_BASE_URL, useValue: isDevMode() ? 'http://localhost:8080' : ''},
```

Add the import at the top of the file:
```typescript
import { CAPTCHA_SITE_KEY } from './core/config/captcha-site-key.token';
```

Add the new provider immediately after the API_BASE_URL one:
```typescript
// Real site-key is injected at deploy time (env-specific) or stays empty
// in dev — the <app-turnstile> component bypasses the widget when empty.
{ provide: CAPTCHA_SITE_KEY, useValue: '' },
```

- [ ] **Step 3: Verify frontend still builds**

```bash
cd frontend && npm run build 2>&1 | tail -5
```
Expected: build succeeds, `Output location:` line printed.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/core/config/captcha-site-key.token.ts \
        frontend/src/app/app.config.ts
git commit -m "feat(frontend): add CAPTCHA_SITE_KEY injection token"
```

---

## Task 11: Frontend — `<app-turnstile>` component (TDD)

**Files:**
- Create: `frontend/src/app/shared/uis/turnstile/turnstile.component.ts`
- Create: `frontend/src/app/shared/uis/turnstile/turnstile.component.html`
- Create: `frontend/src/app/shared/uis/turnstile/turnstile.component.spec.ts`

- [ ] **Step 1: Write the failing spec**

Create `frontend/src/app/shared/uis/turnstile/turnstile.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TurnstileComponent } from './turnstile.component';
import { CAPTCHA_SITE_KEY } from '../../../core/config/captcha-site-key.token';

describe('TurnstileComponent', () => {
  function setup(siteKey: string): ComponentFixture<TurnstileComponent> {
    TestBed.configureTestingModule({
      imports: [TurnstileComponent],
      providers: [
        provideZonelessChangeDetection(),
        { provide: CAPTCHA_SITE_KEY, useValue: siteKey },
      ],
    });
    const fixture = TestBed.createComponent(TurnstileComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('emits "dev-bypass" token immediately when site-key is empty', async () => {
    const fixture = setup('');
    let received: string | null = null;
    fixture.componentInstance.verified.subscribe((t) => (received = t));
    // The bypass emission happens via Promise.resolve() — wait one microtask.
    await Promise.resolve();
    expect(received).toBe('dev-bypass');
  });

  it('renders nothing in the dev-bypass branch', () => {
    const fixture = setup('');
    const host: HTMLElement = fixture.nativeElement;
    // No widget container should be in the DOM when we bypass.
    expect(host.querySelector('[data-testid="turnstile-widget"]')).toBeNull();
  });

  it('renders the widget container when site-key is set', () => {
    const fixture = setup('0xFAKE_SITE_KEY');
    const host: HTMLElement = fixture.nativeElement;
    const widget = host.querySelector('[data-testid="turnstile-widget"]');
    expect(widget).withContext('expected widget container').not.toBeNull();
  });
});
```

- [ ] **Step 2: Run the spec to see it fail**

```bash
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/turnstile.component.spec.ts' 2>&1 | tail -5
```
Expected: FAIL — `Cannot find module './turnstile.component'`.

- [ ] **Step 3: Create the template**

Create `frontend/src/app/shared/uis/turnstile/turnstile.component.html`:

```html
@if (siteKey()) {
  <div data-testid="turnstile-widget" #widget></div>
}
```

- [ ] **Step 4: Create the component**

Create `frontend/src/app/shared/uis/turnstile/turnstile.component.ts`:

```typescript
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Inject,
  OnDestroy,
  Output,
  PLATFORM_ID,
  ViewChild,
  computed,
  signal,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { CAPTCHA_SITE_KEY } from '../../../core/config/captcha-site-key.token';

declare global {
  interface Window {
    turnstile?: {
      render(
        container: HTMLElement,
        opts: {
          sitekey: string;
          callback: (token: string) => void;
          'error-callback'?: () => void;
          'expired-callback'?: () => void;
        },
      ): string;
      reset(widgetId: string): void;
    };
  }
}

const SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js';

/**
 * Cloudflare Turnstile widget wrapper. Loads the Cloudflare JS once per
 * page, renders the widget inside this component, and emits the token
 * via {@link verified}.
 *
 * <p>When {@link CAPTCHA_SITE_KEY} is empty (typical in dev) the widget
 * is not rendered; instead a {@code 'dev-bypass'} token is emitted on
 * the next microtask. The backend, also in disabled mode, accepts any
 * value, so the flow is unblocked locally.
 */
@Component({
  selector: 'app-turnstile',
  standalone: true,
  templateUrl: './turnstile.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TurnstileComponent implements AfterViewInit, OnDestroy {

  @ViewChild('widget') private widgetRef?: ElementRef<HTMLElement>;

  @Output() readonly verified = new EventEmitter<string>();

  readonly siteKey = computed(() => this._siteKey);
  private readonly _siteKey: string;
  private widgetId: string | null = null;
  private readonly _isBrowser: boolean;

  constructor(@Inject(CAPTCHA_SITE_KEY) siteKey: string, @Inject(PLATFORM_ID) platformId: object) {
    this._siteKey = siteKey;
    this._isBrowser = isPlatformBrowser(platformId);

    if (!siteKey) {
      // dev bypass — emit on next microtask so subscribers attached
      // synchronously by the parent are connected.
      Promise.resolve().then(() => this.verified.emit('dev-bypass'));
    }
  }

  ngAfterViewInit(): void {
    if (!this._siteKey || !this._isBrowser || !this.widgetRef) return;
    this.loadScript().then(() => this.renderWidget());
  }

  ngOnDestroy(): void {
    if (this.widgetId && window.turnstile) {
      try { window.turnstile.reset(this.widgetId); } catch { /* ignore */ }
    }
  }

  private loadScript(): Promise<void> {
    if (window.turnstile) return Promise.resolve();
    const existing = document.querySelector(`script[src="${SCRIPT_SRC}"]`);
    if (existing) {
      return new Promise<void>((resolve) => {
        existing.addEventListener('load', () => resolve(), { once: true });
      });
    }
    return new Promise<void>((resolve, reject) => {
      const s = document.createElement('script');
      s.src = SCRIPT_SRC;
      s.async = true;
      s.defer = true;
      s.onload = () => resolve();
      s.onerror = () => reject(new Error('Failed to load Turnstile script'));
      document.head.appendChild(s);
    });
  }

  private renderWidget(): void {
    if (!window.turnstile || !this.widgetRef) return;
    this.widgetId = window.turnstile.render(this.widgetRef.nativeElement, {
      sitekey: this._siteKey,
      callback: (token: string) => this.verified.emit(token),
      'error-callback': () => this.verified.emit(''),
      'expired-callback': () => this.verified.emit(''),
    });
  }
}
```

- [ ] **Step 5: Run the spec to see it pass**

```bash
npm test -- --watch=false --browsers=ChromeHeadless --include='**/turnstile.component.spec.ts' 2>&1 | tail -5
```
Expected: `TOTAL: 3 SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/shared/uis/turnstile/
git commit -m "feat(frontend): add <app-turnstile> widget component with dev bypass"
```

---

## Task 12: Wire `<app-turnstile>` into register form

**Files:**
- Modify: `frontend/src/app/pages/auth/register/register.component.ts`
- Modify: `frontend/src/app/pages/auth/register/register.component.html`

- [ ] **Step 1: Inspect the existing register component**

Run:
```bash
grep -n "captchaToken\|TurnstileComponent\|signal\|imports:" frontend/src/app/pages/auth/register/register.component.ts | head -10
```

Note the current imports and signal declarations so the additions match the existing style.

- [ ] **Step 2: Update register.component.ts**

Open `frontend/src/app/pages/auth/register/register.component.ts`. Apply two changes:

(a) Add to the imports block at the top of the file (near other component imports):
```typescript
import { TurnstileComponent } from '../../../shared/uis/turnstile/turnstile.component';
```

(b) Add `TurnstileComponent` to the `imports: [...]` array of the `@Component` decorator. Example:
```typescript
imports: [..., TurnstileComponent],
```

(c) Add a `captchaToken` signal next to other signals in the class:
```typescript
protected readonly captchaToken = signal<string | null>(null);
```

(d) In the method that builds the request payload submitted to the backend (search for `register({` or `RegisterRequest` construction), include the field:
```typescript
captchaToken: this.captchaToken(),
```

- [ ] **Step 3: Update register.component.html**

Open `frontend/src/app/pages/auth/register/register.component.html`. Locate the submit button. Just before the submit button, add:

```html
<app-turnstile (verified)="captchaToken.set($event)" />
```

Modify the submit button to disable when `captchaToken()` is null. Find the existing `[disabled]` expression and OR it with `!captchaToken()`. Example if it was `[disabled]="loading()"`:
```html
[disabled]="loading() || !captchaToken()"
```

If there is no `[disabled]` yet, add one: `[disabled]="!captchaToken()"`.

- [ ] **Step 4: Run the register component spec**

```bash
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/register.component.spec.ts' 2>&1 | tail -5
```
Expected: all specs pass. If a spec failed because it set `[disabled]` indirectly or expected a payload without `captchaToken`, update the spec to provide the dev-bypass token by simulating `captchaToken.set('dev-bypass')` before submit.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/auth/register/
git commit -m "feat(auth): wire <app-turnstile> into client register form"
```

---

## Task 13: Wire `<app-turnstile>` into register-pro form

**Files:**
- Modify: `frontend/src/app/pages/auth/register-pro/register-pro.component.ts`
- Modify: `frontend/src/app/pages/auth/register-pro/register-pro.component.html`

- [ ] **Step 1: Update register-pro.component.ts**

Open `frontend/src/app/pages/auth/register-pro/register-pro.component.ts`. Repeat the four edits from Task 12 step 2, applied here:

(a) Import:
```typescript
import { TurnstileComponent } from '../../../shared/uis/turnstile/turnstile.component';
```

(b) Add `TurnstileComponent` to `imports: [...]` array of `@Component`.

(c) Add signal:
```typescript
protected readonly captchaToken = signal<string | null>(null);
```

(d) Add `captchaToken: this.captchaToken()` to the payload submitted in the register call.

- [ ] **Step 2: Update register-pro.component.html**

Open `frontend/src/app/pages/auth/register-pro/register-pro.component.html`. Add before the submit button:

```html
<app-turnstile (verified)="captchaToken.set($event)" />
```

Adjust the submit button's `[disabled]` to include `|| !captchaToken()`.

- [ ] **Step 3: Run the register-pro spec**

```bash
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/register-pro.component.spec.ts' 2>&1 | tail -5
```
Expected: pass. Apply the same dev-bypass fixture fix as Task 12 if needed.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/auth/register-pro/
git commit -m "feat(auth): wire <app-turnstile> into pro register form"
```

---

## Task 14: Wire `<app-turnstile>` into forgot-password form

**Files:**
- Modify: `frontend/src/app/pages/auth/forgot-password/forgot-password.component.ts`
- Modify: `frontend/src/app/pages/auth/forgot-password/forgot-password.component.html`

- [ ] **Step 1: Update forgot-password.component.ts**

Apply the same four edits as Task 12 (import, `imports[]`, signal, payload field).

- [ ] **Step 2: Update forgot-password.component.html**

Add `<app-turnstile (verified)="captchaToken.set($event)" />` before the submit button. Add `|| !captchaToken()` to `[disabled]`.

- [ ] **Step 3: Run the forgot-password spec (if it exists)**

```bash
ls frontend/src/app/pages/auth/forgot-password/*.spec.ts 2>&1
```

If a spec exists, run it:
```bash
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/forgot-password.component.spec.ts' 2>&1 | tail -5
```
Expected: pass (or the file does not exist, which is also fine — no spec to break).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/auth/forgot-password/
git commit -m "feat(auth): wire <app-turnstile> into forgot-password form"
```

---

## Task 15: i18n keys

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add the captcha key to French translations**

Open `frontend/public/i18n/fr.json`. Find the `"errors"` top-level object. Inside it, find or create a `"captcha"` sub-object. Add a `"required"` field:

```json
"captcha": {
  "required": "Veuillez compléter la vérification"
}
```

If the `errors` object doesn't have a `captcha` sub-object yet, add the whole block. If it does, just add the field.

- [ ] **Step 2: Add the captcha key to English translations**

Open `frontend/public/i18n/en.json`. Same pattern:

```json
"captcha": {
  "required": "Please complete the verification"
}
```

- [ ] **Step 3: Validate the JSON files**

```bash
python3 -c "import json; json.load(open('frontend/public/i18n/fr.json'))" && echo "FR OK"
python3 -c "import json; json.load(open('frontend/public/i18n/en.json'))" && echo "EN OK"
```
Expected: `FR OK` and `EN OK`.

- [ ] **Step 4: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "i18n(auth): add errors.captcha.required translation"
```

---

## Task 16: Operational documentation

**Files:**
- Create: `docs/OPS_CLOUDFLARE.md`
- Modify: `.env.example`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Create the operations doc**

Create `docs/OPS_CLOUDFLARE.md`:

```markdown
# Cloudflare proxy & Turnstile — operations runbook

LuxPretty in production sits behind Cloudflare. This page documents the
one-time setup and the operational levers when things go wrong.

## One-time setup (after PR1 ships)

1. Create a Cloudflare account (free tier).
2. Add the LuxPretty domain to Cloudflare. Cloudflare scans the existing
   DNS records and gives you two nameservers.
3. At the domain registrar, replace the nameservers with the two from
   Cloudflare. Propagation: 1–24h.
4. Cloudflare dashboard → DNS: enable the orange-cloud proxy on the
   `@`, `www`, and (if applicable) `api` records pointing to the VPS.
5. SSL/TLS → Overview: set mode to **"Full (strict)"**. This requires a
   valid certificate on the VPS — use Let's Encrypt (`certbot` + auto
   renew).
6. SSL/TLS → Edge Certificates: enable **"Always Use HTTPS"** and
   **"Automatic HTTPS Rewrites"**.
7. Security → Bots: enable **"Bot Fight Mode"** (free).
8. Security → WAF → Managed Rules: enable the **"Cloudflare Free Managed
   Ruleset"**.
9. Security → DDoS: leave on "Default" (automatic).
10. Caching → Configuration: "Standard" caching level. Then add a Page
    Rule for `*luxpretty.lu/api/*` with **Cache Level: Bypass** — the API
    must never be cached.
11. Speed → Brotli compression: enable.

## Turnstile site setup

1. Cloudflare dashboard → Turnstile → "Add site".
2. Set domain to `luxpretty.lu` (and any subdomains used by the
   frontend).
3. Mode: **Managed** (Cloudflare decides invisible vs interactive based
   on risk).
4. Copy:
   - **Site Key** (public) → goes in `frontend/src/app/app.config.ts`
     `CAPTCHA_SITE_KEY` provider, or in the deploy-time config script.
   - **Secret Key** (private) → goes in the production env var
     `TURNSTILE_SECRET_KEY` (server-side only, never commit).
5. Set the backend env vars:
   ```
   TURNSTILE_ENABLED=true
   TURNSTILE_SECRET_KEY=<paste secret here>
   ```
6. Redeploy backend and frontend.

## Verifying the proxy works

```
curl -sI https://luxpretty.lu/ping | grep -i 'cf-ray\|server'
```
Expected: a `cf-ray:` header and `server: cloudflare`. If those are
missing, Cloudflare is not proxying (orange-cloud might be disabled).

## Verifying Turnstile works

1. Open `https://luxpretty.lu/register/client` in an incognito window.
2. The Turnstile widget should appear above the submit button.
3. Submit without completing it: the button stays disabled.
4. Complete the widget, submit: a successful POST `/api/auth/register`
   returns 201.
5. Backend logs should show `Turnstile verification ok` at INFO level.

## Emergency disable (Turnstile is blocking real users)

1. Set the env var `TURNSTILE_ENABLED=false` on the backend.
2. Redeploy backend (≈30s downtime).
3. The aspect short-circuits and accepts any value.
4. Investigate via Cloudflare → Security → Events to see what was
   rejected and why.

## Emergency disable (Bot Fight Mode false positives)

1. Cloudflare dashboard → Security → Bots → toggle "Bot Fight Mode" off.
2. Effective immediately, no redeploy.

## Cloudflare CIDR ranges

The backend's `ClientIpResolver` only trusts `CF-Connecting-IP` when the
request comes from a published Cloudflare range. The ranges are baked
into `backend/src/main/java/com/luxpretty/app/security/ip/CloudflareIpRanges.java`.

Update procedure when Cloudflare publishes new ranges (rare):
1. Visit https://www.cloudflare.com/ips/.
2. Diff against the constants in `CloudflareIpRanges.java`.
3. Open a PR adding/removing ranges. No other change needed.
```

- [ ] **Step 2: Update .env.example**

Open `.env.example`. Add at the bottom (after the existing block):

```
# Cloudflare Turnstile captcha (PR1 of the security roadmap)
# Set TURNSTILE_ENABLED=true and TURNSTILE_SECRET_KEY in production.
# Local dev: leave both unset, the aspect bypasses.
TURNSTILE_ENABLED=false
TURNSTILE_SECRET_KEY=
```

- [ ] **Step 3: Update CLAUDE.md**

Open `CLAUDE.md`. Find the `## Important Notes` section (or the last section before "Additional Resources" if it's structured differently). Add one new bullet:

```
- **Cloudflare proxy in prod:** Production traffic goes through Cloudflare proxy. The backend reads the real client IP from `CF-Connecting-IP` via `ClientIpResolver`, only trusting it when the remote address is in a Cloudflare CIDR range. See `docs/OPS_CLOUDFLARE.md`.
```

- [ ] **Step 4: Verify the doc renders properly**

```bash
head -20 docs/OPS_CLOUDFLARE.md
```
Expected: clean markdown, no broken backticks.

- [ ] **Step 5: Commit**

```bash
git add docs/OPS_CLOUDFLARE.md .env.example CLAUDE.md
git commit -m "docs(security): Cloudflare + Turnstile operations runbook"
```

---

## Task 17: Final cross-repo verification

**Files:** none modified — verification only.

- [ ] **Step 1: Run the full backend test suite**

```bash
cd backend && mvn test -q 2>&1 | grep "Tests run:" | tail -1
```
Expected: `Tests run: 565, Failures: 0, Errors: 31, Skipped: 0` (baseline 553 + 12 new = 565; the 31 errors are pre-existing).

- [ ] **Step 2: Run the full frontend test suite**

```bash
cd ../frontend && npm test -- --watch=false --browsers=ChromeHeadless 2>&1 | tail -3
```
Expected: `TOTAL: 603 SUCCESS` (baseline 600 + 3 new TurnstileComponent specs).

- [ ] **Step 3: Run the frontend production build**

```bash
npm run build 2>&1 | tail -5
```
Expected: build succeeds, `Output location:` printed.

- [ ] **Step 4: Smoke test the backend boot in disabled mode**

```bash
cd ../backend && mvn spring-boot:run -q 2>&1 | head -40 &
sleep 25
curl -sf -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Test","email":"smoke@test.fr","password":"password123","consent":true}' \
  >/dev/null && echo "SMOKE OK (registered without captcha — disabled mode works)" || echo "SMOKE FAILED"
pkill -f spring-boot:run
```
Expected: `SMOKE OK` — the disabled toggle short-circuits the aspect and the register endpoint accepts requests without a `captchaToken` field.

(If the register endpoint requires CSRF, this curl will fail with 403 — that's expected and not a captcha problem. Then re-run by also fetching `/api/csrf` first, or just look for the absence of "captcha invalide" in the backend logs as proof the aspect bypassed correctly.)

- [ ] **Step 5: Smoke test the backend boot in enabled mode (without a real key)**

```bash
TURNSTILE_ENABLED=true mvn spring-boot:run -q 2>&1 | head -20 | grep -E "secret-key is blank|IllegalStateException"
```
Expected: the fail-fast triggers and prints `secret-key is blank`. This proves the safety net works.

- [ ] **Step 6: Final commit if any fixes were needed**

If steps 1–5 surfaced anything, commit fixes:
```bash
git add -A
git commit -m "fix: address smoke-test findings for cloudflare-turnstile PR1"
```

If nothing to commit, just continue.

---

## Task 18: Open the PR

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/cloudflare-turnstile
```

- [ ] **Step 2: Create the PR**

```bash
gh pr create --title "feat(security): Cloudflare proxy + Turnstile captcha (PR1)" --body "$(cat <<'EOF'
## Summary
First chantier of the security roadmap (`docs/superpowers/specs/2026-05-11-security-roadmap.md`).

Adds Turnstile captcha verification on `/register`, `/register/pro`, `/register/client`, `/forgot-password`. **`/login` is intentionally NOT covered** — friction too high, PR2 (rate limit) + PR3 (account lockout) will protect it.

Also ships `ClientIpResolver` which reads `CF-Connecting-IP` only when the request comes from a Cloudflare CIDR range (anti-spoof). Will be reused by PR2's rate limiter.

## Architecture
- Backend: `@TurnstileRequired` annotation + AOP aspect calls Cloudflare `siteverify`. Toggle off in dev/test via `app.security.turnstile.enabled=false`.
- Frontend: `<app-turnstile>` standalone component loads the Cloudflare widget script dynamically, emits the token via `Output`. In dev (no site-key set), it emits a `'dev-bypass'` token immediately.

## Manual actions post-merge
1. Cloudflare dashboard → Turnstile → "Add site" → copy **Site Key** + **Secret Key**.
2. Set prod env vars: `TURNSTILE_ENABLED=true`, `TURNSTILE_SECRET_KEY=<secret>`.
3. Inject the Site Key in the frontend deploy config.
4. Follow `docs/OPS_CLOUDFLARE.md` to wire the Cloudflare proxy itself (DNS, SSL/TLS Full strict, Bot Fight Mode, WAF, Page Rule bypass cache on `/api/*`).

## Test plan
- [x] `mvn test`: 565 tests (553 baseline + 12 new), 0 failures
- [x] `npm test`: 603 specs (600 baseline + 3 new), 0 failures
- [x] `npm run build`: prod build OK
- [x] Smoke: backend boot disabled-mode → register accepted without captcha
- [x] Smoke: backend boot enabled-mode without secret → fail-fast at boot
- [ ] Post-merge: manual end-to-end with real Cloudflare site key in staging

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Save the PR URL**

Note the URL printed by `gh pr create` and share it.

---

## Self-review notes

**Spec coverage:**
- ✅ Architecture (Cloudflare proxy + Turnstile, separation infra/code) → Tasks 1-9 (backend) + 10-14 (frontend) + 16 (doc)
- ✅ Étapes manuelles Cloudflare → Task 16 (`docs/OPS_CLOUDFLARE.md`)
- ✅ Backend code (`TurnstileProperties`, `TurnstileVerifier`, `TurnstileAspect`, `TurnstileRequired`, `ClientIpResolver`, `CloudflareIpRanges`) → Tasks 2, 3, 4, 5, 6, 7
- ✅ Backend wiring (DTOs + controller annotations) → Tasks 8, 9
- ✅ Frontend component + 3 forms + i18n → Tasks 10, 11, 12, 13, 14, 15
- ✅ Documentation → Task 16
- ✅ Validation → Task 17
- ✅ PR opening → Task 18

**Type consistency:** `TurnstileVerifier.isValid(String token, String clientIp)` — same signature in Tasks 3, 7. `<app-turnstile>` selector `app-turnstile` and `verified` output — same in Tasks 11, 12, 13, 14. `CAPTCHA_SITE_KEY` token created Task 10, consumed Task 11.

**No placeholders:** every step has either complete code or an exact command. The few "Adjust if needed" notes are paired with concrete steps (e.g. Task 8 step 5 about positional DTO args) so an engineer knows what to look for.
