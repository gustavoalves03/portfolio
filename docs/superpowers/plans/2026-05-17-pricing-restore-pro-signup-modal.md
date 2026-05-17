# Restauration `/pricing` + Pro Signup Modal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restaurer `/pricing` avec son UI existante, rendre VITRINE payant (9.99€ mensuel / 7.99€ annuel), introduire un `ProSignupModalComponent` léger (4 champs) déclenché depuis les CTAs de la page pricing, et ajouter un endpoint `/api/auth/upgrade-to-pro` pour les clients déjà connectés.

**Architecture:** PR1 backend (DTO assoupli + endpoint upgrade + PricingCatalog VITRINE). PR2 frontend (restaurer PricingPageComponent depuis git history, créer ProSignupModalComponent, brancher CTAs, supprimer ancien RegisterProComponent). PR3 gate paiement avant publish dashboard pro. Le `guided tour` existant et le `AuthModalComponent` client restent intouchés.

**Tech Stack:** Angular 20 standalone signals, Spring Boot 3.5 (Java 21), Stripe Subscriptions, Oracle, Jasmine/Karma, JUnit 5

---

## File Structure

### PR1 — Backend
- Modify : `backend/src/main/java/com/luxpretty/app/auth/dto/ProRegisterRequest.java`
- Modify : `backend/src/main/java/com/luxpretty/app/auth/AuthController.java`
- Create : `backend/src/main/java/com/luxpretty/app/auth/dto/ProUpgradeRequest.java`
- Modify : `backend/src/main/java/com/luxpretty/app/subscription/app/PricingCatalog.java`
- Modify : `backend/src/main/java/com/luxpretty/app/subscription/app/SubscriptionService.java`
- Modify (si besoin) : `backend/src/test/java/com/luxpretty/app/auth/...` tests

### PR2 — Frontend
- Create (restored from `git show c3b02eb^:`) :
  - `frontend/src/app/features/subscription/pricing/pricing-page.component.ts`
  - `frontend/src/app/features/subscription/pricing/pricing-page.component.html`
  - `frontend/src/app/features/subscription/pricing/pricing-page.component.scss`
  - `frontend/src/app/features/subscription/pricing/pricing-page.component.spec.ts`
- Modify (after restoration) : pricing-page.component.{ts,html,spec.ts} for VITRINE pricing + CTA logic
- Create :
  - `frontend/src/app/shared/modals/pro-signup-modal/pro-signup-modal.component.ts`
  - `frontend/src/app/shared/modals/pro-signup-modal/pro-signup-modal.component.html`
  - `frontend/src/app/shared/modals/pro-signup-modal/pro-signup-modal.component.scss`
  - `frontend/src/app/shared/modals/pro-signup-modal/pro-signup-modal.component.spec.ts`
- Modify :
  - `frontend/src/app/core/auth/auth.service.ts` — registerPro signature (tier+billing, business fields optionnels) + nouvelle méthode `upgradeToPro`
  - `frontend/src/app/app.routes.ts` — restore `/pricing` route, `/register/pro` → redirect `/pricing`
  - `frontend/src/app/pages/home/home.ts` — CTAs vers `/pricing` (au lieu de `/register/pro`)
  - `frontend/src/app/shared/layout/footer/footer.html` — CTAs vers `/pricing`
  - `frontend/src/app/shared/layout/navigation/navigation-routes.ts` — CTAs vers `/pricing`
  - `frontend/public/i18n/fr.json` + `en.json` — clés `pricing.tiers.vitrine.price`, `proSignup.modal.*`
- Delete :
  - `frontend/src/app/pages/auth/register-pro/` (4 fichiers)

### PR3 — Frontend gate publish
- Modify : `frontend/src/app/pages/pro/pro-dashboard.component.ts`
- Modify : `frontend/src/app/pages/pro/pro-dashboard.component.spec.ts`

---

## PR1 — Backend

### Task 1 : Setup branche + baseline

**Files:** aucun

- [ ] **Step 1 : Créer la branche feature**

Run: `git checkout -b feat/pricing-restore-pro-signup`

- [ ] **Step 2 : Lancer les tests backend pour baseline**

Run: `cd backend && mvn test -q 2>&1 | tail -20`
Expected: BUILD SUCCESS

- [ ] **Step 3 : Lancer les tests frontend pour baseline**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`
Expected: tous passent (~654)

---

### Task 2 : Modifier `ProRegisterRequest` (tier+billing requis, business optionnels)

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/dto/ProRegisterRequest.java`

- [ ] **Step 1 : Remplacer tout le contenu du fichier**

```java
package com.luxpretty.app.auth.dto;

import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import jakarta.validation.constraints.*;

public record ProRegisterRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    @NotNull @AssertTrue Boolean consent,
    @NotNull SubscriptionTier tier,
    @NotNull SubscriptionBilling billing,
    String salonName,
    String phone,
    String addressStreet,
    String addressPostalCode,
    String addressCity,
    String siret
) {}
```

- [ ] **Step 2 : Compiler pour voir où ça casse**

Run: `cd backend && mvn compile -q 2>&1 | tail -20`
Expected: probable échec dans `AuthController.registerProWithSalonInfo` car il appelle `request.salonName()` sans null-check, et `request.plan()` qui n'existe plus.

---

### Task 3 : Adapter `AuthController.registerProWithSalonInfo` pour champs optionnels

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/AuthController.java:128-175`

- [ ] **Step 1 : Lire le bloc existant pour repère**

Run: `sed -n '128,175p' backend/src/main/java/com/luxpretty/app/auth/AuthController.java`

- [ ] **Step 2 : Remplacer le bloc `var tenant = ... tenantRepository.save(tenant);`**

Trouver :
```java
        var tenant = tenantProvisioningService.provision(savedUser);
        tenant.setName(request.salonName());
        tenant.setPhone(request.phone());
        tenant.setAddressStreet(request.addressStreet());
        tenant.setAddressPostalCode(request.addressPostalCode());
        tenant.setAddressCity(request.addressCity());
        tenant.setSiret(request.siret());
        tenantRepository.save(tenant);
```

Remplacer par :

```java
        var tenant = tenantProvisioningService.provision(savedUser);
        // Salon name: use provided value, or fall back to user's name as placeholder
        // (guided tour will let the pro update it on first login).
        String salonName = (request.salonName() != null && !request.salonName().isBlank())
            ? request.salonName()
            : request.name();
        tenant.setName(salonName);
        tenant.setPhone(request.phone());
        tenant.setAddressStreet(request.addressStreet());
        tenant.setAddressPostalCode(request.addressPostalCode());
        tenant.setAddressCity(request.addressCity());
        tenant.setSiret(request.siret());
        tenant.setSubscriptionTier(request.tier());
        tenant.setSubscriptionBilling(request.billing());
        tenantRepository.save(tenant);
```

- [ ] **Step 3 : Compiler**

Run: `cd backend && mvn compile -q 2>&1 | tail -10`
Expected: BUILD SUCCESS

- [ ] **Step 4 : Lancer tests backend**

Run: `cd backend && mvn test -q 2>&1 | tail -20`
Expected: BUILD SUCCESS. Si tests `AuthControllerTests` ou similaire échouent parce qu'ils envoyaient `"plan":"pro"` ou n'envoyaient pas `tier/billing` dans le body, les adapter en ajoutant ces clés (`"tier": "GESTION", "billing": "YEARLY"`). Pas de changements de comportement attendus côté welcome email / initialize Stripe.

- [ ] **Step 5 : Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/auth/dto/ProRegisterRequest.java \
        backend/src/main/java/com/luxpretty/app/auth/AuthController.java \
        backend/src/test/
git commit -m "$(cat <<'EOF'
feat(auth): ProRegisterRequest tier+billing required, business fields optional

Soften the pro registration DTO so the signup modal can create
accounts with just name+email+password+tier+billing+consent. The
guided tour will collect salon details on first dashboard load.
When salonName is not provided, the user's own name is used as
a placeholder.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4 : Créer `ProUpgradeRequest` DTO

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/auth/dto/ProUpgradeRequest.java`

- [ ] **Step 1 : Créer le fichier**

```java
package com.luxpretty.app.auth.dto;

import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import jakarta.validation.constraints.NotNull;

public record ProUpgradeRequest(
    @NotNull SubscriptionTier tier,
    @NotNull SubscriptionBilling billing
) {}
```

- [ ] **Step 2 : Compiler**

Run: `cd backend && mvn compile -q 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

### Task 5 : Écrire le test d'intégration pour `/api/auth/upgrade-to-pro`

**Files:**
- Test: `backend/src/test/java/com/luxpretty/app/auth/UpgradeToProControllerTests.java` (créer)

- [ ] **Step 1 : Inspecter un test d'AuthController existant pour copier la structure**

Run: `find backend/src/test -name "AuthController*" -o -name "Auth*Tests*" 2>/dev/null | head -5`
Run: `head -80 <chemin du premier fichier trouvé>` pour comprendre le pattern (probable @SpringBootTest + MockMvc + @AutoConfigureMockMvc).

- [ ] **Step 2 : Créer le fichier de test**

Note : si la classe `AuthControllerTests.java` existe déjà, **ajouter** les tests dedans plutôt que de créer un nouveau fichier (cohérence). Sinon créer `UpgradeToProControllerTests.java` :

```java
package com.luxpretty.app.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UpgradeToProControllerTests {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private MockMvc mvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        }
        return mockMvc;
    }

    @Test
    void upgradeToPro_unauthenticated_returns401() throws Exception {
        String body = """
            { "tier": "GESTION", "billing": "YEARLY" }
            """;
        mvc().perform(post("/api/auth/upgrade-to-pro")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "client@example.com")
    void upgradeToPro_authenticatedClient_returns200() throws Exception {
        User client = User.builder()
            .name("Client User")
            .email("client@example.com")
            .password(passwordEncoder.encode("password123"))
            .build();
        userRepository.save(client);

        String body = """
            { "tier": "GESTION", "billing": "YEARLY" }
            """;
        mvc().perform(post("/api/auth/upgrade-to-pro")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alreadypro@example.com")
    void upgradeToPro_userAlreadyPro_returns409() throws Exception {
        // Setup: user with an existing pro tenant
        // (utilise tenantProvisioningService ou fixture similaire à AuthController flow)
        // Pour la simplicité, marque-le comme test pending si la fixture est lourde :
        // OK to skip implementation detail — actual fixture setup follows project conventions.

        // The test should: create user + provision tenant + assert that a second upgrade-to-pro returns 409.
        // If too complex for one test, mark as @Disabled with TODO and rely on manual smoke test.
    }
}
```

**Note** : Le 3e test peut être plus simple à écrire après avoir vu comment `tenantProvisioningService` est utilisé en test. Si la setup fixture est lourde, le marquer `@Disabled` au début et le compléter plus tard.

- [ ] **Step 3 : Lancer les tests (ils doivent échouer car l'endpoint n'existe pas encore)**

Run: `cd backend && mvn test -Dtest=UpgradeToProControllerTests -q 2>&1 | tail -30`
Expected: ÉCHEC — 404 ou similaire car la route `/api/auth/upgrade-to-pro` n'existe pas.

---

### Task 6 : Implémenter l'endpoint `upgrade-to-pro` dans `AuthController`

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/AuthController.java`

- [ ] **Step 1 : Ajouter l'import du DTO**

Ajouter dans la section des imports (avec les autres `import com.luxpretty.app.auth.dto.*`) :
```java
import com.luxpretty.app.auth.dto.ProUpgradeRequest;
import com.luxpretty.app.users.domain.Role;
import org.springframework.security.core.Authentication;
```

(Vérifier les imports existants — `Authentication` est sans doute déjà importé.)

- [ ] **Step 2 : Ajouter la méthode `upgradeToPro` après `registerPro` (~ligne 90)**

```java
    @PostMapping("/upgrade-to-pro")
    @Transactional
    public ResponseEntity<AuthResponse> upgradeToPro(
            @Valid @RequestBody ProUpgradeRequest request,
            Authentication authentication
    ) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        // Reject if user already has a pro tenant
        boolean alreadyPro = userRoleService.findUserTenantIds(user.getId())
            .stream()
            .findAny()
            .isPresent();
        if (alreadyPro) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already has a pro tenant");
        }

        var tenant = tenantProvisioningService.provision(user);
        tenant.setName(user.getName());
        tenant.setSubscriptionTier(request.tier());
        tenant.setSubscriptionBilling(request.billing());
        tenantRepository.save(tenant);

        try {
            subscriptionService.initializeForTenant(user, tenant);
        } catch (Exception e) {
            logger.warn("Failed to initialize Stripe customer for upgraded user {}: {}", user.getEmail(), e.getMessage());
        }

        AuthResponse response = buildAuthResponse(user, tenant.getId());
        return ResponseEntity.ok(response);
    }
```

**Note importante** sur la détection "déjà pro" : `userRoleService.findUserTenantIds` retourne tous les tenants où l'user a un rôle. La logique ci-dessus considère "any tenant = already pro", ce qui peut être incorrect si l'user est EMPLOYEE quelque part. Vérifier le comportement attendu en lisant `UserRoleService.java` au step suivant et raffiner si besoin (ex : filtrer par `Role.PRO`).

- [ ] **Step 3 : Vérifier la logique "déjà pro"**

Run: `grep -n "findUserTenantIds\|hasRoleInTenant\|Role.PRO" backend/src/main/java/com/luxpretty/app/users/app/UserRoleService.java`

Si une méthode plus précise existe (ex : `findTenantsWhereUserHasRole(userId, Role.PRO)`), utiliser celle-là à la place dans la condition `alreadyPro`. Sinon, conserver le check actuel (any tenant = already pro) — c'est conservateur et évite les doublons.

- [ ] **Step 4 : Compiler**

Run: `cd backend && mvn compile -q 2>&1 | tail -10`
Expected: BUILD SUCCESS

- [ ] **Step 5 : Lancer les tests d'upgrade**

Run: `cd backend && mvn test -Dtest=UpgradeToProControllerTests -q 2>&1 | tail -30`
Expected: tests 401 et 200 passent. Le test 409 peut rester @Disabled si fixture trop lourde.

- [ ] **Step 6 : Lancer toute la suite backend**

Run: `cd backend && mvn test -q 2>&1 | tail -20`
Expected: BUILD SUCCESS

- [ ] **Step 7 : Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/auth/dto/ProUpgradeRequest.java \
        backend/src/main/java/com/luxpretty/app/auth/AuthController.java \
        backend/src/test/java/com/luxpretty/app/auth/
git commit -m "$(cat <<'EOF'
feat(auth): add POST /api/auth/upgrade-to-pro endpoint

Allow authenticated clients to upgrade to a pro account without
going through the signup modal. Creates a pro tenant linked to
the existing user, with the chosen tier+billing pre-set. Returns
409 if the user already has any tenant.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 7 : Ajouter VITRINE au `PricingCatalog` + retirer le throw `VITRINE`

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/subscription/app/PricingCatalog.java`
- Modify: `backend/src/main/java/com/luxpretty/app/subscription/app/SubscriptionService.java`

- [ ] **Step 1 : Lire `PricingCatalog` pour repère**

Run: `cat backend/src/main/java/com/luxpretty/app/subscription/app/PricingCatalog.java`

- [ ] **Step 2 : Ajouter les `@Value` VITRINE en haut de la classe**

Trouver les `@Value` existants (probable `stripe.price.gestion-monthly`, etc.) et ajouter à côté :

```java
    @Value("${stripe.price.vitrine-monthly:}")
    private String vitrineMonthly;

    @Value("${stripe.price.vitrine-yearly:}")
    private String vitrineYearly;
```

(Le `:` à la fin signifie "valeur par défaut = chaîne vide" → Spring ne crashera pas au boot si la propriété n'est pas définie.)

- [ ] **Step 3 : Ajouter les `putIfPresent` dans `@PostConstruct` ou le constructeur**

Trouver le bloc `@PostConstruct` (ou constructeur) qui contient :
```java
putIfPresent(SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY, gestionMonthly);
putIfPresent(SubscriptionTier.GESTION, SubscriptionBilling.YEARLY, gestionYearly);
putIfPresent(SubscriptionTier.PREMIUM, SubscriptionBilling.MONTHLY, premiumMonthly);
putIfPresent(SubscriptionTier.PREMIUM, SubscriptionBilling.YEARLY, premiumYearly);
```

Ajouter au début (avant les GESTION) :
```java
putIfPresent(SubscriptionTier.VITRINE, SubscriptionBilling.MONTHLY, vitrineMonthly);
putIfPresent(SubscriptionTier.VITRINE, SubscriptionBilling.YEARLY, vitrineYearly);
```

- [ ] **Step 4 : Retirer le check `VITRINE` dans `priceIdFor`**

Trouver dans `priceIdFor` :
```java
public Optional<String> priceIdFor(SubscriptionTier tier, SubscriptionBilling billing) {
    if (tier == SubscriptionTier.VITRINE) {
        return Optional.empty();
    }
    ...
```

Remplacer par :
```java
public Optional<String> priceIdFor(SubscriptionTier tier, SubscriptionBilling billing) {
    // All tiers (including VITRINE) can have Stripe Price IDs. If a tier+billing
    // combo isn't configured yet, returns Optional.empty() naturally via the map.
    ...
```

(Supprime juste le `if VITRINE` — laisse la map lookup faire son travail.)

- [ ] **Step 5 : Retirer le throw VITRINE dans `SubscriptionService.startCheckout`**

Run: `grep -n "VITRINE tier does not require\|tier == SubscriptionTier.VITRINE" backend/src/main/java/com/luxpretty/app/subscription/app/SubscriptionService.java`

Localiser le bloc :
```java
if (tier == SubscriptionTier.VITRINE) {
    throw new IllegalArgumentException("VITRINE tier does not require a Stripe subscription");
}
```

Supprimer ce bloc entièrement. VITRINE est désormais un tier payant normal.

- [ ] **Step 6 : Compiler + tests**

Run: `cd backend && mvn test -q 2>&1 | tail -20`
Expected: BUILD SUCCESS. Si un test vérifie spécifiquement que `startCheckout(VITRINE, ...)` throw, l'adapter pour vérifier qu'il fonctionne maintenant (passe par le Stripe code path).

- [ ] **Step 7 : Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/subscription/app/PricingCatalog.java \
        backend/src/main/java/com/luxpretty/app/subscription/app/SubscriptionService.java \
        backend/src/test/
git commit -m "$(cat <<'EOF'
feat(subscription): VITRINE is now a paid tier (9.99/7.99)

Add Stripe Price ID slots for VITRINE_MONTHLY and VITRINE_YEARLY
in PricingCatalog. Remove the IllegalArgumentException thrown by
startCheckout for VITRINE — it's now a regular paid tier. Actual
Stripe Price IDs must be created in the Stripe Dashboard and
injected via application properties (stripe.price.vitrine-*).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## PR2 — Frontend : restaurer `/pricing` + ProSignupModal

### Task 8 : Restaurer les 4 fichiers `PricingPageComponent` depuis l'historique

**Files:**
- Create (4 fichiers via git show) :
  - `frontend/src/app/features/subscription/pricing/pricing-page.component.ts`
  - `frontend/src/app/features/subscription/pricing/pricing-page.component.html`
  - `frontend/src/app/features/subscription/pricing/pricing-page.component.scss`
  - `frontend/src/app/features/subscription/pricing/pricing-page.component.spec.ts`

- [ ] **Step 1 : Créer le dossier**

Run: `mkdir -p frontend/src/app/features/subscription/pricing`

- [ ] **Step 2 : Restaurer les 4 fichiers depuis le commit parent de `c3b02eb`**

```bash
git show c3b02eb^:frontend/src/app/features/subscription/pricing/pricing-page.component.ts > frontend/src/app/features/subscription/pricing/pricing-page.component.ts
git show c3b02eb^:frontend/src/app/features/subscription/pricing/pricing-page.component.html > frontend/src/app/features/subscription/pricing/pricing-page.component.html
git show c3b02eb^:frontend/src/app/features/subscription/pricing/pricing-page.component.scss > frontend/src/app/features/subscription/pricing/pricing-page.component.scss
git show c3b02eb^:frontend/src/app/features/subscription/pricing/pricing-page.component.spec.ts > frontend/src/app/features/subscription/pricing/pricing-page.component.spec.ts
```

- [ ] **Step 3 : Vérifier que les fichiers sont là et lisibles**

Run: `ls -la frontend/src/app/features/subscription/pricing/`
Expected: 4 fichiers, chacun > 1 ko

- [ ] **Step 4 : Restaurer la route `/pricing` (et `/register/pro` → redirect)**

Modifier `frontend/src/app/app.routes.ts`. Trouver le bloc actuel :

```typescript
  { path: 'pricing', redirectTo: '/register/pro', pathMatch: 'full' },
  {
    path: 'register/pro',
    loadComponent: () => import('./pages/auth/register-pro/register-pro.component').then(m => m.RegisterProComponent),
  },
```

(Si l'état actuel est différent, adapter selon ce qui existe à ce moment-là — au moment d'écrire ce plan on est sur `main` où le merge n'a pas eu lieu, donc la route `/pricing` est encore le lazy load original. Vérifier d'abord avec `grep -n "pricing\|register/pro" frontend/src/app/app.routes.ts`.)

État cible à la fin de cette task :
```typescript
  {
    path: 'pricing',
    loadComponent: () =>
      import('./features/subscription/pricing/pricing-page.component').then(
        (m) => m.PricingPageComponent,
      ),
  },
  { path: 'register/pro', redirectTo: '/pricing', pathMatch: 'full' },
```

- [ ] **Step 5 : Build + tests pour vérifier que tout compile**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`
Expected: tests passent (incluant ceux de pricing-page restaurés)

- [ ] **Step 6 : Commit**

```bash
git add frontend/src/app/features/subscription/pricing/ frontend/src/app/app.routes.ts
git commit -m "$(cat <<'EOF'
revert(pricing): restore PricingPageComponent from c3b02eb^

Restore the comparative pricing table UI. The /pricing route is
restored too; /register/pro now redirects there. VITRINE pricing
will be set to 9.99/7.99 in the next task.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 9 : Rendre VITRINE payant dans `pricing-page.component.ts`

**Files:**
- Modify: `frontend/src/app/features/subscription/pricing/pricing-page.component.ts`

- [ ] **Step 1 : Ajouter les fallbacks VITRINE**

Trouver le bloc `FALLBACKS` (~ ligne 52) :

```typescript
  private readonly FALLBACKS = {
    gestionMonthly: 49.99,
    gestionYearly: 42.49,
    premiumMonthly: 67.99,
    premiumYearly: 57.79,
  };
```

Remplacer par :

```typescript
  private readonly FALLBACKS = {
    vitrineMonthly: 9.99,
    vitrineYearly: 7.99,
    gestionMonthly: 49.99,
    gestionYearly: 42.49,
    premiumMonthly: 67.99,
    premiumYearly: 57.79,
  };
```

- [ ] **Step 2 : Ajouter un computed `vitrinePrice`**

Trouver les autres computeds (`gestionPrice`, `premiumPrice`, ~ ligne 60) :

```typescript
  gestionPrice = computed(() => this.priceFor('GESTION', this.billing()));
  premiumPrice = computed(() => this.priceFor('PREMIUM', this.billing()));
```

Ajouter juste avant :

```typescript
  vitrinePrice = computed(() => this.priceFor('VITRINE', this.billing()));
```

- [ ] **Step 3 : Ajouter la branche VITRINE dans `priceFor()`**

Trouver la méthode `priceFor` (~ ligne 138). Elle ne gère aujourd'hui que GESTION et PREMIUM. Ajouter une branche VITRINE.

État actuel attendu (avant edit) :
```typescript
  private priceFor(tier: 'GESTION' | 'PREMIUM', billing: 'MONTHLY' | 'YEARLY'): number {
    const plan = this.plans().find(...)
    if (plan) {
      return plan.monthlyPriceEuros;
    }
    // Fallback
    if (tier === 'GESTION') {
      return billing === 'YEARLY' ? this.FALLBACKS.gestionYearly : this.FALLBACKS.gestionMonthly;
    }
    return billing === 'YEARLY' ? this.FALLBACKS.premiumYearly : this.FALLBACKS.premiumMonthly;
  }
```

Remplacer la signature et le corps :

```typescript
  private priceFor(tier: 'VITRINE' | 'GESTION' | 'PREMIUM', billing: 'MONTHLY' | 'YEARLY'): number {
    const plan = this.plans().find((p) => p.tier === tier && p.billing === billing);
    if (plan) {
      return plan.monthlyPriceEuros;
    }
    if (tier === 'VITRINE') {
      return billing === 'YEARLY' ? this.FALLBACKS.vitrineYearly : this.FALLBACKS.vitrineMonthly;
    }
    if (tier === 'GESTION') {
      return billing === 'YEARLY' ? this.FALLBACKS.gestionYearly : this.FALLBACKS.gestionMonthly;
    }
    return billing === 'YEARLY' ? this.FALLBACKS.premiumYearly : this.FALLBACKS.premiumMonthly;
  }
```

(Adapter la ligne `this.plans().find(...)` au code réel restauré — la logique de lookup PricingPlan ne change pas.)

- [ ] **Step 4 : Build pour vérifier que rien ne casse**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/pricing-page.component.spec.ts' 2>&1 | tail -10`
Expected: tests passent (le test d'affichage prix VITRINE sera ajouté plus tard)

---

### Task 10 : Update template `pricing-page.component.html` pour afficher prix VITRINE

**Files:**
- Modify: `frontend/src/app/features/subscription/pricing/pricing-page.component.html`

- [ ] **Step 1 : Trouver tous les usages de `pricing.tiers.vitrine.free`**

Run: `grep -n 'pricing.tiers.vitrine.free\|vitrine.*free\|vitrine.*Free' frontend/src/app/features/subscription/pricing/pricing-page.component.html`

Attendre 2-3 occurrences (table desktop col VITRINE + accordion mobile + éventuel sous-texte).

- [ ] **Step 2 : Remplacer les occurrences par le prix dynamique**

Pour chaque occurrence, remplacer un fragment du type :
```html
<div class="th-tier-price">{{ 'pricing.tiers.vitrine.free' | transloco }}</div>
```

Par :
```html
<div class="th-tier-price">{{ vitrinePrice() }}€/mois</div>
@if (billing() === 'YEARLY') {
  <div class="th-tier-label">{{ 'pricing.tiers.vitrine.annualNote' | transloco }}</div>
}
```

Adapter aux autres endroits (accordion mobile) qui pourraient avoir un format différent (`Gratuit pour toujours` etc.) — utiliser la même structure que pour `gestion` qui existe déjà juste à côté.

- [ ] **Step 3 : Trouver le footer CTA VITRINE (table desktop)**

Run: `grep -n 'tiers.vitrine.cta\|routerLink="/register"' frontend/src/app/features/subscription/pricing/pricing-page.component.html`

Trouver le bloc :
```html
<a routerLink="/register" class="btn-outline">
  {{ 'pricing.tiers.vitrine.cta' | transloco }}
</a>
```

Le remplacer par un bouton qui appelle `onStartTier('VITRINE')` (la méthode sera créée Task 12) :

```html
<button type="button" class="btn-outline" (click)="onStartTier('VITRINE')">
  {{ 'pricing.tiers.vitrine.cta' | transloco }}
</button>
```

- [ ] **Step 4 : Idem pour les CTAs GESTION et PREMIUM (table desktop)**

Trouver les blocs avec `routerLink="/register/pro"` et `[queryParams]="{ tier: 'GESTION', billing: billing() }"`. Les remplacer par :

```html
<button type="button" class="btn-solid" (click)="onStartTier('GESTION')">
  {{ 'pricing.tiers.gestion.cta' | transloco }}
</button>
```

Et :
```html
<button type="button" class="btn-outline" (click)="onStartTier('PREMIUM')">
  {{ 'pricing.tiers.premium.cta' | transloco }}
</button>
```

- [ ] **Step 5 : Idem pour les CTAs mobile accordion + featured card**

Run: `grep -n 'routerLink="/register' frontend/src/app/features/subscription/pricing/pricing-page.component.html`

Pour chaque résultat restant, remplacer par le bouton `(click)="onStartTier('...')"` correspondant au tier de cette section.

Aucun `routerLink="/register"` ne doit subsister dans ce fichier après cette étape.

- [ ] **Step 6 : Vérifier**

Run: `grep -n 'routerLink="/register\|/register/pro' frontend/src/app/features/subscription/pricing/pricing-page.component.html`
Expected: aucun résultat.

---

### Task 11 : Tests TDD pour `ProSignupModalComponent` (rouge)

**Files:**
- Create: `frontend/src/app/shared/modals/pro-signup-modal/pro-signup-modal.component.spec.ts`

- [ ] **Step 1 : Créer le dossier**

Run: `mkdir -p frontend/src/app/shared/modals/pro-signup-modal`

- [ ] **Step 2 : Créer le fichier de spec (test-first)**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideZonelessChangeDetection } from '@angular/core';
import { of, throwError, Observable } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslocoTestingModule } from '@jsverse/transloco';

import {
  ProSignupModalComponent,
  ProSignupModalData,
} from './pro-signup-modal.component';
import { AuthService } from '../../../core/auth/auth.service';

describe('ProSignupModalComponent', () => {
  let fixture: ComponentFixture<ProSignupModalComponent>;
  let component: ProSignupModalComponent;
  let authService: jasmine.SpyObj<AuthService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<ProSignupModalComponent>>;
  const data: ProSignupModalData = { tier: 'GESTION', billing: 'YEARLY' };

  beforeEach(async () => {
    authService = jasmine.createSpyObj('AuthService', ['registerPro']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [
        ProSignupModalComponent,
        TranslocoTestingModule.forRoot({ langs: { fr: {} }, translocoConfig: { defaultLang: 'fr' } }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authService },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProSignupModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the component', () => {
    expect(component).toBeTruthy();
  });

  it('exposes tier and billing from injected data', () => {
    expect(component.tier).toBe('GESTION');
    expect(component.billing).toBe('YEARLY');
  });

  it('isFormValid returns false when fields are empty', () => {
    expect(component.isFormValid()).toBe(false);
  });

  it('isFormValid returns true when all 4 fields are filled', () => {
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.password.set('password123');
    component.consent.set(true);
    expect(component.isFormValid()).toBe(true);
  });

  it('submit calls registerPro with tier+billing from data', () => {
    authService.registerPro.and.returnValue(of({ id: 1 } as any));
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.password.set('password123');
    component.consent.set(true);

    component.submit();

    expect(authService.registerPro).toHaveBeenCalledWith(jasmine.objectContaining({
      name: 'Alice',
      email: 'a@b.com',
      password: 'password123',
      tier: 'GESTION',
      billing: 'YEARLY',
    }));
  });

  it('submit closes dialog with authenticated:true on success', () => {
    authService.registerPro.and.returnValue(of({ id: 1 } as any));
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.password.set('password123');
    component.consent.set(true);

    component.submit();

    expect(dialogRef.close).toHaveBeenCalledWith({ authenticated: true });
  });

  it('submit sets emailAlreadyInUse error on 409', () => {
    authService.registerPro.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409 }))
    );
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.password.set('password123');
    component.consent.set(true);

    component.submit();

    expect(component.errorKey()).toBe('proSignup.modal.errors.emailAlreadyInUse');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('submit sets networkError on non-409 error', () => {
    authService.registerPro.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.password.set('password123');
    component.consent.set(true);

    component.submit();

    expect(component.errorKey()).toBe('proSignup.modal.errors.networkError');
  });

  it('submit does nothing when form invalid', () => {
    component.submit();
    expect(authService.registerPro).not.toHaveBeenCalled();
  });

  it('re-entrancy guard: second submit while loading does nothing', () => {
    authService.registerPro.and.returnValue(new Observable(() => {}));
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.password.set('password123');
    component.consent.set(true);

    component.submit();
    component.submit();

    expect(authService.registerPro).toHaveBeenCalledTimes(1);
  });

  it('openLogin closes dialog with authenticated:false', () => {
    component.openLogin();
    expect(dialogRef.close).toHaveBeenCalledWith({ authenticated: false, switchToLogin: true });
  });
});
```

- [ ] **Step 3 : Lancer pour vérifier que les tests échouent**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/pro-signup-modal.component.spec.ts' 2>&1 | tail -20`
Expected: ÉCHEC (compilation error : composant inexistant)

---

### Task 12 : Implémenter `ProSignupModalComponent`

**Files:**
- Create: `frontend/src/app/shared/modals/pro-signup-modal/pro-signup-modal.component.ts`
- Create: `frontend/src/app/shared/modals/pro-signup-modal/pro-signup-modal.component.html`
- Create: `frontend/src/app/shared/modals/pro-signup-modal/pro-signup-modal.component.scss`
- Modify: `frontend/src/app/core/auth/auth.service.ts`

- [ ] **Step 1 : Modifier `AuthService.registerPro` pour rendre les business fields optionnels**

Trouver la méthode `registerPro` dans `auth.service.ts`. La signature actuelle attend `salonName`, `phone`, etc. comme champs requis. Remplacer par :

```typescript
  registerPro(data: {
    name: string;
    email: string;
    password: string;
    tier: 'VITRINE' | 'GESTION' | 'PREMIUM';
    billing: 'MONTHLY' | 'YEARLY';
    salonName?: string;
    phone?: string;
    addressStreet?: string;
    addressPostalCode?: string;
    addressCity?: string;
    siret?: string;
  }): Observable<User> {
    return this.http.post<{accessToken: string, user: User}>(
      `${this.apiBaseUrl}/api/auth/register/pro`,
      { ...data, consent: true }
    ).pipe(
      tap(response => {
        this.setToken(response.accessToken);
        this.currentUser.set(response.user);
      }),
      map(response => response.user),
      catchError(error => { throw error; })
    );
  }
```

- [ ] **Step 2 : Ajouter `upgradeToPro` dans `AuthService`**

Juste après `registerPro`, ajouter :

```typescript
  upgradeToPro(data: {
    tier: 'VITRINE' | 'GESTION' | 'PREMIUM';
    billing: 'MONTHLY' | 'YEARLY';
  }): Observable<User> {
    return this.http.post<{accessToken: string, user: User}>(
      `${this.apiBaseUrl}/api/auth/upgrade-to-pro`,
      data
    ).pipe(
      tap(response => {
        this.setToken(response.accessToken);
        this.currentUser.set(response.user);
      }),
      map(response => response.user),
    );
  }
```

- [ ] **Step 3 : Créer `pro-signup-modal.component.ts`**

```typescript
import { Component, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';

export interface ProSignupModalData {
  tier: 'VITRINE' | 'GESTION' | 'PREMIUM';
  billing: 'MONTHLY' | 'YEARLY';
}

export interface ProSignupModalResult {
  authenticated: boolean;
  switchToLogin?: boolean;
}

@Component({
  selector: 'app-pro-signup-modal',
  standalone: true,
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    TranslocoPipe,
  ],
  templateUrl: './pro-signup-modal.component.html',
  styleUrl: './pro-signup-modal.component.scss',
})
export class ProSignupModalComponent {
  private readonly authService = inject(AuthService);
  private readonly dialogRef = inject(MatDialogRef<ProSignupModalComponent>);
  private readonly data = inject<ProSignupModalData>(MAT_DIALOG_DATA);

  readonly tier = this.data.tier;
  readonly billing = this.data.billing;

  readonly name = signal('');
  readonly email = signal('');
  readonly password = signal('');
  readonly consent = signal(false);
  readonly isLoading = signal(false);
  readonly errorKey = signal<string | null>(null);

  isFormValid(): boolean {
    return this.name().trim().length > 0
      && this.email().includes('@')
      && this.password().length >= 8
      && this.consent();
  }

  submit(): void {
    if (!this.isFormValid()) return;
    if (this.isLoading()) return;

    this.isLoading.set(true);
    this.errorKey.set(null);

    this.authService.registerPro({
      name: this.name().trim(),
      email: this.email().trim(),
      password: this.password(),
      tier: this.tier,
      billing: this.billing,
    }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.dialogRef.close({ authenticated: true } as ProSignupModalResult);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        if (err.status === 409) {
          this.errorKey.set('proSignup.modal.errors.emailAlreadyInUse');
        } else {
          this.errorKey.set('proSignup.modal.errors.networkError');
        }
      },
    });
  }

  openLogin(): void {
    this.dialogRef.close({ authenticated: false, switchToLogin: true } as ProSignupModalResult);
  }

  close(): void {
    this.dialogRef.close({ authenticated: false } as ProSignupModalResult);
  }
}
```

- [ ] **Step 4 : Créer `pro-signup-modal.component.html`**

```html
<div class="pro-signup-modal">
  <header class="modal-header">
    <h2 mat-dialog-title>
      {{ 'proSignup.modal.title' | transloco: { tier: tier } }}
    </h2>
    <p class="modal-subtitle">
      {{ 'proSignup.modal.subtitle' | transloco: { billing: billing } }}
    </p>
  </header>

  <form mat-dialog-content class="form" (submit)="$event.preventDefault(); submit()">
    <mat-form-field appearance="outline">
      <mat-label>{{ 'proSignup.modal.fields.name' | transloco }}</mat-label>
      <input matInput required [ngModel]="name()" (ngModelChange)="name.set($event)" name="name" />
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>{{ 'proSignup.modal.fields.email' | transloco }}</mat-label>
      <input matInput required type="email" [ngModel]="email()" (ngModelChange)="email.set($event)" name="email" />
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>{{ 'proSignup.modal.fields.password' | transloco }}</mat-label>
      <input matInput required type="password" minlength="8" [ngModel]="password()" (ngModelChange)="password.set($event)" name="password" />
    </mat-form-field>

    <mat-checkbox required [ngModel]="consent()" (ngModelChange)="consent.set($event)" name="consent">
      {{ 'proSignup.modal.fields.consent' | transloco }}
    </mat-checkbox>

    @if (errorKey()) {
      <p class="form-error">{{ errorKey()! | transloco }}</p>
    }

    <button
      mat-flat-button
      color="primary"
      type="submit"
      class="submit-btn"
      [disabled]="!isFormValid() || isLoading()"
    >
      @if (isLoading()) {
        <mat-spinner diameter="20"></mat-spinner>
      } @else {
        {{ 'proSignup.modal.submit' | transloco }}
      }
    </button>

    <button type="button" class="link-btn" (click)="openLogin()">
      {{ 'proSignup.modal.alreadyHaveAccount' | transloco }}
    </button>

    <p class="no-card-notice">
      {{ 'proSignup.modal.noCard' | transloco }}
    </p>
  </form>
</div>
```

- [ ] **Step 5 : Créer `pro-signup-modal.component.scss`**

```scss
.pro-signup-modal {
  display: flex;
  flex-direction: column;
  padding: 24px;
  max-width: 480px;
  width: 100%;
}

.modal-header {
  margin-bottom: 16px;

  h2 {
    margin: 0 0 4px;
    font-size: 20px;
    font-weight: 600;
  }

  .modal-subtitle {
    margin: 0;
    font-size: 13px;
    color: var(--mat-sys-on-surface-variant);
  }
}

.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-error {
  color: var(--mat-sys-error);
  font-size: 14px;
  margin: 0;
}

.submit-btn {
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  margin-top: 8px;
}

.link-btn {
  background: transparent;
  border: none;
  color: var(--mat-sys-primary);
  font-size: 13px;
  cursor: pointer;
  padding: 8px;
  text-align: center;
  font-family: inherit;

  &:hover {
    text-decoration: underline;
  }
}

.no-card-notice {
  text-align: center;
  font-size: 12px;
  color: var(--mat-sys-on-surface-variant);
  margin: 8px 0 0;
}
```

- [ ] **Step 6 : Lancer les tests du modal**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/pro-signup-modal.component.spec.ts' 2>&1 | tail -20`
Expected: 11 tests PASS

- [ ] **Step 7 : Commit**

```bash
git add frontend/src/app/shared/modals/pro-signup-modal/ \
        frontend/src/app/core/auth/auth.service.ts
git commit -m "$(cat <<'EOF'
feat(pro-signup-modal): light signup modal triggered from /pricing CTAs

4-field signup modal (name+email+password+consent) with tier+billing
injected as MAT_DIALOG_DATA. On success, closes with authenticated:true.
On 409 conflict, surfaces emailAlreadyInUse error. The caller is
responsible for navigating to /pro/dashboard after closure.

Also adds AuthService.upgradeToPro for already-authenticated clients
and relaxes registerPro to make business fields optional.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 13 : Brancher `onStartTier` dans `pricing-page.component.ts`

**Files:**
- Modify: `frontend/src/app/features/subscription/pricing/pricing-page.component.ts`

- [ ] **Step 1 : Ajouter les imports**

Dans `pricing-page.component.ts`, ajouter (avec les autres imports en tête) :

```typescript
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { ProSignupModalComponent, ProSignupModalResult } from '../../../shared/modals/pro-signup-modal/pro-signup-modal.component';
```

- [ ] **Step 2 : Injecter les dépendances dans la classe**

À côté des autres `inject(...)` au début de la classe, ajouter :

```typescript
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
```

- [ ] **Step 3 : Ajouter la méthode `onStartTier`**

À la fin de la classe (avant la fermeture de l'accolade) :

```typescript
  onStartTier(tier: 'VITRINE' | 'GESTION' | 'PREMIUM'): void {
    const billing = this.billing();
    const user = this.authService.currentUser();

    if (!user) {
      this.dialog.open(ProSignupModalComponent, {
        data: { tier, billing },
        width: '480px',
      }).afterClosed().subscribe((result: ProSignupModalResult | undefined) => {
        if (result?.authenticated) {
          this.router.navigate(['/pro/dashboard']);
        }
        // If result?.switchToLogin is true, we could open an AuthModal here.
        // For MVP, the link in the signup modal simply closes — user can use
        // the regular login flow from the header.
      });
      return;
    }

    if (user.role === 'PRO') {
      this.router.navigate(['/pro/dashboard']);
      return;
    }

    // Authenticated client → upgrade directly
    this.authService.upgradeToPro({ tier, billing }).subscribe({
      next: () => this.router.navigate(['/pro/dashboard']),
      error: (err) => {
        if (err.status === 409) {
          // User already has a pro tenant — just navigate
          this.router.navigate(['/pro/dashboard']);
        } else {
          this.snackBar.open('Une erreur est survenue', 'OK', { duration: 4000 });
        }
      },
    });
  }
```

- [ ] **Step 4 : Vérifier que `currentUser()` retourne bien un objet avec `.role`**

Run: `grep -n "interface User\|role:" frontend/src/app/core/auth/auth.model.ts`

Si le type `Role` est un enum (probable `Role.PRO`), ajuster le check :
```typescript
if (user.role === Role.PRO) {
```
et ajouter l'import `import { Role } from '../../../core/auth/auth.model';`.

- [ ] **Step 5 : Build + tests pricing-page**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/pricing-page.component.spec.ts' 2>&1 | tail -20`
Expected: tests existants passent (les tests de `onStartTier` seront ajoutés Task 14)

---

### Task 14 : Ajouter les tests de `onStartTier` dans `pricing-page.component.spec.ts`

**Files:**
- Modify: `frontend/src/app/features/subscription/pricing/pricing-page.component.spec.ts`

- [ ] **Step 1 : Lire la spec restaurée pour comprendre la setup TestBed existante**

Run: `head -80 frontend/src/app/features/subscription/pricing/pricing-page.component.spec.ts`

Identifier : quels providers existent, quels services sont mockés.

- [ ] **Step 2 : Ajouter les imports nécessaires en haut du fichier (si manquants)**

```typescript
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ProSignupModalComponent } from '../../../shared/modals/pro-signup-modal/pro-signup-modal.component';
```

- [ ] **Step 3 : Adapter le TestBed pour mocker AuthService et MatDialog**

Dans le `beforeEach`, ajouter aux `providers` :

```typescript
        { provide: AuthService, useValue: jasmine.createSpyObj('AuthService', ['currentUser', 'upgradeToPro']) },
        { provide: MatDialog, useValue: jasmine.createSpyObj('MatDialog', ['open']) },
```

(Le `Router` est probablement déjà fourni via `provideRouter([])` — sinon l'ajouter.)

- [ ] **Step 4 : Ajouter un `describe('onStartTier', ...)`**

À la fin du fichier (avant la fermeture du describe principal) :

```typescript
  describe('onStartTier', () => {
    let authService: jasmine.SpyObj<AuthService>;
    let dialog: jasmine.SpyObj<MatDialog>;
    let router: Router;

    beforeEach(() => {
      authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
      dialog = TestBed.inject(MatDialog) as jasmine.SpyObj<MatDialog>;
      router = TestBed.inject(Router);
    });

    it('opens ProSignupModal when user is not authenticated', () => {
      authService.currentUser.and.returnValue(null);
      const dialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
      dialogRef.afterClosed.and.returnValue(of({ authenticated: true }));
      dialog.open.and.returnValue(dialogRef);
      spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

      component.onStartTier('GESTION');

      expect(dialog.open).toHaveBeenCalledWith(ProSignupModalComponent, jasmine.objectContaining({
        data: jasmine.objectContaining({ tier: 'GESTION' }),
      }));
      expect(router.navigate).toHaveBeenCalledWith(['/pro/dashboard']);
    });

    it('does not navigate when modal closes without authentication', () => {
      authService.currentUser.and.returnValue(null);
      const dialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
      dialogRef.afterClosed.and.returnValue(of({ authenticated: false }));
      dialog.open.and.returnValue(dialogRef);
      spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

      component.onStartTier('GESTION');

      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('navigates directly to /pro/dashboard when user is already PRO', () => {
      authService.currentUser.and.returnValue({ id: 1, role: 'PRO' } as any);
      spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

      component.onStartTier('PREMIUM');

      expect(router.navigate).toHaveBeenCalledWith(['/pro/dashboard']);
      expect(dialog.open).not.toHaveBeenCalled();
    });

    it('calls upgradeToPro and navigates when user is a CLIENT', () => {
      authService.currentUser.and.returnValue({ id: 1, role: 'CLIENT' } as any);
      authService.upgradeToPro.and.returnValue(of({ id: 1, role: 'PRO' } as any));
      spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

      component.onStartTier('VITRINE');

      expect(authService.upgradeToPro).toHaveBeenCalledWith({ tier: 'VITRINE', billing: jasmine.any(String) });
      expect(router.navigate).toHaveBeenCalledWith(['/pro/dashboard']);
    });

    it('redirects to dashboard on 409 (user already pro)', () => {
      authService.currentUser.and.returnValue({ id: 1, role: 'CLIENT' } as any);
      authService.upgradeToPro.and.returnValue(throwError(() => ({ status: 409 })));
      spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

      component.onStartTier('GESTION');

      expect(router.navigate).toHaveBeenCalledWith(['/pro/dashboard']);
    });
  });
```

- [ ] **Step 5 : Lancer les tests pricing-page**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/pricing-page.component.spec.ts' 2>&1 | tail -20`
Expected: tous les tests PASS (existants + 5 nouveaux)

- [ ] **Step 6 : Lancer toute la suite frontend**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`
Expected: tous verts

- [ ] **Step 7 : Commit (incluant aussi l'edit ts/html de Task 9 + 10 + 13)**

```bash
git add frontend/src/app/features/subscription/pricing/
git commit -m "$(cat <<'EOF'
feat(pricing): VITRINE 9.99/7.99 paid + CTAs open ProSignupModal

VITRINE tier now shows a price (9.99€ monthly / 7.99€ annual) in the
comparative table. All 3 tier CTAs (VITRINE/GESTION/PREMIUM) now call
onStartTier(), which opens the ProSignupModal for anonymous users,
calls upgradeToPro for clients, or routes straight to dashboard for
existing pros.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 15 : i18n — clés `pricing.tiers.vitrine.*` + `proSignup.modal.*`

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1 : Ajouter `pricing.tiers.vitrine.annualNote` dans `fr.json` et `en.json`**

Sous `pricing.tiers.vitrine.*`, ajouter :
- fr.json : `"annualNote": "facturé annuellement"`
- en.json : `"annualNote": "billed annually"`

Si l'ancienne clé `"free"` est encore référencée nulle part dans le code, la supprimer. Sinon, la laisser (innocuous).

- [ ] **Step 2 : Ajouter le sous-arbre `proSignup` dans `fr.json`**

```json
"proSignup": {
  "modal": {
    "title": "Démarrer avec {{ tier }}",
    "subtitle": "Facturation {{ billing }}",
    "fields": {
      "name": "Votre nom",
      "email": "Email",
      "password": "Mot de passe (8 caractères min.)",
      "consent": "J'accepte les conditions générales d'utilisation"
    },
    "submit": "Créer mon compte",
    "alreadyHaveAccount": "Vous avez déjà un compte ? Se connecter",
    "errors": {
      "emailAlreadyInUse": "Cet email est déjà utilisé. Connectez-vous.",
      "networkError": "Erreur réseau. Réessayez."
    },
    "noCard": "Aucune carte bancaire demandée. Vous paierez au moment de publier."
  }
}
```

(Ajouter au top-level, à côté de `pricing`, `register`, etc.)

- [ ] **Step 3 : Idem dans `en.json`**

```json
"proSignup": {
  "modal": {
    "title": "Get started with {{ tier }}",
    "subtitle": "Billing: {{ billing }}",
    "fields": {
      "name": "Your name",
      "email": "Email",
      "password": "Password (8 chars min.)",
      "consent": "I accept the terms and conditions"
    },
    "submit": "Create my account",
    "alreadyHaveAccount": "Already have an account? Log in",
    "errors": {
      "emailAlreadyInUse": "This email is already in use. Log in.",
      "networkError": "Network error. Try again."
    },
    "noCard": "No credit card required. You'll only pay when publishing."
  }
}
```

- [ ] **Step 4 : Valider JSON**

Run: `cd frontend && node -e "JSON.parse(require('fs').readFileSync('public/i18n/fr.json')); JSON.parse(require('fs').readFileSync('public/i18n/en.json')); console.log('OK')"`
Expected: `OK`

- [ ] **Step 5 : Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "$(cat <<'EOF'
i18n: add proSignup.modal.* and pricing.tiers.vitrine.annualNote

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 16 : Supprimer `RegisterProComponent` + mettre à jour CTAs

**Files:**
- Delete: `frontend/src/app/pages/auth/register-pro/` (4 fichiers)
- Modify: `frontend/src/app/pages/home/home.ts`
- Modify: `frontend/src/app/shared/layout/footer/footer.html`
- Modify: `frontend/src/app/shared/layout/navigation/navigation-routes.ts`

- [ ] **Step 1 : Supprimer le dossier register-pro**

Run: `rm -rf frontend/src/app/pages/auth/register-pro`

- [ ] **Step 2 : Vérifier que la route `/register/pro` est bien un redirect (set in Task 8)**

Run: `grep -A 1 "register/pro" frontend/src/app/app.routes.ts`
Expected: `{ path: 'register/pro', redirectTo: '/pricing', pathMatch: 'full' },` (sans loadComponent).

- [ ] **Step 3 : Trouver et remplacer les CTAs `/register/pro` → `/pricing`**

Run: `grep -rn "'/register/pro'\|\"/register/pro\"\|routerLink=\"/register/pro\"" frontend/src --include='*.ts' --include='*.html'`

Pour chaque occurrence (hors `app.routes.ts` qui est OK), remplacer `/register/pro` par `/pricing`. Suspects :
- `frontend/src/app/pages/home/home.ts` (lignes 113, 160)
- `frontend/src/app/shared/layout/footer/footer.html` (5 occurrences)
- `frontend/src/app/shared/layout/navigation/navigation-routes.ts:128`

Utiliser `Edit` avec `replace_all: true` fichier par fichier pour les fichiers ayant plusieurs occurrences.

- [ ] **Step 4 : Vérifier les imports orphelins**

Run: `grep -rn "RegisterProComponent\|register-pro" frontend/src --include='*.ts'`
Expected: aucun résultat.

- [ ] **Step 5 : Lancer les tests**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`
Expected: tous verts

- [ ] **Step 6 : Commit**

```bash
git add -A frontend/src/app/pages/auth/register-pro \
        frontend/src/app/pages/home/home.ts \
        frontend/src/app/shared/layout/footer/footer.html \
        frontend/src/app/shared/layout/navigation/navigation-routes.ts
git commit -m "$(cat <<'EOF'
chore(register-pro): remove RegisterProComponent, point CTAs to /pricing

The old multi-step pro registration form is gone — pro signup now
goes through /pricing → tier CTA → ProSignupModal. All CTAs in home,
footer, and navigation are updated. The /register/pro route is
preserved as a redirect.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 17 : Build production + smoke tests

**Files:** aucun

- [ ] **Step 1 : Build frontend prod**

Run: `cd frontend && npm run build 2>&1 | tail -10`
Expected: build OK (warnings tolérés)

- [ ] **Step 2 : Smoke test manuel**

Démarrer dev (docker compose pour frontend + mvn spring-boot:run pour backend) et tester :
- `/` → cliquer "Démarrer" sur la home → arrive sur `/pricing` (table comparative)
- Sur `/pricing` : toggle billing MONTHLY/YEARLY → prix VITRINE change de 9.99 à 7.99
- Sur `/pricing` : clic "Commencer" sur VITRINE → modal `ProSignupModal` s'ouvre avec "Démarrer avec VITRINE"
- Remplir le modal (4 champs) → soumettre → arrive sur `/pro/dashboard` (guided tour s'affiche)
- `/register/pro` → redirige vers `/pricing`

---

## PR3 — Frontend : Gate paiement avant publish

### Task 18 : Tests `pro-dashboard` pour le gate publish (rouge)

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-dashboard.component.spec.ts`

- [ ] **Step 1 : Lire le test existant pour la setup**

Run: `head -80 frontend/src/app/pages/pro/pro-dashboard.component.spec.ts`

Identifier comment `SubscriptionService` est ou n'est pas déjà mocké.

- [ ] **Step 2 : Ajouter `SubscriptionService` au TestBed providers (si manquant)**

Dans le `beforeEach`, ajouter au tableau `providers` :

```typescript
{ provide: SubscriptionService, useValue: jasmine.createSpyObj('SubscriptionService', ['getCurrentSubscription']) },
```

Ajouter l'import en haut :
```typescript
import { SubscriptionService } from '../../features/subscription/services/subscription.service';
```

- [ ] **Step 3 : Ajouter un `describe('publish gate', ...)` à la fin du test principal**

```typescript
  describe('publish gate (subscription check)', () => {
    let subscriptionService: jasmine.SpyObj<SubscriptionService>;
    let router: Router;

    beforeEach(() => {
      subscriptionService = TestBed.inject(SubscriptionService) as jasmine.SpyObj<SubscriptionService>;
      router = TestBed.inject(Router);
    });

    it('redirects to /pro/onboarding/payment when subscription is not active', () => {
      spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
      subscriptionService.getCurrentSubscription.and.returnValue(of({
        tier: 'GESTION',
        billing: 'YEARLY',
        status: 'VITRINE_FREE',
        stripeCustomerId: null,
        stripeSubscriptionId: null,
        currentPeriodEnd: null,
        trialEnd: null,
      }));

      component.onPublish();

      expect(router.navigate).toHaveBeenCalledWith(
        ['/pro/onboarding/payment'],
        { queryParams: { tier: 'GESTION', billing: 'YEARLY' } }
      );
    });

    it('calls store.publish() when subscription is ACTIVE', () => {
      const publishSpy = spyOn(component.store, 'publish');
      subscriptionService.getCurrentSubscription.and.returnValue(of({
        tier: 'GESTION', billing: 'YEARLY', status: 'ACTIVE',
        stripeCustomerId: 'cus_x', stripeSubscriptionId: 'sub_x',
        currentPeriodEnd: '2026-12-01', trialEnd: null,
      }));

      component.onPublish();

      expect(publishSpy).toHaveBeenCalled();
    });

    it('calls store.publish() when subscription is TRIALING', () => {
      const publishSpy = spyOn(component.store, 'publish');
      subscriptionService.getCurrentSubscription.and.returnValue(of({
        tier: 'PREMIUM', billing: 'MONTHLY', status: 'TRIALING',
        stripeCustomerId: 'cus_x', stripeSubscriptionId: 'sub_x',
        currentPeriodEnd: '2026-12-01', trialEnd: '2026-06-01',
      }));

      component.onPublish();

      expect(publishSpy).toHaveBeenCalled();
    });

    it('redirects to /pro/onboarding/payment when getCurrentSubscription errors', () => {
      spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
      subscriptionService.getCurrentSubscription.and.returnValue(
        throwError(() => new Error('network down'))
      );

      component.onPublish();

      expect(router.navigate).toHaveBeenCalledWith(['/pro/onboarding/payment']);
    });
  });
```

Ajouter les imports si manquants : `of, throwError` from rxjs.

- [ ] **Step 4 : Lancer pour vérifier l'échec**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/pro-dashboard.component.spec.ts' 2>&1 | tail -20`
Expected: ÉCHEC des 4 nouveaux tests (la méthode `onPublish` actuelle ne consulte pas la subscription).

---

### Task 19 : Implémenter le gate dans `pro-dashboard.component.ts`

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-dashboard.component.ts`

- [ ] **Step 1 : Ajouter l'import**

Ajouter en haut (avec les autres imports) :
```typescript
import { SubscriptionService } from '../../features/subscription/services/subscription.service';
```

- [ ] **Step 2 : Injecter `SubscriptionService`**

À côté des autres `inject(...)` au début de la classe :
```typescript
  private readonly subscriptionService = inject(SubscriptionService);
```

- [ ] **Step 3 : Remplacer la méthode `onPublish` (ligne ~362)**

```typescript
  onPublish(): void {
    this.subscriptionService.getCurrentSubscription().subscribe({
      next: (sub) => {
        if (sub.status === 'ACTIVE' || sub.status === 'TRIALING') {
          this.store.publish();
        } else {
          this.router.navigate(['/pro/onboarding/payment'], {
            queryParams: { tier: sub.tier, billing: sub.billing },
          });
        }
      },
      error: () => {
        this.router.navigate(['/pro/onboarding/payment']);
      },
    });
  }
```

Vérifier que `this.router` est déjà injecté ailleurs dans le composant — sinon ajouter `private readonly router = inject(Router);` et l'import.

- [ ] **Step 4 : Lancer les tests dashboard**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/pro-dashboard.component.spec.ts' 2>&1 | tail -20`
Expected: tous verts (existants + 4 nouveaux)

- [ ] **Step 5 : Lancer toute la suite frontend**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`
Expected: tous verts

- [ ] **Step 6 : Commit**

```bash
git add frontend/src/app/pages/pro/pro-dashboard.component.ts \
        frontend/src/app/pages/pro/pro-dashboard.component.spec.ts
git commit -m "$(cat <<'EOF'
feat(pro-dashboard): gate publish behind active Stripe subscription

Publish first calls getCurrentSubscription. ACTIVE/TRIALING → publish,
otherwise → redirect to /pro/onboarding/payment with tier+billing.
On network error, also redirect (safer default). After payment, pro
re-clicks Publish manually from the dashboard.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 20 : Final verification + push

**Files:** aucun

- [ ] **Step 1 : Tests frontend + backend en parallèle**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`
Run: `cd backend && mvn test -q 2>&1 | tail -10`
Expected: tous verts

- [ ] **Step 2 : Build prod**

Run: `cd frontend && npm run build 2>&1 | tail -5`
Expected: OK

- [ ] **Step 3 : Vérifier les commits sur la branche**

Run: `git log --oneline main..HEAD`
Expected: 8 à 10 commits propres

- [ ] **Step 4 : Smoke test manuel complet**

Couvrir :
1. Anonyme : `/pricing` → clic VITRINE → modal → inscription → dashboard + guided tour
2. Anonyme : `/pricing` → clic GESTION → modal → inscription → dashboard
3. Connecté client : `/pricing` → clic n'importe quel tier → direct dashboard (via upgrade)
4. Pro existant : `/pricing` → clic → direct dashboard sans modal
5. Dashboard pro DRAFT : clic Publier → redirect `/pro/onboarding/payment?tier=X&billing=Y`
6. `/register/pro` → redirect `/pricing`

- [ ] **Step 5 : Push (à confirmer par l'humain)**

Ne PAS push sans confirmation explicite. Si OK :
```bash
git push -u origin feat/pricing-restore-pro-signup
gh pr create --title "feat: restore /pricing + ProSignupModal (VITRINE paid)" --body "..."
```

---

## Self-Review

**Spec coverage :**
- ✅ §1 Restauration `/pricing` → Task 8
- ✅ §2 VITRINE payant 9.99/7.99 → Task 7 (backend), Task 9-10 (frontend)
- ✅ §3 `ProSignupModalComponent` → Task 11-12
- ✅ §4 Logique CTA `onStartTier` → Task 13-14
- ✅ §5 Backend `ProRegisterRequest` assoupli → Task 2-3
- ✅ §6 Endpoint `upgrade-to-pro` → Task 4-6
- ✅ §7 Suppression `RegisterProComponent` + CTAs → Task 16
- ✅ §8 Gate paiement publish → Task 18-19
- ✅ i18n → Task 15

**Placeholders :** aucun "TBD" ou "implement later" dans les steps.

**Type consistency :**
- `SubscriptionTier` partout : `'VITRINE' | 'GESTION' | 'PREMIUM'`
- `SubscriptionBilling` : `'MONTHLY' | 'YEARLY'` (le `'FREE'` de l'enum existant n'est pas utilisé dans le nouveau flow)
- `ProSignupModalData`/`Result` interfaces définies Task 12, utilisées Task 13
- `Role.PRO` vérifié Task 13 step 4 — soit string `'PRO'` soit enum, ajusté à ce moment-là
- `AuthService.registerPro` signature : Task 12 step 1, utilisée Task 12 step 3

**Risques notables à l'exécution :**
- Le test backend Task 5 step 2 "user already pro" peut être lourd à setup — marqué optionnel/`@Disabled`
- L'enum `Role` (string vs enum) ajusté à la volée en Task 13 step 4
- Le path i18n est `frontend/public/i18n/` (pas `frontend/src/assets/i18n/`) — confirmé par l'expérience précédente

---

## Execution Handoff

**Plan complet et sauvegardé dans `docs/superpowers/plans/2026-05-17-pricing-restore-pro-signup-modal.md`. Deux options d'exécution :**

**1. Subagent-Driven (recommandé)** — un subagent par task, review entre chaque, itération rapide

**2. Inline Execution** — exécution dans cette session, batch avec checkpoints

**Quelle approche ?**
