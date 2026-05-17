# Restauration `/pricing` + Pro Signup Modal (VITRINE payant)

**Date** : 2026-05-17
**Statut** : Spec validée, à implémenter
**Remplace** : `2026-05-17-unify-pricing-register-pro-design.md` (approche fausse, retirée)

---

## Objectif

Restaurer la page `/pricing` (UI table comparative existante) avec ses 3 tiers. Rendre VITRINE payant (9.99€/mois mensuel, 7.99€/mois annuel). Le clic "Démarrer" sur un tier ouvre un **modal d'inscription pro ultra-léger** (4 champs) qui crée le compte rapidement, puis le pro arrive sur `/pro/dashboard` où le **guided tour existant prend le relais** pour la configuration salon/cares/etc.

Pas de page d'inscription pro dédiée. Pas de formulaire long. Le compte se crée en 4 champs, l'onboarding gère le reste.

## Motivation

- Le pattern SaaS standard (Stripe, Notion, Shopify) : signup minimal → onboarding intégré qui demande progressivement les infos
- Le guided tour est déjà en place et fonctionnel — il faut juste lui laisser sa place
- L'ancienne page `/pricing` avait une UI riche (table comparative desktop + accordion mobile + featured card) à conserver
- VITRINE gratuit créait de la dette : on conserve VITRINE mais on le rend payant pour aligner avec le modèle business

## Périmètre

### Inclus

1. Restauration de `PricingPageComponent` (récup via `git show c3b02eb^:...`)
2. VITRINE payant : prix 9.99€ mensuel / 7.99€ annuel, mise à jour table comparative + PricingCatalog backend
3. Nouveau composant `ProSignupModalComponent` (4 champs : name/email/password/consent + rappel du tier choisi)
4. CTAs des 3 tiers dans `/pricing` ouvrent le modal (au lieu de naviguer vers `/register/pro`)
5. Si user déjà authentifié au clic CTA → skip modal, appeler endpoint `/api/pro/upgrade` pour lier un tenant pro au user existant
6. Backend : `ProRegisterRequest` assoupli (salonName + adresse deviennent optionnels) + ajout `tier` + `billing` obligatoires
7. Nouveau endpoint `POST /api/pro/upgrade` pour user authentifié sans tenant pro
8. Suppression `RegisterProComponent` + route `/register/pro` (redirect vers `/pricing` pour anciens liens)
9. Mise à jour CTAs home + footer : "Démarrer" → `/pricing`
10. Gate paiement avant publish (déjà spec'é dans l'ancienne version, repris ici)

### Exclus

- Refonte du guided tour (`features/onboarding/tour/`) — reste inchangé
- Refonte du `AuthModalComponent` existant (utilisé pour clients) — reste inchangé
- Refonte du `RegisterComponent` (`/register` pour clients) — reste inchangé
- Stripe Price IDs VITRINE_MONTHLY / VITRINE_YEARLY (action manuelle Stripe Dashboard, hors code)
- Migration de données pros existants (zéro pro en prod confirmé)
- E-mail welcome après upgrade-to-pro (le mail welcome standard du registerPro continue de marcher)

## Architecture

### 1. Restauration `/pricing`

```typescript
// app.routes.ts
{
  path: 'pricing',
  loadComponent: () =>
    import('./features/subscription/pricing/pricing-page.component').then(
      (m) => m.PricingPageComponent,
    ),
},
{ path: 'register/pro', redirectTo: '/pricing', pathMatch: 'full' },
```

**Source du composant** : `git show c3b02eb^:frontend/src/app/features/subscription/pricing/pricing-page.component.ts` (et `.html`, `.scss`, `.spec.ts`). Recréer le dossier `frontend/src/app/features/subscription/pricing/` avec les 4 fichiers.

### 2. VITRINE payant

**Frontend (`pricing-page.component.ts` + `.html`)** :
- Ajouter `vitrineMonthly = 9.99`, `vitrineYearly = 7.99` aux signaux/constantes
- Dans la table comparative : remplacer `pricing.tiers.vitrine.free` ("Gratuit") par `pricing.tiers.vitrine.price` avec interpolation prix + format `/mois` (et "facturé annuellement" si yearly)
- Le check/cross/unlimited dans les colonnes VITRINE reste **inchangé** (mêmes features qu'avant, juste le prix change)
- Footer row VITRINE : remplacer le `routerLink="/register"` par un bouton qui appelle `onStartTier('VITRINE')` (voir §3 ci-dessous)

**i18n** :
- Modifier `pricing.tiers.vitrine.cta` : "Commencer" (au lieu de "S'inscrire gratuitement")
- Remplacer `pricing.tiers.vitrine.free` par `pricing.tiers.vitrine.price` (la valeur sera dynamique)
- Ajouter `pricing.tiers.vitrine.annualNote` (parité avec gestion/premium)

**Backend (`PricingCatalog.java`)** :
- Ajouter `putIfPresent(SubscriptionTier.VITRINE, SubscriptionBilling.MONTHLY, vitrineMonthly);`
- Ajouter `putIfPresent(SubscriptionTier.VITRINE, SubscriptionBilling.YEARLY, vitrineYearly);`
- Ajouter `@Value` injecté pour `stripe.price.vitrine-monthly` et `stripe.price.vitrine-yearly` (vides au boot, à remplir via Stripe Dashboard)
- Retirer la condition `if (tier == VITRINE) return Optional.empty()` dans `priceIdFor()` — VITRINE est désormais un tier Stripe normal
- **`SubscriptionService.startCheckout`** : retirer `throw new IllegalArgumentException("VITRINE tier does not require a Stripe subscription")`

### 3. Nouveau `ProSignupModalComponent`

**Localisation** : `frontend/src/app/shared/modals/pro-signup-modal/`

**Fichiers** :
- `pro-signup-modal.component.ts`
- `pro-signup-modal.component.html`
- `pro-signup-modal.component.scss`
- `pro-signup-modal.component.spec.ts`

**Data injectée** :
```typescript
export interface ProSignupModalData {
  tier: 'VITRINE' | 'GESTION' | 'PREMIUM';
  billing: 'MONTHLY' | 'YEARLY';
}

export interface ProSignupModalResult {
  authenticated: boolean;
}
```

**Champs (4 seulement)** :
- `name` (Validators.required)
- `email` (required + email)
- `password` (required + minLength 8)
- `consent` (requiredTrue) — checkbox CGU

**UI** :
- Header : "Démarrer avec **{{ tierName }}**" + sous-titre "{{ price }}€/mois · {{ billing }}"
- 4 champs en stack vertical
- Bouton primaire : "Créer mon compte"
- Lien sous le bouton : "Vous avez déjà un compte ? Se connecter" → ferme le modal et ouvre `AuthModalComponent` (login tab) — l'auth-modal existant gère le login client ; après login, on ré-appellera `/api/pro/upgrade` programmatiquement via le caller (pricing-page)
- Pas de bouton Google OAuth dans cette première version (YAGNI — peut s'ajouter plus tard)

**Logique submit** :
```typescript
this.authService.registerPro({
  name, email, password,
  tier: this.data.tier,
  billing: this.data.billing,
  // pas de salonName/adresse/siret — le tour les demandera
}).subscribe({
  next: () => this.dialogRef.close({ authenticated: true }),
  error: (err) => {
    if (err.status === 409) {
      this.errorKey.set('proSignup.modal.errors.emailAlreadyInUse');
      // Optionnel : proposer de switcher vers login
    } else {
      this.errorKey.set('proSignup.modal.errors.networkError');
    }
  },
});
```

Après `dialogRef.close({ authenticated: true })`, le caller (pricing-page.component) navigue vers `/pro/dashboard` (où le guided tour démarre).

### 4. Logique CTA dans pricing-page

```typescript
// pricing-page.component.ts
onStartTier(tier: 'VITRINE' | 'GESTION' | 'PREMIUM'): void {
  const billing = this.billing(); // MONTHLY | YEARLY
  const user = this.authService.currentUser();

  if (!user) {
    // Pas connecté → ouvre signup modal
    this.dialog.open(ProSignupModalComponent, {
      data: { tier, billing },
      width: '480px',
      ...bottomSheetConfig(...), // si pattern existant pour mobile
    }).afterClosed().subscribe(result => {
      if (result?.authenticated) {
        this.router.navigate(['/pro/dashboard']);
      }
    });
    return;
  }

  if (user.role === 'PRO') {
    // Déjà pro → direct dashboard
    this.router.navigate(['/pro/dashboard']);
    return;
  }

  // Connecté en tant que CLIENT → upgrade direct via endpoint
  this.subscriptionService.upgradeToPro({ tier, billing }).subscribe({
    next: () => this.router.navigate(['/pro/dashboard']),
    error: (err) => {
      if (err.status === 409) {
        // Tenant pro déjà existant pour cet user → redirect dashboard
        this.router.navigate(['/pro/dashboard']);
      } else {
        // Afficher snackbar erreur
        this.snackBar.open(...);
      }
    },
  });
}
```

Tous les `routerLink="/register/pro"` ou `routerLink="/register"` dans le footer/table de `pricing-page.component.html` sont remplacés par `(click)="onStartTier('VITRINE'/'GESTION'/'PREMIUM')"`.

### 5. Backend — `ProRegisterRequest` assoupli

```java
public record ProRegisterRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    @NotNull @AssertTrue Boolean consent,
    @NotNull SubscriptionTier tier,
    @NotNull SubscriptionBilling billing,
    // Optionnels — remplis par le guided tour après inscription
    String salonName,
    String phone,
    String addressStreet,
    String addressPostalCode,
    String addressCity,
    String siret
) {}
```

**`AuthController.registerProWithSalonInfo`** :
- Si `request.salonName()` est null/blank → `tenant.setName(request.name())` (le nom du pro sert de placeholder, le tour le remplacera dans `/pro/dashboard`)
- Sinon → comportement actuel
- Idem pour les autres champs business (gardent la valeur fournie ou null)
- `tenant.setSubscriptionTier(request.tier())` et `setSubscriptionBilling(request.billing())` (déjà présent en mémoire de l'ancien essai)

### 6. Nouvel endpoint `POST /api/pro/upgrade`

**Pour les users déjà authentifiés en tant que CLIENT qui veulent devenir PRO.**

**Localisation** : nouvelle méthode dans `AuthController` (ou nouveau `ProUpgradeController` dans `auth/`), au choix de l'implémenteur. Préférer une nouvelle méthode dans `AuthController` pour la cohésion.

**DTO** :
```java
public record ProUpgradeRequest(
    @NotNull SubscriptionTier tier,
    @NotNull SubscriptionBilling billing
) {}
```

**Logique** :
```java
@PostMapping("/upgrade-to-pro")
@Transactional
public ResponseEntity<AuthResponse> upgradeToPro(
    @Valid @RequestBody ProUpgradeRequest request,
    Authentication auth
) {
    User user = userRepository.findByEmail(auth.getName())
        .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED));

    // Si user a déjà un tenant pro → 409
    if (userRoleService.findUserTenantIds(user.getId()).stream()
            .anyMatch(tid -> tenantRepository.findById(tid)
                .map(t -> userRoleService.hasRoleInTenant(user, t, Role.PRO))
                .orElse(false))) {
        throw new ResponseStatusException(CONFLICT, "User already has a pro tenant");
    }

    // Provision tenant
    var tenant = tenantProvisioningService.provision(user);
    tenant.setName(user.getName()); // placeholder, tour remplacera
    tenant.setSubscriptionTier(request.tier());
    tenant.setSubscriptionBilling(request.billing());
    tenantRepository.save(tenant);

    // Initialize Stripe customer (idempotent)
    try {
        subscriptionService.initializeForTenant(user, tenant);
    } catch (Exception e) {
        logger.warn("Failed to init Stripe for upgraded user {}: {}", user.getEmail(), e.getMessage());
    }

    // Note: pas de welcome email — l'user est déjà connu
    return ResponseEntity.ok(buildAuthResponse(user, tenant.getId()));
}
```

**Endpoint** : `POST /api/auth/upgrade-to-pro` (sous le même `/api/auth` que les autres, car c'est une opération d'auth/rôle).

**Sécurité** : Spring Security exige déjà authentification — pas de guard supplémentaire à ajouter. Le rôle Pro est ajouté implicitement via la création du tenant (vérifier que `tenantProvisioningService.provision` ou `userRoleService` ajoute bien le rôle PRO à l'user).

**Frontend `SubscriptionService.upgradeToPro`** (nouvelle méthode) :
```typescript
upgradeToPro(payload: { tier: SubscriptionTier; billing: SubscriptionBilling }): Observable<User> {
  return this.http.post<{accessToken: string, user: User}>(
    `${this.apiBaseUrl}/api/auth/upgrade-to-pro`,
    payload
  ).pipe(
    tap(response => {
      this.authService.setToken(response.accessToken);
      // Recharger currentUser car le rôle a changé
    }),
    map(response => response.user),
  );
}
```

Note : `setToken` est privé dans `AuthService` — exposer une méthode publique `refreshAuthFromResponse(accessToken, user)` ou déplacer `upgradeToPro` dans `AuthService` directement (plus cohérent avec `registerPro`).

**Décision** : placer `upgradeToPro` dans `AuthService` (à côté de `registerPro` et `registerClient`), pas dans `SubscriptionService`. Symétrie + accès direct à `setToken`/`currentUser`.

### 7. Suppression `RegisterProComponent`

- Supprimer `frontend/src/app/pages/auth/register-pro/` (4 fichiers)
- Remplacer la route `/register/pro` par redirect vers `/pricing`
- Mettre à jour les CTAs qui pointaient vers `/register/pro` :
  - `frontend/src/app/pages/home/home.ts` (lignes 113, 160) → `/pricing`
  - `frontend/src/app/shared/layout/footer/footer.html` (5 occurrences) → vérifier au cas par cas (certains peuvent rester sur `/register` pour client)
  - `frontend/src/app/shared/layout/navigation/navigation-routes.ts:128` → `/pricing`

### 8. Gate paiement avant publish

(Identique à l'ancienne spec — repris ici intégralement.)

**Avant** : `[Publier] → store.publish()`
**Après** : `[Publier] → subscriptionService.getCurrentSubscription()` →
- Si `status === 'ACTIVE'` ou `'TRIALING'` → `store.publish()`
- Sinon → `router.navigate(['/pro/onboarding/payment'], { queryParams: { tier, billing } })`
- Si erreur réseau → redirect vers payment onboarding (fallback)

Modifications : `pro-dashboard.component.ts` (méthode `onPublish`), spec test.

## i18n

### Clés à ajouter

```json
"pricing": {
  "tiers": {
    "vitrine": {
      "price": "{{ amount }}€/mois",
      "annualNote": "facturé annuellement"
    }
  }
},
"proSignup": {
  "modal": {
    "title": "Démarrer avec {{ tier }}",
    "subtitle": "{{ price }}€/mois · {{ billing }}",
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
    "noCard": "Aucune carte bancaire demandée. Vous paierez au moment de publier votre salon."
  }
}
```

(et équivalent en anglais dans `en.json`).

### Clés à modifier

- `pricing.tiers.vitrine.cta` : "Commencer" → (déjà OK ou ajuster pour cohérence avec les autres tiers)
- Retirer `pricing.tiers.vitrine.free` (plus utilisé)

## Tests

### Frontend

- `pricing-page.component.spec.ts` (restauré + adapté) :
  - Affichage VITRINE avec prix 9.99 (monthly) / 7.99 (yearly)
  - Toggle billing met à jour les prix des 3 tiers
  - Click CTA tier ouvre `ProSignupModalComponent` si pas connecté
  - Click CTA tier appelle `subscriptionService.upgradeToPro` si connecté en client
  - Click CTA tier redirige `/pro/dashboard` si déjà pro

- `pro-signup-modal.component.spec.ts` (nouveau) :
  - Affichage tier/prix depuis `MAT_DIALOG_DATA`
  - Soumission appelle `authService.registerPro({...4 champs + tier + billing})`
  - 409 affiche message + reste ouvert
  - Validation : tous les 4 champs requis
  - Lien "déjà un compte" ferme le modal avec result `{ authenticated: false }` (le caller ouvrira AuthModal)

- `auth.service.spec.ts` :
  - Ajouter test `upgradeToPro` appelle bon endpoint et met à jour token + user
  - Adapter test `registerPro` pour nouvelle signature (`tier` + `billing` requis, autres champs optionnels)

- `pro-dashboard.component.spec.ts` :
  - Tests gate publish (3 tests : redirect VITRINE_FREE, publish ACTIVE, publish TRIALING) + 1 test fallback erreur

### Backend

- `AuthControllerTests` :
  - `registerPro` avec champs business null → tenant créé avec name = user.name
  - `upgradeToPro` user authentifié sans tenant pro → 200 + tenant créé
  - `upgradeToPro` user déjà pro → 409
  - `upgradeToPro` user non authentifié → 401
- `PricingCatalogTests` (si existe) : VITRINE retourne maintenant un priceId (si configuré), sinon Optional.empty()
- `SubscriptionServiceTests` : `startCheckout(VITRINE, ...)` ne throw plus

## Découpage en PRs

### PR1 — Backend assoupli + endpoint upgrade (~2h)
- `ProRegisterRequest` : `tier`+`billing` obligatoires, business fields optionnels
- `AuthController.registerProWithSalonInfo` : placeholder salonName=user.name si null
- Nouveau endpoint `POST /api/auth/upgrade-to-pro` + DTO `ProUpgradeRequest`
- `PricingCatalog` : VITRINE_MONTHLY/YEARLY ajoutés (Stripe Price IDs vides ok, config plus tard)
- `SubscriptionService.startCheckout` : retirer throw VITRINE
- Tests backend

### PR2 — Frontend restauration pricing + ProSignupModal (~4h)
- Restaurer `PricingPageComponent` (récup git show c3b02eb^)
- VITRINE 9.99/7.99 dans la table + i18n
- Créer `ProSignupModalComponent` (4 champs + tier rappel)
- `AuthService.registerPro` : nouvelle signature (tier+billing, autres optionnels)
- `AuthService.upgradeToPro` : nouvelle méthode
- Logique CTA `onStartTier` dans pricing-page : 3 branches (pas connecté / client / pro)
- Suppression `RegisterProComponent` + dossier
- Route `/register/pro` redirect vers `/pricing`
- CTAs home + footer : pointer vers `/pricing`
- Tests frontend (pricing, modal, auth.service)

### PR3 — Gate paiement avant publish (~2h)
- `pro-dashboard.component.ts` : intercepter clic Publier, check subscription, redirect onboarding paiement si pas active
- Tests dashboard

Total : ~8h sur 3 PRs livrables séparément (PR1 et PR3 indépendantes, PR2 dépend de PR1 pour le DTO).

## Décisions actées

1. **Pattern SaaS** : auth-modal light → onboarding (guided tour) demande le reste
2. **VITRINE payant** 9.99€/mois mensuel, 7.99€/mois annuel — Stripe Price IDs à configurer manuellement plus tard
3. **`salonName` placeholder = user.name** quand non fourni à l'inscription (le tour remplacera)
4. **Skip modal si déjà connecté** : endpoint `/api/auth/upgrade-to-pro` pour client → pro
5. **Pas de Google OAuth dans ProSignupModal** (YAGNI, ajout possible plus tard)
6. **3 PRs séparées** : backend / frontend pricing+modal / gate publish
7. **Guided tour NE PAS toucher** — il reste inchangé
8. **AuthModalComponent NE PAS toucher** — utilisé pour clients, indépendant
9. **`upgradeToPro` dans `AuthService`** (pas SubscriptionService) — cohérence avec `registerPro`/`registerClient`

## Risques & mitigations

| Risque | Mitigation |
|---|---|
| User clique CTA VITRINE alors que Stripe Price IDs VITRINE pas configurés | Soit afficher placeholder de prix dans UI mais bloquer publication tant que Stripe est OK (PricingCatalog retourne empty → erreur claire au publish), soit retarder le go-live VITRINE |
| Liens externes `/register/pro` cassés | Redirect 301 vers `/pricing` |
| Tests existants sur RegisterProComponent | Tous supprimés avec le composant ; pas de migration nécessaire |
| `upgradeToPro` : la session JWT ne se rafraîchit pas correctement (les rôles changent) | Retourner un nouveau accessToken + user dans la réponse, AuthService remplace le token. Vérifier que l'AuthGuard re-évalue le rôle au prochain refresh. |
| Backend assoupli (`salonName` optionnel) casse une logique existante | Grep des consommateurs de `tenant.getName()` qui supposent non-null, ajuster si besoin |
| User upgrade-to-pro alors qu'il a déjà un tenant pro (race condition) | 409 retourné, frontend redirige vers `/pro/dashboard` sans erreur visible |
