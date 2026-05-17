# Spec — Email verification + Reminder J-1

**Date :** 2026-05-17
**Auteur :** Gustavo + Claude (brainstorming)
**Status :** Validé pour writing-plans

## Contexte

Deux features manquantes identifiées dans l'audit du backlog 2026-05-11 (bug B3 partiel) :

1. **Email verification** : le champ `User.emailVerified` existe en base mais aucun flux ne le set à `true`. Pas d'endpoint `/verify-email`, pas de template mail.
2. **Reminder J-1** : pas de scheduler, pas de template mail pour rappeler aux clients leur RDV du lendemain.

L'email "RDV modifié" évoqué initialement est **hors scope** (la feature "modifier un RDV" n'existe pas encore côté pro).

Le forgot password est **déjà implémenté** (endpoints `/api/auth/forgot-password` + `/reset-password`, template `RESET_PASSWORD`) — hors scope.

## Décisions de scope (validées en brainstorm)

- **Email verification client** : soft warning à l'inscription, blocage uniquement au moment de réserver un RDV
- **Email verification pro** : bloquant dès la connexion (redirect `/verify-email-required` au lieu de `/pro/dashboard`)
- **OAuth Google** : auto-vérifié (Google garantit l'email)
- **Mécanisme** : lien magique (token UUID, expiry 24h) — calque du pattern reset-password existant
- **Reminder J-1** : client uniquement, 24h avant le RDV
- **Anti-spam reminder** : skip si la confirmation de booking date de moins de 2h
- **Backfill prod** : tous les users LOCAL existants → `emailVerified=true` (zéro friction sur les utilisateurs actuels)

## Architecture globale

```
┌─────────────────────────────────────────────────────────────┐
│  Frontend (Angular)                                         │
│  - register pro: blocage redirect /verify-email-required    │
│  - register client: signup OK, blocage au booking           │
│  - /verify-email?token=<uuid> page                          │
│  - bandeau resend / boutons "Renvoyer"                      │
└─────────────────────┬───────────────────────────────────────┘
                      │ REST
┌─────────────────────▼───────────────────────────────────────┐
│  Backend (Spring Boot)                                      │
│                                                             │
│  AuthController                                             │
│  ├─ POST /api/auth/send-verification    (resend)            │
│  ├─ POST /api/auth/verify-email         (consume token)     │
│  └─ (existing) /register, /pro/register → queue VERIFY mail │
│                                                             │
│  CareBookingService                                         │
│  └─ create() → throw 403 if client AND !verified            │
│                                                             │
│  BookingReminderScheduler (NEW)                             │
│  └─ @Scheduled hourly → queue REMINDER_J1 mails             │
│                                                             │
│  MailOutboxService.queue(VERIFY_EMAIL | BOOKING_REMINDER_J1)│
│         │                                                   │
│         ▼                                                   │
│  MailWorker → Postmark → user inbox                         │
└─────────────────────────────────────────────────────────────┘
```

Les deux features partagent l'infra `MailOutboxService` existante. On ajoute 2 templates Thymeleaf + 2 endpoints auth + 1 scheduler.

## Data model

### Modification entité `User`

Ajout 2 colonnes (calque exact du pattern `password_reset_token`) :

```java
@Column(name = "email_verification_token", unique = true)
private String emailVerificationToken;

@Column(name = "email_verification_token_expires_at")
private Instant emailVerificationTokenExpiresAt;
```

Le champ `emailVerified` existe déjà (User.java l.40).

### Modification entité `CareBooking`

```java
@Column(name = "reminder_sent_at")
private Instant reminderSentAt;
```

### Migration Flyway

**Shared schema** — numéro de version à confirmer en plan (dernière migration shared = V9 selon `db/migration/oracle/`) — `V10__user_email_verification_token.sql` :

```sql
ALTER TABLE users ADD (
    email_verification_token VARCHAR2(36),
    email_verification_token_expires_at TIMESTAMP
);
CREATE UNIQUE INDEX uk_users_email_verif_token ON users(email_verification_token);

-- Backfill : tous les users existants sont considérés vérifiés (zéro friction)
UPDATE users SET email_verified = 1 WHERE email_verified = 0;
```

**Tenant schema** — `V10__booking_reminder_sent_at.sql` :

```sql
ALTER TABLE care_bookings ADD (reminder_sent_at TIMESTAMP);
CREATE INDEX ix_care_bookings_reminder ON care_bookings(appointment_date, appointment_time, reminder_sent_at);
```

### Mirror Java pour legacy tenants

Ajouter le `CREATE TABLE` / `ALTER TABLE` idempotent dans `migrateOracleSchema()` (cf. memory `feedback_legacy_tenant_create_table` : sinon ORA-00942 au boot pour les tenants existants).

## Flux email verification

### Flux 1 — Signup classique (LOCAL)

```
POST /api/auth/register | /api/auth/pro/register
  ├─ create User (emailVerified=false, token UUID, expiresAt=now+24h)
  ├─ queue VERIFY_EMAIL mail (lien: /verify-email?token=<uuid>)
  └─ return JWT  ← le client est connecté mais "non vérifié"
```

### Flux 2 — Signup OAuth Google

Dans `CustomOAuth2UserService` (méthode de création/upsert utilisateur OAuth) : appeler `user.setEmailVerified(true)` à la création. Aucun mail envoyé. Idem pour `CustomOidcUserService` si applicable.

### Flux 3 — Click sur lien de vérification

```
GET /verify-email?token=xxx  (page Angular)
  └─ POST /api/auth/verify-email { token }
       ├─ find user by token
       ├─ check expiresAt > now (sinon 400 TOKEN_EXPIRED)
       ├─ user.emailVerified = true
       ├─ user.emailVerificationToken = null
       └─ return 200 OK
  └─ frontend: redirect /pro/dashboard (pro) ou / (client) + toast "Email vérifié ✓"
```

### Flux 4 — Resend

```
POST /api/auth/send-verification      (auth required)
  ├─ if emailVerified=true → 409 ALREADY_VERIFIED
  ├─ cooldown: if token exists AND expiresAt > now-1min → 429 COOLDOWN
  ├─ regenerate token + expiresAt=now+24h
  └─ queue VERIFY_EMAIL mail
```

### Flux 5 — Blocage pro back-office (frontend guard)

```typescript
export const proEmailVerifiedGuard: CanActivateFn = () => {
  const user = inject(AuthService).currentUser();
  const router = inject(Router);
  if (user?.roles.includes('PRO') && !user.emailVerified) {
    router.navigate(['/verify-email-required']);
    return false;
  }
  return true;
};
```

Appliqué sur toutes les routes `/pro/**` sauf `/verify-email-required` lui-même.

### Flux 6 — Blocage client au booking (backend)

Dans `CareBookingService.create()` :

```java
if (!client.getEmailVerified()) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");
}
```

Frontend intercepte `403 EMAIL_NOT_VERIFIED` → ouvre `EmailNotVerifiedModalComponent` avec bouton resend.

## Flux reminder J-1

### Scheduler

```java
@Component
public class BookingReminderScheduler {

    @Scheduled(cron = "0 0 * * * *")   // toutes les heures à HH:00
    public void sendReminders() {
        Instant windowStart = Instant.now().plus(23, HOURS);
        Instant windowEnd   = Instant.now().plus(25, HOURS);
        // 2h de fenêtre pour absorber les retards du cron / restarts

        for (Tenant tenant : tenantRepository.findAll()) {
            TenantContext.setCurrentTenant(tenant.getSlug());
            try {
                processTenantReminders(tenant, windowStart, windowEnd);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
```

Calque du pattern `BirthdayScheduler` existant.

### Query repository

```java
@Query("""
    SELECT b FROM CareBooking b
    WHERE b.status = 'CONFIRMED'
      AND b.reminderSentAt IS NULL
      AND (b.appointmentDate || ' ' || b.appointmentTime) BETWEEN :start AND :end
""")
List<CareBooking> findRemindersDue(Instant start, Instant end);
```

(Détail dialect Oracle pour concat date+time : à résoudre en plan. Option fallback : fetch large par date puis filter en Java.)

### Anti-spam (par booking)

```java
for (CareBooking b : dueBookings) {
    Instant bookingCreatedAt = b.getCreatedAt();
    if (Duration.between(bookingCreatedAt, Instant.now()).toHours() < 2) {
        // confirmation trop récente, skip mais marquer pour ne pas re-checker
        b.setReminderSentAt(Instant.now());
        continue;
    }
    queueReminderMail(b);
    b.setReminderSentAt(Instant.now());
}
```

On utilise `createdAt` du booking comme proxy de "moment où la confirmation a été envoyée" (les deux sont émis dans la même transaction).

### Garanties

- **Idempotent** : `reminderSentAt IS NULL` filter + set après queue → jamais 2 mails même si scheduler tourne 2x
- **Pas de spam** : flag posé même quand on skip (confirmation < 2h)
- **Multi-tenant** : loop sur tous les tenants, `TenantContext` set/clear comme `BirthdayScheduler`
- **No-shows / annulations** : status != CONFIRMED → exclu naturellement

## Templates mails

### Nouveaux templates Thymeleaf

- `templates/mail/verify-email.html` + `verify-email.txt`
- `templates/mail/booking-reminder-j1.html` + `booking-reminder-j1.txt`

### Nouvelles classes MailVars

```java
public record VerifyEmailVars(String name, String verifyUrl) implements MailVars {
    @Override public String subject() { return "Vérifie ton email LuxPretty"; }
}

public record BookingReminderVars(
    String clientName, String salonName, String careName,
    String dateStr, String timeStr, String address,
    Long bookingId, String bookingUrl
) implements MailVars {
    @Override public String subject() { return "Rappel : ton RDV demain à " + timeStr; }
}
```

### Enum `MailTemplate` — ajouts

```java
VERIFY_EMAIL("verify-email", VerifyEmailVars.class),
BOOKING_REMINDER_J1("booking-reminder-j1", BookingReminderVars.class),
```

### `ThymeleafMailRenderer` — switch case

Ajouter 2 nouveaux cases (cf. pattern existant l.109+ pour `BOOKING_CONFIRMED`).

### i18n frontend

Clés à ajouter dans `fr.json` + `en.json` :

```
verifyEmail.required.title              "Vérifie ton email"
verifyEmail.required.body               "Pour accéder à ton espace pro, vérifie ton email."
verifyEmail.required.resend             "Renvoyer le mail"
verifyEmail.required.resendCooldown     "Mail renvoyé, vérifie ta boîte."
verifyEmail.success.title               "Email vérifié ✓"
verifyEmail.success.body                "Bienvenue sur LuxPretty"
verifyEmail.expired.title               "Lien expiré"
verifyEmail.expired.body                "Demande un nouveau lien"
verifyEmail.invalid                     "Lien invalide"

booking.blocked.notVerified.title       "Vérifie ton email pour réserver"
booking.blocked.notVerified.body        "On t'a envoyé un mail à {{email}}."
booking.blocked.notVerified.resend      "Renvoyer le mail"
```

## Frontend Angular

### Nouvelles routes

```typescript
{ path: 'verify-email', loadComponent: () => import('./pages/verify-email/verify-email.component') },
{ path: 'verify-email-required', loadComponent: () => import('./pages/verify-email-required/verify-email-required.component') },
```

### Page `verify-email` (consume token)

- Lit `?token=` depuis URL
- POST `/api/auth/verify-email` au `ngOnInit`
- 3 états : `pending` (spinner), `success` (toast + redirect après 2s), `error` (TOKEN_EXPIRED / INVALID_TOKEN + bouton renvoyer)

### Page `verify-email-required` (landing pro non-vérifié)

- Affiche email + bouton "Renvoyer"
- Bouton "Se déconnecter"
- Compte à rebours cooldown (1 min) sur le bouton renvoyer

### Modale "Email non vérifié" au booking

Intercepteur HTTP qui détecte `403 EMAIL_NOT_VERIFIED` → ouvre `EmailNotVerifiedModalComponent` :
- Affiche email cible
- Bouton "Renvoyer le mail"
- Bouton "Compris"

### Mise à jour `UserDto`

Exposer `emailVerified: boolean` dans le DTO renvoyé par `/me` et `/login` (champ présent en base, à ajouter dans `buildUserDto`).

### AuthService — méthodes ajoutées

```typescript
sendVerification(): Observable<void> {
  return this.http.post<void>(`${API_BASE_URL}/api/auth/send-verification`, {});
}
verifyEmail(token: string): Observable<void> {
  return this.http.post<void>(`${API_BASE_URL}/api/auth/verify-email`, { token });
}
```

## Error handling

### Backend — codes HTTP + payload

| Endpoint | Cas | Réponse |
|---|---|---|
| `POST /verify-email` | Token absent en DB | `400 {"error": "INVALID_TOKEN"}` |
| `POST /verify-email` | Token expiré | `400 {"error": "TOKEN_EXPIRED"}` |
| `POST /verify-email` | Email déjà vérifié | `200 {"message": "already verified"}` (idempotent) |
| `POST /send-verification` | Non authentifié | `401` |
| `POST /send-verification` | Déjà vérifié | `409 {"error": "ALREADY_VERIFIED"}` |
| `POST /send-verification` | Cooldown actif (< 1 min) | `429 {"error": "COOLDOWN", "retryAfter": 45}` |
| `POST /bookings` | Email client non vérifié | `403 {"error": "EMAIL_NOT_VERIFIED"}` |
| Pro accède /pro/** | Email pro non vérifié | Frontend guard → redirect (pas d'erreur HTTP) |

### Frontend — gestion globale

- Intercepteur HTTP intercepte 403 EMAIL_NOT_VERIFIED → ouvre modale
- Page verify-email : switch sur `error.error.error` pour afficher le bon message
- Cooldown 429 : disable bouton + countdown basé sur `retryAfter`

### Mail send failures

Le `MailWorker` existant gère retry exponentiel. Si Postmark échoue :
- `RetryableMailException` → retry automatique
- `HardMailException` → marqué FAILED en outbox, log error, pas de fallback côté verify-email (user peut cliquer "Renvoyer")
- Pour reminder J-1 : si fail → log, `reminderSentAt` reste set (pas de re-tentative)

### Scheduler reliability

- Si app crash pendant scheduler → au prochain tick, les bookings non envoyés sont récupérés (fenêtre 2h absorbe le décalage)
- Si app down > 2h pendant la fenêtre → quelques reminders ratés (acceptable, log warn)
- Lock distribué : **non** pour l'instant (single-instance VPS). Si scale-out, ajouter `@SchedulerLock` (ShedLock).

## Testing

### Backend — tests unitaires

**`AuthControllerTests`** (extension fichier existant)
- `verifyEmail_validToken_setsVerifiedTrue`
- `verifyEmail_expiredToken_returns400`
- `verifyEmail_invalidToken_returns400`
- `verifyEmail_alreadyVerified_returns200_idempotent`
- `sendVerification_alreadyVerified_returns409`
- `sendVerification_cooldownActive_returns429`
- `sendVerification_queuesMail` (mock `MailOutboxService`)

**`BookingReminderSchedulerTests`** (nouveau)
- `sendReminders_bookingDueIn24h_queuesMail`
- `sendReminders_bookingDueIn48h_skipped`
- `sendReminders_bookingAlreadyReminded_skipped`
- `sendReminders_bookingCreatedLessThan2hAgo_skippedButMarked`
- `sendReminders_cancelledBooking_skipped`
- `sendReminders_multiTenant_processesAllTenants`

### Backend — tests d'intégration

**`EmailVerificationIntegrationTests`** (nouveau, `@SpringBootTest`)
- Full flow : register → assert mail queued → consume token → assert `emailVerified=true`
- Booking blocked : create booking avec user non-vérifié → assert 403 EMAIL_NOT_VERIFIED

**`BookingReminderIntegrationTests`** (nouveau)
- Setup booking dans tenant test, scheduler manuel, assert mail outbox

### Frontend — tests Karma

- `verify-email.component.spec.ts` (token URL, success, error states)
- `verify-email-required.component.spec.ts` (cooldown, logout)
- `pro-email-verified.guard.spec.ts` (PRO non vérifié redirect, client non bloqué)
- `email-not-verified-modal.component.spec.ts` (resend, affichage email)

### Coverage cible

- 80%+ sur les nouveaux fichiers
- Pas de tests E2E Playwright pour cette feature

## Migration et déploiement

### Ordre de déploiement

1 PR unique (les 2 features partagent l'infra mail + auth).

### Étapes prod

1. Merge PR → CI build l'image Docker
2. Flyway s'exécute au boot :
   - Shared : `V10__user_email_verification_token.sql`
   - Tenant : `V10__booking_reminder_sent_at.sql`
   - **+ mirror Java** dans `migrateOracleSchema()` pour legacy tenants
3. Backfill OAuth : inclus dans la migration shared (`UPDATE users SET email_verified = 1 WHERE email_verified = 0`)
4. Postmark : créer les 2 templates côté dashboard si nécessaire (selon config render Thymeleaf)
5. Variables d'env : aucune nouvelle (réutilise `APP_FRONTEND_URL`, `POSTMARK_TOKEN`)

### Backfill — politique validée

Tous les users LOCAL existants → `emailVerified=true` (zéro friction sur utilisateurs actuels). La feature s'applique uniquement aux nouveaux signups post-deploy.

### Risques + mitigations

| Risque | Mitigation |
|---|---|
| Scheduler tourne sur un schéma legacy sans colonne `reminder_sent_at` | Mirror Java assure la colonne |
| Mail Postmark down → reminders perdus | `reminderSentAt` set quand même → pas de re-envoi (acceptable, log warn) |
| Pro Google OAuth créé avant le fix `emailVerified=true` | Backfill global couvre aussi les OAuth |

## Liens

- Memory : `project_pending_product_backlog_2026_05_11.md` (B3 partiel)
- Memory : `feedback_legacy_tenant_create_table.md` (mirror Java obligatoire)
- Memory : `project_pending_mail_outbox.md` (MailWorker live)
- Backlog source : `docs/superpowers/backlog/2026-05-11-product-backlog.md` (B3)
- Pattern référence : `backend/.../config/BirthdayScheduler.java`
- Pattern référence : `AuthController.forgotPassword` (l.273) + `resetPassword` (l.302)
