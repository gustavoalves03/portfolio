# Pro PC Densification + Preview Share Tokens (Jalon 5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a signed-token storefront preview share feature (full-stack) and apply minimal PC density polish to pro pages so they exploit large screens.

**Architecture:** Two coupled but independent sub-features ship in this jalon. **Part A (tokens)** introduces a `SalonPreviewToken` entity in the shared schema, three pro endpoints (create / list / revoke), an extension to `PublicSalonController.getSalon` that accepts `?preview=<token>`, and a small "Share preview" section in `/pro/salon`. **Part B (densification)** raises pro page containers to `max-width: 1440px` with responsive padding via a single shared SCSS class, then individually polishes the highest-impact pages (`pro-cares`, `pro-employees`, `pro-bookings`, `pro-settings`). The split-view live preview on `/pro/salon` is **deferred** to a follow-up jalon — it requires non-trivial factoring of `SalonPageComponent` and the value-add over the J2 banner-based preview is marginal.

**Tech Stack:** Spring Boot 3.5.4 + JPA/Hibernate + Flyway (Oracle dialect). Angular 20 standalone, signals, Transloco. Tests: JUnit 5 + Mockito (backend), Karma/Jasmine (frontend).

**Spec reference:** `docs/superpowers/specs/2026-05-06-vitrine-preview-onboarding-pc-design.md` — Jalon 5 section.

**Branch:** `feat/pro-pc-densification` (create from `main` after Jalon 4 has been merged).

---

## File Structure

### Part A — Preview share tokens

**Backend — new files (8):**

| Path | Responsibility |
|------|----------------|
| `backend/src/main/resources/db/migration/oracle/V6__salon_preview_tokens.sql` | Flyway migration creating `SALON_PREVIEW_TOKENS` table. |
| `backend/src/main/java/com/prettyface/app/tenant/domain/SalonPreviewToken.java` | JPA entity. |
| `backend/src/main/java/com/prettyface/app/tenant/repo/SalonPreviewTokenRepository.java` | Spring Data repository. |
| `backend/src/main/java/com/prettyface/app/tenant/app/SalonPreviewTokenService.java` | Service: create / list / revoke / validate. |
| `backend/src/main/java/com/prettyface/app/tenant/app/SalonPreviewTokenServiceTests.java` | (new test file under `src/test/java/...`) Unit tests for the service. |
| `backend/src/main/java/com/prettyface/app/tenant/web/dto/PreviewTokenResponse.java` | DTO for list/create response. |
| `backend/src/main/java/com/prettyface/app/tenant/web/SalonPreviewTokenController.java` | `/api/pro/salon/preview-tokens` endpoints. |
| `backend/src/test/java/com/prettyface/app/tenant/web/SalonPreviewTokenControllerTests.java` | Controller tests. |

**Backend — modified files (1):**

| Path | Change |
|------|--------|
| `backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java` | `getSalon` accepts `?preview=<token>` and authorizes DRAFT view via the token (in addition to owner). |

**Frontend — new files (5):**

| Path | Responsibility |
|------|----------------|
| `frontend/src/app/features/salon-profile/services/preview-token.service.ts` | HTTP client for the three endpoints. |
| `frontend/src/app/features/salon-profile/services/preview-token.service.spec.ts` | Tests. |
| `frontend/src/app/features/salon-profile/models/preview-token.model.ts` | TS type for the DTO. |
| `frontend/src/app/features/salon-profile/preview-share/preview-share.component.ts` | UI block: list + generate + copy + revoke. |
| `frontend/src/app/features/salon-profile/preview-share/preview-share.component.html` | Template. |
| `frontend/src/app/features/salon-profile/preview-share/preview-share.component.scss` | Styles. |
| `frontend/src/app/features/salon-profile/preview-share/preview-share.component.spec.ts` | Tests. |

**Frontend — modified files (3):**

| Path | Change |
|------|--------|
| `frontend/src/app/features/salon-profile/salon-profile.component.ts` | Import + render `<app-preview-share>`. |
| `frontend/src/app/features/salon-profile/salon-profile.component.html` | Section anchor for the share UI. |
| `frontend/src/app/pages/salon/salon-page.component.ts` | When loading the salon, pass `?preview=<token>` from the route's query params to the API call. |
| `frontend/src/app/features/salon-profile/services/salon-profile.service.ts` | `getPublicSalon(slug, previewToken?)` — token forwarded as query param. |
| `frontend/public/i18n/{fr,en}.json` | Add `salon.previewShare.*` block. |

### Part B — PC densification

**Frontend — new files (1):**

| Path | Responsibility |
|------|----------------|
| `frontend/src/app/shared/uis/pro-page-shell/pro-page-shell.scss` | Shared `.pro-page` mixin/class — `max-width: 1440px`, responsive padding. (Implemented as a global class in `styles.scss`, no component file needed.) |

**Frontend — modified files (~6):**

| Path | Change |
|------|--------|
| `frontend/src/styles.scss` | Append a global `.pro-page` rule. |
| `frontend/src/app/features/cares/cares.component.scss` | Wrap content with `.pro-page`. Two-column layout PC ≥ 1024px. |
| `frontend/src/app/pages/pro/pro-employees.component.scss` | `.pro-page` + 2-3 col grid PC. |
| `frontend/src/app/pages/pro/pro-bookings.component.scss` | `.pro-page` + 2-col split (list + detail) PC. |
| `frontend/src/app/pages/pro/pro-settings.component.scss` | `.pro-page` + 2-col formulaire PC. |
| `frontend/src/app/pages/pro/pro-dashboard.component.scss:21-27` | `.dashboard-container` already uses 1200px — bump to 1440px and verify analytics layout still flows. |

(The corresponding `.html` files don't change — the `.pro-page` class wraps existing markup via SCSS, applied at the existing top-level container.)

**Out of scope (deferred):**
- Split-view live preview on `/pro/salon` (form left, embedded preview right).
- `density: -2` Material density tweak (too risky for this PR; many forms use density-sensitive layouts).
- Pro page sticky headers under the indicator stepper.
- `/pro/cares` table-vs-cards layout switch (the existing cards layout on PC is acceptable; deferring for a dedicated polish PR).

---

## Conventions

- Backend: Flyway migration name is `V<N>__<snake_case_summary>.sql`. Next available is `V6`. Place under `src/main/resources/db/migration/oracle/` (the shared schema). Tenant schema migrations are unrelated.
- Backend: JPA entity uses Lombok `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`. Naming: `Tenant.java`, `SalonPreviewToken.java`.
- Backend: Tokens are `UUID.randomUUID().toString()` — 36-char URL-safe values. Never persist anything bcrypt-hashed: the security model is "possession of the URL = access" (same as classic share links). Tokens MAY have an `expiresAt`. We default to `null` (never-expire) but the field is in place for future short-lived links.
- Backend: Service layer wraps the repo; controller is thin; DTOs in `web/dto/`.
- Frontend: standalone components, signals, `inject()`, `@if` / `@for`. Both `fr.json` and `en.json` updated together.
- Frontend: `MatSnackBar` for feedback (e.g. "Lien copié"), already used elsewhere in the project.
- Conventional Commits.

---

# Part A — Preview share tokens

## Task A1: Flyway migration for SALON_PREVIEW_TOKENS

**Files:**
- Create: `backend/src/main/resources/db/migration/oracle/V6__salon_preview_tokens.sql`

- [ ] **Step 1: Create the migration**

Create `backend/src/main/resources/db/migration/oracle/V6__salon_preview_tokens.sql`:

```sql
-- Salon storefront preview share tokens (Jalon 5).
--
-- A pro can mint a URL-safe token that grants temporary, revokable access
-- to their DRAFT storefront. The token is checked by PublicSalonController
-- when ?preview=<token> is present and the tenant is DRAFT.

CREATE TABLE SALON_PREVIEW_TOKENS (
    id          NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tenant_id   NUMBER(19) NOT NULL,
    token       VARCHAR2(64) NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    expires_at  TIMESTAMP NULL,
    revoked_at  TIMESTAMP NULL,
    CONSTRAINT FK_PREVIEW_TOKEN_TENANT
        FOREIGN KEY (tenant_id) REFERENCES TENANTS(id) ON DELETE CASCADE,
    CONSTRAINT UK_PREVIEW_TOKEN_TOKEN UNIQUE (token)
);

CREATE INDEX IX_PREVIEW_TOKEN_TENANT ON SALON_PREVIEW_TOKENS (tenant_id);
```

- [ ] **Step 2: Verify migration runs against a clean dev DB**

```bash
cd backend && ./mvnw flyway:info -q 2>&1 | tail -20
```
Expected: V6 listed as `Pending`.

```bash
cd backend && ./mvnw flyway:migrate -q 2>&1 | tail -10
```
Expected: V6 successfully applied (the existing dev DB will pick it up; if you don't have a dev DB locally, skip this step — the migration is exercised at app startup).

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/oracle/V6__salon_preview_tokens.sql
git commit -m "feat(salon-preview): add Flyway V6 for SALON_PREVIEW_TOKENS table"
```

---

## Task A2: SalonPreviewToken entity + repository

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/tenant/domain/SalonPreviewToken.java`
- Create: `backend/src/main/java/com/prettyface/app/tenant/repo/SalonPreviewTokenRepository.java`

- [ ] **Step 1: Create the entity**

Create `backend/src/main/java/com/prettyface/app/tenant/domain/SalonPreviewToken.java`:

```java
package com.prettyface.app.tenant.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "SALON_PREVIEW_TOKENS", uniqueConstraints = {
        @UniqueConstraint(name = "UK_PREVIEW_TOKEN_TOKEN", columnNames = "token")
})
public class SalonPreviewToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "token", nullable = false, length = 64)
    private String token;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
```

- [ ] **Step 2: Create the repository**

Create `backend/src/main/java/com/prettyface/app/tenant/repo/SalonPreviewTokenRepository.java`:

```java
package com.prettyface.app.tenant.repo;

import com.prettyface.app.tenant.domain.SalonPreviewToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalonPreviewTokenRepository extends JpaRepository<SalonPreviewToken, Long> {
    Optional<SalonPreviewToken> findByToken(String token);
    List<SalonPreviewToken> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
```

- [ ] **Step 3: Verify compile**

```bash
cd backend && ./mvnw compile -q
```
Expected: success.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/domain/SalonPreviewToken.java backend/src/main/java/com/prettyface/app/tenant/repo/SalonPreviewTokenRepository.java
git commit -m "feat(salon-preview): add SalonPreviewToken entity and repository"
```

---

## Task A3: SalonPreviewTokenService

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/tenant/app/SalonPreviewTokenService.java`
- Create: `backend/src/test/java/com/prettyface/app/tenant/app/SalonPreviewTokenServiceTests.java`

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/java/com/prettyface/app/tenant/app/SalonPreviewTokenServiceTests.java`:

```java
package com.prettyface.app.tenant.app;

import com.prettyface.app.tenant.domain.SalonPreviewToken;
import com.prettyface.app.tenant.repo.SalonPreviewTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalonPreviewTokenServiceTests {

    @Mock private SalonPreviewTokenRepository repository;

    @InjectMocks private SalonPreviewTokenService service;

    private static final long TENANT_ID = 42L;

    @BeforeEach
    void setUp() {
        // Save returns the entity passed in (Mockito default behaviour after stubbing).
        when(repository.save(org.mockito.ArgumentMatchers.any(SalonPreviewToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createGeneratesAUniqueTokenAndPersistsTheEntity() {
        SalonPreviewToken result = service.create(TENANT_ID);

        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getRevokedAt()).isNull();
        verify(repository).save(org.mockito.ArgumentMatchers.any(SalonPreviewToken.class));
    }

    @Test
    void listReturnsTheTenantTokensOrderedByCreationDesc() {
        SalonPreviewToken a = SalonPreviewToken.builder().id(1L).tenantId(TENANT_ID).token("a").build();
        SalonPreviewToken b = SalonPreviewToken.builder().id(2L).tenantId(TENANT_ID).token("b").build();
        when(repository.findByTenantIdOrderByCreatedAtDesc(TENANT_ID)).thenReturn(List.of(a, b));

        List<SalonPreviewToken> tokens = service.listByTenant(TENANT_ID);

        assertThat(tokens).extracting(SalonPreviewToken::getToken).containsExactly("a", "b");
    }

    @Test
    void revokeMarksTheTokenAndPersistsIt() {
        SalonPreviewToken existing = SalonPreviewToken.builder()
                .id(99L).tenantId(TENANT_ID).token("t").createdAt(LocalDateTime.now()).build();
        when(repository.findById(99L)).thenReturn(Optional.of(existing));

        service.revoke(99L, TENANT_ID);

        ArgumentCaptor<SalonPreviewToken> captor = ArgumentCaptor.forClass(SalonPreviewToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRevokedAt()).isNotNull();
    }

    @Test
    void revokeRefusesToRevokeATokenFromAnotherTenant() {
        SalonPreviewToken existing = SalonPreviewToken.builder()
                .id(99L).tenantId(TENANT_ID + 1).token("t").createdAt(LocalDateTime.now()).build();
        when(repository.findById(99L)).thenReturn(Optional.of(existing));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.revoke(99L, TENANT_ID));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validateReturnsTrueForActiveTokens() {
        SalonPreviewToken active = SalonPreviewToken.builder()
                .id(1L).tenantId(TENANT_ID).token("good")
                .createdAt(LocalDateTime.now())
                .build();
        when(repository.findByToken("good")).thenReturn(Optional.of(active));

        boolean ok = service.isValidForTenant("good", TENANT_ID);

        assertThat(ok).isTrue();
    }

    @Test
    void validateReturnsFalseForExpiredTokens() {
        SalonPreviewToken expired = SalonPreviewToken.builder()
                .id(1L).tenantId(TENANT_ID).token("old")
                .createdAt(LocalDateTime.now().minusDays(2))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        when(repository.findByToken("old")).thenReturn(Optional.of(expired));

        boolean ok = service.isValidForTenant("old", TENANT_ID);

        assertThat(ok).isFalse();
    }

    @Test
    void validateReturnsFalseForRevokedTokens() {
        SalonPreviewToken revoked = SalonPreviewToken.builder()
                .id(1L).tenantId(TENANT_ID).token("dead")
                .createdAt(LocalDateTime.now())
                .revokedAt(LocalDateTime.now())
                .build();
        when(repository.findByToken("dead")).thenReturn(Optional.of(revoked));

        boolean ok = service.isValidForTenant("dead", TENANT_ID);

        assertThat(ok).isFalse();
    }

    @Test
    void validateReturnsFalseForTokenMismatchedToTenant() {
        SalonPreviewToken otherTenant = SalonPreviewToken.builder()
                .id(1L).tenantId(TENANT_ID + 1).token("t")
                .createdAt(LocalDateTime.now())
                .build();
        when(repository.findByToken("t")).thenReturn(Optional.of(otherTenant));

        boolean ok = service.isValidForTenant("t", TENANT_ID);

        assertThat(ok).isFalse();
    }

    @Test
    void validateReturnsFalseForUnknownTokens() {
        when(repository.findByToken("nope")).thenReturn(Optional.empty());

        boolean ok = service.isValidForTenant("nope", TENANT_ID);

        assertThat(ok).isFalse();
    }
}
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd backend && ./mvnw test -Dtest=SalonPreviewTokenServiceTests -q
```
Expected: compilation FAIL — `SalonPreviewTokenService` not found.

- [ ] **Step 3: Implement the service**

Create `backend/src/main/java/com/prettyface/app/tenant/app/SalonPreviewTokenService.java`:

```java
package com.prettyface.app.tenant.app;

import com.prettyface.app.tenant.domain.SalonPreviewToken;
import com.prettyface.app.tenant.repo.SalonPreviewTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mints, lists, revokes, and validates URL-safe storefront preview tokens.
 *
 * Security model: possession of the token === access (classic share-link
 * pattern). Tokens are stored in plaintext because the URL itself is the
 * credential; revocation uses the `revoked_at` timestamp.
 */
@Service
public class SalonPreviewTokenService {

    private final SalonPreviewTokenRepository repository;

    public SalonPreviewTokenService(SalonPreviewTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SalonPreviewToken create(Long tenantId) {
        SalonPreviewToken token = SalonPreviewToken.builder()
                .tenantId(tenantId)
                .token(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .build();
        return repository.save(token);
    }

    @Transactional(readOnly = true)
    public List<SalonPreviewToken> listByTenant(Long tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public void revoke(Long tokenId, Long tenantId) {
        SalonPreviewToken token = repository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Token not found"));
        if (!token.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Token belongs to another tenant");
        }
        token.setRevokedAt(LocalDateTime.now());
        repository.save(token);
    }

    @Transactional(readOnly = true)
    public boolean isValidForTenant(String token, Long tenantId) {
        Optional<SalonPreviewToken> maybe = repository.findByToken(token);
        if (maybe.isEmpty()) return false;
        SalonPreviewToken t = maybe.get();
        if (!t.getTenantId().equals(tenantId)) return false;
        if (t.getRevokedAt() != null) return false;
        if (t.getExpiresAt() != null && t.getExpiresAt().isBefore(LocalDateTime.now())) return false;
        return true;
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
cd backend && ./mvnw test -Dtest=SalonPreviewTokenServiceTests -q
```
Expected: 9 tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/app/SalonPreviewTokenService.java backend/src/test/java/com/prettyface/app/tenant/app/SalonPreviewTokenServiceTests.java
git commit -m "feat(salon-preview): add SalonPreviewTokenService with create/list/revoke/validate"
```

---

## Task A4: PreviewTokenResponse DTO + SalonPreviewTokenController

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/tenant/web/dto/PreviewTokenResponse.java`
- Create: `backend/src/main/java/com/prettyface/app/tenant/web/SalonPreviewTokenController.java`
- Create: `backend/src/test/java/com/prettyface/app/tenant/web/SalonPreviewTokenControllerTests.java`

- [ ] **Step 1: Create the DTO**

Create `backend/src/main/java/com/prettyface/app/tenant/web/dto/PreviewTokenResponse.java`:

```java
package com.prettyface.app.tenant.web.dto;

import java.time.LocalDateTime;

public record PreviewTokenResponse(
        Long id,
        String token,
        String shareUrl,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt
) {}
```

- [ ] **Step 2: Write the controller test**

Create `backend/src/test/java/com/prettyface/app/tenant/web/SalonPreviewTokenControllerTests.java`:

```java
package com.prettyface.app.tenant.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.tenant.app.SalonPreviewTokenService;
import com.prettyface.app.tenant.app.TenantService;
import com.prettyface.app.tenant.domain.SalonPreviewToken;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.web.dto.PreviewTokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalonPreviewTokenControllerTests {

    @Mock private SalonPreviewTokenService tokenService;
    @Mock private TenantService tenantService;

    @InjectMocks private SalonPreviewTokenController controller;

    private static final long OWNER_ID = 42L;
    private static final long TENANT_ID = 7L;

    private UserPrincipal principal() {
        return new UserPrincipal(OWNER_ID, "u@example.com", "User", null);
    }

    private Tenant tenant() {
        return Tenant.builder().id(TENANT_ID).slug("demo").ownerId(OWNER_ID).build();
    }

    @Test
    void createReturnsTheTokenWithAShareUrl() {
        when(tenantService.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant()));
        SalonPreviewToken minted = SalonPreviewToken.builder()
                .id(1L).tenantId(TENANT_ID).token("abc")
                .createdAt(LocalDateTime.now()).build();
        when(tokenService.create(TENANT_ID)).thenReturn(minted);

        ResponseEntity<PreviewTokenResponse> response = controller.create(principal());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isEqualTo("abc");
        assertThat(response.getBody().shareUrl()).contains("/salon/demo");
        assertThat(response.getBody().shareUrl()).contains("preview=abc");
    }

    @Test
    void listReturnsTokensForTheCurrentTenantOnly() {
        when(tenantService.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant()));
        SalonPreviewToken t = SalonPreviewToken.builder()
                .id(1L).tenantId(TENANT_ID).token("a")
                .createdAt(LocalDateTime.now()).build();
        when(tokenService.listByTenant(TENANT_ID)).thenReturn(List.of(t));

        ResponseEntity<List<PreviewTokenResponse>> response = controller.list(principal());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).token()).isEqualTo("a");
    }

    @Test
    void deleteRevokesTheTokenAndReturnsNoContent() {
        when(tenantService.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant()));

        ResponseEntity<Void> response = controller.delete(99L, principal());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(tokenService).revoke(99L, TENANT_ID);
    }

    @Test
    void createReturns404WhenTheUserHasNoTenant() {
        when(tenantService.findByOwnerId(OWNER_ID)).thenReturn(Optional.empty());

        org.springframework.web.server.ResponseStatusException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        () -> controller.create(principal()));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
```

- [ ] **Step 3: Run tests — expect failure**

```bash
cd backend && ./mvnw test -Dtest=SalonPreviewTokenControllerTests -q
```
Expected: compilation FAIL — controller not found.

- [ ] **Step 4: Implement the controller**

Create `backend/src/main/java/com/prettyface/app/tenant/web/SalonPreviewTokenController.java`:

```java
package com.prettyface.app.tenant.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.tenant.app.SalonPreviewTokenService;
import com.prettyface.app.tenant.app.TenantService;
import com.prettyface.app.tenant.domain.SalonPreviewToken;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.web.dto.PreviewTokenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/pro/salon/preview-tokens")
public class SalonPreviewTokenController {

    private final SalonPreviewTokenService tokenService;
    private final TenantService tenantService;

    public SalonPreviewTokenController(SalonPreviewTokenService tokenService, TenantService tenantService) {
        this.tokenService = tokenService;
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<PreviewTokenResponse> create(@AuthenticationPrincipal UserPrincipal principal) {
        Tenant tenant = requireTenant(principal);
        SalonPreviewToken minted = tokenService.create(tenant.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(minted, tenant.getSlug()));
    }

    @GetMapping
    public ResponseEntity<List<PreviewTokenResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        Tenant tenant = requireTenant(principal);
        List<PreviewTokenResponse> tokens = tokenService.listByTenant(tenant.getId()).stream()
                .map(t -> toResponse(t, tenant.getSlug()))
                .toList();
        return ResponseEntity.ok(tokens);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        Tenant tenant = requireTenant(principal);
        tokenService.revoke(id, tenant.getId());
        return ResponseEntity.noContent().build();
    }

    private Tenant requireTenant(UserPrincipal principal) {
        return tenantService.findByOwnerId(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tenant for user"));
    }

    private static PreviewTokenResponse toResponse(SalonPreviewToken t, String slug) {
        // Relative URL — frontend prepends the origin when displaying.
        String shareUrl = "/salon/" + slug + "?preview=" + t.getToken();
        return new PreviewTokenResponse(
                t.getId(),
                t.getToken(),
                shareUrl,
                t.getCreatedAt(),
                t.getExpiresAt(),
                t.getRevokedAt()
        );
    }
}
```

- [ ] **Step 5: Run controller tests — expect pass**

```bash
cd backend && ./mvnw test -Dtest=SalonPreviewTokenControllerTests -q
```
Expected: 4 tests pass.

- [ ] **Step 6: Verify the security config allows authenticated access on `/api/pro/**`**

```bash
grep -n '/api/pro' backend/src/main/java/com/prettyface/app/config/SecurityConfig.java
```

If `/api/pro/**` is matched as `authenticated()` AND has the PRO role guard, no change needed. If a more specific matcher is needed, add `.requestMatchers("/api/pro/salon/preview-tokens/**").hasAnyRole("PRO", "ADMIN")` before the catch-all.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/web/dto/PreviewTokenResponse.java backend/src/main/java/com/prettyface/app/tenant/web/SalonPreviewTokenController.java backend/src/test/java/com/prettyface/app/tenant/web/SalonPreviewTokenControllerTests.java
git commit -m "feat(salon-preview): add SalonPreviewTokenController and DTO"
```

---

## Task A5: Extend PublicSalonController to accept ?preview=<token>

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java`
- Modify: `backend/src/test/java/com/prettyface/app/tenant/web/PublicSalonControllerPreviewTests.java`

The Jalon 2 controller currently authorizes DRAFT view only for the owner. We add a second path: a valid `?preview=<token>` query param.

- [ ] **Step 1: Inspect the current controller**

```bash
grep -n 'canViewStorefront\|getSalon\|@AuthenticationPrincipal' backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java | head -10
```

Find the existing `getSalon` and `canViewStorefront` method (added in Jalon 2).

- [ ] **Step 2: Update PublicSalonController**

Open `backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java`. Add the import:

```java
import com.prettyface.app.tenant.app.SalonPreviewTokenService;
```

Update the constructor to inject the new service:

The current constructor injects 12 collaborators. Add `SalonPreviewTokenService` as the 13th, both as a field and as a constructor parameter:

```java
private final SalonPreviewTokenService previewTokenService;
```

```java
public PublicSalonController(/* existing 12 ... */ SalonPreviewTokenService previewTokenService) {
    /* existing 12 assignments */
    this.previewTokenService = previewTokenService;
}
```

Update the `getSalon` method to accept `@RequestParam(required = false) String preview` and pass it into `canViewStorefront`:

```java
@GetMapping("/{slug}")
public ResponseEntity<PublicSalonResponse> getSalon(
        @PathVariable String slug,
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) String preview) {
    return tenantService.findBySlug(slug)
            .filter(tenant -> canViewStorefront(tenant, principal, preview))
            .map(tenant -> {
                TenantContext.setCurrentTenant(tenant.getSlug());
                try {
                    List<Category> categories = categoryRepository.findAllWithCaresFull();
                    return ResponseEntity.ok(TenantMapper.toPublicResponse(tenant, categories));
                } finally {
                    TenantContext.clear();
                }
            })
            .orElse(ResponseEntity.notFound().build());
}
```

Update `canViewStorefront` (the private method added in Jalon 2):

```java
private boolean canViewStorefront(
        com.prettyface.app.tenant.domain.Tenant tenant,
        UserPrincipal principal,
        String previewToken) {
    if (tenant.getStatus() == TenantStatus.ACTIVE) {
        return true;
    }
    if (tenant.getStatus() == TenantStatus.DRAFT) {
        if (principal != null && tenant.getOwnerId().equals(principal.getId())) {
            return true;
        }
        if (previewToken != null
                && previewTokenService.isValidForTenant(previewToken, tenant.getId())) {
            return true;
        }
    }
    return false;
}
```

- [ ] **Step 3: Update existing PublicSalonControllerPreviewTests**

Open `backend/src/test/java/com/prettyface/app/tenant/web/PublicSalonControllerPreviewTests.java`. The existing tests need a new mock for `SalonPreviewTokenService` and the `getSalon` calls need a third argument (the token, `null` for the existing scenarios).

Add the mock at the top of the class (alongside the existing `@Mock` fields):

```java
@Mock private SalonPreviewTokenService previewTokenService;
```

Add the import at the top:

```java
import com.prettyface.app.tenant.app.SalonPreviewTokenService;
```

Update each `controller.getSalon(SLUG, principal)` call site to pass `null` as the third argument: `controller.getSalon(SLUG, principal, null)`. Same for the anonymous calls: `controller.getSalon(SLUG, null, null)`. Same for `controller.getSalon("unknown", principal(OWNER_ID))` → `controller.getSalon("unknown", principal(OWNER_ID), null)`.

Then add three new tests at the bottom:

```java
    @Test
    void anonymousWithValidPreviewTokenCanViewDraftSalon() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.DRAFT)));
        when(previewTokenService.isValidForTenant("good-token", 1L)).thenReturn(true);

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, null, "good-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("DRAFT");
    }

    @Test
    void anonymousWithInvalidPreviewTokenGetsNotFoundForDraft() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.DRAFT)));
        when(previewTokenService.isValidForTenant("bad-token", 1L)).thenReturn(false);

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, null, "bad-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void previewTokenIsIgnoredWhenSalonIsActive() {
        // ACTIVE = visible to anyone; we don't even check the token.
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.ACTIVE)));

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, null, "any-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // The token service should NOT be consulted for ACTIVE salons.
        verify(previewTokenService, never()).isValidForTenant(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }
```

Add the missing imports at the top:

```java
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
```

The existing `tenantWithStatus(...)` builder uses `id(1L)`. The new tests use the same `1L` to match.

- [ ] **Step 4: Run all PublicSalonController tests**

```bash
cd backend && ./mvnw test -Dtest='PublicSalonController*' -q
```
Expected: all tests pass (existing 6 from Jalon 2 + 3 new).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java backend/src/test/java/com/prettyface/app/tenant/web/PublicSalonControllerPreviewTests.java
git commit -m "feat(salon-public): authorize DRAFT preview via ?preview=<token>"
```

---

## Task A6: Frontend — preview-token model + service

**Files:**
- Create: `frontend/src/app/features/salon-profile/models/preview-token.model.ts`
- Create: `frontend/src/app/features/salon-profile/services/preview-token.service.ts`
- Create: `frontend/src/app/features/salon-profile/services/preview-token.service.spec.ts`

- [ ] **Step 1: Create the model**

Create `frontend/src/app/features/salon-profile/models/preview-token.model.ts`:

```typescript
export interface PreviewTokenResponse {
  id: number;
  token: string;
  /** Relative path like "/salon/demo?preview=<token>". The UI prepends location.origin to render the absolute share link. */
  shareUrl: string;
  createdAt: string;
  expiresAt: string | null;
  revokedAt: string | null;
}
```

- [ ] **Step 2: Write the failing service test**

Create `frontend/src/app/features/salon-profile/services/preview-token.service.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PreviewTokenService } from './preview-token.service';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';

describe('PreviewTokenService', () => {
  let service: PreviewTokenService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api' },
      ],
    });
    service = TestBed.inject(PreviewTokenService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('list issues GET /api/pro/salon/preview-tokens', () => {
    let result: any;
    service.list().subscribe((r) => (result = r));
    const req = httpTesting.expectOne('http://api/api/pro/salon/preview-tokens');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, token: 't', shareUrl: '/salon/demo?preview=t', createdAt: '2026-05-06T10:00', expiresAt: null, revokedAt: null }]);
    expect(result).toEqual([
      jasmine.objectContaining({ id: 1, token: 't' }),
    ]);
  });

  it('create issues POST /api/pro/salon/preview-tokens', () => {
    let result: any;
    service.create().subscribe((r) => (result = r));
    const req = httpTesting.expectOne('http://api/api/pro/salon/preview-tokens');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 5, token: 'abc', shareUrl: '/salon/demo?preview=abc', createdAt: '2026-05-06T10:00', expiresAt: null, revokedAt: null });
    expect(result.token).toBe('abc');
  });

  it('revoke issues DELETE /api/pro/salon/preview-tokens/:id', () => {
    service.revoke(99).subscribe();
    const req = httpTesting.expectOne('http://api/api/pro/salon/preview-tokens/99');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
```

- [ ] **Step 3: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/preview-token.service.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 4: Implement the service**

Create `frontend/src/app/features/salon-profile/services/preview-token.service.ts`:

```typescript
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { PreviewTokenResponse } from '../models/preview-token.model';

@Injectable({ providedIn: 'root' })
export class PreviewTokenService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = inject(API_BASE_URL);

  private endpoint(): string {
    return `${this.apiBase}/api/pro/salon/preview-tokens`;
  }

  list(): Observable<PreviewTokenResponse[]> {
    return this.http.get<PreviewTokenResponse[]>(this.endpoint());
  }

  create(): Observable<PreviewTokenResponse> {
    return this.http.post<PreviewTokenResponse>(this.endpoint(), {});
  }

  revoke(id: number): Observable<void> {
    return this.http.delete<void>(`${this.endpoint()}/${id}`);
  }
}
```

- [ ] **Step 5: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/preview-token.service.spec.ts' --watch=false
```
Expected: 3 specs pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/salon-profile/models/preview-token.model.ts frontend/src/app/features/salon-profile/services/preview-token.service.ts frontend/src/app/features/salon-profile/services/preview-token.service.spec.ts
git commit -m "feat(preview-share): add PreviewTokenService HTTP client"
```

---

## Task A7: i18n keys for the share UI

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add FR keys**

Open `frontend/public/i18n/fr.json`. Locate the `"salon"` block. Add a new sibling block `"previewShare"` (alongside `"preview"` from Jalon 2):

```json
"previewShare": {
  "title": "Partager un aperçu",
  "subtitle": "Générez un lien temporaire pour montrer votre vitrine à un proche avant publication.",
  "generate": "Générer un lien",
  "noLinks": "Aucun lien actif",
  "createdAt": "Créé le {{date}}",
  "copy": "Copier",
  "copied": "Lien copié",
  "revoke": "Révoquer",
  "revoked": "Lien révoqué",
  "revokeConfirm": "Révoquer ce lien ? Le destinataire n'y aura plus accès."
}
```

- [ ] **Step 2: Add EN keys**

Open `frontend/public/i18n/en.json`. Add to `"salon"`:

```json
"previewShare": {
  "title": "Share a preview",
  "subtitle": "Generate a temporary link to show your storefront to someone before publication.",
  "generate": "Generate a link",
  "noLinks": "No active links",
  "createdAt": "Created on {{date}}",
  "copy": "Copy",
  "copied": "Link copied",
  "revoke": "Revoke",
  "revoked": "Link revoked",
  "revokeConfirm": "Revoke this link? The recipient won't be able to access it anymore."
}
```

- [ ] **Step 3: Validate JSON**

```bash
python3 -m json.tool frontend/public/i18n/fr.json > /dev/null && echo FR_OK
python3 -m json.tool frontend/public/i18n/en.json > /dev/null && echo EN_OK
```
Expected: both `OK`.

- [ ] **Step 4: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat(i18n): add salon.previewShare.* keys (FR/EN)"
```

---

## Task A8: PreviewShareComponent

**Files:**
- Create: `frontend/src/app/features/salon-profile/preview-share/preview-share.component.ts`
- Create: `frontend/src/app/features/salon-profile/preview-share/preview-share.component.html`
- Create: `frontend/src/app/features/salon-profile/preview-share/preview-share.component.scss`
- Create: `frontend/src/app/features/salon-profile/preview-share/preview-share.component.spec.ts`

UI block: list active tokens + button to generate + copy / revoke per row.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/features/salon-profile/preview-share/preview-share.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { PreviewShareComponent } from './preview-share.component';
import { PreviewTokenService } from '../services/preview-token.service';
import { PreviewTokenResponse } from '../models/preview-token.model';

function token(overrides: Partial<PreviewTokenResponse> = {}): PreviewTokenResponse {
  return {
    id: 1,
    token: 't',
    shareUrl: '/salon/demo?preview=t',
    createdAt: '2026-05-06T10:00:00',
    expiresAt: null,
    revokedAt: null,
    ...overrides,
  };
}

describe('PreviewShareComponent', () => {
  let fixture: ComponentFixture<PreviewShareComponent>;
  let component: PreviewShareComponent;
  let serviceSpy: jasmine.SpyObj<PreviewTokenService>;

  beforeEach(() => {
    serviceSpy = jasmine.createSpyObj<PreviewTokenService>('PreviewTokenService', [
      'list',
      'create',
      'revoke',
    ]);
    serviceSpy.list.and.returnValue(of([]));
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: PreviewTokenService, useValue: serviceSpy },
      ],
      imports: [
        PreviewShareComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(PreviewShareComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads tokens on init', () => {
    expect(serviceSpy.list).toHaveBeenCalled();
  });

  it('shows the empty state when there is no active token', () => {
    serviceSpy.list.and.returnValue(of([token({ revokedAt: '2026-05-06T11:00:00' })]));
    fixture = TestBed.createComponent(PreviewShareComponent);
    fixture.detectChanges();
    const empty = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="no-links"]');
    expect(empty).not.toBeNull();
  });

  it('shows one row per active token', () => {
    serviceSpy.list.and.returnValue(of([token({ id: 1 }), token({ id: 2, token: 'b' })]));
    fixture = TestBed.createComponent(PreviewShareComponent);
    fixture.detectChanges();
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="token-row"]');
    expect(rows.length).toBe(2);
  });

  it('calls service.create when the generate button is clicked', () => {
    serviceSpy.create.and.returnValue(of(token({ id: 5, token: 'new' })));
    serviceSpy.list.and.returnValue(of([]));
    fixture = TestBed.createComponent(PreviewShareComponent);
    fixture.detectChanges();
    const btn = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      '[data-testid="generate-btn"]',
    );
    btn?.click();
    expect(serviceSpy.create).toHaveBeenCalled();
  });

  it('calls service.revoke when the revoke button is clicked (after confirm)', () => {
    serviceSpy.list.and.returnValue(of([token({ id: 7 })]));
    serviceSpy.revoke.and.returnValue(of(undefined));
    spyOn(window, 'confirm').and.returnValue(true);
    fixture = TestBed.createComponent(PreviewShareComponent);
    fixture.detectChanges();
    const btn = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      '[data-testid="revoke-btn-7"]',
    );
    btn?.click();
    expect(serviceSpy.revoke).toHaveBeenCalledWith(7);
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/preview-share.component.spec.ts' --watch=false
```
Expected: FAIL.

- [ ] **Step 3: Create the component TS**

Create `frontend/src/app/features/salon-profile/preview-share/preview-share.component.ts`:

```typescript
import { Component, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { PreviewTokenService } from '../services/preview-token.service';
import { PreviewTokenResponse } from '../models/preview-token.model';

@Component({
  selector: 'app-preview-share',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, TranslocoPipe],
  templateUrl: './preview-share.component.html',
  styleUrl: './preview-share.component.scss',
})
export class PreviewShareComponent {
  private readonly tokenService = inject(PreviewTokenService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  protected readonly tokens = signal<PreviewTokenResponse[]>([]);
  protected readonly creating = signal(false);

  protected readonly activeTokens = computed(() =>
    this.tokens().filter((t) => !t.revokedAt),
  );

  constructor() {
    this.refresh();
  }

  protected refresh(): void {
    this.tokenService.list().subscribe({
      next: (tokens) => this.tokens.set(tokens),
    });
  }

  protected onGenerate(): void {
    if (this.creating()) return;
    this.creating.set(true);
    this.tokenService.create().subscribe({
      next: (token) => {
        this.tokens.update((current) => [token, ...current]);
        this.creating.set(false);
      },
      error: () => this.creating.set(false),
    });
  }

  protected onCopy(token: PreviewTokenResponse): void {
    const url = window.location.origin + token.shareUrl;
    navigator.clipboard.writeText(url).then(() => {
      this.snackBar.open(this.transloco.translate('salon.previewShare.copied'), undefined, { duration: 2000 });
    });
  }

  protected onRevoke(token: PreviewTokenResponse): void {
    const confirmed = window.confirm(this.transloco.translate('salon.previewShare.revokeConfirm'));
    if (!confirmed) return;
    this.tokenService.revoke(token.id).subscribe({
      next: () => {
        this.tokens.update((current) =>
          current.map((t) =>
            t.id === token.id ? { ...t, revokedAt: new Date().toISOString() } : t,
          ),
        );
        this.snackBar.open(this.transloco.translate('salon.previewShare.revoked'), undefined, { duration: 2000 });
      },
    });
  }

  protected formattedDate(iso: string): string {
    try {
      return new Date(iso).toLocaleDateString();
    } catch {
      return iso;
    }
  }
}
```

- [ ] **Step 4: Create the template**

Create `frontend/src/app/features/salon-profile/preview-share/preview-share.component.html`:

```html
<section class="ps-section">
  <header class="ps-header">
    <h3 class="ps-title">{{ 'salon.previewShare.title' | transloco }}</h3>
    <p class="ps-subtitle">{{ 'salon.previewShare.subtitle' | transloco }}</p>
  </header>

  <button
    type="button"
    mat-stroked-button
    class="ps-generate"
    data-testid="generate-btn"
    [disabled]="creating()"
    (click)="onGenerate()"
  >
    <mat-icon>add_link</mat-icon>
    {{ 'salon.previewShare.generate' | transloco }}
  </button>

  @if (activeTokens().length === 0) {
    <p class="ps-empty" data-testid="no-links">{{ 'salon.previewShare.noLinks' | transloco }}</p>
  } @else {
    <ul class="ps-list">
      @for (token of activeTokens(); track token.id) {
        <li class="ps-row" data-testid="token-row">
          <div class="ps-row-info">
            <code class="ps-row-token">…{{ token.token.slice(-8) }}</code>
            <span class="ps-row-date">
              {{ 'salon.previewShare.createdAt' | transloco: { date: formattedDate(token.createdAt) } }}
            </span>
          </div>
          <div class="ps-row-actions">
            <button
              type="button"
              mat-stroked-button
              class="ps-action"
              [attr.data-testid]="'copy-btn-' + token.id"
              (click)="onCopy(token)"
            >
              <mat-icon>content_copy</mat-icon>
              {{ 'salon.previewShare.copy' | transloco }}
            </button>
            <button
              type="button"
              mat-stroked-button
              class="ps-action ps-action-danger"
              [attr.data-testid]="'revoke-btn-' + token.id"
              (click)="onRevoke(token)"
            >
              <mat-icon>delete</mat-icon>
              {{ 'salon.previewShare.revoke' | transloco }}
            </button>
          </div>
        </li>
      }
    </ul>
  }
</section>
```

- [ ] **Step 5: Create the styles**

Create `frontend/src/app/features/salon-profile/preview-share/preview-share.component.scss`:

```scss
:host {
  display: block;
}

.ps-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
  background: white;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 14px;
}

.ps-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ps-title {
  font-size: 1.125rem;
  font-weight: 500;
  color: #222;
  margin: 0;
}

.ps-subtitle {
  font-size: 13px;
  color: #666;
  margin: 0;
  line-height: 1.4;
}

.ps-generate {
  align-self: flex-start;
}

.ps-empty {
  font-size: 12px;
  color: #999;
  margin: 0;
}

.ps-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ps-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  background: #fafafa;
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: 10px;

  @media (max-width: 600px) {
    flex-direction: column;
    align-items: stretch;
  }
}

.ps-row-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.ps-row-token {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: #555;
  background: white;
  padding: 2px 6px;
  border-radius: 4px;
  align-self: flex-start;
}

.ps-row-date {
  font-size: 11px;
  color: #999;
}

.ps-row-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.ps-action-danger {
  color: #c0392b;
}
```

- [ ] **Step 6: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/preview-share.component.spec.ts' --watch=false
```
Expected: 5 specs pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/features/salon-profile/preview-share/
git commit -m "feat(preview-share): add PreviewShareComponent for managing share tokens"
```

---

## Task A9: Wire PreviewShareComponent into salon-profile

**Files:**
- Modify: `frontend/src/app/features/salon-profile/salon-profile.component.ts`
- Modify: `frontend/src/app/features/salon-profile/salon-profile.component.html`

- [ ] **Step 1: Update the component TS imports**

Open `frontend/src/app/features/salon-profile/salon-profile.component.ts`. Add the import:

```typescript
import { PreviewShareComponent } from './preview-share/preview-share.component';
```

In the `@Component({ imports: [...] })` array, add `PreviewShareComponent` to the existing list.

- [ ] **Step 2: Add the section to the template**

Open `frontend/src/app/features/salon-profile/salon-profile.component.html`. Find an appropriate location near the bottom of the form (after the main fields, before the save button or after it — pick what makes sense based on the file's structure). Add:

```html
<app-preview-share />
```

If the salon-profile uses tabs (`<mat-tab>` etc.), place the share component on the same tab as the main profile or in a dedicated "Partage" tab. If unsure, place it as the last section before the save action.

- [ ] **Step 3: Verify TS compile + run salon-profile tests**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

```bash
cd frontend && npm test -- --include='**/salon-profile.component.spec.ts' --watch=false
```
Expected: existing tests still pass (or, if pre-existing failures count was 12, the count stays at 12). The PreviewShareComponent injects `PreviewTokenService` which itself uses HttpClient — make sure the test setup has `provideHttpClientTesting()` (likely already there).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/salon-profile/salon-profile.component.ts frontend/src/app/features/salon-profile/salon-profile.component.html
git commit -m "feat(salon-profile): mount PreviewShareComponent in the salon profile page"
```

---

## Task A10: Forward `?preview=<token>` from salon-page route to API call

**Files:**
- Modify: `frontend/src/app/features/salon-profile/services/salon-profile.service.ts`
- Modify: `frontend/src/app/pages/salon/salon-page.component.ts`

The frontend already loads the salon at `/salon/:slug`. When the URL has `?preview=<token>`, we forward the token to the backend so it returns the DRAFT salon for an unauthenticated visitor.

- [ ] **Step 1: Inspect the existing service**

```bash
grep -n 'getPublicSalon\|getPublicBySlug' frontend/src/app/features/salon-profile/services/salon-profile.service.ts
```

The salon-page.component currently calls something like `this.salonService.getPublicSalon(slug)`. Find the exact method name.

- [ ] **Step 2: Update the service to accept an optional token**

Open `frontend/src/app/features/salon-profile/services/salon-profile.service.ts`. Find the public salon fetch method. Add an optional `previewToken` parameter and append it as a query param:

```typescript
import { HttpParams } from '@angular/common/http';

// Inside the service class:
getPublicSalon(slug: string, previewToken?: string | null): Observable<PublicSalonResponse> {
  let params = new HttpParams();
  if (previewToken) {
    params = params.set('preview', previewToken);
  }
  return this.http.get<PublicSalonResponse>(
    `${this.apiBase}/api/salon/${slug}`,
    { params },
  );
}
```

If the existing method has a different name (`getPublicBySlug`, etc.), apply the same change to that method instead. Don't introduce a new method.

If `HttpParams` isn't already imported, add the import.

- [ ] **Step 3: Update salon-page.component.ts to forward the query param**

Open `frontend/src/app/pages/salon/salon-page.component.ts`. Find the constructor / `ngOnInit` block where the salon is fetched. Currently it reads `slug` from the route. Add a read of the `preview` query param.

Locate the existing block (something like):
```typescript
this.salonService.getPublicSalon(slug).subscribe({ next: (salon) => this.salon.set(salon), ... });
```

Replace with:
```typescript
const previewToken = this.route.snapshot.queryParamMap.get('preview');
this.salonService.getPublicSalon(slug, previewToken).subscribe({
  next: (salon) => this.salon.set(salon),
  // ... existing error / not-found handling stays
});
```

If `ActivatedRoute` is not yet injected, add:

```typescript
import { ActivatedRoute } from '@angular/router';
// ...
private readonly route = inject(ActivatedRoute);
```

If the existing `onPublishedFromBanner()` method also calls the service, update it the same way:

```typescript
const previewToken = this.route.snapshot.queryParamMap.get('preview');
this.salonService.getPublicSalon(slug, previewToken).subscribe({ ... });
```

- [ ] **Step 4: Verify TS compile + tests**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

```bash
cd frontend && npm test -- --include='**/salon-page.component.spec.ts' --include='**/salon-profile.service.spec.ts' --watch=false
```
Expected: PASS. If existing tests fail because they spied on the old signature, update the spies to accept the new optional param (it's optional, so spy calls without it should still match — but tests that explicitly assert call arguments may need adjustment).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/salon-profile/services/salon-profile.service.ts frontend/src/app/pages/salon/salon-page.component.ts
git commit -m "feat(salon-page): forward ?preview=<token> from URL to public salon API"
```

---

# Part B — PC densification (minimal scope)

## Task B1: Global `.pro-page` class

**Files:**
- Modify: `frontend/src/styles.scss`

A single global class to standardize the pro pages' top-level container.

- [ ] **Step 1: Append the class**

Open `frontend/src/styles.scss`. At the end, append:

```scss
/* === Pro page shell === */
.pro-page {
  max-width: 1440px;
  margin: 0 auto;
  padding: 16px;

  @media (min-width: 768px) {
    padding: 24px 32px;
  }

  @media (min-width: 1280px) {
    padding: 24px 48px;
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/styles.scss
git commit -m "feat(styles): add global .pro-page container class"
```

---

## Task B2: Apply `.pro-page` to dashboard, cares, employees, bookings, settings

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-dashboard.component.scss`
- Modify: `frontend/src/app/features/cares/cares.component.scss`
- Modify: `frontend/src/app/pages/pro/pro-employees.component.scss`
- Modify: `frontend/src/app/pages/pro/pro-bookings.component.scss`
- Modify: `frontend/src/app/pages/pro/pro-settings.component.scss`

For each page, find the existing top-level container rule and bump its `max-width` to `1440px`. The dashboard already has a `.dashboard-container` at 1200px — bump it.

**Important**: only the SCSS changes. Templates are not touched. Each page has a single top-level class on its first `<div>` (or section); we adjust that class's width and padding to match `.pro-page`'s constraints.

- [ ] **Step 1: Dashboard**

Open `frontend/src/app/pages/pro/pro-dashboard.component.scss`. Find `.dashboard-container` (around line 21):

```scss
.dashboard-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px;

  @media (min-width: 768px) {
    padding-top: 8px;
  }
}
```

Replace with:

```scss
.dashboard-container {
  max-width: 1440px;
  margin: 0 auto;
  padding: 16px;

  @media (min-width: 768px) {
    padding: 24px 32px;
    padding-top: 8px;
  }

  @media (min-width: 1280px) {
    padding: 24px 48px;
    padding-top: 8px;
  }
}
```

- [ ] **Step 2: Cares**

Inspect `frontend/src/app/features/cares/cares.component.scss`. Find the top-level container rule (likely `.cares-container`, `.cares-page` or similar). Apply the same pattern: `max-width: 1440px; margin: 0 auto; padding: 16px;` with media queries for `768px` (`24px 32px`) and `1280px` (`24px 48px`).

If the existing rule has a different `max-width` (e.g. 900px), bump it to 1440px and merge the padding.

- [ ] **Step 3: Employees**

Inspect `frontend/src/app/pages/pro/pro-employees.component.scss`. Apply the same pattern to its top-level container.

- [ ] **Step 4: Bookings**

Inspect `frontend/src/app/pages/pro/pro-bookings.component.scss`. Apply the same pattern.

- [ ] **Step 5: Settings**

Inspect `frontend/src/app/pages/pro/pro-settings.component.scss`. Apply the same pattern.

- [ ] **Step 6: Verify TS compile + run tests**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors (SCSS changes don't break TS).

```bash
cd frontend && npm test -- --include='**/pro-*.spec.ts' --include='**/cares.component.spec.ts' --watch=false
```
Expected: existing tests still pass (or unchanged failure count — SCSS changes don't affect test logic).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/pages/pro/pro-dashboard.component.scss frontend/src/app/features/cares/cares.component.scss frontend/src/app/pages/pro/pro-employees.component.scss frontend/src/app/pages/pro/pro-bookings.component.scss frontend/src/app/pages/pro/pro-settings.component.scss
git commit -m "feat(pro-pages): bump max-width to 1440px on dashboard, cares, employees, bookings, settings"
```

---

## Task B3: Final integration check

**Files:** none (verification + smoke test).

- [ ] **Step 1: Run the focused frontend test suite**

```bash
cd frontend && npm test -- --include='**/preview-token.service.spec.ts' --include='**/preview-share.component.spec.ts' --include='**/salon-profile.component.spec.ts' --include='**/salon-page.component.spec.ts' --watch=false
```
Expected: PASS (or known pre-existing failures unchanged).

- [ ] **Step 2: Run backend tests**

```bash
cd backend && ./mvnw test -Dtest='SalonPreviewToken*,PublicSalonController*' -q
```
Expected: PASS.

- [ ] **Step 3: TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 4: Smoke check (visual + flow)**

Start backend and frontend.

1. Login as a pro with DRAFT status.
2. Navigate to `/pro/salon` — confirm the "Partager un aperçu" section is visible at the bottom.
3. Click "Générer un lien" — a new row appears with a token short hash (last 8 chars), creation date, Copy + Revoke buttons.
4. Click "Copier" — snackbar "Lien copié". Paste into a browser address bar to inspect: format `http://localhost:4200/salon/<slug>?preview=<token>`.
5. Open an incognito window. Paste the URL. The salon DRAFT loads with the preview banner (the J2 banner; the visitor isn't an owner so the publish button shouldn't show).
6. Back in the pro session, click "Révoquer". Confirm the dialog. The row disappears.
7. Reload the incognito window. Now you get the 404 "Salon introuvable" — the token is revoked.

8. Resize the browser to 1440px+ width. Navigate to `/pro/dashboard`. Confirm the layout uses up to 1440px (was capped at 1200px). Same for `/pro/cares`, `/pro/employees`, `/pro/bookings`, `/pro/settings`.

- [ ] **Step 5: Final commit (only if Step 1-3 surfaced fix-ups)**

Skip if everything passed. Otherwise:

```bash
git add -A
git commit -m "fix(j5): address integration issues"
```

---

## Self-Review Notes

**Spec coverage check:**

| Spec requirement | Implemented in |
|------------------|----------------|
| `SalonPreviewToken` entity | Tasks A1, A2 |
| Repository | Task A2 |
| Service create/list/revoke + validation | Task A3 |
| `POST /api/pro/salon/preview-tokens` | Task A4 |
| `GET /api/pro/salon/preview-tokens` | Task A4 |
| `DELETE /api/pro/salon/preview-tokens/{id}` | Task A4 |
| Public controller accepts `?preview=<token>` and validates | Task A5 |
| Frontend service + DTO | Task A6 |
| i18n FR/EN | Task A7 |
| `<app-preview-share>` UI | Task A8 |
| Mounted in `/pro/salon` | Task A9 |
| Salon page forwards query param to API | Task A10 |
| 1440px max-width on pro pages | Tasks B1, B2 |

**Out of scope (deliberately deferred):**
- **Split-view live preview on `/pro/salon`** — defer to a follow-up. Requires factoring `SalonPageComponent` (`embeddedPreview` mode), debounced form-to-preview signal sync, and SSR handling. The J2 banner-based preview already covers the core "preview before publish" need.
- **Material `density: -2`** — too risky for many forms; accepted that pro pages are wider but not denser in this jalon.
- **Sticky page headers under the indicator stepper** — tracker bug for follow-up.
- **`/pro/cares` table-vs-cards switch** — current cards layout works; deferred polish.

**Placeholders scan:** none — all steps contain concrete code or commands. Where a file's exact line/class isn't known to the plan author (e.g. salon-profile.component.html structure), the steps explicitly say "find … apply same pattern" with enough context.

**Type consistency:**
- `PreviewTokenResponse` (TS) mirrors `PreviewTokenResponse` (Java record) field-by-field.
- `SalonPreviewTokenService.isValidForTenant(token, tenantId)` is called from `PublicSalonController.canViewStorefront(tenant, principal, previewToken)` with `tenant.getId()` as the tenantId argument — same `Long` type.
- `PreviewShareComponent` calls `PreviewTokenService.{list, create, revoke}` with signatures matching Task A6.

---

## Notes for the executing engineer

- **The salon-profile spec test has 12 known pre-existing failures** (observed in earlier jalons). Task A9 should not introduce new failures; the count stays at 12. If it climbs, investigate.
- **The Tenant entity has `findByOwnerId`** — this is what links a logged-in pro to their tenant. The controller uses this to scope the token operations.
- **Token strings are 36-char UUIDs** — long enough to be unguessable. The DB column is `VARCHAR2(64)` to leave headroom.
- **The shareUrl in PreviewTokenResponse is RELATIVE** (`/salon/<slug>?preview=<token>`). The frontend prepends `window.location.origin` when copying.
- **Densification is intentionally minimal** — only top-level max-widths. No grid restructuring, no Material density tweak, no split-views. This makes the jalon shippable in one PR with low risk.
- **Backend security**: `/api/pro/salon/preview-tokens/**` is covered by the existing `/api/pro/**` rule (authenticated + PRO role). Verify in Task A4 step 6.
