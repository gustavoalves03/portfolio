# Stripe PR1 — Pro Subscription (Backend + minimal frontend)

> **Document unique** combinant architecture, spec, TDD tests et tickets découpés pour exécution efficace. Cible : MVP en ligne vendredi 19 mai 2026.

**Date :** 2026-05-15
**Provider :** Stripe (subscription only — no Connect, no CVC hold dans cette PR)
**Scope :** Backend + minimal frontend (pricing page + signup checkout flow)
**Effort estimé :** ~2 jours (16h) en 11 tickets indépendants.

---

## 0. Architecture cible

### 0.1 Vision

Tout pro inscrit s'abonne à LuxPretty pour accéder aux outils de gestion (dashboard, employés, réservation). Trois tiers :
- **Vitrine** (0€) : page publique salon, posts, infos. **Pas de prise de RDV en ligne.**
- **Gestion** (49,99€/mois ou 42,49€/mois en annuel) : + 2 employés + dashboard + réservation
- **Premium** (67,99€/mois ou 57,79€/mois en annuel) : + paiements Stripe + mails notifs clients

Trial 7 jours **carte requise** au signup. Cancel à tout moment via Stripe Customer Portal (pas de remboursement prorata — accès jusqu'à fin de période payée).

Le **paywall fonctionnel par tier** (ex. Vitrine ne peut pas activer la réservation, Premium peut activer les paiements Stripe) viendra en **PR2**. Cette PR1 pose les fondations : Customer + Subscription + Trial + SubscriptionGuard global qui bloque `/api/pro/**` si statut ≠ TRIALING/ACTIVE.

### 0.2 Décisions architecturales

Reprises de `project_pending_payments.md` (memo 2026-05-05) :

| Sujet | Décision |
|---|---|
| Provider | Stripe |
| Modèle économique | Subscription only (no marketplace commission) |
| Card capture | SetupIntent au signup → attache à Customer → Subscription `trial_period_days=7` |
| Webhook events | `customer.subscription.{created,updated,deleted}`, `invoice.paid`, `invoice.payment_failed`, `customer.subscription.trial_will_end` |
| Customer Portal | Stripe-hosted (download invoice, change card, cancel) |
| VAT | Stripe Tax activé dès le 1er jour |
| Cancellation | No prorated refund. Access until end of paid period. |
| Card storage | Zero (tokens Stripe only) |
| Géographie | EU complète |

### 0.3 Schéma de données

**Nouvelles colonnes sur `TENANTS` (app schema) :**

```sql
ALTER TABLE "${appSchema}".TENANTS ADD (
    STRIPE_CUSTOMER_ID       VARCHAR2(255 CHAR),
    STRIPE_SUBSCRIPTION_ID   VARCHAR2(255 CHAR),
    SUBSCRIPTION_STATUS      VARCHAR2(32 CHAR),     -- TRIALING, ACTIVE, PAST_DUE, CANCELED, INCOMPLETE, INCOMPLETE_EXPIRED, UNPAID, PAUSED
    SUBSCRIPTION_TIER        VARCHAR2(32 CHAR),     -- VITRINE, GESTION, PREMIUM
    SUBSCRIPTION_BILLING     VARCHAR2(16 CHAR),     -- MONTHLY, YEARLY, FREE
    CURRENT_PERIOD_END       TIMESTAMP,             -- fin de période payée actuelle
    TRIAL_END                TIMESTAMP              -- fin du trial 7j (null si pas en trial)
);

CREATE UNIQUE INDEX UK_TENANTS_STRIPE_CUSTOMER 
    ON "${appSchema}".TENANTS (STRIPE_CUSTOMER_ID);
CREATE UNIQUE INDEX UK_TENANTS_STRIPE_SUBSCRIPTION 
    ON "${appSchema}".TENANTS (STRIPE_SUBSCRIPTION_ID);
CREATE INDEX IX_TENANTS_SUB_STATUS 
    ON "${appSchema}".TENANTS (SUBSCRIPTION_STATUS);
```

**Backfill V10** :
- Tenants existants → `SUBSCRIPTION_TIER='VITRINE'`, `SUBSCRIPTION_STATUS='ACTIVE'` (legacy = grandfathered en vitrine gratuite)
- Pas de stripe_customer_id (sera créé à la 1ère interaction)

### 0.4 Flow signup pro

```
[Pricing page UI]
   ↓ user clicks "Choisir Gestion"
[Signup form] (email, password, salon name, tier choice, monthly/yearly)
   ↓ POST /api/auth/register/pro
[AuthController.registerPro]
   1. Create User + Tenant (existing flow)
   2. UserRoleService.assignOnTenant(PRO, tenant)  ← already exists
   3. EmployeeService.createSelfEmployee  ← already exists
   4. **NEW**: StripeService.createCustomer(user, tenant) → returns customerId
   5. **NEW**: tenant.setStripeCustomerId(customerId), tier=VITRINE par défaut
   6. Return AuthResponse with JWT
   
   ↓ (si user a choisi Gestion ou Premium)
[Frontend redirect to /pro/onboarding/payment]
   ↓ POST /api/pro/subscription/setup-intent
[SubscriptionController.createSetupIntent]
   1. Retrieve tenant.stripeCustomerId
   2. stripe.setupIntents.create({customer, payment_method_types: ['card'], usage: 'off_session'})
   3. Return client_secret to frontend
   
   ↓ frontend uses Stripe Elements to collect card
[Stripe.confirmCardSetup(client_secret)]
   1. Card token attached to Customer
   2. SetupIntent succeeded webhook fires → no-op (we wait for subscription create)
   
   ↓ POST /api/pro/subscription/create {tier: 'GESTION', billing: 'MONTHLY'}
[SubscriptionController.createSubscription]
   1. Lookup priceId from tier+billing combo (server-side map, not client-controlled)
   2. stripe.subscriptions.create({
        customer: tenant.stripeCustomerId,
        items: [{price: priceId}],
        trial_period_days: 7,
        default_payment_method: <from setup intent>,
        automatic_tax: {enabled: true},
        payment_settings: {save_default_payment_method: 'on_subscription'}
      })
   3. Webhook customer.subscription.created fires → updates tenant fields
   4. Return subscription summary to frontend
   
   ↓ Frontend redirect to /pro/dashboard
[Pro can now use the app, trial 7d before first charge]
```

### 0.5 Flow webhook

```
[Stripe sends event]
   ↓ POST /api/webhooks/stripe (raw body + signature header)
[StripeWebhookController.handle]
   1. Verify signature: stripe.webhooks.constructEvent(body, sig, webhookSecret)
   2. Dispatch by event type:
      - customer.subscription.created/updated → SubscriptionEventHandler.onSubscriptionChange
      - customer.subscription.deleted → SubscriptionEventHandler.onCancellation
      - invoice.paid → SubscriptionEventHandler.onInvoicePaid (queue mail "Paiement confirmé")
      - invoice.payment_failed → SubscriptionEventHandler.onPaymentFailed (queue mail "Paiement échoué")
      - customer.subscription.trial_will_end → SubscriptionEventHandler.onTrialWillEnd (queue mail "Trial finit dans 3 jours")
   3. Mark Stripe event as processed in DB (idempotency, see 0.6)
   4. Return 200 within 5 seconds (Stripe retries on timeout/non-2xx)
```

### 0.6 Idempotency

Stripe peut renvoyer le même webhook plusieurs fois. On stocke chaque `event.id` reçu dans une table `STRIPE_EVENTS_PROCESSED (event_id, type, processed_at)` avec contrainte unique. Si on reçoit un event déjà dans la table → on skip silencieusement et on renvoie 200.

### 0.7 SubscriptionGuard

Filter Spring sur les routes `/api/pro/**` (sauf `/api/pro/subscription/**` et `/api/auth/**`) :

```java
if (tenant.subscriptionStatus IN (TRIALING, ACTIVE, PAST_DUE_grace_window)) {
    proceed;
} else {
    return 402 PAYMENT_REQUIRED + {redirect: "/pro/onboarding/payment"};
}
```

`PAST_DUE_grace_window` = 7 jours après la première facture impayée. Au-delà, blocage total.

**Tenants Vitrine (gratuits)** : `subscriptionStatus=ACTIVE` + `subscriptionTier=VITRINE`. Le guard global les laisse passer (le tier-based feature gating viendra en PR2).

### 0.8 Stripe Products & Prices (setup manuel)

Pré-requis avant code : créer dans Stripe Dashboard (test mode) :

```
Product: LuxPretty Gestion
  Price: gestion_monthly   - 49.99 EUR/month - recurring
  Price: gestion_yearly    - 509.90 EUR/year - recurring (= 42.49/mois × 12)

Product: LuxPretty Premium
  Price: premium_monthly   - 67.99 EUR/month - recurring
  Price: premium_yearly    - 693.50 EUR/year - recurring (= 57.79/mois × 12)
```

Les Price IDs (`price_xxx`) seront copiés dans `application.properties` via env vars :
```
APP_STRIPE_PRICE_GESTION_MONTHLY=price_...
APP_STRIPE_PRICE_GESTION_YEARLY=price_...
APP_STRIPE_PRICE_PREMIUM_MONTHLY=price_...
APP_STRIPE_PRICE_PREMIUM_YEARLY=price_...
```

Vitrine n'a pas de Price (tier gratuit, géré côté app sans Stripe Subscription).

### 0.9 Stripe Tax

Activer dans Stripe Dashboard → Tax → Settings. Origin = Luxembourg. Subscriptions créées avec `automatic_tax: {enabled: true}` → Stripe calcule la TVA selon le pays du client (reverse charge pour B2B intra-EU avec VAT ID, OSS pour B2C, etc.). Stripe émet l'invoice avec ligne TVA correcte.

### 0.10 Files touchés

```
backend/src/main/java/com/luxpretty/app/
├── tenant/
│   ├── domain/Tenant.java                    [MODIFY] +6 colonnes Stripe
│   └── repo/TenantRepository.java            [MODIFY] +findByStripeCustomerId, +findByStripeSubscriptionId
├── subscription/                              [NEW package]
│   ├── domain/
│   │   ├── SubscriptionStatus.java           [NEW] enum
│   │   ├── SubscriptionTier.java             [NEW] enum
│   │   ├── SubscriptionBilling.java          [NEW] enum
│   │   └── StripeEventProcessed.java         [NEW] entité idempotency
│   ├── repo/
│   │   └── StripeEventRepository.java        [NEW]
│   ├── app/
│   │   ├── StripeService.java                [NEW] wrapping Stripe SDK
│   │   ├── SubscriptionService.java          [NEW] business logic
│   │   ├── SubscriptionEventHandler.java     [NEW] webhook event dispatch
│   │   └── PricingCatalog.java               [NEW] tier+billing → priceId map
│   └── web/
│       ├── SubscriptionController.java       [NEW] /api/pro/subscription/*
│       ├── StripeWebhookController.java      [NEW] /api/webhooks/stripe
│       └── dto/
│           ├── SetupIntentResponse.java
│           ├── CreateSubscriptionRequest.java
│           ├── SubscriptionResponse.java
│           ├── PortalSessionResponse.java
│           └── PricingPlanDto.java
├── auth/AuthController.java                  [MODIFY] register/pro creates Stripe Customer
└── config/
    ├── SubscriptionGuard.java                [NEW] Spring filter
    └── SecurityConfig.java                   [MODIFY] permit /api/webhooks/stripe (no auth)

backend/src/main/resources/
├── db/migration/oracle/
│   ├── V8__stripe_tenant_columns.sql         [NEW]
│   └── V9__stripe_events_processed.sql       [NEW]
├── application.properties                    [MODIFY] +Stripe config
└── application-prod.properties.example       [MODIFY] +Stripe vars

frontend/src/app/
├── features/subscription/                     [NEW]
│   ├── models/subscription.model.ts
│   ├── services/subscription.service.ts
│   ├── pricing-page/pricing-page.component.ts
│   └── payment-onboarding/
│       └── payment-onboarding.component.ts   (Stripe Elements integration)
└── core/guards/subscription.guard.ts          [NEW] redirige vers /pro/onboarding/payment si needed
```

---

## 1. Spec détaillée

### 1.1 Enum `SubscriptionStatus`

```java
package com.luxpretty.app.subscription.domain;

public enum SubscriptionStatus {
    /** Free tier with no Stripe subscription. */
    VITRINE_FREE,
    /** Stripe trialing — card on file, will charge at trial_end. */
    TRIALING,
    /** Stripe active — paying customer. */
    ACTIVE,
    /** First invoice failed but in 7-day grace period. */
    PAST_DUE,
    /** Beyond grace window, app access blocked. */
    UNPAID,
    /** User canceled, access until current_period_end. */
    CANCELED,
    /** First payment intent failed (rare). */
    INCOMPLETE,
    INCOMPLETE_EXPIRED,
    PAUSED;
    
    public boolean grantsAccess() {
        return this == VITRINE_FREE || this == TRIALING || this == ACTIVE || this == PAST_DUE;
    }
}
```

### 1.2 Enum `SubscriptionTier`

```java
package com.luxpretty.app.subscription.domain;

public enum SubscriptionTier {
    VITRINE,    // 0€, no Stripe subscription
    GESTION,    // 49.99€/mo or 42.49€/mo yearly
    PREMIUM;    // 67.99€/mo or 57.79€/mo yearly
}
```

### 1.3 Enum `SubscriptionBilling`

```java
package com.luxpretty.app.subscription.domain;

public enum SubscriptionBilling {
    FREE,     // VITRINE tier
    MONTHLY,
    YEARLY;
}
```

### 1.4 Modifications `Tenant.java`

Ajouter 6 champs :

```java
@Column(name = "stripe_customer_id")
private String stripeCustomerId;

@Column(name = "stripe_subscription_id")
private String stripeSubscriptionId;

@Enumerated(EnumType.STRING)
@Column(name = "subscription_status")
private SubscriptionStatus subscriptionStatus = SubscriptionStatus.VITRINE_FREE;

@Enumerated(EnumType.STRING)
@Column(name = "subscription_tier")
private SubscriptionTier subscriptionTier = SubscriptionTier.VITRINE;

@Enumerated(EnumType.STRING)
@Column(name = "subscription_billing")
private SubscriptionBilling subscriptionBilling = SubscriptionBilling.FREE;

@Column(name = "current_period_end")
private LocalDateTime currentPeriodEnd;

@Column(name = "trial_end")
private LocalDateTime trialEnd;
```

### 1.5 Entité `StripeEventProcessed`

```java
package com.luxpretty.app.subscription.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "STRIPE_EVENTS_PROCESSED")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StripeEventProcessed {
    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;
    
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;
    
    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
    
    @PrePersist
    void onCreate() {
        if (processedAt == null) processedAt = LocalDateTime.now();
    }
}
```

### 1.6 `PricingCatalog`

```java
package com.luxpretty.app.subscription.app;

import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class PricingCatalog {
    
    private final Map<TierBilling, String> priceMap;
    
    public PricingCatalog(
        @Value("${app.stripe.price.gestion-monthly:}") String gestionMonthly,
        @Value("${app.stripe.price.gestion-yearly:}") String gestionYearly,
        @Value("${app.stripe.price.premium-monthly:}") String premiumMonthly,
        @Value("${app.stripe.price.premium-yearly:}") String premiumYearly
    ) {
        this.priceMap = Map.of(
            new TierBilling(SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY), gestionMonthly,
            new TierBilling(SubscriptionTier.GESTION, SubscriptionBilling.YEARLY), gestionYearly,
            new TierBilling(SubscriptionTier.PREMIUM, SubscriptionBilling.MONTHLY), premiumMonthly,
            new TierBilling(SubscriptionTier.PREMIUM, SubscriptionBilling.YEARLY), premiumYearly
        );
    }
    
    public Optional<String> priceIdFor(SubscriptionTier tier, SubscriptionBilling billing) {
        if (tier == SubscriptionTier.VITRINE) return Optional.empty();
        return Optional.ofNullable(priceMap.get(new TierBilling(tier, billing)))
            .filter(s -> !s.isBlank());
    }
    
    public Optional<TierBilling> tierBillingFor(String priceId) {
        return priceMap.entrySet().stream()
            .filter(e -> e.getValue().equals(priceId))
            .map(Map.Entry::getKey)
            .findFirst();
    }
    
    public record TierBilling(SubscriptionTier tier, SubscriptionBilling billing) {}
}
```

### 1.7 `StripeService` (wrapping SDK)

```java
package com.luxpretty.app.subscription.app;

import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.users.domain.User;
import com.stripe.Stripe;
import com.stripe.model.Customer;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.model.billingportal.Session;
import com.stripe.param.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StripeService {
    
    @Value("${app.stripe.secret-key}")
    private String secretKey;
    
    @Value("${app.stripe.customer-portal-return-url:https://luxpretty.lu/pro/settings}")
    private String portalReturnUrl;
    
    @PostConstruct
    void init() {
        Stripe.apiKey = secretKey;
    }
    
    public Customer createCustomer(User owner, Tenant tenant) throws Exception {
        CustomerCreateParams params = CustomerCreateParams.builder()
            .setEmail(owner.getEmail())
            .setName(owner.getName())
            .setDescription("LuxPretty tenant: " + tenant.getSlug())
            .putMetadata("tenant_id", String.valueOf(tenant.getId()))
            .putMetadata("tenant_slug", tenant.getSlug())
            .build();
        return Customer.create(params);
    }
    
    public SetupIntent createSetupIntent(String customerId) throws Exception {
        SetupIntentCreateParams params = SetupIntentCreateParams.builder()
            .setCustomer(customerId)
            .addPaymentMethodType("card")
            .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
            .build();
        return SetupIntent.create(params);
    }
    
    public Subscription createSubscription(String customerId, String priceId,
                                           String defaultPaymentMethodId) throws Exception {
        SubscriptionCreateParams params = SubscriptionCreateParams.builder()
            .setCustomer(customerId)
            .addItem(SubscriptionCreateParams.Item.builder().setPrice(priceId).build())
            .setTrialPeriodDays(7L)
            .setDefaultPaymentMethod(defaultPaymentMethodId)
            .setAutomaticTax(SubscriptionCreateParams.AutomaticTax.builder()
                .setEnabled(true).build())
            .setPaymentSettings(SubscriptionCreateParams.PaymentSettings.builder()
                .setSaveDefaultPaymentMethod(
                    SubscriptionCreateParams.PaymentSettings.SaveDefaultPaymentMethod.ON_SUBSCRIPTION)
                .build())
            .build();
        return Subscription.create(params);
    }
    
    public Session createPortalSession(String customerId) throws Exception {
        com.stripe.param.billingportal.SessionCreateParams params = 
            com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(portalReturnUrl)
                .build();
        return Session.create(params);
    }
}
```

### 1.8 `SubscriptionService`

Business logic, transactional. Méthodes :
- `initializeForTenant(User owner, Tenant tenant)` — appelée à register/pro : crée Customer Stripe + persiste customerId
- `startCheckout(Long tenantId, SubscriptionTier tier, SubscriptionBilling billing, String paymentMethodId)` — crée la Subscription Stripe + sauvegarde subId
- `createPortalSession(Long tenantId)` — pour Customer Portal
- `applySubscriptionWebhookEvent(SubscriptionEvent event)` — applique l'event Stripe au Tenant local

(Code complet dans le ticket 4.)

### 1.9 `SubscriptionEventHandler` (webhook dispatch)

```java
package com.luxpretty.app.subscription.app;

import com.luxpretty.app.mail.app.MailOutboxService;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.subscription.domain.StripeEventProcessed;
import com.luxpretty.app.subscription.repo.StripeEventRepository;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionEventHandler {
    
    private final StripeEventRepository eventRepo;
    private final SubscriptionService subscriptionService;
    private final TenantRepository tenantRepository;
    private final MailOutboxService mailService;
    
    // constructor injection...
    
    @Transactional
    public void handle(Event event) {
        // Idempotency: skip if already processed
        try {
            eventRepo.save(StripeEventProcessed.builder()
                .eventId(event.getId())
                .eventType(event.getType())
                .build());
        } catch (DataIntegrityViolationException e) {
            // Already processed — skip silently
            return;
        }
        
        switch (event.getType()) {
            case "customer.subscription.created",
                 "customer.subscription.updated" -> onSubscriptionChange(event);
            case "customer.subscription.deleted" -> onCancellation(event);
            case "invoice.paid"            -> onInvoicePaid(event);
            case "invoice.payment_failed"  -> onPaymentFailed(event);
            case "customer.subscription.trial_will_end" -> onTrialWillEnd(event);
            default -> {} // ignore other events
        }
    }
    
    private void onSubscriptionChange(Event event) {
        Subscription sub = (Subscription) event.getDataObjectDeserializer()
            .getObject().orElseThrow();
        subscriptionService.applySubscriptionUpdate(sub);
    }
    
    private void onCancellation(Event event) { /* ... */ }
    private void onInvoicePaid(Event event) { /* ... queue mail confirmation ... */ }
    private void onPaymentFailed(Event event) { /* ... queue mail "paiement échoué" + status PAST_DUE ... */ }
    private void onTrialWillEnd(Event event) { /* ... queue mail "trial finit dans 3j" ... */ }
}
```

### 1.10 `SubscriptionGuard`

```java
package com.luxpretty.app.config;

import com.luxpretty.app.subscription.domain.SubscriptionStatus;
import com.luxpretty.app.tenant.repo.TenantRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@Order(2) // after JwtAuthenticationFilter
public class SubscriptionGuard implements Filter {
    
    private static final List<String> EXEMPT_PREFIXES = List.of(
        "/api/auth/",
        "/api/webhooks/",
        "/api/pro/subscription/",  // user must be able to view/start subscription
        "/api/pro/tenant"          // GET tenant info OK without active sub
    );
    
    private final TenantRepository tenantRepository;
    
    public SubscriptionGuard(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        String path = http.getRequestURI();
        
        if (!path.startsWith("/api/pro/") || EXEMPT_PREFIXES.stream().anyMatch(path::startsWith)) {
            chain.doFilter(req, res);
            return;
        }
        
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            chain.doFilter(req, res); // let SecurityConfig reject downstream
            return;
        }
        
        // Find tenant for current user (assumes activeTenantId or owner lookup)
        var tenant = tenantRepository.findByOwnerId(principal.getId());
        if (tenant.isEmpty()) {
            chain.doFilter(req, res);
            return;
        }
        
        SubscriptionStatus status = tenant.get().getSubscriptionStatus();
        if (status == null || !status.grantsAccess()) {
            HttpServletResponse httpRes = (HttpServletResponse) res;
            httpRes.setStatus(402); // Payment Required
            httpRes.setContentType("application/json");
            httpRes.getWriter().write(
                "{\"error\":\"SUBSCRIPTION_REQUIRED\",\"redirect\":\"/pro/onboarding/payment\"}");
            return;
        }
        
        chain.doFilter(req, res);
    }
}
```

### 1.11 `StripeWebhookController`

```java
package com.luxpretty.app.subscription.web;

import com.luxpretty.app.subscription.app.SubscriptionEventHandler;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/stripe")
public class StripeWebhookController {
    
    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);
    
    @Value("${app.stripe.webhook-secret}")
    private String webhookSecret;
    
    private final SubscriptionEventHandler handler;
    
    public StripeWebhookController(SubscriptionEventHandler handler) {
        this.handler = handler;
    }
    
    @PostMapping
    public ResponseEntity<String> handle(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            logger.warn("Invalid Stripe webhook signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }
        
        try {
            handler.handle(event);
        } catch (Exception e) {
            logger.error("Webhook handling failed: type={} id={}", event.getType(), event.getId(), e);
            // Return 500 so Stripe retries
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing error");
        }
        
        return ResponseEntity.ok("ok");
    }
}
```

### 1.12 Migrations Flyway

**V8** : ajoute les 6 colonnes Stripe à TENANTS.
**V9** : crée la table STRIPE_EVENTS_PROCESSED.

Détails dans les tickets.

### 1.13 Configuration

`application.properties` (additions) :

```properties
# Stripe
app.stripe.secret-key=${STRIPE_SECRET_KEY:}
app.stripe.publishable-key=${STRIPE_PUBLISHABLE_KEY:}
app.stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:}
app.stripe.customer-portal-return-url=${STRIPE_PORTAL_RETURN_URL:http://localhost:4300/pro/settings}

# Stripe Prices (test mode IDs, replaced in prod env vars)
app.stripe.price.gestion-monthly=${STRIPE_PRICE_GESTION_MONTHLY:}
app.stripe.price.gestion-yearly=${STRIPE_PRICE_GESTION_YEARLY:}
app.stripe.price.premium-monthly=${STRIPE_PRICE_PREMIUM_MONTHLY:}
app.stripe.price.premium-yearly=${STRIPE_PRICE_PREMIUM_YEARLY:}
```

---

## 2. Découpage en tickets

11 tickets séquentiels. TDD partout.

### Légende
- 🟢 trivial (~30 min)
- 🟡 moyen (1-2h)
- 🔴 important (3-6h)

---

### 🟢 Ticket 1 — Stripe SDK dependency + enums

**Files:**
- Modify: `backend/pom.xml`
- Create: `subscription/domain/SubscriptionStatus.java`
- Create: `subscription/domain/SubscriptionTier.java`
- Create: `subscription/domain/SubscriptionBilling.java`

**Steps :**
1. Add to `pom.xml`:
   ```xml
   <dependency>
       <groupId>com.stripe</groupId>
       <artifactId>stripe-java</artifactId>
       <version>26.10.0</version>
   </dependency>
   ```
2. Run `mvn dependency:resolve` to fetch
3. Create the 3 enum files (code in section 1.1-1.3)
4. `mvn compile` → SUCCESS
5. Commit

**Commit:** `feat(stripe): add stripe-java dep + subscription enums`

---

### 🟡 Ticket 2 — Migration V8 + Tenant columns (TDD)

**Files:**
- Create: `db/migration/oracle/V8__stripe_tenant_columns.sql`
- Modify: `tenant/domain/Tenant.java`
- Modify: `tenant/repo/TenantRepository.java`
- Modify: `tenant/domain/TenantTests.java` (or new)

**V8 SQL:**
```sql
ALTER TABLE "${appSchema}".TENANTS ADD (
    STRIPE_CUSTOMER_ID       VARCHAR2(255 CHAR),
    STRIPE_SUBSCRIPTION_ID   VARCHAR2(255 CHAR),
    SUBSCRIPTION_STATUS      VARCHAR2(32 CHAR) DEFAULT 'VITRINE_FREE' NOT NULL,
    SUBSCRIPTION_TIER        VARCHAR2(32 CHAR) DEFAULT 'VITRINE' NOT NULL,
    SUBSCRIPTION_BILLING     VARCHAR2(16 CHAR) DEFAULT 'FREE' NOT NULL,
    CURRENT_PERIOD_END       TIMESTAMP,
    TRIAL_END                TIMESTAMP
);

CREATE UNIQUE INDEX UK_TENANTS_STRIPE_CUSTOMER 
    ON "${appSchema}".TENANTS (STRIPE_CUSTOMER_ID);
CREATE UNIQUE INDEX UK_TENANTS_STRIPE_SUBSCRIPTION 
    ON "${appSchema}".TENANTS (STRIPE_SUBSCRIPTION_ID);
CREATE INDEX IX_TENANTS_SUB_STATUS 
    ON "${appSchema}".TENANTS (SUBSCRIPTION_STATUS);
```

**Tenant.java** add 6 fields (code section 1.4).

**Repository :**
```java
Optional<Tenant> findByStripeCustomerId(String stripeCustomerId);
Optional<Tenant> findByStripeSubscriptionId(String stripeSubscriptionId);
Optional<Tenant> findByOwnerId(Long ownerId);
```

**Tests** (4 tests `@DataJpaTest`):
1. `tenant_persists_with_default_vitrine_status`
2. `findByStripeCustomerId_returns_tenant`
3. `findByStripeSubscriptionId_returns_tenant`
4. `findByOwnerId_returns_tenant`

**Steps :**
1. Write 4 failing tests
2. Run → red
3. Create V8 SQL + entity fields + repo methods
4. Run tests → green
5. Run full suite → green
6. ⚠️ Also update H2 path in `TenantSchemaManager` if it creates tenants with the legacy schema (probably needs the new columns there too — check)
7. Commit

**Commit:** `feat(tenant): add Stripe subscription columns (V8) + repo finders`

---

### 🟡 Ticket 3 — Migration V9 + StripeEventProcessed

**Files:**
- Create: `db/migration/oracle/V9__stripe_events_processed.sql`
- Create: `subscription/domain/StripeEventProcessed.java`
- Create: `subscription/repo/StripeEventRepository.java`
- Create: `subscription/repo/StripeEventRepositoryTests.java`

**V9 SQL:**
```sql
CREATE TABLE "${appSchema}".STRIPE_EVENTS_PROCESSED (
    EVENT_ID      VARCHAR2(255 CHAR) PRIMARY KEY,
    EVENT_TYPE    VARCHAR2(64 CHAR) NOT NULL,
    PROCESSED_AT  TIMESTAMP NOT NULL
);
```

**Tests (2):**
1. `save_persists_event_processed_record`
2. `save_duplicateEventId_throwsDataIntegrityViolation`

**Steps:**
1. Write tests
2. Run → red
3. Create V9 + entity + repo
4. Run → green
5. Commit

**Commit:** `feat(stripe): StripeEventProcessed entity for webhook idempotency (V9)`

---

### 🟡 Ticket 4 — PricingCatalog + tests

**Files:**
- Create: `subscription/app/PricingCatalog.java`
- Create: `subscription/app/PricingCatalogTests.java`

**Tests (5):**
1. `priceIdFor_returnsEmpty_forVitrineTier`
2. `priceIdFor_returnsConfiguredPrice_forGestionMonthly`
3. `priceIdFor_returnsConfiguredPrice_forPremiumYearly`
4. `tierBillingFor_returnsCorrectTuple_givenPriceId`
5. `priceIdFor_returnsEmpty_whenPropertyIsBlank`

**Steps:**
1. Write tests (with `@Value` properties mocked via `@TestPropertySource`)
2. Run → red
3. Implement (code section 1.6)
4. Run → green
5. Commit

**Commit:** `feat(stripe): PricingCatalog mapping tier+billing to Stripe price IDs`

---

### 🔴 Ticket 5 — StripeService wrapper (TDD with mocked SDK)

**Files:**
- Create: `subscription/app/StripeService.java`
- Create: `subscription/app/StripeServiceTests.java`

**Tests (5):** mocking Stripe SDK via `Mockito.mockStatic(Customer.class)` etc.
1. `createCustomer_callsStripeWithCorrectParams`
2. `createSetupIntent_returnsClientSecret`
3. `createSubscription_includesTrial7days`
4. `createSubscription_enablesAutomaticTax`
5. `createPortalSession_returnsUrl`

**Steps:**
1. Write tests (use Mockito's `mockStatic` for Stripe SDK static methods)
2. Run → red
3. Implement (code section 1.7)
4. Run → green
5. Commit

**Commit:** `feat(stripe): StripeService wrapping SDK calls (5 tests)`

---

### 🔴 Ticket 6 — SubscriptionService + integration with AuthController (TDD)

**Files:**
- Create: `subscription/app/SubscriptionService.java`
- Modify: `auth/AuthController.java`
- Create/Modify tests

**SubscriptionService responsibilities:**
- `initializeForTenant(owner, tenant)` — appelée par AuthController au registerPro, crée Customer Stripe, persiste customerId. Idempotent.
- `startCheckout(tenantId, tier, billing, paymentMethodId)` — crée Subscription Stripe + persiste sur tenant
- `applySubscriptionUpdate(stripeSubscription)` — synchronise statut+période depuis webhook
- `createPortalSession(tenantId)` — pour Customer Portal UI

**Tests (8) :**
1. `initializeForTenant_createsCustomer_andPersistsId`
2. `initializeForTenant_isIdempotent_skipIfCustomerExists`
3. `startCheckout_createsSubscription_withCorrectPrice`
4. `startCheckout_failsIfTierIsVitrine`
5. `applySubscriptionUpdate_setsStatusActive_whenStripeStatusIsActive`
6. `applySubscriptionUpdate_setsCurrentPeriodEnd`
7. `applySubscriptionUpdate_setsTier_basedOnPriceId`
8. `createPortalSession_returnsSessionUrl`

**AuthController.registerProWithSalonInfo :** ajouter `subscriptionService.initializeForTenant(user, tenant)` après création du tenant.

**Steps:**
1. Write the 8 tests
2. Run → red
3. Implement SubscriptionService
4. Modify AuthController
5. Run → green
6. Run full suite → green
7. Commit

**Commit:** `feat(subscription): SubscriptionService + AuthController initializes Stripe Customer (8 tests)`

---

### 🔴 Ticket 7 — Webhook handler + StripeWebhookController (TDD)

**Files:**
- Create: `subscription/app/SubscriptionEventHandler.java`
- Create: `subscription/web/StripeWebhookController.java`
- Modify: `config/SecurityConfig.java` (permit `/api/webhooks/stripe` without auth)
- Create tests

**Tests (7):**
1. `handle_subscriptionCreated_updatesTenant`
2. `handle_subscriptionUpdated_updatesTenant`
3. `handle_subscriptionDeleted_setsStatusCanceled`
4. `handle_invoicePaid_queuesConfirmationMail`
5. `handle_invoicePaymentFailed_setsStatusPastDue_andQueuesMail`
6. `handle_trialWillEnd_queuesReminderMail`
7. `handle_isIdempotent_skipsIfEventAlreadyProcessed`

**Webhook controller tests (3):**
1. `webhook_returnsBadRequest_onInvalidSignature`
2. `webhook_returns200_onValidEvent`
3. `webhook_returns500_onHandlerException` (Stripe retries)

**Steps:**
1. Write the 10 tests
2. Run → red
3. Implement handler (code section 1.9)
4. Implement controller (code section 1.11)
5. Update SecurityConfig to permit `/api/webhooks/stripe`
6. Run → green
7. Commit

**Commit:** `feat(stripe): webhook handler + controller for subscription events (10 tests)`

---

### 🔴 Ticket 8 — SubscriptionGuard filter (TDD)

**Files:**
- Create: `config/SubscriptionGuard.java`
- Modify: `config/SecurityConfig.java` (register the filter)
- Create: `config/SubscriptionGuardTests.java`

**Tests (6):**
1. `guard_allowsAuthEndpoints_regardless_of_status`
2. `guard_allowsWebhooksEndpoints`
3. `guard_allowsSubscriptionEndpoints_even_when_unpaid`
4. `guard_allowsProRequest_whenStatusIsTrialing`
5. `guard_allowsProRequest_whenStatusIsActive`
6. `guard_returns402_whenStatusIsUnpaid`

**Steps:**
1. Write tests (Spring @WebMvcTest + custom filter chain)
2. Run → red
3. Implement guard (code section 1.10)
4. Register in SecurityConfig (after JwtFilter)
5. Run → green
6. Commit

**Commit:** `feat(subscription): SubscriptionGuard blocks /api/pro/* when status doesn't grant access`

---

### 🟡 Ticket 9 — SubscriptionController endpoints (TDD)

**Files:**
- Create: `subscription/web/SubscriptionController.java`
- Create: DTOs (SetupIntentResponse, CreateSubscriptionRequest, SubscriptionResponse, PortalSessionResponse, PricingPlanDto)
- Create: `subscription/web/SubscriptionControllerTests.java`

**Endpoints:**
- `GET /api/pricing` — returns pricing plans (public, no auth). Pour la pricing page.
- `POST /api/pro/subscription/setup-intent` — returns client_secret for Stripe Elements
- `POST /api/pro/subscription/create` — body `{tier, billing, paymentMethodId}` — crée la Subscription
- `GET /api/pro/subscription` — returns current subscription summary
- `POST /api/pro/subscription/portal-session` — returns portal URL for redirect

**Tests (10):**
1. `getPricing_returnsAllTiers_withCorrectPrices`
2. `createSetupIntent_returnsClientSecret`
3. `createSetupIntent_requiresAuth`
4. `createSubscription_callsService_andReturnsSummary`
5. `createSubscription_rejectsVitrineTier`
6. `createSubscription_rejectsInvalidPriceId`
7. `getCurrentSubscription_returnsStatusForTenant`
8. `getCurrentSubscription_returnsVitrineFreeForFreeTenants`
9. `createPortalSession_returnsUrl`
10. `createPortalSession_rejects_whenNoStripeCustomerYet`

**Steps:**
1. Write tests
2. Run → red
3. Implement controller + DTOs
4. Run → green
5. Commit

**Commit:** `feat(subscription): SubscriptionController endpoints (10 tests)`

---

### 🟡 Ticket 10 — Frontend pricing page + Stripe Elements onboarding

**Files:**
- Create: `frontend/src/app/features/subscription/models/subscription.model.ts`
- Create: `frontend/src/app/features/subscription/services/subscription.service.ts`
- Create: `frontend/src/app/features/subscription/pricing-page/pricing-page.component.ts`
- Create: `frontend/src/app/features/subscription/payment-onboarding/payment-onboarding.component.ts`
- Modify: `frontend/src/app/app.routes.ts` (ajoute /pricing et /pro/onboarding/payment)
- Modify: `frontend/package.json` (ajoute `@stripe/stripe-js`)
- Translations FR + EN

**Composants minimaux pour MVP:**

**`pricing-page.component.ts`** — 3 cards (Vitrine/Gestion/Premium) avec toggle monthly/yearly, bouton "Choisir" pour Gestion/Premium qui redirige vers `/auth/register/pro?tier=GESTION&billing=MONTHLY`. Vitrine → registration directe.

**`payment-onboarding.component.ts`** — utilise Stripe Elements (`@stripe/stripe-js`) :
1. ngOnInit : POST /api/pro/subscription/setup-intent → récupère client_secret
2. Initialise Stripe Elements card input
3. Bouton "Confirmer" → `stripe.confirmCardSetup(client_secret)` → récupère paymentMethodId
4. POST /api/pro/subscription/create { tier, billing, paymentMethodId }
5. Redirect /pro/dashboard

**Tests Karma (5) :** principalement smoke tests sur les composants (les flows Stripe Elements ne peuvent pas être unit-testés sans mock complexe ; on les valide manuellement en smoke).

**Steps :**
1. `npm install @stripe/stripe-js`
2. Create models + service + components
3. Add routes
4. Add translations
5. Smoke test manual : pricing page renders, click Gestion → register form, fill, redirect to payment onboarding, fill test card `4242 4242 4242 4242`, submit → trial active
6. Commit

**Commit:** `feat(subscription): pricing page + Stripe Elements payment onboarding`

---

### 🟢 Ticket 11 — Configuration prod-readiness + smoke test E2E

**Files:**
- Modify: `application.properties` (ajoute Stripe vars)
- Modify: `application-prod.properties.example` (documente Stripe)
- Create: Smoke E2E doc dans `docs/superpowers/runbooks/2026-05-15-stripe-pr1-smoke.md`

**Documentation runbook E2E:**
1. Stripe Dashboard test mode → create Products (Gestion + Premium) + Prices
2. Copier les Price IDs dans `.env`
3. Configurer Stripe CLI pour forward webhooks vers localhost : `stripe listen --forward-to localhost:8080/api/webhooks/stripe`
4. Récupérer le webhook secret affiché par stripe listen → `.env` STRIPE_WEBHOOK_SECRET
5. Démarrer backend + frontend
6. Aller sur /pricing → choisir Gestion → register → onboarding payment
7. Carte test `4242 4242 4242 4242` exp `12/34` cvc `123`
8. Confirmer → vérifier :
   - Subscription créée dans Stripe Dashboard
   - Webhook reçu : `customer.subscription.created`
   - Tenant.subscriptionStatus = TRIALING
   - Tenant.trialEnd = J+7
   - Mail "Bienvenue, ton trial commence" reçu

**Steps:**
1. Add config keys to application.properties
2. Document in .example file
3. Write the runbook
4. Run a manual smoke
5. Commit

**Commit:** `docs(stripe): PR1 smoke runbook + prod config keys`

---

## 3. Plan de vérification

### 3.1 Tests automatisés

Backend : viser **~50 nouveaux tests** (4 + 2 + 5 + 5 + 8 + 10 + 6 + 10 + 5 ≈ 50)
Frontend : 5 smoke tests

Total attendu après PR1 : ~670 tests backend, ~640 frontend.

### 3.2 Smoke test prod-like (runbook complet ticket 11)

### 3.3 Hors-scope (PR2+)

- Tier-based feature gating (Vitrine sans booking, Premium avec Stripe payments)
- Stripe Connect (marketplace, encaissements client→salon)
- CVC hold + no-show fee
- Subscription upgrade/downgrade mid-cycle
- Coupon codes (préparé via PricingCatalog mais pas UI)

## 4. Estimations finales

| Ticket | Effort | Cumul |
|---|---|---|
| 1 — SDK + enums | 30 min | 30 min |
| 2 — V8 + Tenant cols | 1h30 | 2h |
| 3 — V9 + Event entity | 1h | 3h |
| 4 — PricingCatalog | 1h | 4h |
| 5 — StripeService | 3h | 7h |
| 6 — SubscriptionService + Auth | 3h | 10h |
| 7 — Webhook handler+ctrl | 3h | 13h |
| 8 — SubscriptionGuard | 2h | 15h |
| 9 — SubscriptionController | 2h | 17h |
| 10 — Frontend pricing+payment | 3h | 20h |
| 11 — Config + smoke runbook | 1h | 21h |

**Total : ~21h soit 2.5 jours focalisés.**

Avec le découpage TDD je l'estime plus prudemment 2 jours pleins (16h) + 4h de smoke/debug = 2.5 jours.

## 5. Pré-requis manuels (toi, en parallèle dev)

1. **Compte Stripe** + KYC (déjà en cours)
2. **Stripe Tax** activer dashboard
3. **Products + Prices test mode** créer 2 products × 2 prices = 4 prix
4. **Stripe CLI installé localement** pour test webhook (`brew install stripe/stripe-cli/stripe`)
5. **Webhook endpoint configuré** dans Stripe Dashboard (Settings → Webhooks → Add endpoint → URL: `https://luxpretty.lu/api/webhooks/stripe`, events à écouter : les 5 listés section 0.4)

## 6. Risques

1. **KYC pas validé vendredi** : on lance en mode test uniquement, paiements simulés. Pas de revenue mais MVP fonctionnel. Bascule live mode dès KYC OK (variables d'env à swap, 5 min).
2. **Webhook délai** : Stripe peut prendre 1-5 secondes pour envoyer le webhook après l'action. UX : afficher "Activation en cours" et poller `GET /api/pro/subscription` jusqu'à status=TRIALING.
3. **Stripe API breaking changes** : version SDK figée à 26.10.0 dans pom.xml.
4. **Tenant H2 schema** : les colonnes Stripe doivent être dans le path H2 aussi (ticket 2 step 6 le couvre).

## 7. Prochaines étapes (post PR1)

- **Domain DNS** pour `https://api.luxpretty.lu` (subdomain backend) avant déploiement
- **PR2** : tier-based feature gating + UI Customer Portal embed
- **PR3** : Stripe Connect Standard pour encaissements client→salon (Premium tier)
- **PR4** : CVC hold + no-show charge (premium feature)
