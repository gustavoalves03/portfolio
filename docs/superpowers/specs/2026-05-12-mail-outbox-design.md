# Mail outbox + Postmark

**Date** : 2026-05-12
**Branche cible** : `feat/mail-outbox` (worktree depuis `main`)
**Dépend de** : rien. **Débloque** : `project_pending_payments.md` (3 PRs Stripe, qui dépendent du pipeline mail transactionnel).

## Objectif

Remplacer le `EmailService` actuel (synchrone `@Async`, pas de garantie transactionnelle, perte de mail possible sur rollback) par un système outbox transactionnel avec Postmark en prod et Mailpit en dev. Pose les fondations pour les mails Stripe à venir (subscription, no-show, invoice ready, etc.).

## Non-objectifs

- Pas d'override From par tenant (toujours LuxPretty)
- Pas d'analytics open/click (RGPD-friendly)
- Pas d'A/B testing de templates
- Pas de double opt-in newsletter (transactionnel only)
- Pas de templates Stripe encore (viendront avec le chantier paiements)
- Pas de Postmark domain verification par tenant

## Décisions clés

| Sujet | Décision |
|---|---|
| Provider | **Postmark** (Java SDK officiel `com.postmarkapp:postmark`, transactional-only) |
| Architecture | **Outbox pattern** (DB row + worker scheduled, atomique avec la tx caller) |
| Dev local | **Mailpit** Docker (UI :8025, SMTP :1025) — pas Postmark sandbox |
| Branding | **LuxPretty** dès la PR1 (palette rose nacré `#A83E58`, monogramme LXP, Cormorant Garamond + Inter) |
| Schéma DB | **App schema** (shared), pas tenant. Colonne `tenant_slug` pour contexte. |
| Templates initiaux | **3** : `RESET_PASSWORD`, `BOOKING_CONFIRMED`, `ACCOUNT_VERIFICATION` |
| Catalogue mails | **Typed** : `MailTemplate` enum + `MailVars` sealed interface + records |
| Migration EmailService | **Rewrite** : tous les callsites passent à `MailOutboxService.queue()`, `EmailService.java` supprimé |
| Flyway | V8 (`MAIL_OUTBOX`) + V9 (colonne `USERS.EMAIL_BLOCKED`) |
| Worker | `@Scheduled fixedDelay=30s`, batch 10 par tick, `SELECT FOR UPDATE SKIP LOCKED` |
| Retry | Exponential backoff 5 essais : 1m → 5m → 25m → 2h → 12h (≈14h cumul) |
| Tx sémantique `queue()` | **REQUIRED** (rejoint la tx caller — atomicité outbox) |
| From address | Toujours `noreply@luxpretty.lu` ; `Reply-To` peut être customisé plus tard |
| Webhook secret | **Shared secret** dans header `X-Postmark-Webhook-Token` |
| Webhooks traités | Bounce hard, SpamComplaint, Delivery (pas Open/Click) |

## Architecture

### Packages

```
backend/src/main/java/com/luxpretty/app/mail/
├── domain/
│   ├── MailOutbox.java
│   ├── MailStatus.java              (PENDING, IN_FLIGHT, SENT, PERMANENTLY_FAILED)
│   └── MailTemplate.java
├── vars/
│   ├── MailVars.java                (sealed interface)
│   ├── ResetPasswordVars.java
│   ├── BookingConfirmedVars.java
│   └── AccountVerificationVars.java
├── repo/
│   └── MailOutboxRepository.java
├── app/
│   ├── MailOutboxService.java       (queue(template, vars, recipient, tenantSlug))
│   ├── MailWorker.java              (@Scheduled, @Profile("!test"))
│   ├── MailRenderer.java            (Thymeleaf + Jsoup inline CSS)
│   ├── MailSender.java              (interface)
│   ├── SmtpMailSender.java          (@Profile("dev"))
│   ├── PostmarkMailSender.java      (@Profile("prod") via app.mail.provider switch)
│   ├── MailRetryPolicy.java
│   ├── RetryableMailException.java
│   └── HardMailException.java
└── web/
    ├── PostmarkWebhookController.java
    └── dto/PostmarkWebhookPayload.java
```

Le package est **transverse** (pas une feature métier) ; il fournit l'infrastructure mail réutilisée par auth, bookings, etc.

### Flow

```
Caller @Transactional
  ├─ businessRepo.save(...)
  └─ mailOutboxService.queue(template, vars, recipient, tenantSlug)
       → INSERT INTO MAIL_OUTBOX (PENDING, next_attempt_at=NOW)
  ↓ commit OR rollback (atomique)

MailWorker @Scheduled(fixedDelay=30s)
  SELECT FROM MAIL_OUTBOX
    WHERE status='PENDING' AND next_attempt_at <= NOW
    FETCH FIRST 10 ROWS ONLY FOR UPDATE SKIP LOCKED
  For each row:
    render(template, vars) → (html, txt)
    mailSender.send(...) → providerMessageId
    ├─ success → SENT
    ├─ RetryableMailException → next_attempt_at += backoff
    │   IF attempts >= 5 → PERMANENTLY_FAILED
    └─ HardMailException → PERMANENTLY_FAILED

Postmark webhook POST /api/webhooks/postmark
  X-Postmark-Webhook-Token check (401 sinon)
  Bounce[type=HardBounce] → User.email_blocked=true
  SpamComplaint → User.email_blocked=true
  Delivery → MailOutbox.delivered_at=NOW
```

### Modèle de données

**Flyway V8** dans `db/migration/oracle/V8__create_mail_outbox.sql` :

```sql
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

**Flyway V9** dans `db/migration/oracle/V9__users_email_blocked.sql` :

```sql
ALTER TABLE USERS ADD EMAIL_BLOCKED NUMBER(1) DEFAULT 0 NOT NULL;
ALTER TABLE USERS ADD CONSTRAINT CK_USERS_EMAIL_BLOCKED CHECK (EMAIL_BLOCKED IN (0,1));
COMMENT ON COLUMN USERS.EMAIL_BLOCKED IS
  'Set to 1 on hard bounce or spam complaint via Postmark webhook. MailWorker skips sending to blocked addresses.';
```

**Justifications** :
- `VARS_JSON CLOB` : payloads typés sérialisés en JSON Jackson, désérialisation déduite du `TEMPLATE` enum côté worker.
- `TENANT_SLUG` nullable : un `RESET_PASSWORD` n'est pas lié à un tenant.
- `IN_FLIGHT` : statut transitoire entre lock et fin d'envoi ; tx du worker libère le lock à la fin.
- `NEXT_ATTEMPT_AT = NOW` au queue : envoyé au prochain tick (~30s).

### Retry policy

Exponential backoff dans `MailRetryPolicy.nextAttempt(int attempts)` :

| attempts | délai depuis NOW |
|---|---|
| 1 | 1 min |
| 2 | 5 min |
| 3 | 25 min |
| 4 | 2 h |
| 5 | _ne calcule pas_ → `PERMANENTLY_FAILED` |

Total ~14h cumulés avant abandon. Permet d'absorber une coupure Postmark de quelques heures.

**Distinction retryable / hard** dans `PostmarkMailSender` :
- Retryable : HTTP 5xx, timeout, ConnectException, IOException → throw `RetryableMailException`
- Hard : HTTP 422 (invalid email), 401 (bad token) → throw `HardMailException`

### Templates Thymeleaf

```
backend/src/main/resources/templates/mail/
├── _layout.html              fragment commun (header rose nacré + footer mentions)
├── _styles.css               CSS source, chargé une fois par MailRenderer
├── reset-password.html       extends _layout via th:replace
├── reset-password.txt        plain text (deliverability)
├── booking-confirmed.html
├── booking-confirmed.txt
├── account-verification.html
└── account-verification.txt
```

**Charte** (cohérente avec PDFs factures et logo `luxpretty-full.svg`) :
- Header : bandeau plat `#A83E58` avec monogramme LXP + wordmark "LuxPretty"
- Corps : fond `#FBF6F4`, texte `#2B1F25`, CTA `#A83E58`
- Footer : mentions sobres `#806771`, copyright

**`MailRenderer`** :
1. Charge `_styles.css` une fois au démarrage (cache mémoire).
2. Pour chaque envoi :
   - Thymeleaf rend `{template}.html` avec contexte = `MailVars`
   - Jsoup inline les styles dans `style="..."` sur chaque balise (Outlook/Gmail ignorent `<style>` externe)
   - Thymeleaf rend `{template}.txt` séparément
3. Le sujet est dérivé du `MailTemplate` enum via `subject(MailVars)`.

**Plain text** : obligatoire pour la deliverability (Postmark le recommande, anti-spam plus indulgent).

### MailVars (typage strict)

```java
public sealed interface MailVars
    permits ResetPasswordVars, BookingConfirmedVars, AccountVerificationVars {}

public record ResetPasswordVars(String userName, String resetUrl, Duration expiresIn) implements MailVars {}
public record BookingConfirmedVars(String userName, String salonName, LocalDateTime when, String careName) implements MailVars {}
public record AccountVerificationVars(String userName, String verificationUrl) implements MailVars {}
```

Force le run-time check : le worker connaît le `MailTemplate` du row et désérialise le JSON vers la classe `MailVars` concrète attendue par ce template.

Java enums ne sont pas génériques par instance, donc on garde la signature simple :

```java
public void queue(MailTemplate template, MailVars vars, String recipient, String tenantSlug);
```

Le lien template ↔ vars est validé soit :
- par convention (callers passent toujours le bon couple)
- par une méthode helper sur `MailTemplate` : `MailTemplate.BOOKING_CONFIRMED.varsClass() → BookingConfirmedVars.class`, et `queue()` peut faire `assert template.varsClass().isInstance(vars)` en debug.

C'est un compromis vs un typage compile-time strict (qui demanderait soit une factory par template, soit du code généré). Pour 3 templates initiaux le coût de mismatch runtime est faible.

### Webhook Postmark

**Endpoint** : `POST /api/webhooks/postmark` (`PostmarkWebhookController` dans `mail/web/`).

**Sécurité** : Postmark permet un header custom configurable. On déclare `X-Postmark-Webhook-Token: ${app.mail.postmark.webhook-secret}` côté Postmark. Backend rejette en 401 si manquant ou invalide. Endpoint exclu du CSRF (pattern aligné avec futurs webhooks Stripe).

**Payload** : un seul endpoint, type discriminé par `RecordType` :

```json
{ "RecordType": "Bounce",        "BounceType": "HardBounce", "Email": "...", "MessageID": "..." }
{ "RecordType": "SpamComplaint", "Email": "...", "MessageID": "..." }
{ "RecordType": "Delivery",      "Email": "...", "MessageID": "..." }
```

**Traitement** :
- `Bounce` avec `BounceType=HardBounce` → `User.email_blocked=true` + flag MailOutbox `PERMANENTLY_FAILED`
- `SpamComplaint` → `User.email_blocked=true`
- `Delivery` → `MailOutbox.delivered_at=NOW`
- Soft bounce ignoré (Postmark retry automatiquement)

**Idempotence** : tous handlers idempotents (set flag/timestamp, replay sans effet).

### Sécurité & isolation

- Webhook : shared secret obligatoire.
- `User.email_blocked` check dans le worker (1 seule source de vérité, pas au queue).
- `VARS_JSON` ne contient pas de données sensibles inutiles (pas de mot de passe, pas de token long terme — juste un reset token court ou un URL signé).
- L'outbox vit en app schema, accessible à tous les tenants via le router multi-tenant standard. Les rows portent `tenant_slug` pour traçabilité mais ne sont pas isolés physiquement (mails inter-tenants comme `RESET_PASSWORD` peuvent ne pas avoir de tenant).

## Tests

### Backend

| Test | Coverage |
|---|---|
| `MailOutboxServiceTests` | `queue()` insère PENDING avec next_attempt=now, vars sérialisées |
| `MailOutboxServiceTransactionalTests` (`@DataJpaTest`) | Rollback caller → row pas persisté (atomicité) |
| `MailWorkerTests` | Mock `MailSender`: succès → SENT, RetryableMailException → next_attempt incr, 5e → PERMANENTLY_FAILED, HardMailException → immédiat PERMANENTLY_FAILED, recipient blocked → PERMANENTLY_FAILED sans envoi |
| `MailRetryPolicyTests` | Backoff exact (1m/5m/25m/2h/12h) |
| `MailRendererTests` | Template rendu, vars injectées, CSS inliné sur balises |
| `PostmarkWebhookControllerTests` (`@WebMvcTest`) | Bounce hard → email_blocked, SpamComplaint → email_blocked, Delivery → delivered_at, secret manquant → 401, replay idempotent |
| `MailOutboxIntegrationTests` (`@SpringBootTest` + Testcontainers Mailpit) | End-to-end : queue → tick → mail livré dans Mailpit, vérifié via API HTTP Mailpit |

### Configuration tests

`@Profile("!test")` sur `MailWorker` pour qu'il ne tourne pas pendant les tests classiques. Tests d'intégration explicites activent un profile dédié ou le réactivent.

## Configuration

### Dev

`docker-compose.yml` (profile `dev`) :
```yaml
mailpit:
  image: axllent/mailpit:latest
  ports:
    - "8025:8025"
    - "1025:1025"
  profiles: ["dev"]
```

`application-dev.properties` :
```properties
spring.mail.host=localhost
spring.mail.port=1025
app.mail.provider=smtp
app.mail.from=noreply@luxpretty.local
```

### Prod

`application-prod.properties` (set quand domaine acheté) :
```properties
app.mail.provider=postmark
app.mail.postmark.api-token=${POSTMARK_API_TOKEN}
app.mail.postmark.webhook-secret=${POSTMARK_WEBHOOK_SECRET}
app.mail.from=noreply@luxpretty.lu
```

### Dépendances Maven à ajouter

```xml
<dependency>
    <groupId>com.postmarkapp</groupId>
    <artifactId>postmark</artifactId>
    <version>1.10.0</version>
</dependency>
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

> Vérifier les versions exactes au moment de l'implémentation (Maven Central). Le SDK Postmark s'appelait `com.wildbit.java:postmark` puis a été renommé en `com.postmarkapp:postmark`.

## Migration EmailService → MailOutboxService

Callsites identifiés du `EmailService` actuel :
- `AuthController.java` (reset password + account verification)
- `CareBookingService.java` (booking confirmed)

Étapes en PR2 :
1. Remplacer chaque appel `emailService.sendXxx()` par `mailOutboxService.queue(MailTemplate.XXX, vars, recipient, tenantSlug)`.
2. Supprimer `backend/src/main/java/com/luxpretty/app/notification/app/EmailService.java`.
3. Supprimer les anciens templates Thymeleaf (`welcome-pro.html`, etc.) ; les nouveaux templates vivent sous `templates/mail/`.
4. Grep CI : aucun import `EmailService` ne doit subsister.

## Error handling

- Webhook secret manquant ou invalide → 401, log structuré WARN.
- Mail render fail (template manquant, vars manquantes) → log ERROR, row marqué `PERMANENTLY_FAILED` avec `last_error`.
- Postmark API token invalide → `HardMailException` → row `PERMANENTLY_FAILED`, log CRITICAL (alerte ops).
- Worker exception non gérée → row reste `IN_FLIGHT` ; un cleanup job (out of scope ici) pourrait reset les `IN_FLIGHT` > 5min vers `PENDING`. Pour la phase actuelle, le `@Transactional` du worker garantit que les exceptions rollback le statut.

## Plan de livraison

1 worktree `feat/mail-outbox` depuis `main`, 2 PRs séquentiels.

| # | Scope | Estimation |
|---|---|---|
| **PR1 — Plumbing** | Deps Postmark + Jsoup + Mailpit Docker + Flyway V8 + entité/repo + `MailTemplate` enum + `MailVars` sealed + `MailOutboxService.queue()` + `MailWorker` + `MailRetryPolicy` + `MailSender` interface + `SmtpMailSender` + `PostmarkMailSender` + tests unitaires + smoke test Testcontainers Mailpit | ~5h |
| **PR2 — Branding + flows réels** | `_layout.html` LuxPretty + 3 templates HTML+TXT + `MailRenderer` avec Jsoup inline CSS + Flyway V9 `email_blocked` + `PostmarkWebhookController` + secret check + migration callsites `EmailService → MailOutboxService` + suppression `EmailService.java` et anciens templates | ~3h |

**Total ~8h**. Un seul push final, une seule PR sur GitHub (ou merge local comme pour les factures).

## Prérequis manuels post-implémentation

Pas de code, à faire avant d'activer le mode `postmark` en prod :

1. Acheter domaine `luxpretty.lu` (ou similaire)
2. Ajouter domaine dans Postmark dashboard
3. Configurer DNS Postmark : SPF, DKIM, DMARC (`p=none` puis escalade)
4. Set env vars `POSTMARK_API_TOKEN` + `POSTMARK_WEBHOOK_SECRET` en prod
5. Créer webhook Postmark vers `https://api.luxpretty.lu/api/webhooks/postmark` avec header custom `X-Postmark-Webhook-Token=<secret>`
6. Flip `app.mail.provider=postmark` en profil prod

## Out of scope (rappel)

- Templates Stripe → chantier paiements
- Tenant From override / Reply-To dynamique
- Open/click tracking (RGPD)
- Postmark domain verification par tenant
- A/B testing templates
- Newsletter / double opt-in

## Prérequis débloqués

Aucun blocker. Les clés API Stripe sont en attente du chantier suivant.
