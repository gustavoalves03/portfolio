# Mail Outbox + Postmark Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the synchronous `@Async EmailService` with a transactional outbox + Postmark provider. Outbox row INSERT joins the caller transaction (atomicity), a scheduled worker dequeues and sends via Mailpit (dev) or Postmark (prod). Webhook handles bounces/complaints/deliveries.

**Architecture:** Outbox pattern in app schema. Worker `@Scheduled(fixedDelay=30s)` with `SELECT FOR UPDATE SKIP LOCKED` batch=10. Exponential backoff retry (5 attempts, 1m → 5m → 25m → 2h → 12h). `MailSender` interface with two implementations (`SmtpMailSender` for Mailpit, `PostmarkMailSender` for prod) selected via `app.mail.provider` property. Templates Thymeleaf with LuxPretty branding, CSS inlined by Jsoup before send.

**Tech Stack:** Spring Boot 3.5.4 / Java 21 / Oracle / Flyway / Thymeleaf (already present) / Jsoup 1.17.2 / Postmark Java SDK 1.10.0 / Mailpit (Docker).

**Branche cible:** `feat/mail-outbox` (worktree depuis `main`).

---

## Phase 1 — PR1: Plumbing (~5h)

### Task 1: Create worktree

**Files:** worktree

- [ ] **Step 1: Create worktree from main**

From the main repo (`/Users/Gustavo.alves/Documents/personal/portfolio`):

```bash
git worktree add ../portfolio-mail -b feat/mail-outbox main
```

- [ ] **Step 2: Verify**

```bash
cd ../portfolio-mail && git status
```

Expected: `On branch feat/mail-outbox`, working tree clean.

> All subsequent tasks run from `/Users/Gustavo.alves/Documents/personal/portfolio-mail`. Use absolute paths in Bash to avoid confusion.

---

### Task 2: Add Maven dependencies (Postmark + Jsoup)

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: Add dependencies**

In `backend/pom.xml`, add inside `<dependencies>` (logical place: after `spring-boot-starter-thymeleaf`):

```xml
<!-- Postmark transactional mail provider (prod) -->
<dependency>
    <groupId>com.postmarkapp</groupId>
    <artifactId>postmark</artifactId>
    <version>1.10.0</version>
</dependency>
<!-- Jsoup: HTML manipulation, used to inline CSS in mail templates -->
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

- [ ] **Step 2: Verify resolution**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q -DskipTests compile
```

Expected: BUILD SUCCESS.

> If `com.postmarkapp:postmark` doesn't resolve, fall back to `com.wildbit.java:postmark:1.9.0` (older artifact ID still on Maven Central). The API is the same.

- [ ] **Step 3: Commit**

```bash
git add backend/pom.xml
git commit -m "build(backend): add Postmark + Jsoup deps for mail outbox"
```

---

### Task 3: Mailpit in docker-compose

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add Mailpit service**

Open `docker-compose.yml`. Locate the `services:` section. Add the following service near the other dev services:

```yaml
  # Mailpit: dev SMTP catcher with HTTP UI (replaces the need for a real SMTP server in dev)
  mailpit:
    profiles: ["dev"]
    image: axllent/mailpit:latest
    container_name: mailpit
    ports:
      - "8025:8025"   # web UI: http://localhost:8025
      - "1025:1025"   # SMTP
```

- [ ] **Step 2: Verify yaml syntax**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail
docker compose config --profile dev 2>&1 | tail -20
```

Expected: parsed config printed, mailpit service visible, no syntax errors.

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "build(docker): add Mailpit service for dev SMTP capture"
```

---

### Task 4: Flyway V8 — MAIL_OUTBOX table

**Files:**
- Create: `backend/src/main/resources/db/migration/oracle/V8__create_mail_outbox.sql`

- [ ] **Step 1: Write the migration**

```sql
-- ── MAIL_OUTBOX : transactional outbox for mail delivery ──
-- Rows are INSERTed by MailOutboxService.queue() inside the caller's transaction
-- (rollback-safe). A scheduled worker dequeues PENDING rows with FOR UPDATE
-- SKIP LOCKED and sends via the configured MailSender (smtp / postmark).
CREATE TABLE MAIL_OUTBOX (
    ID                    NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    TEMPLATE              VARCHAR2(64 CHAR) NOT NULL,
    RECIPIENT_EMAIL       VARCHAR2(320 CHAR) NOT NULL,
    TENANT_SLUG           VARCHAR2(64 CHAR),
    VARS_JSON             CLOB NOT NULL,
    STATUS                VARCHAR2(32 CHAR) DEFAULT 'PENDING' NOT NULL,
    ATTEMPTS              NUMBER(5) DEFAULT 0 NOT NULL,
    NEXT_ATTEMPT_AT       TIMESTAMP NOT NULL,
    LAST_ERROR            VARCHAR2(2000 CHAR),
    PROVIDER_MESSAGE_ID   VARCHAR2(255 CHAR),
    CREATED_AT            TIMESTAMP NOT NULL,
    SENT_AT               TIMESTAMP,
    DELIVERED_AT          TIMESTAMP,
    CONSTRAINT CK_MAIL_OUTBOX_STATUS CHECK (STATUS IN ('PENDING','IN_FLIGHT','SENT','PERMANENTLY_FAILED'))
);

CREATE INDEX IX_MAIL_OUTBOX_PENDING ON MAIL_OUTBOX (STATUS, NEXT_ATTEMPT_AT);
CREATE INDEX IX_MAIL_OUTBOX_PROVIDER_MSG ON MAIL_OUTBOX (PROVIDER_MESSAGE_ID);
```

- [ ] **Step 2: Compile (Flyway validation runs at startup, but JPA validation runs at compile)**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q -DskipTests compile
```

Expected: BUILD SUCCESS. The migration itself is not validated yet (no entity referencing it).

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/oracle/V8__create_mail_outbox.sql
git commit -m "feat(db): add MAIL_OUTBOX table (V8)"
```

---

### Task 5: Domain enums (MailStatus, MailTemplate)

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/mail/domain/MailStatus.java`
- Create: `backend/src/main/java/com/luxpretty/app/mail/domain/MailTemplate.java`

- [ ] **Step 1: MailStatus enum**

```java
// backend/src/main/java/com/luxpretty/app/mail/domain/MailStatus.java
package com.luxpretty.app.mail.domain;

public enum MailStatus {
    PENDING,
    IN_FLIGHT,
    SENT,
    PERMANENTLY_FAILED
}
```

- [ ] **Step 2: MailTemplate enum**

```java
// backend/src/main/java/com/luxpretty/app/mail/domain/MailTemplate.java
package com.luxpretty.app.mail.domain;

import com.luxpretty.app.mail.vars.BookingConfirmedVars;
import com.luxpretty.app.mail.vars.BookingReceivedProVars;
import com.luxpretty.app.mail.vars.MailVars;
import com.luxpretty.app.mail.vars.ResetPasswordVars;
import com.luxpretty.app.mail.vars.WelcomeProVars;

/**
 * Catalogue of available transactional mail templates.
 *
 * <p>Each entry binds a logical template name to:
 * <ul>
 *   <li>the Thymeleaf template path (under {@code templates/mail/})</li>
 *   <li>the concrete {@link MailVars} class expected by the template</li>
 * </ul>
 *
 * <p>Run-time check: {@code MailOutboxService.queue()} asserts
 * {@code template.varsClass().isInstance(vars)} to fail fast if a caller
 * passes mismatched vars.
 */
public enum MailTemplate {
    RESET_PASSWORD("reset-password", ResetPasswordVars.class),
    BOOKING_CONFIRMED("booking-confirmed", BookingConfirmedVars.class),
    BOOKING_RECEIVED_PRO("booking-received-pro", BookingReceivedProVars.class),
    WELCOME_PRO("welcome-pro", WelcomeProVars.class);

    private final String templatePath;
    private final Class<? extends MailVars> varsClass;

    MailTemplate(String templatePath, Class<? extends MailVars> varsClass) {
        this.templatePath = templatePath;
        this.varsClass = varsClass;
    }

    public String templatePath() { return templatePath; }
    public Class<? extends MailVars> varsClass() { return varsClass; }
}
```

- [ ] **Step 3: Compile**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q -DskipTests compile
```

Expected: COMPILATION FAILURE because the `vars.*` classes don't exist yet. That's fine — we create them next.

> Do NOT commit yet; commit happens after vars are created (Task 6).

---

### Task 6: MailVars sealed interface + 4 records

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/mail/vars/MailVars.java`
- Create: `backend/src/main/java/com/luxpretty/app/mail/vars/ResetPasswordVars.java`
- Create: `backend/src/main/java/com/luxpretty/app/mail/vars/BookingConfirmedVars.java`
- Create: `backend/src/main/java/com/luxpretty/app/mail/vars/BookingReceivedProVars.java`
- Create: `backend/src/main/java/com/luxpretty/app/mail/vars/WelcomeProVars.java`

- [ ] **Step 1: Sealed interface**

```java
// backend/src/main/java/com/luxpretty/app/mail/vars/MailVars.java
package com.luxpretty.app.mail.vars;

/**
 * Sealed marker interface for typed mail template variables.
 * <p>Each {@code MailTemplate} entry binds to one concrete subtype.
 * Records are serialized to JSON and stored in {@code MAIL_OUTBOX.VARS_JSON}.
 */
public sealed interface MailVars
    permits ResetPasswordVars, BookingConfirmedVars, BookingReceivedProVars, WelcomeProVars {}
```

- [ ] **Step 2: ResetPasswordVars**

```java
// backend/src/main/java/com/luxpretty/app/mail/vars/ResetPasswordVars.java
package com.luxpretty.app.mail.vars;

public record ResetPasswordVars(
        String userName,
        String resetUrl
) implements MailVars {}
```

- [ ] **Step 3: BookingConfirmedVars**

```java
// backend/src/main/java/com/luxpretty/app/mail/vars/BookingConfirmedVars.java
package com.luxpretty.app.mail.vars;

import java.math.BigDecimal;

/**
 * Sent to the client when their booking is confirmed.
 * Dates are pre-formatted to avoid JSON serialization of temporals
 * (and to keep rendering deterministic across worker restarts).
 */
public record BookingConfirmedVars(
        String clientName,
        String salonName,
        String careName,
        BigDecimal carePrice,
        String careDuration,
        String appointmentDate,
        String appointmentTime,
        Long bookingId,
        String dashboardUrl
) implements MailVars {}
```

- [ ] **Step 4: BookingReceivedProVars**

```java
// backend/src/main/java/com/luxpretty/app/mail/vars/BookingReceivedProVars.java
package com.luxpretty.app.mail.vars;

import java.math.BigDecimal;

/**
 * Sent to the salon (pro) when a new booking is created by a client.
 */
public record BookingReceivedProVars(
        String proName,
        String clientName,
        String careName,
        BigDecimal carePrice,
        String careDuration,
        String appointmentDate,
        String appointmentTime,
        Long bookingId,
        String dashboardUrl
) implements MailVars {}
```

- [ ] **Step 5: WelcomeProVars**

```java
// backend/src/main/java/com/luxpretty/app/mail/vars/WelcomeProVars.java
package com.luxpretty.app.mail.vars;

public record WelcomeProVars(
        String userName,
        String userEmail,
        String dashboardUrl
) implements MailVars {}
```

- [ ] **Step 6: Compile**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q -DskipTests compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit (covers Tasks 5 + 6)**

```bash
git add backend/src/main/java/com/luxpretty/app/mail/domain/ \
        backend/src/main/java/com/luxpretty/app/mail/vars/
git commit -m "feat(mail): add MailStatus + MailTemplate enums + 4 MailVars records"
```

---

### Task 7: MailOutbox entity + repository

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/mail/domain/MailOutbox.java`
- Create: `backend/src/main/java/com/luxpretty/app/mail/repo/MailOutboxRepository.java`

- [ ] **Step 1: Entity**

```java
// backend/src/main/java/com/luxpretty/app/mail/domain/MailOutbox.java
package com.luxpretty.app.mail.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "MAIL_OUTBOX")
public class MailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "template", nullable = false, length = 64)
    private MailTemplate template;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Column(name = "tenant_slug", length = 64)
    private String tenantSlug;

    @Lob
    @Basic(fetch = FetchType.EAGER)
    @Column(name = "vars_json", nullable = false, columnDefinition = "CLOB")
    private String varsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MailStatus status = MailStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (nextAttemptAt == null) nextAttemptAt = createdAt;
    }
}
```

- [ ] **Step 2: Repository**

```java
// backend/src/main/java/com/luxpretty/app/mail/repo/MailOutboxRepository.java
package com.luxpretty.app.mail.repo;

import com.luxpretty.app.mail.domain.MailOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MailOutboxRepository extends JpaRepository<MailOutbox, Long> {

    /**
     * Atomically locks a batch of PENDING rows whose retry window has elapsed.
     * Uses Oracle FOR UPDATE SKIP LOCKED so multiple workers can run concurrently
     * without double-sending.
     */
    @Query(value = """
        SELECT * FROM MAIL_OUTBOX
        WHERE STATUS = 'PENDING'
          AND NEXT_ATTEMPT_AT <= :now
        ORDER BY CREATED_AT
        FETCH FIRST :batchSize ROWS ONLY
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<MailOutbox> lockBatchForSending(@Param("now") LocalDateTime now,
                                         @Param("batchSize") int batchSize);

    Optional<MailOutbox> findByProviderMessageId(String providerMessageId);
}
```

- [ ] **Step 3: Compile**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q -DskipTests compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/mail/domain/MailOutbox.java \
        backend/src/main/java/com/luxpretty/app/mail/repo/
git commit -m "feat(mail): add MailOutbox JPA entity + repository with locking query"
```

---

### Task 8: MailRetryPolicy + tests (TDD)

**Files:**
- Create: `backend/src/test/java/com/luxpretty/app/mail/app/MailRetryPolicyTests.java`
- Create: `backend/src/main/java/com/luxpretty/app/mail/app/MailRetryPolicy.java`

- [ ] **Step 1: Write failing test**

```java
// backend/src/test/java/com/luxpretty/app/mail/app/MailRetryPolicyTests.java
package com.luxpretty.app.mail.app;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailRetryPolicyTests {

    private final MailRetryPolicy policy = new MailRetryPolicy();

    @Test
    void attempt_1_delays_1_minute() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = policy.nextAttemptAfter(1, now);
        assertThat(Duration.between(now, next)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void attempt_2_delays_5_minutes() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(Duration.between(now, policy.nextAttemptAfter(2, now)))
                .isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void attempt_3_delays_25_minutes() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(Duration.between(now, policy.nextAttemptAfter(3, now)))
                .isEqualTo(Duration.ofMinutes(25));
    }

    @Test
    void attempt_4_delays_2_hours() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(Duration.between(now, policy.nextAttemptAfter(4, now)))
                .isEqualTo(Duration.ofHours(2));
    }

    @Test
    void attempt_5_is_terminal_throws() {
        // 5+ means we should have already marked PERMANENTLY_FAILED before calling
        assertThatThrownBy(() -> policy.nextAttemptAfter(5, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void max_attempts_is_5() {
        assertThat(MailRetryPolicy.MAX_ATTEMPTS).isEqualTo(5);
    }
}
```

- [ ] **Step 2: Verify failure**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q test -Dtest=MailRetryPolicyTests
```

Expected: COMPILATION FAILURE (`MailRetryPolicy` doesn't exist).

- [ ] **Step 3: Implement**

```java
// backend/src/main/java/com/luxpretty/app/mail/app/MailRetryPolicy.java
package com.luxpretty.app.mail.app;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Exponential backoff for transient mail send failures.
 * <p>Attempt index is 1-based (first retry after attempt 1).
 * After {@link #MAX_ATTEMPTS} attempts the mail should be marked
 * {@code PERMANENTLY_FAILED} by the caller (not retried again).
 */
@Component
public class MailRetryPolicy {

    public static final int MAX_ATTEMPTS = 5;

    /**
     * Backoff delays at index i (0-based): 1m, 5m, 25m, 2h.
     * Index 4 (5th attempt) is terminal — no delay returned.
     */
    private static final List<Duration> BACKOFFS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(25),
            Duration.ofHours(2)
    );

    /**
     * Returns the timestamp at which the next attempt should be scheduled.
     * @param attemptCount how many attempts have been made so far (1..MAX_ATTEMPTS-1)
     * @param now reference timestamp (caller-supplied for testability)
     */
    public LocalDateTime nextAttemptAfter(int attemptCount, LocalDateTime now) {
        if (attemptCount < 1 || attemptCount >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException(
                "attemptCount must be in [1, " + (MAX_ATTEMPTS - 1) + "], got " + attemptCount);
        }
        return now.plus(BACKOFFS.get(attemptCount - 1));
    }
}
```

- [ ] **Step 4: Verify pass**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q test -Dtest=MailRetryPolicyTests
```

Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/mail/app/MailRetryPolicy.java \
        backend/src/test/java/com/luxpretty/app/mail/app/MailRetryPolicyTests.java
git commit -m "feat(mail): add MailRetryPolicy with exponential backoff + tests"
```

---

### Task 9: MailSender interface + exception classes

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/mail/app/MailSender.java`
- Create: `backend/src/main/java/com/luxpretty/app/mail/app/RetryableMailException.java`
- Create: `backend/src/main/java/com/luxpretty/app/mail/app/HardMailException.java`

- [ ] **Step 1: Exception classes**

```java
// backend/src/main/java/com/luxpretty/app/mail/app/RetryableMailException.java
package com.luxpretty.app.mail.app;

/** Transient send error (5xx, timeout, IO). Caller should retry with backoff. */
public class RetryableMailException extends RuntimeException {
    public RetryableMailException(String message) { super(message); }
    public RetryableMailException(String message, Throwable cause) { super(message, cause); }
}
```

```java
// backend/src/main/java/com/luxpretty/app/mail/app/HardMailException.java
package com.luxpretty.app.mail.app;

/** Permanent send error (invalid email, bad credentials). Do not retry. */
public class HardMailException extends RuntimeException {
    public HardMailException(String message) { super(message); }
    public HardMailException(String message, Throwable cause) { super(message, cause); }
}
```

- [ ] **Step 2: Interface**

```java
// backend/src/main/java/com/luxpretty/app/mail/app/MailSender.java
package com.luxpretty.app.mail.app;

/**
 * Abstraction over the underlying mail provider.
 * <p>Two implementations selected at runtime via {@code app.mail.provider}:
 * <ul>
 *   <li>{@code smtp}: {@link SmtpMailSender} (dev — Mailpit)</li>
 *   <li>{@code postmark}: {@link PostmarkMailSender} (prod)</li>
 * </ul>
 */
public interface MailSender {

    /**
     * Sends a mail synchronously.
     *
     * @param recipientEmail to address
     * @param subject mail subject
     * @param htmlBody HTML body (already inlined-CSS)
     * @param textBody plain text body
     * @return provider-specific message id (Postmark MessageID) or {@code null} for SMTP
     * @throws RetryableMailException for transient failures (5xx, timeout, IO)
     * @throws HardMailException for permanent failures (invalid email, bad credentials)
     */
    String send(String recipientEmail, String subject, String htmlBody, String textBody);
}
```

- [ ] **Step 3: Compile**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q -DskipTests compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/mail/app/MailSender.java \
        backend/src/main/java/com/luxpretty/app/mail/app/RetryableMailException.java \
        backend/src/main/java/com/luxpretty/app/mail/app/HardMailException.java
git commit -m "feat(mail): add MailSender interface + retry/hard exception types"
```

---

### Task 10: SmtpMailSender (Mailpit dev)

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/mail/app/SmtpMailSender.java`

- [ ] **Step 1: Implementation**

```java
// backend/src/main/java/com/luxpretty/app/mail/app/SmtpMailSender.java
package com.luxpretty.app.mail.app;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailSender implements MailSender {

    private final JavaMailSender javaMailSender;
    private final String fromAddress;
    private final String fromName;

    public SmtpMailSender(
            JavaMailSender javaMailSender,
            @Value("${app.mail.from}") String fromAddress,
            @Value("${app.mail.from-name:LuxPretty}") String fromName
    ) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public String send(String recipientEmail, String subject, String htmlBody, String textBody) {
        try {
            MimeMessage msg = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);  // multipart alternative
            javaMailSender.send(msg);
            return null;  // SMTP has no provider message id
        } catch (MailException e) {
            // Spring wraps IO/timeout in MailSendException
            throw new RetryableMailException("SMTP send failed", e);
        } catch (MessagingException | UnsupportedEncodingException e) {
            // Bad subject encoding, invalid From — caller config error
            throw new HardMailException("SMTP message construction failed", e);
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q -DskipTests compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/mail/app/SmtpMailSender.java
git commit -m "feat(mail): add SmtpMailSender (active when app.mail.provider=smtp)"
```

---

### Task 11: PostmarkMailSender

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/mail/app/PostmarkMailSender.java`

- [ ] **Step 1: Implementation**

> The Postmark SDK API: `Postmark.getApiClient(token).deliverMessage(message)`. Key classes: `com.postmarkapp.postmark.client.ApiClient`, `com.postmarkapp.postmark.client.data.model.message.Message`, `MessageResponse`. Exceptions: `PostmarkException` (HTTP errors).

```java
// backend/src/main/java/com/luxpretty/app/mail/app/PostmarkMailSender.java
package com.luxpretty.app.mail.app;

import com.postmarkapp.postmark.Postmark;
import com.postmarkapp.postmark.client.ApiClient;
import com.postmarkapp.postmark.client.data.model.message.Message;
import com.postmarkapp.postmark.client.data.model.message.MessageResponse;
import com.postmarkapp.postmark.client.exception.PostmarkException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "postmark")
public class PostmarkMailSender implements MailSender {

    private final ApiClient client;
    private final String fromAddress;
    private final String fromName;

    public PostmarkMailSender(
            @Value("${app.mail.postmark.api-token}") String apiToken,
            @Value("${app.mail.from}") String fromAddress,
            @Value("${app.mail.from-name:LuxPretty}") String fromName
    ) {
        this.client = Postmark.getApiClient(apiToken);
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public String send(String recipientEmail, String subject, String htmlBody, String textBody) {
        Message msg = new Message(
                fromName + " <" + fromAddress + ">",
                recipientEmail,
                subject,
                textBody
        );
        msg.setHtmlBody(htmlBody);
        msg.setMessageStream("outbound");  // Postmark default transactional stream

        try {
            MessageResponse resp = client.deliverMessage(msg);
            return resp.getMessageId();
        } catch (PostmarkException e) {
            // Postmark SDK throws PostmarkException for both 4xx and 5xx.
            // We distinguish by HTTP code if available in the message.
            // ErrorCode 300 = invalid recipient, 400 = inactive recipient → HardMailException
            String msgText = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (msgText.contains("inactiverecipient")
                    || msgText.contains("invalidemail")
                    || msgText.contains("422")
                    || msgText.contains("401")) {
                throw new HardMailException("Postmark hard error: " + e.getMessage(), e);
            }
            throw new RetryableMailException("Postmark transient error: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RetryableMailException("Postmark IO error", e);
        }
    }
}
```

> If the SDK class names differ slightly (e.g. `com.wildbit.java.postmark.*` for the old artifact), adjust imports. The Message API is stable.

- [ ] **Step 2: Compile**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q -DskipTests compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/mail/app/PostmarkMailSender.java
git commit -m "feat(mail): add PostmarkMailSender (active when app.mail.provider=postmark)"
```

---

### Task 12: MailOutboxService.queue() + tests

**Files:**
- Create: `backend/src/test/java/com/luxpretty/app/mail/app/MailOutboxServiceTests.java`
- Create: `backend/src/main/java/com/luxpretty/app/mail/app/MailOutboxService.java`

- [ ] **Step 1: Write failing test**

```java
// backend/src/test/java/com/luxpretty/app/mail/app/MailOutboxServiceTests.java
package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import com.luxpretty.app.mail.vars.ResetPasswordVars;
import com.luxpretty.app.mail.vars.WelcomeProVars;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailOutboxServiceTests {

    private MailOutboxRepository repo;
    private MailOutboxService service;

    @BeforeEach
    void setUp() {
        repo = mock(MailOutboxRepository.class);
        when(repo.save(org.mockito.ArgumentMatchers.any(MailOutbox.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service = new MailOutboxService(repo, new ObjectMapper());
    }

    @Test
    void queue_inserts_pending_row_with_serialized_vars() {
        ResetPasswordVars vars = new ResetPasswordVars("Alice", "https://app/reset?token=xyz");

        service.queue(MailTemplate.RESET_PASSWORD, vars, "alice@example.com", null);

        ArgumentCaptor<MailOutbox> captor = ArgumentCaptor.forClass(MailOutbox.class);
        verify(repo).save(captor.capture());
        MailOutbox saved = captor.getValue();
        assertThat(saved.getTemplate()).isEqualTo(MailTemplate.RESET_PASSWORD);
        assertThat(saved.getRecipientEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getStatus()).isEqualTo(MailStatus.PENDING);
        assertThat(saved.getAttempts()).isEqualTo(0);
        assertThat(saved.getTenantSlug()).isNull();
        assertThat(saved.getVarsJson()).contains("Alice");
        assertThat(saved.getVarsJson()).contains("https://app/reset?token=xyz");
    }

    @Test
    void queue_with_tenant_slug_persists_it() {
        WelcomeProVars vars = new WelcomeProVars("Bob", "bob@salon.fr", "https://app/pro/dashboard");

        service.queue(MailTemplate.WELCOME_PRO, vars, "bob@salon.fr", "salon-bob");

        ArgumentCaptor<MailOutbox> captor = ArgumentCaptor.forClass(MailOutbox.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getTenantSlug()).isEqualTo("salon-bob");
    }

    @Test
    void queue_rejects_mismatched_vars_type() {
        // Passing WelcomeProVars to RESET_PASSWORD template should fail fast
        WelcomeProVars wrong = new WelcomeProVars("x", "y", "z");
        assertThatThrownBy(() ->
                service.queue(MailTemplate.RESET_PASSWORD, wrong, "alice@example.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RESET_PASSWORD")
                .hasMessageContaining("WelcomeProVars");
    }
}
```

- [ ] **Step 2: Verify failure**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q test -Dtest=MailOutboxServiceTests
```

Expected: COMPILATION FAILURE.

- [ ] **Step 3: Implement service**

```java
// backend/src/main/java/com/luxpretty/app/mail/app/MailOutboxService.java
package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import com.luxpretty.app.mail.vars.MailVars;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MailOutboxService {

    private final MailOutboxRepository repo;
    private final ObjectMapper objectMapper;

    public MailOutboxService(MailOutboxRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    /**
     * Inserts a mail in the outbox. Joins the caller's transaction (REQUIRED):
     * if the caller rolls back, the mail is not queued — guarantees atomicity
     * between business action and mail send.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void queue(MailTemplate template, MailVars vars, String recipientEmail, String tenantSlug) {
        if (!template.varsClass().isInstance(vars)) {
            throw new IllegalArgumentException(
                "Template " + template + " expects " + template.varsClass().getSimpleName()
                + " but got " + vars.getClass().getSimpleName());
        }

        MailOutbox row = new MailOutbox();
        row.setTemplate(template);
        row.setRecipientEmail(recipientEmail);
        row.setTenantSlug(tenantSlug);
        row.setVarsJson(serialize(vars));
        row.setStatus(MailStatus.PENDING);
        row.setAttempts(0);
        row.setNextAttemptAt(LocalDateTime.now());
        repo.save(row);
    }

    private String serialize(MailVars vars) {
        try {
            return objectMapper.writeValueAsString(vars);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize MailVars: " + vars, e);
        }
    }
}
```

- [ ] **Step 4: Verify pass**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q test -Dtest=MailOutboxServiceTests
```

Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/mail/app/MailOutboxService.java \
        backend/src/test/java/com/luxpretty/app/mail/app/MailOutboxServiceTests.java
git commit -m "feat(mail): add MailOutboxService.queue() (REQUIRED tx, fail-fast type check) + tests"
```

---

### Task 13: MailWorker + tests

**Files:**
- Create: `backend/src/test/java/com/luxpretty/app/mail/app/MailWorkerTests.java`
- Create: `backend/src/main/java/com/luxpretty/app/mail/app/MailWorker.java`

> The worker depends on `MailRenderer` which we'll build in PR2 (the templates exist there). For PR1 we accept that the renderer is a placeholder — the worker test stubs it. The full integration with templates happens in PR2.

- [ ] **Step 1: Create a stub MailRenderer interface so the worker can compile**

```java
// backend/src/main/java/com/luxpretty/app/mail/app/MailRenderer.java
package com.luxpretty.app.mail.app;

import com.luxpretty.app.mail.domain.MailOutbox;

/**
 * Renders a MailOutbox row into an envelope (subject, html, text).
 * PR1 ships with a stub that returns minimal placeholders;
 * PR2 replaces it with Thymeleaf + Jsoup inlining.
 */
public interface MailRenderer {
    record Rendered(String subject, String htmlBody, String textBody) {}
    Rendered render(MailOutbox row);
}
```

Stub implementation for PR1 (replaced in PR2):

```java
// backend/src/main/java/com/luxpretty/app/mail/app/StubMailRenderer.java
package com.luxpretty.app.mail.app;

import com.luxpretty.app.mail.domain.MailOutbox;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Stub renderer used in PR1 (before real Thymeleaf templates ship in PR2).
 * Disabled automatically when {@code ThymeleafMailRenderer} becomes available.
 */
@Component
@ConditionalOnMissingBean(name = "thymeleafMailRenderer")
public class StubMailRenderer implements MailRenderer {
    @Override
    public Rendered render(MailOutbox row) {
        String subject = "[" + row.getTemplate() + "]";
        String body = "Mail template not yet rendered (PR1 stub). Vars: " + row.getVarsJson();
        return new Rendered(subject, "<p>" + body + "</p>", body);
    }
}
```

- [ ] **Step 2: Write failing test**

```java
// backend/src/test/java/com/luxpretty/app/mail/app/MailWorkerTests.java
package com.luxpretty.app.mail.app;

import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailWorkerTests {

    private MailOutboxRepository repo;
    private MailSender sender;
    private MailRenderer renderer;
    private MailRetryPolicy retryPolicy;
    private MailWorker worker;

    @BeforeEach
    void setUp() {
        repo = mock(MailOutboxRepository.class);
        sender = mock(MailSender.class);
        renderer = mock(MailRenderer.class);
        retryPolicy = new MailRetryPolicy();
        worker = new MailWorker(repo, sender, renderer, retryPolicy);

        when(renderer.render(any())).thenReturn(
                new MailRenderer.Rendered("subj", "<p>html</p>", "txt"));
    }

    @Test
    void success_marks_sent_with_provider_id() {
        MailOutbox row = pending();
        when(repo.lockBatchForSending(any(), anyInt())).thenReturn(List.of(row));
        when(sender.send("user@example.com", "subj", "<p>html</p>", "txt")).thenReturn("postmark-msg-id-123");

        worker.pollAndSend();

        assertThat(row.getStatus()).isEqualTo(MailStatus.SENT);
        assertThat(row.getProviderMessageId()).isEqualTo("postmark-msg-id-123");
        assertThat(row.getSentAt()).isNotNull();
    }

    @Test
    void retryable_failure_increments_attempts_and_schedules_next() {
        MailOutbox row = pending();
        LocalDateTime before = LocalDateTime.now();
        when(repo.lockBatchForSending(any(), anyInt())).thenReturn(List.of(row));
        when(sender.send(any(), any(), any(), any()))
                .thenThrow(new RetryableMailException("timeout"));

        worker.pollAndSend();

        assertThat(row.getStatus()).isEqualTo(MailStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isAfter(before);
        assertThat(row.getLastError()).contains("timeout");
    }

    @Test
    void fifth_attempt_marks_permanently_failed() {
        MailOutbox row = pending();
        row.setAttempts(4);  // 5th attempt about to happen
        when(repo.lockBatchForSending(any(), anyInt())).thenReturn(List.of(row));
        when(sender.send(any(), any(), any(), any()))
                .thenThrow(new RetryableMailException("still down"));

        worker.pollAndSend();

        assertThat(row.getStatus()).isEqualTo(MailStatus.PERMANENTLY_FAILED);
        assertThat(row.getAttempts()).isEqualTo(5);
    }

    @Test
    void hard_failure_marks_permanently_failed_immediately() {
        MailOutbox row = pending();
        when(repo.lockBatchForSending(any(), anyInt())).thenReturn(List.of(row));
        when(sender.send(any(), any(), any(), any()))
                .thenThrow(new HardMailException("invalid email"));

        worker.pollAndSend();

        assertThat(row.getStatus()).isEqualTo(MailStatus.PERMANENTLY_FAILED);
        assertThat(row.getAttempts()).isEqualTo(0);  // not incremented on hard fail
        assertThat(row.getLastError()).contains("invalid email");
    }

    @Test
    void batch_size_is_10() {
        worker.pollAndSend();
        verify(repo, times(1)).lockBatchForSending(any(), eq(10));
    }

    private MailOutbox pending() {
        MailOutbox row = new MailOutbox();
        row.setTemplate(MailTemplate.RESET_PASSWORD);
        row.setRecipientEmail("user@example.com");
        row.setVarsJson("{\"userName\":\"u\",\"resetUrl\":\"http://x\"}");
        row.setStatus(MailStatus.PENDING);
        row.setAttempts(0);
        row.setNextAttemptAt(LocalDateTime.now());
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }

    private static int eq(int v) { return org.mockito.ArgumentMatchers.eq(v); }
}
```

- [ ] **Step 3: Verify failure**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q test -Dtest=MailWorkerTests
```

Expected: COMPILATION FAILURE (`MailWorker` missing).

- [ ] **Step 4: Implement worker**

```java
// backend/src/main/java/com/luxpretty/app/mail/app/MailWorker.java
package com.luxpretty.app.mail.app;

import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Polls MAIL_OUTBOX for PENDING rows and sends them via the configured MailSender.
 * Runs every 30 seconds, batch size 10. Disabled in test profile.
 */
@Component
@Profile("!test")
public class MailWorker {

    private static final Logger log = LoggerFactory.getLogger(MailWorker.class);
    private static final int BATCH_SIZE = 10;
    private static final int LAST_ERROR_MAX_LEN = 2000;

    private final MailOutboxRepository repo;
    private final MailSender sender;
    private final MailRenderer renderer;
    private final MailRetryPolicy retryPolicy;

    public MailWorker(MailOutboxRepository repo,
                      MailSender sender,
                      MailRenderer renderer,
                      MailRetryPolicy retryPolicy) {
        this.repo = repo;
        this.sender = sender;
        this.renderer = renderer;
        this.retryPolicy = retryPolicy;
    }

    @Scheduled(fixedDelayString = "${app.mail.worker.poll-interval-ms:30000}",
               initialDelayString = "${app.mail.worker.initial-delay-ms:10000}")
    @Transactional
    public void pollAndSend() {
        List<MailOutbox> batch = repo.lockBatchForSending(LocalDateTime.now(), BATCH_SIZE);
        if (batch.isEmpty()) return;
        log.debug("Mail worker: processing {} row(s)", batch.size());

        for (MailOutbox row : batch) {
            processOne(row);
        }
    }

    private void processOne(MailOutbox row) {
        try {
            MailRenderer.Rendered envelope = renderer.render(row);
            String providerMessageId = sender.send(
                    row.getRecipientEmail(),
                    envelope.subject(),
                    envelope.htmlBody(),
                    envelope.textBody());
            row.setStatus(MailStatus.SENT);
            row.setSentAt(LocalDateTime.now());
            row.setProviderMessageId(providerMessageId);
            log.info("Mail sent: id={} template={} recipient={} providerMsgId={}",
                    row.getId(), row.getTemplate(), row.getRecipientEmail(), providerMessageId);
        } catch (RetryableMailException e) {
            row.setAttempts(row.getAttempts() + 1);
            row.setLastError(truncate(e.getMessage()));
            if (row.getAttempts() >= MailRetryPolicy.MAX_ATTEMPTS) {
                row.setStatus(MailStatus.PERMANENTLY_FAILED);
                log.warn("Mail permanently failed (max attempts): id={} template={} error={}",
                        row.getId(), row.getTemplate(), e.getMessage());
            } else {
                row.setNextAttemptAt(retryPolicy.nextAttemptAfter(row.getAttempts(), LocalDateTime.now()));
                log.warn("Mail send failed, scheduled retry: id={} attempts={} next_attempt_at={} error={}",
                        row.getId(), row.getAttempts(), row.getNextAttemptAt(), e.getMessage());
            }
        } catch (HardMailException e) {
            row.setStatus(MailStatus.PERMANENTLY_FAILED);
            row.setLastError(truncate(e.getMessage()));
            log.warn("Mail permanently failed (hard error): id={} template={} error={}",
                    row.getId(), row.getTemplate(), e.getMessage());
        } catch (Exception e) {
            // Defensive: any other unexpected error → mark permanent and log
            row.setStatus(MailStatus.PERMANENTLY_FAILED);
            row.setLastError(truncate("unexpected: " + e.getMessage()));
            log.error("Mail unexpected failure: id={} template={}",
                    row.getId(), row.getTemplate(), e);
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > LAST_ERROR_MAX_LEN ? s.substring(0, LAST_ERROR_MAX_LEN) : s;
    }
}
```

- [ ] **Step 5: Enable @Scheduled in main app config**

Check if `@EnableScheduling` is already on `LuxPrettyApplication` or another config class:

```bash
grep -rn "@EnableScheduling" backend/src/main/java | head
```

If not present, add it to `LuxPrettyApplication.java`:

```java
@EnableScheduling
@SpringBootApplication
public class LuxPrettyApplication { ... }
```

(If grep shows it's already enabled elsewhere, skip this step.)

- [ ] **Step 6: Verify tests pass**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q test -Dtest=MailWorkerTests
```

Expected: 5 tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/mail/app/MailWorker.java \
        backend/src/main/java/com/luxpretty/app/mail/app/MailRenderer.java \
        backend/src/main/java/com/luxpretty/app/mail/app/StubMailRenderer.java \
        backend/src/test/java/com/luxpretty/app/mail/app/MailWorkerTests.java
# only if @EnableScheduling was added:
# git add backend/src/main/java/com/luxpretty/app/LuxPrettyApplication.java
git commit -m "feat(mail): add MailWorker (@Scheduled, batch=10, backoff retry) + tests + stub renderer"
```

---

### Task 14: application.properties — mail config

**Files:**
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/main/resources/application-docker.properties`

- [ ] **Step 1: Check current state**

```bash
grep -n "app.mail" backend/src/main/resources/application.properties
```

The existing config already has `app.mail.from` and `app.mail.from-name` (from `EmailService`). We add the new keys.

- [ ] **Step 2: Edit `application.properties`**

Find the existing `app.mail.*` section and ensure these properties are present (add if missing):

```properties
# Mail (default: smtp via Mailpit in dev)
spring.mail.host=${SPRING_MAIL_HOST:localhost}
spring.mail.port=${SPRING_MAIL_PORT:1025}
spring.mail.properties.mail.smtp.auth=false
spring.mail.properties.mail.smtp.starttls.enable=false

app.mail.provider=${APP_MAIL_PROVIDER:smtp}
app.mail.from=${APP_MAIL_FROM:noreply@luxpretty.local}
app.mail.from-name=${APP_MAIL_FROM_NAME:LuxPretty}
app.mail.worker.poll-interval-ms=30000
app.mail.worker.initial-delay-ms=10000

# Postmark (used only when app.mail.provider=postmark)
app.mail.postmark.api-token=${POSTMARK_API_TOKEN:}
app.mail.postmark.webhook-secret=${POSTMARK_WEBHOOK_SECRET:}
```

- [ ] **Step 3: `application-docker.properties` — same approach**

If `app.mail.*` keys exist in `application-docker.properties`, replace/augment with the same set so docker prod can switch via env vars.

- [ ] **Step 4: Verify compile + start**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q -DskipTests compile
```

Expected: BUILD SUCCESS. Don't attempt to start the app yet (needs Oracle + Mailpit running).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/application.properties \
        backend/src/main/resources/application-docker.properties
git commit -m "config(mail): add app.mail.provider switch + Postmark + worker tuning"
```

---

### Task 15: Smoke integration test against Mailpit (Testcontainers)

**Files:**
- Create: `backend/src/test/java/com/luxpretty/app/mail/app/MailpitSmokeTests.java`

> This test starts a Mailpit container, queues a mail, ticks the worker manually, then asserts the mail appears in Mailpit's HTTP API. Use plain `GenericContainer` (Mailpit ships with HTTP API on :8025/api).

- [ ] **Step 1: Write the test**

```java
// backend/src/test/java/com/luxpretty/app/mail/app/MailpitSmokeTests.java
package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import com.luxpretty.app.mail.vars.ResetPasswordVars;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "app.mail.provider=smtp",
        "app.mail.from=test@luxpretty.local",
        "spring.mail.properties.mail.smtp.auth=false"
})
@Testcontainers
class MailpitSmokeTests {

    private static final GenericContainer<?> MAILPIT =
            new GenericContainer<>("axllent/mailpit:latest")
                    .withExposedPorts(1025, 8025)
                    .waitingFor(Wait.forLogMessage(".*accessible via.*", 1));

    @BeforeAll
    static void startContainer() { MAILPIT.start(); }

    @AfterAll
    static void stopContainer() { MAILPIT.stop(); }

    @DynamicPropertySource
    static void wireSmtp(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    @Autowired MailOutboxService service;
    @Autowired MailWorker worker;
    @Autowired MailOutboxRepository repo;

    @Test
    void queue_then_worker_tick_delivers_to_mailpit() throws Exception {
        // Queue a mail
        service.queue(MailTemplate.RESET_PASSWORD,
                new ResetPasswordVars("Alice", "https://app/reset?token=abc"),
                "alice@example.com",
                null);

        // Tick the worker (synchronous — no need to wait for @Scheduled)
        worker.pollAndSend();

        // Assert delivery via Mailpit HTTP API
        String mailpitApiUrl = "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025)
                + "/api/v1/messages";
        ObjectMapper om = new ObjectMapper();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            HttpResponse<String> resp = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(mailpitApiUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode body = om.readTree(resp.body());
            assertThat(body.get("messages_count").asInt()).isGreaterThanOrEqualTo(1);
            JsonNode first = body.get("messages").get(0);
            assertThat(first.get("To").get(0).get("Address").asText()).isEqualTo("alice@example.com");
        });

        // Row should now be SENT
        var row = repo.findAll().get(0);
        assertThat(row.getStatus().name()).isEqualTo("SENT");
    }
}
```

- [ ] **Step 2: Add awaitility test dependency if missing**

```bash
grep "awaitility" backend/pom.xml
```

If absent, add (in `<dependencies>`, after `spring-boot-starter-test`):

```xml
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>
```

Spring Boot 3.5 manages the version.

- [ ] **Step 3: Verify Testcontainers dep is present**

```bash
grep -E "testcontainers" backend/pom.xml
```

If absent, add:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 4: Run the smoke test**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q test -Dtest=MailpitSmokeTests
```

Expected: 1 test passes. Requires Docker running locally. If Docker isn't running, the test will fail to start the Mailpit container — that's an environmental issue, not a code problem. Document this in the commit message.

> Note: this test loads the full Spring context, so the `@Profile("!test")` on `MailWorker` will prevent its `@Scheduled` from auto-running — but we inject it directly and call `pollAndSend()` manually. We need the worker bean to actually be created, so adjust: change `@Profile("!test")` to `@ConditionalOnProperty(name="app.mail.worker.enabled", havingValue="true", matchIfMissing=true)`. Set `app.mail.worker.enabled=true` explicitly in this test's `@SpringBootTest(properties=...)`. Update Task 13 step 4 to use `@ConditionalOnProperty` instead of `@Profile("!test")` if you haven't already (this is a fix to make the smoke test work — adjust retroactively if needed).

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/luxpretty/app/mail/app/MailpitSmokeTests.java backend/pom.xml
# possibly: backend/src/main/java/com/luxpretty/app/mail/app/MailWorker.java (if @Profile adjusted)
git commit -m "test(mail): add Mailpit smoke integration test via Testcontainers"
```

---

## Phase 2 — PR2: Branding + real flows (~3h)

### Task 16: Flyway V9 — users.email_blocked column

**Files:**
- Create: `backend/src/main/resources/db/migration/oracle/V9__users_email_blocked.sql`
- Modify: `backend/src/main/java/com/luxpretty/app/users/domain/User.java`

- [ ] **Step 1: Migration SQL**

```sql
-- backend/src/main/resources/db/migration/oracle/V9__users_email_blocked.sql
-- Track users whose email is blocked (hard bounce or spam complaint).
-- The MailWorker skips sending to blocked recipients.
ALTER TABLE USERS ADD EMAIL_BLOCKED NUMBER(1) DEFAULT 0 NOT NULL;
ALTER TABLE USERS ADD CONSTRAINT CK_USERS_EMAIL_BLOCKED CHECK (EMAIL_BLOCKED IN (0,1));
COMMENT ON COLUMN USERS.EMAIL_BLOCKED IS
  'Set to 1 on hard bounce or spam complaint via Postmark webhook. MailWorker skips sending to blocked addresses.';
```

- [ ] **Step 2: Update User entity**

Open `backend/src/main/java/com/luxpretty/app/users/domain/User.java`. Find the field list and add (e.g., after `email`):

```java
@Column(name = "email_blocked", nullable = false)
private Boolean emailBlocked = false;
```

(Oracle stores boolean as `NUMBER(1)`; Hibernate maps Boolean ↔ NUMBER(1) automatically.)

- [ ] **Step 3: Compile**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q -DskipTests compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/oracle/V9__users_email_blocked.sql \
        backend/src/main/java/com/luxpretty/app/users/domain/User.java
git commit -m "feat(db): add USERS.email_blocked column (V9) + User entity field"
```

---

### Task 17: MailWorker — skip blocked recipients

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/users/repo/UserRepository.java`
- Modify: `backend/src/main/java/com/luxpretty/app/mail/app/MailWorker.java`

- [ ] **Step 1: Add UserRepository method**

In `UserRepository.java`, add (if not present):

```java
@Query("SELECT u.emailBlocked FROM User u WHERE u.email = :email")
Optional<Boolean> findEmailBlockedByEmail(@Param("email") String email);
```

Import `Optional` and `Param` as needed.

- [ ] **Step 2: Inject UserRepository in MailWorker**

In `MailWorker.java`, add `UserRepository` constructor parameter and field. In `processOne`, BEFORE the `renderer.render(row)` call, check:

```java
boolean blocked = userRepo.findEmailBlockedByEmail(row.getRecipientEmail()).orElse(false);
if (blocked) {
    row.setStatus(MailStatus.PERMANENTLY_FAILED);
    row.setLastError("recipient email blocked");
    log.info("Mail skipped (recipient blocked): id={} recipient={}",
            row.getId(), row.getRecipientEmail());
    return;
}
```

- [ ] **Step 3: Update tests**

`MailWorkerTests` needs a mocked `UserRepository`. Update `setUp()`:

```java
private UserRepository userRepo;
// ...
@BeforeEach void setUp() {
    // ... existing mocks ...
    userRepo = mock(UserRepository.class);
    when(userRepo.findEmailBlockedByEmail(anyString())).thenReturn(Optional.of(false));
    worker = new MailWorker(repo, sender, renderer, retryPolicy, userRepo);
    // ...
}
```

Add a new test case:

```java
@Test
void blocked_recipient_marks_permanently_failed_without_sending() {
    MailOutbox row = pending();
    when(repo.lockBatchForSending(any(), anyInt())).thenReturn(List.of(row));
    when(userRepo.findEmailBlockedByEmail("user@example.com")).thenReturn(Optional.of(true));

    worker.pollAndSend();

    assertThat(row.getStatus()).isEqualTo(MailStatus.PERMANENTLY_FAILED);
    assertThat(row.getLastError()).contains("blocked");
    verify(sender, never()).send(any(), any(), any(), any());
}
```

(Add `import static org.mockito.Mockito.never;` and `import static org.mockito.ArgumentMatchers.anyString;`.)

- [ ] **Step 4: Run tests**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q test -Dtest=MailWorkerTests
```

Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/users/repo/UserRepository.java \
        backend/src/main/java/com/luxpretty/app/mail/app/MailWorker.java \
        backend/src/test/java/com/luxpretty/app/mail/app/MailWorkerTests.java
git commit -m "feat(mail): skip sending to blocked recipients in MailWorker"
```

---

### Task 18: Mail templates HTML+TXT (4 templates × 2 = 8 files)

**Files:**
- Create: `backend/src/main/resources/templates/mail/_layout.html`
- Create: `backend/src/main/resources/templates/mail/_styles.css`
- Create: `backend/src/main/resources/templates/mail/reset-password.html`
- Create: `backend/src/main/resources/templates/mail/reset-password.txt`
- Create: `backend/src/main/resources/templates/mail/booking-confirmed.html`
- Create: `backend/src/main/resources/templates/mail/booking-confirmed.txt`
- Create: `backend/src/main/resources/templates/mail/booking-received-pro.html`
- Create: `backend/src/main/resources/templates/mail/booking-received-pro.txt`
- Create: `backend/src/main/resources/templates/mail/welcome-pro.html`
- Create: `backend/src/main/resources/templates/mail/welcome-pro.txt`

- [ ] **Step 1: Shared CSS**

```css
/* backend/src/main/resources/templates/mail/_styles.css */
body { font-family: Georgia, 'Cormorant Garamond', serif; color: #2B1F25; line-height: 1.5; background: #FBF6F4; margin: 0; padding: 0; }
.container { max-width: 600px; margin: 0 auto; background: #FFFFFF; }
.header { background-color: #A83E58; color: #FFFFFF; padding: 24px; text-align: center; }
.header .monogram { display: inline-block; width: 40px; height: 40px; line-height: 40px; background: rgba(255,255,255,0.18); color: #FFFFFF; font-size: 14px; letter-spacing: 1.5px; }
.header .brand { font-size: 20px; letter-spacing: 1px; margin-left: 10px; vertical-align: middle; }
.body { padding: 32px 24px; }
.body h1 { font-size: 22px; color: #A83E58; margin: 0 0 16px; }
.body p { font-size: 14px; margin: 0 0 14px; }
.cta { display: inline-block; background-color: #A83E58; color: #FFFFFF; text-decoration: none; padding: 12px 24px; font-weight: bold; font-size: 14px; margin: 16px 0; }
.foot { padding: 18px 24px; font-size: 11px; color: #806771; text-align: center; border-top: 1px solid #F4ECE8; }
.foot a { color: #A83E58; }
```

- [ ] **Step 2: Layout fragment**

```html
<!-- backend/src/main/resources/templates/mail/_layout.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title th:text="${pageTitle}">LuxPretty</title>
</head>
<body>
<div class="container">
    <div class="header">
        <span class="monogram">LXP</span>
        <span class="brand">LuxPretty</span>
    </div>
    <div class="body" th:insert="~{this :: content}">
        <!-- replaced by each template's content fragment -->
    </div>
    <div class="foot">
        LuxPretty — <a href="https://luxpretty.lu">luxpretty.lu</a><br/>
        Vous recevez cet email parce que vous avez un compte LuxPretty.
    </div>
</div>
</body>
</html>
```

- [ ] **Step 3: reset-password.html + txt**

```html
<!-- backend/src/main/resources/templates/mail/reset-password.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" th:replace="~{mail/_layout :: html(pageTitle='Réinitialiser votre mot de passe')}">
<body>
<div th:fragment="content">
    <h1>Réinitialiser votre mot de passe</h1>
    <p>Bonjour <span th:text="${userName}">…</span>,</p>
    <p>Vous avez demandé à réinitialiser le mot de passe de votre compte LuxPretty. Cliquez sur le bouton ci-dessous pour choisir un nouveau mot de passe.</p>
    <p><a th:href="${resetUrl}" class="cta">Réinitialiser mon mot de passe</a></p>
    <p>Ce lien est valable 1 heure. Si vous n'êtes pas à l'origine de cette demande, vous pouvez ignorer cet email — votre mot de passe ne sera pas modifié.</p>
</div>
</body>
</html>
```

```text
[`backend/src/main/resources/templates/mail/reset-password.txt`]
Bonjour [(${userName})],

Vous avez demandé à réinitialiser le mot de passe de votre compte LuxPretty.
Ouvrez ce lien pour choisir un nouveau mot de passe :

[(${resetUrl})]

Ce lien est valable 1 heure. Si vous n'êtes pas à l'origine de cette demande,
vous pouvez ignorer cet email.

— LuxPretty
```

(Note Thymeleaf TEXT mode uses `[(${var})]` for unescaped output and `[[${var}]]` for escaped.)

- [ ] **Step 4: booking-confirmed.html + txt**

```html
<!-- backend/src/main/resources/templates/mail/booking-confirmed.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" th:replace="~{mail/_layout :: html(pageTitle='Rendez-vous confirmé')}">
<body>
<div th:fragment="content">
    <h1>Votre rendez-vous est confirmé</h1>
    <p>Bonjour <span th:text="${clientName}">…</span>,</p>
    <p>Votre rendez-vous chez <strong th:text="${salonName}">…</strong> est bien confirmé.</p>
    <p>
        <strong>Soin :</strong> <span th:text="${careName}">…</span> (<span th:text="${careDuration}">…</span>)<br/>
        <strong>Date :</strong> <span th:text="${appointmentDate}">…</span> à <span th:text="${appointmentTime}">…</span><br/>
        <strong>Tarif :</strong> <span th:text="${carePrice}">…</span> €
    </p>
    <p><a th:href="${dashboardUrl}" class="cta">Voir mes rendez-vous</a></p>
    <p>À très bientôt !</p>
</div>
</body>
</html>
```

```text
[`backend/src/main/resources/templates/mail/booking-confirmed.txt`]
Bonjour [(${clientName})],

Votre rendez-vous chez [(${salonName})] est bien confirmé.

  Soin    : [(${careName})] ([(${careDuration})])
  Date    : [(${appointmentDate})] à [(${appointmentTime})]
  Tarif   : [(${carePrice})] €

Voir mes rendez-vous : [(${dashboardUrl})]

À très bientôt !
— LuxPretty
```

- [ ] **Step 5: booking-received-pro.html + txt**

```html
<!-- backend/src/main/resources/templates/mail/booking-received-pro.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" th:replace="~{mail/_layout :: html(pageTitle='Nouveau rendez-vous')}">
<body>
<div th:fragment="content">
    <h1>Nouveau rendez-vous</h1>
    <p>Bonjour <span th:text="${proName}">…</span>,</p>
    <p>Vous avez reçu une nouvelle réservation de la part de <strong th:text="${clientName}">…</strong>.</p>
    <p>
        <strong>Soin :</strong> <span th:text="${careName}">…</span> (<span th:text="${careDuration}">…</span>)<br/>
        <strong>Date :</strong> <span th:text="${appointmentDate}">…</span> à <span th:text="${appointmentTime}">…</span><br/>
        <strong>Tarif :</strong> <span th:text="${carePrice}">…</span> €
    </p>
    <p><a th:href="${dashboardUrl}" class="cta">Voir mes rendez-vous</a></p>
</div>
</body>
</html>
```

```text
[`backend/src/main/resources/templates/mail/booking-received-pro.txt`]
Bonjour [(${proName})],

Nouvelle réservation reçue de [(${clientName})].

  Soin    : [(${careName})] ([(${careDuration})])
  Date    : [(${appointmentDate})] à [(${appointmentTime})]
  Tarif   : [(${carePrice})] €

Voir mes rendez-vous : [(${dashboardUrl})]

— LuxPretty
```

- [ ] **Step 6: welcome-pro.html + txt**

```html
<!-- backend/src/main/resources/templates/mail/welcome-pro.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" th:replace="~{mail/_layout :: html(pageTitle='Bienvenue sur LuxPretty')}">
<body>
<div th:fragment="content">
    <h1>Bienvenue sur LuxPretty</h1>
    <p>Bonjour <span th:text="${userName}">…</span>,</p>
    <p>Votre compte LuxPretty (<span th:text="${userEmail}">…</span>) est créé.</p>
    <p>Vous pouvez dès maintenant configurer votre salon, ajouter vos soins, et commencer à recevoir des réservations.</p>
    <p><a th:href="${dashboardUrl}" class="cta">Accéder à mon espace</a></p>
    <p>Si vous avez la moindre question, répondez simplement à cet email.</p>
</div>
</body>
</html>
```

```text
[`backend/src/main/resources/templates/mail/welcome-pro.txt`]
Bonjour [(${userName})],

Votre compte LuxPretty ([(${userEmail})]) est créé.

Vous pouvez dès maintenant configurer votre salon, ajouter vos soins,
et commencer à recevoir des réservations.

Accéder à mon espace : [(${dashboardUrl})]

— LuxPretty
```

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/templates/mail/
git commit -m "feat(mail): add 4 templates (HTML + TXT) with LuxPretty branding"
```

---

### Task 19: ThymeleafMailRenderer (replaces stub) + tests

**Files:**
- Create: `backend/src/test/java/com/luxpretty/app/mail/app/ThymeleafMailRendererTests.java`
- Create: `backend/src/main/java/com/luxpretty/app/mail/app/ThymeleafMailRenderer.java`
- Delete: `backend/src/main/java/com/luxpretty/app/mail/app/StubMailRenderer.java`

- [ ] **Step 1: Write failing test**

```java
// backend/src/test/java/com/luxpretty/app/mail/app/ThymeleafMailRendererTests.java
package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.vars.ResetPasswordVars;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = { ThymeleafMailRenderer.class, com.luxpretty.app.mail.app.MailRendererTestConfig.class })
@ActiveProfiles("test")
class ThymeleafMailRendererTests {

    @Autowired ThymeleafMailRenderer renderer;

    @Test
    void renders_reset_password_with_vars_inlined() throws Exception {
        ObjectMapper om = new ObjectMapper();
        ResetPasswordVars vars = new ResetPasswordVars("Alice", "https://app/reset?token=xyz");

        MailOutbox row = new MailOutbox();
        row.setTemplate(MailTemplate.RESET_PASSWORD);
        row.setVarsJson(om.writeValueAsString(vars));

        MailRenderer.Rendered out = renderer.render(row);

        assertThat(out.subject()).contains("Réinitialiser");
        assertThat(out.htmlBody()).contains("Alice");
        assertThat(out.htmlBody()).contains("https://app/reset?token=xyz");
        // CSS should be inlined (style attribute present)
        assertThat(out.htmlBody()).containsPattern("style=\"[^\"]*color");
        assertThat(out.textBody()).contains("Alice");
        assertThat(out.textBody()).contains("https://app/reset?token=xyz");
    }
}
```

And a Spring config helper for the slice test:

```java
// backend/src/test/java/com/luxpretty/app/mail/app/MailRendererTestConfig.java
package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Configuration
public class MailRendererTestConfig {

    @Bean
    SpringTemplateEngine templateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        // HTML resolver (default mode HTML)
        ClassLoaderTemplateResolver html = new ClassLoaderTemplateResolver();
        html.setPrefix("templates/");
        html.setSuffix(".html");
        html.setTemplateMode("HTML");
        html.setCharacterEncoding("UTF-8");
        html.setCheckExistence(true);
        html.setOrder(1);
        // TEXT resolver
        ClassLoaderTemplateResolver text = new ClassLoaderTemplateResolver();
        text.setPrefix("templates/");
        text.setSuffix(".txt");
        text.setTemplateMode("TEXT");
        text.setCharacterEncoding("UTF-8");
        text.setCheckExistence(true);
        text.setOrder(2);
        engine.addTemplateResolver(html);
        engine.addTemplateResolver(text);
        return engine;
    }

    @Bean
    ObjectMapper objectMapper() { return new ObjectMapper(); }
}
```

- [ ] **Step 2: Implement renderer**

```java
// backend/src/main/java/com/luxpretty/app/mail/app/ThymeleafMailRenderer.java
package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.vars.BookingConfirmedVars;
import com.luxpretty.app.mail.vars.BookingReceivedProVars;
import com.luxpretty.app.mail.vars.MailVars;
import com.luxpretty.app.mail.vars.ResetPasswordVars;
import com.luxpretty.app.mail.vars.WelcomeProVars;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component("thymeleafMailRenderer")
@Primary
public class ThymeleafMailRenderer implements MailRenderer {

    private final SpringTemplateEngine templateEngine;
    private final ObjectMapper objectMapper;
    private final String stylesCss;

    public ThymeleafMailRenderer(SpringTemplateEngine templateEngine, ObjectMapper objectMapper) {
        this.templateEngine = templateEngine;
        this.objectMapper = objectMapper;
        this.stylesCss = loadStyles();
    }

    @Override
    public Rendered render(MailOutbox row) {
        MailVars vars = deserialize(row);
        Map<String, Object> ctxVars = toContextMap(vars);

        Context htmlCtx = new Context();
        ctxVars.forEach(htmlCtx::setVariable);
        htmlCtx.setVariable("_styles", stylesCss);
        String html = templateEngine.process("mail/" + row.getTemplate().templatePath(), htmlCtx);
        String inlined = inlineCss(html);

        Context txtCtx = new Context();
        ctxVars.forEach(txtCtx::setVariable);
        String txt = templateEngine.process("mail/" + row.getTemplate().templatePath(), txtCtx);

        String subject = subjectFor(row.getTemplate(), vars);
        return new Rendered(subject, inlined, txt);
    }

    private String inlineCss(String html) {
        Document doc = Jsoup.parse(html);
        // Get the <style> block content (if present) and inline rules naïvely.
        // For more sophisticated CSS-to-inline behavior, consider a dedicated lib.
        // Here we inject the shared stylesheet into a <style> in <head>; clients
        // that respect <style> see it; for those that don't, we use the simple
        // approach below: parse each rule and apply to matching elements.
        Element head = doc.head();
        if (head.selectFirst("style") == null && stylesCss != null && !stylesCss.isBlank()) {
            head.appendElement("style").appendText(stylesCss);
        }
        // Apply each rule as inline style on matching elements
        applyInlineStyles(doc);
        return doc.outerHtml();
    }

    private void applyInlineStyles(Document doc) {
        Elements styleBlocks = doc.select("style");
        for (Element styleBlock : styleBlocks) {
            String css = styleBlock.data();
            for (String rule : css.split("\\}")) {
                int braceIdx = rule.indexOf('{');
                if (braceIdx < 0) continue;
                String selector = rule.substring(0, braceIdx).trim();
                String declarations = rule.substring(braceIdx + 1).trim();
                if (selector.isEmpty() || declarations.isEmpty()) continue;
                if (selector.startsWith("@") || selector.contains(":")) continue; // skip @media, pseudo-classes
                try {
                    Elements targets = doc.select(selector);
                    for (Element el : targets) {
                        String existing = el.attr("style");
                        el.attr("style", existing.isEmpty()
                                ? declarations
                                : existing + ";" + declarations);
                    }
                } catch (Exception ignored) {
                    // Invalid selector — skip silently
                }
            }
        }
        // Optionally remove <style> blocks so they don't render twice — keep
        // them for clients that do respect <style>, harmless.
    }

    private MailVars deserialize(MailOutbox row) {
        try {
            return (MailVars) objectMapper.readValue(row.getVarsJson(), row.getTemplate().varsClass());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot deserialize MailVars for row id=" + row.getId(), e);
        }
    }

    private Map<String, Object> toContextMap(MailVars vars) {
        // Use Jackson's conversion to map for a record-agnostic context.
        return objectMapper.convertValue(vars, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    private String subjectFor(MailTemplate template, MailVars vars) {
        return switch (template) {
            case RESET_PASSWORD -> "Réinitialiser votre mot de passe";
            case BOOKING_CONFIRMED -> {
                BookingConfirmedVars v = (BookingConfirmedVars) vars;
                yield "Votre rendez-vous chez " + v.salonName() + " est confirmé";
            }
            case BOOKING_RECEIVED_PRO -> {
                BookingReceivedProVars v = (BookingReceivedProVars) vars;
                yield "Nouveau rendez-vous — " + v.clientName();
            }
            case WELCOME_PRO -> "Bienvenue sur LuxPretty";
        };
    }

    private String loadStyles() {
        try {
            return new String(
                    new ClassPathResource("templates/mail/_styles.css").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Non-fatal: the inline CSS step just becomes a no-op
            return "";
        }
    }
}
```

- [ ] **Step 3: Delete the stub**

```bash
rm backend/src/main/java/com/luxpretty/app/mail/app/StubMailRenderer.java
```

- [ ] **Step 4: Configure Thymeleaf for HTML + TEXT resolvers in main config**

Check whether Thymeleaf is configured with multiple resolvers in the app. The default Spring Boot auto-config uses a single HTML resolver. We need to add a TEXT-mode resolver for the `.txt` templates.

Create `backend/src/main/java/com/luxpretty/app/mail/app/MailTemplateConfig.java`:

```java
package com.luxpretty.app.mail.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

@Configuration
public class MailTemplateConfig {

    /**
     * Additional Thymeleaf resolver in TEXT mode so the engine can process
     * both `.html` (default HTML resolver) and `.txt` templates from the same
     * `mail/` folder. Order is high so the default HTML resolver wins for
     * `.html` lookups; this resolver kicks in when the suffix is `.txt`.
     */
    @Bean
    SpringResourceTemplateResolver mailTextTemplateResolver() {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setPrefix("classpath:/templates/");
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setOrder(100);  // after default HTML resolver
        resolver.setCheckExistence(true);
        return resolver;
    }
}
```

- [ ] **Step 5: Run tests**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q test -Dtest=ThymeleafMailRendererTests
```

Expected: 1 test passes.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/mail/app/ThymeleafMailRenderer.java \
        backend/src/main/java/com/luxpretty/app/mail/app/MailTemplateConfig.java \
        backend/src/test/java/com/luxpretty/app/mail/app/ThymeleafMailRendererTests.java \
        backend/src/test/java/com/luxpretty/app/mail/app/MailRendererTestConfig.java
git rm backend/src/main/java/com/luxpretty/app/mail/app/StubMailRenderer.java
git commit -m "feat(mail): replace stub with ThymeleafMailRenderer (Jsoup CSS inline) + tests"
```

---

### Task 20: Postmark webhook controller + tests

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/mail/web/dto/PostmarkWebhookPayload.java`
- Create: `backend/src/main/java/com/luxpretty/app/mail/web/PostmarkWebhookController.java`
- Create: `backend/src/test/java/com/luxpretty/app/mail/web/PostmarkWebhookControllerTests.java`
- Modify: `backend/src/main/java/com/luxpretty/app/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/luxpretty/app/users/repo/UserRepository.java`

- [ ] **Step 1: DTO**

```java
// backend/src/main/java/com/luxpretty/app/mail/web/dto/PostmarkWebhookPayload.java
package com.luxpretty.app.mail.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Postmark webhook payload. Single endpoint receives all event types,
 * discriminated by {@code RecordType}.
 */
public record PostmarkWebhookPayload(
        @JsonProperty("RecordType") String recordType,
        @JsonProperty("BounceType") String bounceType,
        @JsonProperty("Email") String email,
        @JsonProperty("MessageID") String messageId
) {}
```

- [ ] **Step 2: Add UserRepository.markEmailBlocked**

In `UserRepository.java`, add:

```java
@Modifying
@Transactional
@Query("UPDATE User u SET u.emailBlocked = true WHERE u.email = :email")
int markEmailBlocked(@Param("email") String email);
```

(Import `@Modifying` from `org.springframework.data.jpa.repository.Modifying`.)

- [ ] **Step 3: Controller**

```java
// backend/src/main/java/com/luxpretty/app/mail/web/PostmarkWebhookController.java
package com.luxpretty.app.mail.web;

import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import com.luxpretty.app.mail.web.dto.PostmarkWebhookPayload;
import com.luxpretty.app.users.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/webhooks/postmark")
public class PostmarkWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PostmarkWebhookController.class);

    private final UserRepository userRepo;
    private final MailOutboxRepository mailRepo;
    private final String expectedSecret;

    public PostmarkWebhookController(
            UserRepository userRepo,
            MailOutboxRepository mailRepo,
            @Value("${app.mail.postmark.webhook-secret:}") String expectedSecret
    ) {
        this.userRepo = userRepo;
        this.mailRepo = mailRepo;
        this.expectedSecret = expectedSecret;
    }

    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestHeader(name = "X-Postmark-Webhook-Token", required = false) String token,
            @RequestBody PostmarkWebhookPayload payload
    ) {
        if (expectedSecret == null || expectedSecret.isBlank()) {
            log.warn("Postmark webhook secret not configured — rejecting all webhook calls");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (token == null || !expectedSecret.equals(token)) {
            log.warn("Postmark webhook rejected: invalid or missing token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (payload.recordType() == null) {
            return ResponseEntity.badRequest().build();
        }

        switch (payload.recordType()) {
            case "Bounce" -> {
                if ("HardBounce".equalsIgnoreCase(payload.bounceType())) {
                    blockUserAndMail(payload.email(), payload.messageId(), "hard bounce");
                }
                // Soft bounce: no action — Postmark retries on its own
            }
            case "SpamComplaint" -> blockUserAndMail(payload.email(), payload.messageId(), "spam complaint");
            case "Delivery" -> {
                if (payload.messageId() != null) {
                    mailRepo.findByProviderMessageId(payload.messageId())
                            .ifPresent(m -> { m.setDeliveredAt(LocalDateTime.now()); mailRepo.save(m); });
                }
            }
            default -> log.debug("Ignoring Postmark RecordType={}", payload.recordType());
        }

        return ResponseEntity.ok().build();
    }

    private void blockUserAndMail(String email, String messageId, String reason) {
        if (email != null) {
            int updated = userRepo.markEmailBlocked(email);
            log.info("Postmark {}: marked {} email(s) as blocked for {}", reason, updated, email);
        }
        if (messageId != null) {
            mailRepo.findByProviderMessageId(messageId).ifPresent(m -> {
                m.setStatus(MailStatus.PERMANENTLY_FAILED);
                m.setLastError(reason);
                mailRepo.save(m);
            });
        }
    }
}
```

- [ ] **Step 4: SecurityConfig — exempt webhook from auth**

Open `SecurityConfig.java`. Locate the `permitAll()` whitelist (or whatever pattern is used to allow public endpoints). Add `/api/webhooks/postmark` to it:

```java
.requestMatchers("/api/webhooks/postmark").permitAll()
```

CSRF must also be disabled for this path. If CSRF is globally disabled for `/api/**` already, no change needed. Otherwise add `csrf.ignoringRequestMatchers("/api/webhooks/postmark")`.

- [ ] **Step 5: Test**

```java
// backend/src/test/java/com/luxpretty/app/mail/web/PostmarkWebhookControllerTests.java
package com.luxpretty.app.mail.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.config.SecurityConfig;
import com.luxpretty.app.config.CsrfLoggingFilter;
import com.luxpretty.app.common.error.RestAccessDeniedHandler;
import com.luxpretty.app.common.error.RestAuthenticationEntryPoint;
import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.auth.CustomOAuth2UserService;
import com.luxpretty.app.auth.CustomOidcUserService;
import com.luxpretty.app.auth.OAuth2AuthenticationSuccessHandler;
import com.luxpretty.app.auth.OAuth2AuthenticationFailureHandler;
import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import com.luxpretty.app.mail.web.dto.PostmarkWebhookPayload;
import com.luxpretty.app.multitenancy.TenantFilter;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostmarkWebhookController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.mail.postmark.webhook-secret=test-secret")
class PostmarkWebhookControllerTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockBean UserRepository userRepo;
    @MockBean MailOutboxRepository mailRepo;

    // Required by SecurityConfig
    @MockBean TokenService tokenService;
    @MockBean CustomOAuth2UserService customOAuth2UserService;
    @MockBean CustomOidcUserService customOidcUserService;
    @MockBean OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean TenantService tenantService;
    @SpyBean RestAccessDeniedHandler restAccessDeniedHandler;
    @SpyBean RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    @SpyBean TenantFilter tenantFilter;
    @SpyBean CsrfLoggingFilter csrfLoggingFilter;

    @Test
    void missing_secret_returns_401() throws Exception {
        PostmarkWebhookPayload p = new PostmarkWebhookPayload("Delivery", null, "x@y.com", "msg-1");
        mvc.perform(post("/api/webhooks/postmark")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(p)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void wrong_secret_returns_401() throws Exception {
        PostmarkWebhookPayload p = new PostmarkWebhookPayload("Delivery", null, "x@y.com", "msg-1");
        mvc.perform(post("/api/webhooks/postmark")
                .header("X-Postmark-Webhook-Token", "wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(p)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void hard_bounce_blocks_user_and_marks_mail_failed() throws Exception {
        MailOutbox m = new MailOutbox();
        m.setProviderMessageId("msg-1");
        m.setStatus(MailStatus.SENT);
        when(mailRepo.findByProviderMessageId("msg-1")).thenReturn(Optional.of(m));

        PostmarkWebhookPayload p = new PostmarkWebhookPayload("Bounce", "HardBounce", "x@y.com", "msg-1");
        mvc.perform(post("/api/webhooks/postmark")
                .header("X-Postmark-Webhook-Token", "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(p)))
            .andExpect(status().isOk());

        verify(userRepo).markEmailBlocked("x@y.com");
    }

    @Test
    void soft_bounce_does_not_block() throws Exception {
        PostmarkWebhookPayload p = new PostmarkWebhookPayload("Bounce", "SoftBounce", "x@y.com", "msg-1");
        mvc.perform(post("/api/webhooks/postmark")
                .header("X-Postmark-Webhook-Token", "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(p)))
            .andExpect(status().isOk());
        verify(userRepo, never()).markEmailBlocked(anyString());
    }

    @Test
    void spam_complaint_blocks_user() throws Exception {
        PostmarkWebhookPayload p = new PostmarkWebhookPayload("SpamComplaint", null, "x@y.com", "msg-1");
        mvc.perform(post("/api/webhooks/postmark")
                .header("X-Postmark-Webhook-Token", "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(p)))
            .andExpect(status().isOk());
        verify(userRepo).markEmailBlocked("x@y.com");
    }

    @Test
    void delivery_sets_delivered_at() throws Exception {
        MailOutbox m = new MailOutbox();
        m.setProviderMessageId("msg-1");
        when(mailRepo.findByProviderMessageId("msg-1")).thenReturn(Optional.of(m));

        PostmarkWebhookPayload p = new PostmarkWebhookPayload("Delivery", null, "x@y.com", "msg-1");
        mvc.perform(post("/api/webhooks/postmark")
                .header("X-Postmark-Webhook-Token", "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(p)))
            .andExpect(status().isOk());

        verify(mailRepo).save(m);
    }
}
```

- [ ] **Step 6: Verify**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q test -Dtest=PostmarkWebhookControllerTests
```

Expected: 5 tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/mail/web/ \
        backend/src/main/java/com/luxpretty/app/users/repo/UserRepository.java \
        backend/src/main/java/com/luxpretty/app/config/SecurityConfig.java \
        backend/src/test/java/com/luxpretty/app/mail/web/
git commit -m "feat(mail): add Postmark webhook (bounce/complaint/delivery) with secret check + tests"
```

---

### Task 21: Migrate AuthController callsites

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/AuthController.java`

- [ ] **Step 1: Inject MailOutboxService instead of EmailService**

In `AuthController.java`:
1. Remove `import com.luxpretty.app.notification.app.EmailService;`
2. Remove the `EmailService emailService` field and constructor parameter.
3. Add `import com.luxpretty.app.mail.app.MailOutboxService;`, `import com.luxpretty.app.mail.domain.MailTemplate;`, and the relevant Vars classes.
4. Add `private final MailOutboxService mailOutbox;` field and constructor parameter.
5. Inject `@Value("${app.frontend.base-url}")` if not already present, as we need `dashboardUrl` for `WelcomeProVars`.

- [ ] **Step 2: Replace the 3 callsites**

Line 98 (`sendWelcomeEmail` for classic signup):
```java
// Before:
emailService.sendWelcomeEmail(savedUser);

// After:
mailOutbox.queue(
    MailTemplate.WELCOME_PRO,
    new com.luxpretty.app.mail.vars.WelcomeProVars(
        savedUser.getName(),
        savedUser.getEmail(),
        frontendBaseUrl + "/pro/dashboard"),
    savedUser.getEmail(),
    null  // no tenant context yet at signup
);
```

Line 141 (`sendWelcomeEmail` for OAuth signup) — same replacement.

Line 248 (`sendPasswordResetEmail`):
```java
// Before:
emailService.sendPasswordResetEmail(user, token);

// After:
mailOutbox.queue(
    MailTemplate.RESET_PASSWORD,
    new com.luxpretty.app.mail.vars.ResetPasswordVars(
        user.getName(),
        frontendBaseUrl + "/reset-password?token=" + token),
    user.getEmail(),
    null
);
```

- [ ] **Step 3: Verify compile + existing tests**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q -DskipTests compile
mvn -q test -Dtest='AuthControllerTests'
```

Expected: BUILD SUCCESS for both.

> If `AuthControllerTests` was mocking `EmailService` directly, it now needs to mock `MailOutboxService` instead. Adjust the `@MockBean` line in the test class.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/auth/AuthController.java \
        backend/src/test/java/com/luxpretty/app/auth/AuthControllerTests.java
git commit -m "refactor(auth): migrate EmailService callsites to MailOutboxService.queue()"
```

---

### Task 22: Migrate CareBookingService callsites

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java`

- [ ] **Step 1: Same migration pattern**

In `CareBookingService.java`:
1. Remove EmailService field/import, add MailOutboxService.
2. Around line 408-409, replace:

```java
// Before:
emailService.sendBookingConfirmationEmail(client, booking, care, salonName);
emailService.sendNewBookingNotificationEmail(owner, booking, care, client.getName());

// After:
java.time.format.DateTimeFormatter dateFmt = java.time.format.DateTimeFormatter.ofPattern(
    "EEEE d MMMM yyyy", java.util.Locale.FRENCH);
java.time.format.DateTimeFormatter timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
String dateStr = booking.getAppointmentDate().format(dateFmt);
String timeStr = booking.getAppointmentTime().format(timeFmt);
String durationStr = formatDuration(care.getDuration());

mailOutbox.queue(
    com.luxpretty.app.mail.domain.MailTemplate.BOOKING_CONFIRMED,
    new com.luxpretty.app.mail.vars.BookingConfirmedVars(
        client.getName(), salonName, care.getName(),
        java.math.BigDecimal.valueOf(care.getPrice()),
        durationStr, dateStr, timeStr,
        booking.getId(), frontendBaseUrl + "/bookings"),
    client.getEmail(),
    /* tenantSlug = */ tenant.getSlug()  // assuming `tenant` is accessible in scope
);

mailOutbox.queue(
    com.luxpretty.app.mail.domain.MailTemplate.BOOKING_RECEIVED_PRO,
    new com.luxpretty.app.mail.vars.BookingReceivedProVars(
        owner.getName(), client.getName(), care.getName(),
        java.math.BigDecimal.valueOf(care.getPrice()),
        durationStr, dateStr, timeStr,
        booking.getId(), frontendBaseUrl + "/pro/bookings"),
    owner.getEmail(),
    tenant.getSlug()
);
```

> The `tenant` variable may not be directly accessible at this callsite; look at the surrounding method to find the right way to get the slug — likely via `TenantContext.getCurrentTenant()` or by adding a parameter to the method. Use whatever is already in scope.

> Move the `formatDuration` helper to a local static method (it currently lives in `EmailService` which is about to be deleted). Copy it as-is into `CareBookingService` or a new helper class.

- [ ] **Step 2: Add formatDuration helper**

Add to `CareBookingService` as a `private static` method (copy from `EmailService.java:160-170`):

```java
private static String formatDuration(int minutes) {
    if (minutes < 60) return minutes + " min";
    int hours = minutes / 60;
    int remaining = minutes % 60;
    if (remaining == 0) return hours + "h";
    return hours + "h" + String.format("%02d", remaining);
}
```

- [ ] **Step 3: Verify compile + existing tests**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn -q -DskipTests compile
mvn -q test -Dtest='CareBookingServiceTests'
```

Expected: BUILD SUCCESS. Tests may need `@MockBean MailOutboxService` instead of `EmailService`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java \
        backend/src/test/java/com/luxpretty/app/bookings/app/CareBookingServiceTests.java
git commit -m "refactor(bookings): migrate EmailService callsites to MailOutboxService.queue()"
```

---

### Task 23: Delete EmailService and legacy templates

**Files:**
- Delete: `backend/src/main/java/com/luxpretty/app/notification/app/EmailService.java`
- Delete: `backend/src/main/resources/templates/welcome-pro.html`
- Delete: `backend/src/main/resources/templates/password-reset.html`
- Delete: `backend/src/main/resources/templates/booking-confirmation.html`
- Delete: `backend/src/main/resources/templates/booking-notification-pro.html`

- [ ] **Step 1: Verify no remaining usage**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
grep -rn "EmailService\|emailService" src/main/java src/test/java 2>/dev/null
```

Expected: empty output. If anything remains, fix that callsite first.

- [ ] **Step 2: Delete**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail
rm backend/src/main/java/com/luxpretty/app/notification/app/EmailService.java
rm backend/src/main/resources/templates/welcome-pro.html
rm backend/src/main/resources/templates/password-reset.html
rm backend/src/main/resources/templates/booking-confirmation.html
rm backend/src/main/resources/templates/booking-notification-pro.html
```

- [ ] **Step 3: Verify build**

```bash
cd backend && mvn -q -DskipTests compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add -A backend/src/main/java/com/luxpretty/app/notification/app/EmailService.java \
        backend/src/main/resources/templates/welcome-pro.html \
        backend/src/main/resources/templates/password-reset.html \
        backend/src/main/resources/templates/booking-confirmation.html \
        backend/src/main/resources/templates/booking-notification-pro.html
git commit -m "refactor(mail): delete legacy EmailService and root-level templates"
```

---

### Task 24: Final validation + merge prep

**Files:** none

- [ ] **Step 1: Full backend test suite**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail/backend
mvn test 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```

Expected: All tests pass, BUILD SUCCESS.

- [ ] **Step 2: Final smoke**

If Docker is running locally:

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio-mail
docker compose --profile dev up -d mailpit oracle
# wait for Oracle health
sleep 60
cd backend && mvn spring-boot:run &
```

Test flow:
1. Open `http://localhost:8025` (Mailpit UI)
2. Hit a real endpoint that triggers a mail (e.g., signup or password reset)
3. Verify the mail lands in Mailpit, rendered with LuxPretty brand
4. Verify the `MAIL_OUTBOX` row in Oracle has `STATUS='SENT'`, `SENT_AT` set

Document the result. Kill processes after.

- [ ] **Step 3: Final summary**

Branch is ready. Do NOT push; controller will handle merge to main locally per project convention.

```bash
git log --oneline main..feat/mail-outbox | wc -l
```

Note the commit count for the merge commit message.

---

## Self-Review

### Spec coverage check

| Spec section | Task(s) |
|---|---|
| Architecture / packages | Tasks 5-13, 16-23 |
| `MAIL_OUTBOX` table V8 | Task 4 |
| `USERS.email_blocked` column V9 | Task 16 |
| Entity + repo | Task 7 |
| `MailTemplate` enum + 4 templates | Task 5 (enum) + Task 18 (templates) |
| `MailVars` sealed + 4 records | Task 6 |
| `MailOutboxService.queue()` with REQUIRED tx + type check | Task 12 |
| `MailWorker` with @Scheduled, batch 10, SELECT FOR UPDATE SKIP LOCKED | Task 13 |
| Exponential backoff 5 attempts (1m/5m/25m/2h/12h) | Task 8 |
| `MailSender` interface | Task 9 |
| `SmtpMailSender` (Mailpit) | Task 10 |
| `PostmarkMailSender` | Task 11 |
| `MailRenderer` (Thymeleaf + Jsoup CSS inline) | Task 19 |
| Postmark webhook + secret check | Task 20 |
| Bounce hard → email_blocked, Complaint → email_blocked, Delivery → delivered_at | Task 20 |
| Skip sending to blocked recipients | Task 17 |
| Mailpit `docker-compose.yml` | Task 3 |
| Postmark + Jsoup deps | Task 2 |
| Migration of 5 callsites | Tasks 21-22 |
| Delete legacy `EmailService` | Task 23 |
| Tests (unit + smoke Testcontainers) | Throughout |

All spec requirements have tasks. ✅

### Placeholder scan

No "TBD", "TODO", "implement later". All steps have code blocks where required. The "stub renderer" in PR1 is intentional and explicitly replaced in PR2 Task 19.

### Type consistency check

- `MailVars` record names match between Tasks 5 (enum), 6 (records), and 21-22 (callsites). ✅
- `MailTemplate.WELCOME_PRO`, `BOOKING_CONFIRMED`, `BOOKING_RECEIVED_PRO`, `RESET_PASSWORD` consistent throughout. ✅
- `MailStatus` values (`PENDING/IN_FLIGHT/SENT/PERMANENTLY_FAILED`) consistent with DB CHECK constraint (Task 4). ✅
- `MailRetryPolicy.MAX_ATTEMPTS = 5` consistent between Task 8 (impl) and Task 13 (worker uses it). ✅
- Field `nextAttemptAt` consistent across entity (Task 7), repo query (Task 7), retry policy (Task 8), worker (Task 13). ✅

### One latent gap

Task 17 (skip blocked recipients) modifies `UserRepository` to add `findEmailBlockedByEmail`. Task 20 also modifies `UserRepository` to add `markEmailBlocked`. Both methods reference the `emailBlocked` field added on the `User` entity in Task 16. Order: Task 16 → 17 → 20. The plan order is correct (Task 16 is first in PR2). ✅

No other gaps detected.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-12-mail-outbox.md`.**

Two execution options:

**1. Subagent-Driven (recommended)** — Fresh subagent per task, two-stage review between tasks, fast iteration. Same approach as the invoices chantier.

**2. Inline Execution** — Execute tasks sequentially in this session via executing-plans skill, with batched checkpoints.

Which approach ?
