# Pro Guided Tour — Design

**Date** : 2026-05-08
**Statut** : Draft, en attente de validation user
**Remplace** : la spec wizard `2026-05-07-pro-onboarding-wizard-design.md` (pivot annulé via revert + cherry-pick le 2026-05-08).

## Contexte

Le tour précédent (wizard à `/pro/onboarding`) recréait des écrans dédiés et des formulaires en double des pages existantes. Le pro a explicité son intention : il veut être **guidé sur les vraies pages** de l'application (`/pro/salon`, `/pro/cares`, `/pro/planning`), avec leurs vrais champs, pas un parcours parallèle.

On garde du travail précédent ce qui sert ici :
- `TenantReadinessResponse` enrichi (`hasContact`, `hasLogo`, fallback `CategoryRepository.count()`)
- `PATCH /api/pro/tenant` pour les sauvegardes partielles
- Fix de provisioning `name=null` à la création (Flyway V7)
- `DashboardStore.publishMissing` + `PublishMissingDialogComponent` qui pointe vers les vraies pages

## Décision

Construire un **tour guidé in-context** sous forme d'un overlay flottant (CDK Overlay) qui met un champ en spotlight sur la page concernée. L'utilisateur reste sur la page existante, interagit avec le vrai champ, et le tour avance automatiquement quand `readiness` confirme que la condition est remplie.

Pas de nouvelle route, pas de nouveau formulaire. Juste un service Angular + un composant overlay + des annotations `data-tour-step` sur les pages cibles.

## Périmètre V1

**Inclus :**
- 6 étapes guidées : `name`, `hasContact`, `hasLogo`, `hasCategory`, `hasActiveCare`, `hasOpeningHours`.
- Spotlight overlay sombre + halo rosé autour du champ ciblé + bulle ancrée (style A des mockups).
- Avancement automatique piloté par `readiness` après chaque sauvegarde.
- Navigation automatique entre `/pro/salon`, `/pro/cares`, `/pro/planning` avec court fade ("✓ étape suivante : ...").
- Déclenchement manuel : depuis le bandeau d'onboarding existant (clic sur une étape lance le tour).
- Fermeture libre via "Plus tard" — le bandeau reste, le pro relance quand il veut.
- Sélection de la cible via attribut HTML `data-tour-step="<key>"` posé sur les vrais champs.
- Tour transversal aux pages : un seul `<app-tour-overlay />` monté dans `ProShellComponent`.

**Exclu V1 :**
- Mobile (< 1024px) — le bandeau mobile sheet existant reste seul, le tour ne s'active pas.
- Persistance "j'ai déjà fait le tour" — pas nécessaire vu le déclenchement manuel.
- Tour à l'intérieur des modales (ex: pas de spotlight sur le formulaire d'ajout de soin).
- Animations sophistiquées (fade CSS suffit).
- Tour pour le bouton "Publier" final — quand `canPublish === true`, le bandeau affiche déjà le bouton.

## Architecture

### Composants nouveaux

| Path | Rôle |
|------|------|
| `src/app/features/onboarding/tour/tour.service.ts` | Source de vérité du tour. Signaux `active`, `currentStep`, `inTransition`, `progress`. Effect qui avance auto sur changement de readiness. |
| `src/app/features/onboarding/tour/tour-steps.ts` | Catalogue immuable `TOUR_STEPS: readonly TourStep[]`. Map readiness flag → route + tourStep + i18n keys. |
| `src/app/features/onboarding/tour/tour-step.model.ts` | Types `TourStep`, `TourState`. |
| `src/app/shared/uis/tour-overlay/tour-overlay.component.{ts,html,scss,spec.ts}` | Composant unique monté dans `ProShellComponent`. Cherche `[data-tour-step="<key>"]`, mesure son rect, affiche overlay + halo. |
| `src/app/shared/uis/tour-overlay/tour-bubble.component.{ts,html,scss,spec.ts}` | La bulle (titre, desc, compteur, bouton "Plus tard"). Computed `bubblePosition` qui choisit au-dessus ou en-dessous selon le viewport. |

### Composants modifiés

| Path | Modification |
|------|--------------|
| `src/app/pages/pro/pro-shell.component.html` | Ajouter `<app-tour-overlay />` au-dessus de `<router-outlet />`. |
| `src/app/shared/features/onboarding-indicator/onboarding-indicator.component.ts` | Injecter `TourService`. Au clic sur une étape, si `TOUR_STEPS` la couvre → `tour.start(stepKey)` au lieu de naviguer. |
| `src/app/features/salon-profile/salon-profile.component.html` | Poser `data-tour-step="name"`, `data-tour-step="contact"`, `data-tour-step="logo"` sur les éléments correspondants. |
| `src/app/pages/pro/pro-cares.component.html` (ou équivalent) | Poser `data-tour-step="categories"` et `data-tour-step="add-care"` sur les boutons d'ajout. |
| `src/app/pages/pro/pro-planning.component.html` | Poser `data-tour-step="opening-hours"` sur le bloc des horaires. |
| `frontend/public/i18n/{fr,en}.json` | Ajouter le bloc `pro.tour.*`. |

### Données

```typescript
// tour-step.model.ts
export interface TourStep {
  readonly key: WizardStepKey;        // 'name' | 'contact' | 'logo' | ...
  readonly readinessFlag: keyof TenantReadiness;
  readonly route: string;              // '/pro/salon', '/pro/cares', '/pro/planning'
  readonly tourStep: string;           // valeur attendue de [data-tour-step]
  readonly titleKey: string;           // i18n key
  readonly descKey: string;
}
```

`WizardStepKey` est conservé du travail précédent (déjà supprimé après revert ; on le ressort sous une forme allégée). Au lieu de réimporter le fichier wizard, on déclare un type local au feature `tour` :
```typescript
export type WizardStepKey = 'name' | 'contact' | 'logo' | 'categories' | 'cares' | 'openingHours';
```

### Catalogue

```typescript
export const TOUR_STEPS: readonly TourStep[] = [
  { key: 'name',         readinessFlag: 'name',           route: '/pro/salon',    tourStep: 'name',         titleKey: 'pro.tour.steps.name.title',         descKey: 'pro.tour.steps.name.desc' },
  { key: 'contact',      readinessFlag: 'hasContact',     route: '/pro/salon',    tourStep: 'contact',      titleKey: 'pro.tour.steps.contact.title',      descKey: 'pro.tour.steps.contact.desc' },
  { key: 'logo',         readinessFlag: 'hasLogo',        route: '/pro/salon',    tourStep: 'logo',         titleKey: 'pro.tour.steps.logo.title',         descKey: 'pro.tour.steps.logo.desc' },
  { key: 'categories',   readinessFlag: 'hasCategory',    route: '/pro/cares',    tourStep: 'categories',   titleKey: 'pro.tour.steps.categories.title',   descKey: 'pro.tour.steps.categories.desc' },
  { key: 'cares',        readinessFlag: 'hasActiveCare',  route: '/pro/cares',    tourStep: 'add-care',     titleKey: 'pro.tour.steps.cares.title',        descKey: 'pro.tour.steps.cares.desc' },
  { key: 'openingHours', readinessFlag: 'hasOpeningHours',route: '/pro/planning', tourStep: 'opening-hours',titleKey: 'pro.tour.steps.openingHours.title', descKey: 'pro.tour.steps.openingHours.desc' },
] as const;
```

## Data flow

```
Pro clique "Logo" dans le bandeau onboarding
  → OnboardingIndicator.onStepClick('logo')
  → TourService.start('logo')
      .active = true
      .navigateTo(logoStep) — router.navigateByUrl('/pro/salon')
      .currentStep = logoStep
TourOverlayComponent (déjà monté dans pro-shell)
  → effect détecte active=true et currentStep changed
  → bindToTarget('logo')
      querySelector('[data-tour-step="logo"]')
      mesure rect, monte overlay CDK + bulle
      attache ResizeObserver + scroll listener
Pro tape, sauve dans le vrai champ logo
  → SalonProfileComponent appelle son endpoint existant
  → DashboardStore.loadReadiness() — déjà en place
  → readiness().hasLogo === true
TourService effect (réagit à readiness changes)
  → currentStep.readinessFlag est 'hasLogo' et r.hasLogo === true
  → advance()
      inTransition.set(true)
      bulle affiche "✓ Logo ajouté → étape suivante"
      après 1500ms : firstMissing → 'hasCategory'
      navigate /pro/cares + currentStep = categoriesStep
Boucle jusqu'à canPublish === true
  → firstMissing retourne null
  → tour.stop()
```

### Cas particuliers

- **Cible introuvable** (timing async) : retry exponentiel ×3 à 500 ms. Au 4ᵉ échec, `console.warn` + `tour.stop()`.
- **Pro navigue manuellement ailleurs pendant tour actif** : listen `Router.events.NavigationEnd`. Si URL ≠ `currentStep.route` (et pas en `inTransition`), `tour.stop()`.
- **`readiness === null`** : `tour.start()` no-op.
- **`status === 'ACTIVE' && canPublish`** : `tour.start()` no-op.
- **MatDialog ouverte par-dessus** : `tour-overlay` z-index 999, `MatDialog` 1000. La modale masque visuellement le tour ; à sa fermeture le tour redevient visible et avance grâce à readiness.
- **Resize / scroll** : `ResizeObserver` sur la cible + `scroll` listener recalculent le rect en temps réel, la bulle se repositionne.
- **Cible cachée derrière un toggle** (`*ngIf`, `@if`) : retry 3× couvre le cas du composant pas encore monté.
- **Reload page pendant tour** : pas de persistance — `tour.active() === false` au reload.

## Visual design (style A — overlay sombre + halo rosé)

- Overlay : `position: fixed; inset: 0; background: rgba(43, 31, 37, 0.55); backdrop-filter: blur(1px); z-index: 800`.
- Le rect de la cible est "découpé" via `clip-path` sur l'overlay : `pointer-events: auto` partout sauf sur la cible (qui reste interactive). Reste de la page : non interactif.
- Halo : `position: fixed; border: 3px solid var(--pf-rose); border-radius: 12px; box-shadow: 0 0 0 2px rgba(255,255,255,0.3); pointer-events: none; z-index: 801`. Animation pulse 2 s.

Z-index : overlay (800) < halo (801) < bulle (802) < MatDialog backdrop (1000) < MatDialog container (1000+). Le tour reste sous toute modale Material — quand un dialog s'ouvre, il masque le tour visuellement, ce qui est le comportement voulu.
- Bulle : `background: white; border-radius: 14px; padding: 14px 16px; box-shadow: 0 12px 32px rgba(0,0,0,0.18); max-width: 320px`. Position calculée : sous la cible si elle tient, sinon au-dessus.
- Bulle pendant transition : remplace le contenu par un check icon + texte "✓ Sauvegardé — étape suivante" (1,5 s).
- Boutons : "Plus tard" (texte simple, gris), pas de "Suivant" (avancement piloté par readiness).

## i18n

Bloc à ajouter dans FR + EN :

```jsonc
"pro": {
  "tour": {
    "steps": {
      "name":         { "title": "...", "desc": "..." },
      "contact":      { "title": "...", "desc": "..." },
      "logo":         { "title": "...", "desc": "..." },
      "categories":   { "title": "...", "desc": "..." },
      "cares":        { "title": "...", "desc": "..." },
      "openingHours": { "title": "...", "desc": "..." }
    },
    "transition": { "success": "..." },
    "actions":    { "later": "..." }
  }
}
```

Le contenu textuel sera défini lors de l'implémentation. Cible : titre 4-6 mots, description 1-2 phrases ("Cliquez ici pour saisir votre nom de salon. Visible sur votre page publique.").

## Tests

**`TourService.spec.ts` — 6 tests :**
- `start()` no-op si `readiness === null`.
- `start()` no-op si tenant `ACTIVE && canPublish`.
- `start('logo')` set `currentStep` à l'étape logo + navigate `/pro/salon`.
- `start()` sans key part du `firstMissing(readiness)`.
- L'effect d'avancement passe au step suivant 1500 ms après que `readiness[currentStep.readinessFlag]` devient `true`.
- `stop()` reset `active`, `currentStep`, `inTransition`.

**`TourOverlayComponent.spec.ts` — 4 tests :**
- Ne monte rien quand `tour.active() === false`.
- Bind `data-tour-step="name"` quand `currentStep.tourStep === 'name'`.
- Retry ×3 puis ferme le tour si cible introuvable (`fakeAsync + tick(500*3)`).
- Cleanup les observers à `OnDestroy`.

**`TourBubbleComponent.spec.ts` — 2 tests :**
- Affiche "✓ étape suivante" quand `inTransition === true`.
- Emit `(close)` au clic sur "Plus tard".

**`OnboardingIndicatorComponent.spec.ts` — 1 test ajouté :**
- Clic sur étape couverte par `TOUR_STEPS` → `tour.start(stepKey)` (mock du service), pas de `router.navigate` direct.

Pas de test E2E — la couverture unitaire est suffisante pour V1. Smoke manuel sur `npm start` à la fin.

## Décomposition en PRs

PR unique faisable en 1 jour. Si on découpe :

1. **PR1 — Core service + catalogue + tests** (`tour.service.ts`, `tour-steps.ts`, `tour-step.model.ts`, spec). Pas d'UI encore. Mergeable seul, aucune régression visible.
2. **PR2 — Overlay component + bubble + i18n** (`tour-overlay`, `tour-bubble`, i18n keys). Ajout de `<app-tour-overlay />` dans pro-shell, pas encore activé.
3. **PR3 — Pose des `data-tour-step` + intégration onboarding-indicator**. Active le tour de bout en bout.

## Décisions consignées

- **Style A** validé (overlay sombre + halo rosé + bulle ancrée).
- **Niveau moyen** : spotlight + bulle persistante.
- **Manuel** : déclenchement depuis le bandeau (jamais auto).
- **Auto-progression** : avancement piloté par `readiness`, pas par bouton "Suivant".
- **Ordre dynamique** : suit `firstMissing(readiness)`.
- **Inter-pages** : navigation auto avec court fade.
- **Soins** : pointe juste le bouton "+ Ajouter", la modale existante prend le relai.
- **Fermeture** : libre, garde le bandeau visible.
- **Cible** : attribut `data-tour-step` sur les vrais champs.
- **Mobile** : exclu V1, code extensible.
- **Approche technique** : custom Angular CDK Overlay, zéro dépendance externe.
