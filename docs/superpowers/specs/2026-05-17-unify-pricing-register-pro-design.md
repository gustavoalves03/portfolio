# Unification `/pricing` + `/register/pro` — Inscription pro sans CB

**Date** : 2026-05-17
**Statut** : Spec validée, à implémenter
**Contexte mémoire** : remplace partiellement [project_pending_payments] (le flow inscription n'attend plus Stripe shippé)

---

## Objectif

Fusionner les pages publiques `/pricing` et `/register/pro` en une seule page d'inscription pro, et déporter la demande de carte bancaire au moment où le pro clique sur **Publier** (et non plus à l'inscription).

Suppression complète du tier gratuit `VITRINE`. Plus de différenciation entre "compte vitrine" et "compte gestion" à l'inscription : tous les pros démarrent en `DRAFT`, choisissent leur tier payant (GESTION ou PREMIUM) à l'inscription pour la mémoire de préférence, puis paient au moment de publier.

## Motivation

- Friction inutile : aujourd'hui le pro doit choisir un plan + valider la grille tarifaire avant même d'avoir vu l'app.
- Le tier VITRINE gratuit crée de la dette (logique conditionnelle, UI à maintenir) pour zéro valeur business.
- Stripe Connect/Subscription est déjà branché sur `/pro/onboarding/payment` mais inutilisé dans le flow d'inscription — on capitalise dessus en le réservant à la publication.
- Cohérence produit : "tester gratuitement" = créer compte + salon en DRAFT sans CB. "Aller en prod" = CB.

## Périmètre

### Inclus

1. Suppression de la route `/pricing` et de `PricingPageComponent`
2. Refonte de `/register/pro` : intégration de la grille tarifaire en tête de page + formulaire compte/salon
3. Suppression du tier `VITRINE` côté frontend (enum, UI, traductions)
4. Modification du flow "Publier" sur le dashboard pro : redirection vers `/pro/onboarding/payment` si pas de subscription active
5. Mise à jour de tous les CTA pointant vers `/pricing` ou `/register/pro` (home, footer, navigation)
6. Redirect `/pricing` → `/register/pro` pour préserver SEO/liens externes
7. Backend : `RegisterProRequest` accepte `tier` (`GESTION` | `PREMIUM`) + `billing` (`MONTHLY` | `YEARLY`), ne déclenche aucun appel Stripe à l'inscription

### Exclus

- Migration de données pros existants (aucun pro en prod, confirmé)
- Refonte du composant `/pro/onboarding/payment` (déjà fonctionnel, juste à brancher)
- Modification du backend Stripe / `SubscriptionService.createSubscription` (déjà OK)
- Refonte visuelle de la grille tarifaire elle-même (on réutilise le markup existant adapté au contexte register)
- Gestion des coupons / essais gratuits (hors scope)

## Architecture

### Routes

**Avant :**
```
/pricing         → PricingPageComponent (grille comparative)
/register/pro    → RegisterProComponent (3 steps: pricing → account → business)
```

**Après :**
```
/pricing         → redirect 301 vers /register/pro
/register/pro    → RegisterProComponent (1 page: grille tarifs en tête + formulaire compte+salon)
```

La route `/pro/onboarding/payment` reste inchangée (gardée comme étape post-publish-attempt).

### Composant `/register/pro` — Nouveau layout

Une seule vue avec deux blocs verticaux :

**Bloc 1 — Sélection tier (en haut de page)**
- Toggle billing MONTHLY/YEARLY (réutilise UI existante)
- 2 cards : GESTION (mise en avant) et PREMIUM
- Sélection visuelle (radio implicite) → met à jour signal `selectedTier`
- Pas de bouton CTA dans ce bloc (le CTA est le bouton "Créer mon compte" en bas)
- Réutilise les sources de prix de `pricing-page.component.ts` (constantes ou service)

**Bloc 2 — Formulaire compte + salon**
- Champs : `name`, `email`, `password`, `confirmPassword`, `salonName`, `phone`, `addressStreet`, `addressPostalCode`, `addressCity`, `siret`, `consent`
- Un seul bouton "Créer mon compte" en bas
- Mention sous le bouton : "Aucune carte bancaire demandée. Vous paierez au moment de publier votre salon."
- Validation : même règles qu'aujourd'hui (email valide, password ≥8, passwordsMatch, consent obligatoire, salonName non vide)

**Plus de stepper interne** (`step: 'pricing' | 'account' | 'business'` supprimé). Tout est sur une page.

### Suppression VITRINE

**Frontend :**
- `SubscriptionTier` : `'VITRINE' | 'GESTION' | 'PREMIUM'` → `'GESTION' | 'PREMIUM'`
- Supprimer toutes les références UI à VITRINE (cards, accordion, traductions `pricing.tiers.vitrine.*`)
- Adapter les composants qui lisent `tier` (dashboard, settings) — au runtime, aucun compte VITRINE n'existe donc pas de branche à conserver

**Backend :**
- `SubscriptionTier` enum : retirer `VITRINE` (sauf si d'autres entités le référencent — à vérifier au moment de l'implem)
- `RegisterProRequest` accepte uniquement `GESTION` ou `PREMIUM`
- Pas de migration BDD (zéro données en prod)

### Flow Publier (dashboard pro)

**Avant :**
```
[Publier] → store.publish() → POST /tenants/me/publish → DRAFT → ACTIVE
```

**Après :**
```
[Publier] → check subscription status
  ├─ ACTIVE  → store.publish() (inchangé)
  └─ absent  → router.navigate(['/pro/onboarding/payment'],
                  { queryParams: { tier, billing, returnTo: 'publish' } })
                ↓
              [paiement Stripe SetupIntent + createSubscription]
                ↓
              redirect /pro/dashboard + auto-trigger publish (ou message "abonnement actif, cliquez Publier")
```

**Décision design** : après paiement réussi, on retourne au dashboard SANS auto-publier. Le pro doit recliquer "Publier". Raison : laisser un dernier contrôle, éviter double-trigger, et garder le composant payment-onboarding ignorant du contexte d'appel. Le pro voit alors "abonnement actif" et son bouton Publier fonctionne.

Le `tier` et `billing` à passer en query params viennent du tenant lui-même (champs persistés à l'inscription).

### Backend — `RegisterProRequest`

DTO actuel attend `plan: string` (libre). Modifier pour :
```java
public record RegisterProRequest(
    @NotBlank String name,
    @Email String email,
    @Size(min=8) String password,
    @NotBlank String salonName,
    String phone,
    String addressStreet,
    String addressPostalCode,
    String addressCity,
    String siret,
    @NotNull SubscriptionTier tier,        // GESTION | PREMIUM
    @NotNull SubscriptionBilling billing,  // MONTHLY | YEARLY
    boolean consent
) {}
```

Le service `AuthService.registerPro` :
- Crée le `User` (rôle PRO)
- Crée le `Tenant` en statut `DRAFT` avec `tier` et `billing` persistés sur le tenant (ou table dédiée si déjà existante)
- **Ne crée aucune subscription Stripe**
- Retourne le `{accessToken, user}` standard

## Migration des CTA

| Fichier | Action |
|---|---|
| `app.routes.ts` | Remplacer route `/pricing` par redirect vers `/register/pro` |
| `frontend/src/app/pages/home/home.ts:113` | `router.navigate(['/register/pro'])` — déjà bon, vérifier |
| `frontend/src/app/shared/layout/footer/footer.html:49` | Déjà `/register/pro` — vérifier |
| `frontend/src/app/shared/layout/navigation/navigation-routes.ts:128` | Déjà `/register/pro` — vérifier |
| `pricing-page.component.html` | Fichier supprimé |
| Tout autre lien `/pricing` | Grep + remplacer |

## i18n

Clés à supprimer : `pricing.*` (toute la page), `pricing.tiers.vitrine.*` partout.
Clés à ajouter dans `register.pro.*` :
- `register.pro.tierSection.title`
- `register.pro.tierSection.subtitle`
- `register.pro.billing.monthly` / `yearly`
- `register.pro.noCard.notice` ("Aucune carte bancaire demandée. Vous paierez au moment de publier.")
- `register.pro.submit.cta`

Mettre à jour `fr.json` ET `en.json` simultanément.

## Tests

### Frontend
- `register-pro.component.spec.ts` : réécrire pour le nouveau layout sans steps. Tests :
  - Affichage des 2 cards tier
  - Toggle billing change les prix affichés
  - Sélection tier met à jour `selectedTier`
  - Validation form : tous les champs requis
  - Submit appelle `authService.registerPro({...form, tier, billing})`
  - Succès → redirige `/pro/dashboard`
  - Erreur 409 → affiche message email conflict
- `pricing-page.component.spec.ts` : **supprimé**
- `pro-dashboard.component.spec.ts` : ajouter test "publier sans subscription redirige vers /pro/onboarding/payment avec tier+billing en queryParams"
- Tests E2E (si existent) : adapter le parcours register pro

### Backend
- `AuthControllerTests` / `AuthServiceTests` : adapter pour le nouveau DTO (tier + billing typés, plus de `plan` libre)
- Vérifier qu'aucun appel Stripe n'est déclenché à l'inscription

## Risques & mitigations

| Risque | Mitigation |
|---|---|
| Liens externes vers `/pricing` cassés | Redirect 301 vers `/register/pro` |
| Pros déjà inscrits sans tier persisté sur tenant | Aucun pro en prod (confirmé) — pas de migration |
| Backend lie encore `SubscriptionTier.VITRINE` quelque part | Grep côté Java avant suppression de l'enum value ; si référencé ailleurs, garder en `@Deprecated` au lieu de supprimer |
| Page `/register/pro` devient longue | Acceptable — c'est un funnel de conversion, le scroll est attendu ; CTA principal reste visible en sticky si nécessaire (à voir en implem) |
| Pro clique Publier, paie, et oublie de recliquer | Message clair sur retour dashboard : "Abonnement actif. Cliquez Publier pour mettre en ligne." + bouton mis en évidence |

## Découpage en PRs

**PR1 — Frontend : unification page (~3h)**
- Refonte `RegisterProComponent` (1 page, plus de steps)
- Suppression `PricingPageComponent` + dossier `features/subscription/pricing/`
- Suppression VITRINE côté TypeScript
- Redirect `/pricing` → `/register/pro`
- Update i18n (fr + en)
- Tests `register-pro.component.spec.ts` réécrits
- Vérification grep CTA

**PR2 — Backend : DTO + suppression VITRINE (~1h30)**
- `RegisterProRequest` typé (tier + billing enums)
- Retrait `VITRINE` de l'enum si non référencé ailleurs
- Tests backend adaptés

**PR3 — Flow publier avec gate paiement (~2h)**
- `pro-dashboard.component.ts` : intercepter clic Publier, check subscription, rediriger vers onboarding paiement si manquante
- Message "abonnement actif" au retour
- Tests dashboard adaptés

Total estimé : ~6h30 sur 3 PRs livrables séparément.

## Décisions actées

1. **Une seule page** `/register/pro` avec grille tarifs + formulaire (pas de stepper)
2. **VITRINE supprimé** complètement (frontend + backend si possible)
3. **Tier + billing persistés** sur le tenant à l'inscription pour réutilisation au moment de publier
4. **Pas d'auto-publish** après paiement : le pro reclique manuellement Publier
5. **Redirect `/pricing` → `/register/pro`** pour préserver SEO
6. **Pas de migration BDD** (zéro pros en prod)
