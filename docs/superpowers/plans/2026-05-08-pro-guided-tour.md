# Pro Guided Tour Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an in-context guided tour that points the pro at the real form fields they need to fill on `/pro/salon`, `/pro/cares`, `/pro/planning`, with a spotlight overlay + bubble that auto-advances when `TenantReadiness` confirms each condition is met.

**Architecture:** Custom Angular CDK Overlay implementation. A root-scoped `TourService` owns the tour state (active, currentStep, inTransition). A single `<app-tour-overlay />` mounted in `ProShellComponent` queries `[data-tour-step="<key>"]` on the current page, draws an overlay with a clip-path cutout around the target, and pins a bubble to it. Auto-advance is driven by an `effect()` reacting to `DashboardStore.readiness()`. The existing onboarding indicator launches the tour at the picked step instead of doing a plain navigate.

**Tech Stack:** Angular 20 standalone + signals, Angular CDK Overlay, Karma/Jasmine, Transloco i18n.

**Spec:** `docs/superpowers/specs/2026-05-08-pro-guided-tour-design.md`

---

## File Structure

### New files

| Path | Role |
|------|------|
| `frontend/src/app/features/onboarding/tour/tour-step.model.ts` | Types `TourStep` + `WizardStepKey` union for the 6 conditions. |
| `frontend/src/app/features/onboarding/tour/tour-steps.ts` | Immutable catalogue `TOUR_STEPS: readonly TourStep[]`. |
| `frontend/src/app/features/onboarding/tour/tour.service.ts` | Root-scoped service. Signals `active`, `currentStep`, `inTransition`, `progress`. Methods `start`, `stop`, `advance`. Effect that reacts to `readiness()` to auto-advance. |
| `frontend/src/app/features/onboarding/tour/tour.service.spec.ts` | Unit tests for the service (6 scenarios). |
| `frontend/src/app/shared/uis/tour-overlay/tour-overlay.component.ts` | The overlay container. Reads `tour.currentStep()`, queries DOM, mounts overlay + halo + bubble. Owns ResizeObserver and scroll listener. |
| `frontend/src/app/shared/uis/tour-overlay/tour-overlay.component.html` | Template with overlay (clip-path cutout) + halo + `<app-tour-bubble />`. |
| `frontend/src/app/shared/uis/tour-overlay/tour-overlay.component.scss` | Styles for overlay, halo, animations. |
| `frontend/src/app/shared/uis/tour-overlay/tour-overlay.component.spec.ts` | Unit tests (4 scenarios including retry). |
| `frontend/src/app/shared/uis/tour-overlay/tour-bubble.component.ts` | The bubble. Inputs `step`, `progress`, `inTransition`, `targetRect`. Computed `bubblePosition` (above/below). Output `(close)`. |
| `frontend/src/app/shared/uis/tour-overlay/tour-bubble.component.html` | Template with title, desc, counter, "Plus tard" button. Transition state shows ✓ + success line. |
| `frontend/src/app/shared/uis/tour-overlay/tour-bubble.component.scss` | Styles for bubble, arrow, transition state. |
| `frontend/src/app/shared/uis/tour-overlay/tour-bubble.component.spec.ts` | Unit tests (2 scenarios). |

### Modified files

| Path | Modification |
|------|--------------|
| `frontend/src/app/features/onboarding/onboarding-step.model.ts` | Extend `OnboardingStepKey` from 3 to 6 keys; the additional `OnboardingStep`s the indicator now exposes. |
| `frontend/src/app/features/onboarding/onboarding-checklist.service.ts` | `buildSteps` returns 6 entries instead of 3. Each step includes `link` + `queryParams` so legacy navigation still works as a fallback. |
| `frontend/src/app/features/onboarding/onboarding-checklist.service.spec.ts` | Update fixtures to match the 6-step output. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.ts` | Inject `TourService`. New method `onStepClick(stepKey)` that calls `tour.start(stepKey)` if `TOUR_STEPS` covers it, else falls back to `router.navigate`. Sheet handler routes `step` action through the same gate. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.html` | Replace `[routerLink]`/`[queryParams]` on the stepper item link with `(click)="onStepClick(step.key)"`. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.spec.ts` | Add one test: clicking a covered step calls `tour.start(stepKey)`. |
| `frontend/src/app/pages/pro/pro-shell.component.ts` | Import `TourOverlayComponent`. |
| `frontend/src/app/pages/pro/pro-shell.component.html` | Add `<app-tour-overlay />` after `<router-outlet />`. |
| `frontend/src/app/features/salon-profile/salon-profile.component.html` | Add `data-tour-step="name"` on the `<mat-form-field>` containing the salon name, `data-tour-step="contact"` on the address section root, `data-tour-step="logo"` on the `.logo-wrapper`. |
| `frontend/src/app/features/cares/cares.component.html` | Add `data-tour-step="categories"` on the `Add category` button (line 40 area), `data-tour-step="add-care"` on the `Add care` button (line 56 area). |
| `frontend/src/app/features/availability/availability.component.html` | Add `data-tour-step="opening-hours"` on the root `<div class="availability-page">`. |
| `frontend/public/i18n/fr.json` and `en.json` | Add `pro.tour.*` block (steps × 6 with `title`+`desc`, `transition.success`, `actions.later`). |

---

## Section A — Core service + catalogue (PR1, mergeable solo)

### Task A1: Create `TourStep` types

**Files:**
- Create: `frontend/src/app/features/onboarding/tour/tour-step.model.ts`

- [ ] **Step 1: Write the model file**

```typescript
import { TenantReadiness } from '../../dashboard/models/dashboard.model';

/** Stable identifier for one of the 6 publish conditions, used as i18n suffix and DOM marker. */
export type WizardStepKey =
  | 'name'
  | 'contact'
  | 'logo'
  | 'categories'
  | 'cares'
  | 'openingHours';

/**
 * One step covered by the guided tour. Maps a backend readiness flag to:
 *  - the page that owns the field (`route`)
 *  - the value of `data-tour-step` placed on the field in that page's template
 *  - the i18n keys for the bubble title and description
 */
export interface TourStep {
  readonly key: WizardStepKey;
  readonly readinessFlag: keyof TenantReadiness;
  readonly route: string;
  readonly tourStep: string;
  readonly titleKey: string;
  readonly descKey: string;
}
```

- [ ] **Step 2: Verify type-check**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx tsc --noEmit -p tsconfig.app.json
```
Expected: no output (success).

- [ ] **Step 3: Commit**

```bash
git -C /Users/Gustavo.alves/Documents/personal/portfolio add frontend/src/app/features/onboarding/tour/tour-step.model.ts
git -C /Users/Gustavo.alves/Documents/personal/portfolio commit -m "feat(tour): add TourStep + WizardStepKey types"
```

---

### Task A2: Create `TOUR_STEPS` catalogue

**Files:**
- Create: `frontend/src/app/features/onboarding/tour/tour-steps.ts`

- [ ] **Step 1: Write the catalogue**

```typescript
import { TourStep } from './tour-step.model';

export const TOUR_STEPS: readonly TourStep[] = [
  { key: 'name',         readinessFlag: 'name',            route: '/pro/salon',    tourStep: 'name',          titleKey: 'pro.tour.steps.name.title',         descKey: 'pro.tour.steps.name.desc' },
  { key: 'contact',      readinessFlag: 'hasContact',      route: '/pro/salon',    tourStep: 'contact',       titleKey: 'pro.tour.steps.contact.title',      descKey: 'pro.tour.steps.contact.desc' },
  { key: 'logo',         readinessFlag: 'hasLogo',         route: '/pro/salon',    tourStep: 'logo',          titleKey: 'pro.tour.steps.logo.title',         descKey: 'pro.tour.steps.logo.desc' },
  { key: 'categories',   readinessFlag: 'hasCategory',     route: '/pro/cares',    tourStep: 'categories',    titleKey: 'pro.tour.steps.categories.title',   descKey: 'pro.tour.steps.categories.desc' },
  { key: 'cares',        readinessFlag: 'hasActiveCare',   route: '/pro/cares',    tourStep: 'add-care',      titleKey: 'pro.tour.steps.cares.title',        descKey: 'pro.tour.steps.cares.desc' },
  { key: 'openingHours', readinessFlag: 'hasOpeningHours', route: '/pro/planning', tourStep: 'opening-hours', titleKey: 'pro.tour.steps.openingHours.title', descKey: 'pro.tour.steps.openingHours.desc' },
] as const;
```

- [ ] **Step 2: Verify type-check**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx tsc --noEmit -p tsconfig.app.json
```
Expected: no output.

- [ ] **Step 3: Commit**

```bash
git -C /Users/Gustavo.alves/Documents/personal/portfolio add frontend/src/app/features/onboarding/tour/tour-steps.ts
git -C /Users/Gustavo.alves/Documents/personal/portfolio commit -m "feat(tour): add TOUR_STEPS catalogue"
```

---

### Task A3: Write `TourService` failing tests

**Files:**
- Create: `frontend/src/app/features/onboarding/tour/tour.service.spec.ts`

- [ ] **Step 1: Write the spec**

```typescript
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { DashboardStore } from '../../dashboard/store/dashboard.store';
import { TenantReadiness } from '../../dashboard/models/dashboard.model';
import { TourService } from './tour.service';

function readiness(overrides: Partial<TenantReadiness> = {}): TenantReadiness {
  return {
    slug: 'demo',
    name: false,
    hasCategory: false,
    hasContact: false,
    hasLogo: false,
    hasActiveCare: false,
    hasOpeningHours: false,
    canPublish: false,
    status: 'DRAFT',
    ...overrides,
  };
}

describe('TourService', () => {
  let service: TourService;
  let store: any;
  let router: jasmine.SpyObj<Router>;

  function setup(initialReadiness: TenantReadiness | null) {
    const readinessSig = signal<TenantReadiness | null>(initialReadiness);
    store = {
      readiness: readinessSig,
      _setReadiness: (r: TenantReadiness | null) => readinessSig.set(r),
    };
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl'], { url: '/pro/dashboard' });
    router.navigateByUrl.and.returnValue(Promise.resolve(true));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_BASE_URL, useValue: 'http://t' },
        { provide: DashboardStore, useValue: store },
        { provide: Router, useValue: router },
        TourService,
      ],
    });
    service = TestBed.inject(TourService);
    return readinessSig;
  }

  it('start() is a no-op when readiness is null', () => {
    setup(null);
    service.start('logo');
    expect(service.active()).toBeFalse();
    expect(service.currentStep()).toBeNull();
  });

  it('start() is a no-op when tenant is ACTIVE and canPublish', () => {
    setup(readiness({ status: 'ACTIVE', canPublish: true, name: true, hasContact: true, hasLogo: true, hasCategory: true, hasActiveCare: true, hasOpeningHours: true }));
    service.start();
    expect(service.active()).toBeFalse();
  });

  it('start("logo") sets currentStep to the logo step and navigates to /pro/salon', () => {
    setup(readiness());
    service.start('logo');
    expect(service.active()).toBeTrue();
    expect(service.currentStep()?.key).toBe('logo');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/pro/salon');
  });

  it('start() with no key picks the first missing condition', () => {
    setup(readiness({ name: true })); // hasContact is the first missing
    service.start();
    expect(service.currentStep()?.key).toBe('contact');
  });

  it('auto-advances 1500ms after readiness flips the current step to true', fakeAsync(() => {
    const sig = setup(readiness());
    service.start('name');
    expect(service.currentStep()?.key).toBe('name');

    sig.set(readiness({ name: true })); // hasContact still false
    TestBed.tick();
    expect(service.inTransition()).toBeTrue();

    tick(1500);
    expect(service.inTransition()).toBeFalse();
    expect(service.currentStep()?.key).toBe('contact');
  }));

  it('stop() resets all signals', () => {
    setup(readiness());
    service.start('logo');
    service.stop();
    expect(service.active()).toBeFalse();
    expect(service.currentStep()).toBeNull();
    expect(service.inTransition()).toBeFalse();
  });
});
```

- [ ] **Step 2: Run, expect FAIL (compile error — service does not exist)**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/tour.service.spec.ts' --watch=false
```
Expected: FAIL with module not found / compile error.

---

### Task A4: Implement `TourService`

**Files:**
- Create: `frontend/src/app/features/onboarding/tour/tour.service.ts`

- [ ] **Step 1: Write the service**

```typescript
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DashboardStore } from '../../dashboard/store/dashboard.store';
import { TenantReadiness } from '../../dashboard/models/dashboard.model';
import { TOUR_STEPS } from './tour-steps';
import { TourStep, WizardStepKey } from './tour-step.model';

const TRANSITION_DELAY_MS = 1500;

@Injectable({ providedIn: 'root' })
export class TourService {
  private readonly router = inject(Router);
  private readonly store = inject(DashboardStore);

  private readonly _active = signal(false);
  private readonly _currentStep = signal<TourStep | null>(null);
  private readonly _inTransition = signal(false);

  readonly active = this._active.asReadonly();
  readonly currentStep = this._currentStep.asReadonly();
  readonly inTransition = this._inTransition.asReadonly();

  readonly progress = computed(() => {
    const r = this.store.readiness();
    if (!r) return { done: 0, total: TOUR_STEPS.length };
    const done = TOUR_STEPS.filter(s => r[s.readinessFlag] === true).length;
    return { done, total: TOUR_STEPS.length };
  });

  constructor() {
    // Auto-advance when readiness flips the current step's flag to true.
    effect(() => {
      const step = this._currentStep();
      const r = this.store.readiness();
      if (!step || !r || !this._active() || this._inTransition()) return;
      if (r[step.readinessFlag] === true) {
        this.advance();
      }
    });
  }

  start(fromKey?: WizardStepKey): void {
    const r = this.store.readiness();
    if (!r) return;
    if (r.status === 'ACTIVE' && r.canPublish) return;

    const step = fromKey
      ? TOUR_STEPS.find(s => s.key === fromKey) ?? null
      : this.firstMissing(r);
    if (!step) return;

    this._active.set(true);
    this.navigateTo(step);
  }

  stop(): void {
    this._active.set(false);
    this._currentStep.set(null);
    this._inTransition.set(false);
  }

  private advance(): void {
    this._inTransition.set(true);
    setTimeout(() => {
      const r = this.store.readiness();
      if (!r) {
        this.stop();
        return;
      }
      const next = this.firstMissing(r);
      this._inTransition.set(false);
      if (!next) {
        this.stop();
        return;
      }
      this.navigateTo(next);
    }, TRANSITION_DELAY_MS);
  }

  private navigateTo(step: TourStep): void {
    if (this.router.url.startsWith(step.route)) {
      this._currentStep.set(step);
    } else {
      this.router.navigateByUrl(step.route).then(() => this._currentStep.set(step));
    }
  }

  private firstMissing(r: TenantReadiness): TourStep | null {
    return TOUR_STEPS.find(s => r[s.readinessFlag] === false) ?? null;
  }
}
```

- [ ] **Step 2: Run tests, expect PASS**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/tour.service.spec.ts' --watch=false
```
Expected: 6/6 PASS.

- [ ] **Step 3: Commit**

```bash
git -C /Users/Gustavo.alves/Documents/personal/portfolio add frontend/src/app/features/onboarding/tour/tour.service.ts frontend/src/app/features/onboarding/tour/tour.service.spec.ts
git -C /Users/Gustavo.alves/Documents/personal/portfolio commit -m "feat(tour): add TourService with auto-advance on readiness changes"
```

---

## Section B — Tour overlay UI (PR2, brings the visual)

### Task B1: Write `TourBubbleComponent` failing spec

**Files:**
- Create: `frontend/src/app/shared/uis/tour-overlay/tour-bubble.component.spec.ts`

- [ ] **Step 1: Write the spec**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { TourBubbleComponent } from './tour-bubble.component';
import { TourStep } from '../../../features/onboarding/tour/tour-step.model';

const STEP: TourStep = {
  key: 'logo',
  readinessFlag: 'hasLogo',
  route: '/pro/salon',
  tourStep: 'logo',
  titleKey: 'pro.tour.steps.logo.title',
  descKey: 'pro.tour.steps.logo.desc',
};

const RECT = { x: 100, y: 200, width: 240, height: 80, top: 200, left: 100, right: 340, bottom: 280, toJSON: () => ({}) } as DOMRect;

describe('TourBubbleComponent', () => {
  let fixture: ComponentFixture<TourBubbleComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [
        TourBubbleComponent,
        TranslocoTestingModule.forRoot({ langs: { en: {} }, translocoConfig: { availableLangs: ['en'], defaultLang: 'en' } }),
      ],
    });
    fixture = TestBed.createComponent(TourBubbleComponent);
  });

  it('shows transition success line when inTransition is true', () => {
    fixture.componentRef.setInput('step', STEP);
    fixture.componentRef.setInput('progress', { done: 3, total: 6 });
    fixture.componentRef.setInput('inTransition', true);
    fixture.componentRef.setInput('targetRect', RECT);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="tour-bubble-success"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="tour-bubble-later"]')).toBeNull();
  });

  it('emits (close) when the "Later" button is clicked', () => {
    fixture.componentRef.setInput('step', STEP);
    fixture.componentRef.setInput('progress', { done: 0, total: 6 });
    fixture.componentRef.setInput('inTransition', false);
    fixture.componentRef.setInput('targetRect', RECT);
    fixture.detectChanges();
    let closed = false;
    fixture.componentInstance.close.subscribe(() => closed = true);
    fixture.nativeElement.querySelector('[data-testid="tour-bubble-later"]').click();
    expect(closed).toBeTrue();
  });
});
```

- [ ] **Step 2: Run, expect FAIL**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/tour-bubble.component.spec.ts' --watch=false
```

---

### Task B2: Implement `TourBubbleComponent`

**Files:**
- Create: `frontend/src/app/shared/uis/tour-overlay/tour-bubble.component.ts`
- Create: `frontend/src/app/shared/uis/tour-overlay/tour-bubble.component.html`
- Create: `frontend/src/app/shared/uis/tour-overlay/tour-bubble.component.scss`

- [ ] **Step 1: Write the component**

```typescript
import { Component, computed, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatIconModule } from '@angular/material/icon';
import { TourStep } from '../../../features/onboarding/tour/tour-step.model';

const BUBBLE_WIDTH = 320;
const BUBBLE_GAP = 14;
const ESTIMATED_HEIGHT = 200;

@Component({
  selector: 'app-tour-bubble',
  standalone: true,
  imports: [TranslocoPipe, MatIconModule],
  templateUrl: './tour-bubble.component.html',
  styleUrl: './tour-bubble.component.scss',
})
export class TourBubbleComponent {
  readonly step = input.required<TourStep>();
  readonly progress = input.required<{ done: number; total: number }>();
  readonly inTransition = input(false);
  readonly targetRect = input.required<DOMRect>();
  readonly close = output<void>();

  protected readonly bubblePosition = computed(() => {
    const r = this.targetRect();
    const fitsBelow = r.bottom + ESTIMATED_HEIGHT + BUBBLE_GAP < window.innerHeight;
    const left = Math.max(16, Math.min(window.innerWidth - BUBBLE_WIDTH - 16, r.left));
    return fitsBelow
      ? { top: r.bottom + BUBBLE_GAP, left, placement: 'below' as const }
      : { top: r.top - ESTIMATED_HEIGHT - BUBBLE_GAP, left, placement: 'above' as const };
  });
}
```

- [ ] **Step 2: Write the template**

```html
<div
  class="tour-bubble"
  [class.is-above]="bubblePosition().placement === 'above'"
  [style.top.px]="bubblePosition().top"
  [style.left.px]="bubblePosition().left"
>
  @if (inTransition()) {
    <p class="tour-bubble-success" data-testid="tour-bubble-success">
      <mat-icon aria-hidden="true">check_circle</mat-icon>
      <span>{{ 'pro.tour.transition.success' | transloco }}</span>
    </p>
  } @else {
    <h5 class="tour-bubble-title">{{ step().titleKey | transloco }}</h5>
    <p class="tour-bubble-desc">{{ step().descKey | transloco }}</p>
    <div class="tour-bubble-actions">
      <span class="tour-bubble-counter">{{ progress().done }} / {{ progress().total }}</span>
      <button
        type="button"
        class="tour-btn-text"
        data-testid="tour-bubble-later"
        (click)="close.emit()"
      >
        {{ 'pro.tour.actions.later' | transloco }}
      </button>
    </div>
  }
</div>
```

- [ ] **Step 3: Write the styles**

```scss
.tour-bubble {
  position: fixed;
  width: 320px;
  background: #fff;
  border-radius: 14px;
  padding: 14px 16px 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18);
  color: var(--pf-ink, #2b1f25);
  z-index: 802;
  font-family: inherit;
}

.tour-bubble::before {
  content: '';
  position: absolute;
  top: -7px;
  left: 28px;
  width: 14px;
  height: 14px;
  background: #fff;
  transform: rotate(45deg);
  box-shadow: -2px -2px 4px rgba(0, 0, 0, 0.04);
}

.tour-bubble.is-above::before {
  top: auto;
  bottom: -7px;
  box-shadow: 2px 2px 4px rgba(0, 0, 0, 0.04);
}

.tour-bubble-title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
}

.tour-bubble-desc {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.45;
  color: var(--pf-ink-mute, #666);
}

.tour-bubble-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tour-bubble-counter {
  flex: 1;
  font-size: 11px;
  color: var(--pf-ink-mute, #999);
}

.tour-btn-text {
  background: none;
  border: 0;
  color: var(--pf-ink-mute, #999);
  font-size: 12px;
  cursor: pointer;
  padding: 4px 8px;
  font-family: inherit;
}

.tour-btn-text:hover {
  color: var(--pf-rose, #c66075);
}

.tour-bubble-success {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--pf-rose, #c66075);
}

.tour-bubble-success mat-icon {
  flex-shrink: 0;
}
```

- [ ] **Step 4: Run, expect 2/2 PASS**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/tour-bubble.component.spec.ts' --watch=false
```

- [ ] **Step 5: Commit**

```bash
git -C /Users/Gustavo.alves/Documents/personal/portfolio add frontend/src/app/shared/uis/tour-overlay/tour-bubble.component.ts frontend/src/app/shared/uis/tour-overlay/tour-bubble.component.html frontend/src/app/shared/uis/tour-overlay/tour-bubble.component.scss frontend/src/app/shared/uis/tour-overlay/tour-bubble.component.spec.ts
git -C /Users/Gustavo.alves/Documents/personal/portfolio commit -m "feat(tour): add TourBubbleComponent (style A)"
```

---

### Task B3: Write `TourOverlayComponent` failing spec

**Files:**
- Create: `frontend/src/app/shared/uis/tour-overlay/tour-overlay.component.spec.ts`

- [ ] **Step 1: Write the spec**

```typescript
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { TourOverlayComponent } from './tour-overlay.component';
import { TourService } from '../../../features/onboarding/tour/tour.service';
import { TourStep } from '../../../features/onboarding/tour/tour-step.model';

const NAME_STEP: TourStep = {
  key: 'name',
  readinessFlag: 'name',
  route: '/pro/salon',
  tourStep: 'name',
  titleKey: 'pro.tour.steps.name.title',
  descKey: 'pro.tour.steps.name.desc',
};

describe('TourOverlayComponent', () => {
  let fixture: ComponentFixture<TourOverlayComponent>;
  let tourActive: ReturnType<typeof signal<boolean>>;
  let tourCurrent: ReturnType<typeof signal<TourStep | null>>;
  let tourTransition: ReturnType<typeof signal<boolean>>;
  let stopSpy: jasmine.Spy;

  function setup() {
    tourActive = signal(false);
    tourCurrent = signal<TourStep | null>(null);
    tourTransition = signal(false);
    stopSpy = jasmine.createSpy('stop');
    const tourStub = {
      active: tourActive,
      currentStep: tourCurrent,
      inTransition: tourTransition,
      progress: signal({ done: 0, total: 6 }),
      stop: stopSpy,
    };

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: TourService, useValue: tourStub },
      ],
      imports: [
        TourOverlayComponent,
        TranslocoTestingModule.forRoot({ langs: { en: {} }, translocoConfig: { availableLangs: ['en'], defaultLang: 'en' } }),
      ],
    });
    fixture = TestBed.createComponent(TourOverlayComponent);
    fixture.detectChanges();
  }

  afterEach(() => {
    document.querySelectorAll('[data-tour-step]').forEach(el => el.remove());
  });

  it('renders nothing while tour is inactive', () => {
    setup();
    expect(fixture.nativeElement.querySelector('.tour-overlay')).toBeNull();
  });

  it('binds to [data-tour-step="name"] when current step is the name step', () => {
    setup();
    const target = document.createElement('div');
    target.setAttribute('data-tour-step', 'name');
    target.style.cssText = 'position:fixed;top:50px;left:60px;width:200px;height:40px';
    document.body.appendChild(target);

    tourActive.set(true);
    tourCurrent.set(NAME_STEP);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.tour-halo')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-tour-bubble')).not.toBeNull();
  });

  it('retries 3 times then stops the tour when target is missing', fakeAsync(() => {
    setup();
    tourActive.set(true);
    tourCurrent.set(NAME_STEP);
    fixture.detectChanges();
    // each retry waits 500ms; 3 retries = 1500ms, then warn + stop
    tick(500);
    tick(500);
    tick(500);
    expect(stopSpy).toHaveBeenCalled();
  }));

  it('cleans up observers and listeners on destroy', () => {
    setup();
    const target = document.createElement('div');
    target.setAttribute('data-tour-step', 'name');
    document.body.appendChild(target);

    tourActive.set(true);
    tourCurrent.set(NAME_STEP);
    fixture.detectChanges();
    const removeSpy = spyOn(window, 'removeEventListener').and.callThrough();
    fixture.destroy();
    expect(removeSpy).toHaveBeenCalledWith('scroll', jasmine.any(Function));
  });
});
```

- [ ] **Step 2: Run, expect FAIL (compile error)**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/tour-overlay.component.spec.ts' --watch=false
```

---

### Task B4: Implement `TourOverlayComponent`

**Files:**
- Create: `frontend/src/app/shared/uis/tour-overlay/tour-overlay.component.ts`
- Create: `frontend/src/app/shared/uis/tour-overlay/tour-overlay.component.html`
- Create: `frontend/src/app/shared/uis/tour-overlay/tour-overlay.component.scss`

- [ ] **Step 1: Write the component**

```typescript
import { Component, OnDestroy, effect, inject, signal } from '@angular/core';
import { TourService } from '../../../features/onboarding/tour/tour.service';
import { TourBubbleComponent } from './tour-bubble.component';

const RETRY_DELAY_MS = 500;
const MAX_RETRIES = 3;

@Component({
  selector: 'app-tour-overlay',
  standalone: true,
  imports: [TourBubbleComponent],
  templateUrl: './tour-overlay.component.html',
  styleUrl: './tour-overlay.component.scss',
})
export class TourOverlayComponent implements OnDestroy {
  protected readonly tour = inject(TourService);
  protected readonly targetRect = signal<DOMRect | null>(null);

  private resizeObs: ResizeObserver | null = null;
  private currentEl: HTMLElement | null = null;
  private readonly scrollHandler = () => this.measureCurrent();

  constructor() {
    effect(() => {
      const step = this.tour.currentStep();
      const active = this.tour.active();
      if (!step || !active) {
        this.cleanup();
        return;
      }
      this.bindToTarget(step.tourStep);
    });
  }

  ngOnDestroy(): void {
    this.cleanup();
  }

  private bindToTarget(tourStep: string, attempt = 0): void {
    const el = document.querySelector<HTMLElement>(`[data-tour-step="${tourStep}"]`);
    if (!el) {
      if (attempt < MAX_RETRIES) {
        setTimeout(() => this.bindToTarget(tourStep, attempt + 1), RETRY_DELAY_MS);
      } else {
        console.warn(`[tour] Target [data-tour-step="${tourStep}"] not found after ${MAX_RETRIES} retries — closing tour.`);
        this.tour.stop();
      }
      return;
    }
    this.cleanup();
    this.currentEl = el;
    this.targetRect.set(el.getBoundingClientRect());
    this.resizeObs = new ResizeObserver(() => this.measureCurrent());
    this.resizeObs.observe(el);
    window.addEventListener('scroll', this.scrollHandler, { passive: true });
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  private measureCurrent(): void {
    if (!this.currentEl) return;
    this.targetRect.set(this.currentEl.getBoundingClientRect());
  }

  private cleanup(): void {
    this.resizeObs?.disconnect();
    this.resizeObs = null;
    window.removeEventListener('scroll', this.scrollHandler);
    this.currentEl = null;
    this.targetRect.set(null);
  }
}
```

- [ ] **Step 2: Write the template**

```html
@if (tour.active() && tour.currentStep() && targetRect(); as rect) {
  <div
    class="tour-overlay"
    [style.--target-x.px]="rect.x"
    [style.--target-y.px]="rect.y"
    [style.--target-w.px]="rect.width"
    [style.--target-h.px]="rect.height"
  ></div>
  <div
    class="tour-halo"
    [style.left.px]="rect.x - 4"
    [style.top.px]="rect.y - 4"
    [style.width.px]="rect.width + 8"
    [style.height.px]="rect.height + 8"
  ></div>
  <app-tour-bubble
    [step]="tour.currentStep()!"
    [progress]="tour.progress()"
    [inTransition]="tour.inTransition()"
    [targetRect]="rect"
    (close)="tour.stop()"
  />
}
```

- [ ] **Step 3: Write the styles**

```scss
:host {
  display: contents;
}

.tour-overlay {
  position: fixed;
  inset: 0;
  background: rgba(43, 31, 37, 0.55);
  backdrop-filter: blur(1px);
  z-index: 800;
  pointer-events: auto;
  /* Carve out the target rect so clicks pass through. */
  clip-path: polygon(
    0 0,
    100% 0,
    100% 100%,
    0 100%,
    0 0,
    var(--target-x) var(--target-y),
    var(--target-x) calc(var(--target-y) + var(--target-h)),
    calc(var(--target-x) + var(--target-w)) calc(var(--target-y) + var(--target-h)),
    calc(var(--target-x) + var(--target-w)) var(--target-y),
    var(--target-x) var(--target-y)
  );
  animation: tour-overlay-fade-in 200ms ease;
}

.tour-halo {
  position: fixed;
  border: 3px solid var(--pf-rose, #c66075);
  border-radius: 12px;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.3);
  pointer-events: none;
  z-index: 801;
  animation: tour-halo-pulse 2s ease-in-out infinite;
}

@keyframes tour-halo-pulse {
  0%, 100% { box-shadow: 0 0 0 2px rgba(198, 96, 117, 0.3); }
  50%      { box-shadow: 0 0 0 6px rgba(198, 96, 117, 0.15); }
}

@keyframes tour-overlay-fade-in {
  from { opacity: 0; }
  to   { opacity: 1; }
}
```

- [ ] **Step 4: Run, expect 4/4 PASS**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/tour-overlay.component.spec.ts' --watch=false
```

- [ ] **Step 5: Commit**

```bash
git -C /Users/Gustavo.alves/Documents/personal/portfolio add frontend/src/app/shared/uis/tour-overlay/tour-overlay.component.ts frontend/src/app/shared/uis/tour-overlay/tour-overlay.component.html frontend/src/app/shared/uis/tour-overlay/tour-overlay.component.scss frontend/src/app/shared/uis/tour-overlay/tour-overlay.component.spec.ts
git -C /Users/Gustavo.alves/Documents/personal/portfolio commit -m "feat(tour): add TourOverlayComponent with clip-path cutout + retry"
```

---

### Task B5: Add i18n keys

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Edit `fr.json` to add the `pro.tour` block**

Locate the `"pro"` object. Inside it, add a `"tour"` sub-block alongside `dashboard`, `salon`, etc:

```json
"tour": {
  "steps": {
    "name": {
      "title": "Renseignez le nom de votre salon",
      "desc": "Le nom affiché à vos clientes sur votre page publique, vos confirmations et vos factures."
    },
    "contact": {
      "title": "Ajoutez vos coordonnées",
      "desc": "Adresse complète + téléphone ou email. C'est ce qui permet aux clientes de vous trouver et de vous contacter."
    },
    "logo": {
      "title": "Ajoutez votre logo",
      "desc": "Une image carrée représentant votre salon. Visible sur votre vitrine et dans les confirmations."
    },
    "categories": {
      "title": "Créez votre première catégorie",
      "desc": "Regroupez vos soins par famille (visage, manucure, massage…) pour aider vos clientes à s'y retrouver."
    },
    "cares": {
      "title": "Ajoutez votre premier soin",
      "desc": "Au moins un soin actif est nécessaire pour publier votre salon. Vous pourrez en ajouter d'autres ensuite."
    },
    "openingHours": {
      "title": "Définissez vos horaires",
      "desc": "Les jours et heures où vous recevez. Vous pourrez gérer fermetures et exceptions plus tard."
    }
  },
  "transition": {
    "success": "Sauvegardé — étape suivante…"
  },
  "actions": {
    "later": "Plus tard"
  }
}
```

- [ ] **Step 2: Mirror in `en.json`**

```json
"tour": {
  "steps": {
    "name": {
      "title": "Set your salon's name",
      "desc": "The name shown to clients on your public page, confirmations, and invoices."
    },
    "contact": {
      "title": "Add your contact details",
      "desc": "Full address + phone or email. This is how clients find and reach you."
    },
    "logo": {
      "title": "Add your logo",
      "desc": "A square image representing your salon. Shown on your storefront and in confirmations."
    },
    "categories": {
      "title": "Create your first category",
      "desc": "Group your services by family (facial, nails, massage…) so clients can browse easily."
    },
    "cares": {
      "title": "Add your first service",
      "desc": "At least one active service is required to publish. You can add more afterwards."
    },
    "openingHours": {
      "title": "Set your opening hours",
      "desc": "The days and times you welcome clients. Closures and exceptions can be added later."
    }
  },
  "transition": {
    "success": "Saved — next step…"
  },
  "actions": {
    "later": "Later"
  }
}
```

- [ ] **Step 3: Validate JSON**

```bash
node -e "JSON.parse(require('fs').readFileSync('/Users/Gustavo.alves/Documents/personal/portfolio/frontend/public/i18n/fr.json','utf8')); JSON.parse(require('fs').readFileSync('/Users/Gustavo.alves/Documents/personal/portfolio/frontend/public/i18n/en.json','utf8')); console.log('OK')"
```
Expected: `OK`.

- [ ] **Step 4: Commit**

```bash
git -C /Users/Gustavo.alves/Documents/personal/portfolio add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git -C /Users/Gustavo.alves/Documents/personal/portfolio commit -m "feat(i18n): add pro.tour.* keys (FR/EN)"
```

---

### Task B6: Mount overlay in `ProShellComponent`

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-shell.component.ts`
- Modify: `frontend/src/app/pages/pro/pro-shell.component.html`

- [ ] **Step 1: Add import + standalone component to imports**

In `pro-shell.component.ts`, add the import at the top:
```typescript
import { TourOverlayComponent } from '../../shared/uis/tour-overlay/tour-overlay.component';
```

Add `TourOverlayComponent` to the component's `imports` array:
```typescript
@Component({
  selector: 'app-pro-shell',
  standalone: true,
  imports: [RouterOutlet, OnboardingIndicatorComponent, TourOverlayComponent],
  // ... rest unchanged
})
```

- [ ] **Step 2: Add `<app-tour-overlay />` to the template**

In `pro-shell.component.html`, add the tag after `<router-outlet />`:
```html
<router-outlet />
<app-onboarding-indicator />
<app-tour-overlay />
```

(Keep whatever existing markup is around — only add the new tag.)

- [ ] **Step 3: Type-check + run pro-shell spec**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx tsc --noEmit -p tsconfig.app.json
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/pro-shell.component.spec.ts' --watch=false
```
Expected: type-check clean, spec passes (existing tests unaffected).

- [ ] **Step 4: Commit**

```bash
git -C /Users/Gustavo.alves/Documents/personal/portfolio add frontend/src/app/pages/pro/pro-shell.component.ts frontend/src/app/pages/pro/pro-shell.component.html
git -C /Users/Gustavo.alves/Documents/personal/portfolio commit -m "feat(tour): mount TourOverlayComponent in pro shell"
```

---

## Section C — Wiring (PR3, ships the feature end-to-end)

### Task C1: Extend `OnboardingStepKey` to 6 keys + update model

**Files:**
- Modify: `frontend/src/app/features/onboarding/onboarding-step.model.ts`

- [ ] **Step 1: Replace the `OnboardingStepKey` type**

Find:
```typescript
export type OnboardingStepKey = 'name' | 'cares' | 'openingHours';
```

Replace with:
```typescript
export type OnboardingStepKey =
  | 'name'
  | 'contact'
  | 'logo'
  | 'categories'
  | 'cares'
  | 'openingHours';
```

- [ ] **Step 2: Type-check, expect failures in fixtures**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx tsc --noEmit -p tsconfig.app.json
```
Expected: TypeScript may flag `OnboardingChecklistService.buildSteps` if its return uses union member assignments incompatible with the new wider union. Check output. If empty, the next task will trigger the actual fixture updates.

- [ ] **Step 3: Don't commit yet** — bundle with the next task.

---

### Task C2: Extend `buildSteps` to return 6 entries

**Files:**
- Modify: `frontend/src/app/features/onboarding/onboarding-checklist.service.ts`
- Modify: `frontend/src/app/features/onboarding/onboarding-checklist.service.spec.ts`

- [ ] **Step 1: Update `buildSteps`**

Replace the file content with:

```typescript
import { Injectable } from '@angular/core';
import { TenantReadiness } from '../dashboard/models/dashboard.model';
import { OnboardingProgress, OnboardingStep } from './onboarding-step.model';

@Injectable({ providedIn: 'root' })
export class OnboardingChecklistService {
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
        key: 'contact',
        done: readiness.hasContact,
        link: '/pro/salon',
        queryParams: readiness.hasContact ? null : { focus: 'contact' },
      },
      {
        key: 'logo',
        done: readiness.hasLogo,
        link: '/pro/salon',
        queryParams: readiness.hasLogo ? null : { focus: 'logo' },
      },
      {
        key: 'categories',
        done: readiness.hasCategory,
        link: '/pro/cares',
        queryParams: readiness.hasCategory ? null : { focus: 'categories' },
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
}
```

- [ ] **Step 2: Update the spec fixtures**

In `onboarding-checklist.service.spec.ts`, find any test that asserts on the number of steps or specific keys. The existing `readiness()` helper builds a `TenantReadiness` (which already has `hasContact`, `hasLogo`, etc.). Update assertions:
- Tests that previously expected `buildSteps(...).length === 3` now expect `=== 6`.
- Tests that asserted on the order/keys must include `contact`, `logo`, `categories` between `name` and `cares`.

If the existing tests don't make explicit assertions on length or order, no change is needed — the test suite will still pass on the wider output.

Add at minimum one new test:
```typescript
it('returns 6 steps in wizard order', () => {
  const steps = service.buildSteps(readiness());
  expect(steps.map(s => s.key)).toEqual([
    'name', 'contact', 'logo', 'categories', 'cares', 'openingHours',
  ]);
});
```

- [ ] **Step 3: Run the spec**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/onboarding-checklist.service.spec.ts' --watch=false
```
Expected: all tests pass (existing + new).

- [ ] **Step 4: Run the indicator spec to make sure it still passes**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/onboarding-indicator.component.spec.ts' --watch=false
```
Expected: passes.

- [ ] **Step 5: Commit**

```bash
git -C /Users/Gustavo.alves/Documents/personal/portfolio add frontend/src/app/features/onboarding/onboarding-step.model.ts frontend/src/app/features/onboarding/onboarding-checklist.service.ts frontend/src/app/features/onboarding/onboarding-checklist.service.spec.ts
git -C /Users/Gustavo.alves/Documents/personal/portfolio commit -m "feat(onboarding-checklist): extend to 6 conditions for the guided tour"
```

---

### Task C3: Wire `OnboardingIndicatorComponent` to start the tour

**Files:**
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.ts`
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.html`
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.spec.ts`

- [ ] **Step 1: Add `TourService` injection + `onStepClick` method**

In `onboarding-indicator.component.ts`, add the import:
```typescript
import { TourService } from '../../../features/onboarding/tour/tour.service';
import { TOUR_STEPS } from '../../../features/onboarding/tour/tour-steps';
import { OnboardingStepKey } from '../../../features/onboarding/onboarding-step.model';
```

Inside the class, add the injection:
```typescript
private readonly tour = inject(TourService);
```

Add the new method (place it near `onPillClick`):
```typescript
protected onStepClick(stepKey: OnboardingStepKey, event?: Event): void {
  if (TOUR_STEPS.some(s => s.key === stepKey)) {
    event?.preventDefault();
    this.tour.start(stepKey);
    return;
  }
  // Fallback: legacy navigation for keys the tour does not cover (none today,
  // but keeps the indicator robust if the catalogue shrinks later).
  const step = this.steps().find(s => s.key === stepKey);
  if (step) this.router.navigate([step.link], { queryParams: step.queryParams ?? undefined });
}
```

Update `handleSheetResult` to route the `step` action through the same gate:
```typescript
case 'step': {
  this.onStepClick(result.stepKey);
  return;
}
```

- [ ] **Step 2: Update the desktop stepper template**

In `onboarding-indicator.component.html`, find the desktop stepper `<a class="stepper-link" [routerLink]="step.link" ...>`. Replace it with a button that calls `onStepClick`:

Find:
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

Replace with:
```html
<button
  type="button"
  class="stepper-link"
  (click)="onStepClick(step.key, $event)"
  [matTooltip]="('pro.dashboard.checklist.' + step.key + 'Desc') | transloco"
  matTooltipPosition="below"
  [attr.aria-current]="!step.done && progress().nextKey === step.key ? 'step' : null"
  [attr.data-testid]="'stepper-step-' + step.key"
>
```

(Match the closing tag from `</a>` to `</button>`.)

You can also drop `RouterLink` from the component's `imports` array if no other element uses it — verify with grep before removing.

- [ ] **Step 3: Add a test in the indicator spec**

In `onboarding-indicator.component.spec.ts`, add a new `it`:

```typescript
it('clicking a covered step calls tour.start with the step key', () => {
  // Mock TourService — replace the existing TestBed providers to inject the mock.
  // If the existing setup uses real TourService, swap to a mock here:
  const tourMock = jasmine.createSpyObj('TourService', ['start', 'stop'], {
    active: signal(false), currentStep: signal(null), inTransition: signal(false), progress: signal({ done: 0, total: 6 }),
  });
  TestBed.overrideProvider(TourService, { useValue: tourMock });
  // ... rest of the existing setup; render the desktop stepper variant.

  const link = fixture.nativeElement.querySelector('[data-testid="stepper-step-name"]') as HTMLButtonElement;
  link.click();
  expect(tourMock.start).toHaveBeenCalledWith('name');
});
```

If the existing `TestBed` setup doesn't mock `TourService`, you'll need to provide a stub at the top of the file. Use the pattern from the spec for `TourOverlayComponent` (Task B3): a small `TourService` stub object with the signals used by the indicator (`active`, etc.) plus `start`/`stop` spies. Add it to `providers`. Existing tests don't exercise tour interactions and remain green.

- [ ] **Step 4: Run the spec**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/onboarding-indicator.component.spec.ts' --watch=false
```
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git -C /Users/Gustavo.alves/Documents/personal/portfolio add frontend/src/app/shared/features/onboarding-indicator/
git -C /Users/Gustavo.alves/Documents/personal/portfolio commit -m "feat(onboarding-indicator): launch TourService on step click"
```

---

### Task C4: Add `data-tour-step` markers on `/pro/salon`

**Files:**
- Modify: `frontend/src/app/features/salon-profile/salon-profile.component.html`

- [ ] **Step 1: Mark the name field**

Find:
```html
<mat-form-field appearance="outline" class="full-width" appFocusOnQueryParam="name">
  <mat-label>{{ 'pro.salon.name' | transloco }}</mat-label>
  <input matInput [ngModel]="name()" (ngModelChange)="name.set($event); markDirty()" required maxlength="100" name="salonName" />
```

Add `data-tour-step="name"` on the `<mat-form-field>`:
```html
<mat-form-field appearance="outline" class="full-width" appFocusOnQueryParam="name" data-tour-step="name">
```

- [ ] **Step 2: Mark the logo wrapper**

Find:
```html
<div class="logo-wrapper" (click)="triggerLogoUpload()">
```

Add the attribute:
```html
<div class="logo-wrapper" (click)="triggerLogoUpload()" data-tour-step="logo">
```

- [ ] **Step 3: Mark the contact section**

The contact fields span lines ~86-119 (addressStreet, addressPostalCode, addressCity, phone, contactEmail). They are inside the same `<form>` but no semantic wrapper currently groups them.

Wrap them in a `<section>` (NOT a `<fieldset>` — Material form fields don't render correctly inside fieldsets). Find the line just before the first contact field (`<input matInput ... name="addressStreet">`) and add:

```html
<section class="contact-section" data-tour-step="contact">
```

Find the line just after the last contact field (`<input ... name="contactEmail">` and any closing `</mat-form-field>`/`</div>` for it) and close the section:

```html
</section>
```

Don't include the `siret` field (line 125) — it's not part of the contact requirement. The `<section>` wraps only addressStreet, addressPostalCode, addressCity, addressCountry (if present), phone, contactEmail.

Add a minimal style to `salon-profile.component.scss` if needed:
```scss
.contact-section { display: contents; }
```
This keeps the wrapper transparent to the existing layout (no spacing changes).

- [ ] **Step 4: Verify type-check + visual sanity**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx tsc --noEmit -p tsconfig.app.json
```
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git -C /Users/Gustavo.alves/Documents/personal/portfolio add frontend/src/app/features/salon-profile/salon-profile.component.html
git -C /Users/Gustavo.alves/Documents/personal/portfolio commit -m "feat(salon-profile): add data-tour-step markers (name, contact, logo)"
```

---

### Task C5: Add `data-tour-step` markers on `/pro/cares`

**Files:**
- Modify: `frontend/src/app/features/cares/cares.component.html`

- [ ] **Step 1: Mark the Add Category button**

Around line 40, find:
```html
(click)="onAddCategory()"
```
Locate the parent `<button>` for that handler. Add `data-tour-step="categories"` on it.

- [ ] **Step 2: Mark the Add Care button**

Around line 56, find:
```html
<button mat-raised-button color="primary" class="add-btn" (click)="onAddCare()">
```
Add `data-tour-step="add-care"`:
```html
<button mat-raised-button color="primary" class="add-btn" (click)="onAddCare()" data-tour-step="add-care">
```

- [ ] **Step 3: Verify**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx tsc --noEmit -p tsconfig.app.json
```
Expected: no output.

- [ ] **Step 4: Commit**

```bash
git -C /Users/Gustavo.alves/Documents/personal/portfolio add frontend/src/app/features/cares/cares.component.html
git -C /Users/Gustavo.alves/Documents/personal/portfolio commit -m "feat(cares): add data-tour-step markers (categories, add-care)"
```

---

### Task C6: Add `data-tour-step` marker on `/pro/planning`

**Files:**
- Modify: `frontend/src/app/features/availability/availability.component.html`

- [ ] **Step 1: Mark the page root**

Find line 1:
```html
<div class="availability-page">
```

Replace with:
```html
<div class="availability-page" data-tour-step="opening-hours">
```

- [ ] **Step 2: Verify**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx tsc --noEmit -p tsconfig.app.json
```
Expected: no output.

- [ ] **Step 3: Commit**

```bash
git -C /Users/Gustavo.alves/Documents/personal/portfolio add frontend/src/app/features/availability/availability.component.html
git -C /Users/Gustavo.alves/Documents/personal/portfolio commit -m "feat(availability): add data-tour-step marker (opening-hours)"
```

---

### Task C7: Manual smoke test

- [ ] **Step 1: Start the app**

In two terminals:
```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && /usr/local/bin/mvn spring-boot:run
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm start
```

- [ ] **Step 2: Walk through the tour**

  1. Register a fresh pro at `/auth/register/pro`. Confirm the dashboard loads.
  2. The bandeau onboarding should show 6 conditions with several missing.
  3. Click "Renseignez le nom de votre salon" on the bandeau. Expect:
     - Navigation to `/pro/salon`.
     - Dark overlay covers the page, halo glows around the salon-name field, bubble appears under it.
     - Reste de la page non interactif, le champ name reste cliquable et éditable.
  4. Type a name, save. Within ~1.5s the bubble shows "✓ Sauvegardé — étape suivante…", then the spotlight slides to the next missing condition (likely `contact` if address is empty).
  5. When all 6 conditions are met, the tour closes automatically and the bandeau shows the Publish button.

- [ ] **Step 3: Test the failure path**

  1. From the dashboard, click "Publier" while several conditions are missing.
  2. The `PublishMissingDialog` opens, listing each missing condition with "Y aller".
  3. Clicking "Y aller" navigates to the relevant page (e.g. `/pro/salon`).
  4. The tour does NOT auto-start from the dialog click — the pro can re-launch from the bandeau if they want.

- [ ] **Step 4: Test edge cases**

  - Resize the window during the tour → the halo and bubble should follow the target.
  - Scroll while the tour is open → halo follows.
  - Click "Plus tard" in the bubble → tour closes, bandeau remains.
  - Open a modal (e.g. "Ajouter un soin") → modal sits above the tour overlay.

- [ ] **Step 5: If anything fails, document it and decide whether to fix in this PR or follow up.**

No commit needed — this is verification.

---

## Self-Review Checklist (already applied)

**Spec coverage:**
- ✅ Tour service with auto-advance (A3-A4)
- ✅ Tour overlay with clip-path cutout + retry (B3-B4)
- ✅ Tour bubble with above/below positioning + transition state (B1-B2)
- ✅ Onboarding-checklist extended to 6 keys (C1-C2)
- ✅ Indicator wired to launch tour (C3)
- ✅ `data-tour-step` markers on /pro/salon (C4), /pro/cares (C5), /pro/planning (C6)
- ✅ FR/EN i18n bundle (B5)
- ✅ Mount overlay in pro-shell (B6)
- ✅ Manual smoke test plan (C7)

**Type consistency:**
- `WizardStepKey` defined in A1, used in A2 (catalogue), A4 (service), C3 (indicator). All references match.
- `TourStep.tourStep` values match the `data-tour-step` attribute values posted in C4-C6 (`name`, `contact`, `logo`, `categories`, `add-care`, `opening-hours`).
- `OnboardingStepKey` widened in C1 to match the 6 keys used by `buildSteps` (C2) and consumed by the indicator's `onStepClick` (C3).

**Placeholder scan:** No "TBD"/"TODO"/"add appropriate"/"similar to" — every code step shows the actual code.

---

## Execution Order

Recommended: A1 → A2 → A3 → A4 → B1 → B2 → B3 → B4 → B5 → B6 → C1 → C2 → C3 → C4 → C5 → C6 → C7.

The three sections also stand as logical PR boundaries if you want to ship incrementally:
- **PR1 (A1-A4):** types + service + tests, no UI yet.
- **PR2 (B1-B6):** overlay + bubble + i18n + mount in shell. Tour reachable but no entry point yet — start it from devtools (`tourService.start()`) to verify.
- **PR3 (C1-C7):** indicator wiring + DOM markers + smoke test. End-to-end usable.
