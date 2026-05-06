# Onboarding Indicator (Jalon 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display a persistent onboarding progress indicator on every `/pro/*` page while the salon is in `DRAFT` — pill on mobile (< 768px) opening a bottom-sheet, full stepper on desktop with clickable steps, both sharing the same `DashboardStore` and the same step-computation service.

**Architecture:** Introduce a `ProShellComponent` route wrapper around `/pro` that hoists `DashboardStore` to the routing parent so every pro page can read `readiness()`. The wrapper renders `<app-onboarding-indicator>` which auto-hides when `!isDraft()`. A new `OnboardingChecklistService` centralizes the step list/progress logic so both the new indicator and the existing dashboard checklist consume the same source of truth.

**Tech Stack:** Angular 20 (standalone, signals, zoneless), NgRx SignalStore, Angular Material (`MatBottomSheet`), Tailwind, Transloco i18n. Tests with Karma/Jasmine.

**Spec reference:** `docs/superpowers/specs/2026-05-06-vitrine-preview-onboarding-pc-design.md` — Jalon 1 section.

---

## File Structure

**New files:**

| Path | Responsibility |
|------|----------------|
| `frontend/src/app/features/onboarding/onboarding-checklist.service.ts` | Pure logic: build steps from `TenantReadiness`, compute progress, find next step. Single source of truth used by dashboard + indicator. |
| `frontend/src/app/features/onboarding/onboarding-checklist.service.spec.ts` | Unit tests for the service. |
| `frontend/src/app/features/onboarding/onboarding-step.model.ts` | Type definitions: `OnboardingStep`, `OnboardingProgress`. |
| `frontend/src/app/core/utils/breakpoint.signal.ts` | `isDesktop` signal exposing `matchMedia('(min-width: 768px)')`, SSR-safe (defaults `false`). |
| `frontend/src/app/core/utils/breakpoint.signal.spec.ts` | Unit tests. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.ts` | Main component: renders pill (mobile) or stepper (desktop) based on `isDesktop()`. Auto-hides when `!isDraft()`. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.html` | Template with `@if` switch. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.scss` | Styles for both modes. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.spec.ts` | Component test. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.ts` | Bottom-sheet content shown when pill is tapped (mobile). |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.html` | Sheet template. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.scss` | Sheet styles. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.spec.ts` | Sheet test. |
| `frontend/src/app/pages/pro/pro-shell.component.ts` | Route wrapper, hoists `DashboardStore`, mounts the indicator + `<router-outlet>`. |
| `frontend/src/app/pages/pro/pro-shell.component.html` | Wrapper template. |
| `frontend/src/app/pages/pro/pro-shell.component.scss` | Wrapper styles. |
| `frontend/src/app/pages/pro/pro-shell.component.spec.ts` | Wrapper test. |

**Modified files:**

| Path | Change |
|------|--------|
| `frontend/src/app/app.routes.ts:43-118` | Wrap `/pro` children under `ProShellComponent` via `loadComponent`. |
| `frontend/src/app/pages/pro/pro-dashboard.component.ts:49,55,83-111` | Drop local `providers: [DashboardStore]`, replace inline checklist computed with calls to `OnboardingChecklistService`. |
| `frontend/src/app/pages/pro/pro-dashboard.component.html:75-110` | Use service-derived signals (no template change to logic; only ensures TS refactor compiles). |
| `frontend/public/i18n/fr.json` | Add `pro.onboarding.indicator.*` and `pro.onboarding.sheet.*` blocks. |
| `frontend/public/i18n/en.json` | Same. |

---

## Conventions used by this codebase

Read these once before coding:

- **Standalone components**, no `NgModule`. `imports` array in `@Component`.
- **Signals everywhere** — `signal()`, `computed()`, `effect()`. `inject()` instead of constructor injection.
- **Control flow** uses `@if` / `@for` / `@switch` (not `*ngIf` / `*ngFor`).
- **i18n** via Transloco: `{{ 'key.path' | transloco }}` or `{{ 'key' | transloco: { var: value } }}`. **Both** `fr.json` (default) and `en.json` must be updated together.
- **Tests** use functional providers: `provideHttpClient()`, `provideHttpClientTesting()`, `provideRouter([])`, `provideNoopAnimations()`, `provideZonelessChangeDetection()`, `TranslocoTestingModule`.
- **NgRx SignalStore** is provided at the component level; tests instantiate the store inside `TestBed.runInInjectionContext` or by providing the host component.
- **Conventional Commits**: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.
- **Material density**: not used here. Standard buttons.
- **Tailwind**: utility classes preferred in templates; SCSS for component-specific layout.
- **Routes**: lazy-loaded via `loadComponent: () => import(...)`. Existing pattern in `app.routes.ts`.

---

## Task 1: Type definitions for onboarding steps

**Files:**
- Create: `frontend/src/app/features/onboarding/onboarding-step.model.ts`

- [ ] **Step 1: Write the type definitions**

Create `frontend/src/app/features/onboarding/onboarding-step.model.ts`:

```typescript
/** Identifier matching i18n keys under `pro.dashboard.checklist.*` and `pro.onboarding.*`. */
export type OnboardingStepKey = 'name' | 'cares' | 'openingHours';

/** A single step in the onboarding checklist. */
export interface OnboardingStep {
  /** Stable key, also used as i18n suffix. */
  readonly key: OnboardingStepKey;
  /** True when the underlying readiness flag is satisfied. */
  readonly done: boolean;
  /** Router link the user should follow to act on this step. */
  readonly link: string;
  /** Optional query params to pass when navigating (e.g. `{ openCreate: 'care' }`). */
  readonly queryParams: Record<string, string> | null;
}

/** Aggregate progress derived from a list of steps. */
export interface OnboardingProgress {
  /** Number of steps with `done === true`. */
  readonly done: number;
  /** Total steps. */
  readonly total: number;
  /** First step with `done === false`, or `null` when all are done. */
  readonly nextKey: OnboardingStepKey | null;
  /** Integer 0-100. */
  readonly percent: number;
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/features/onboarding/onboarding-step.model.ts
git commit -m "feat(onboarding): add OnboardingStep and OnboardingProgress types"
```

---

## Task 2: OnboardingChecklistService — buildSteps

**Files:**
- Create: `frontend/src/app/features/onboarding/onboarding-checklist.service.ts`
- Create: `frontend/src/app/features/onboarding/onboarding-checklist.service.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/features/onboarding/onboarding-checklist.service.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { OnboardingChecklistService } from './onboarding-checklist.service';
import { TenantReadiness } from '../dashboard/models/dashboard.model';

function readiness(overrides: Partial<TenantReadiness> = {}): TenantReadiness {
  return {
    slug: 'demo',
    name: false,
    hasCategory: false,
    hasActiveCare: false,
    hasOpeningHours: false,
    canPublish: false,
    status: 'DRAFT',
    ...overrides,
  };
}

describe('OnboardingChecklistService', () => {
  let service: OnboardingChecklistService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
    });
    service = TestBed.inject(OnboardingChecklistService);
  });

  describe('buildSteps', () => {
    it('returns empty when readiness is null', () => {
      expect(service.buildSteps(null)).toEqual([]);
    });

    it('returns three steps with correct keys and links', () => {
      const steps = service.buildSteps(readiness());
      expect(steps.map((s) => s.key)).toEqual(['name', 'cares', 'openingHours']);
      expect(steps.map((s) => s.link)).toEqual(['/pro/salon', '/pro/cares', '/pro/planning']);
    });

    it('marks each step done according to readiness flags', () => {
      const steps = service.buildSteps(
        readiness({ name: true, hasActiveCare: false, hasOpeningHours: true })
      );
      expect(steps.find((s) => s.key === 'name')?.done).toBe(true);
      expect(steps.find((s) => s.key === 'cares')?.done).toBe(false);
      expect(steps.find((s) => s.key === 'openingHours')?.done).toBe(true);
    });

    it('passes openCreate=care queryParam when cares step is undone', () => {
      const steps = service.buildSteps(readiness({ hasActiveCare: false }));
      const cares = steps.find((s) => s.key === 'cares');
      expect(cares?.queryParams).toEqual({ openCreate: 'care' });
    });

    it('passes null queryParams when cares step is done', () => {
      const steps = service.buildSteps(readiness({ hasActiveCare: true }));
      const cares = steps.find((s) => s.key === 'cares');
      expect(cares?.queryParams).toBeNull();
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run from `frontend/`:
```bash
npm test -- --include='**/onboarding-checklist.service.spec.ts' --watch=false
```
Expected: FAIL — "Cannot find module './onboarding-checklist.service'".

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/app/features/onboarding/onboarding-checklist.service.ts`:

```typescript
import { Injectable } from '@angular/core';
import { TenantReadiness } from '../dashboard/models/dashboard.model';
import { OnboardingStep } from './onboarding-step.model';

@Injectable({ providedIn: 'root' })
export class OnboardingChecklistService {
  buildSteps(readiness: TenantReadiness | null): OnboardingStep[] {
    if (!readiness) return [];
    return [
      {
        key: 'name',
        done: readiness.name,
        link: '/pro/salon',
        queryParams: null,
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
        queryParams: null,
      },
    ];
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --include='**/onboarding-checklist.service.spec.ts' --watch=false
```
Expected: PASS — 5 specs.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/onboarding/onboarding-checklist.service.ts frontend/src/app/features/onboarding/onboarding-checklist.service.spec.ts
git commit -m "feat(onboarding): add OnboardingChecklistService.buildSteps"
```

---

## Task 3: OnboardingChecklistService — computeProgress

**Files:**
- Modify: `frontend/src/app/features/onboarding/onboarding-checklist.service.ts`
- Modify: `frontend/src/app/features/onboarding/onboarding-checklist.service.spec.ts`

- [ ] **Step 1: Write the failing test**

Append to `onboarding-checklist.service.spec.ts` inside the `describe('OnboardingChecklistService', ...)` block, after the `buildSteps` describe:

```typescript
  describe('computeProgress', () => {
    it('returns zeros when steps is empty', () => {
      expect(service.computeProgress([])).toEqual({
        done: 0,
        total: 0,
        nextKey: null,
        percent: 0,
      });
    });

    it('counts done steps and identifies the first undone step', () => {
      const steps = service.buildSteps(
        readiness({ name: true, hasActiveCare: false, hasOpeningHours: false })
      );
      expect(service.computeProgress(steps)).toEqual({
        done: 1,
        total: 3,
        nextKey: 'cares',
        percent: 33,
      });
    });

    it('returns null nextKey when all steps are done', () => {
      const steps = service.buildSteps(
        readiness({ name: true, hasActiveCare: true, hasOpeningHours: true })
      );
      expect(service.computeProgress(steps)).toEqual({
        done: 3,
        total: 3,
        nextKey: null,
        percent: 100,
      });
    });

    it('rounds percent to nearest integer', () => {
      const steps = service.buildSteps(
        readiness({ name: false, hasActiveCare: true, hasOpeningHours: false })
      );
      // 1/3 = 33.33 → 33
      expect(service.computeProgress(steps).percent).toBe(33);
    });
  });
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm test -- --include='**/onboarding-checklist.service.spec.ts' --watch=false
```
Expected: FAIL — "service.computeProgress is not a function".

- [ ] **Step 3: Add the method**

In `onboarding-checklist.service.ts`, add inside the class:

```typescript
import { OnboardingProgress, OnboardingStep } from './onboarding-step.model';
```

(Update the existing import to include `OnboardingProgress`.)

Then add the method:

```typescript
  computeProgress(steps: OnboardingStep[]): OnboardingProgress {
    const total = steps.length;
    const done = steps.filter((s) => s.done).length;
    const next = steps.find((s) => !s.done);
    const percent = total === 0 ? 0 : Math.round((done / total) * 100);
    return {
      done,
      total,
      nextKey: next ? next.key : null,
      percent,
    };
  }
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --include='**/onboarding-checklist.service.spec.ts' --watch=false
```
Expected: PASS — 9 specs total.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/onboarding/onboarding-checklist.service.ts frontend/src/app/features/onboarding/onboarding-checklist.service.spec.ts
git commit -m "feat(onboarding): add OnboardingChecklistService.computeProgress"
```

---

## Task 4: Refactor pro-dashboard to use OnboardingChecklistService

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-dashboard.component.ts`

This task removes duplicated step-building logic from `pro-dashboard.component.ts`. No template change.

- [ ] **Step 1: Verify existing dashboard tests pass first (baseline)**

```bash
npm test -- --include='**/pro-dashboard.component.spec.ts' --watch=false
```
Expected: PASS (or note any pre-existing failures — they are not your concern).

- [ ] **Step 2: Update pro-dashboard.component.ts imports**

In `frontend/src/app/pages/pro/pro-dashboard.component.ts`, find the imports block at the top and add:

```typescript
import { OnboardingChecklistService } from '../../features/onboarding/onboarding-checklist.service';
```

- [ ] **Step 3: Inject the service**

In the class body of `ProDashboardComponent`, locate the existing `inject(...)` calls (around line 60) and add:

```typescript
  private readonly checklistService = inject(OnboardingChecklistService);
```

- [ ] **Step 4: Replace the inline checklist computed**

In `pro-dashboard.component.ts`, **replace the block at lines 83-111** (the four `checklistSteps`, `checklistDone`, `checklistTotal`, `nextStepKey`, `checklistProgressPercent` definitions) with:

```typescript
  readonly checklistSteps = computed(() =>
    this.checklistService.buildSteps(this.store.readiness())
  );

  private readonly checklistProgress = computed(() =>
    this.checklistService.computeProgress(this.checklistSteps())
  );

  readonly checklistDone = computed(() => this.checklistProgress().done);

  readonly checklistTotal = computed(() => this.checklistProgress().total);

  readonly nextStepKey = computed(() => this.checklistProgress().nextKey);

  readonly checklistProgressPercent = computed(() => this.checklistProgress().percent);
```

The template (`pro-dashboard.component.html`) does not change because the public signal names are identical.

- [ ] **Step 5: Run dashboard tests**

```bash
npm test -- --include='**/pro-dashboard.component.spec.ts' --watch=false
```
Expected: PASS (same set as before).

- [ ] **Step 6: Verify build still works**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/pages/pro/pro-dashboard.component.ts
git commit -m "refactor(pro-dashboard): use OnboardingChecklistService for steps and progress"
```

---

## Task 5: Breakpoint signal (isDesktop)

**Files:**
- Create: `frontend/src/app/core/utils/breakpoint.signal.ts`
- Create: `frontend/src/app/core/utils/breakpoint.signal.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/core/utils/breakpoint.signal.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID, provideZonelessChangeDetection } from '@angular/core';
import { isDesktopSignal } from './breakpoint.signal';

describe('isDesktopSignal', () => {
  it('returns false in non-browser (SSR) context', () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PLATFORM_ID, useValue: 'server' },
      ],
    });
    const signal = TestBed.runInInjectionContext(() => isDesktopSignal());
    expect(signal()).toBe(false);
  });

  it('reflects matchMedia(min-width: 768px) in browser context', () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PLATFORM_ID, useValue: 'browser' },
      ],
    });

    // Stub matchMedia to report desktop.
    const original = window.matchMedia;
    let listener: ((e: MediaQueryListEvent) => void) | null = null;
    window.matchMedia = ((query: string): MediaQueryList => ({
      matches: query === '(min-width: 768px)',
      media: query,
      onchange: null,
      addEventListener: (_: string, cb: any) => { listener = cb; },
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    } as unknown as MediaQueryList)) as typeof window.matchMedia;

    try {
      const signal = TestBed.runInInjectionContext(() => isDesktopSignal());
      expect(signal()).toBe(true);
      // Simulate viewport shrink.
      listener?.({ matches: false } as MediaQueryListEvent);
      expect(signal()).toBe(false);
    } finally {
      window.matchMedia = original;
    }
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm test -- --include='**/breakpoint.signal.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Write the implementation**

Create `frontend/src/app/core/utils/breakpoint.signal.ts`:

```typescript
import { DestroyRef, PLATFORM_ID, Signal, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

const DESKTOP_QUERY = '(min-width: 768px)';

/**
 * Reactive boolean signal that tracks whether the viewport is desktop-sized.
 *
 * Returns a signal that is `false` during SSR and on viewports < 768px,
 * `true` otherwise. The signal is updated via `matchMedia` change events
 * and the listener is cleaned up automatically when the injection context
 * is destroyed.
 *
 * Must be called inside an injection context (component, directive, service).
 */
export function isDesktopSignal(): Signal<boolean> {
  const platformId = inject(PLATFORM_ID);
  const destroyRef = inject(DestroyRef);

  if (!isPlatformBrowser(platformId)) {
    return signal(false).asReadonly();
  }

  const mql = window.matchMedia(DESKTOP_QUERY);
  const result = signal(mql.matches);
  const handler = (e: MediaQueryListEvent) => result.set(e.matches);
  mql.addEventListener('change', handler);
  destroyRef.onDestroy(() => mql.removeEventListener('change', handler));

  return result.asReadonly();
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --include='**/breakpoint.signal.spec.ts' --watch=false
```
Expected: PASS — 2 specs.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/utils/breakpoint.signal.ts frontend/src/app/core/utils/breakpoint.signal.spec.ts
git commit -m "feat(core): add isDesktopSignal helper for responsive logic"
```

---

## Task 6: i18n keys for the indicator

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add FR keys**

Open `frontend/public/i18n/fr.json`. Locate the `"pro"` object (search `"pro": {`). Inside it, find the existing `"dashboard"` block and add a new sibling block `"onboarding"` next to it (same nesting level as `"dashboard"`):

```json
    "onboarding": {
      "indicator": {
        "label": "Configuration",
        "next": "Suivant : {{step}}",
        "ready": "Vitrine prête — publier",
        "preview": "Aperçu",
        "ariaProgress": "Progression de la configuration : étape {{done}} sur {{total}}"
      },
      "sheet": {
        "title": "Votre configuration",
        "preview": "Aperçu de la vitrine",
        "backToDashboard": "Retour au tableau de bord",
        "publish": "Publier ma vitrine"
      }
    },
```

(Make sure to add a comma after the previous sibling block so the JSON stays valid.)

- [ ] **Step 2: Add EN keys**

Open `frontend/public/i18n/en.json`. Locate the `"pro"` object and add the matching `"onboarding"` block:

```json
    "onboarding": {
      "indicator": {
        "label": "Setup",
        "next": "Next: {{step}}",
        "ready": "Storefront ready — publish it",
        "preview": "Preview",
        "ariaProgress": "Setup progress: step {{done}} of {{total}}"
      },
      "sheet": {
        "title": "Your setup",
        "preview": "Preview the storefront",
        "backToDashboard": "Back to dashboard",
        "publish": "Publish my storefront"
      }
    },
```

- [ ] **Step 3: Validate JSON**

```bash
python3 -m json.tool frontend/public/i18n/fr.json > /dev/null && echo "FR ok"
python3 -m json.tool frontend/public/i18n/en.json > /dev/null && echo "EN ok"
```
Expected: `FR ok` and `EN ok`.

- [ ] **Step 4: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat(i18n): add pro.onboarding.indicator and sheet keys (FR/EN)"
```

---

## Task 7: OnboardingIndicatorSheetComponent (mobile bottom-sheet content)

**Files:**
- Create: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.ts`
- Create: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.html`
- Create: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.scss`
- Create: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `onboarding-indicator-sheet.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { OnboardingIndicatorSheetComponent, OnboardingSheetData } from './onboarding-indicator-sheet.component';

describe('OnboardingIndicatorSheetComponent', () => {
  let fixture: ComponentFixture<OnboardingIndicatorSheetComponent>;
  let component: OnboardingIndicatorSheetComponent;
  let dialogRef: jasmine.SpyObj<MatDialogRef<OnboardingIndicatorSheetComponent>>;

  function setup(data: OnboardingSheetData) {
    dialogRef = jasmine.createSpyObj<MatDialogRef<OnboardingIndicatorSheetComponent>>(
      'MatDialogRef',
      ['close']
    );
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
      imports: [
        OnboardingIndicatorSheetComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(OnboardingIndicatorSheetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('exposes steps, progress, and canPublish from injected data', () => {
    setup({
      steps: [
        { key: 'name', done: true, link: '/pro/salon', queryParams: null },
        { key: 'cares', done: false, link: '/pro/cares', queryParams: null },
        { key: 'openingHours', done: false, link: '/pro/planning', queryParams: null },
      ],
      progress: { done: 1, total: 3, nextKey: 'cares', percent: 33 },
      canPublish: false,
      slug: 'demo',
    });
    expect(component.steps.length).toBe(3);
    expect(component.progress.done).toBe(1);
    expect(component.canPublish).toBe(false);
  });

  it('closes the sheet with action="step" and the step key when a step is selected', () => {
    setup({
      steps: [
        { key: 'name', done: false, link: '/pro/salon', queryParams: null },
      ],
      progress: { done: 0, total: 1, nextKey: 'name', percent: 0 },
      canPublish: false,
      slug: 'demo',
    });
    component.onStep('name');
    expect(dialogRef.close).toHaveBeenCalledWith({ action: 'step', stepKey: 'name' });
  });

  it('closes the sheet with action="preview" when preview is selected', () => {
    setup({
      steps: [],
      progress: { done: 0, total: 0, nextKey: null, percent: 0 },
      canPublish: false,
      slug: 'demo',
    });
    component.onPreview();
    expect(dialogRef.close).toHaveBeenCalledWith({ action: 'preview' });
  });

  it('closes the sheet with action="publish" when publish is selected', () => {
    setup({
      steps: [],
      progress: { done: 3, total: 3, nextKey: null, percent: 100 },
      canPublish: true,
      slug: 'demo',
    });
    component.onPublish();
    expect(dialogRef.close).toHaveBeenCalledWith({ action: 'publish' });
  });

  it('closes the sheet with action="dashboard" when back-to-dashboard is selected', () => {
    setup({
      steps: [],
      progress: { done: 0, total: 0, nextKey: null, percent: 0 },
      canPublish: false,
      slug: 'demo',
    });
    component.onDashboard();
    expect(dialogRef.close).toHaveBeenCalledWith({ action: 'dashboard' });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm test -- --include='**/onboarding-indicator-sheet.component.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Create the component TS file**

Create `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.ts`:

```typescript
import { Component, inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { OnboardingProgress, OnboardingStep, OnboardingStepKey } from '../../../features/onboarding/onboarding-step.model';

export interface OnboardingSheetData {
  readonly steps: readonly OnboardingStep[];
  readonly progress: OnboardingProgress;
  readonly canPublish: boolean;
  readonly slug: string;
}

export type OnboardingSheetResult =
  | { action: 'step'; stepKey: OnboardingStepKey }
  | { action: 'preview' }
  | { action: 'publish' }
  | { action: 'dashboard' };

@Component({
  selector: 'app-onboarding-indicator-sheet',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, TranslocoPipe],
  templateUrl: './onboarding-indicator-sheet.component.html',
  styleUrl: './onboarding-indicator-sheet.component.scss',
})
export class OnboardingIndicatorSheetComponent {
  private readonly dialogRef = inject(MatDialogRef<OnboardingIndicatorSheetComponent>);
  private readonly data = inject<OnboardingSheetData>(MAT_DIALOG_DATA);

  readonly steps = this.data.steps;
  readonly progress = this.data.progress;
  readonly canPublish = this.data.canPublish;
  readonly slug = this.data.slug;

  onStep(stepKey: OnboardingStepKey): void {
    this.dialogRef.close({ action: 'step', stepKey });
  }

  onPreview(): void {
    this.dialogRef.close({ action: 'preview' });
  }

  onPublish(): void {
    this.dialogRef.close({ action: 'publish' });
  }

  onDashboard(): void {
    this.dialogRef.close({ action: 'dashboard' });
  }
}
```

- [ ] **Step 4: Create the component template**

Create `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.html`:

```html
<div class="sheet-container">
  <header class="sheet-header">
    <h2 class="sheet-title">{{ 'pro.onboarding.sheet.title' | transloco }}</h2>
    <div class="sheet-progress">
      <span class="sheet-progress-label">
        {{ 'pro.dashboard.checklist.progress' | transloco: { done: progress.done, total: progress.total } }}
      </span>
      <div class="sheet-progress-track">
        <div class="sheet-progress-fill" [style.width.%]="progress.percent"></div>
      </div>
    </div>
  </header>

  @if (canPublish) {
    <button
      type="button"
      class="sheet-publish"
      (click)="onPublish()"
      data-testid="sheet-publish"
    >
      <mat-icon>rocket_launch</mat-icon>
      <span>{{ 'pro.onboarding.sheet.publish' | transloco }}</span>
    </button>
  }

  <ul class="sheet-steps">
    @for (step of steps; track step.key) {
      <li>
        <button
          type="button"
          class="sheet-step"
          [class.is-done]="step.done"
          [class.is-next]="!step.done && progress.nextKey === step.key"
          (click)="onStep(step.key)"
          [attr.data-testid]="'sheet-step-' + step.key"
        >
          <mat-icon class="sheet-step-icon">
            {{ step.done ? 'check_circle' : 'radio_button_unchecked' }}
          </mat-icon>
          <span class="sheet-step-label">
            {{ 'pro.dashboard.checklist.' + step.key | transloco }}
          </span>
          <mat-icon class="sheet-step-chevron">chevron_right</mat-icon>
        </button>
      </li>
    }
  </ul>

  <footer class="sheet-footer">
    <button
      type="button"
      class="sheet-action sheet-action-primary"
      (click)="onPreview()"
      data-testid="sheet-preview"
    >
      <mat-icon>visibility</mat-icon>
      <span>{{ 'pro.onboarding.sheet.preview' | transloco }}</span>
    </button>
    <button
      type="button"
      class="sheet-action sheet-action-secondary"
      (click)="onDashboard()"
      data-testid="sheet-dashboard"
    >
      {{ 'pro.onboarding.sheet.backToDashboard' | transloco }}
    </button>
  </footer>
</div>
```

- [ ] **Step 5: Create the component styles**

Create `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.scss`:

```scss
.sheet-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px 20px 24px;
}

.sheet-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sheet-title {
  font-size: 18px;
  font-weight: 500;
  margin: 0;
}

.sheet-progress {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sheet-progress-label {
  font-size: 12px;
  color: var(--mat-sys-on-surface-variant, #666);
}

.sheet-progress-track {
  height: 4px;
  background: rgba(0, 0, 0, 0.06);
  border-radius: 2px;
  overflow: hidden;
}

.sheet-progress-fill {
  height: 100%;
  background: var(--mat-sys-primary, #c06);
  transition: width 250ms ease;
}

.sheet-publish {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: var(--mat-sys-primary, #c06);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.sheet-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sheet-step {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  text-align: left;
}

.sheet-step.is-next {
  background: rgba(192, 0, 102, 0.06);
  border-color: rgba(192, 0, 102, 0.2);
}

.sheet-step.is-done .sheet-step-icon {
  color: var(--mat-sys-primary, #c06);
}

.sheet-step-label {
  flex: 1;
  font-size: 14px;
}

.sheet-step-chevron {
  color: rgba(0, 0, 0, 0.3);
}

.sheet-footer {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.sheet-action {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid transparent;
}

.sheet-action-primary {
  background: rgba(192, 0, 102, 0.08);
  color: var(--mat-sys-primary, #c06);
  border-color: rgba(192, 0, 102, 0.2);
}

.sheet-action-secondary {
  background: transparent;
  color: var(--mat-sys-on-surface-variant, #666);
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
npm test -- --include='**/onboarding-indicator-sheet.component.spec.ts' --watch=false
```
Expected: PASS — 5 specs.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.ts frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.html frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.scss frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator-sheet.component.spec.ts
git commit -m "feat(onboarding-indicator): add bottom-sheet content component"
```

---

## Task 8: OnboardingIndicatorComponent — auto-hide and pill rendering (mobile)

**Files:**
- Create: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.ts`
- Create: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.html`
- Create: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.scss`
- Create: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal, WritableSignal } from '@angular/core';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { patchState } from '@ngrx/signals';
import { DashboardStore } from '../../../features/dashboard/store/dashboard.store';
import { TenantReadiness } from '../../../features/dashboard/models/dashboard.model';
import { OnboardingIndicatorComponent } from './onboarding-indicator.component';
import { ONBOARDING_BREAKPOINT } from './breakpoint.token';

function readiness(overrides: Partial<TenantReadiness> = {}): TenantReadiness {
  return {
    slug: 'demo',
    name: false,
    hasCategory: false,
    hasActiveCare: false,
    hasOpeningHours: false,
    canPublish: false,
    status: 'DRAFT',
    ...overrides,
  };
}

describe('OnboardingIndicatorComponent', () => {
  let fixture: ComponentFixture<OnboardingIndicatorComponent>;
  let store: InstanceType<typeof DashboardStore>;
  let isDesktop: WritableSignal<boolean>;

  function setup(initialDesktop: boolean) {
    isDesktop = signal(initialDesktop);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        DashboardStore,
        { provide: ONBOARDING_BREAKPOINT, useValue: () => isDesktop },
      ],
      imports: [
        OnboardingIndicatorComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    store = TestBed.inject(DashboardStore);
    fixture = TestBed.createComponent(OnboardingIndicatorComponent);
    fixture.detectChanges();
  }

  it('renders nothing when readiness is null', () => {
    setup(false);
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="onboarding-pill"]')).toBeNull();
    expect(root.querySelector('[data-testid="onboarding-stepper"]')).toBeNull();
  });

  it('renders nothing when status is ACTIVE', () => {
    setup(false);
    patchState(store, { readiness: readiness({ status: 'ACTIVE', name: true }) });
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="onboarding-pill"]')).toBeNull();
    expect(root.querySelector('[data-testid="onboarding-stepper"]')).toBeNull();
  });

  it('renders pill on mobile when status is DRAFT', () => {
    setup(false);
    patchState(store, { readiness: readiness({ name: true }) });
    fixture.detectChanges();
    const pill = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="onboarding-pill"]'
    );
    expect(pill).not.toBeNull();
  });

  it('does not render pill on desktop', () => {
    setup(true);
    patchState(store, { readiness: readiness({ name: true }) });
    fixture.detectChanges();
    const pill = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="onboarding-pill"]'
    );
    expect(pill).toBeNull();
  });
});
```

- [ ] **Step 2: Create the breakpoint injection token**

Create `frontend/src/app/shared/features/onboarding-indicator/breakpoint.token.ts`:

```typescript
import { InjectionToken, Signal } from '@angular/core';
import { isDesktopSignal } from '../../../core/utils/breakpoint.signal';

/**
 * Indirection token so tests can swap the desktop breakpoint signal
 * without monkey-patching `window.matchMedia`.
 *
 * Default factory invokes `isDesktopSignal()` inside an injection context.
 */
export const ONBOARDING_BREAKPOINT = new InjectionToken<() => Signal<boolean>>(
  'OnboardingBreakpoint',
  { providedIn: 'root', factory: () => isDesktopSignal }
);
```

- [ ] **Step 3: Create the component TS**

Create `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.ts`:

```typescript
import { Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DashboardStore } from '../../../features/dashboard/store/dashboard.store';
import { OnboardingChecklistService } from '../../../features/onboarding/onboarding-checklist.service';
import { ONBOARDING_BREAKPOINT } from './breakpoint.token';

@Component({
  selector: 'app-onboarding-indicator',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, TranslocoPipe],
  templateUrl: './onboarding-indicator.component.html',
  styleUrl: './onboarding-indicator.component.scss',
})
export class OnboardingIndicatorComponent {
  private readonly store = inject(DashboardStore);
  private readonly checklistService = inject(OnboardingChecklistService);
  private readonly transloco = inject(TranslocoService);

  protected readonly isDesktop = inject(ONBOARDING_BREAKPOINT)();

  protected readonly steps = computed(() =>
    this.checklistService.buildSteps(this.store.readiness())
  );
  protected readonly progress = computed(() =>
    this.checklistService.computeProgress(this.steps())
  );
  protected readonly visible = computed(() => this.store.isDraft());

  protected readonly nextStepLabel = computed(() => {
    const key = this.progress().nextKey;
    if (!key) return '';
    return this.transloco.translate(`pro.dashboard.checklist.${key}`);
  });
}
```

- [ ] **Step 4: Create the component template (pill only for now)**

Create `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.html`:

```html
@if (visible()) {
  @if (!isDesktop()) {
    <button
      type="button"
      class="indicator-pill"
      data-testid="onboarding-pill"
      [attr.aria-label]="'pro.onboarding.indicator.ariaProgress' | transloco: { done: progress().done, total: progress().total }"
    >
      <span class="pill-ring">
        <svg viewBox="0 0 30 30" width="30" height="30" aria-hidden="true">
          <circle cx="15" cy="15" r="12" fill="none" stroke="rgba(192,0,102,0.18)" stroke-width="3"></circle>
          <circle
            cx="15"
            cy="15"
            r="12"
            fill="none"
            stroke="#c06"
            stroke-width="3"
            stroke-linecap="round"
            [attr.stroke-dasharray]="75.4"
            [attr.stroke-dashoffset]="75.4 * (1 - progress().percent / 100)"
            transform="rotate(-90 15 15)"
          ></circle>
        </svg>
        <span class="pill-ring-text">{{ progress().done }}/{{ progress().total }}</span>
      </span>
      <span class="pill-text">
        <span class="pill-eyebrow">{{ 'pro.onboarding.indicator.label' | transloco }}</span>
        @if (progress().nextKey) {
          <span class="pill-next">{{ 'pro.onboarding.indicator.next' | transloco: { step: nextStepLabel() } }}</span>
        } @else {
          <span class="pill-next">{{ 'pro.onboarding.indicator.ready' | transloco }}</span>
        }
      </span>
      <mat-icon class="pill-chevron" aria-hidden="true">chevron_right</mat-icon>
    </button>
  }
}
```

- [ ] **Step 5: Create the component styles**

Create `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.scss`:

```scss
:host {
  display: block;
  position: relative;
  z-index: 30;
}

// ===== Pill (mobile) =====
.indicator-pill {
  position: fixed;
  top: 84px; // below the 80px header
  left: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px 6px 6px;
  background: white;
  border: 1px solid rgba(192, 0, 102, 0.25);
  border-radius: 999px;
  box-shadow: 0 4px 14px rgba(192, 0, 102, 0.12);
  cursor: pointer;
  z-index: 30;

  @media (prefers-reduced-motion: no-preference) {
    transition: transform 150ms ease, box-shadow 150ms ease;
    &:hover { transform: translateY(-1px); }
  }
}

.pill-ring {
  position: relative;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
}

.pill-ring-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 9px;
  font-weight: 600;
  color: #c06;
}

.pill-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  text-align: left;
}

.pill-eyebrow {
  font-size: 10px;
  color: #888;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.pill-next {
  font-size: 12px;
  color: #222;
  font-weight: 500;
}

.pill-chevron {
  font-size: 18px;
  width: 18px;
  height: 18px;
  color: #c06;
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
npm test -- --include='**/onboarding-indicator.component.spec.ts' --watch=false
```
Expected: PASS — 4 specs.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/shared/features/onboarding-indicator/
git commit -m "feat(onboarding-indicator): add component with mobile pill and auto-hide"
```

---

## Task 9: OnboardingIndicatorComponent — pill opens bottom-sheet

**Files:**
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.ts`
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.html`
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.spec.ts`

- [ ] **Step 1: Add the failing test**

Append to `onboarding-indicator.component.spec.ts` inside the `describe('OnboardingIndicatorComponent', ...)` block (top-level, after the existing tests):

```typescript
  it('opens the bottom-sheet when the pill is tapped (mobile)', () => {
    const dialog = jasmine.createSpyObj('MatDialog', ['open']);
    dialog.open.and.returnValue({
      afterClosed: () => ({ subscribe: (fn: any) => fn(undefined) }),
    });
    setup(false);
    patchState(store, { readiness: readiness({ name: true }) });
    fixture.componentInstance['dialog'] = dialog as any;
    fixture.detectChanges();

    const pill = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      '[data-testid="onboarding-pill"]'
    );
    pill?.click();
    expect(dialog.open).toHaveBeenCalled();
  });
```

(This patches the `dialog` field — we'll mark it `protected` rather than `private` so the patch works. See step 2.)

- [ ] **Step 2: Update the component TS to inject `MatDialog` and handle clicks**

Replace the body of `OnboardingIndicatorComponent` (`onboarding-indicator.component.ts`) so it becomes:

```typescript
import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DashboardStore } from '../../../features/dashboard/store/dashboard.store';
import { OnboardingChecklistService } from '../../../features/onboarding/onboarding-checklist.service';
import { OnboardingStepKey } from '../../../features/onboarding/onboarding-step.model';
import { bottomSheetConfig } from '../../uis/sheet-handle/bottom-sheet.config';
import { ONBOARDING_BREAKPOINT } from './breakpoint.token';
import {
  OnboardingIndicatorSheetComponent,
  OnboardingSheetData,
  OnboardingSheetResult,
} from './onboarding-indicator-sheet.component';

@Component({
  selector: 'app-onboarding-indicator',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, TranslocoPipe],
  templateUrl: './onboarding-indicator.component.html',
  styleUrl: './onboarding-indicator.component.scss',
})
export class OnboardingIndicatorComponent {
  private readonly store = inject(DashboardStore);
  private readonly checklistService = inject(OnboardingChecklistService);
  private readonly transloco = inject(TranslocoService);
  private readonly router = inject(Router);
  protected dialog = inject(MatDialog);

  protected readonly isDesktop = inject(ONBOARDING_BREAKPOINT)();

  protected readonly steps = computed(() =>
    this.checklistService.buildSteps(this.store.readiness())
  );
  protected readonly progress = computed(() =>
    this.checklistService.computeProgress(this.steps())
  );
  protected readonly visible = computed(() => this.store.isDraft());

  protected readonly nextStepLabel = computed(() => {
    const key = this.progress().nextKey;
    if (!key) return '';
    return this.transloco.translate(`pro.dashboard.checklist.${key}`);
  });

  protected onPillClick(): void {
    const readiness = this.store.readiness();
    if (!readiness) return;

    const ref = this.dialog.open<
      OnboardingIndicatorSheetComponent,
      OnboardingSheetData,
      OnboardingSheetResult
    >(
      OnboardingIndicatorSheetComponent,
      bottomSheetConfig<OnboardingSheetData>({
        data: {
          steps: this.steps(),
          progress: this.progress(),
          canPublish: this.store.canPublish(),
          slug: readiness.slug,
        },
      })
    );

    ref.afterClosed().subscribe((result) => this.handleSheetResult(result));
  }

  private handleSheetResult(result: OnboardingSheetResult | undefined): void {
    if (!result) return;
    switch (result.action) {
      case 'step': {
        const step = this.steps().find((s) => s.key === result.stepKey);
        if (step) this.router.navigate([step.link], { queryParams: step.queryParams ?? undefined });
        return;
      }
      case 'preview': {
        const slug = this.store.readiness()?.slug;
        if (slug) this.router.navigate(['/salon', slug]);
        return;
      }
      case 'publish':
        this.store.publish();
        return;
      case 'dashboard':
        this.router.navigate(['/pro/dashboard']);
        return;
    }
  }

  protected stepKeyForTrack(_index: number, key: OnboardingStepKey): OnboardingStepKey {
    return key;
  }
}
```

- [ ] **Step 3: Update the template**

In `onboarding-indicator.component.html`, change the pill `<button>` opening tag to bind `(click)`:

```html
    <button
      type="button"
      class="indicator-pill"
      data-testid="onboarding-pill"
      (click)="onPillClick()"
      [attr.aria-label]="'pro.onboarding.indicator.ariaProgress' | transloco: { done: progress().done, total: progress().total }"
    >
```

(Only the `(click)="onPillClick()"` line is added — the rest stays.)

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --include='**/onboarding-indicator.component.spec.ts' --watch=false
```
Expected: PASS — 5 specs.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/features/onboarding-indicator/
git commit -m "feat(onboarding-indicator): wire pill click to bottom-sheet with action routing"
```

---

## Task 10: OnboardingIndicatorComponent — desktop stepper rendering

**Files:**
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.html`
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.scss`
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.spec.ts`

- [ ] **Step 1: Add the failing test**

Append to `onboarding-indicator.component.spec.ts`, inside the `describe` block:

```typescript
  it('renders the desktop stepper with all step labels and a preview button', () => {
    setup(true);
    patchState(store, { readiness: readiness({ name: true }) });
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="onboarding-stepper"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="stepper-step-name"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="stepper-step-cares"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="stepper-step-openingHours"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="stepper-preview"]')).not.toBeNull();
  });

  it('shows publish button only when canPublish is true', () => {
    setup(true);
    patchState(store, { readiness: readiness({ name: true, hasActiveCare: true, hasOpeningHours: true, canPublish: true }) });
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="stepper-publish"]')
    ).not.toBeNull();
  });

  it('does not show publish button when canPublish is false', () => {
    setup(true);
    patchState(store, { readiness: readiness({ name: true, canPublish: false }) });
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="stepper-publish"]')
    ).toBeNull();
  });
```

- [ ] **Step 2: Run tests to verify two of them fail**

```bash
npm test -- --include='**/onboarding-indicator.component.spec.ts' --watch=false
```
Expected: 3 new specs FAIL — selectors not found.

- [ ] **Step 3: Update the template to render the stepper on desktop**

In `onboarding-indicator.component.html`, **replace the entire content** with:

```html
@if (visible()) {
  @if (!isDesktop()) {
    <button
      type="button"
      class="indicator-pill"
      data-testid="onboarding-pill"
      (click)="onPillClick()"
      [attr.aria-label]="'pro.onboarding.indicator.ariaProgress' | transloco: { done: progress().done, total: progress().total }"
    >
      <span class="pill-ring">
        <svg viewBox="0 0 30 30" width="30" height="30" aria-hidden="true">
          <circle cx="15" cy="15" r="12" fill="none" stroke="rgba(192,0,102,0.18)" stroke-width="3"></circle>
          <circle
            cx="15"
            cy="15"
            r="12"
            fill="none"
            stroke="#c06"
            stroke-width="3"
            stroke-linecap="round"
            [attr.stroke-dasharray]="75.4"
            [attr.stroke-dashoffset]="75.4 * (1 - progress().percent / 100)"
            transform="rotate(-90 15 15)"
          ></circle>
        </svg>
        <span class="pill-ring-text">{{ progress().done }}/{{ progress().total }}</span>
      </span>
      <span class="pill-text">
        <span class="pill-eyebrow">{{ 'pro.onboarding.indicator.label' | transloco }}</span>
        @if (progress().nextKey) {
          <span class="pill-next">{{ 'pro.onboarding.indicator.next' | transloco: { step: nextStepLabel() } }}</span>
        } @else {
          <span class="pill-next">{{ 'pro.onboarding.indicator.ready' | transloco }}</span>
        }
      </span>
      <mat-icon class="pill-chevron" aria-hidden="true">chevron_right</mat-icon>
    </button>
  } @else {
    <div class="indicator-stepper" data-testid="onboarding-stepper">
      <span class="stepper-eyebrow">{{ 'pro.onboarding.indicator.label' | transloco }}</span>

      <ol class="stepper-list">
        @for (step of steps(); track step.key) {
          <li class="stepper-item"
              [class.is-done]="step.done"
              [class.is-next]="!step.done && progress().nextKey === step.key">
            <a
              class="stepper-link"
              [routerLink]="step.link"
              [queryParams]="step.queryParams"
              [attr.aria-current]="!step.done && progress().nextKey === step.key ? 'step' : null"
              [attr.data-testid]="'stepper-step-' + step.key"
            >
              <span class="stepper-circle">
                @if (step.done) {
                  <mat-icon aria-hidden="true">check</mat-icon>
                } @else {
                  <span>{{ $index + 1 }}</span>
                }
              </span>
              <span class="stepper-label">{{ 'pro.dashboard.checklist.' + step.key | transloco }}</span>
            </a>
            @if (!$last) {
              <span class="stepper-connector" aria-hidden="true"></span>
            }
          </li>
        }
      </ol>

      <div class="stepper-actions">
        <a
          class="stepper-preview"
          [routerLink]="['/salon', store.readiness()?.slug]"
          data-testid="stepper-preview"
        >
          <mat-icon aria-hidden="true">visibility</mat-icon>
          <span>{{ 'pro.onboarding.indicator.preview' | transloco }}</span>
        </a>
        @if (store.canPublish()) {
          <button
            type="button"
            class="stepper-publish"
            (click)="onPublishClick()"
            data-testid="stepper-publish"
          >
            <mat-icon aria-hidden="true">rocket_launch</mat-icon>
            <span>{{ 'pro.onboarding.sheet.publish' | transloco }}</span>
          </button>
        }
      </div>
    </div>
  }
}
```

- [ ] **Step 4: Add `RouterLink` import + `onPublishClick` method**

In `onboarding-indicator.component.ts`, update the `imports` array of the `@Component` decorator and add `RouterLink`:

```typescript
import { Router, RouterLink } from '@angular/router';
```

```typescript
@Component({
  selector: 'app-onboarding-indicator',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, TranslocoPipe, RouterLink],
  templateUrl: './onboarding-indicator.component.html',
  styleUrl: './onboarding-indicator.component.scss',
})
```

Inside the class, also expose the `store` to the template (so we can read `store.canPublish()` and `store.readiness()?.slug`) and add `onPublishClick`:

Change the line:
```typescript
  private readonly store = inject(DashboardStore);
```

To:
```typescript
  protected readonly store = inject(DashboardStore);
```

Add at the end of the class:
```typescript
  protected onPublishClick(): void {
    this.store.publish();
  }
```

- [ ] **Step 5: Append stepper styles**

Append to `onboarding-indicator.component.scss`:

```scss
// ===== Stepper (desktop) =====
.indicator-stepper {
  position: sticky;
  top: 80px; // below header
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 24px;
  background: linear-gradient(to bottom, #fff8fb, #ffffff);
  border-bottom: 1px solid rgba(192, 0, 102, 0.12);
  z-index: 25;
}

.stepper-eyebrow {
  font-size: 10px;
  color: #888;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  flex-shrink: 0;
}

.stepper-list {
  display: flex;
  align-items: center;
  flex: 1;
  list-style: none;
  margin: 0;
  padding: 0;
  gap: 0;
}

.stepper-item {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.stepper-link {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  color: #555;
  padding: 4px 6px;
  border-radius: 6px;

  &:hover {
    background: rgba(192, 0, 102, 0.04);
  }
}

.stepper-circle {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.55);
  background: white;
  flex-shrink: 0;

  mat-icon {
    font-size: 14px;
    width: 14px;
    height: 14px;
  }
}

.stepper-item.is-done .stepper-circle {
  background: #c06;
  border-color: #c06;
  color: white;
}

.stepper-item.is-next .stepper-circle {
  border-color: #c06;
  color: #c06;
}

.stepper-item.is-next .stepper-link {
  color: #222;
  font-weight: 500;
}

.stepper-label {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.stepper-connector {
  flex: 1;
  height: 2px;
  background: rgba(0, 0, 0, 0.08);
  margin: 0 8px;
}

.stepper-item.is-done + .stepper-item .stepper-connector,
.stepper-item.is-done > .stepper-connector {
  background: #c06;
}

.stepper-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.stepper-preview,
.stepper-publish {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid transparent;
  text-decoration: none;

  mat-icon {
    font-size: 16px;
    width: 16px;
    height: 16px;
  }
}

.stepper-preview {
  background: transparent;
  color: #c06;
  border-color: rgba(192, 0, 102, 0.3);

  &:hover {
    background: rgba(192, 0, 102, 0.06);
  }
}

.stepper-publish {
  background: #c06;
  color: white;
  border-color: #c06;

  &:hover {
    background: #a05;
  }
}

@media (prefers-reduced-motion: reduce) {
  .stepper-link, .stepper-preview, .stepper-publish, .indicator-pill {
    transition: none;
  }
}
```

- [ ] **Step 6: Run tests**

```bash
npm test -- --include='**/onboarding-indicator.component.spec.ts' --watch=false
```
Expected: PASS — 8 specs total.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/shared/features/onboarding-indicator/
git commit -m "feat(onboarding-indicator): add desktop stepper with clickable steps and publish CTA"
```

---

## Task 11: ProShellComponent (route wrapper hoisting DashboardStore)

**Files:**
- Create: `frontend/src/app/pages/pro/pro-shell.component.ts`
- Create: `frontend/src/app/pages/pro/pro-shell.component.html`
- Create: `frontend/src/app/pages/pro/pro-shell.component.scss`
- Create: `frontend/src/app/pages/pro/pro-shell.component.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/pages/pro/pro-shell.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { ProShellComponent } from './pro-shell.component';
import { DashboardStore } from '../../features/dashboard/store/dashboard.store';

describe('ProShellComponent', () => {
  let fixture: ComponentFixture<ProShellComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
      imports: [
        ProShellComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(ProShellComponent);
    fixture.detectChanges();
  });

  it('provides DashboardStore at the component level', () => {
    const store = fixture.debugElement.injector.get(DashboardStore);
    expect(store).toBeTruthy();
  });

  it('renders an onboarding indicator and a router outlet', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('app-onboarding-indicator')).not.toBeNull();
    expect(root.querySelector('router-outlet')).not.toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm test -- --include='**/pro-shell.component.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Create the component TS**

Create `frontend/src/app/pages/pro/pro-shell.component.ts`:

```typescript
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
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
export class ProShellComponent {}
```

- [ ] **Step 4: Create the template**

Create `frontend/src/app/pages/pro/pro-shell.component.html`:

```html
<app-onboarding-indicator />
<router-outlet />
```

- [ ] **Step 5: Create the styles**

Create `frontend/src/app/pages/pro/pro-shell.component.scss`:

```scss
:host {
  display: block;
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
npm test -- --include='**/pro-shell.component.spec.ts' --watch=false
```
Expected: PASS — 2 specs.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/pages/pro/pro-shell.component.ts frontend/src/app/pages/pro/pro-shell.component.html frontend/src/app/pages/pro/pro-shell.component.scss frontend/src/app/pages/pro/pro-shell.component.spec.ts
git commit -m "feat(pro): add ProShellComponent wrapping /pro routes with hoisted DashboardStore"
```

---

## Task 12: Wire ProShellComponent into the routing

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/pages/pro/pro-dashboard.component.ts`

- [ ] **Step 1: Update the `/pro` route to use ProShellComponent as wrapper**

In `frontend/src/app/app.routes.ts`, find the block starting at line ~43:

```typescript
  // Protected pro routes
  {
    path: 'pro',
    canActivate: [authGuard, roleGuard(Role.PRO)],
    children: [
```

Change it to:

```typescript
  // Protected pro routes
  {
    path: 'pro',
    canActivate: [authGuard, roleGuard(Role.PRO)],
    loadComponent: () =>
      import('./pages/pro/pro-shell.component').then((m) => m.ProShellComponent),
    children: [
```

Leave the rest of the children array unchanged.

- [ ] **Step 2: Drop local DashboardStore provider from pro-dashboard**

In `frontend/src/app/pages/pro/pro-dashboard.component.ts`, locate line ~49:

```typescript
  providers: [DashboardStore, provideCharts(withDefaultRegisterables())],
```

Replace with:

```typescript
  providers: [provideCharts(withDefaultRegisterables())],
```

(`DashboardStore` now comes from the parent `ProShellComponent`.)

- [ ] **Step 3: Verify build still works**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 4: Run pro-dashboard tests**

```bash
npm test -- --include='**/pro-dashboard.component.spec.ts' --watch=false
```
Expected: PASS. If tests fail with "No provider for DashboardStore", the existing test setup needs `DashboardStore` in `providers`. Update `pro-dashboard.component.spec.ts` `TestBed.configureTestingModule({ providers: [...] })` to include `DashboardStore`. Add at the very end of the `providers` array of the `beforeEach` (or wherever providers are configured):

```typescript
DashboardStore,
```

Re-run:
```bash
npm test -- --include='**/pro-dashboard.component.spec.ts' --watch=false
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/app.routes.ts frontend/src/app/pages/pro/pro-dashboard.component.ts frontend/src/app/pages/pro/pro-dashboard.component.spec.ts
git commit -m "refactor(routes): wrap /pro under ProShellComponent and hoist DashboardStore"
```

---

## Task 13: Hide onboarding-wrap on dashboard when stepper is shown elsewhere

The dashboard's local `onboarding-wrap` (rich form with personas + checklist) **must remain** when the user is on `/pro/dashboard` — that's the spec decision (the global indicator is the compact form, the wrap is the rich form). However, if the global stepper is sticky-positioned on top, both might overlap visually. We add a small SCSS adjustment so the dashboard's existing `dashboard-header` sits below the stepper.

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-dashboard.component.scss`

- [ ] **Step 1: Open the dashboard SCSS**

Open `frontend/src/app/pages/pro/pro-dashboard.component.scss`. Locate `.dashboard-container` (likely near the top).

- [ ] **Step 2: Add top padding so content sits below the indicator**

Add (or merge if present) inside `.dashboard-container`:

```scss
.dashboard-container {
  // existing rules...

  // Ensure dashboard content does not collide with the desktop stepper
  // (the stepper has min-height ~52px on desktop).
  @media (min-width: 768px) {
    padding-top: 8px;
  }
}
```

If `.dashboard-container` already has a `padding-top`, merge the value (keep it OR raise it to at least 8px). Do not duplicate the selector if it's already present — append the media query inside the existing rule.

- [ ] **Step 3: Verify visually**

Run dev server:
```bash
cd frontend && npm start
```
Open http://localhost:4200, log in as a pro with a DRAFT salon. Navigate to `/pro/dashboard`. Confirm the desktop stepper is visible at the top, and the dashboard's "Configurez votre salon" section sits below without overlap.

Stop the server (Ctrl+C).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/pro/pro-dashboard.component.scss
git commit -m "fix(pro-dashboard): add top spacing so content sits below desktop stepper"
```

---

## Task 14: Final integration check across pro pages

**Files:** none (manual verification)

- [ ] **Step 1: Start the backend**

In one terminal, from `backend/`:

```bash
mvn spring-boot:run
```

Wait until you see "Started ... in N seconds".

- [ ] **Step 2: Start the frontend**

In another terminal, from `frontend/`:

```bash
npm start
```

- [ ] **Step 3: Manual smoke checks (DRAFT)**

Open http://localhost:4200 in a desktop browser (≥ 768px width). Log in as a pro user whose tenant has `status=DRAFT`. Navigate through:

1. `/pro/dashboard` — confirm desktop stepper appears at top, all 3 step labels visible, preview button visible.
2. `/pro/cares` — stepper still visible above the page content.
3. `/pro/planning` — stepper still visible.
4. `/pro/employees` — stepper still visible.
5. `/pro/settings` — stepper still visible.
6. Click on each step in the stepper — confirm navigation to `/pro/salon`, `/pro/cares`, `/pro/planning` respectively.
7. Click the "Aperçu" link — confirm navigation to `/salon/<slug>` (will 404 until Jalon 2 ships; that's expected).

- [ ] **Step 4: Mobile smoke checks**

Resize the browser to < 768px (or use DevTools mobile emulation). Reload `/pro/dashboard`.

1. Confirm the pill appears top-left below the header (no stepper).
2. Tap the pill — confirm a bottom-sheet opens with the 3 steps + preview/dashboard buttons.
3. Tap a step in the sheet — confirm sheet closes and navigation occurs.
4. Re-open the sheet — confirm tapping "Retour au tableau de bord" navigates back to `/pro/dashboard` and closes the sheet.

- [ ] **Step 5: Hide check (ACTIVE)**

In the backend, update the tenant's status to `ACTIVE` (or use the dashboard's "Publier" button if the checklist is complete). Reload `/pro/dashboard`.

1. Confirm neither the pill nor the stepper appears on any `/pro/*` page.

- [ ] **Step 6: Run full frontend test suite**

```bash
cd frontend && npm test -- --watch=false
```
Expected: PASS (no regressions).

- [ ] **Step 7: Run typecheck**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 8: Final commit (only if Step 6 or 7 surfaced fix-ups)**

If everything passed in steps 6 and 7 with no edits needed, skip this step. Otherwise commit any fixes:

```bash
git add -A
git commit -m "fix(onboarding-indicator): address integration issues"
```

---

## Self-Review Notes

**Spec coverage check (Jalon 1 only):**

| Spec requirement | Implemented in |
|------------------|----------------|
| Indicator visible on all `/pro/*` pages while DRAFT | Tasks 8, 11, 12 |
| Auto-hide when status === ACTIVE | Task 8 (`visible` computed) |
| Pill mobile (< 768px) with circle progression + label | Tasks 5, 8 |
| Stepper PC (≥ 768px) with clickable steps | Tasks 5, 10 |
| Bottom-sheet content on mobile | Task 7 |
| Pill click opens bottom-sheet | Task 9 |
| "Prêt à publier" state at 3/3 with publish CTA | Task 10 (stepper-publish), Task 7 (sheet-publish) |
| Aperçu shortcut always visible | Task 10 (stepper-preview), Task 7 (sheet preview button) |
| `OnboardingChecklistService` factorized, consumed by both dashboard and indicator | Tasks 2, 3, 4, 8 |
| `DashboardStore` hoisted to `ProShellComponent` | Tasks 11, 12 |
| Dashboard's existing `onboarding-wrap` preserved | Task 13 (only spacing tweak; no content change) |
| i18n keys FR + EN | Task 6 |
| Accessibility (aria-label on pill, aria-current on stepper, prefers-reduced-motion) | Tasks 8, 9, 10 |

**Out of scope for this plan (handled in later jalons):**
- Preview vitrine routing/banner (Jalon 2 — the indicator's "Aperçu" link will 404 until then; expected).
- Token preview share (Jalon 5).
- Synchronisation publish↔ProShellComponent edge case (Jalon 2).
- "Mode prêt" visual styling polish for the stepper (covered minimally; richer state can be added during Jalon 2 integration).

**Placeholders scan:** none — all steps contain concrete code or commands.

**Type consistency:**
- `OnboardingStepKey` defined in Task 1, used in Tasks 2, 7, 9.
- `OnboardingStep` / `OnboardingProgress` consistent across Tasks 1, 2, 3, 7, 8.
- `OnboardingSheetData` / `OnboardingSheetResult` defined in Task 7, consumed in Task 9.
- `ONBOARDING_BREAKPOINT` token defined in Task 8 step 2, injected in Task 8 step 3 and used in tests in Task 8 step 1. Tests in Tasks 9-10 reuse the same `setup()` helper from Task 8 (the helper provides the token override).
- `isDesktopSignal()` in Task 5 returns `Signal<boolean>`; the token's factory references it directly.

---

## Notes for the executing engineer

- The `pro-dashboard.component.spec.ts` test setup currently provides `DashboardStore` implicitly via the component's `providers`. Once we drop that in Task 12, you must add `DashboardStore` explicitly to the test's `providers` array. Task 12 step 4 covers this with the exact instructions.
- Existing dashboard checklist behavior must stay identical from the user's POV — Task 4 is a pure refactor. If `pro-dashboard.component.spec.ts` asserts on `checklistSteps()` shape, those assertions will keep passing because the service returns the same structure.
- The `bottomSheetConfig` helper is already provided by `shared/uis/sheet-handle/bottom-sheet.config.ts`. Do not redefine it.
- The dashboard's `onboarding-wrap` (the rich quickstart with personas) **stays** on `/pro/dashboard`. Do not delete it. The new indicator is the compact, persistent form; the wrap is the rich, in-context form.
