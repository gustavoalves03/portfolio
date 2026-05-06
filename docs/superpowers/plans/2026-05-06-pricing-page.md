# Pricing Page (Jalon 4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a dedicated `/pricing` landing page for pros: a parallax hero, a "your numbers come to life" section with three scroll-triggered animated widgets inside a mock browser frame, a four-card features grid, a placeholder trial card (Stripe stub), and a final CTA.

**Architecture:** A standalone `PricingComponent` lazy-loaded under the `/pricing` route (replacing the current redirect to register-pro). Reusable building blocks live under `shared/uis/`: `<app-parallax-hero>` for the top section, `<app-mock-browser>` for the demo frame, plus three widget components — `<app-revenue-widget>`, `<app-calendar-widget>`, `<app-reviews-widget>`. A pair of small zero-dependency utilities — `useIntersection()` (signal-based IntersectionObserver) and `useCountUp()` (RAF-driven number animation) — keep the widgets concise. All animations are CSS + RAF + signals; SSR renders the final state, client hydrates with animation triggered by an `IntersectionObserver` once the demo section enters the viewport.

**Tech Stack:** Angular 20 (standalone, signals, zoneless, SSR-ready), Tailwind + SCSS, Transloco i18n. No new external dependencies.

**Spec reference:** `docs/superpowers/specs/2026-05-06-vitrine-preview-onboarding-pc-design.md` — Jalon 4 section.

**Branch:** `feat/pricing-page` (create from `main` after Jalon 3 has been merged).

---

## File Structure

**New files (15):**

| Path | Responsibility |
|------|----------------|
| `frontend/src/app/shared/utils/use-intersection.ts` | `useIntersection(elRef, threshold)` returns a `Signal<boolean>` that flips true once and stays. SSR-safe (returns false). |
| `frontend/src/app/shared/utils/use-intersection.spec.ts` | Unit tests with stubbed IntersectionObserver. |
| `frontend/src/app/shared/utils/use-count-up.ts` | `useCountUp(target, durationMs, startSignal)` returns a `Signal<number>` that ramps from 0 to `target` once `startSignal()` is true. RAF-driven, zoneless-friendly. |
| `frontend/src/app/shared/utils/use-count-up.spec.ts` | Unit tests with `jasmine.clock()`. |
| `frontend/src/app/shared/uis/parallax-hero/parallax-hero.component.ts` | Standalone parallax hero; `<ng-content>` for overlay. SSR-safe. |
| `frontend/src/app/shared/uis/parallax-hero/parallax-hero.component.html` | Template. |
| `frontend/src/app/shared/uis/parallax-hero/parallax-hero.component.scss` | Styles. |
| `frontend/src/app/shared/uis/parallax-hero/parallax-hero.component.spec.ts` | Tests. |
| `frontend/src/app/shared/uis/mock-browser/mock-browser.component.ts` | Decorative browser-frame wrapper with `<ng-content>`. |
| `frontend/src/app/shared/uis/mock-browser/mock-browser.component.html` | Template. |
| `frontend/src/app/shared/uis/mock-browser/mock-browser.component.scss` | Styles. |
| `frontend/src/app/shared/uis/mock-browser/mock-browser.component.spec.ts` | Tests. |
| `frontend/src/app/pages/pricing/pricing.component.ts` | Top-level page. |
| `frontend/src/app/pages/pricing/pricing.component.html` | Template (hero, demo, features, plan, final CTA). |
| `frontend/src/app/pages/pricing/pricing.component.scss` | Styles for sections + widgets. |
| `frontend/src/app/pages/pricing/pricing.component.spec.ts` | Tests. |
| `frontend/src/app/pages/pricing/widgets/revenue-widget.component.ts` | KPI card: count-up CA + sparkline + trend chip. |
| `frontend/src/app/pages/pricing/widgets/revenue-widget.component.html` | Template. |
| `frontend/src/app/pages/pricing/widgets/revenue-widget.component.scss` | Styles. |
| `frontend/src/app/pages/pricing/widgets/revenue-widget.component.spec.ts` | Tests. |
| `frontend/src/app/pages/pricing/widgets/calendar-widget.component.ts` | 7×6 grid filling cascade. |
| `frontend/src/app/pages/pricing/widgets/calendar-widget.component.html` | Template. |
| `frontend/src/app/pages/pricing/widgets/calendar-widget.component.scss` | Styles. |
| `frontend/src/app/pages/pricing/widgets/calendar-widget.component.spec.ts` | Tests. |
| `frontend/src/app/pages/pricing/widgets/reviews-widget.component.ts` | 5 stars cascade + count-up + quotes fade-in. |
| `frontend/src/app/pages/pricing/widgets/reviews-widget.component.html` | Template. |
| `frontend/src/app/pages/pricing/widgets/reviews-widget.component.scss` | Styles. |
| `frontend/src/app/pages/pricing/widgets/reviews-widget.component.spec.ts` | Tests. |

**Asset files (1):**

| Path | Responsibility |
|------|----------------|
| `frontend/public/pricing/parallax-hero.jpg` | Parallax hero background. Placeholder Pexels skincare image. |

**Modified files (3):**

| Path | Change |
|------|--------|
| `frontend/src/app/app.routes.ts:22-25` | Route `/pricing` now loads `PricingComponent` (was redirecting to register-pro). The `/register/pro` route already handles the registration flow separately. |
| `frontend/public/i18n/fr.json` | Add `pricing.*` block. |
| `frontend/public/i18n/en.json` | Same. |

---

## Conventions

- Standalone components, signals, `inject()`, `@if` / `@for`. Zoneless tests.
- `provideRouter([])`, `provideHttpClient()`, `provideHttpClientTesting()`, `provideZonelessChangeDetection()` in tests.
- SSR: `isPlatformBrowser(PLATFORM_ID)` for any browser-only code (matchMedia, IntersectionObserver, requestAnimationFrame).
- i18n: BOTH `frontend/public/i18n/fr.json` AND `en.json` updated together. No hardcoded user-facing text.
- Conventional Commits.
- No new external dependencies. CSS + RAF + signals + IntersectionObserver only.
- Follow the patterns established by Jalon 3 (`HeroVideoComponent`, `SalonCarouselComponent`) — `input.required<T>()` for required inputs, `signal()` for state, `computed()` for derivations.

---

## Task 1: Asset placeholder for parallax hero

**Files:**
- Create: `frontend/public/pricing/parallax-hero.jpg`

- [ ] **Step 1: Create the directory**

```bash
mkdir -p /Users/Gustavo.alves/Documents/personal/portfolio/frontend/public/pricing
```

- [ ] **Step 2: Download placeholder**

Pick a free-licence Pexels image (skincare studio interior, soft lighting, ~ 1920×1080):

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
curl -L "https://images.pexels.com/photos/3997991/pexels-photo-3997991.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&dpr=1" \
  -o frontend/public/pricing/parallax-hero.jpg
```

If the URL 404s, try:

```bash
curl -L "https://images.pexels.com/photos/3993455/pexels-photo-3993455.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&dpr=1" \
  -o frontend/public/pricing/parallax-hero.jpg
```

Or any other Pexels skincare/beauty image at https://www.pexels.com/search/skincare/.

Verify size (target < 250 KB):

```bash
ls -lh frontend/public/pricing/parallax-hero.jpg
file frontend/public/pricing/parallax-hero.jpg
```

If > 300 KB, retry with `&w=1600`.

- [ ] **Step 3: Commit**

```bash
git add frontend/public/pricing/
git commit -m "chore(pricing): add placeholder parallax hero image"
```

---

## Task 2: useIntersection helper

**Files:**
- Create: `frontend/src/app/shared/utils/use-intersection.ts`
- Create: `frontend/src/app/shared/utils/use-intersection.spec.ts`

A signal that flips `true` once when the host element enters the viewport (sticky one-shot — never goes back to false). SSR-safe.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/shared/utils/use-intersection.spec.ts`:

```typescript
import { Component, ElementRef, PLATFORM_ID, viewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { useIntersection } from './use-intersection';

@Component({
  standalone: true,
  template: `<div #target>hello</div>`,
})
class HostComponent {
  readonly target = viewChild.required<ElementRef<HTMLElement>>('target');
  readonly visible = useIntersection(this.target, 0.3);
}

describe('useIntersection', () => {
  let originalIO: typeof window.IntersectionObserver;
  let observers: Array<{
    cb: IntersectionObserverCallback;
    instance: IntersectionObserver;
  }>;

  beforeEach(() => {
    observers = [];
    originalIO = window.IntersectionObserver;
    (window as any).IntersectionObserver = class {
      constructor(public cb: IntersectionObserverCallback) {
        observers.push({ cb, instance: this as any });
      }
      observe(): void {}
      unobserve(): void {}
      disconnect(): void {}
      takeRecords(): IntersectionObserverEntry[] { return []; }
      readonly root = null;
      readonly rootMargin = '';
      readonly thresholds: number[] = [0.3];
    };
  });

  afterEach(() => {
    window.IntersectionObserver = originalIO;
  });

  function setup(platform: 'browser' | 'server'): ComponentFixture<HostComponent> {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PLATFORM_ID, useValue: platform },
      ],
      imports: [HostComponent],
    });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('returns false on the server (SSR)', () => {
    const fixture = setup('server');
    expect(fixture.componentInstance.visible()).toBe(false);
  });

  it('returns false on the browser before intersection', () => {
    const fixture = setup('browser');
    expect(fixture.componentInstance.visible()).toBe(false);
  });

  it('flips to true when the element intersects', () => {
    const fixture = setup('browser');
    const obs = observers[0];
    obs.cb(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      obs.instance,
    );
    expect(fixture.componentInstance.visible()).toBe(true);
  });

  it('stays true even if the element later leaves the viewport', () => {
    const fixture = setup('browser');
    const obs = observers[0];
    obs.cb(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      obs.instance,
    );
    expect(fixture.componentInstance.visible()).toBe(true);
    obs.cb(
      [{ isIntersecting: false } as IntersectionObserverEntry],
      obs.instance,
    );
    expect(fixture.componentInstance.visible()).toBe(true);
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/use-intersection.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the helper**

Create `frontend/src/app/shared/utils/use-intersection.ts`:

```typescript
import { DestroyRef, ElementRef, PLATFORM_ID, Signal, effect, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * Returns a `Signal<boolean>` that flips `true` once when the host element
 * enters the viewport at the given threshold, and stays true afterwards.
 *
 * SSR-safe: returns a static `false` signal on the server.
 *
 * Must be called inside an injection context (component constructor or
 * field initializer).
 */
export function useIntersection(
  elementRef: Signal<ElementRef<HTMLElement>>,
  threshold = 0.3,
): Signal<boolean> {
  const platformId = inject(PLATFORM_ID);
  const destroyRef = inject(DestroyRef);

  const visible = signal(false);

  if (!isPlatformBrowser(platformId)) {
    return visible.asReadonly();
  }

  let observer: IntersectionObserver | null = null;
  let observedElement: HTMLElement | null = null;

  effect(() => {
    if (visible()) return; // Already visible; no need to keep observing.

    const el = elementRef()?.nativeElement;
    if (!el || el === observedElement) return;

    if (observer && observedElement) {
      observer.unobserve(observedElement);
    }
    if (!observer) {
      observer = new IntersectionObserver(
        (entries) => {
          if (entries.some((e) => e.isIntersecting)) {
            visible.set(true);
            if (observer && observedElement) {
              observer.unobserve(observedElement);
            }
          }
        },
        { threshold },
      );
    }
    observedElement = el;
    observer.observe(el);
  });

  destroyRef.onDestroy(() => {
    observer?.disconnect();
    observer = null;
    observedElement = null;
  });

  return visible.asReadonly();
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/use-intersection.spec.ts' --watch=false
```
Expected: 4 specs pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/utils/use-intersection.ts frontend/src/app/shared/utils/use-intersection.spec.ts
git commit -m "feat(use-intersection): add signal-based one-shot IntersectionObserver helper"
```

---

## Task 3: useCountUp helper

**Files:**
- Create: `frontend/src/app/shared/utils/use-count-up.ts`
- Create: `frontend/src/app/shared/utils/use-count-up.spec.ts`

A signal that ramps from 0 to a target value when a start signal becomes true. RAF-driven, easeOutCubic.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/shared/utils/use-count-up.spec.ts`:

```typescript
import { Component, PLATFORM_ID, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { useCountUp } from './use-count-up';

@Component({
  standalone: true,
  template: ``,
})
class HostComponent {
  readonly start = signal(false);
  readonly value = useCountUp(100, 1000, this.start);
}

describe('useCountUp', () => {
  function setup(platform: 'browser' | 'server'): ComponentFixture<HostComponent> {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PLATFORM_ID, useValue: platform },
      ],
      imports: [HostComponent],
    });
    return TestBed.createComponent(HostComponent);
  }

  it('returns 0 on the server (SSR)', () => {
    const fixture = setup('server');
    expect(fixture.componentInstance.value()).toBe(0);
    fixture.componentInstance.start.set(true);
    fixture.detectChanges();
    expect(fixture.componentInstance.value()).toBe(0);
  });

  it('returns 0 in the browser before start is true', () => {
    const fixture = setup('browser');
    expect(fixture.componentInstance.value()).toBe(0);
  });

  it('ramps from 0 toward target after start flips true', (done) => {
    const fixture = setup('browser');
    fixture.componentInstance.start.set(true);
    fixture.detectChanges();
    // After two RAF ticks (~32ms), the value should be > 0.
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        expect(fixture.componentInstance.value()).toBeGreaterThan(0);
        done();
      });
    });
  });

  it('settles at target when duration elapses', (done) => {
    const fixture = setup('browser');
    fixture.componentInstance.start.set(true);
    fixture.detectChanges();
    // Wait beyond the 1000ms duration.
    setTimeout(() => {
      expect(fixture.componentInstance.value()).toBe(100);
      done();
    }, 1200);
  }, 2000);
});
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/use-count-up.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the helper**

Create `frontend/src/app/shared/utils/use-count-up.ts`:

```typescript
import { DestroyRef, PLATFORM_ID, Signal, effect, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * Returns a `Signal<number>` that ramps from 0 to `target` over `durationMs`
 * once `startSignal()` becomes true. Uses RAF + easeOutCubic.
 *
 * Re-triggering: only ramps once. After the first start, subsequent flips
 * of `startSignal` are ignored.
 *
 * SSR-safe: returns a static 0 signal on the server.
 *
 * Must be called inside an injection context.
 */
export function useCountUp(
  target: number,
  durationMs: number,
  startSignal: Signal<boolean>,
): Signal<number> {
  const platformId = inject(PLATFORM_ID);
  const destroyRef = inject(DestroyRef);

  const value = signal(0);

  if (!isPlatformBrowser(platformId)) {
    return value.asReadonly();
  }

  let started = false;
  let rafId = 0;

  effect(() => {
    if (started) return;
    if (!startSignal()) return;
    started = true;

    const startTime = performance.now();
    const tick = (now: number) => {
      const elapsed = now - startTime;
      const t = Math.min(1, elapsed / durationMs);
      // easeOutCubic
      const eased = 1 - Math.pow(1 - t, 3);
      value.set(target * eased);
      if (t < 1) {
        rafId = requestAnimationFrame(tick);
      } else {
        value.set(target);
      }
    };
    rafId = requestAnimationFrame(tick);
  });

  destroyRef.onDestroy(() => {
    if (rafId) cancelAnimationFrame(rafId);
  });

  return value.asReadonly();
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/use-count-up.spec.ts' --watch=false
```
Expected: 4 specs pass.

If the "ramps from 0 toward target" test fails because RAF callbacks don't fire in zoneless test mode, the helper's behavior is fine — the test environment is the issue. In that case, simplify:

```typescript
  it('ramps from 0 toward target after start flips true', (done) => {
    const fixture = setup('browser');
    fixture.componentInstance.start.set(true);
    fixture.detectChanges();
    setTimeout(() => {
      expect(fixture.componentInstance.value()).toBeGreaterThan(0);
      done();
    }, 100);
  });
```

If the "settles at target" test is too slow (CI timeout), reduce its `setTimeout` to 200ms and use a smaller `durationMs` (e.g. 50ms in the test only by changing the host component constructor to `useCountUp(100, 50, this.start)`).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/utils/use-count-up.ts frontend/src/app/shared/utils/use-count-up.spec.ts
git commit -m "feat(use-count-up): add RAF-driven count-up animation helper"
```

---

## Task 4: i18n keys

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add FR keys**

Open `frontend/public/i18n/fr.json`. Find the top-level object — add a new top-level block `"pricing"` (alongside `"home"`, `"salon"`, `"pro"`, etc.):

```json
"pricing": {
  "hero": {
    "title": "Votre salon, augmenté",
    "subtitle": "Prenez les rendez-vous, fidélisez vos clients, et faites grandir votre activité — en un seul endroit.",
    "ctaPrimary": "Lancer mon salon",
    "ctaSecondary": "Voir la démo"
  },
  "demo": {
    "title": "Vos chiffres prennent vie",
    "subtitle": "Un aperçu de votre futur tableau de bord."
  },
  "widgets": {
    "revenue": {
      "label": "Chiffre d'affaires",
      "trendVsLastMonth": "vs mois dernier"
    },
    "calendar": {
      "label": "Réservations cette semaine"
    },
    "reviews": {
      "label": "Note moyenne",
      "ratingsCount": "{{count}} avis",
      "quote1": "Service impeccable, je reviens !",
      "quote2": "Une vraie parenthèse de douceur.",
      "quote3": "Soin parfait, accueil chaleureux."
    }
  },
  "features": {
    "title": "Tout ce dont vous avez besoin",
    "vitrine": {
      "title": "Vitrine en ligne",
      "bullets": "Page personnalisée · Photos · Réservation directe"
    },
    "planning": {
      "title": "Planning intelligent",
      "bullets": "Disponibilités · Buffer · Rappels auto"
    },
    "clients": {
      "title": "Clients suivis",
      "bullets": "Historique · Notes · Photos avant/après"
    },
    "payments": {
      "title": "Paiements simples",
      "bullets": "Stripe intégré · Factures auto"
    }
  },
  "plan": {
    "title": "30 jours gratuits",
    "subtitle": "Sans engagement, sans carte bancaire",
    "cta": "Démarrer maintenant"
  },
  "finalCta": {
    "title": "Prêt à lancer votre salon ?",
    "button": "Créer mon compte pro"
  }
}
```

(Don't forget the comma between this new block and the next sibling.)

- [ ] **Step 2: Add EN keys**

Open `frontend/public/i18n/en.json`. Add the matching `"pricing"` block:

```json
"pricing": {
  "hero": {
    "title": "Your salon, amplified",
    "subtitle": "Book appointments, keep clients coming back, grow your business — all in one place.",
    "ctaPrimary": "Launch my salon",
    "ctaSecondary": "See the demo"
  },
  "demo": {
    "title": "Your numbers come to life",
    "subtitle": "A glimpse of your future dashboard."
  },
  "widgets": {
    "revenue": {
      "label": "Revenue",
      "trendVsLastMonth": "vs last month"
    },
    "calendar": {
      "label": "Bookings this week"
    },
    "reviews": {
      "label": "Average rating",
      "ratingsCount": "{{count}} reviews",
      "quote1": "Flawless service, I'll be back!",
      "quote2": "A true bubble of softness.",
      "quote3": "Perfect treatment, warm welcome."
    }
  },
  "features": {
    "title": "Everything you need",
    "vitrine": {
      "title": "Online storefront",
      "bullets": "Personal page · Photos · Direct booking"
    },
    "planning": {
      "title": "Smart scheduling",
      "bullets": "Availability · Buffer · Auto reminders"
    },
    "clients": {
      "title": "Client tracking",
      "bullets": "History · Notes · Before/after photos"
    },
    "payments": {
      "title": "Simple payments",
      "bullets": "Stripe built-in · Automatic invoices"
    }
  },
  "plan": {
    "title": "30 free days",
    "subtitle": "No commitment, no credit card",
    "cta": "Start now"
  },
  "finalCta": {
    "title": "Ready to launch your salon?",
    "button": "Create my pro account"
  }
}
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
git commit -m "feat(i18n): add pricing.* block (FR/EN)"
```

---

## Task 5: ParallaxHeroComponent

**Files:**
- Create: `frontend/src/app/shared/uis/parallax-hero/parallax-hero.component.ts`
- Create: `frontend/src/app/shared/uis/parallax-hero/parallax-hero.component.html`
- Create: `frontend/src/app/shared/uis/parallax-hero/parallax-hero.component.scss`
- Create: `frontend/src/app/shared/uis/parallax-hero/parallax-hero.component.spec.ts`

A reusable hero block with a background image that translates upward as the user scrolls. SSR-safe (image is static on server). `prefers-reduced-motion` disables the parallax. Overlay content via `<ng-content>`.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/shared/uis/parallax-hero/parallax-hero.component.spec.ts`:

```typescript
import { Component, PLATFORM_ID, provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ParallaxHeroComponent } from './parallax-hero.component';

@Component({
  standalone: true,
  imports: [ParallaxHeroComponent],
  template: `
    <app-parallax-hero imageUrl="/pricing/hero.jpg">
      <span class="overlay">overlay-content</span>
    </app-parallax-hero>
  `,
})
class HostComponent {}

describe('ParallaxHeroComponent', () => {
  function setup(platform: 'browser' | 'server'): ComponentFixture<HostComponent> {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PLATFORM_ID, useValue: platform },
      ],
      imports: [HostComponent],
    });
    const f = TestBed.createComponent(HostComponent);
    f.detectChanges();
    return f;
  }

  it('renders the background image', () => {
    const fixture = setup('browser');
    const bg = (fixture.nativeElement as HTMLElement).querySelector('.parallax-bg');
    expect(bg).not.toBeNull();
    expect((bg as HTMLElement).style.backgroundImage).toContain('/pricing/hero.jpg');
  });

  it('projects ng-content as the overlay', () => {
    const fixture = setup('browser');
    const overlay = (fixture.nativeElement as HTMLElement).querySelector('.overlay');
    expect(overlay?.textContent?.trim()).toBe('overlay-content');
  });

  it('renders correctly during SSR (no scroll listener errors)', () => {
    const fixture = setup('server');
    expect(() => fixture.detectChanges()).not.toThrow();
    const bg = (fixture.nativeElement as HTMLElement).querySelector('.parallax-bg');
    expect(bg).not.toBeNull();
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/parallax-hero.component.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Create the component TS**

Create `frontend/src/app/shared/uis/parallax-hero/parallax-hero.component.ts`:

```typescript
import {
  Component,
  DestroyRef,
  ElementRef,
  PLATFORM_ID,
  inject,
  input,
  signal,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * Hero section with a parallax-scrolling background image.
 *
 * The image translates upward at half the page-scroll speed via
 * `transform: translate3d(0, -scrollY*0.5, 0)`, throttled with
 * `requestAnimationFrame`. SSR-safe (no scroll math on the server).
 * `prefers-reduced-motion: reduce` disables the parallax (image stays
 * static).
 *
 * Overlay content (title, CTAs) is projected via <ng-content>.
 */
@Component({
  selector: 'app-parallax-hero',
  standalone: true,
  templateUrl: './parallax-hero.component.html',
  styleUrl: './parallax-hero.component.scss',
})
export class ParallaxHeroComponent {
  readonly imageUrl = input.required<string>();

  private readonly platformId = inject(PLATFORM_ID);
  private readonly hostRef = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly translateY = signal(0);

  constructor() {
    if (!isPlatformBrowser(this.platformId)) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    let rafScheduled = false;
    const onScroll = () => {
      if (rafScheduled) return;
      rafScheduled = true;
      requestAnimationFrame(() => {
        rafScheduled = false;
        const rect = this.hostRef.nativeElement.getBoundingClientRect();
        // Only animate while the hero is in/near viewport.
        const top = rect.top;
        if (top > window.innerHeight || top + rect.height < 0) return;
        // -0.5x scroll relative to the hero's top edge.
        this.translateY.set(-top * 0.5);
      });
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
    this.destroyRef.onDestroy(() =>
      window.removeEventListener('scroll', onScroll),
    );
  }
}
```

- [ ] **Step 4: Create the template**

Create `frontend/src/app/shared/uis/parallax-hero/parallax-hero.component.html`:

```html
<div class="parallax-wrapper">
  <div
    class="parallax-bg"
    [style.background-image]="'url(' + imageUrl() + ')'"
    [style.transform]="'translate3d(0, ' + translateY() + 'px, 0)'"
    aria-hidden="true"
  ></div>
  <div class="parallax-gradient"></div>
  <div class="parallax-content">
    <ng-content />
  </div>
</div>
```

- [ ] **Step 5: Create the styles**

Create `frontend/src/app/shared/uis/parallax-hero/parallax-hero.component.scss`:

```scss
:host {
  display: block;
  position: relative;
  width: 100%;
}

.parallax-wrapper {
  position: relative;
  width: 100%;
  height: 85vh;
  min-height: 560px;
  overflow: hidden;
}

.parallax-bg {
  position: absolute;
  top: -10%;
  left: 0;
  right: 0;
  bottom: -10%;
  background-size: cover;
  background-position: center;
  z-index: 0;
  will-change: transform;
}

.parallax-gradient {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.15) 0%, rgba(0, 0, 0, 0.45) 100%);
}

.parallax-content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  width: 100%;
  height: 100%;
  padding: 24px;
  text-align: center;
}
```

- [ ] **Step 6: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/parallax-hero.component.spec.ts' --watch=false
```
Expected: 3 specs pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/shared/uis/parallax-hero/
git commit -m "feat(parallax-hero): add SSR-safe parallax hero component"
```

---

## Task 6: MockBrowserComponent

**Files:**
- Create: `frontend/src/app/shared/uis/mock-browser/mock-browser.component.ts`
- Create: `frontend/src/app/shared/uis/mock-browser/mock-browser.component.html`
- Create: `frontend/src/app/shared/uis/mock-browser/mock-browser.component.scss`
- Create: `frontend/src/app/shared/uis/mock-browser/mock-browser.component.spec.ts`

A purely decorative wrapper that renders a stylized browser window (title bar with traffic lights + URL bar) around projected content.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/shared/uis/mock-browser/mock-browser.component.spec.ts`:

```typescript
import { Component, provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockBrowserComponent } from './mock-browser.component';

@Component({
  standalone: true,
  imports: [MockBrowserComponent],
  template: `
    <app-mock-browser url="prettyface.app/dashboard">
      <div class="inside">payload</div>
    </app-mock-browser>
  `,
})
class HostComponent {}

describe('MockBrowserComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [HostComponent],
    });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders the URL in the address bar', () => {
    const url = (fixture.nativeElement as HTMLElement).querySelector('.mb-url');
    expect(url?.textContent?.trim()).toBe('prettyface.app/dashboard');
  });

  it('projects ng-content as the body', () => {
    const inside = (fixture.nativeElement as HTMLElement).querySelector('.inside');
    expect(inside?.textContent?.trim()).toBe('payload');
  });

  it('renders three traffic-light dots', () => {
    const dots = (fixture.nativeElement as HTMLElement).querySelectorAll('.mb-dot');
    expect(dots.length).toBe(3);
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/mock-browser.component.spec.ts' --watch=false
```
Expected: FAIL.

- [ ] **Step 3: Create the component TS**

Create `frontend/src/app/shared/uis/mock-browser/mock-browser.component.ts`:

```typescript
import { Component, input } from '@angular/core';

/**
 * Decorative browser-window frame: rounded shell with three traffic-light
 * dots and a URL bar, projecting any content via <ng-content>. Purely
 * visual — no interactivity.
 */
@Component({
  selector: 'app-mock-browser',
  standalone: true,
  templateUrl: './mock-browser.component.html',
  styleUrl: './mock-browser.component.scss',
})
export class MockBrowserComponent {
  readonly url = input<string>('prettyface.app');
}
```

- [ ] **Step 4: Create the template**

Create `frontend/src/app/shared/uis/mock-browser/mock-browser.component.html`:

```html
<div class="mb-frame" aria-hidden="true">
  <div class="mb-titlebar">
    <span class="mb-dot mb-dot-red"></span>
    <span class="mb-dot mb-dot-yellow"></span>
    <span class="mb-dot mb-dot-green"></span>
    <span class="mb-url">{{ url() }}</span>
  </div>
  <div class="mb-body">
    <ng-content />
  </div>
</div>
```

- [ ] **Step 5: Create the styles**

Create `frontend/src/app/shared/uis/mock-browser/mock-browser.component.scss`:

```scss
:host {
  display: block;
  width: 100%;
}

.mb-frame {
  background: #fff;
  border-radius: 14px;
  box-shadow:
    0 24px 60px rgba(0, 0, 0, 0.12),
    0 6px 18px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.mb-titlebar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: linear-gradient(to bottom, #f6f4f2, #efedea);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.mb-dot {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  flex-shrink: 0;
}

.mb-dot-red { background: #ff5f56; }
.mb-dot-yellow { background: #ffbd2e; }
.mb-dot-green { background: #27c93f; }

.mb-url {
  margin-left: 12px;
  font-size: 12px;
  color: #888;
  letter-spacing: 0.02em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mb-body {
  padding: 24px;
  background: #fafafa;
}
```

- [ ] **Step 6: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/mock-browser.component.spec.ts' --watch=false
```
Expected: 3 specs pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/shared/uis/mock-browser/
git commit -m "feat(mock-browser): add decorative browser-window frame component"
```

---

## Task 7: RevenueWidgetComponent

**Files:**
- Create: `frontend/src/app/pages/pricing/widgets/revenue-widget.component.ts`
- Create: `frontend/src/app/pages/pricing/widgets/revenue-widget.component.html`
- Create: `frontend/src/app/pages/pricing/widgets/revenue-widget.component.scss`
- Create: `frontend/src/app/pages/pricing/widgets/revenue-widget.component.spec.ts`

KPI card mimicking the pro dashboard's revenue tile. Inputs: `started: Signal<boolean>` (the "play" trigger). Animates: count-up + sparkline trace + trend chip.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/pages/pricing/widgets/revenue-widget.component.spec.ts`:

```typescript
import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { RevenueWidgetComponent } from './revenue-widget.component';

@Component({
  standalone: true,
  imports: [RevenueWidgetComponent],
  template: `<app-revenue-widget [started]="started()" />`,
})
class HostComponent {
  readonly started = signal(false);
}

describe('RevenueWidgetComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [
        HostComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders 0 € when not started', () => {
    const value = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="revenue-value"]');
    expect(value?.textContent).toContain('0');
  });

  it('renders the SVG sparkline path', () => {
    const path = (fixture.nativeElement as HTMLElement).querySelector('svg path[data-testid="sparkline"]');
    expect(path).not.toBeNull();
  });

  it('renders the label and trend chip', () => {
    const label = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="revenue-label"]');
    expect(label).not.toBeNull();
    const trend = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="revenue-trend"]');
    expect(trend).not.toBeNull();
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/revenue-widget.component.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Create the component TS**

Create `frontend/src/app/pages/pricing/widgets/revenue-widget.component.ts`:

```typescript
import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { useCountUp } from '../../../shared/utils/use-count-up';

const TARGET_REVENUE = 12450;
const TARGET_TREND = 18;
// Sparkline: 8 monthly data points, last is the current month (target / 12).
// Inline polyline coordinates inside a 0..100 viewBox.
const SPARKLINE_PATH = 'M 0,70 L 14,60 L 28,55 L 42,52 L 56,42 L 70,38 L 84,28 L 100,18';

@Component({
  selector: 'app-revenue-widget',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './revenue-widget.component.html',
  styleUrl: './revenue-widget.component.scss',
})
export class RevenueWidgetComponent {
  readonly started = input.required<boolean>();

  // Bound to a startSignal-shaped accessor for useCountUp.
  private readonly startedSignal = computed(() => this.started());

  protected readonly revenue = useCountUp(TARGET_REVENUE, 1200, this.startedSignal);
  protected readonly trend = useCountUp(TARGET_TREND, 1200, this.startedSignal);

  protected readonly sparklinePath = SPARKLINE_PATH;
  protected readonly sparklinePathLength = 250; // approximate length, stable enough for stroke-dashoffset

  protected readonly displayedRevenue = computed(() => Math.round(this.revenue()));
  protected readonly displayedTrend = computed(() => Math.round(this.trend()));
}
```

- [ ] **Step 4: Create the template**

Create `frontend/src/app/pages/pricing/widgets/revenue-widget.component.html`:

```html
<div class="rev-widget">
  <span class="rev-label" data-testid="revenue-label">{{ 'pricing.widgets.revenue.label' | transloco }}</span>
  <div class="rev-row">
    <span class="rev-value" data-testid="revenue-value">{{ displayedRevenue() }} €</span>
    <span class="rev-trend" data-testid="revenue-trend">▲ +{{ displayedTrend() }}%</span>
  </div>
  <svg class="rev-spark" viewBox="0 0 100 80" preserveAspectRatio="none" aria-hidden="true">
    <path
      data-testid="sparkline"
      [attr.d]="sparklinePath"
      [attr.stroke-dasharray]="sparklinePathLength"
      [attr.stroke-dashoffset]="started() ? 0 : sparklinePathLength"
    />
  </svg>
  <span class="rev-vs">{{ 'pricing.widgets.revenue.trendVsLastMonth' | transloco }}</span>
</div>
```

- [ ] **Step 5: Create the styles**

Create `frontend/src/app/pages/pricing/widgets/revenue-widget.component.scss`:

```scss
:host {
  display: block;
}

.rev-widget {
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 14px;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.rev-label {
  font-size: 11px;
  font-weight: 500;
  color: #888;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.rev-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.rev-value {
  font-size: 28px;
  font-weight: 600;
  color: #222;
  font-variant-numeric: tabular-nums;
}

.rev-trend {
  font-size: 12px;
  font-weight: 600;
  color: #2e7d32;
  background: rgba(46, 125, 50, 0.08);
  padding: 3px 8px;
  border-radius: 10px;
}

.rev-spark {
  width: 100%;
  height: 60px;
  fill: none;
  stroke: #c06;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;

  @media (prefers-reduced-motion: no-preference) {
    path {
      transition: stroke-dashoffset 1200ms cubic-bezier(0.4, 0, 0.2, 1);
    }
  }
}

.rev-vs {
  font-size: 11px;
  color: #aaa;
}
```

- [ ] **Step 6: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/revenue-widget.component.spec.ts' --watch=false
```
Expected: 3 specs pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/pages/pricing/widgets/revenue-widget.component.ts frontend/src/app/pages/pricing/widgets/revenue-widget.component.html frontend/src/app/pages/pricing/widgets/revenue-widget.component.scss frontend/src/app/pages/pricing/widgets/revenue-widget.component.spec.ts
git commit -m "feat(pricing): add revenue widget with count-up and sparkline trace"
```

---

## Task 8: CalendarWidgetComponent

**Files:**
- Create: `frontend/src/app/pages/pricing/widgets/calendar-widget.component.ts`
- Create: `frontend/src/app/pages/pricing/widgets/calendar-widget.component.html`
- Create: `frontend/src/app/pages/pricing/widgets/calendar-widget.component.scss`
- Create: `frontend/src/app/pages/pricing/widgets/calendar-widget.component.spec.ts`

A 7×6 grid where ~25 of 42 cells fade from gray to rose in cascade once `started` flips true. Each cell has a stagger delay computed from its index.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/pages/pricing/widgets/calendar-widget.component.spec.ts`:

```typescript
import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { CalendarWidgetComponent } from './calendar-widget.component';

@Component({
  standalone: true,
  imports: [CalendarWidgetComponent],
  template: `<app-calendar-widget [started]="started()" />`,
})
class HostComponent {
  readonly started = signal(false);
}

describe('CalendarWidgetComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [
        HostComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders 42 cells (7×6 grid)', () => {
    const cells = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="cal-cell"]');
    expect(cells.length).toBe(42);
  });

  it('marks ~25 cells as filled in the data model (regardless of started)', () => {
    const filled = (fixture.nativeElement as HTMLElement).querySelectorAll('.cal-cell.is-filled');
    expect(filled.length).toBeGreaterThan(20);
    expect(filled.length).toBeLessThan(30);
  });

  it('toggles is-active on filled cells when started becomes true', () => {
    fixture.componentInstance.started.set(true);
    fixture.detectChanges();
    const active = (fixture.nativeElement as HTMLElement).querySelectorAll('.cal-cell.is-filled.is-active');
    expect(active.length).toBeGreaterThan(20);
  });

  it('renders the label', () => {
    const label = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="cal-label"]');
    expect(label).not.toBeNull();
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/calendar-widget.component.spec.ts' --watch=false
```
Expected: FAIL.

- [ ] **Step 3: Create the component TS**

Create `frontend/src/app/pages/pricing/widgets/calendar-widget.component.ts`:

```typescript
import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

interface Cell {
  readonly index: number;
  readonly filled: boolean;
  readonly delayMs: number;
}

// Deterministic pattern for filled cells: 25 of 42 (~60%).
// Hand-picked indices to look like a realistic week — denser midweek.
const FILLED_INDICES = new Set([
  1, 2, 3, 5, 6,
  8, 9, 11, 12, 13,
  15, 17, 19, 20,
  22, 23, 24, 25, 27,
  29, 30, 31, 33,
  36, 37, 39,
]);

const CELL_COUNT = 42;
const STAGGER_MS = 30;

@Component({
  selector: 'app-calendar-widget',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './calendar-widget.component.html',
  styleUrl: './calendar-widget.component.scss',
})
export class CalendarWidgetComponent {
  readonly started = input.required<boolean>();

  protected readonly cells: readonly Cell[] = Array.from({ length: CELL_COUNT }).map(
    (_, index) => {
      const filled = FILLED_INDICES.has(index);
      // Stagger only filled cells to keep the cascade tight.
      const filledIndex = filled ? Array.from(FILLED_INDICES).indexOf(index) : 0;
      return { index, filled, delayMs: filledIndex * STAGGER_MS };
    },
  );
}
```

- [ ] **Step 4: Create the template**

Create `frontend/src/app/pages/pricing/widgets/calendar-widget.component.html`:

```html
<div class="cal-widget">
  <span class="cal-label" data-testid="cal-label">{{ 'pricing.widgets.calendar.label' | transloco }}</span>
  <div class="cal-grid" aria-hidden="true">
    @for (cell of cells; track cell.index) {
      <div
        class="cal-cell"
        data-testid="cal-cell"
        [class.is-filled]="cell.filled"
        [class.is-active]="cell.filled && started()"
        [style.--delay]="cell.delayMs + 'ms'"
      ></div>
    }
  </div>
</div>
```

- [ ] **Step 5: Create the styles**

Create `frontend/src/app/pages/pricing/widgets/calendar-widget.component.scss`:

```scss
:host {
  display: block;
}

.cal-widget {
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 14px;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.cal-label {
  font-size: 11px;
  font-weight: 500;
  color: #888;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  grid-template-rows: repeat(6, 1fr);
  gap: 4px;
  aspect-ratio: 7 / 6;
}

.cal-cell {
  background: #f0eeec;
  border-radius: 4px;

  @media (prefers-reduced-motion: no-preference) {
    transition: background 350ms ease;
    transition-delay: var(--delay, 0ms);
  }
}

.cal-cell.is-filled.is-active {
  background: linear-gradient(135deg, #d486a4, #c06);
}

@media (prefers-reduced-motion: reduce) {
  .cal-cell.is-filled {
    background: linear-gradient(135deg, #d486a4, #c06);
  }
}
```

- [ ] **Step 6: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/calendar-widget.component.spec.ts' --watch=false
```
Expected: 4 specs pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/pages/pricing/widgets/calendar-widget.component.ts frontend/src/app/pages/pricing/widgets/calendar-widget.component.html frontend/src/app/pages/pricing/widgets/calendar-widget.component.scss frontend/src/app/pages/pricing/widgets/calendar-widget.component.spec.ts
git commit -m "feat(pricing): add calendar widget with cascading fill animation"
```

---

## Task 9: ReviewsWidgetComponent

**Files:**
- Create: `frontend/src/app/pages/pricing/widgets/reviews-widget.component.ts`
- Create: `frontend/src/app/pages/pricing/widgets/reviews-widget.component.html`
- Create: `frontend/src/app/pages/pricing/widgets/reviews-widget.component.scss`
- Create: `frontend/src/app/pages/pricing/widgets/reviews-widget.component.spec.ts`

5 stars cascade scale-in + count-up `4.9` + 3 quotes fade-in stagger.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/pages/pricing/widgets/reviews-widget.component.spec.ts`:

```typescript
import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { ReviewsWidgetComponent } from './reviews-widget.component';

@Component({
  standalone: true,
  imports: [ReviewsWidgetComponent],
  template: `<app-reviews-widget [started]="started()" />`,
})
class HostComponent {
  readonly started = signal(false);
}

describe('ReviewsWidgetComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [
        HostComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders 5 stars', () => {
    const stars = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="rev-star"]');
    expect(stars.length).toBe(5);
  });

  it('renders 3 quote elements', () => {
    const quotes = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="rev-quote"]');
    expect(quotes.length).toBe(3);
  });

  it('renders the rating value', () => {
    const value = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="rev-value"]');
    expect(value).not.toBeNull();
  });

  it('toggles is-active on stars when started becomes true', () => {
    fixture.componentInstance.started.set(true);
    fixture.detectChanges();
    const active = (fixture.nativeElement as HTMLElement).querySelectorAll('.rev-star.is-active');
    expect(active.length).toBe(5);
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/reviews-widget.component.spec.ts' --watch=false
```
Expected: FAIL.

- [ ] **Step 3: Create the component TS**

Create `frontend/src/app/pages/pricing/widgets/reviews-widget.component.ts`:

```typescript
import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { useCountUp } from '../../../shared/utils/use-count-up';

const TARGET_RATING = 4.9;
const TARGET_COUNT = 184;
const STAR_COUNT = 5;
const STAR_STAGGER_MS = 100;
const QUOTE_KEYS = ['quote1', 'quote2', 'quote3'] as const;
const QUOTE_STAGGER_MS = 180;

@Component({
  selector: 'app-reviews-widget',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './reviews-widget.component.html',
  styleUrl: './reviews-widget.component.scss',
})
export class ReviewsWidgetComponent {
  readonly started = input.required<boolean>();

  private readonly startedSignal = computed(() => this.started());
  protected readonly ratingValue = useCountUp(TARGET_RATING, 1200, this.startedSignal);
  protected readonly displayedRating = computed(() => this.ratingValue().toFixed(1));

  protected readonly count = TARGET_COUNT;
  protected readonly stars = Array.from({ length: STAR_COUNT }).map((_, i) => ({
    index: i,
    delayMs: i * STAR_STAGGER_MS,
  }));

  protected readonly quotes = QUOTE_KEYS.map((key, i) => ({
    key,
    delayMs: i * QUOTE_STAGGER_MS,
  }));
}
```

- [ ] **Step 4: Create the template**

Create `frontend/src/app/pages/pricing/widgets/reviews-widget.component.html`:

```html
<div class="rev-widget">
  <span class="rev-label">{{ 'pricing.widgets.reviews.label' | transloco }}</span>
  <div class="rev-stars" aria-hidden="true">
    @for (star of stars; track star.index) {
      <span
        class="rev-star"
        data-testid="rev-star"
        [class.is-active]="started()"
        [style.--delay]="star.delayMs + 'ms'"
      >★</span>
    }
    <span class="rev-value" data-testid="rev-value">{{ displayedRating() }}</span>
    <span class="rev-count">{{
      'pricing.widgets.reviews.ratingsCount' | transloco: { count }
    }}</span>
  </div>
  <ul class="rev-quotes">
    @for (quote of quotes; track quote.key) {
      <li
        class="rev-quote"
        data-testid="rev-quote"
        [class.is-active]="started()"
        [style.--delay]="quote.delayMs + 'ms'"
      >
        “{{ 'pricing.widgets.reviews.' + quote.key | transloco }}”
      </li>
    }
  </ul>
</div>
```

- [ ] **Step 5: Create the styles**

Create `frontend/src/app/pages/pricing/widgets/reviews-widget.component.scss`:

```scss
:host {
  display: block;
}

.rev-widget {
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 14px;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.rev-label {
  font-size: 11px;
  font-weight: 500;
  color: #888;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.rev-stars {
  display: flex;
  align-items: center;
  gap: 4px;
}

.rev-star {
  display: inline-block;
  font-size: 22px;
  color: #f5b536;
  transform: scale(0);

  @media (prefers-reduced-motion: no-preference) {
    transition: transform 200ms cubic-bezier(0.4, 0, 0.2, 1);
    transition-delay: var(--delay, 0ms);
  }

  &.is-active {
    transform: scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .rev-star {
    transform: scale(1);
  }
}

.rev-value {
  font-size: 22px;
  font-weight: 600;
  color: #222;
  margin-left: 8px;
  font-variant-numeric: tabular-nums;
}

.rev-count {
  font-size: 12px;
  color: #888;
}

.rev-quotes {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rev-quote {
  font-size: 13px;
  color: #555;
  font-style: italic;
  opacity: 0;
  transform: translateY(6px);

  @media (prefers-reduced-motion: no-preference) {
    transition:
      opacity 350ms ease,
      transform 350ms ease;
    transition-delay: var(--delay, 0ms);
  }

  &.is-active {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .rev-quote {
    opacity: 1;
    transform: none;
  }
}
```

- [ ] **Step 6: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/reviews-widget.component.spec.ts' --watch=false
```
Expected: 4 specs pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/pages/pricing/widgets/reviews-widget.component.ts frontend/src/app/pages/pricing/widgets/reviews-widget.component.html frontend/src/app/pages/pricing/widgets/reviews-widget.component.scss frontend/src/app/pages/pricing/widgets/reviews-widget.component.spec.ts
git commit -m "feat(pricing): add reviews widget with cascading stars and fading quotes"
```

---

## Task 10: PricingComponent (page)

**Files:**
- Create: `frontend/src/app/pages/pricing/pricing.component.ts`
- Create: `frontend/src/app/pages/pricing/pricing.component.html`
- Create: `frontend/src/app/pages/pricing/pricing.component.scss`
- Create: `frontend/src/app/pages/pricing/pricing.component.spec.ts`

The page assembles the parallax hero, the demo section (mock browser frame + 3 widgets), the features grid, the placeholder plan card, and the final CTA. The demo's `started` signal is driven by `useIntersection` on a sentinel element. The 3 widgets receive cascading `started` flags (W1 immediate, W2 +400ms, W3 +800ms).

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/pages/pricing/pricing.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { PricingComponent } from './pricing.component';

describe('PricingComponent', () => {
  let fixture: ComponentFixture<PricingComponent>;

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
        PricingComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(PricingComponent);
    fixture.detectChanges();
  });

  it('renders the parallax hero', () => {
    const hero = (fixture.nativeElement as HTMLElement).querySelector('app-parallax-hero');
    expect(hero).not.toBeNull();
  });

  it('renders the mock browser frame containing the three widgets', () => {
    const frame = (fixture.nativeElement as HTMLElement).querySelector('app-mock-browser');
    expect(frame).not.toBeNull();
    expect(frame?.querySelector('app-revenue-widget')).not.toBeNull();
    expect(frame?.querySelector('app-calendar-widget')).not.toBeNull();
    expect(frame?.querySelector('app-reviews-widget')).not.toBeNull();
  });

  it('renders the four feature cards', () => {
    const cards = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="feature-card"]');
    expect(cards.length).toBe(4);
  });

  it('renders the plan card', () => {
    const plan = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="plan-card"]');
    expect(plan).not.toBeNull();
  });

  it('renders the final CTA with the right router link', () => {
    const cta = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="final-cta"]');
    expect(cta).not.toBeNull();
    expect(cta?.getAttribute('href')).toContain('/register/pro');
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/pricing.component.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Create the component TS**

Create `frontend/src/app/pages/pricing/pricing.component.ts`:

```typescript
import {
  Component,
  ElementRef,
  PLATFORM_ID,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { ParallaxHeroComponent } from '../../shared/uis/parallax-hero/parallax-hero.component';
import { MockBrowserComponent } from '../../shared/uis/mock-browser/mock-browser.component';
import { useIntersection } from '../../shared/utils/use-intersection';
import { RevenueWidgetComponent } from './widgets/revenue-widget.component';
import { CalendarWidgetComponent } from './widgets/calendar-widget.component';
import { ReviewsWidgetComponent } from './widgets/reviews-widget.component';

interface Feature {
  readonly key: 'vitrine' | 'planning' | 'clients' | 'payments';
  readonly icon: string;
}

@Component({
  selector: 'app-pricing',
  standalone: true,
  imports: [
    RouterLink,
    MatIconModule,
    TranslocoPipe,
    ParallaxHeroComponent,
    MockBrowserComponent,
    RevenueWidgetComponent,
    CalendarWidgetComponent,
    ReviewsWidgetComponent,
  ],
  templateUrl: './pricing.component.html',
  styleUrl: './pricing.component.scss',
})
export class PricingComponent {
  protected readonly demoSentinel = viewChild.required<ElementRef<HTMLElement>>('demoSentinel');
  protected readonly demoVisible = useIntersection(this.demoSentinel, 0.3);

  // Cascade: widget 1 immediate, widget 2 +400ms, widget 3 +800ms.
  private readonly platformId = inject(PLATFORM_ID);
  private readonly w1 = signal(false);
  private readonly w2 = signal(false);
  private readonly w3 = signal(false);

  protected readonly w1Started = computed(() => this.w1());
  protected readonly w2Started = computed(() => this.w2());
  protected readonly w3Started = computed(() => this.w3());

  protected readonly features: readonly Feature[] = [
    { key: 'vitrine', icon: 'storefront' },
    { key: 'planning', icon: 'event_available' },
    { key: 'clients', icon: 'groups' },
    { key: 'payments', icon: 'payments' },
  ];

  constructor() {
    effect(() => {
      if (!this.demoVisible()) return;
      if (this.w1()) return; // Already triggered.
      this.w1.set(true);
      if (!isPlatformBrowser(this.platformId)) {
        // SSR/static: just flip them all so the final state renders.
        this.w2.set(true);
        this.w3.set(true);
        return;
      }
      setTimeout(() => this.w2.set(true), 400);
      setTimeout(() => this.w3.set(true), 800);
    });
  }

  scrollToDemo(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const el = this.demoSentinel().nativeElement;
    el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}
```

- [ ] **Step 4: Create the template**

Create `frontend/src/app/pages/pricing/pricing.component.html`:

```html
<!-- Hero -->
<app-parallax-hero imageUrl="/pricing/parallax-hero.jpg">
  <h1 class="pricing-hero-title">{{ 'pricing.hero.title' | transloco }}</h1>
  <p class="pricing-hero-subtitle">{{ 'pricing.hero.subtitle' | transloco }}</p>
  <div class="pricing-hero-ctas">
    <a routerLink="/register/pro" class="pricing-cta pricing-cta-primary">
      {{ 'pricing.hero.ctaPrimary' | transloco }}
    </a>
    <button type="button" class="pricing-cta pricing-cta-secondary" (click)="scrollToDemo()">
      {{ 'pricing.hero.ctaSecondary' | transloco }} ↓
    </button>
  </div>
</app-parallax-hero>

<!-- Demo -->
<section class="pricing-demo">
  <div class="pricing-demo-inner">
    <h2 class="pricing-section-title">{{ 'pricing.demo.title' | transloco }}</h2>
    <p class="pricing-section-subtitle">{{ 'pricing.demo.subtitle' | transloco }}</p>

    <div #demoSentinel class="pricing-demo-frame">
      <app-mock-browser url="prettyface.app/dashboard">
        <div class="pricing-demo-grid">
          <app-revenue-widget [started]="w1Started()" />
          <app-calendar-widget [started]="w2Started()" />
          <app-reviews-widget [started]="w3Started()" />
        </div>
      </app-mock-browser>
    </div>
  </div>
</section>

<!-- Features -->
<section class="pricing-features">
  <div class="pricing-features-inner">
    <h2 class="pricing-section-title">{{ 'pricing.features.title' | transloco }}</h2>
    <div class="pricing-features-grid">
      @for (feature of features; track feature.key) {
        <article class="pricing-feature-card" data-testid="feature-card">
          <mat-icon class="pricing-feature-icon" aria-hidden="true">{{ feature.icon }}</mat-icon>
          <h3 class="pricing-feature-title">
            {{ 'pricing.features.' + feature.key + '.title' | transloco }}
          </h3>
          <p class="pricing-feature-bullets">
            {{ 'pricing.features.' + feature.key + '.bullets' | transloco }}
          </p>
        </article>
      }
    </div>
  </div>
</section>

<!-- Plan -->
<section class="pricing-plan">
  <article class="pricing-plan-card" data-testid="plan-card">
    <h3 class="pricing-plan-title">{{ 'pricing.plan.title' | transloco }}</h3>
    <p class="pricing-plan-subtitle">{{ 'pricing.plan.subtitle' | transloco }}</p>
    <a routerLink="/register/pro" class="pricing-cta pricing-cta-primary">
      {{ 'pricing.plan.cta' | transloco }} →
    </a>
  </article>
</section>

<!-- Final CTA -->
<section class="pricing-final-cta">
  <div class="pricing-final-cta-inner">
    <h2 class="pricing-final-cta-title">{{ 'pricing.finalCta.title' | transloco }}</h2>
    <a
      routerLink="/register/pro"
      class="pricing-cta pricing-cta-primary"
      data-testid="final-cta"
    >
      {{ 'pricing.finalCta.button' | transloco }} →
    </a>
  </div>
</section>
```

- [ ] **Step 5: Create the styles**

Create `frontend/src/app/pages/pricing/pricing.component.scss`:

```scss
:host {
  display: block;
}

// ===== Hero =====
.pricing-hero-title {
  font-size: 2.5rem;
  font-weight: 300;
  color: white;
  margin: 0;
  letter-spacing: 0.02em;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.2);

  @media (min-width: 768px) {
    font-size: 4rem;
  }
}

.pricing-hero-subtitle {
  font-size: 1rem;
  color: rgba(255, 255, 255, 0.92);
  font-weight: 300;
  max-width: 580px;
  margin: 8px 0 12px;
  line-height: 1.5;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.2);

  @media (min-width: 768px) {
    font-size: 1.125rem;
  }
}

.pricing-hero-ctas {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  justify-content: center;
}

.pricing-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 12px 28px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid transparent;
  text-decoration: none;
  transition:
    background 150ms ease,
    transform 150ms ease;

  &:hover {
    transform: translateY(-1px);
  }
}

.pricing-cta-primary {
  background: #c06;
  color: white;
  border-color: #c06;

  &:hover {
    background: #a05;
  }
}

.pricing-cta-secondary {
  background: rgba(255, 255, 255, 0.92);
  color: #6b1d3f;
  border-color: rgba(255, 255, 255, 0.4);

  &:hover {
    background: white;
  }
}

// ===== Sections =====
.pricing-section-title {
  font-size: 1.75rem;
  font-weight: 400;
  color: #222;
  margin: 0 0 6px;
  text-align: center;

  @media (min-width: 768px) {
    font-size: 2.25rem;
  }
}

.pricing-section-subtitle {
  font-size: 0.95rem;
  color: #777;
  margin: 0 0 32px;
  text-align: center;
}

// ===== Demo =====
.pricing-demo {
  padding: 80px 24px;
  background: linear-gradient(180deg, #faf7f5 0%, #fff 100%);
}

.pricing-demo-inner {
  max-width: 1100px;
  margin: 0 auto;
}

.pricing-demo-frame {
  margin: 0 auto;
  max-width: 980px;
}

.pricing-demo-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18px;

  @media (min-width: 720px) {
    grid-template-columns: 1fr 1fr;

    app-revenue-widget {
      grid-column: 1 / 2;
    }
    app-calendar-widget {
      grid-column: 2 / 3;
      grid-row: 1 / 3;
    }
    app-reviews-widget {
      grid-column: 1 / 2;
    }
  }
}

// ===== Features =====
.pricing-features {
  padding: 80px 24px;
  background: white;
}

.pricing-features-inner {
  max-width: 1100px;
  margin: 0 auto;
}

.pricing-features-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;

  @media (min-width: 720px) {
    grid-template-columns: 1fr 1fr;
    gap: 20px;
  }
}

.pricing-feature-card {
  background: white;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 16px;
  padding: 32px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition:
    border-color 150ms ease,
    box-shadow 150ms ease,
    transform 150ms ease;

  &:hover {
    border-color: rgba(192, 0, 102, 0.3);
    box-shadow: 0 6px 18px rgba(192, 0, 102, 0.08);
    transform: translateY(-2px);
  }
}

.pricing-feature-icon {
  font-size: 28px;
  width: 28px;
  height: 28px;
  color: #c06;
  margin-bottom: 6px;
}

.pricing-feature-title {
  font-size: 1.125rem;
  font-weight: 500;
  color: #222;
  margin: 0;
}

.pricing-feature-bullets {
  font-size: 13px;
  color: #666;
  margin: 0;
  line-height: 1.5;
}

// ===== Plan =====
.pricing-plan {
  padding: 60px 24px;
  background: linear-gradient(135deg, #fdf3f7 0%, #f7ece4 100%);
}

.pricing-plan-card {
  max-width: 480px;
  margin: 0 auto;
  background: white;
  border: 1px solid rgba(192, 0, 102, 0.18);
  border-radius: 20px;
  padding: 40px 32px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  box-shadow: 0 12px 32px rgba(192, 0, 102, 0.1);
}

.pricing-plan-title {
  font-size: 1.5rem;
  font-weight: 500;
  color: #6b1d3f;
  margin: 0;
}

.pricing-plan-subtitle {
  font-size: 13px;
  color: #888;
  margin: 0;
}

// ===== Final CTA =====
.pricing-final-cta {
  padding: 80px 24px;
  background: white;
  text-align: center;
}

.pricing-final-cta-inner {
  max-width: 720px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
}

.pricing-final-cta-title {
  font-size: 1.75rem;
  font-weight: 400;
  color: #222;
  margin: 0;

  @media (min-width: 768px) {
    font-size: 2.25rem;
  }
}
```

- [ ] **Step 6: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/pricing.component.spec.ts' --watch=false
```
Expected: 5 specs pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/pages/pricing/
git commit -m "feat(pricing): add PricingComponent assembling hero, demo, features, plan, CTA"
```

---

## Task 11: Wire `/pricing` route to PricingComponent

**Files:**
- Modify: `frontend/src/app/app.routes.ts`

The current `/pricing` route lazy-loads `RegisterProComponent` (was a temporary redirect). Replace it with `PricingComponent`.

- [ ] **Step 1: Update the route**

Open `frontend/src/app/app.routes.ts`. Find the existing block (around line 22-25):

```typescript
  {
    path: 'pricing',
    loadComponent: () => import('./pages/auth/register-pro/register-pro.component').then(m => m.RegisterProComponent),
  },
```

Replace with:

```typescript
  {
    path: 'pricing',
    loadComponent: () => import('./pages/pricing/pricing.component').then(m => m.PricingComponent),
  },
```

The `/register/pro` route below stays unchanged — that's the actual registration flow.

- [ ] **Step 2: Verify TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 3: Verify the home's Pro CTA still navigates correctly**

The home's Pro CTA button uses `this.router.navigate(['/pricing'])`. After this change, that lands on the new PricingComponent.

```bash
cd frontend && npm test -- --include='**/home.spec.ts' --watch=false
```
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/app.routes.ts
git commit -m "feat(routes): wire /pricing to PricingComponent (was redirecting to register-pro)"
```

---

## Task 12: Final integration check

**Files:** none (verification + smoke test).

- [ ] **Step 1: Run focused J4 test suite**

```bash
cd frontend && npm test -- --include='**/use-intersection.spec.ts' --include='**/use-count-up.spec.ts' --include='**/parallax-hero.component.spec.ts' --include='**/mock-browser.component.spec.ts' --include='**/revenue-widget.component.spec.ts' --include='**/calendar-widget.component.spec.ts' --include='**/reviews-widget.component.spec.ts' --include='**/pricing.component.spec.ts' --watch=false
```
Expected: PASS across all J4 specs.

- [ ] **Step 2: Run home spec to confirm no regression on the Pro CTA**

```bash
cd frontend && npm test -- --include='**/home.spec.ts' --watch=false
```
Expected: PASS.

- [ ] **Step 3: TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 4: Smoke check (visual)**

Start backend (`cd backend && ./mvnw spring-boot:run`) and frontend (`cd frontend && npm start`).

Open http://localhost:4200/pricing in a browser:

1. Hero: parallax background image, title + subtitle visible. Scroll a bit and confirm the bg moves at half speed.
2. Click "Voir la démo" → smooth-scrolls to the demo section.
3. As the demo enters the viewport, the three widgets animate in cascade (~0ms / 400ms / 800ms):
   - Revenue: number counts up to 12 450 €, sparkline traces.
   - Calendar: cells fill from gray to rose in cascade.
   - Reviews: 5 stars scale-in, rating counts to 4.9, quotes fade-in.
4. Scroll past, scroll back: animations DON'T retrigger (one-shot).
5. Features section: 4 cards in 2×2 grid (PC) or 1 col (mobile). Hover → border rose + lift.
6. Plan card: rose-tinted, "Démarrer maintenant" button → navigates to `/register/pro`.
7. Final CTA: large title + button → navigates to `/register/pro`.

Resize the browser to < 720px:
1. Demo grid stacks to 1 column.
2. Features grid stacks to 1 column.
3. Hero CTAs wrap if needed.

Test `prefers-reduced-motion`:
1. macOS: System Settings → Accessibility → Display → Reduce motion ON.
2. Reload `/pricing`. Confirm the parallax doesn't move on scroll, the widgets render their final state immediately, and the demo cascade isn't visible.

- [ ] **Step 5: Lighthouse check (optional)**

Chrome DevTools → Lighthouse → Mobile + Performance + Accessibility. Run on http://localhost:4200/pricing.

Targets:
- Performance ≥ 75 (placeholder hero image affects LCP).
- Accessibility ≥ 95.

- [ ] **Step 6: Final commit (only if Step 1-3 surfaced fix-ups)**

If everything passed, skip. Otherwise:

```bash
git add -A
git commit -m "fix(pricing-page): address integration issues"
```

---

## Self-Review Notes

**Spec coverage check:**

| Spec requirement | Implemented in |
|------------------|----------------|
| Hero parallax ~85vh + image | Tasks 1, 5 |
| Parallax via `transform: translateY` throttled by RAF | Task 5 |
| SSR-safe (no scroll math on server) | Task 5 |
| `prefers-reduced-motion` disables parallax | Task 5 |
| Title + subtitle + 2 CTAs ("Lancer mon salon" / "Voir la démo ↓") | Tasks 4, 10 |
| Smooth-scroll to demo on "Voir la démo" | Task 10 (`scrollToDemo`) |
| `<app-mock-browser>` reusable frame | Task 6 |
| Widget 1 — count-up CA + sparkline | Task 7 |
| Widget 2 — calendar cells cascade fill | Task 8 |
| Widget 3 — stars cascade + count-up + quotes | Task 9 |
| Single IntersectionObserver triggers cascade | Tasks 2, 10 |
| W1 immediate, W2 +400ms, W3 +800ms | Task 10 |
| One-shot (no re-trigger on scroll back) | Task 2 (`useIntersection` flips and stays) |
| Mock data hard-coded in components | Tasks 7, 8, 9 |
| Features grid 2×2 PC / 1 col mobile | Task 10 |
| Features: 4 cards (vitrine/planning/clients/payments) | Tasks 4, 10 |
| Hover: border rose + lift | Task 10 SCSS |
| Plan placeholder card "30 days free" | Tasks 4, 10 |
| Final CTA bandeau pointing to `/register/pro` | Task 10 |
| `<app-parallax-hero>` reusable | Task 5 |
| `useIntersection` + `useCountUp` helpers | Tasks 2, 3 |
| No external lib (CSS+RAF+signals only) | All tasks |
| SSR-safe (final state in SSR) | Tasks 2, 3, 5, 7, 8, 9, 10 |
| `prefers-reduced-motion` everywhere | Tasks 5, 7, 8, 9 SCSS |
| `aria-hidden` on decorative animation elements | Tasks 5, 7, 8, 9 |
| i18n FR + EN | Task 4 |
| Route `/pricing` wires to PricingComponent | Task 11 |

**Out of scope (acceptable):**
- Real Stripe plans card — placeholder until `project_pending_payments.md` ships. A 2nd iteration of the page will swap in actual plans.
- Production parallax image — placeholder Pexels image; a brand asset can replace it later without code change.
- 60fps measurement script — verified manually via DevTools during smoke check.

**Placeholders scan:** none — all steps contain concrete code or commands.

**Type consistency:**
- `useIntersection(elementRef: Signal<ElementRef>, threshold)` — used in Task 10 with `viewChild.required<ElementRef<HTMLElement>>('demoSentinel')`.
- `useCountUp(target, durationMs, startSignal)` — used in Tasks 7, 9 with explicit `Signal<boolean>`.
- `Feature` interface in Task 10 keys (`'vitrine'`, etc.) match the i18n keys in Task 4.
- `started` input is `Signal<boolean>` for all 3 widgets (Tasks 7-9).

---

## Notes for the executing engineer

- **`useIntersection` and `useCountUp` are NEW utilities** but kept tiny (~50 lines each). They live under `shared/utils/` next to other future helpers.
- **Animations are CSS-driven where possible**: the calendar cells, stars, and quotes animate via `transition` + `transition-delay: var(--delay)`. Only the count-up uses RAF.
- **The pricing page does not back-end-call anything**. Mock data is hard-coded in the widgets. The page is purely marketing.
- **The home's Pro CTA links to `/pricing`** (already in place from Jalon 3). Task 11 just swaps what `/pricing` renders.
- **`/register/pro` is unchanged** — the registration flow keeps its own route. The pricing page CTAs all link there.
- **Asset URLs in Task 1**: if the Pexels link 404s, pick another at https://www.pexels.com/search/skincare/ — don't overthink, any valid JPG ~ 1920×1080 < 250 KB works.
