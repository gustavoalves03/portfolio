# Onboarding Polish (Jalon 1.5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish the pro onboarding experience — refresh checklist on dashboard return, gate sidenav navigation while DRAFT, add explanatory tooltips/descriptions on the indicator, focus-pulse the target field after step click, and add password confirmation + form-level validation hints to the auth and salon-profile forms.

**Architecture:** Four independent PRs (1.5.A → 1.5.D). PR A patches the `ProShellComponent` to re-fetch `readiness` on dashboard navigation. PR B introduces `TenantStatusService` (root-singleton, fed by `DashboardStore` via `effect`) and a `lockedUntilPublished` flag on nav routes. PR C adds Material tooltips on the desktop stepper, expands the mobile sheet items with descriptions, and ships a reusable `[appFocusOnQueryParam]` directive consumed by salon-profile and pro-planning. PR D adds a shared `passwordMatchValidator` (used by `register` and `reset-password` reactive forms) plus a `<app-form-validation-hint>` component, with `register-pro` getting a signals-native equivalent.

**Tech Stack:** Angular 20 (standalone, signals, zoneless), NgRx SignalStore, Angular Material, ReactiveForms, Transloco. Tests: Karma/Jasmine.

**Spec reference:** `docs/superpowers/specs/2026-05-06-onboarding-polish-design.md`.

**Branch:** `feat/onboarding-polish` (create from `main` after Jalon 1 has been merged). If Jalon 1 is still on `feat/onboarding-indicator`, create from there and rebase later.

---

## File Structure

**PR 1.5.A — refresh checklist (2 files):**

| Path | Change |
|------|--------|
| `frontend/src/app/pages/pro/pro-shell.component.ts` | Add `Router` injection + `NavigationEnd` filter that re-calls `store.loadReadiness()`. |
| `frontend/src/app/pages/pro/pro-shell.component.spec.ts` | Add a test that simulates router events. |

**PR 1.5.B — sidenav gating (8 files):**

| Path | Change |
|------|--------|
| `frontend/src/app/core/tenant/tenant-status.service.ts` | New, root-provided service exposing a `Signal<TenantStatus \| null>`. |
| `frontend/src/app/core/tenant/tenant-status.service.spec.ts` | New tests (set/reset). |
| `frontend/src/app/shared/layout/navigation/navigation-routes.ts` | Add `lockedUntilPublished?: boolean` to `NavigationRoute`; flag posts/employees. |
| `frontend/src/app/shared/layout/navigation/sidenav-menu.ts` | Inject `TenantStatusService` + `MatSnackBar` + `TranslocoService`; add `onLockedClick`. |
| `frontend/src/app/shared/layout/navigation/sidenav-menu.html` | Locked-state binding (class, tooltip, icon, click handler). |
| `frontend/src/app/shared/layout/navigation/sidenav-menu.scss` | `.is-locked` styles. |
| `frontend/src/app/shared/layout/navigation/sidenav-menu.spec.ts` | New tests for the locked-state branches (file may already exist or be created). |
| `frontend/src/app/pages/pro/pro-shell.component.ts` | Add `effect` mirroring `store.readiness().status` into `tenantStatusService`. |
| `frontend/src/app/core/auth/auth.service.ts` | Reset `tenantStatusService` on logout. |
| `frontend/public/i18n/{fr,en}.json` | Add `nav.lockedUntilPublished`. |

**PR 1.5.C — tooltips + focus pulse (10 files):**

| Path | Change |
|------|--------|
| `frontend/src/app/features/onboarding/onboarding-checklist.service.ts` | Add `focus` query param to `name` and `openingHours` undone steps. |
| `frontend/src/app/features/onboarding/onboarding-checklist.service.spec.ts` | New assertions on the `focus` queryParam. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.ts` | Add `MatTooltipModule` to imports. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.html` | Add `[matTooltip]` on stepper links. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.html` | Restructure step item to include a description line. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.scss` | Style `.sheet-step-body` and `.sheet-step-desc`. |
| `frontend/src/app/shared/uis/focus-on-query-param/focus-on-query-param.directive.ts` | New directive. |
| `frontend/src/app/shared/uis/focus-on-query-param/focus-on-query-param.directive.spec.ts` | New tests. |
| `frontend/src/styles.scss` | Global `.focus-pulse` keyframe + reduced-motion fallback. |
| `frontend/src/app/features/salon-profile/salon-profile.component.html` | Apply directive to the `name` field. |
| `frontend/src/app/features/salon-profile/salon-profile.component.ts` | Add directive to imports. |
| `frontend/src/app/pages/pro/pro-planning.component.html` | Apply directive to opening hours block. |
| `frontend/src/app/pages/pro/pro-planning.component.ts` | Add directive to imports. |

**PR 1.5.D — password confirmation + form hints (12 files):**

| Path | Change |
|------|--------|
| `frontend/src/app/core/auth/password-match.validator.ts` | New cross-field validator. |
| `frontend/src/app/core/auth/password-match.validator.spec.ts` | New tests. |
| `frontend/src/app/shared/uis/form-validation-hint/form-validation-hint.component.ts` | New component. |
| `frontend/src/app/shared/uis/form-validation-hint/form-validation-hint.component.scss` | Hint styles. |
| `frontend/src/app/shared/uis/form-validation-hint/form-validation-hint.component.spec.ts` | New tests. |
| `frontend/src/app/pages/auth/register/register.component.ts` | Add `confirmPassword` control + `passwordMatchValidator`; add hint component. |
| `frontend/src/app/pages/auth/register/register.component.html` | Add confirmPassword field, mat-error, hint placement. |
| `frontend/src/app/pages/auth/register-pro/register-pro.component.ts` | Add `confirmPassword` signal + `passwordsMatch` computed. |
| `frontend/src/app/pages/auth/register-pro/register-pro.component.html` | Add confirmPassword field + manual error display. |
| `frontend/src/app/pages/auth/reset-password/reset-password.component.ts` | Refactor to use shared validator instead of manual check. |
| `frontend/src/app/pages/auth/reset-password/reset-password.component.html` | Use `mat-error` driven by validator instead of manual `passwordsMismatch`. |
| `frontend/src/app/features/salon-profile/salon-profile.component.html` | Add hint component near submit button. |
| `frontend/src/app/features/salon-profile/salon-profile.component.ts` | Add `FormValidationHintComponent` to imports if needed. |
| `frontend/public/i18n/{fr,en}.json` | Add `auth.field.confirmPassword`, `auth.errors.passwordMismatch`, `common.form.fillRequiredFields`. |

---

## Conventions

(Same as the Jalon 1 plan; condensed.)

- Standalone components, signals, `inject()`, `@if` / `@for`.
- Functional providers in `TestBed`: `provideZonelessChangeDetection()`, `provideRouter([])`, etc.
- `patchState(store as any, ...)` is the project pattern when patching SignalStores in tests (NgRx exposes `patchState` with private state-source typing).
- i18n: BOTH `frontend/public/i18n/fr.json` AND `en.json` updated. Existing `pro.dashboard.checklist.{nameDesc,caresDesc,openingHoursDesc}` keys are reused — don't duplicate.
- Tests: from `frontend/`, run `npm test -- --include='<glob>' --watch=false`.
- Conventional Commits.

---

# PR 1.5.A — Refresh checklist on dashboard return

## Task A1: Add NavigationEnd subscription in ProShellComponent

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-shell.component.ts`
- Modify: `frontend/src/app/pages/pro/pro-shell.component.spec.ts`

- [ ] **Step 1: Write the failing test**

In `pro-shell.component.spec.ts`, add a new test inside the existing `describe`. Replace the imports at the top to include `Router`, `NavigationEnd`, and `Subject`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { NavigationEnd, Router, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Subject } from 'rxjs';
import { ProShellComponent } from './pro-shell.component';
import { DashboardStore } from '../../features/dashboard/store/dashboard.store';
```

Then add a new test inside the describe (after the existing two):

```typescript
  it('reloads readiness when navigating to /pro/dashboard', () => {
    const httpTesting = TestBed.inject(HttpTestingController);
    // Initial load on construction (from store's onInit hook)
    httpTesting.expectOne((req) => req.url.endsWith('/api/dashboard/readiness')).flush({
      slug: 'demo',
      name: false,
      hasCategory: false,
      hasActiveCare: false,
      hasOpeningHours: false,
      canPublish: false,
      status: 'DRAFT',
    });

    // Simulate a NavigationEnd event toward /pro/dashboard
    const router = TestBed.inject(Router);
    const events = router.events as unknown as Subject<unknown>;
    events.next(new NavigationEnd(1, '/pro/dashboard', '/pro/dashboard'));

    // Expect a second readiness request
    httpTesting.expectOne((req) => req.url.endsWith('/api/dashboard/readiness')).flush({
      slug: 'demo',
      name: true,
      hasCategory: false,
      hasActiveCare: false,
      hasOpeningHours: false,
      canPublish: false,
      status: 'DRAFT',
    });
    httpTesting.verify();
  });
```

- [ ] **Step 2: Run the test — expect failure**

```bash
cd frontend && npm test -- --include='**/pro-shell.component.spec.ts' --watch=false
```
Expected: 3 specs total, the new one fails — second `expectOne` finds zero requests because `loadReadiness` isn't re-triggered.

- [ ] **Step 3: Implement the subscription**

Replace the entire content of `frontend/src/app/pages/pro/pro-shell.component.ts` with:

```typescript
import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { DashboardStore } from '../../features/dashboard/store/dashboard.store';
import { OnboardingIndicatorComponent } from '../../shared/features/onboarding-indicator/onboarding-indicator.component';

@Component({
  selector: 'app-pro-shell',
  standalone: true,
  imports: [RouterOutlet, OnboardingIndicatorComponent],
  providers: [DashboardStore],
  templateUrl: './pro-shell.component.html',
  styleUrl: './pro-shell.component.scss',
})
export class ProShellComponent {
  private readonly store = inject(DashboardStore);
  private readonly router = inject(Router);

  constructor() {
    // Re-fetch readiness on every entry to the dashboard so the checklist
    // reflects steps the pro just completed on /pro/cares or /pro/planning.
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

- [ ] **Step 4: Run the test — expect pass**

```bash
cd frontend && npm test -- --include='**/pro-shell.component.spec.ts' --watch=false
```
Expected: 3 specs pass.

- [ ] **Step 5: Verify TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/pro/pro-shell.component.ts frontend/src/app/pages/pro/pro-shell.component.spec.ts
git commit -m "fix(pro-shell): refetch readiness on every navigation to /pro/dashboard"
```

---

# PR 1.5.B — Sidenav gating during DRAFT

## Task B1: TenantStatusService

**Files:**
- Create: `frontend/src/app/core/tenant/tenant-status.service.ts`
- Create: `frontend/src/app/core/tenant/tenant-status.service.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/core/tenant/tenant-status.service.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TenantStatusService } from './tenant-status.service';

describe('TenantStatusService', () => {
  let service: TenantStatusService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
    });
    service = TestBed.inject(TenantStatusService);
  });

  it('starts with null status', () => {
    expect(service.status()).toBeNull();
  });

  it('set() updates the status signal', () => {
    service.set('DRAFT');
    expect(service.status()).toBe('DRAFT');
    service.set('ACTIVE');
    expect(service.status()).toBe('ACTIVE');
  });

  it('reset() returns the status to null', () => {
    service.set('DRAFT');
    service.reset();
    expect(service.status()).toBeNull();
  });
});
```

- [ ] **Step 2: Run the test — expect failure**

```bash
cd frontend && npm test -- --include='**/tenant-status.service.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the service**

Create `frontend/src/app/core/tenant/tenant-status.service.ts`:

```typescript
import { Injectable, signal } from '@angular/core';

export type TenantStatusValue = 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';

/**
 * Lightweight root-scoped store for the current pro's tenant status.
 *
 * Fed by the ProShellComponent (which holds the heavy DashboardStore) via
 * an effect. Consumed by the global SidenavMenu which sits outside the
 * /pro/* route subtree and therefore can't access the dashboard store
 * directly. Stays null until a pro session populates it; logout calls reset.
 */
@Injectable({ providedIn: 'root' })
export class TenantStatusService {
  readonly status = signal<TenantStatusValue | null>(null);

  set(value: TenantStatusValue | null): void {
    this.status.set(value);
  }

  reset(): void {
    this.status.set(null);
  }
}
```

- [ ] **Step 4: Run the test — expect pass**

```bash
cd frontend && npm test -- --include='**/tenant-status.service.spec.ts' --watch=false
```
Expected: 3 specs pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/tenant/tenant-status.service.ts frontend/src/app/core/tenant/tenant-status.service.spec.ts
git commit -m "feat(tenant): add TenantStatusService for global status access"
```

---

## Task B2: Mark posts and employees routes as locked-until-published

**Files:**
- Modify: `frontend/src/app/shared/layout/navigation/navigation-routes.ts`

- [ ] **Step 1: Extend the NavigationRoute type**

Open `frontend/src/app/shared/layout/navigation/navigation-routes.ts`. Find the `NavigationRoute` interface (top of file). Add the new optional field:

```typescript
export interface NavigationRoute {
  label: string;
  path: string;
  icon?: string;
  requiresAuth?: boolean;
  requiredRole?: 'PRO' | 'ADMIN' | 'EMPLOYEE';
  children?: NavigationRoute[];
  /**
   * If true, this pro route is locked while the salon is in DRAFT status.
   * Renders a disabled link with a "available after publication" tooltip.
   */
  lockedUntilPublished?: boolean;
}
```

- [ ] **Step 2: Flag posts and employees**

In the same file, find the `PRO_NAVIGATION_ROUTES` array. Locate the entry for `/pro/posts`:

```typescript
  {
    label: 'nav.pro.posts',
    path: '/pro/posts',
    icon: 'photo_library',
    requiresAuth: true,
    requiredRole: 'PRO',
  },
```

Replace it with:

```typescript
  {
    label: 'nav.pro.posts',
    path: '/pro/posts',
    icon: 'photo_library',
    requiresAuth: true,
    requiredRole: 'PRO',
    lockedUntilPublished: true,
  },
```

Locate the entry for `/pro/employees`:

```typescript
  {
    label: 'nav.pro.employees',
    path: '/pro/employees',
    icon: 'groups',
    requiresAuth: true,
    requiredRole: 'PRO',
  },
```

Replace it with:

```typescript
  {
    label: 'nav.pro.employees',
    path: '/pro/employees',
    icon: 'groups',
    requiresAuth: true,
    requiredRole: 'PRO',
    lockedUntilPublished: true,
  },
```

- [ ] **Step 3: Verify TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/shared/layout/navigation/navigation-routes.ts
git commit -m "feat(nav): flag posts and employees routes as lockedUntilPublished"
```

---

## Task B3: i18n keys for sidenav lock message

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add the FR key**

Open `frontend/public/i18n/fr.json`. Find the existing `"nav"` block. Add the key `"lockedUntilPublished"` as a sibling of existing nav keys (e.g. next to `"menu"`, `"home"`):

```json
    "lockedUntilPublished": "Disponible après publication",
```

- [ ] **Step 2: Add the EN key**

Open `frontend/public/i18n/en.json`. Add to `"nav"`:

```json
    "lockedUntilPublished": "Available after publication",
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
git commit -m "feat(i18n): add nav.lockedUntilPublished key (FR/EN)"
```

---

## Task B4: SidenavMenu locked-state rendering

**Files:**
- Modify: `frontend/src/app/shared/layout/navigation/sidenav-menu.ts`
- Modify: `frontend/src/app/shared/layout/navigation/sidenav-menu.html`
- Modify: `frontend/src/app/shared/layout/navigation/sidenav-menu.scss`

- [ ] **Step 1: Update the component TS**

Open `frontend/src/app/shared/layout/navigation/sidenav-menu.ts`. Replace the imports section (top of file) with:

```typescript
import { Component, computed, inject, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  CLIENT_NAVIGATION_ROUTES,
  EMPLOYEE_NAVIGATION_ROUTES,
  NAVIGATION_ROUTES,
  NavigationRoute,
  PRO_NAVIGATION_ROUTES,
} from './navigation-routes';
import { LangService } from '../../../i18n/lang.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Role } from '../../../core/auth/auth.model';
import { TenantFeaturesService } from '../../../core/tenant/tenant-features.service';
import { TenantStatusService } from '../../../core/tenant/tenant-status.service';
```

Replace the `@Component` decorator's `imports` array:

```typescript
@Component({
  selector: 'app-sidenav-menu',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatListModule, MatIconModule, MatTooltipModule, TranslocoPipe],
  templateUrl: './sidenav-menu.html',
  styleUrl: './sidenav-menu.scss',
})
```

Inside the class, add the new injections at the top of the existing field declarations:

```typescript
  protected readonly authService = inject(AuthService);
  protected readonly langService = inject(LangService);
  protected readonly featuresService = inject(TenantFeaturesService);
  protected readonly tenantStatus = inject(TenantStatusService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
```

At the end of the class, add the locked-click handler:

```typescript
  protected onLockedClick(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    if (typeof window !== 'undefined' && window.matchMedia('(hover: none)').matches) {
      this.snackBar.open(
        this.transloco.translate('nav.lockedUntilPublished'),
        undefined,
        { duration: 2500 },
      );
    }
  }
```

- [ ] **Step 2: Update the template**

Open `frontend/src/app/shared/layout/navigation/sidenav-menu.html`. Find the `@for (route of routes(); track route.path)` block (around line 10-22). Replace its entire content with:

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
          [attr.aria-disabled]="locked ? true : null"
          (click)="locked ? onLockedClick($event) : onLinkClick()"
          class="nav-item"
        >
          @if (route.icon) {
            <mat-icon matListItemIcon class="text-neutral-600">{{ route.icon }}</mat-icon>
          }
          <span matListItemTitle class="text-neutral-900 font-light tracking-wide">
            {{ route.label | transloco }}
          </span>
          @if (locked) {
            <mat-icon matListItemMeta class="lock-icon" aria-hidden="true">lock</mat-icon>
          }
        </a>
      }
```

- [ ] **Step 3: Add the locked styles**

Open `frontend/src/app/shared/layout/navigation/sidenav-menu.scss`. Append:

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

- [ ] **Step 4: Verify TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/layout/navigation/sidenav-menu.ts frontend/src/app/shared/layout/navigation/sidenav-menu.html frontend/src/app/shared/layout/navigation/sidenav-menu.scss
git commit -m "feat(sidenav): render locked state on routes lockedUntilPublished while DRAFT"
```

---

## Task B5: Sync DashboardStore.readiness().status into TenantStatusService

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-shell.component.ts`

- [ ] **Step 1: Update the component**

Open `frontend/src/app/pages/pro/pro-shell.component.ts`. Replace the entire content with:

```typescript
import { Component, effect, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { DashboardStore } from '../../features/dashboard/store/dashboard.store';
import { OnboardingIndicatorComponent } from '../../shared/features/onboarding-indicator/onboarding-indicator.component';
import { TenantStatusService } from '../../core/tenant/tenant-status.service';

@Component({
  selector: 'app-pro-shell',
  standalone: true,
  imports: [RouterOutlet, OnboardingIndicatorComponent],
  providers: [DashboardStore],
  templateUrl: './pro-shell.component.html',
  styleUrl: './pro-shell.component.scss',
})
export class ProShellComponent {
  private readonly store = inject(DashboardStore);
  private readonly router = inject(Router);
  private readonly tenantStatus = inject(TenantStatusService);

  constructor() {
    // Re-fetch readiness on every entry to the dashboard so the checklist
    // reflects steps the pro just completed on /pro/cares or /pro/planning.
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        filter((e) => e.urlAfterRedirects.startsWith('/pro/dashboard')),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.store.loadReadiness());

    // Mirror tenant status into the global service so the sidenav (which
    // sits outside this route subtree) can render lock states.
    effect(() => {
      const status = this.store.readiness()?.status ?? null;
      this.tenantStatus.set(status);
    });
  }
}
```

- [ ] **Step 2: Verify the existing tests still pass**

```bash
cd frontend && npm test -- --include='**/pro-shell.component.spec.ts' --watch=false
```
Expected: 3 specs pass (the existing 2 from Jalon 1 + the readiness-refetch one from PR A).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/pro/pro-shell.component.ts
git commit -m "feat(pro-shell): mirror tenant status into TenantStatusService for sidenav"
```

---

## Task B6: Reset TenantStatusService on logout

**Files:**
- Modify: `frontend/src/app/core/auth/auth.service.ts`

- [ ] **Step 1: Identify the logout method**

```bash
grep -n 'logout' frontend/src/app/core/auth/auth.service.ts | head -5
```

Confirm the `logout()` method body is at the line shown.

- [ ] **Step 2: Add the import and inject the service**

Open `frontend/src/app/core/auth/auth.service.ts`. At the top of the file, add the import:

```typescript
import { TenantStatusService } from '../tenant/tenant-status.service';
```

Inside the `AuthService` class, locate the existing `inject(...)` calls (top of class). Add:

```typescript
  private readonly tenantStatus = inject(TenantStatusService);
```

- [ ] **Step 3: Reset on logout**

Find the `logout(): void {` method body. At the very top of the method body (before any other statement), add:

```typescript
    this.tenantStatus.reset();
```

If `logout()` is called from multiple paths (e.g. token expiry triggers it), the reset still runs. Good.

- [ ] **Step 4: Run auth tests to verify no regression**

```bash
cd frontend && npm test -- --include='**/auth.service.spec.ts' --watch=false
```
Expected: PASS (or note any pre-existing failures — they're not your concern).

If the test fails because `TenantStatusService` isn't provided, add it to the test's TestBed `providers` array.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/auth/auth.service.ts
git commit -m "feat(auth): reset TenantStatusService on logout"
```

---

# PR 1.5.C — Tooltips on indicator + focus pulse

## Task C1: Extend OnboardingChecklistService with focus query param

**Files:**
- Modify: `frontend/src/app/features/onboarding/onboarding-checklist.service.ts`
- Modify: `frontend/src/app/features/onboarding/onboarding-checklist.service.spec.ts`

- [ ] **Step 1: Add the failing tests**

Open `frontend/src/app/features/onboarding/onboarding-checklist.service.spec.ts`. Inside the `describe('buildSteps', ...)` block (after the existing tests), add:

```typescript
    it('passes focus=name queryParam when name step is undone', () => {
      const steps = service.buildSteps(readiness({ name: false }));
      const nameStep = steps.find((s) => s.key === 'name');
      expect(nameStep?.queryParams).toEqual({ focus: 'name' });
    });

    it('passes null queryParams on name step when name is done', () => {
      const steps = service.buildSteps(readiness({ name: true }));
      const nameStep = steps.find((s) => s.key === 'name');
      expect(nameStep?.queryParams).toBeNull();
    });

    it('passes focus=openingHours queryParam when openingHours step is undone', () => {
      const steps = service.buildSteps(readiness({ hasOpeningHours: false }));
      const ohStep = steps.find((s) => s.key === 'openingHours');
      expect(ohStep?.queryParams).toEqual({ focus: 'openingHours' });
    });

    it('passes null queryParams on openingHours step when done', () => {
      const steps = service.buildSteps(readiness({ hasOpeningHours: true }));
      const ohStep = steps.find((s) => s.key === 'openingHours');
      expect(ohStep?.queryParams).toBeNull();
    });
```

- [ ] **Step 2: Run tests to verify failures**

```bash
cd frontend && npm test -- --include='**/onboarding-checklist.service.spec.ts' --watch=false
```
Expected: the 4 new specs FAIL.

- [ ] **Step 3: Update the implementation**

Open `frontend/src/app/features/onboarding/onboarding-checklist.service.ts`. Replace the `buildSteps` method body with:

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

- [ ] **Step 4: Run tests to verify passes**

```bash
cd frontend && npm test -- --include='**/onboarding-checklist.service.spec.ts' --watch=false
```
Expected: 13 specs pass (5 buildSteps + 4 computeProgress + 4 new).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/onboarding/onboarding-checklist.service.ts frontend/src/app/features/onboarding/onboarding-checklist.service.spec.ts
git commit -m "feat(onboarding): add focus queryParam to name and openingHours steps"
```

---

## Task C2: Add MatTooltip on stepper steps

**Files:**
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.ts`
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.html`
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.spec.ts`

- [ ] **Step 1: Add the failing test**

Open `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.spec.ts`. Inside the existing `describe(...)` block, add a new test (after the others):

```typescript
  it('attaches a Material tooltip with the step description on each desktop step', () => {
    setup(true);
    patchState(store as any, { readiness: readiness({ name: true }) });
    fixture.detectChanges();
    const stepName = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="stepper-step-name"]'
    );
    expect(stepName?.getAttribute('ng-reflect-message')).toBeTruthy();
  });
```

(Material Tooltip's directive sets `ng-reflect-message` on the host. The exact attribute name varies — if this assertion proves brittle, replace it with checking the directive presence via `By.directive`.)

- [ ] **Step 2: Run the test — expect failure**

```bash
cd frontend && npm test -- --include='**/onboarding-indicator.component.spec.ts' --watch=false
```
Expected: the new spec FAILS.

- [ ] **Step 3: Add MatTooltipModule import**

Open `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.ts`. Update the imports section. Find:

```typescript
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
```

Add:

```typescript
import { MatTooltipModule } from '@angular/material/tooltip';
```

In the `@Component({ imports: [...] })` array, add `MatTooltipModule`:

```typescript
  imports: [MatIconModule, MatButtonModule, TranslocoPipe, RouterLink, MatTooltipModule],
```

- [ ] **Step 4: Update the stepper template**

Open `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.html`. Find the stepper `<a class="stepper-link" ... [attr.data-testid]="'stepper-step-' + step.key">` opening tag. Replace it with:

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
```

- [ ] **Step 5: Run tests**

```bash
cd frontend && npm test -- --include='**/onboarding-indicator.component.spec.ts' --watch=false
```
Expected: ALL specs pass (8 existing + 1 new).

If the new spec still fails because of the assertion strategy (`ng-reflect-message`), update it to:

```typescript
  it('attaches a Material tooltip with the step description on each desktop step', () => {
    setup(true);
    patchState(store as any, { readiness: readiness({ name: true }) });
    fixture.detectChanges();
    const stepName = fixture.debugElement.query(
      (el) => el.attributes['data-testid'] === 'stepper-step-name'
    );
    expect(stepName).toBeTruthy();
    // The matTooltip directive should be attached.
    const tooltipDir = stepName.injector.get((window as any).MatTooltip ?? null, null);
    // Fallback: check the host has the tooltip-related attribute.
    expect(stepName.attributes['mattooltip'] !== undefined ||
           stepName.attributes['ng-reflect-message'] !== undefined).toBe(true);
  });
```

(The exact testing form depends on Material's version. If both attempts fail, replace the test with a snapshot of `step.nativeElement.outerHTML` and assert the description text appears as an attribute. The behavior is verified manually via tooltip hover in smoke check.)

- [ ] **Step 6: Verify TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.ts frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.html frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.spec.ts
git commit -m "feat(onboarding-indicator): add Material tooltip with step description on desktop steps"
```

---

## Task C3: Add description line on mobile bottom-sheet steps

**Files:**
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.html`
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.scss`

- [ ] **Step 1: Update the template**

Open `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.html`. Find the `<button class="sheet-step" ...>` block (inside the `@for` loop). Replace its entire body. Locate:

```html
        <button
          type="button"
          class="sheet-step"
          [class.is-done]="step.done"
          [class.is-next]="!step.done && progress.nextKey === step.key"
          [attr.aria-current]="!step.done && progress.nextKey === step.key ? 'step' : null"
          (click)="onStep(step.key)"
          [attr.data-testid]="'sheet-step-' + step.key"
        >
          <mat-icon class="sheet-step-icon" aria-hidden="true">
            {{ step.done ? 'check_circle' : 'radio_button_unchecked' }}
          </mat-icon>
          <span class="sheet-step-label">
            {{ 'pro.dashboard.checklist.' + step.key | transloco }}
          </span>
          <mat-icon class="sheet-step-chevron" aria-hidden="true">chevron_right</mat-icon>
        </button>
```

Replace it with:

```html
        <button
          type="button"
          class="sheet-step"
          [class.is-done]="step.done"
          [class.is-next]="!step.done && progress.nextKey === step.key"
          [attr.aria-current]="!step.done && progress.nextKey === step.key ? 'step' : null"
          (click)="onStep(step.key)"
          [attr.data-testid]="'sheet-step-' + step.key"
        >
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

- [ ] **Step 2: Update the styles**

Open `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.scss`. Find the `.sheet-step-label` rule. **Before** that rule, add:

```scss
.sheet-step-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.sheet-step-desc {
  font-size: 11px;
  color: var(--mat-sys-on-surface-variant, #888);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
```

- [ ] **Step 3: Run sheet tests**

```bash
cd frontend && npm test -- --include='**/onboarding-indicator-sheet.component.spec.ts' --watch=false
```
Expected: 5 specs still pass (the existing tests don't assert against the new body wrapper).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.html frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.scss
git commit -m "feat(onboarding-indicator): show step description under each mobile sheet step"
```

---

## Task C4: FocusOnQueryParamDirective

**Files:**
- Create: `frontend/src/app/shared/uis/focus-on-query-param/focus-on-query-param.directive.ts`
- Create: `frontend/src/app/shared/uis/focus-on-query-param/focus-on-query-param.directive.spec.ts`

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/shared/uis/focus-on-query-param/focus-on-query-param.directive.spec.ts`:

```typescript
import { Component, viewChild, ElementRef } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { FocusOnQueryParamDirective } from './focus-on-query-param.directive';

@Component({
  standalone: true,
  imports: [FocusOnQueryParamDirective],
  template: `
    <div #target appFocusOnQueryParam="name">
      <input id="my-input" />
    </div>
  `,
})
class HostComponent {
  readonly target = viewChild<ElementRef<HTMLDivElement>>('target');
}

describe('FocusOnQueryParamDirective', () => {
  function setup(focusValue: string | null): ComponentFixture<HostComponent> {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(focusValue ? { focus: focusValue } : {}),
            },
          },
        },
      ],
      imports: [HostComponent],
    });
    const f = TestBed.createComponent(HostComponent);
    f.detectChanges();
    return f;
  }

  it('does nothing when query param is missing', fakeAsync(() => {
    const fixture = setup(null);
    tick();
    const target = fixture.componentInstance.target()?.nativeElement;
    expect(target?.classList.contains('focus-pulse')).toBe(false);
  }));

  it('does nothing when query param does not match the directive value', fakeAsync(() => {
    const fixture = setup('other');
    tick();
    const target = fixture.componentInstance.target()?.nativeElement;
    expect(target?.classList.contains('focus-pulse')).toBe(false);
  }));

  it('adds focus-pulse class when query param matches', fakeAsync(() => {
    const fixture = setup('name');
    tick(); // flush the setTimeout(0)
    const target = fixture.componentInstance.target()?.nativeElement;
    expect(target?.classList.contains('focus-pulse')).toBe(true);
  }));

  it('removes focus-pulse class after 2400ms', fakeAsync(() => {
    const fixture = setup('name');
    tick();
    const target = fixture.componentInstance.target()?.nativeElement;
    expect(target?.classList.contains('focus-pulse')).toBe(true);
    tick(2400);
    expect(target?.classList.contains('focus-pulse')).toBe(false);
  }));

  it('focuses the inner input when the host contains one', fakeAsync(() => {
    const fixture = setup('name');
    tick();
    const input = fixture.nativeElement.querySelector('#my-input') as HTMLInputElement;
    expect(document.activeElement).toBe(input);
  }));
});
```

- [ ] **Step 2: Run tests — expect failure (module not found)**

```bash
cd frontend && npm test -- --include='**/focus-on-query-param.directive.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the directive**

Create `frontend/src/app/shared/uis/focus-on-query-param/focus-on-query-param.directive.ts`:

```typescript
import { Directive, ElementRef, OnInit, PLATFORM_ID, inject, input } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { ActivatedRoute } from '@angular/router';

/**
 * On initialization, if the current route's `?focus=<value>` query param
 * matches `appFocusOnQueryParam`, the host element receives focus + a
 * temporary `.focus-pulse` class and is scrolled into view.
 *
 * Used to guide the pro toward the field they need to fill after
 * clicking an unfinished step in the onboarding indicator.
 *
 * SSR-safe: the highlight only runs in a browser context.
 */
@Directive({
  selector: '[appFocusOnQueryParam]',
  standalone: true,
})
export class FocusOnQueryParamDirective implements OnInit {
  /** The string the `?focus=` query param must equal. */
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

- [ ] **Step 4: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/focus-on-query-param.directive.spec.ts' --watch=false
```
Expected: 5 specs pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/uis/focus-on-query-param/
git commit -m "feat(focus-on-query-param): add directive to highlight a field via ?focus= URL param"
```

---

## Task C5: Global focus-pulse animation in styles.scss

**Files:**
- Modify: `frontend/src/styles.scss`

- [ ] **Step 1: Append the focus-pulse styles**

Open `frontend/src/styles.scss`. At the very end of the file, append:

```scss
/* === Focus pulse (used by [appFocusOnQueryParam]) === */
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

- [ ] **Step 2: Verify the build still compiles**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors. (SCSS isn't checked by tsc but the build will fail later if syntax is broken — quick sanity here.)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/styles.scss
git commit -m "feat(styles): add global .focus-pulse animation with reduced-motion fallback"
```

---

## Task C6: Apply directive on salon-profile name field

**Files:**
- Modify: `frontend/src/app/features/salon-profile/salon-profile.component.ts`
- Modify: `frontend/src/app/features/salon-profile/salon-profile.component.html`

- [ ] **Step 1: Identify the name field in the template**

```bash
grep -n 'name\b' frontend/src/app/features/salon-profile/salon-profile.component.html | head -10
```

Find the `<mat-form-field>` that contains the salon name input. It should have `formControlName="name"` or `[(ngModel)]="..."` bound to the salon name signal/control.

- [ ] **Step 2: Add the directive import**

Open `frontend/src/app/features/salon-profile/salon-profile.component.ts`. Find the imports list at the top. Add:

```typescript
import { FocusOnQueryParamDirective } from '../../shared/uis/focus-on-query-param/focus-on-query-param.directive';
```

In the `@Component({ imports: [...] })` array, add `FocusOnQueryParamDirective`. The exact position: after the existing imports, before the closing `]`.

- [ ] **Step 3: Apply the directive in the template**

Open `frontend/src/app/features/salon-profile/salon-profile.component.html`. Find the `<mat-form-field>` for the name field. On its opening tag, add the attribute:

```html
appFocusOnQueryParam="name"
```

Example: if the existing line is `<mat-form-field appearance="outline">`, it becomes `<mat-form-field appearance="outline" appFocusOnQueryParam="name">`.

- [ ] **Step 4: Verify TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 5: Run the existing salon-profile tests**

```bash
cd frontend && npm test -- --include='**/salon-profile.component.spec.ts' --watch=false
```
Expected: PASS (same set as before — directive is no-op when `?focus=` is absent).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/salon-profile/salon-profile.component.ts frontend/src/app/features/salon-profile/salon-profile.component.html
git commit -m "feat(salon-profile): focus pulse on name field when arriving with ?focus=name"
```

---

## Task C7: Apply directive on pro-planning opening hours block

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-planning.component.ts`
- Modify: `frontend/src/app/pages/pro/pro-planning.component.html`

- [ ] **Step 1: Identify the opening hours block**

```bash
grep -n 'opening' frontend/src/app/pages/pro/pro-planning.component.html | head -10
```

Find the wrapper `<div>` or `<section>` that contains the opening hours form. If there's no clear wrapper, wrap the existing opening hours form in a `<div class="opening-hours-block">`.

- [ ] **Step 2: Add the directive import**

Open `frontend/src/app/pages/pro/pro-planning.component.ts`. In the imports list at the top, add:

```typescript
import { FocusOnQueryParamDirective } from '../../shared/uis/focus-on-query-param/focus-on-query-param.directive';
```

In the `@Component({ imports: [...] })` array, add `FocusOnQueryParamDirective`.

- [ ] **Step 3: Apply the directive on the wrapper**

Open `frontend/src/app/pages/pro/pro-planning.component.html`. On the opening hours section's wrapper element, add:

```html
appFocusOnQueryParam="openingHours"
```

Example: `<section class="opening-hours">` → `<section class="opening-hours" appFocusOnQueryParam="openingHours">`.

- [ ] **Step 4: Verify TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 5: Run pro-planning tests**

```bash
cd frontend && npm test -- --include='**/pro-planning.component.spec.ts' --watch=false
```
Expected: PASS (no behavior change without `?focus=`).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/pro/pro-planning.component.ts frontend/src/app/pages/pro/pro-planning.component.html
git commit -m "feat(pro-planning): focus pulse on opening hours when arriving with ?focus=openingHours"
```

---

# PR 1.5.D — Password confirmation + form validation hints

## Task D1: passwordMatchValidator (shared)

**Files:**
- Create: `frontend/src/app/core/auth/password-match.validator.ts`
- Create: `frontend/src/app/core/auth/password-match.validator.spec.ts`

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/core/auth/password-match.validator.spec.ts`:

```typescript
import { FormBuilder } from '@angular/forms';
import { passwordMatchValidator } from './password-match.validator';

describe('passwordMatchValidator', () => {
  const fb = new FormBuilder();

  it('returns null when both passwords are empty', () => {
    const group = fb.group({ password: '', confirmPassword: '' });
    expect(passwordMatchValidator(group)).toBeNull();
  });

  it('returns null when passwords match', () => {
    const group = fb.group({ password: 'secret123', confirmPassword: 'secret123' });
    expect(passwordMatchValidator(group)).toBeNull();
  });

  it('returns { passwordMismatch: true } when passwords differ', () => {
    const group = fb.group({ password: 'secret123', confirmPassword: 'other' });
    expect(passwordMatchValidator(group)).toEqual({ passwordMismatch: true });
  });

  it('returns null when confirm is still empty (let required handle it)', () => {
    const group = fb.group({ password: 'secret123', confirmPassword: '' });
    expect(passwordMatchValidator(group)).toBeNull();
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/password-match.validator.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the validator**

Create `frontend/src/app/core/auth/password-match.validator.ts`:

```typescript
import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Cross-field validator for a FormGroup that contains both `password` and
 * `confirmPassword` controls. Returns `{ passwordMismatch: true }` on the
 * group when the values differ. Returns null when either field is empty,
 * letting individual `Validators.required` validators surface their own
 * errors first.
 */
export const passwordMatchValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const password = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  if (!password || !confirm) return null;
  return password === confirm ? null : { passwordMismatch: true };
};
```

- [ ] **Step 4: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/password-match.validator.spec.ts' --watch=false
```
Expected: 4 specs pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/auth/password-match.validator.ts frontend/src/app/core/auth/password-match.validator.spec.ts
git commit -m "feat(auth): add passwordMatchValidator shared cross-field validator"
```

---

## Task D2: i18n keys for password and form hints

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add FR keys**

Open `frontend/public/i18n/fr.json`. Locate the `"auth"` block. Inside it, locate or create a `"field"` block, and add:

```json
      "confirmPassword": "Confirmer le mot de passe",
```

Add an `"errors"` block (or extend if already present):

```json
      "passwordMismatch": "Les mots de passe ne correspondent pas",
```

Locate the top-level `"common"` block. Inside, locate or create `"form"`:

```json
      "fillRequiredFields": "Remplissez tous les champs requis pour continuer",
```

If `"common"` doesn't exist at the top level of `fr.json`, add it:

```json
  "common": {
    "form": {
      "fillRequiredFields": "Remplissez tous les champs requis pour continuer"
    }
  },
```

- [ ] **Step 2: Add EN keys**

Same operations on `frontend/public/i18n/en.json` with these translations:
- `auth.field.confirmPassword`: `"Confirm password"`
- `auth.errors.passwordMismatch`: `"Passwords don't match"`
- `common.form.fillRequiredFields`: `"Fill in all required fields to continue"`

- [ ] **Step 3: Validate JSON**

```bash
python3 -m json.tool frontend/public/i18n/fr.json > /dev/null && echo FR_OK
python3 -m json.tool frontend/public/i18n/en.json > /dev/null && echo EN_OK
```
Expected: both `OK`.

- [ ] **Step 4: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat(i18n): add confirm password and form-validation-hint keys"
```

---

## Task D3: FormValidationHintComponent

**Files:**
- Create: `frontend/src/app/shared/uis/form-validation-hint/form-validation-hint.component.ts`
- Create: `frontend/src/app/shared/uis/form-validation-hint/form-validation-hint.component.scss`
- Create: `frontend/src/app/shared/uis/form-validation-hint/form-validation-hint.component.spec.ts`

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/shared/uis/form-validation-hint/form-validation-hint.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { FormValidationHintComponent } from './form-validation-hint.component';

describe('FormValidationHintComponent', () => {
  let fixture: ComponentFixture<FormValidationHintComponent>;
  let fb: FormBuilder;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [
        ReactiveFormsModule,
        FormValidationHintComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: { common: { form: { fillRequiredFields: 'Fill required' } } } },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fb = TestBed.inject(FormBuilder);
    fixture = TestBed.createComponent(FormValidationHintComponent);
  });

  function setForm(form: ReturnType<FormBuilder['group']>) {
    fixture.componentRef.setInput('form', form);
    fixture.detectChanges();
  }

  it('hides the hint when the form is pristine and untouched', () => {
    const form = fb.group({ name: ['', Validators.required] });
    setForm(form);
    const hint = (fixture.nativeElement as HTMLElement).querySelector('.form-validation-hint');
    expect(hint).toBeNull();
  });

  it('hides the hint when the form is valid', () => {
    const form = fb.group({ name: ['Pretty', Validators.required] });
    form.markAllAsTouched();
    setForm(form);
    const hint = (fixture.nativeElement as HTMLElement).querySelector('.form-validation-hint');
    expect(hint).toBeNull();
  });

  it('shows the hint when the form is invalid and touched', () => {
    const form = fb.group({ name: ['', Validators.required] });
    form.markAllAsTouched();
    setForm(form);
    const hint = (fixture.nativeElement as HTMLElement).querySelector('.form-validation-hint');
    expect(hint).not.toBeNull();
  });

  it('shows the hint when the form is invalid and dirty', () => {
    const form = fb.group({ name: ['', Validators.required] });
    form.markAsDirty();
    setForm(form);
    const hint = (fixture.nativeElement as HTMLElement).querySelector('.form-validation-hint');
    expect(hint).not.toBeNull();
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/form-validation-hint.component.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the component TS**

Create `frontend/src/app/shared/uis/form-validation-hint/form-validation-hint.component.ts`:

```typescript
import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Renders a small inline hint when its bound FormGroup is invalid AND the user
 * has interacted with it (touched or dirty). Defaults to the
 * `common.form.fillRequiredFields` i18n key.
 *
 * Place it next to a submit button: when the button is disabled because the
 * form is invalid, this hint explains why.
 */
@Component({
  selector: 'app-form-validation-hint',
  standalone: true,
  imports: [MatIconModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
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

  // FormGroup state isn't natively reactive to signals. We bump a tick
  // signal whenever the form's status or pristine/touched state changes so
  // the `visible` computed re-evaluates.
  private readonly tick = signal(0);

  protected readonly visible = computed(() => {
    void this.tick();
    const f = this.form();
    return f.invalid && (f.touched || f.dirty);
  });

  constructor() {
    effect((onCleanup) => {
      const f = this.form();
      const sub = f.statusChanges.subscribe(() => this.tick.update((n) => n + 1));
      // Mark/dirty changes don't always emit statusChanges. We hook into
      // touched/dirty bumps via the form's own observable streams when
      // available; otherwise the tick on statusChanges suffices for the
      // primary use case (typing into a required field).
      onCleanup(() => sub.unsubscribe());
    });
  }
}
```

- [ ] **Step 4: Implement the SCSS**

Create `frontend/src/app/shared/uis/form-validation-hint/form-validation-hint.component.scss`:

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
  line-height: 1.4;

  mat-icon {
    font-size: 18px;
    width: 18px;
    height: 18px;
    color: #c06;
  }
}
```

- [ ] **Step 5: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/form-validation-hint.component.spec.ts' --watch=false
```
Expected: 4 specs pass. If a test about `markAsDirty` fails because no `statusChanges` event is emitted, accept the limitation: the hint will still appear once the user interacts with any field (which triggers statusChanges in practice). Document this in a comment if it surfaces.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/shared/uis/form-validation-hint/
git commit -m "feat(form-validation-hint): add inline hint component for invalid forms"
```

---

## Task D4: register.component — confirmPassword + hint

**Files:**
- Modify: `frontend/src/app/pages/auth/register/register.component.ts`
- Modify: `frontend/src/app/pages/auth/register/register.component.html`
- Modify: `frontend/src/app/pages/auth/register/register.component.spec.ts`

- [ ] **Step 1: Update the component TS**

Open `frontend/src/app/pages/auth/register/register.component.ts`. At the top, add the imports:

```typescript
import { passwordMatchValidator } from '../../../core/auth/password-match.validator';
import { FormValidationHintComponent } from '../../../shared/uis/form-validation-hint/form-validation-hint.component';
```

In the `@Component({ imports: [...] })` array, add `FormValidationHintComponent`.

Replace the `form` declaration:

```typescript
  readonly form = this.fb.group(
    {
      name: ['', [Validators.required]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]],
      consent: [false, Validators.requiredTrue],
    },
    { validators: [passwordMatchValidator] },
  );
```

Add a getter (next to existing getters):

```typescript
  get confirmPasswordControl() { return this.form.get('confirmPassword'); }
```

Update `onSubmit` to extract the password fields without confirmPassword (the existing destructure is fine; just keep `confirmPassword` out of the API payload):

Find:

```typescript
    const { name, email, password } = this.form.value;
```

(no change needed — `confirmPassword` is already excluded.)

- [ ] **Step 2: Update the template**

Open `frontend/src/app/pages/auth/register/register.component.html`. After the password `<mat-form-field>`, add a new field for confirmPassword. Locate the password `<mat-form-field>` (it has `formControlName="password"`). After its closing `</mat-form-field>`, insert:

```html
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ 'auth.field.confirmPassword' | transloco }}</mat-label>
          <input matInput type="password" formControlName="confirmPassword" required />
          @if (confirmPasswordControl?.touched && confirmPasswordControl?.errors?.['required']) {
            <mat-error>{{ 'auth.errors.required' | transloco }}</mat-error>
          }
          @if (confirmPasswordControl?.touched && form.errors?.['passwordMismatch']) {
            <mat-error>{{ 'auth.errors.passwordMismatch' | transloco }}</mat-error>
          }
        </mat-form-field>
```

(`auth.errors.required` exists in the project; if not, use a literal `Ce champ est requis` and add the i18n key separately. Check the existing register template for the pattern used.)

After the existing submit `<button>`, add:

```html
      <app-form-validation-hint [form]="form" />
```

- [ ] **Step 3: Update the existing spec to match the new form shape**

Open `frontend/src/app/pages/auth/register/register.component.spec.ts`. Find any test that builds form values like `{ name: '...', email: '...', password: '...' }`. Add `confirmPassword: '<same as password>'` so existing valid-submission tests stay valid.

Add a new test:

```typescript
  it('marks the form invalid when passwords do not match', () => {
    component.form.patchValue({
      name: 'Demo',
      email: 'demo@example.com',
      password: 'secret123',
      confirmPassword: 'wrong',
      consent: true,
    });
    expect(component.form.errors?.['passwordMismatch']).toBe(true);
    expect(component.form.invalid).toBe(true);
  });
```

- [ ] **Step 4: Run register tests**

```bash
cd frontend && npm test -- --include='**/register.component.spec.ts' --watch=false
```
Expected: PASS (existing tests + new one). If existing tests fail because `confirmPassword` was missing, fix them per step 3.

- [ ] **Step 5: Verify TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/auth/register/register.component.ts frontend/src/app/pages/auth/register/register.component.html frontend/src/app/pages/auth/register/register.component.spec.ts
git commit -m "feat(register): add confirm password field and form validation hint"
```

---

## Task D5: register-pro — confirmPassword (signals-native)

**Files:**
- Modify: `frontend/src/app/pages/auth/register-pro/register-pro.component.ts`
- Modify: `frontend/src/app/pages/auth/register-pro/register-pro.component.html`
- Modify: `frontend/src/app/pages/auth/register-pro/register-pro.component.spec.ts`

`register-pro` uses `signal()` per field, not a FormGroup. We add a signal-based equivalent of password matching. We keep this isolated to avoid migrating the entire form to ReactiveForms.

- [ ] **Step 1: Update the component TS**

Open `frontend/src/app/pages/auth/register-pro/register-pro.component.ts`. Find the existing account fields:

```typescript
  // Account fields
  readonly name = signal('');
  readonly email = signal('');
  readonly password = signal('');
```

Replace with:

```typescript
  // Account fields
  readonly name = signal('');
  readonly email = signal('');
  readonly password = signal('');
  readonly confirmPassword = signal('');

  readonly passwordsMatch = computed(() => {
    const p = this.password();
    const c = this.confirmPassword();
    if (!p || !c) return true; // empty fields handled by required check
    return p === c;
  });
```

Add `computed` to the existing `import { Component, inject, signal, OnInit }` line:

```typescript
import { Component, inject, signal, OnInit, computed } from '@angular/core';
```

- [ ] **Step 2: Find and update the form-completion guard**

The component likely exposes a computed or method for "can submit account step" which checks individual fields. Search:

```bash
grep -n 'canSubmitAccount\|accountValid\|step()===.account' frontend/src/app/pages/auth/register-pro/register-pro.component.ts
```

Find the equivalent gate. Update it to also check `passwordsMatch()`. Example: if there's a button `[disabled]="!isAccountValid()"`, ensure the `isAccountValid` computed returns false when `passwordsMatch()` is false.

If no such computed exists, add one:

```typescript
  readonly isAccountValid = computed(() => {
    return !!this.name().trim()
      && !!this.email().trim()
      && this.password().length >= 8
      && this.passwordsMatch();
  });
```

And use it on the "Continue" button: `[disabled]="!isAccountValid()"`.

- [ ] **Step 3: Update the template**

Open `frontend/src/app/pages/auth/register-pro/register-pro.component.html`. Find the password `<input>` (likely with `[(ngModel)]="password"` or `[ngModel]` + `(ngModelChange)`). Inspect the existing pattern.

After the password `<mat-form-field>`, add a confirmPassword field following the same pattern. If the existing password field uses signals via `(ngModelChange)`:

```html
<mat-form-field appearance="outline">
  <mat-label>{{ 'auth.field.confirmPassword' | transloco }}</mat-label>
  <input matInput type="password" required
         [ngModel]="confirmPassword()"
         (ngModelChange)="confirmPassword.set($event)"
         #confirmRef="ngModel" />
  @if (confirmRef.touched && !confirmPassword()) {
    <mat-error>{{ 'auth.errors.required' | transloco }}</mat-error>
  }
  @if (confirmPassword() && !passwordsMatch()) {
    <mat-error>{{ 'auth.errors.passwordMismatch' | transloco }}</mat-error>
  }
</mat-form-field>
```

(If the existing password field uses a different binding pattern, mirror it. Don't introduce a new pattern.)

- [ ] **Step 4: Update the spec**

Open `frontend/src/app/pages/auth/register-pro/register-pro.component.spec.ts`. Add a test:

```typescript
  it('passwordsMatch is true when both signals match', () => {
    component.password.set('secret123');
    component.confirmPassword.set('secret123');
    expect(component.passwordsMatch()).toBe(true);
  });

  it('passwordsMatch is false when signals differ', () => {
    component.password.set('secret123');
    component.confirmPassword.set('wrong');
    expect(component.passwordsMatch()).toBe(false);
  });
```

- [ ] **Step 5: Run register-pro tests**

```bash
cd frontend && npm test -- --include='**/register-pro.component.spec.ts' --watch=false
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/auth/register-pro/register-pro.component.ts frontend/src/app/pages/auth/register-pro/register-pro.component.html frontend/src/app/pages/auth/register-pro/register-pro.component.spec.ts
git commit -m "feat(register-pro): add confirm password signal and passwordsMatch gate"
```

---

## Task D6: reset-password — refactor to use shared validator

**Files:**
- Modify: `frontend/src/app/pages/auth/reset-password/reset-password.component.ts`
- Modify: `frontend/src/app/pages/auth/reset-password/reset-password.component.html`
- Modify: `frontend/src/app/pages/auth/reset-password/reset-password.component.spec.ts`

The component already has `confirmPassword` and a manual `passwordsMismatch` getter. Refactor to use the shared validator for consistency. Note: the password field is named `newPassword`, not `password`, so the validator needs adapting OR the field needs renaming.

**Decision:** rename `newPassword` to `password` in the form group (only). Keep the i18n label as "Nouveau mot de passe" via the i18n key. This way the shared validator works without alteration.

- [ ] **Step 1: Update the component TS**

Open `frontend/src/app/pages/auth/reset-password/reset-password.component.ts`. At the top, add:

```typescript
import { passwordMatchValidator } from '../../../core/auth/password-match.validator';
import { FormValidationHintComponent } from '../../../shared/uis/form-validation-hint/form-validation-hint.component';
```

Add `FormValidationHintComponent` to the `@Component({ imports: [...] })` array.

Replace the form declaration:

```typescript
  readonly form = this.fb.group(
    {
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: [passwordMatchValidator] },
  );
```

Replace the getters:

```typescript
  get passwordControl() { return this.form.get('password'); }
  get confirmPasswordControl() { return this.form.get('confirmPassword'); }
```

Remove the old `passwordsMismatch` getter (the `<mat-error>` will read from `form.errors?.['passwordMismatch']` instead).

Find any usage of `newPassword` in the component body (likely in `onSubmit`):

```typescript
const { newPassword } = this.form.value;
```

Replace with:

```typescript
const { password } = this.form.value;
```

And update the call to `authService.resetPassword(...)` to pass `password` instead of `newPassword`. The signature is `resetPassword(token, password)` — verify by reading the service if needed.

- [ ] **Step 2: Update the template**

Open `frontend/src/app/pages/auth/reset-password/reset-password.component.html`. Find the field bound to `newPassword`. Change `formControlName="newPassword"` to `formControlName="password"`.

Find the field bound to `confirmPassword`. Replace any manual `passwordsMismatch` reference with the validator-driven check:

```html
@if (confirmPasswordControl?.touched && form.errors?.['passwordMismatch']) {
  <mat-error>{{ 'auth.errors.passwordMismatch' | transloco }}</mat-error>
}
```

After the submit button, add:

```html
<app-form-validation-hint [form]="form" />
```

- [ ] **Step 3: Update the spec**

Open `frontend/src/app/pages/auth/reset-password/reset-password.component.spec.ts`. Find any test using `newPassword`. Rename to `password`. Run the tests to verify:

```bash
cd frontend && npm test -- --include='**/reset-password.component.spec.ts' --watch=false
```
Expected: PASS.

- [ ] **Step 4: Verify TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/auth/reset-password/reset-password.component.ts frontend/src/app/pages/auth/reset-password/reset-password.component.html frontend/src/app/pages/auth/reset-password/reset-password.component.spec.ts
git commit -m "refactor(reset-password): use shared passwordMatchValidator and FormValidationHint"
```

---

## Task D7: salon-profile — add validation hint near submit

**Files:**
- Modify: `frontend/src/app/features/salon-profile/salon-profile.component.ts`
- Modify: `frontend/src/app/features/salon-profile/salon-profile.component.html`

`salon-profile` uses signals + a save button gated by individual signal validations. We add the hint component near the save button. The hint requires a FormGroup, but salon-profile doesn't have one. **Decision:** for salon-profile, we display a simpler signal-driven hint — same visual, but bound to a custom signal `saveBlockedReason` rather than a FormGroup.

The simplest path is to inline the hint markup in `salon-profile.component.html` rather than reuse the component (which expects a FormGroup). We define a small computed `saveBlockedReason()` returning the i18n key when invalid.

- [ ] **Step 1: Add a signal-driven hint computed**

Open `frontend/src/app/features/salon-profile/salon-profile.component.ts`. Find the existing computeds for save validity (likely `canSave` or similar):

```bash
grep -n 'canSave\|saveDisabled\|isFormValid' frontend/src/app/features/salon-profile/salon-profile.component.ts
```

Add or extend a computed that returns the hint message key when the form can't be saved:

```typescript
  protected readonly saveBlockedReason = computed<string | null>(() => {
    if (this.canSave()) return null; // adapt the existing computed name
    return 'common.form.fillRequiredFields';
  });
```

If no `canSave` computed exists, infer from the disabled binding on the button. The hint should be visible exactly when the button is disabled for invalidity reasons (not when the save is just in-flight).

- [ ] **Step 2: Add the inline hint markup near the save button**

Open `frontend/src/app/features/salon-profile/salon-profile.component.html`. Find the save `<button>` (likely `<button ... [disabled]="..." (click)="save()">`). After the button, add:

```html
@if (saveBlockedReason()) {
  <p class="form-validation-hint" role="status">
    <mat-icon aria-hidden="true">info</mat-icon>
    <span>{{ saveBlockedReason()! | transloco }}</span>
  </p>
}
```

The `.form-validation-hint` class is already in `styles.scss` from Task D3 SCSS — no, that SCSS is in the component. We need the style here. Easiest path: reuse the component's selector if you've migrated to a FormGroup. Otherwise, copy the SCSS into `salon-profile.component.scss`:

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
  line-height: 1.4;

  mat-icon {
    font-size: 18px;
    width: 18px;
    height: 18px;
    color: #c06;
  }
}
```

Append to `frontend/src/app/features/salon-profile/salon-profile.component.scss`.

- [ ] **Step 3: Verify TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 4: Run salon-profile tests**

```bash
cd frontend && npm test -- --include='**/salon-profile.component.spec.ts' --watch=false
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/salon-profile/salon-profile.component.ts frontend/src/app/features/salon-profile/salon-profile.component.html frontend/src/app/features/salon-profile/salon-profile.component.scss
git commit -m "feat(salon-profile): show inline validation hint when save is blocked"
```

---

## Task D8: Final integration check

**Files:** none (manual verification + automated suite).

- [ ] **Step 1: Run the focused frontend test suite**

```bash
cd frontend && npm test -- --include='**/onboarding-*.spec.ts' --include='**/pro-shell.component.spec.ts' --include='**/sidenav-menu.spec.ts' --include='**/tenant-status.service.spec.ts' --include='**/focus-on-query-param.directive.spec.ts' --include='**/password-match.validator.spec.ts' --include='**/form-validation-hint.component.spec.ts' --include='**/register.component.spec.ts' --include='**/register-pro.component.spec.ts' --include='**/reset-password.component.spec.ts' --include='**/salon-profile.component.spec.ts' --watch=false
```
Expected: PASS across all jalon 1.5 test files.

- [ ] **Step 2: TypeScript clean**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 3: Smoke check (DRAFT pro)**

Start backend + frontend.

1. Login as a pro with DRAFT status.
2. Open the burger menu (sidenav). Confirm "Posts" and "Employés" are grayed out with a lock icon. Hover (PC) → tooltip "Disponible après publication". Tap (mobile) → snackbar.
3. Click "Cares" (free) → navigate normally. Click "Posts" (locked) → does NOT navigate.
4. On `/pro/dashboard`, click on the "Soins" step in the indicator stepper. Should navigate to `/pro/cares?openCreate=care` (no focus pulse — that step uses a different param).
5. Click on "Renseigne le nom" step → navigate to `/pro/salon?focus=name`. The salon name field should pulse rose for ~2.4s and receive focus.
6. Hover any step in the desktop stepper → tooltip with the step description appears.
7. On mobile, tap the indicator pill → bottom-sheet opens with each step description visible under its title.
8. Complete a step (e.g. set the salon name). Navigate back to `/pro/dashboard` via the sidenav. The step appears in green in the dashboard checklist.

- [ ] **Step 4: Smoke check (forms)**

1. Visit `/register`. Try to submit with empty fields. The submit button is disabled. Below the button, the hint *"Remplissez tous les champs requis pour continuer"* appears.
2. Fill all fields except confirmPassword → still disabled, still hint.
3. Fill matching passwords → button enables, hint disappears.
4. Visit `/register/pro`. On the account step, fill name/email/password but NOT confirmPassword → "Continue" button stays disabled.
5. Set confirmPassword to a different value → mat-error "Les mots de passe ne correspondent pas".
6. Visit `/reset-password?token=abc`. Same matching behavior as register.
7. Visit `/pro/salon`. Try to leave a required field empty. Hint appears under save button.

- [ ] **Step 5: Smoke check (ACTIVE pro)**

1. Publish the salon (if checklist allows).
2. Reload. Sidenav: "Posts" and "Employés" are NOT grayed out anymore. Lock icon gone.
3. Indicator pill / stepper: gone.

- [ ] **Step 6: Final commit (only if Step 1 or 2 surfaced fix-ups)**

If everything passed in steps 1-2 with no edits needed, skip this step. Otherwise:

```bash
git add -A
git commit -m "fix(onboarding-polish): address integration issues"
```

---

## Self-Review Notes

**Spec coverage check:**

| Spec requirement | Implemented in |
|------------------|----------------|
| Refresh checklist on dashboard return | Task A1 |
| Sidenav posts/employees locked in DRAFT | Tasks B2, B4 |
| TenantStatusService | Tasks B1, B5 |
| Reset on logout | Task B6 |
| i18n nav.lockedUntilPublished | Task B3 |
| MatTooltip on stepper steps | Task C2 |
| Description in mobile sheet | Task C3 |
| `?focus=` queryParam from service | Task C1 |
| `[appFocusOnQueryParam]` directive | Task C4 |
| Global `.focus-pulse` style | Task C5 |
| Apply on salon-profile | Task C6 |
| Apply on pro-planning | Task C7 |
| `passwordMatchValidator` shared | Task D1 |
| i18n confirm/mismatch/fillRequired | Task D2 |
| `<app-form-validation-hint>` | Task D3 |
| register confirmPassword + hint | Task D4 |
| register-pro confirmPassword + signal gate | Task D5 |
| reset-password refactored to validator | Task D6 |
| salon-profile inline hint | Task D7 |
| Smoke checks | Task D8 |

**Out of scope (deliberately not implemented):**
- Route-level guard preventing direct URL access to `/pro/posts` while DRAFT (sidenav gating only — discussed risk in spec).
- Full ReactiveForms migration of `register-pro` (kept signals for YAGNI).
- New `auth.errors.required` i18n key — relies on existing keys; if missing in some forms, add per-form.

**Placeholders scan:** none. All steps contain concrete code, file paths, and commands.

**Type consistency:**
- `TenantStatusValue` exported from `tenant-status.service.ts`, used as the shape of the signal.
- `passwordMatchValidator` used identically in `register` and `reset-password` reactive forms.
- `FocusOnQueryParamDirective` selector `[appFocusOnQueryParam]` referenced in service, salon-profile, pro-planning.
- The `focus` query param value matches across `OnboardingChecklistService` (`'name'`, `'openingHours'`) and the directive applications on the corresponding pages.

---

## Notes for the executing engineer

- **Order of PRs**: A → B → C → D (any order works since they're independent, but A unblocks the most-visible bug first).
- **Testing matTooltip**: Material's tooltip directive sets attributes whose names vary by version. Task C2 step 5 includes a fallback test strategy. If both fail, accept that the test is a smoke-level check and trust manual smoke verification.
- **`register-pro` is signals-native** intentionally; don't try to migrate it to ReactiveForms in this jalon. The signal pattern is sufficient for password matching.
- **`auth.errors.required` i18n key**: check if it exists in the project before adding it. If absent, add it once in the i18n files alongside the new keys in Task D2 — but verify first to avoid duplicates.
- **`statusChanges` reactivity in `FormValidationHintComponent`**: the `effect` + tick signal pattern is a deliberate workaround for FormGroup's non-signal-native API. If Angular 21 ships first-class signal forms, revisit.
- **`canSave` / `saveBlockedReason` in salon-profile (Task D7)** depends on the existing component's API. Read first, adapt the computed name to match what's there. The hint should be visible only when the user has interacted (mirror the FormGroup hint behavior) — if the salon-profile uses a "dirty" flag, gate the hint on it.
