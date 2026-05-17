# Email Verification + Reminder J-1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add email verification flow (token-based, client soft / pro blocking) and J-1 booking reminder cron, both routed through existing MailOutboxService.

**Architecture:**
- Backend: 2 nouvelles colonnes sur `User`, 1 sur `CareBooking`, 2 endpoints AuthController, 1 scheduler hourly multi-tenant calqué sur `BirthdayScheduler`, 2 templates Thymeleaf.
- Frontend: 2 pages (`/verify-email`, `/verify-email-required`), 1 guard pro, 1 modale "booking blocked", expose `emailVerified` dans UserDto.
- Réutilise intégralement `MailOutboxService`, `MailWorker`, `ResponseStatusException`, `TenantContext`.

**Tech Stack:** Spring Boot 3.5 / Java 21 / JPA / Oracle / Flyway, Angular 20 standalone / SignalStore, Thymeleaf, Postmark via MailWorker existant.

**Spec source:** `docs/superpowers/specs/2026-05-17-email-verification-and-reminder-design.md`

**Note importante détectée à l'exploration:** Le code OAuth dans `CustomOAuth2UserService` set déjà `emailVerified=true` (l.112, 149). **Aucun travail OAuth nécessaire.**

---

## File structure

### Backend — création

- `backend/.../mail/vars/VerifyEmailVars.java` — record MailVars
- `backend/.../mail/vars/BookingReminderVars.java` — record MailVars
- `backend/src/main/resources/templates/mail/verify-email.html`
- `backend/src/main/resources/templates/mail/verify-email.txt`
- `backend/src/main/resources/templates/mail/booking-reminder-j1.html`
- `backend/src/main/resources/templates/mail/booking-reminder-j1.txt`
- `backend/src/main/resources/db/migration/oracle/V10__user_email_verification_token.sql`
- `backend/src/main/resources/db/migration/tenant/V{N}__booking_reminder_sent_at.sql` (numéro à confirmer)
- `backend/.../bookings/app/BookingReminderScheduler.java`
- `backend/src/test/java/.../auth/EmailVerificationAuthControllerTests.java`
- `backend/src/test/java/.../bookings/BookingReminderSchedulerTests.java`

### Backend — modification

- `backend/.../users/domain/User.java` — ajouter `emailVerificationToken`, `emailVerificationTokenExpiresAt`
- `backend/.../bookings/domain/CareBooking.java` — ajouter `reminderSentAt`
- `backend/.../bookings/repo/CareBookingRepository.java` — ajouter `findRemindersDue`
- `backend/.../users/repo/UserRepository.java` — ajouter `findByEmailVerificationToken`
- `backend/.../mail/domain/MailTemplate.java` — ajouter `VERIFY_EMAIL`, `BOOKING_REMINDER_J1`
- `backend/.../mail/app/ThymeleafMailRenderer.java` — ajouter subject cases
- `backend/.../auth/AuthController.java` — ajouter `verifyEmail`, `sendVerification`, déclencher mail au register (LOCAL)
- `backend/.../bookings/app/CareBookingService.java` — guard 403 EMAIL_NOT_VERIFIED dans `create()`
- `backend/.../config/TenantSchemaMigrator.java` (ou équivalent legacy mirror, à localiser) — mirror Java pour la colonne `reminder_sent_at`
- `backend/.../me/web/dto/UserDto.java` — exposer `emailVerified`
- `backend/.../auth/AuthController.java#buildUserDto` — set `emailVerified` dans le DTO

### Frontend — création

- `frontend/src/app/pages/verify-email/verify-email.component.ts/html/scss`
- `frontend/src/app/pages/verify-email/verify-email.component.spec.ts`
- `frontend/src/app/pages/verify-email-required/verify-email-required.component.ts/html/scss`
- `frontend/src/app/pages/verify-email-required/verify-email-required.component.spec.ts`
- `frontend/src/app/core/auth/pro-email-verified.guard.ts`
- `frontend/src/app/core/auth/pro-email-verified.guard.spec.ts`
- `frontend/src/app/features/bookings/modals/email-not-verified-modal/email-not-verified-modal.component.ts/html/scss`
- `frontend/src/app/features/bookings/modals/email-not-verified-modal/email-not-verified-modal.component.spec.ts`

### Frontend — modification

- `frontend/src/app/core/auth/auth.service.ts` — ajouter `verifyEmail()`, `sendVerification()`
- `frontend/src/app/core/auth/auth.model.ts` — ajouter `emailVerified: boolean` à UserDto
- `frontend/src/app/app.routes.ts` — routes `/verify-email` et `/verify-email-required`
- `frontend/src/app/features/bookings/...` — intercepter erreur 403 EMAIL_NOT_VERIFIED
- `frontend/src/assets/i18n/fr.json` + `en.json` — clés `verifyEmail.*` + `booking.blocked.notVerified.*`

---

## Phase 1 — Migrations Flyway + entités

### Task 1: Migration shared schema (User token columns + backfill)

**Files:**
- Create: `backend/src/main/resources/db/migration/oracle/V10__user_email_verification_token.sql`

- [ ] **Step 1: Vérifier le numéro de migration**

Run: `ls backend/src/main/resources/db/migration/oracle/ | sort -V | tail -3`
Expected: dernière migration = `V9__users_email_blocked.sql`. Donc on prend `V10`. Si une `V10` existe déjà (autre branche merged), incrémenter.

- [ ] **Step 2: Créer la migration**

```sql
-- V10__user_email_verification_token.sql
ALTER TABLE users ADD (
    email_verification_token VARCHAR2(36),
    email_verification_token_expires_at TIMESTAMP
);

CREATE UNIQUE INDEX uk_users_email_verif_token ON users(email_verification_token);

-- Backfill : tous les users existants sont considérés vérifiés (zéro friction).
-- La feature s'applique aux nouveaux signups post-deploy.
UPDATE users SET email_verified = 1 WHERE email_verified = 0;
COMMIT;
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/oracle/V10__user_email_verification_token.sql
git commit -m "feat(auth): V10 add email verification token columns + backfill existing users"
```

---

### Task 2: Migration tenant schema (CareBooking reminder_sent_at)

**Files:**
- Create: `backend/src/main/resources/db/migration/tenant/V{N}__booking_reminder_sent_at.sql`

- [ ] **Step 1: Vérifier le numéro tenant**

Run: `ls backend/src/main/resources/db/migration/tenant/ | sort -V | tail -3`
Expected: prendre le numéro après le plus récent. Adapter le nom du fichier si nécessaire.

- [ ] **Step 2: Créer la migration tenant**

```sql
-- V{N}__booking_reminder_sent_at.sql
ALTER TABLE care_bookings ADD (reminder_sent_at TIMESTAMP);

CREATE INDEX ix_care_bookings_reminder
    ON care_bookings(appointment_date, appointment_time, reminder_sent_at);
```

- [ ] **Step 3: Mirror Java pour legacy tenants**

Run: `grep -rln "migrateOracleSchema\|CREATE TABLE CARE_BOOKINGS\|CREATE TABLE care_bookings" backend/src/main/java`
Expected: typiquement un fichier dans `multitenancy/` ou `config/` qui contient les `CREATE TABLE ...` idempotents par tenant.

Dans ce fichier, repérer le bloc qui crée/migre `care_bookings` et ajouter (juste après le `CREATE TABLE` ou comme `ALTER TABLE` idempotent enveloppé dans try/catch ORA-01430) :

```java
try {
    jdbcTemplate.execute("ALTER TABLE care_bookings ADD (reminder_sent_at TIMESTAMP)");
} catch (DataAccessException e) {
    if (!e.getMessage().contains("ORA-01430")) throw e; // column already exists → ignore
}
```

Si le pattern existant utilise un check `USER_TAB_COLUMNS` plutôt que try/catch, le copier.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/tenant/ backend/src/main/java/com/luxpretty/app/config/
git commit -m "feat(bookings): tenant migration + legacy mirror for reminder_sent_at"
```

---

### Task 3: Ajouter les champs à l'entité User

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/users/domain/User.java`

- [ ] **Step 1: Ajouter les colonnes JPA**

Ajouter après les colonnes `passwordResetToken` (vers l.57-61) :

```java
@Column(name = "email_verification_token", unique = true)
private String emailVerificationToken;

@Column(name = "email_verification_token_expires_at")
private java.time.Instant emailVerificationTokenExpiresAt;
```

- [ ] **Step 2: Build pour vérifier**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/users/domain/User.java
git commit -m "feat(users): add email verification token fields to User entity"
```

---

### Task 4: Ajouter le champ reminderSentAt à CareBooking

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/bookings/domain/CareBooking.java`

- [ ] **Step 1: Ajouter la colonne JPA**

Ajouter (avant les colonnes audit `createdAt`) :

```java
@Column(name = "reminder_sent_at")
private java.time.Instant reminderSentAt;
```

- [ ] **Step 2: Build**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/domain/CareBooking.java
git commit -m "feat(bookings): add reminderSentAt field to CareBooking entity"
```

---

## Phase 2 — Mail infrastructure (templates + vars + renderer)

### Task 5: Créer les classes MailVars

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/mail/vars/VerifyEmailVars.java`
- Create: `backend/src/main/java/com/luxpretty/app/mail/vars/BookingReminderVars.java`

- [ ] **Step 1: VerifyEmailVars**

```java
package com.luxpretty.app.mail.vars;

public record VerifyEmailVars(String name, String verifyUrl) implements MailVars {}
```

- [ ] **Step 2: BookingReminderVars**

```java
package com.luxpretty.app.mail.vars;

public record BookingReminderVars(
        String clientName,
        String salonName,
        String careName,
        String dateStr,
        String timeStr,
        String address,
        Long bookingId,
        String bookingUrl
) implements MailVars {}
```

- [ ] **Step 3: Build**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/mail/vars/
git commit -m "feat(mail): add VerifyEmailVars + BookingReminderVars records"
```

---

### Task 6: Enregistrer les templates dans l'enum MailTemplate

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/mail/domain/MailTemplate.java`

- [ ] **Step 1: Ajouter les imports + entries**

```java
import com.luxpretty.app.mail.vars.VerifyEmailVars;
import com.luxpretty.app.mail.vars.BookingReminderVars;
```

Dans l'enum, ajouter avant la dernière entry :

```java
VERIFY_EMAIL("verify-email", VerifyEmailVars.class),
BOOKING_REMINDER_J1("booking-reminder-j1", BookingReminderVars.class),
```

- [ ] **Step 2: Mettre à jour ThymeleafMailRenderer.subjectFor**

Modify: `backend/src/main/java/com/luxpretty/app/mail/app/ThymeleafMailRenderer.java`

Ajouter dans le `switch` (méthode `subjectFor` autour l.106) :

```java
case VERIFY_EMAIL -> "Vérifie ton email LuxPretty";
case BOOKING_REMINDER_J1 -> {
    BookingReminderVars v = (BookingReminderVars) vars;
    yield "Rappel : ton RDV demain à " + v.timeStr();
}
```

Et ajouter l'import :

```java
import com.luxpretty.app.mail.vars.BookingReminderVars;
```

- [ ] **Step 3: Build**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS (le compilateur exige tous les cases de l'enum dans le switch)

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/mail/domain/MailTemplate.java backend/src/main/java/com/luxpretty/app/mail/app/ThymeleafMailRenderer.java
git commit -m "feat(mail): register VERIFY_EMAIL + BOOKING_REMINDER_J1 templates"
```

---

### Task 7: Templates Thymeleaf — verify-email

**Files:**
- Create: `backend/src/main/resources/templates/mail/verify-email.html`
- Create: `backend/src/main/resources/templates/mail/verify-email.txt`

- [ ] **Step 1: Inspecter un template existant pour calquer la structure**

Run: `cat backend/src/main/resources/templates/mail/reset-password.html | head -40`
Expected: voir layout/base structure utilisé

- [ ] **Step 2: Créer verify-email.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
    <meta charset="UTF-8">
    <title>Vérifie ton email LuxPretty</title>
    <style th:utext="${_styles}"></style>
</head>
<body>
<div class="container">
    <h1>Bonjour <span th:text="${name}">Camille</span> ✨</h1>
    <p>Bienvenue sur <strong>LuxPretty</strong>. Pour activer ton compte, vérifie ton email en cliquant sur le bouton ci-dessous :</p>
    <p style="text-align: center;">
        <a th:href="${verifyUrl}" class="cta">Vérifier mon email</a>
    </p>
    <p class="muted">Ce lien expire dans 24 heures. Si tu n'as pas créé de compte, ignore ce mail.</p>
    <p class="muted">Le lien direct : <a th:href="${verifyUrl}" th:text="${verifyUrl}"></a></p>
</div>
</body>
</html>
```

- [ ] **Step 3: Créer verify-email.txt**

```
Bonjour {{name}},

Bienvenue sur LuxPretty.

Pour activer ton compte, vérifie ton email en cliquant sur ce lien :
{{verifyUrl}}

Ce lien expire dans 24 heures.
Si tu n'as pas créé de compte, ignore ce mail.

— L'équipe LuxPretty
```

Note: utiliser la syntaxe Thymeleaf si elle est utilisée dans le .txt existant (`cat reset-password.txt`).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/templates/mail/verify-email.*
git commit -m "feat(mail): add verify-email Thymeleaf templates (html + txt)"
```

---

### Task 8: Templates Thymeleaf — booking-reminder-j1

**Files:**
- Create: `backend/src/main/resources/templates/mail/booking-reminder-j1.html`
- Create: `backend/src/main/resources/templates/mail/booking-reminder-j1.txt`

- [ ] **Step 1: Calquer sur booking-confirmed**

Run: `cat backend/src/main/resources/templates/mail/booking-confirmed.html | head -60`
Expected: voir la structure carte RDV (date/heure/soin/salon)

- [ ] **Step 2: Créer booking-reminder-j1.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
    <meta charset="UTF-8">
    <title>Rappel — ton RDV demain</title>
    <style th:utext="${_styles}"></style>
</head>
<body>
<div class="container">
    <h1>Rappel : ton RDV est demain ✨</h1>
    <p>Bonjour <span th:text="${clientName}">Camille</span>,</p>
    <p>Petite piqûre de rappel pour ton rendez-vous chez <strong th:text="${salonName}">Salon</strong>.</p>

    <div class="card">
        <p><strong>Soin :</strong> <span th:text="${careName}"></span></p>
        <p><strong>Date :</strong> <span th:text="${dateStr}"></span></p>
        <p><strong>Heure :</strong> <span th:text="${timeStr}"></span></p>
        <p th:if="${address}"><strong>Adresse :</strong> <span th:text="${address}"></span></p>
    </div>

    <p style="text-align: center;">
        <a th:href="${bookingUrl}" class="cta">Voir mon RDV</a>
    </p>
    <p class="muted">À demain ! Si tu ne peux pas venir, pense à annuler le plus tôt possible.</p>
</div>
</body>
</html>
```

- [ ] **Step 3: Créer booking-reminder-j1.txt**

```
Bonjour {{clientName}},

Rappel : tu as RDV demain chez {{salonName}}.

  Soin    : {{careName}}
  Date    : {{dateStr}}
  Heure   : {{timeStr}}

Voir ton RDV : {{bookingUrl}}

À demain !
— LuxPretty
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/templates/mail/booking-reminder-j1.*
git commit -m "feat(mail): add booking-reminder-j1 Thymeleaf templates"
```

---

## Phase 3 — Endpoints AuthController (TDD)

### Task 9: Test verify-email (success path)

**Files:**
- Create: `backend/src/test/java/com/luxpretty/app/auth/EmailVerificationAuthControllerTests.java`

- [ ] **Step 1: Écrire le test failing**

```java
package com.luxpretty.app.auth;

import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationAuthControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepo;

    @Test
    void verifyEmail_validToken_setsEmailVerifiedTrue() throws Exception {
        String token = UUID.randomUUID().toString();
        User u = userRepo.save(User.builder()
            .name("Test").email("test-verif@example.com")
            .password("x").provider(com.luxpretty.app.auth.AuthProvider.LOCAL)
            .emailVerified(false)
            .emailVerificationToken(token)
            .emailVerificationTokenExpiresAt(Instant.now().plusSeconds(3600))
            .build());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
            .andExpect(status().isOk());

        User reloaded = userRepo.findById(u.getId()).orElseThrow();
        assertThat(reloaded.getEmailVerified()).isTrue();
        assertThat(reloaded.getEmailVerificationToken()).isNull();
    }
}
```

- [ ] **Step 2: Run test (doit FAIL — endpoint pas encore défini)**

Run: `cd backend && mvn test -Dtest=EmailVerificationAuthControllerTests -q`
Expected: FAIL — endpoint not found (404) ou compile error si méthode repo absente

- [ ] **Step 3: Commit (test seul, rouge attendu)**

```bash
git add backend/src/test/java/com/luxpretty/app/auth/EmailVerificationAuthControllerTests.java
git commit -m "test(auth): add failing test for verify-email endpoint"
```

---

### Task 10: Implémenter UserRepository.findByEmailVerificationToken

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/users/repo/UserRepository.java`

- [ ] **Step 1: Ajouter la méthode**

```java
Optional<User> findByEmailVerificationToken(String token);
```

- [ ] **Step 2: Build**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/users/repo/UserRepository.java
git commit -m "feat(users): add findByEmailVerificationToken repo method"
```

---

### Task 11: Implémenter AuthController.verifyEmail

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/AuthController.java`
- Create: `backend/src/main/java/com/luxpretty/app/auth/dto/VerifyEmailRequest.java`

- [ ] **Step 1: Créer le DTO**

```java
package com.luxpretty.app.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(@NotBlank String token) {}
```

- [ ] **Step 2: Ajouter l'endpoint dans AuthController**

Insérer après `resetPassword` (vers l.302+) :

```java
@PostMapping("/verify-email")
@Transactional
public ResponseEntity<Map<String, String>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    User user = userRepository.findByEmailVerificationToken(request.token())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN"));

    if (Boolean.TRUE.equals(user.getEmailVerified())) {
        return ResponseEntity.ok(Map.of("message", "already verified"));
    }

    if (user.getEmailVerificationTokenExpiresAt() == null
            || user.getEmailVerificationTokenExpiresAt().isBefore(Instant.now())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED");
    }

    user.setEmailVerified(true);
    user.setEmailVerificationToken(null);
    user.setEmailVerificationTokenExpiresAt(null);
    userRepository.save(user);

    return ResponseEntity.ok(Map.of("message", "Email verified"));
}
```

- [ ] **Step 3: Ajouter l'import**

```java
import com.luxpretty.app.auth.dto.VerifyEmailRequest;
```

- [ ] **Step 4: Run test (doit PASS)**

Run: `cd backend && mvn test -Dtest=EmailVerificationAuthControllerTests#verifyEmail_validToken_setsEmailVerifiedTrue -q`
Expected: PASS

- [ ] **Step 5: Whitelist /verify-email dans Spring Security**

Run: `grep -rn "permitAll\|/forgot-password\|/reset-password" backend/src/main/java/com/luxpretty/app/config/ | head -10`
Expected: trouver le `SecurityConfig.java` (ou équivalent) qui contient la liste `permitAll()` pour les endpoints publics. Ajouter `/api/auth/verify-email` à la liste (PAS `/api/auth/send-verification` qui requiert auth).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/auth/
git commit -m "feat(auth): POST /api/auth/verify-email endpoint"
```

---

### Task 12: Tests verify-email — cas d'erreur

**Files:**
- Modify: `backend/src/test/java/com/luxpretty/app/auth/EmailVerificationAuthControllerTests.java`

- [ ] **Step 1: Ajouter les tests d'erreur**

```java
@Test
void verifyEmail_invalidToken_returns400() throws Exception {
    mockMvc.perform(post("/api/auth/verify-email")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"does-not-exist\"}"))
        .andExpect(status().isBadRequest());
}

@Test
void verifyEmail_expiredToken_returns400() throws Exception {
    String token = UUID.randomUUID().toString();
    userRepo.save(User.builder()
        .name("Expired").email("expired@example.com")
        .password("x").provider(com.luxpretty.app.auth.AuthProvider.LOCAL)
        .emailVerified(false)
        .emailVerificationToken(token)
        .emailVerificationTokenExpiresAt(Instant.now().minusSeconds(60))
        .build());

    mockMvc.perform(post("/api/auth/verify-email")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\"}"))
        .andExpect(status().isBadRequest());
}

@Test
void verifyEmail_alreadyVerified_returns200_idempotent() throws Exception {
    String token = UUID.randomUUID().toString();
    userRepo.save(User.builder()
        .name("Done").email("done@example.com")
        .password("x").provider(com.luxpretty.app.auth.AuthProvider.LOCAL)
        .emailVerified(true)
        .emailVerificationToken(token)
        .emailVerificationTokenExpiresAt(Instant.now().plusSeconds(3600))
        .build());

    mockMvc.perform(post("/api/auth/verify-email")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\"}"))
        .andExpect(status().isOk());
}
```

- [ ] **Step 2: Run tests (doivent PASS)**

Run: `cd backend && mvn test -Dtest=EmailVerificationAuthControllerTests -q`
Expected: 4 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/luxpretty/app/auth/EmailVerificationAuthControllerTests.java
git commit -m "test(auth): cover verify-email error cases (invalid/expired/already)"
```

---

### Task 13: Endpoint send-verification (resend)

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/AuthController.java`
- Modify: `backend/src/test/java/com/luxpretty/app/auth/EmailVerificationAuthControllerTests.java`

- [ ] **Step 1: Repérer le pattern de test authentifié existant**

Run: `grep -rn "@WithMockUser\|@WithUserDetails\|UserPrincipal" backend/src/test/java --include="*.java" | head -10`
Expected: voir comment les tests authentifiés sont écrits dans ce repo (probablement `@WithMockUser` ou injection manuelle de `UserPrincipal` via `SecurityContextHolder`).

- [ ] **Step 2: Test failing**

Ajouter au fichier test, en calquant le pattern repéré au step 1 :

```java
@Test
void sendVerification_unauthenticated_returns401() throws Exception {
    mockMvc.perform(post("/api/auth/send-verification"))
        .andExpect(status().isUnauthorized());
}
```

Pour les tests authentifiés (alreadyVerified, cooldown), utiliser le pattern existant. Si `@WithMockUser` est utilisé ailleurs, créer une fixture user en base via `@BeforeEach`, puis annoter le test avec `@WithMockUser(username = "<email>")` et configurer `SecurityContext` pour exposer le `UserPrincipal` avec l'id du fixture user. Si trop complexe, faire un test de service direct (`SendVerificationServiceTests`) en bypassant MockMvc.

- [ ] **Step 3: Run (FAIL)**

Run: `cd backend && mvn test -Dtest=EmailVerificationAuthControllerTests -q`
Expected: nouveaux tests FAIL

- [ ] **Step 4: Implémenter l'endpoint**

Dans AuthController, ajouter après `verifyEmail` :

```java
@PostMapping("/send-verification")
@Transactional
public ResponseEntity<?> sendVerification(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
        return ResponseEntity.status(401).build();
    }
    UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
    User user = userRepository.findById(principal.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    if (Boolean.TRUE.equals(user.getEmailVerified())) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "ALREADY_VERIFIED"));
    }

    // Cooldown : 1 minute
    if (user.getEmailVerificationToken() != null
            && user.getEmailVerificationTokenExpiresAt() != null
            && user.getEmailVerificationTokenExpiresAt().isAfter(Instant.now().plusSeconds(3600 * 23 + 60))) {
        // token créé il y a moins d'1 minute → expiresAt > now + 23h59min
        return ResponseEntity.status(429).body(Map.of("error", "COOLDOWN", "retryAfter", 60));
    }

    String token = UUID.randomUUID().toString();
    user.setEmailVerificationToken(token);
    user.setEmailVerificationTokenExpiresAt(Instant.now().plusSeconds(3600 * 24));
    userRepository.save(user);

    mailOutbox.queue(
        MailTemplate.VERIFY_EMAIL,
        new VerifyEmailVars(user.getName(), frontendBaseUrl + "/verify-email?token=" + token),
        user.getEmail(),
        null);

    return ResponseEntity.ok(Map.of("message", "Verification email sent"));
}
```

- [ ] **Step 5: Ajouter les imports**

```java
import com.luxpretty.app.mail.vars.VerifyEmailVars;
```

- [ ] **Step 6: Run tests (PASS)**

Run: `cd backend && mvn test -Dtest=EmailVerificationAuthControllerTests -q`
Expected: tous tests PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/auth/AuthController.java backend/src/test/java/com/luxpretty/app/auth/EmailVerificationAuthControllerTests.java
git commit -m "feat(auth): POST /api/auth/send-verification with cooldown"
```

---

### Task 14: Déclencher le mail VERIFY au register LOCAL

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/AuthController.java`

- [ ] **Step 1: Repérer les méthodes register**

Run: `grep -n "register\|PostMapping" backend/src/main/java/com/luxpretty/app/auth/AuthController.java | head -10`
Expected: identifier `@PostMapping("/register")` et `@PostMapping("/pro/register")`

- [ ] **Step 2: Helper interne**

Ajouter une méthode privée dans AuthController :

```java
private void queueVerificationMail(User user) {
    String token = UUID.randomUUID().toString();
    user.setEmailVerificationToken(token);
    user.setEmailVerificationTokenExpiresAt(Instant.now().plusSeconds(3600 * 24));
    userRepository.save(user);

    mailOutbox.queue(
        MailTemplate.VERIFY_EMAIL,
        new VerifyEmailVars(user.getName(), frontendBaseUrl + "/verify-email?token=" + token),
        user.getEmail(),
        null);
}
```

- [ ] **Step 3: Appeler après chaque register LOCAL**

Dans les deux méthodes register (client + pro), après `userRepository.save(user)` et avant `buildAuthResponse` :

```java
queueVerificationMail(user);
```

Note : ne PAS appeler pour les flows OAuth (le code OAuth set déjà `emailVerified=true`).

- [ ] **Step 4: Build + test smoke**

Run: `cd backend && mvn test -Dtest=EmailVerificationAuthControllerTests -q`
Expected: PASS (les tests existants ne doivent pas casser)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/auth/AuthController.java
git commit -m "feat(auth): queue VERIFY_EMAIL mail on LOCAL register (client + pro)"
```

---

### Task 15: Exposer emailVerified dans UserDto

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/me/web/dto/UserDto.java` (chemin à confirmer)
- Modify: `backend/src/main/java/com/luxpretty/app/auth/AuthController.java#buildUserDto`

- [ ] **Step 1: Localiser UserDto**

Run: `find backend/src/main/java -name "UserDto.java"`
Expected: 1 fichier

- [ ] **Step 2: Ajouter le champ Lombok**

Dans `UserDto` (record ou class @Builder) :

```java
@Builder.Default
private Boolean emailVerified = false;
```

(adapter au style record/class du DTO existant)

- [ ] **Step 3: Set dans buildUserDto**

Dans `AuthController.buildUserDto` (l.332+), ajouter avant `.build()` :

```java
.emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
```

- [ ] **Step 4: Build**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/
git commit -m "feat(users): expose emailVerified in UserDto"
```

---

## Phase 4 — Blocage client au booking

### Task 16: Test blocage booking client non vérifié

**Files:**
- Modify: `backend/src/test/java/.../bookings/CareBookingServiceTests.java` (ou créer fichier dédié)

- [ ] **Step 1: Repérer le test corpus existant pour CareBookingService**

Run: `find backend/src/test -name "*CareBooking*"`
Expected: lister tests existants pour calquer le style

- [ ] **Step 2: Repérer un test existant qui crée un booking**

Run: `grep -rn "careBookingService.create\|CreateBookingRequest\|new CreateBookingRequest" backend/src/test/java --include="*.java" | head -5`
Expected: voir au moins un test existant qui construit un `CreateBookingRequest` avec ses champs. Copier exactement la structure de l'objet pour la fixture.

- [ ] **Step 3: Test failing — calqué sur la fixture trouvée**

```java
@Test
void create_clientEmailNotVerified_throws403() {
    User client = userRepo.save(User.builder()
        .name("Test").email("noverif@example.com")
        .password("x").provider(AuthProvider.LOCAL)
        .emailVerified(false).build());

    // Construire CreateBookingRequest avec les champs minimaux requis
    // (calquer EXACTEMENT sur la fixture du test existant repéré au step 2)
    CreateBookingRequest req = /* fixture identique au test existant, juste changer le client */;

    assertThatThrownBy(() -> careBookingService.create(req, client.getId()))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
            ResponseStatusException rse = (ResponseStatusException) e;
            assertThat(rse.getStatusCode().value()).isEqualTo(403);
            assertThat(rse.getReason()).isEqualTo("EMAIL_NOT_VERIFIED");
        });
}
```

Note: si la signature de `create()` ne prend pas `clientUserId` en argument (ex: récupéré depuis le `SecurityContext`), adapter le test pour setter le contexte avant l'appel.

- [ ] **Step 4: Run (FAIL)**

Run: `cd backend && mvn test -Dtest=CareBookingServiceTests#create_clientEmailNotVerified_throws403 -q`
Expected: FAIL

- [ ] **Step 5: Commit (rouge)**

```bash
git add backend/src/test/java/com/luxpretty/app/bookings/
git commit -m "test(bookings): failing test for booking blocked when email not verified"
```

---

### Task 17: Implémenter le guard 403 dans CareBookingService

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java`

- [ ] **Step 1: Ajouter le guard**

Dans la méthode `create()` au tout début (avant les autres validations) :

```java
User client = userRepository.findById(clientUserId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));

if (!Boolean.TRUE.equals(client.getEmailVerified())) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");
}
```

(adapter selon signature actuelle de `create()` et la façon dont le client est résolu)

- [ ] **Step 2: Run test (PASS)**

Run: `cd backend && mvn test -Dtest=CareBookingServiceTests -q`
Expected: PASS

- [ ] **Step 3: Vérifier qu'aucun autre test n'est cassé**

Run: `cd backend && mvn test -Dtest='*BookingServiceTests' -q`
Expected: tous PASS — sinon mettre à jour les fixtures qui ne settent pas emailVerified

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java
git commit -m "feat(bookings): block booking with 403 EMAIL_NOT_VERIFIED when client not verified"
```

---

## Phase 5 — Scheduler reminder J-1 (TDD)

### Task 18: CareBookingRepository.findRemindersDue

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/bookings/repo/CareBookingRepository.java`

- [ ] **Step 1: Inspecter les méthodes existantes**

Run: `grep -n "@Query\|Optional\|List<" backend/src/main/java/com/luxpretty/app/bookings/repo/CareBookingRepository.java`
Expected: voir le style des requêtes existantes (JPQL vs native)

- [ ] **Step 2: Ajouter la méthode**

```java
@Query("""
    SELECT b FROM CareBooking b
    WHERE b.status = com.luxpretty.app.bookings.domain.BookingStatus.CONFIRMED
      AND b.reminderSentAt IS NULL
      AND b.appointmentDate = :targetDate
""")
List<CareBooking> findRemindersDueOnDate(@Param("targetDate") LocalDate targetDate);
```

**Note:** simplification — on filtre par date entière (J+1) côté SQL puis on affine en Java sur l'heure exacte. Ça évite de combiner date+time dans la requête Oracle.

- [ ] **Step 3: Build**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/repo/CareBookingRepository.java
git commit -m "feat(bookings): findRemindersDueOnDate repo method"
```

---

### Task 19: Test BookingReminderScheduler — happy path

**Files:**
- Create: `backend/src/test/java/com/luxpretty/app/bookings/BookingReminderSchedulerTests.java`

- [ ] **Step 1: Test failing**

```java
package com.luxpretty.app.bookings;

import com.luxpretty.app.bookings.app.BookingReminderScheduler;
import com.luxpretty.app.mail.app.MailOutboxService;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.vars.BookingReminderVars;
// ... autres imports

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class BookingReminderSchedulerTests {

    @Autowired private BookingReminderScheduler scheduler;
    @SpyBean   private MailOutboxService mailOutbox;

    @Test
    void sendReminders_bookingDueTomorrow_queuesMail() {
        // setup tenant + booking pour demain via fixtures
        // appel manuel scheduler.sendReminders()
        verify(mailOutbox, times(1)).queue(
            eq(MailTemplate.BOOKING_REMINDER_J1),
            any(BookingReminderVars.class),
            any(String.class),
            any());
    }
}
```

- [ ] **Step 2: Run (FAIL)**

Run: `cd backend && mvn test -Dtest=BookingReminderSchedulerTests -q`
Expected: FAIL (scheduler n'existe pas)

- [ ] **Step 3: Commit (rouge)**

```bash
git add backend/src/test/java/com/luxpretty/app/bookings/BookingReminderSchedulerTests.java
git commit -m "test(bookings): failing test for J-1 reminder scheduler"
```

---

### Task 20: Implémenter BookingReminderScheduler

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/bookings/app/BookingReminderScheduler.java`

- [ ] **Step 1: Calquer sur BirthdayScheduler**

Run: `cat backend/src/main/java/com/luxpretty/app/config/BirthdayScheduler.java`
Expected: voir le pattern TenantContext + loop tenants

- [ ] **Step 2: Créer la classe**

```java
package com.luxpretty.app.bookings.app;

import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.care.repo.CareRepository;
import com.luxpretty.app.mail.app.MailOutboxService;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.vars.BookingReminderVars;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.tracking.repo.SalonClientRepository;
import com.luxpretty.app.tracking.domain.SalonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class BookingReminderScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BookingReminderScheduler.class);
    private static final Duration CONFIRMATION_FRESHNESS = Duration.ofHours(2);

    private final TenantRepository tenantRepository;
    private final CareBookingRepository bookingRepo;
    private final CareRepository careRepo;
    private final SalonClientRepository clientRepo;
    private final MailOutboxService mailOutbox;

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    public BookingReminderScheduler(TenantRepository tenantRepository,
                                     CareBookingRepository bookingRepo,
                                     CareRepository careRepo,
                                     SalonClientRepository clientRepo,
                                     MailOutboxService mailOutbox) {
        this.tenantRepository = tenantRepository;
        this.bookingRepo = bookingRepo;
        this.careRepo = careRepo;
        this.clientRepo = clientRepo;
        this.mailOutbox = mailOutbox;
    }

    @Scheduled(cron = "0 0 * * * *")  // toutes les heures à HH:00
    public void sendReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        for (Tenant tenant : tenantRepository.findAll()) {
            try {
                TenantContext.setCurrentTenant(tenant.getSlug());
                processTenantReminders(tenant, tomorrow);
            } catch (Exception e) {
                logger.error("Reminder scheduler failed for tenant {}", tenant.getSlug(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Transactional
    public void processTenantReminders(Tenant tenant, LocalDate targetDate) {
        List<CareBooking> due = bookingRepo.findRemindersDueOnDate(targetDate);
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        Instant now = Instant.now();

        for (CareBooking b : due) {
            // Anti-spam : skip si confirmation < 2h, mais marquer pour ne pas re-checker
            if (b.getCreatedAt() != null
                    && Duration.between(b.getCreatedAt(), now).compareTo(CONFIRMATION_FRESHNESS) < 0) {
                b.setReminderSentAt(now);
                bookingRepo.save(b);
                continue;
            }

            SalonClient client = clientRepo.findById(b.getClientId()).orElse(null);
            var care = careRepo.findById(b.getCareId()).orElse(null);
            if (client == null || care == null || client.getEmail() == null) {
                b.setReminderSentAt(now);
                bookingRepo.save(b);
                continue;
            }

            mailOutbox.queue(
                MailTemplate.BOOKING_REMINDER_J1,
                new BookingReminderVars(
                    client.getName(), tenant.getName(), care.getName(),
                    b.getAppointmentDate().format(dateFmt),
                    b.getAppointmentTime().format(timeFmt),
                    tenant.getAddress(),  // si dispo, sinon null
                    b.getId(),
                    frontendBaseUrl + "/bookings/" + b.getId()),
                client.getEmail(),
                tenant.getSlug());

            b.setReminderSentAt(now);
            bookingRepo.save(b);
            logger.info("Reminder J-1 queued for booking {} (tenant {})", b.getId(), tenant.getSlug());
        }
    }
}
```

Note : adapter les getters (`getClientId`, `getCareId`, `getAddress`) selon les noms réels des entités. Le sous-agent doit `grep` pour vérifier.

- [ ] **Step 3: Run test happy path**

Run: `cd backend && mvn test -Dtest=BookingReminderSchedulerTests#sendReminders_bookingDueTomorrow_queuesMail -q`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/app/BookingReminderScheduler.java
git commit -m "feat(bookings): hourly J-1 reminder scheduler with anti-spam guard"
```

---

### Task 21: Tests scheduler — cas edge

**Files:**
- Modify: `backend/src/test/java/com/luxpretty/app/bookings/BookingReminderSchedulerTests.java`

- [ ] **Step 1: Tests à ajouter**

```java
@Test
void sendReminders_bookingAlreadyReminded_skipped() {
    // setup booking demain avec reminderSentAt non null
    scheduler.sendReminders();
    verify(mailOutbox, never()).queue(eq(MailTemplate.BOOKING_REMINDER_J1), any(), any(), any());
}

@Test
void sendReminders_cancelledBooking_skipped() {
    // setup booking demain status=CANCELLED
    scheduler.sendReminders();
    verify(mailOutbox, never()).queue(eq(MailTemplate.BOOKING_REMINDER_J1), any(), any(), any());
}

@Test
void sendReminders_confirmationLessThan2hAgo_skippedButMarked() {
    // setup booking demain createdAt = now - 30min
    scheduler.sendReminders();
    verify(mailOutbox, never()).queue(eq(MailTemplate.BOOKING_REMINDER_J1), any(), any(), any());
    // assert booking.reminderSentAt != null
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && mvn test -Dtest=BookingReminderSchedulerTests -q`
Expected: tous PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/luxpretty/app/bookings/BookingReminderSchedulerTests.java
git commit -m "test(bookings): cover scheduler edge cases (already-sent, cancelled, fresh)"
```

---

## Phase 6 — Frontend Angular

### Task 22: AuthService — méthodes verify + send

**Files:**
- Modify: `frontend/src/app/core/auth/auth.service.ts`
- Modify: `frontend/src/app/core/auth/auth.model.ts`

- [ ] **Step 1: Ajouter emailVerified au modèle UserDto**

Dans `auth.model.ts`, repérer interface UserDto et ajouter :

```typescript
emailVerified: boolean;
```

- [ ] **Step 2: Ajouter les méthodes au service**

Dans `auth.service.ts` :

```typescript
verifyEmail(token: string): Observable<{ message: string }> {
  return this.http.post<{ message: string }>(
    `${this.apiBaseUrl}/api/auth/verify-email`,
    { token }
  );
}

sendVerification(): Observable<{ message: string }> {
  return this.http.post<{ message: string }>(
    `${this.apiBaseUrl}/api/auth/send-verification`,
    {}
  );
}
```

(adapter `apiBaseUrl` au nom utilisé dans le service existant)

- [ ] **Step 3: Build**

Run: `cd frontend && npm run build -- --configuration=development`
Expected: build successful

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/core/auth/
git commit -m "feat(auth): AuthService.verifyEmail + sendVerification methods"
```

---

### Task 23: Page /verify-email (consume token)

**Files:**
- Create: `frontend/src/app/pages/verify-email/verify-email.component.ts`
- Create: `frontend/src/app/pages/verify-email/verify-email.component.html`
- Create: `frontend/src/app/pages/verify-email/verify-email.component.scss`

- [ ] **Step 1: Composant standalone**

```typescript
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoModule } from '@jsverse/transloco';
import { AuthService } from '../../core/auth/auth.service';

type VerifyState = 'pending' | 'success' | 'expired' | 'invalid';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatProgressSpinnerModule, TranslocoModule],
  templateUrl: './verify-email.component.html',
  styleUrl: './verify-email.component.scss'
})
export class VerifyEmailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private auth = inject(AuthService);

  state = signal<VerifyState>('pending');

  ngOnInit() {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.state.set('invalid');
      return;
    }
    this.auth.verifyEmail(token).subscribe({
      next: () => {
        this.state.set('success');
        setTimeout(() => this.router.navigate(['/']), 2000);
      },
      error: (err) => {
        const code = err?.error?.error || err?.error?.message;
        if (code === 'TOKEN_EXPIRED' || code?.includes('expired')) {
          this.state.set('expired');
        } else {
          this.state.set('invalid');
        }
      }
    });
  }

  resend() {
    this.auth.sendVerification().subscribe();
  }
}
```

- [ ] **Step 2: HTML**

```html
<div class="container">
  @if (state() === 'pending') {
    <mat-spinner></mat-spinner>
    <p>{{ 'common.loading' | transloco }}</p>
  }
  @if (state() === 'success') {
    <h1>{{ 'verifyEmail.success.title' | transloco }}</h1>
    <p>{{ 'verifyEmail.success.body' | transloco }}</p>
  }
  @if (state() === 'expired') {
    <h1>{{ 'verifyEmail.expired.title' | transloco }}</h1>
    <p>{{ 'verifyEmail.expired.body' | transloco }}</p>
    <button mat-flat-button color="primary" (click)="resend()">
      {{ 'verifyEmail.required.resend' | transloco }}
    </button>
  }
  @if (state() === 'invalid') {
    <h1>{{ 'verifyEmail.invalid' | transloco }}</h1>
    <a mat-stroked-button routerLink="/">{{ 'common.home' | transloco }}</a>
  }
</div>
```

- [ ] **Step 3: SCSS minimal**

```scss
.container {
  max-width: 520px;
  margin: 4rem auto;
  text-align: center;
  padding: 2rem;
}
```

- [ ] **Step 4: Ajouter la route**

Modify: `frontend/src/app/app.routes.ts`

```typescript
{
  path: 'verify-email',
  loadComponent: () => import('./pages/verify-email/verify-email.component').then(m => m.VerifyEmailComponent)
},
```

- [ ] **Step 5: Build**

Run: `cd frontend && npm run build -- --configuration=development`
Expected: build successful

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/verify-email/ frontend/src/app/app.routes.ts
git commit -m "feat(verify-email): page consuming token + 4 UI states"
```

---

### Task 24: Page /verify-email-required (landing pro)

**Files:**
- Create: `frontend/src/app/pages/verify-email-required/verify-email-required.component.ts/html/scss`

- [ ] **Step 1: Composant**

```typescript
import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-verify-email-required',
  standalone: true,
  imports: [CommonModule, MatButtonModule, TranslocoModule],
  templateUrl: './verify-email-required.component.html',
  styleUrl: './verify-email-required.component.scss'
})
export class VerifyEmailRequiredComponent {
  private auth = inject(AuthService);
  private snackBar = inject(MatSnackBar);
  private transloco = inject(TranslocoService);

  user = this.auth.currentUser;
  cooldownSec = signal(0);

  resend() {
    this.auth.sendVerification().subscribe({
      next: () => {
        this.snackBar.open(
          this.transloco.translate('verifyEmail.required.resendCooldown'),
          'OK',
          { duration: 4000 }
        );
        this.startCooldown(60);
      },
      error: (err) => {
        if (err.status === 429) {
          this.startCooldown(err.error?.retryAfter ?? 60);
        }
      }
    });
  }

  private startCooldown(sec: number) {
    this.cooldownSec.set(sec);
    const interval = setInterval(() => {
      const next = this.cooldownSec() - 1;
      if (next <= 0) {
        clearInterval(interval);
        this.cooldownSec.set(0);
      } else {
        this.cooldownSec.set(next);
      }
    }, 1000);
  }

  logout() {
    this.auth.logout();
  }
}
```

- [ ] **Step 2: HTML**

```html
<div class="container">
  <h1>{{ 'verifyEmail.required.title' | transloco }}</h1>
  <p>{{ 'verifyEmail.required.body' | transloco }}</p>
  <p class="email" *ngIf="user()">{{ user()?.email }}</p>

  <button mat-flat-button color="primary"
          [disabled]="cooldownSec() > 0"
          (click)="resend()">
    @if (cooldownSec() > 0) {
      {{ 'verifyEmail.required.resend' | transloco }} ({{ cooldownSec() }}s)
    } @else {
      {{ 'verifyEmail.required.resend' | transloco }}
    }
  </button>

  <button mat-stroked-button (click)="logout()">
    {{ 'common.logout' | transloco }}
  </button>
</div>
```

- [ ] **Step 3: SCSS**

```scss
.container {
  max-width: 520px;
  margin: 4rem auto;
  text-align: center;
  padding: 2rem;
  .email { font-weight: 500; opacity: 0.7; }
  button { margin: 0.5rem; }
}
```

- [ ] **Step 4: Route**

```typescript
{
  path: 'verify-email-required',
  loadComponent: () => import('./pages/verify-email-required/verify-email-required.component').then(m => m.VerifyEmailRequiredComponent)
},
```

- [ ] **Step 5: Build**

Run: `cd frontend && npm run build -- --configuration=development`
Expected: build successful

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/verify-email-required/ frontend/src/app/app.routes.ts
git commit -m "feat(verify-email-required): pro landing page with resend cooldown"
```

---

### Task 25: Guard proEmailVerifiedGuard

**Files:**
- Create: `frontend/src/app/core/auth/pro-email-verified.guard.ts`

- [ ] **Step 1: Guard fonctionnel**

```typescript
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const proEmailVerifiedGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.currentUser();

  if (user?.roles?.includes('PRO') && !user.emailVerified) {
    router.navigate(['/verify-email-required']);
    return false;
  }
  return true;
};
```

- [ ] **Step 2: Appliquer sur les routes /pro/**

Modify: `frontend/src/app/app.routes.ts`

Repérer les routes `/pro/**` et ajouter le guard :

```typescript
{
  path: 'pro',
  canActivate: [authGuard, proEmailVerifiedGuard],  // ajouter après authGuard existant
  children: [...]
},
```

Ne PAS l'ajouter sur `/verify-email-required` (sinon loop).

- [ ] **Step 3: Build**

Run: `cd frontend && npm run build -- --configuration=development`
Expected: build successful

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/core/auth/pro-email-verified.guard.ts frontend/src/app/app.routes.ts
git commit -m "feat(auth): proEmailVerifiedGuard redirects unverified pros"
```

---

### Task 26: Modale "Email non vérifié" au booking

**Files:**
- Create: `frontend/src/app/features/bookings/modals/email-not-verified-modal/email-not-verified-modal.component.ts/html/scss`

- [ ] **Step 1: Modale standalone**

```typescript
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../../../../core/auth/auth.service';

@Component({
  selector: 'app-email-not-verified-modal',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatDialogModule, TranslocoModule],
  templateUrl: './email-not-verified-modal.component.html'
})
export class EmailNotVerifiedModalComponent {
  private auth = inject(AuthService);
  private dialogRef = inject(MatDialogRef<EmailNotVerifiedModalComponent>);
  private snackBar = inject(MatSnackBar);
  private transloco = inject(TranslocoService);

  user = this.auth.currentUser;

  resend() {
    this.auth.sendVerification().subscribe({
      next: () => {
        this.snackBar.open(
          this.transloco.translate('verifyEmail.required.resendCooldown'),
          'OK', { duration: 4000 });
        this.dialogRef.close();
      }
    });
  }
}
```

- [ ] **Step 2: HTML**

```html
<h2 mat-dialog-title>{{ 'booking.blocked.notVerified.title' | transloco }}</h2>
<mat-dialog-content>
  <p [innerHTML]="'booking.blocked.notVerified.body' | transloco:{ email: user()?.email }"></p>
</mat-dialog-content>
<mat-dialog-actions>
  <button mat-button mat-dialog-close>{{ 'common.cancel' | transloco }}</button>
  <button mat-flat-button color="primary" (click)="resend()">
    {{ 'booking.blocked.notVerified.resend' | transloco }}
  </button>
</mat-dialog-actions>
```

- [ ] **Step 3: Brancher l'ouverture sur erreur 403 EMAIL_NOT_VERIFIED**

Run: `grep -rn "createBooking\|book.*subscribe\|EMAIL_NOT_VERIFIED" frontend/src/app/features/bookings --include="*.ts" | head -10`
Expected: trouver le call site qui POST /bookings

Au call site (ex. `booking-stepper.component.ts` ou store) :

```typescript
this.bookingsService.create(req).subscribe({
  next: (booking) => { /* succès existant */ },
  error: (err) => {
    if (err.status === 403 && err.error?.error === 'EMAIL_NOT_VERIFIED') {
      this.dialog.open(EmailNotVerifiedModalComponent, { width: '420px' });
      return;
    }
    // gestion erreur existante
  }
});
```

- [ ] **Step 4: Build**

Run: `cd frontend && npm run build -- --configuration=development`
Expected: build successful

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/bookings/
git commit -m "feat(bookings): modal + 403 interception for unverified email"
```

---

### Task 27: i18n FR + EN

**Files:**
- Modify: `frontend/src/assets/i18n/fr.json`
- Modify: `frontend/src/assets/i18n/en.json`

- [ ] **Step 1: Ajouter les clés FR**

Dans `fr.json`, ajouter sous la racine :

```json
"verifyEmail": {
  "required": {
    "title": "Vérifie ton email",
    "body": "Pour accéder à ton espace pro, vérifie ton email.",
    "resend": "Renvoyer le mail",
    "resendCooldown": "Mail renvoyé, vérifie ta boîte."
  },
  "success": {
    "title": "Email vérifié ✓",
    "body": "Bienvenue sur LuxPretty"
  },
  "expired": {
    "title": "Lien expiré",
    "body": "Demande un nouveau lien"
  },
  "invalid": "Lien invalide"
},
"booking": {
  "blocked": {
    "notVerified": {
      "title": "Vérifie ton email pour réserver",
      "body": "On t'a envoyé un mail à {{email}}.",
      "resend": "Renvoyer le mail"
    }
  }
}
```

(Si `booking` existe déjà, merger sans casser les clés existantes.)

- [ ] **Step 2: Clés EN équivalentes**

```json
"verifyEmail": {
  "required": {
    "title": "Verify your email",
    "body": "To access your pro space, please verify your email.",
    "resend": "Resend email",
    "resendCooldown": "Email sent, check your inbox."
  },
  "success": {
    "title": "Email verified ✓",
    "body": "Welcome to LuxPretty"
  },
  "expired": {
    "title": "Link expired",
    "body": "Request a new link"
  },
  "invalid": "Invalid link"
},
"booking": {
  "blocked": {
    "notVerified": {
      "title": "Verify your email to book",
      "body": "We sent an email to {{email}}.",
      "resend": "Resend email"
    }
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/assets/i18n/
git commit -m "i18n: verifyEmail.* + booking.blocked.notVerified.* keys (fr + en)"
```

---

## Phase 7 — Tests frontend

### Task 28: Test verify-email.component

**Files:**
- Create: `frontend/src/app/pages/verify-email/verify-email.component.spec.ts`

- [ ] **Step 1: Spec**

```typescript
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { VerifyEmailComponent } from './verify-email.component';
import { AuthService } from '../../core/auth/auth.service';

describe('VerifyEmailComponent', () => {
  let fixture: ComponentFixture<VerifyEmailComponent>;
  let auth: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    auth = jasmine.createSpyObj('AuthService', ['verifyEmail', 'sendVerification']);

    TestBed.configureTestingModule({
      imports: [VerifyEmailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: {
          snapshot: { queryParamMap: { get: () => 'valid-token' } }
        }},
      ]
    });
  });

  it('shows success when token is valid', () => {
    auth.verifyEmail.and.returnValue(of({ message: 'Email verified' }));
    fixture = TestBed.createComponent(VerifyEmailComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('success');
  });

  it('shows expired state on TOKEN_EXPIRED error', () => {
    auth.verifyEmail.and.returnValue(throwError(() => ({
      error: { error: 'TOKEN_EXPIRED' }
    })));
    fixture = TestBed.createComponent(VerifyEmailComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('expired');
  });

  it('shows invalid state when token absent', () => {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { snapshot: { queryParamMap: { get: () => null } } }
    });
    fixture = TestBed.createComponent(VerifyEmailComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('invalid');
  });
});
```

- [ ] **Step 2: Run**

Run: `cd frontend && npm test -- --include='**/verify-email.component.spec.ts' --watch=false`
Expected: 3 specs PASS

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/verify-email/verify-email.component.spec.ts
git commit -m "test(verify-email): success/expired/invalid states"
```

---

### Task 29: Test proEmailVerifiedGuard

**Files:**
- Create: `frontend/src/app/core/auth/pro-email-verified.guard.spec.ts`

- [ ] **Step 1: Spec**

```typescript
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { signal } from '@angular/core';
import { proEmailVerifiedGuard } from './pro-email-verified.guard';
import { AuthService } from './auth.service';

describe('proEmailVerifiedGuard', () => {
  let router: jasmine.SpyObj<Router>;
  let auth: { currentUser: any };

  function run() {
    return TestBed.runInInjectionContext(() => proEmailVerifiedGuard({} as any, {} as any));
  }

  beforeEach(() => {
    router = jasmine.createSpyObj('Router', ['navigate']);
    auth = { currentUser: signal(null) };
    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: auth },
      ]
    });
  });

  it('returns true for verified PRO', () => {
    auth.currentUser = signal({ roles: ['PRO'], emailVerified: true });
    TestBed.overrideProvider(AuthService, { useValue: auth });
    expect(run()).toBeTrue();
  });

  it('redirects unverified PRO', () => {
    auth.currentUser = signal({ roles: ['PRO'], emailVerified: false });
    TestBed.overrideProvider(AuthService, { useValue: auth });
    expect(run()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/verify-email-required']);
  });

  it('returns true for non-PRO user even if unverified', () => {
    auth.currentUser = signal({ roles: ['USER'], emailVerified: false });
    TestBed.overrideProvider(AuthService, { useValue: auth });
    expect(run()).toBeTrue();
  });
});
```

- [ ] **Step 2: Run**

Run: `cd frontend && npm test -- --include='**/pro-email-verified.guard.spec.ts' --watch=false`
Expected: 3 specs PASS

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/core/auth/pro-email-verified.guard.spec.ts
git commit -m "test(auth): proEmailVerifiedGuard 3 scenarios"
```

---

## Phase 8 — Vérification finale

### Task 30: Build + tests full

- [ ] **Step 1: Backend full tests**

Run: `cd backend && mvn test -q`
Expected: BUILD SUCCESS, 0 failures

- [ ] **Step 2: Frontend full tests**

Run: `cd frontend && npm test -- --watch=false`
Expected: 0 failed specs

- [ ] **Step 3: Frontend lint**

Run: `cd frontend && npm run lint 2>&1 | tail -20` (si lint configuré)
Expected: 0 errors

- [ ] **Step 4: Frontend build prod**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 5: Smoke test manuel local**

1. Démarrer backend : `cd backend && mvn spring-boot:run`
2. Démarrer frontend : `cd frontend && npm start`
3. Signup nouveau client → vérifier `mail_outbox` table contient `VERIFY_EMAIL`
4. Récupérer token en DB (`SELECT email_verification_token FROM users WHERE email = ...`)
5. Ouvrir `http://localhost:4200/verify-email?token=<token>` → état success
6. Tenter de réserver avec user non vérifié → modale "Email non vérifié"
7. Signup nouveau pro → ne peut pas accéder à `/pro/dashboard` → redirect `/verify-email-required`

- [ ] **Step 6: Commit final + push**

Run: `git log --oneline -30`
Expected: voir tous les commits de la feature

```bash
git push origin <branch>
```

---

## Récap des commits

Phase 1 : 4 commits (migrations + entités)
Phase 2 : 4 commits (mail vars + templates + renderer)
Phase 3 : 7 commits (verify-email + send-verification + register hook + UserDto)
Phase 4 : 2 commits (blocage booking client)
Phase 5 : 4 commits (scheduler + tests)
Phase 6 : 6 commits (frontend pages + guard + modale + i18n)
Phase 7 : 2 commits (tests frontend)
Phase 8 : 1 commit de finalisation

**Total : ~30 commits incrémentaux.**

---

## Notes

- **Tenant context** : le scheduler set/clear `TenantContext` pour chaque tenant — vérifier qu'aucune fuite entre tenants.
- **Performance** : `findAll()` sur `TenantRepository` est OK tant qu'on a < quelques centaines de tenants. Au-delà, paginer.
- **Backfill prod** : la migration shared (`UPDATE users SET email_verified = 1`) doit s'exécuter AVANT que le guard 403 du booking ne soit déployé — sinon clients existants bloqués au moment du redémarrage. Comme Flyway s'exécute au boot avant que l'app accepte du trafic, c'est OK.
- **Cooldown send-verification** : implémenté en comparant `expiresAt` au lieu de stocker un `lastSentAt` séparé. Si on veut un vrai compteur de tentatives, ajouter `email_verification_send_count` plus tard.
- **Email rebond Postmark** : utilise déjà le `email_blocked` flag (`User.emailBlocked`). Pas besoin de logique additionnelle pour les rebonds.
