# Onboarding polish (Jalon 1.5) — design

**Date :** 2026-05-06
**Statut :** Design validé en brainstorming, en attente de plan d'implémentation
**Précède :** Jalon 2 (preview vitrine). Doit shipper avant.
**Branche cible :** `feat/onboarding-polish` (à créer depuis `main` après merge du Jalon 1).

## Contexte

Le Jalon 1 a livré l'indicateur d'étape global (pill mobile + stepper PC + bottom-sheet) et le wrapper `ProShellComponent`. À l'usage, cinq problèmes UX sont apparus :

1. Quand le pro complète une étape puis revient au dashboard via le sidenav, la checklist reste figée — l'étape ne passe pas en vert. Le store ne re-fetch pas `readiness` parce qu'il a été élevé au `ProShellComponent` (Jalon 1) et survit aux navigations.
2. Le sidenav est librement navigable vers des pages qui n'ont pas de sens en `DRAFT` (posts vides, employés sans soins). Le pro se perd.
3. L'indicateur d'étape ne donne aucune aide — il dit "Renseigne le nom de ton salon" sans expliquer pourquoi ni ce qui suit.
4. Quand le pro arrive sur la page cible (ex : `/pro/salon`), rien ne le guide vers le bon champ.
5. Les formulaires d'inscription et de profil :
   - n'ont pas de confirmation de mot de passe (problème de sécurité courant)
   - ont un bouton submit désactivé sans explication quand des champs sont invalides

## Objectif

Polir l'expérience pro pendant l'onboarding pour que :
- Les étapes complétées se reflètent immédiatement au retour dashboard.
- Le sidenav guide plutôt que d'être un piège : pages non-pertinentes en DRAFT visibles mais désactivées avec une raison.
- Chaque étape de l'indicateur a une explication accessible (tooltip PC, ligne de description sur mobile).
- L'arrivée sur la page cible attire l'œil sur le champ à remplir.
- Les formulaires sont sécurisés (confirmation password) et explicites (mat-error visibles + hint sous le bouton désactivé).

## Critères de succès

**Qualitatif :**
- Le pro qui complète une étape voit le statut bascule en vert sans recharger la page.
- Le pro comprend pourquoi un menu est grisé (par le tooltip "Disponible après publication").
- Le pro comprend ce que chaque étape demande (par la bulle ou la description).
- Le pro qui arrive sur `/pro/salon` voit immédiatement où cliquer (focus + border rose pulsante).
- Le pro qui ne peut pas soumettre comprend pourquoi (champ rouge + résumé sous le bouton).

**Mesurable :**
- 100 % des tests existants restent verts.
- Nouveaux tests pour chaque sous-jalon.
- Pas de régression Lighthouse a11y (≥ 95 sur les pages touchées).

## Hors scope

- Onboarding tour interactif type Intro.js (pas de coachmark popover).
- Modification du flow de réservation client.
- Refonte visuelle des modales (spec dédiée à venir).
- Bulles d'aide hors indicateur (sur d'autres composants pro non-onboarding).

## Architecture & jalons

| PR | Périmètre | Pourquoi en premier ? |
|----|-----------|------------------------|
| 1.5.A | Refresh checklist au retour dashboard | Bug le plus critique — débloque la perception de progression. ~10 lignes. |
| 1.5.B | Sidenav avec gating DRAFT | Bloque les chemins de perdition pendant que le reste est en cours. |
| 1.5.C | Bulles indicateur + focus visuel page cible | Améliore le guidage existant. |
| 1.5.D | Confirmation password + feedback formulaires | Polish formulaires, indépendant du reste. |

Les 4 PRs sont **indépendants** (aucune n'ouvre une dépendance dans une autre) — peuvent être livrées dans n'importe quel ordre, mais l'ordre proposé minimise les frictions de revue.

---

## PR 1.5.A — Refresh checklist au retour dashboard

### Comportement utilisateur

Le pro complète "Soins" → revient au dashboard via le sidenav → l'étape "Soins" passe immédiatement en vert dans la checklist du dashboard ET dans l'indicateur d'étape global.

### Cause racine

`DashboardStore.loadReadiness()` est déclenché une seule fois dans `withHooks({ onInit })` quand le store est instancié. Avec le Jalon 1, le store a été élevé au `ProShellComponent` — donc il survit aux navigations entre `/pro/*` mais ne se ré-initialise pas. `readiness()` reste figée.

### Solution

Ajouter un `effect` dans `ProShellComponent` qui re-déclenche `loadReadiness()` à chaque navigation **vers le dashboard** spécifiquement. Cela évite de marteler le backend à chaque clic de menu : seul le retour au dashboard demande une vue fraîche.

```typescript
@Component({
  selector: 'app-pro-shell',
  // ...
  providers: [DashboardStore],
})
export class ProShellComponent {
  private readonly store = inject(DashboardStore);
  private readonly router = inject(Router);

  constructor() {
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        filter((e) => e.urlAfterRedirects.startsWith('/pro/dashboard')),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.store.loadReadiness());
  }
}
```

### Alternative considérée

Re-fetch sur **toute** navigation vers `/pro/*` : rejetée — multiplie les requêtes inutiles. Le pro voit le dashboard moins souvent que les pages de configuration ; c'est le bon endroit pour synchroniser.

### Tests

- `pro-shell.component.spec.ts` : nouveau test. Simule deux `NavigationEnd` (depuis `/pro/cares` puis vers `/pro/dashboard`), vérifie que `loadReadiness` est appelé une fois.

### Périmètre

- Modifié : `frontend/src/app/pages/pro/pro-shell.component.ts` (~10 lignes).
- Modifié : `frontend/src/app/pages/pro/pro-shell.component.spec.ts` (+1 test).

---

## PR 1.5.B — Sidenav avec gating DRAFT

### Comportement utilisateur

Quand `tenant.status === 'DRAFT'` :
- **Pages libres** (cliquables, normales) : `/pro/dashboard`, `/pro/salon`, `/pro/cares`, `/pro/planning`, `/pro/settings`.
- **Pages verrouillées** (cliquables mais grisées avec tooltip) : `/pro/posts`, `/pro/employees`.
  - Au survol PC : tooltip Material *"Disponible après publication"*.
  - Au tap mobile : snackbar *"Disponible après publication"*.
  - Si le pro clique : on bloque la navigation (`event.preventDefault()`) + on affiche le message.

Quand `tenant.status === 'ACTIVE'` : tout redevient libre, plus de tooltip.

### Architecture

#### 1. Étendre `NavigationRoute`

`frontend/src/app/shared/layout/navigation/navigation-routes.ts` — ajout d'un attribut optionnel :

```typescript
export interface NavigationRoute {
  // existing fields…
  /**
   * If true, this pro route is locked while the salon is in DRAFT status.
   * Renders a disabled link with a "available after publication" tooltip.
   */
  lockedUntilPublished?: boolean;
}
```

Application sur les routes concernées :

```typescript
{ path: '/pro/posts', icon: 'photo_library', /* ... */ lockedUntilPublished: true },
{ path: '/pro/employees', icon: 'groups', /* ... */ lockedUntilPublished: true },
```

#### 2. Service `TenantStatusService`

`frontend/src/app/core/tenant/tenant-status.service.ts` — service léger fournissant `Signal<TenantStatus | null>` accessible depuis le sidenav (qui est dans le header global, hors `ProShellComponent`).

```typescript
@Injectable({ providedIn: 'root' })
export class TenantStatusService {
  readonly status = signal<'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'DELETED' | null>(null);

  set(value: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'DELETED' | null): void {
    this.status.set(value);
  }

  reset(): void {
    this.status.set(null);
  }
}
```

#### 3. Synchronisation avec `DashboardStore`

Dans `ProShellComponent`, on bind le service au store via un `effect` — pas de second appel HTTP :

```typescript
constructor() {
  // existing router effect from PR 1.5.A
  effect(() => {
    const status = this.store.readiness()?.status ?? null;
    this.tenantStatusService.set(status);
  });
}
```

Au logout (`AuthService` ou `auth.logout()`), `tenantStatusService.reset()` est appelé.

#### 4. Logique de verrouillage dans `SidenavMenu`

Le composant injecte `TenantStatusService` et calcule la classe `is-locked` par route. Le template :

```html
@for (route of routes(); track route.path) {
  @let locked = route.lockedUntilPublished && tenantStatus.status() === 'DRAFT';
  <a
    mat-list-item
    [routerLink]="locked ? null : route.path"
    routerLinkActive="active-link"
    [routerLinkActiveOptions]="{exact: route.path === '/'}"
    [class.is-locked]="locked"
    [matTooltip]="locked ? ('nav.lockedUntilPublished' | transloco) : null"
    matTooltipPosition="right"
    [attr.aria-disabled]="locked"
    (click)="locked ? onLockedClick($event) : onLinkClick()"
    class="nav-item"
  >
    @if (route.icon) {
      <mat-icon matListItemIcon>{{ route.icon }}</mat-icon>
    }
    <span matListItemTitle>{{ route.label | transloco }}</span>
    @if (locked) {
      <mat-icon matListItemMeta class="lock-icon" aria-hidden="true">lock</mat-icon>
    }
  </a>
}
```

```typescript
protected onLockedClick(event: MouseEvent): void {
  event.preventDefault();
  event.stopPropagation();
  // Mobile only: show snackbar (tooltip wouldn't be reachable on touch).
  if (window.matchMedia('(hover: none)').matches) {
    this.snackBar.open(
      this.transloco.translate('nav.lockedUntilPublished'),
      undefined,
      { duration: 2500 },
    );
  }
}
```

#### 5. Style

```scss
.nav-item.is-locked {
  opacity: 0.5;
  cursor: not-allowed;
  pointer-events: auto; // keep hover for the tooltip

  .mat-mdc-list-item-title {
    color: var(--mat-sys-on-surface-variant);
  }

  .lock-icon {
    font-size: 16px;
    width: 16px;
    height: 16px;
    opacity: 0.6;
  }
}
```

### i18n

- `nav.lockedUntilPublished` — *"Disponible après publication"* / *"Available after publication"*.

### Tests

- `tenant-status.service.spec.ts` : `set()` et `reset()` font ce qu'on attend.
- `sidenav-menu.spec.ts` : ajouts —
  - Quand `tenantStatus.status() === 'DRAFT'`, les routes `lockedUntilPublished` ont la classe `is-locked`.
  - Quand `tenantStatus.status() === 'ACTIVE'`, elles sont normales.
  - `onLockedClick(event)` appelle `event.preventDefault()` et `event.stopPropagation()`.
- `pro-shell.component.spec.ts` : l'effect `tenantStatusService.set(...)` est déclenché quand `store.readiness().status` change.

### Périmètre

- Nouveau : `core/tenant/tenant-status.service.ts(.spec)`.
- Modifié : `shared/layout/navigation/navigation-routes.ts`, `sidenav-menu.{ts,html,scss,spec.ts}`, `pages/pro/pro-shell.component.ts`, `core/auth/auth.service.ts` (reset au logout), `public/i18n/{fr,en}.json`.

---

## PR 1.5.C — Bulles d'explication + focus visuel page cible

### Comportement utilisateur

**Sur l'indicateur** :
- **Stepper PC** : au survol d'une étape, tooltip Material qui affiche la description courte de l'étape.
- **Pill mobile** : la pill ouvre déjà un bottom-sheet où chaque étape est listée. Une **ligne de description** apparaît sous le titre.

**Sur la page cible** :
- Quand le pro arrive sur `/pro/salon` ou `/pro/planning` depuis l'indicateur (avec un query param `?focus=<field>`), le champ visé :
  - reçoit le focus auto,
  - est entouré d'une **border rose pulsante** (animation 2.4s, 3 cycles, puis se calme),
  - est scrollé en vue (`scrollIntoView({ behavior: 'smooth', block: 'center' })`).

### Architecture

#### Tooltips sur le stepper PC

Modification de `onboarding-indicator.component.html` — ajouter `[matTooltip]` sur les `<a>` du stepper :

```html
<a
  class="stepper-link"
  [routerLink]="step.link"
  [queryParams]="step.queryParams"
  [matTooltip]="('pro.dashboard.checklist.' + step.key + 'Desc') | transloco"
  matTooltipPosition="below"
  [attr.aria-current]="!step.done && progress().nextKey === step.key ? 'step' : null"
  [attr.data-testid]="'stepper-step-' + step.key"
>
  <!-- existing content -->
</a>
```

Import à ajouter au composant : `MatTooltipModule`.

Les clés `pro.dashboard.checklist.nameDesc`, `caresDesc`, `openingHoursDesc` existent déjà dans `fr.json` et `en.json` (ajoutées au Jalon 1 pour la checklist du dashboard).

#### Description dans le bottom-sheet mobile

Modification de `onboarding-indicator-sheet.component.html`. Dans la liste des étapes, on passe la `<span>` titre vers une `<span>` body qui contient titre + description :

```html
<button class="sheet-step" /* attributes inchangés */>
  <mat-icon class="sheet-step-icon" aria-hidden="true">
    {{ step.done ? 'check_circle' : 'radio_button_unchecked' }}
  </mat-icon>
  <span class="sheet-step-body">
    <span class="sheet-step-label">
      {{ 'pro.dashboard.checklist.' + step.key | transloco }}
    </span>
    <span class="sheet-step-desc">
      {{ 'pro.dashboard.checklist.' + step.key + 'Desc' | transloco }}
    </span>
  </span>
  <mat-icon class="sheet-step-chevron" aria-hidden="true">chevron_right</mat-icon>
</button>
```

SCSS associé :

```scss
.sheet-step-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.sheet-step-label {
  font-size: 14px;
  color: var(--mat-sys-on-surface);
}

.sheet-step-desc {
  font-size: 11px;
  color: var(--mat-sys-on-surface-variant, #888);
  line-height: 1.3;
}
```

#### Focus visuel sur page cible

**Mécanisme** : query param `?focus=<fieldName>` ajouté au `routerLink` quand l'utilisateur clique sur une étape pas-encore-faite.

##### Étape A — Modifier `OnboardingChecklistService.buildSteps`

```typescript
buildSteps(readiness: TenantReadiness | null): OnboardingStep[] {
  if (!readiness) return [];
  return [
    {
      key: 'name',
      done: readiness.name,
      link: '/pro/salon',
      queryParams: readiness.name ? null : { focus: 'name' },
    },
    {
      key: 'cares',
      done: readiness.hasActiveCare,
      link: '/pro/cares',
      queryParams: readiness.hasActiveCare ? null : { openCreate: 'care' },
    },
    {
      key: 'openingHours',
      done: readiness.hasOpeningHours,
      link: '/pro/planning',
      queryParams: readiness.hasOpeningHours ? null : { focus: 'openingHours' },
    },
  ];
}
```

> L'étape `cares` conserve `openCreate=care` (qui ouvre le modal de création) — c'est un meilleur guidage que focus dans ce contexte. Pas de changement.

##### Étape B — Directive partagée `[appFocusOnQueryParam]`

`frontend/src/app/shared/uis/focus-on-query-param/focus-on-query-param.directive.ts` :

```typescript
@Directive({
  selector: '[appFocusOnQueryParam]',
  standalone: true,
})
export class FocusOnQueryParamDirective implements OnInit {
  /** Match value of the `focus` query param. */
  readonly appFocusOnQueryParam = input.required<string>();

  private readonly el = inject(ElementRef<HTMLElement>);
  private readonly route = inject(ActivatedRoute);
  private readonly platformId = inject(PLATFORM_ID);

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const focusValue = this.route.snapshot.queryParamMap.get('focus');
    if (focusValue !== this.appFocusOnQueryParam()) return;
    setTimeout(() => this.applyHighlight(), 0);
  }

  private applyHighlight(): void {
    const target = this.el.nativeElement;
    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    const input = target.querySelector('input, textarea, select') as HTMLElement | null;
    (input ?? target).focus();
    target.classList.add('focus-pulse');
    setTimeout(() => target.classList.remove('focus-pulse'), 2400);
  }
}
```

##### Étape C — Style global pour `.focus-pulse`

Dans `frontend/src/styles.scss` (global) :

```scss
.focus-pulse {
  border-radius: 8px;
  animation: focus-pulse 800ms ease-in-out 3;
}

@keyframes focus-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(192, 0, 102, 0); }
  50%      { box-shadow: 0 0 0 6px rgba(192, 0, 102, 0.18); }
}

@media (prefers-reduced-motion: reduce) {
  .focus-pulse {
    animation: none;
    box-shadow: 0 0 0 2px rgba(192, 0, 102, 0.45);
  }
}
```

##### Étape D — Application

Sur `salon-profile.component.html`, le `<mat-form-field>` qui wrappe le champ `name` reçoit la directive :

```html
<mat-form-field appearance="outline" appFocusOnQueryParam="name">
  <!-- existing content -->
</mat-form-field>
```

Sur `pro-planning.component.html`, le bloc principal des horaires reçoit la directive sur un wrapper :

```html
<div appFocusOnQueryParam="openingHours" class="opening-hours-block">
  <!-- existing form -->
</div>
```

### Accessibilité

- Tooltip Material : rôle `tooltip` natif, accessible au clavier.
- Animation `focus-pulse` : respecte `prefers-reduced-motion: reduce`.
- `scrollIntoView({ behavior: 'smooth' })` : se réduit automatiquement avec `prefers-reduced-motion: reduce` (comportement natif du navigateur).

### Tests

- `onboarding-indicator.component.spec.ts` : vérifier la présence de l'attribut tooltip avec la valeur attendue sur les éléments de stepper.
- `onboarding-indicator-sheet.component.spec.ts` : vérifier qu'une description s'affiche sous chaque step label.
- `focus-on-query-param.directive.spec.ts` : nouveau spec — un host component avec la directive, simule `?focus=name` via `ActivatedRoute` mock, vérifie que `scrollIntoView`, `focus`, et `classList.add('focus-pulse')` sont appelés.
- `onboarding-checklist.service.spec.ts` : étendre — `name === false` → `queryParams: { focus: 'name' }`, `name === true` → `null`. Idem `openingHours`.

### Périmètre

- Nouveau : `shared/uis/focus-on-query-param/focus-on-query-param.directive.ts(.spec)`.
- Modifié : `onboarding-indicator.component.{ts,html}`, `onboarding-indicator-sheet.component.{html,scss,spec.ts}`, `onboarding-checklist.service.{ts,spec.ts}`, `salon-profile.component.html`, `pro-planning.component.html`, `styles.scss`.

---

## PR 1.5.D — Confirmation password + feedback formulaires

### Sous-sujet 1 — Confirmation mot de passe (3 formulaires)

#### Périmètre

`/register`, `/register/pro`, `/reset-password` — tous les formulaires qui définissent un mot de passe.

#### Validator partagé

`frontend/src/app/core/auth/password-match.validator.ts` :

```typescript
import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export const passwordMatchValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const password = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  if (!password || !confirm) return null; // required validators handle the empty case
  return password === confirm ? null : { passwordMismatch: true };
};
```

#### Application

Dans chaque composant :

```typescript
this.fb.group(
  {
    // existing fields...
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', [Validators.required]],
  },
  { validators: [passwordMatchValidator] },
);
```

Template : nouveau `<mat-form-field>` pour `confirmPassword`, avec affichage de l'erreur :

```html
<mat-form-field appearance="outline">
  <mat-label>{{ 'auth.field.confirmPassword' | transloco }}</mat-label>
  <input matInput type="password" formControlName="confirmPassword" required />
  @if (form.errors?.['passwordMismatch'] && form.get('confirmPassword')?.touched) {
    <mat-error>{{ 'auth.errors.passwordMismatch' | transloco }}</mat-error>
  }
</mat-form-field>
```

### Sous-sujet 2 — Feedback formulaires

#### Composant partagé `<app-form-validation-hint>`

`frontend/src/app/shared/uis/form-validation-hint/form-validation-hint.component.ts` :

```typescript
@Component({
  selector: 'app-form-validation-hint',
  standalone: true,
  imports: [TranslocoPipe, MatIconModule],
  template: `
    @if (visible()) {
      <p class="form-validation-hint" role="status">
        <mat-icon aria-hidden="true">info</mat-icon>
        <span>{{ message() | transloco }}</span>
      </p>
    }
  `,
  styleUrl: './form-validation-hint.component.scss',
})
export class FormValidationHintComponent {
  readonly form = input.required<FormGroup>();
  readonly message = input<string>('common.form.fillRequiredFields');

  // FormGroup state isn't reactive to signals natively. We listen to
  // statusChanges and fold dirty/touched into the same signal.
  protected readonly visible = computed(() => {
    const f = this.form();
    // toSignal of statusChanges so this computed re-evaluates on every status change.
    void this.formStatus(); // touch the dependency
    return f.invalid && (f.touched || f.dirty);
  });

  private readonly formStatus = computed(() => {
    const f = this.form();
    return toSignal(f.statusChanges, { initialValue: f.status });
  });
}
```

> Note d'implémentation : `FormGroup.invalid` n'est pas un signal, il faut bind `statusChanges`. La version finale dans le plan d'implémentation détaille la subtilité — l'idée est de combiner `toSignal(statusChanges)` avec une lecture directe de `invalid`/`touched`/`dirty` au moment du computed.

#### Style

`form-validation-hint.component.scss` :

```scss
.form-validation-hint {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 8px 14px;
  background: rgba(192, 0, 102, 0.06);
  border: 1px solid rgba(192, 0, 102, 0.2);
  border-radius: 8px;
  color: #6b1d3f;
  font-size: 12px;

  mat-icon {
    font-size: 18px;
    width: 18px;
    height: 18px;
    color: #c06;
  }
}
```

#### Application

Sur les 4 formulaires concernés (3 auth + salon-profile) :

```html
<button mat-raised-button color="primary" type="submit" [disabled]="form.invalid || isLoading()">
  {{ 'auth.register.submit' | transloco }}
</button>

<app-form-validation-hint [form]="form" />
```

#### Audit & complétion des `mat-error`

Pendant qu'on touche à ces formulaires, on s'assure que **chaque champ requis** a :
- L'attribut `required` sur `<input>` (asterisque visuel automatique avec Material).
- Un `<mat-error>` qui matche les validators du contrôle (`required`, `email`, `minlength`, etc.).

Le plan d'implémentation listera précisément les ajouts par champ.

### i18n nouvelles clés

```json
{
  "auth": {
    "field": {
      "confirmPassword": "Confirmer le mot de passe"
    },
    "errors": {
      "passwordMismatch": "Les mots de passe ne correspondent pas"
    }
  },
  "common": {
    "form": {
      "fillRequiredFields": "Remplissez tous les champs requis pour continuer"
    }
  }
}
```

(Versions EN dans `en.json`.)

### Tests

- `password-match.validator.spec.ts` : 3 cas (match, mismatch, empty).
- `form-validation-hint.component.spec.ts` : visible quand invalid+touched, hidden sinon, change correctement quand `statusChanges` émet.
- `register.component.spec.ts`, `register-pro.component.spec.ts`, `reset-password.component.spec.ts` : étendus pour couvrir le `confirmPassword` field, le validator cross-field, et la présence du hint.

### Périmètre

- Nouveau : `core/auth/password-match.validator.ts(.spec)`, `shared/uis/form-validation-hint/form-validation-hint.component.{ts,scss,spec.ts}`.
- Modifié : `register.component.{ts,html,spec.ts}`, `register-pro.component.{ts,html,spec.ts}`, `reset-password.component.{ts,html,spec.ts}`, `salon-profile.component.{html,ts}` (intégration du hint), `public/i18n/{fr,en}.json`.

---

## Risques identifiés

- **`FormGroup.statusChanges` réactivité dans `<app-form-validation-hint>`** : le bind via `toSignal` peut être subtil. Le plan d'implémentation devra expliciter le pattern et le tester.
- **Conflit de `?focus=` avec d'autres query params existants** : aujourd'hui seul `openCreate` existe sur `/pro/cares`. Le risque de collision est faible mais à vérifier.
- **`TenantStatusService` non-initialisé** quand le pro n'a pas encore visité une page sous `/pro/*` : la sidenav considère par défaut que le statut est inconnu → tout est libre. C'est correct (un pro sans store actif ne peut pas avoir une UI désincarnée), mais vérifier le cas du *premier accès direct au sidenav* (ex : url `/discover` puis ouverture du burger).
- **PR 1.5.B sidenav** : si un pro accède à `/pro/posts` directement par URL alors qu'il est en DRAFT, le gating sidenav ne le bloque pas. **Décision** : on n'introduit pas de guard pour J1.5 — la priorité est l'expérience sidenav. Un guard route-level pourra venir plus tard si nécessaire.

## Dépendances

- Aucune dépendance externe nouvelle.
- Pré-requis : Jalon 1 mergé (`feat/onboarding-indicator` → `main`).

## Ce qui suit

Plan d'implémentation détaillé pour chaque PR (`writing-plans`). Chaque PR = un commit ou groupe de commits livrable indépendamment. Quand J1.5 est shippé, on passe au plan du Jalon 2 (preview vitrine) qui est déjà rédigé dans `docs/superpowers/plans/2026-05-06-vitrine-preview.md`.
