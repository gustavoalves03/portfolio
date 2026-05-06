# Home Redesign (Jalon 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the public home page with an immersive video hero (PC) / poster image (mobile), a multi-card flip carousel where the centered card flips to reveal a Leaflet mini-map, and a discreet "Pro" CTA bullet pointing to `/pricing`.

**Architecture:** The existing `Home` component sheds the inline mini-map + mini-cards in favor of two new shared components — `HeroVideoComponent` (video PC / image mobile, SSR-safe) and `SalonCarouselComponent` (custom Angular coverflow with CSS-pure flip). The geocoding cache currently inside `home.ts` is extracted into a root `GeocodingService` so the carousel can lazy-init Leaflet only when a card is flipped. A `Pro CTA` block replaces the existing tiny anchor with a fuller-width strip that routes to `/pricing`.

**Tech Stack:** Angular 20 (standalone, signals, zoneless, SSR-ready), Leaflet (already used by the home), Tailwind + SCSS, Transloco i18n. Tests: Karma/Jasmine.

**Spec reference:** `docs/superpowers/specs/2026-05-06-vitrine-preview-onboarding-pc-design.md` — Jalon 3 section.

**Branch:** `feat/home-redesign` (create from `main` after Jalon 2 has been merged).

---

## File Structure

**New files (10):**

| Path | Responsibility |
|------|----------------|
| `frontend/src/app/core/services/geocoding.service.ts` | Root-scoped Nominatim wrapper with in-memory cache. Replaces inline cache in `home.ts`. |
| `frontend/src/app/core/services/geocoding.service.spec.ts` | Unit tests. |
| `frontend/src/app/shared/uis/hero-video/hero-video.component.ts` | Hero block: `<video>` on desktop+hover, `<img>` poster otherwise. SSR-safe. |
| `frontend/src/app/shared/uis/hero-video/hero-video.component.html` | Template. |
| `frontend/src/app/shared/uis/hero-video/hero-video.component.scss` | Styles (full-bleed, overlay, content slot). |
| `frontend/src/app/shared/uis/hero-video/hero-video.component.spec.ts` | Component tests (renders `<img>` in SSR, `<video>` on desktop). |
| `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.ts` | Coverflow carousel + flip logic. |
| `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.html` | Template (front face = photo, back face = lazy Leaflet map). |
| `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.scss` | Coverflow + 3D flip styles. |
| `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.spec.ts` | Tests. |

**Asset files (2):**

| Path | Responsibility |
|------|----------------|
| `frontend/public/hero/hero-poster.jpg` | Poster (mobile + before-load). Placeholder Pexels/Coverr image. |
| `frontend/public/hero/hero-loop.mp4` | MP4 video (desktop). Placeholder Pexels/Coverr 8-15s loop. |

(Asset acquisition is documented in Task 1; the engineer commits placeholder URLs/files.)

**Modified files (5):**

| Path | Change |
|------|--------|
| `frontend/src/app/pages/home/home.html` | Drop mini-map + mini-cards block, add `<app-hero-video>` + `<app-salon-carousel>` + new `pro-cta` block. |
| `frontend/src/app/pages/home/home.scss` | Drop mini-map + mini-cards rules, add hero/carousel/pro-cta wrappers. |
| `frontend/src/app/pages/home/home.ts` | Drop inline geocode + map plotting; provide `salons` signal to `<app-salon-carousel>`. |
| `frontend/public/i18n/fr.json` | Add `home.salons.{flipToMap,flipToPhoto,viewItinerary}` + `home.proCta.*`. |
| `frontend/public/i18n/en.json` | Same. |

---

## Conventions

- Standalone components. `@if`/`@for`. Signals + `inject()`. Zoneless tests.
- `provideRouter([])`, `provideHttpClient()`, `provideHttpClientTesting()`, `provideZonelessChangeDetection()` in tests.
- SSR: `isPlatformBrowser(PLATFORM_ID)` for any browser-only code (Leaflet, matchMedia, etc.).
- i18n: BOTH `frontend/public/i18n/fr.json` AND `en.json` updated together. No hardcoded user-facing text.
- Conventional Commits.
- The carousel does NOT introduce a new dependency. Custom Angular code only.
- Leaflet IS already a dependency (used by home today). Reuse it; don't introduce alternatives.

---

## Task 1: Hero asset placeholders

**Files:**
- Create: `frontend/public/hero/hero-poster.jpg`
- Create: `frontend/public/hero/hero-loop.mp4`

The plan ships placeholder assets so the implementation has something to render. Replacement with proprietary content can happen later without code change.

- [ ] **Step 1: Create the directory**

```bash
mkdir -p frontend/public/hero
```

- [ ] **Step 2: Download placeholder poster**

Use a free-licence image from Pexels (https://www.pexels.com/license/). For a beauty/skincare hero:

```bash
curl -L "https://images.pexels.com/photos/3373736/pexels-photo-3373736.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&dpr=1" \
  -o frontend/public/hero/hero-poster.jpg
```

(If Pexels URL changes, pick another image at https://www.pexels.com/search/skincare/ matching the brand: soft, natural, warm tones. Aim for a < 200 KB JPG ~ 1920×1080.)

Verify size:

```bash
ls -lh frontend/public/hero/hero-poster.jpg
```

If > 250 KB, recompress (e.g. with ImageMagick or via the URL params `&w=1600`).

- [ ] **Step 3: Download placeholder video**

Pick a free-licence loop from Coverr (https://coverr.co/) or Pexels Videos. For example:

```bash
curl -L "https://videos.pexels.com/video-files/3209828/3209828-hd_1920_1080_25fps.mp4" \
  -o frontend/public/hero/hero-loop.mp4
```

(If unavailable, browse https://www.pexels.com/search/videos/skincare/ for a quiet, looping 8-15s clip and update the URL.)

Verify size:

```bash
ls -lh frontend/public/hero/hero-loop.mp4
```

If > 5 MB, pick a shorter or lower-bitrate clip. Cap at 4 MB for the placeholder.

- [ ] **Step 4: Commit**

Note: video assets are large for git. Commit them now since the placeholder is small enough; if your CI rejects large files, switch to git-lfs and update later.

```bash
git add frontend/public/hero/
git commit -m "chore(home): add placeholder hero poster and video assets"
```

---

## Task 2: GeocodingService extraction

**Files:**
- Create: `frontend/src/app/core/services/geocoding.service.ts`
- Create: `frontend/src/app/core/services/geocoding.service.spec.ts`

Extract the inline `geocodeAddress` + cache from `home.ts:285-303` into a root-scoped service. The future `SalonCarouselComponent` will inject this service when it lazy-initializes the Leaflet map on flip.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/core/services/geocoding.service.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { GeocodingService } from './geocoding.service';

describe('GeocodingService', () => {
  let service: GeocodingService;
  let fetchSpy: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
    });
    service = TestBed.inject(GeocodingService);
    fetchSpy = spyOn(window, 'fetch');
  });

  it('returns coords on first lookup and caches them', async () => {
    fetchSpy.and.resolveTo(new Response(JSON.stringify([{ lat: '48.85', lon: '2.35' }])));
    const first = await service.geocode('1 rue de Paris');
    expect(first).toEqual({ lat: 48.85, lng: 2.35 });
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it('returns cached value on second lookup without re-fetching', async () => {
    fetchSpy.and.resolveTo(new Response(JSON.stringify([{ lat: '48.85', lon: '2.35' }])));
    await service.geocode('1 rue de Paris');
    await service.geocode('1 rue de Paris');
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it('returns null and caches null when no result', async () => {
    fetchSpy.and.resolveTo(new Response(JSON.stringify([])));
    const result = await service.geocode('unknown');
    expect(result).toBeNull();
    await service.geocode('unknown');
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it('returns null and caches null on network error', async () => {
    fetchSpy.and.rejectWith(new Error('boom'));
    const result = await service.geocode('1 rue de Paris');
    expect(result).toBeNull();
    await service.geocode('1 rue de Paris');
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/geocoding.service.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the service**

Create `frontend/src/app/core/services/geocoding.service.ts`:

```typescript
import { Injectable } from '@angular/core';

export interface GeocodeResult {
  readonly lat: number;
  readonly lng: number;
}

/**
 * Wrapper around OpenStreetMap Nominatim with an in-memory cache keyed by
 * the query string. Resolves to null when no result is found OR when the
 * request fails — null results are cached to avoid retrying obviously
 * unreachable addresses.
 *
 * Root-scoped so the cache is shared across the app (home page and salon
 * carousel both use it for the same addresses on a given session).
 */
@Injectable({ providedIn: 'root' })
export class GeocodingService {
  private readonly cache = new Map<string, GeocodeResult | null>();
  private static readonly NOMINATIM = 'https://nominatim.openstreetmap.org/search';

  async geocode(address: string): Promise<GeocodeResult | null> {
    if (this.cache.has(address)) {
      return this.cache.get(address)!;
    }
    const url = `${GeocodingService.NOMINATIM}?format=json&q=${encodeURIComponent(address)}&limit=1`;
    try {
      const res = await fetch(url);
      const data = (await res.json()) as Array<{ lat: string; lon: string }>;
      if (data.length > 0) {
        const result: GeocodeResult = { lat: parseFloat(data[0].lat), lng: parseFloat(data[0].lon) };
        this.cache.set(address, result);
        return result;
      }
    } catch {
      // fall through to null
    }
    this.cache.set(address, null);
    return null;
  }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/geocoding.service.spec.ts' --watch=false
```
Expected: 4 specs pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/services/geocoding.service.ts frontend/src/app/core/services/geocoding.service.spec.ts
git commit -m "feat(geocoding): extract Nominatim wrapper with in-memory cache to root service"
```

---

## Task 3: i18n keys for home redesign

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add FR keys**

Open `frontend/public/i18n/fr.json`. Locate the `"home"` block (search `"home":`).

Inside `"home"`, locate the `"salons"` sub-block. Add three keys:

```json
"flipToMap": "Voir sur la carte",
"flipToPhoto": "Voir la photo",
"viewItinerary": "Voir l'itinéraire"
```

(Add as siblings to existing keys like `"empty"`, `"discoverAll"`. Preserve commas.)

Inside `"home"`, add a new sibling block `"proCta"` (next to `"salons"`):

```json
"proCta": {
  "eyebrow": "Pretty Face Pro",
  "title": "Vous êtes esthéticien·ne ?",
  "body": "Une vitrine élégante, un planning intelligent, des outils pensés pour vous.",
  "button": "Découvrir Pretty Face Pro"
}
```

- [ ] **Step 2: Add EN keys**

Same operations on `frontend/public/i18n/en.json`:

Inside `"home.salons"`:

```json
"flipToMap": "See on the map",
"flipToPhoto": "See the photo",
"viewItinerary": "Get directions"
```

Inside `"home"`:

```json
"proCta": {
  "eyebrow": "Pretty Face Pro",
  "title": "Are you a beauty professional?",
  "body": "A polished storefront, smart scheduling, tools made for you.",
  "button": "Discover Pretty Face Pro"
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
git commit -m "feat(i18n): add home.salons.flip* and home.proCta.* keys"
```

---

## Task 4: HeroVideoComponent

**Files:**
- Create: `frontend/src/app/shared/uis/hero-video/hero-video.component.ts`
- Create: `frontend/src/app/shared/uis/hero-video/hero-video.component.html`
- Create: `frontend/src/app/shared/uis/hero-video/hero-video.component.scss`
- Create: `frontend/src/app/shared/uis/hero-video/hero-video.component.spec.ts`

Renders a `<video autoplay muted loop>` on desktop+hover-capable devices, `<img>` poster otherwise (mobile, SSR, `prefers-reduced-motion`). Exposes a content slot via `<ng-content>` for the overlay (search bar + branding).

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/shared/uis/hero-video/hero-video.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, PLATFORM_ID, provideZonelessChangeDetection } from '@angular/core';
import { HeroVideoComponent } from './hero-video.component';

@Component({
  standalone: true,
  imports: [HeroVideoComponent],
  template: `
    <app-hero-video posterUrl="/hero/p.jpg" videoUrl="/hero/v.mp4">
      <span class="overlay">hello</span>
    </app-hero-video>
  `,
})
class HostComponent {}

describe('HeroVideoComponent', () => {
  function setup(platform: 'browser' | 'server', desktopHover = true): ComponentFixture<HostComponent> {
    if (platform === 'browser') {
      // Stub matchMedia to report desktop+hover.
      window.matchMedia = ((query: string): MediaQueryList => ({
        matches: desktopHover && query.includes('(min-width: 768px)'),
        media: query,
        onchange: null,
        addEventListener: () => {},
        removeEventListener: () => {},
        addListener: () => {},
        removeListener: () => {},
        dispatchEvent: () => false,
      } as unknown as MediaQueryList)) as typeof window.matchMedia;
    }
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

  it('renders <img> poster on the server (SSR)', () => {
    const fixture = setup('server');
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('img.hero-poster')).not.toBeNull();
    expect(root.querySelector('video.hero-video')).toBeNull();
  });

  it('renders <video> on desktop+hover-capable browser', () => {
    const fixture = setup('browser', true);
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('video.hero-video')).not.toBeNull();
  });

  it('renders <img> on browser without hover (mobile)', () => {
    const fixture = setup('browser', false);
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('img.hero-poster')).not.toBeNull();
    expect(root.querySelector('video.hero-video')).toBeNull();
  });

  it('projects ng-content as the overlay', () => {
    const fixture = setup('browser', true);
    const overlay = (fixture.nativeElement as HTMLElement).querySelector('.overlay');
    expect(overlay?.textContent?.trim()).toBe('hello');
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/hero-video.component.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Create the component TS**

Create `frontend/src/app/shared/uis/hero-video/hero-video.component.ts`:

```typescript
import { Component, PLATFORM_ID, computed, inject, input, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * Hero block that renders a fullscreen <video> on desktop devices with a
 * pointer (hover) and a static <img> poster everywhere else (mobile,
 * SSR, prefers-reduced-motion).
 *
 * Overlay content (branding, search bar, etc.) is projected via <ng-content>.
 *
 * SSR-safe: the server always renders the <img> poster. The browser hydrates
 * with <video> only when matchMedia reports desktop AND hover-capable AND
 * reduced motion is NOT requested.
 */
@Component({
  selector: 'app-hero-video',
  standalone: true,
  templateUrl: './hero-video.component.html',
  styleUrl: './hero-video.component.scss',
})
export class HeroVideoComponent {
  readonly posterUrl = input.required<string>();
  /** MP4 source URL. Optional — when missing, only the poster is rendered. */
  readonly videoUrl = input<string | null>(null);
  /** Optional WebM source URL for browsers that prefer it. */
  readonly videoWebmUrl = input<string | null>(null);

  private readonly platformId = inject(PLATFORM_ID);

  // Resolved at construction; HostComponent rebuilds on every test.
  protected readonly showVideo = computed(() => this.shouldShowVideo());

  private shouldShowVideo(): boolean {
    if (!isPlatformBrowser(this.platformId)) return false;
    if (!this.videoUrl()) return false;
    const desktopWithHover = window.matchMedia(
      '(min-width: 768px) and (hover: hover)',
    ).matches;
    const reducedMotion = window.matchMedia(
      '(prefers-reduced-motion: reduce)',
    ).matches;
    return desktopWithHover && !reducedMotion;
  }
}
```

- [ ] **Step 4: Create the template**

Create `frontend/src/app/shared/uis/hero-video/hero-video.component.html`:

```html
<div class="hero-wrapper">
  @if (showVideo()) {
    <video
      class="hero-video"
      [poster]="posterUrl()"
      autoplay
      muted
      loop
      playsinline
      preload="metadata"
      aria-hidden="true"
    >
      @if (videoWebmUrl()) {
        <source [src]="videoWebmUrl()" type="video/webm" />
      }
      <source [src]="videoUrl()" type="video/mp4" />
    </video>
  } @else {
    <img class="hero-poster" [src]="posterUrl()" alt="" aria-hidden="true" />
  }

  <div class="hero-gradient"></div>

  <div class="hero-content">
    <ng-content />
  </div>
</div>
```

- [ ] **Step 5: Create the styles**

Create `frontend/src/app/shared/uis/hero-video/hero-video.component.scss`:

```scss
:host {
  display: block;
  position: relative;
  width: 100%;
}

.hero-wrapper {
  position: relative;
  width: 100%;
  height: 60vh;
  min-height: 420px;
  overflow: hidden;

  @media (min-width: 768px) {
    height: 80vh;
    min-height: 560px;
  }
}

.hero-video,
.hero-poster {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  z-index: 0;
}

.hero-gradient {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: linear-gradient(180deg, transparent 0%, rgba(0, 0, 0, 0.35) 100%);
}

.hero-content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  width: 100%;
  height: 100%;
  padding: 24px;
  text-align: center;
}
```

- [ ] **Step 6: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/hero-video.component.spec.ts' --watch=false
```
Expected: 4 specs pass.

If a test fails on `Element/HTMLElement is undefined` in SSR mode, that's a test setup issue — the SSR test only checks the rendered DOM, not the runtime. Verify the test setup uses `PLATFORM_ID = 'server'` correctly.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/shared/uis/hero-video/
git commit -m "feat(hero-video): add SSR-safe hero block with video on desktop, poster elsewhere"
```

---

## Task 5: SalonCarouselComponent — coverflow layout (no flip yet)

**Files:**
- Create: `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.ts`
- Create: `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.html`
- Create: `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.scss`
- Create: `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.spec.ts`

This task ships the carousel with center/side classes and prev/next arrows. Card flip + map come in Tasks 6-7.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { SalonCarouselComponent } from './salon-carousel.component';
import { SalonCard } from '../../../features/discovery/discovery.model';

function makeSalons(count: number): SalonCard[] {
  return Array.from({ length: count }).map((_, i) => ({
    name: `Salon ${i}`,
    slug: `slug-${i}`,
    description: null,
    logoUrl: null,
    categoryNames: null,
    addressCity: 'Paris',
    fullAddress: `${i} rue de Paris`,
  }));
}

describe('SalonCarouselComponent', () => {
  let fixture: ComponentFixture<SalonCarouselComponent>;
  let component: SalonCarouselComponent;

  function setup(salonsCount: number): void {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
      imports: [
        SalonCarouselComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(SalonCarouselComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('salons', makeSalons(salonsCount));
    fixture.detectChanges();
  }

  it('renders one card per salon', () => {
    setup(5);
    const cards = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid^="salon-card-"]');
    expect(cards.length).toBe(5);
  });

  it('marks card 0 as the centered one initially', () => {
    setup(5);
    const center = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="salon-card-slug-0"]');
    expect(center?.classList.contains('center')).toBe(true);
  });

  it('next() shifts the center to slug-1', () => {
    setup(5);
    component.next();
    fixture.detectChanges();
    const center = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="salon-card-slug-1"]');
    expect(center?.classList.contains('center')).toBe(true);
  });

  it('prev() wraps from index 0 to last', () => {
    setup(5);
    component.prev();
    fixture.detectChanges();
    const center = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="salon-card-slug-4"]');
    expect(center?.classList.contains('center')).toBe(true);
  });

  it('goTo(2) centers slug-2', () => {
    setup(5);
    component.goTo(2);
    fixture.detectChanges();
    const center = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="salon-card-slug-2"]');
    expect(center?.classList.contains('center')).toBe(true);
  });

  it('renders nothing visible when given an empty array', () => {
    setup(0);
    const stage = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="carousel-stage"]');
    expect(stage?.children.length ?? 0).toBe(0);
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/salon-carousel.component.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Create the component TS**

Create `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.ts`:

```typescript
import { Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonCard } from '../../../features/discovery/discovery.model';

const SALON_GRADIENTS = [
  'linear-gradient(135deg, #e8d5c4, #f0e0d0)',
  'linear-gradient(135deg, #d5e0d2, #e0ead5)',
  'linear-gradient(135deg, #d5d5e8, #e0e0f0)',
  'linear-gradient(135deg, #e0d5d8, #f0e5e8)',
  'linear-gradient(135deg, #f0e6cc, #f5ecd5)',
];

interface DisplayCard {
  readonly salon: SalonCard;
  readonly index: number;
  readonly offset: number; // distance from center, signed
  readonly position: 'center' | 'side' | 'side-far' | 'hidden';
  readonly gradient: string;
}

@Component({
  selector: 'app-salon-carousel',
  standalone: true,
  imports: [RouterLink, MatIconModule, TranslocoPipe],
  templateUrl: './salon-carousel.component.html',
  styleUrl: './salon-carousel.component.scss',
})
export class SalonCarouselComponent {
  readonly salons = input.required<SalonCard[]>();

  readonly centerIndex = signal(0);

  protected readonly displayCards = computed<DisplayCard[]>(() => {
    const salons = this.salons();
    if (salons.length === 0) return [];
    const center = this.centerIndex();
    return salons.map((salon, index) => {
      const rawOffset = index - center;
      // Signed offset normalized to [-half, half] for circular layout.
      const half = Math.floor(salons.length / 2);
      const offset =
        rawOffset > half
          ? rawOffset - salons.length
          : rawOffset < -half
            ? rawOffset + salons.length
            : rawOffset;
      const abs = Math.abs(offset);
      const position: DisplayCard['position'] =
        abs === 0 ? 'center' : abs === 1 ? 'side' : abs === 2 ? 'side-far' : 'hidden';
      const gradient = SALON_GRADIENTS[index % SALON_GRADIENTS.length];
      return { salon, index, offset, position, gradient };
    });
  });

  private readonly router = inject(Router);

  next(): void {
    const len = this.salons().length;
    if (len === 0) return;
    this.centerIndex.update((i) => (i + 1) % len);
  }

  prev(): void {
    const len = this.salons().length;
    if (len === 0) return;
    this.centerIndex.update((i) => (i - 1 + len) % len);
  }

  goTo(index: number): void {
    const len = this.salons().length;
    if (index < 0 || index >= len) return;
    this.centerIndex.set(index);
  }

  onCardClick(card: DisplayCard, event: MouseEvent): void {
    if (card.position === 'hidden') return;
    if (card.position !== 'center') {
      event.preventDefault();
      event.stopPropagation();
      this.goTo(card.index);
      return;
    }
    // Center card click: navigate to /salon/<slug>.
    this.router.navigate(['/salon', card.salon.slug]);
  }
}
```

- [ ] **Step 4: Create the template**

Create `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.html`:

```html
<div
  class="carousel-stage"
  data-testid="carousel-stage"
  role="region"
  aria-roledescription="carousel"
  [attr.aria-label]="'home.nearYou' | transloco"
>
  @if (salons().length > 0) {
    <button
      type="button"
      class="nav-arrow nav-prev"
      (click)="prev()"
      [attr.aria-label]="'home.salons.previous' | transloco"
    >
      <mat-icon aria-hidden="true">chevron_left</mat-icon>
    </button>

    @for (card of displayCards(); track card.salon.slug) {
      <button
        type="button"
        class="salon-card"
        [class.center]="card.position === 'center'"
        [class.side]="card.position === 'side'"
        [class.side-far]="card.position === 'side-far'"
        [class.hidden]="card.position === 'hidden'"
        [style.background]="card.salon.logoUrl ? 'url(' + card.salon.logoUrl + ') center/cover' : card.gradient"
        [attr.data-testid]="'salon-card-' + card.salon.slug"
        [attr.aria-current]="card.position === 'center' ? 'true' : null"
        [attr.aria-hidden]="card.position === 'hidden' ? true : null"
        [attr.tabindex]="card.position === 'center' ? 0 : -1"
        (click)="onCardClick(card, $event)"
      >
        <div class="card-overlay">
          <span class="card-name">{{ card.salon.name }}</span>
          @if (card.salon.addressCity) {
            <span class="card-loc">{{ card.salon.addressCity }}</span>
          }
        </div>
      </button>
    }

    <button
      type="button"
      class="nav-arrow nav-next"
      (click)="next()"
      [attr.aria-label]="'home.salons.next' | transloco"
    >
      <mat-icon aria-hidden="true">chevron_right</mat-icon>
    </button>
  }
</div>
```

- [ ] **Step 5: Create the styles**

Create `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.scss`:

```scss
:host {
  display: block;
  width: 100%;
}

.carousel-stage {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 32px 16px;
  perspective: 1600px;
  overflow: hidden;
  min-height: 460px;
}

.salon-card {
  flex-shrink: 0;
  border: none;
  cursor: pointer;
  border-radius: 16px;
  overflow: hidden;
  position: relative;
  background-size: cover;
  background-position: center;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  padding: 0;

  @media (prefers-reduced-motion: no-preference) {
    transition:
      width 350ms ease,
      height 350ms ease,
      transform 350ms ease,
      opacity 350ms ease,
      box-shadow 350ms ease;
  }

  &.center {
    width: 360px;
    height: 420px;
    opacity: 1;
    transform: scale(1);
    box-shadow: 0 12px 36px rgba(192, 0, 102, 0.18);
    z-index: 3;
  }

  &.side {
    width: 220px;
    height: 300px;
    opacity: 0.55;
    transform: scale(0.92);
    z-index: 2;
  }

  &.side-far {
    width: 180px;
    height: 240px;
    opacity: 0.3;
    transform: scale(0.85);
    z-index: 1;
  }

  &.hidden {
    display: none;
  }

  // Mobile: only show the centered card (full-width). Sides are hidden.
  @media (max-width: 767px) {
    &.center {
      width: 80vw;
      max-width: 320px;
      height: 380px;
    }
    &.side,
    &.side-far {
      display: none;
    }
  }
}

.card-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px 18px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  color: white;
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: left;
}

.card-name {
  font-size: 18px;
  font-weight: 500;
  letter-spacing: 0.02em;
}

.card-loc {
  font-size: 12px;
  opacity: 0.85;
}

.nav-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  background: white;
  border: 1px solid #eee;
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #444;
  z-index: 5;
  cursor: pointer;
}

.nav-prev {
  left: 24px;
}
.nav-next {
  right: 24px;
}

@media (max-width: 767px) {
  .nav-arrow {
    width: 36px;
    height: 36px;
    background: rgba(255, 255, 255, 0.92);
  }
  .nav-prev {
    left: 8px;
  }
  .nav-next {
    right: 8px;
  }
}
```

- [ ] **Step 6: Add the missing i18n keys for nav arrows**

Open `frontend/public/i18n/fr.json`. Inside `home.salons`, add:

```json
"previous": "Salon précédent",
"next": "Salon suivant"
```

Open `frontend/public/i18n/en.json`. Inside `home.salons`:

```json
"previous": "Previous salon",
"next": "Next salon"
```

Validate:

```bash
python3 -m json.tool frontend/public/i18n/fr.json > /dev/null && echo OK
python3 -m json.tool frontend/public/i18n/en.json > /dev/null && echo OK
```

- [ ] **Step 7: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/salon-carousel.component.spec.ts' --watch=false
```
Expected: 6 specs pass.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/shared/uis/salon-carousel/ frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat(salon-carousel): add coverflow layout with prev/next + click-to-center"
```

---

## Task 6: SalonCarouselComponent — flip 3D + back face placeholder

**Files:**
- Modify: `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.ts`
- Modify: `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.html`
- Modify: `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.scss`
- Modify: `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.spec.ts`

We add the flip toggle on the centered card. The back face shows a placeholder div for now (Task 7 lazy-loads the actual Leaflet map).

- [ ] **Step 1: Add the failing test**

Append inside the existing `describe` (just before the closing `});`):

```typescript
  it('toggles flipped state when toggleFlip is called', () => {
    setup(5);
    expect(component.isFlipped('slug-0')).toBe(false);
    component.toggleFlip('slug-0');
    fixture.detectChanges();
    expect(component.isFlipped('slug-0')).toBe(true);
    component.toggleFlip('slug-0');
    fixture.detectChanges();
    expect(component.isFlipped('slug-0')).toBe(false);
  });

  it('un-flips when the center changes', () => {
    setup(5);
    component.toggleFlip('slug-0');
    expect(component.isFlipped('slug-0')).toBe(true);
    component.next();
    fixture.detectChanges();
    expect(component.isFlipped('slug-0')).toBe(false);
  });

  it('renders a flip toggle button on the center card', () => {
    setup(5);
    const button = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="flip-toggle-slug-0"]',
    );
    expect(button).not.toBeNull();
  });

  it('does not render flip toggle on side cards', () => {
    setup(5);
    const button = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="flip-toggle-slug-1"]',
    );
    expect(button).toBeNull();
  });
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd frontend && npm test -- --include='**/salon-carousel.component.spec.ts' --watch=false
```
Expected: 4 new specs FAIL.

- [ ] **Step 3: Update the component TS**

Open `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.ts`. Replace its entire content with:

```typescript
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonCard } from '../../../features/discovery/discovery.model';

const SALON_GRADIENTS = [
  'linear-gradient(135deg, #e8d5c4, #f0e0d0)',
  'linear-gradient(135deg, #d5e0d2, #e0ead5)',
  'linear-gradient(135deg, #d5d5e8, #e0e0f0)',
  'linear-gradient(135deg, #e0d5d8, #f0e5e8)',
  'linear-gradient(135deg, #f0e6cc, #f5ecd5)',
];

interface DisplayCard {
  readonly salon: SalonCard;
  readonly index: number;
  readonly offset: number;
  readonly position: 'center' | 'side' | 'side-far' | 'hidden';
  readonly gradient: string;
}

@Component({
  selector: 'app-salon-carousel',
  standalone: true,
  imports: [RouterLink, MatIconModule, TranslocoPipe],
  templateUrl: './salon-carousel.component.html',
  styleUrl: './salon-carousel.component.scss',
})
export class SalonCarouselComponent {
  readonly salons = input.required<SalonCard[]>();

  readonly centerIndex = signal(0);
  private readonly flippedSlugs = signal<ReadonlySet<string>>(new Set());

  protected readonly displayCards = computed<DisplayCard[]>(() => {
    const salons = this.salons();
    if (salons.length === 0) return [];
    const center = this.centerIndex();
    return salons.map((salon, index) => {
      const rawOffset = index - center;
      const half = Math.floor(salons.length / 2);
      const offset =
        rawOffset > half
          ? rawOffset - salons.length
          : rawOffset < -half
            ? rawOffset + salons.length
            : rawOffset;
      const abs = Math.abs(offset);
      const position: DisplayCard['position'] =
        abs === 0 ? 'center' : abs === 1 ? 'side' : abs === 2 ? 'side-far' : 'hidden';
      const gradient = SALON_GRADIENTS[index % SALON_GRADIENTS.length];
      return { salon, index, offset, position, gradient };
    });
  });

  private readonly router = inject(Router);

  constructor() {
    // When the center changes, un-flip all cards. Avoids the awkward
    // visual of a card sliding sideways while showing the map.
    effect(
      () => {
        // Read centerIndex to subscribe.
        this.centerIndex();
        if (this.flippedSlugs().size > 0) {
          this.flippedSlugs.set(new Set());
        }
      },
      { allowSignalWrites: true },
    );
  }

  isFlipped(slug: string): boolean {
    return this.flippedSlugs().has(slug);
  }

  toggleFlip(slug: string): void {
    const next = new Set(this.flippedSlugs());
    if (next.has(slug)) {
      next.delete(slug);
    } else {
      next.add(slug);
    }
    this.flippedSlugs.set(next);
  }

  next(): void {
    const len = this.salons().length;
    if (len === 0) return;
    this.centerIndex.update((i) => (i + 1) % len);
  }

  prev(): void {
    const len = this.salons().length;
    if (len === 0) return;
    this.centerIndex.update((i) => (i - 1 + len) % len);
  }

  goTo(index: number): void {
    const len = this.salons().length;
    if (index < 0 || index >= len) return;
    this.centerIndex.set(index);
  }

  onCardClick(card: DisplayCard, event: MouseEvent): void {
    if (card.position === 'hidden') return;
    if (card.position !== 'center') {
      event.preventDefault();
      event.stopPropagation();
      this.goTo(card.index);
      return;
    }
    if (this.isFlipped(card.salon.slug)) {
      // Don't navigate when the card is flipped — let the user dismiss the map first.
      return;
    }
    this.router.navigate(['/salon', card.salon.slug]);
  }

  onFlipClick(card: DisplayCard, event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.toggleFlip(card.salon.slug);
  }
}
```

- [ ] **Step 4: Update the template**

Replace the content of `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.html` with:

```html
<div
  class="carousel-stage"
  data-testid="carousel-stage"
  role="region"
  aria-roledescription="carousel"
  [attr.aria-label]="'home.nearYou' | transloco"
>
  @if (salons().length > 0) {
    <button
      type="button"
      class="nav-arrow nav-prev"
      (click)="prev()"
      [attr.aria-label]="'home.salons.previous' | transloco"
    >
      <mat-icon aria-hidden="true">chevron_left</mat-icon>
    </button>

    @for (card of displayCards(); track card.salon.slug) {
      <div
        class="salon-card-container"
        [class.center]="card.position === 'center'"
        [class.side]="card.position === 'side'"
        [class.side-far]="card.position === 'side-far'"
        [class.hidden]="card.position === 'hidden'"
      >
        <button
          type="button"
          class="salon-card"
          [class.flipped]="isFlipped(card.salon.slug)"
          [style.background]="card.salon.logoUrl ? 'none' : card.gradient"
          [attr.data-testid]="'salon-card-' + card.salon.slug"
          [attr.aria-current]="card.position === 'center' ? 'true' : null"
          [attr.aria-hidden]="card.position === 'hidden' ? true : null"
          [attr.tabindex]="card.position === 'center' ? 0 : -1"
          (click)="onCardClick(card, $event)"
        >
          <div class="card-flip-inner">
            <div class="card-face card-front" [style.background]="card.salon.logoUrl ? 'url(' + card.salon.logoUrl + ') center/cover' : card.gradient">
              <div class="card-overlay">
                <span class="card-name">{{ card.salon.name }}</span>
                @if (card.salon.addressCity) {
                  <span class="card-loc">{{ card.salon.addressCity }}</span>
                }
              </div>
            </div>
            <div class="card-face card-back">
              <div class="card-back-map" data-testid="card-map-placeholder"></div>
              <div class="card-back-info">
                <span class="card-back-name">{{ card.salon.name }}</span>
                @if (card.salon.fullAddress) {
                  <span class="card-back-addr">{{ card.salon.fullAddress }}</span>
                }
              </div>
            </div>
          </div>
        </button>

        @if (card.position === 'center') {
          <button
            type="button"
            class="flip-toggle"
            [class.is-flipped]="isFlipped(card.salon.slug)"
            (click)="onFlipClick(card, $event)"
            [attr.aria-pressed]="isFlipped(card.salon.slug)"
            [attr.aria-label]="
              (isFlipped(card.salon.slug) ? 'home.salons.flipToPhoto' : 'home.salons.flipToMap') | transloco
            "
            [attr.data-testid]="'flip-toggle-' + card.salon.slug"
          >
            <mat-icon aria-hidden="true">{{ isFlipped(card.salon.slug) ? 'image' : 'place' }}</mat-icon>
          </button>
        }
      </div>
    }

    <button
      type="button"
      class="nav-arrow nav-next"
      (click)="next()"
      [attr.aria-label]="'home.salons.next' | transloco"
    >
      <mat-icon aria-hidden="true">chevron_right</mat-icon>
    </button>
  }
</div>
```

- [ ] **Step 5: Update the styles**

Replace the content of `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.scss` with:

```scss
:host {
  display: block;
  width: 100%;
}

.carousel-stage {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 32px 16px;
  perspective: 1600px;
  overflow: hidden;
  min-height: 460px;
}

// ===== Card container (handles position + animation) =====
.salon-card-container {
  position: relative;
  flex-shrink: 0;

  @media (prefers-reduced-motion: no-preference) {
    transition:
      width 350ms ease,
      height 350ms ease,
      transform 350ms ease,
      opacity 350ms ease;
  }

  &.center {
    width: 360px;
    height: 420px;
    opacity: 1;
    transform: scale(1);
    z-index: 3;
  }

  &.side {
    width: 220px;
    height: 300px;
    opacity: 0.55;
    transform: scale(0.92);
    z-index: 2;
  }

  &.side-far {
    width: 180px;
    height: 240px;
    opacity: 0.3;
    transform: scale(0.85);
    z-index: 1;
  }

  &.hidden {
    display: none;
  }

  @media (max-width: 767px) {
    &.center {
      width: 80vw;
      max-width: 320px;
      height: 380px;
    }
    &.side,
    &.side-far {
      display: none;
    }
  }
}

// ===== Flip card =====
.salon-card {
  width: 100%;
  height: 100%;
  border: none;
  cursor: pointer;
  background: none;
  padding: 0;
  position: relative;
  perspective: 1600px;
  border-radius: 16px;
}

.center .salon-card {
  box-shadow: 0 12px 36px rgba(192, 0, 102, 0.18);
  border-radius: 16px;
}

.card-flip-inner {
  position: relative;
  width: 100%;
  height: 100%;
  transform-style: preserve-3d;
  border-radius: 16px;

  @media (prefers-reduced-motion: no-preference) {
    transition: transform 700ms cubic-bezier(0.4, 0, 0.2, 1);
  }
}

.salon-card.flipped .card-flip-inner {
  transform: rotateY(180deg);
}

.card-face {
  position: absolute;
  inset: 0;
  border-radius: 16px;
  overflow: hidden;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  background-size: cover;
  background-position: center;
}

.card-front {
  // Gradient fallback set inline; logoUrl set inline.
}

.card-back {
  transform: rotateY(180deg);
  background: white;
  display: flex;
  flex-direction: column;
}

.card-back-map {
  flex: 1;
  background:
    linear-gradient(45deg, transparent 47%, #c8d8c8 48%, #c8d8c8 52%, transparent 53%),
    linear-gradient(-45deg, transparent 47%, #c8d8c8 48%, #c8d8c8 52%, transparent 53%),
    linear-gradient(135deg, #e8f0e8 0%, #d8e6d8 100%);
  background-size: 40px 40px, 40px 40px, 100% 100%;
  position: relative;
}

.card-back-info {
  background: white;
  padding: 12px 16px;
  border-top: 1px solid #eee;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.card-back-name {
  font-size: 14px;
  font-weight: 600;
  color: #222;
}

.card-back-addr {
  font-size: 11px;
  color: #666;
}

// ===== Front overlay =====
.card-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px 18px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  color: white;
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: left;
}

.card-name {
  font-size: 18px;
  font-weight: 500;
  letter-spacing: 0.02em;
}

.card-loc {
  font-size: 12px;
  opacity: 0.85;
}

// ===== Flip toggle button =====
.flip-toggle {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 40px;
  height: 40px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.96);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  z-index: 10;
  color: #c06;

  @media (prefers-reduced-motion: no-preference) {
    transition:
      background 200ms ease,
      transform 150ms ease;
    &:hover {
      transform: scale(1.05);
    }
  }

  &.is-flipped {
    background: #c06;
    color: white;
  }
}

// ===== Nav arrows =====
.nav-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  background: white;
  border: 1px solid #eee;
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #444;
  z-index: 5;
  cursor: pointer;
}

.nav-prev {
  left: 24px;
}
.nav-next {
  right: 24px;
}

@media (max-width: 767px) {
  .nav-arrow {
    width: 36px;
    height: 36px;
    background: rgba(255, 255, 255, 0.92);
  }
  .nav-prev {
    left: 8px;
  }
  .nav-next {
    right: 8px;
  }
}
```

- [ ] **Step 6: Run tests — expect pass**

```bash
cd frontend && npm test -- --include='**/salon-carousel.component.spec.ts' --watch=false
```
Expected: 10 specs pass (6 from Task 5 + 4 new).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/shared/uis/salon-carousel/
git commit -m "feat(salon-carousel): add 3D flip with placeholder back face"
```

---

## Task 7: SalonCarouselComponent — lazy Leaflet map on flip

**Files:**
- Modify: `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.ts`
- Modify: `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.html`

The placeholder div on the back face is replaced by a real Leaflet map, lazy-loaded on first flip. We reuse `GeocodingService` (Task 2) and import Leaflet dynamically only when needed.

- [ ] **Step 1: Update the component TS**

Open `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.ts`. Add imports near the top:

```typescript
import { ElementRef, PLATFORM_ID, viewChildren } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { GeocodingService } from '../../../core/services/geocoding.service';
```

Inside the class, near the existing `inject(Router)` line, add:

```typescript
  private readonly geocoding = inject(GeocodingService);
  private readonly platformId = inject(PLATFORM_ID);
  private leaflet: any = null;
  private readonly mapRefs = viewChildren<ElementRef<HTMLElement>>('mapHost');
  private readonly mapsBuilt = new Set<string>();
```

Add a method that builds a Leaflet map for a given slug + container element:

```typescript
  private async buildMapFor(slug: string, address: string | null, host: HTMLElement): Promise<void> {
    if (!isPlatformBrowser(this.platformId)) return;
    if (this.mapsBuilt.has(slug)) return;
    if (!address) return;

    if (!this.leaflet) {
      const mod = await import('leaflet');
      this.leaflet = (mod as any).default ?? mod;
    }
    const coords = await this.geocoding.geocode(address);
    if (!coords) return;

    const map = this.leaflet
      .map(host, { zoomControl: false, attributionControl: false, dragging: false, scrollWheelZoom: false, doubleClickZoom: false, touchZoom: false })
      .setView([coords.lat, coords.lng], 14);
    this.leaflet
      .tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', { maxZoom: 19 })
      .addTo(map);
    const icon = this.leaflet.divIcon({
      className: 'salon-pin',
      html: '<div class="salon-pin-shape"></div>',
      iconSize: [22, 22],
      iconAnchor: [11, 22],
    });
    this.leaflet.marker([coords.lat, coords.lng], { icon }).addTo(map);
    this.mapsBuilt.add(slug);
    setTimeout(() => map.invalidateSize(), 50);
  }
```

Update `toggleFlip` to trigger the map build when flipping ON:

```typescript
  toggleFlip(slug: string): void {
    const next = new Set(this.flippedSlugs());
    const wasFlipped = next.has(slug);
    if (wasFlipped) {
      next.delete(slug);
    } else {
      next.add(slug);
    }
    this.flippedSlugs.set(next);

    if (!wasFlipped) {
      // Defer to next tick so the back face is in the DOM.
      setTimeout(() => this.buildMapForSlug(slug), 0);
    }
  }

  private buildMapForSlug(slug: string): void {
    const salon = this.salons().find((s) => s.slug === slug);
    if (!salon) return;
    const host = this.mapRefs().find((ref) => ref.nativeElement.dataset['slug'] === slug)?.nativeElement;
    if (!host) return;
    void this.buildMapFor(slug, salon.fullAddress, host);
  }
```

(Replace the existing `toggleFlip` with this version.)

- [ ] **Step 2: Update the template — replace the placeholder map div**

In `frontend/src/app/shared/uis/salon-carousel/salon-carousel.component.html`, find:

```html
              <div class="card-back-map" data-testid="card-map-placeholder"></div>
```

Replace with:

```html
              <div
                #mapHost
                class="card-back-map"
                [attr.data-slug]="card.salon.slug"
                data-testid="card-map-placeholder"
              ></div>
```

- [ ] **Step 3: Add salon-pin global styles**

Open `frontend/src/styles.scss`. Append:

```scss
/* === Salon carousel map pin === */
.salon-pin {
  background: none !important;
  border: none !important;
}

.salon-pin-shape {
  width: 18px;
  height: 18px;
  background: linear-gradient(135deg, #a8385d, #c06);
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  border: 2px solid white;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}
```

- [ ] **Step 4: Verify all carousel tests still pass**

```bash
cd frontend && npm test -- --include='**/salon-carousel.component.spec.ts' --watch=false
```
Expected: 10 specs pass (the test for `card-map-placeholder` still matches the new `<div>`).

- [ ] **Step 5: Verify TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/shared/uis/salon-carousel/ frontend/src/styles.scss
git commit -m "feat(salon-carousel): lazy-load Leaflet map on first flip"
```

---

## Task 8: Refactor `home.ts` — drop inline geocode, prepare for new components

**Files:**
- Modify: `frontend/src/app/pages/home/home.ts`

We strip the inline geocode + Leaflet logic from `home.ts`. The mini-map is going away (Task 9), so we don't need `mapInstance`, `markerMap`, `userMarker`, `geocodeAndPlotSalons`, `geocodeAddress`, `geocodeCache`. The component still loads salons and recent posts.

- [ ] **Step 1: Replace home.ts content**

Open `frontend/src/app/pages/home/home.ts` and replace its entire content with:

```typescript
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { toSignal, toObservable } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { DiscoveryService } from '../../features/discovery/discovery.service';
import { SalonCard } from '../../features/discovery/discovery.model';
import { PostsService } from '../../features/posts/posts.service';
import { RecentPost } from '../../features/posts/posts.model';
import { RecentPostsViewerComponent } from '../../features/posts/recent-posts-viewer/recent-posts-viewer.component';
import { HeroVideoComponent } from '../../shared/uis/hero-video/hero-video.component';
import { SalonCarouselComponent } from '../../shared/uis/salon-carousel/salon-carousel.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    TranslocoPipe,
    MatIconModule,
    RecentPostsViewerComponent,
    HeroVideoComponent,
    SalonCarouselComponent,
  ],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly router = inject(Router);
  private readonly discoveryService = inject(DiscoveryService);
  private readonly postsService = inject(PostsService);

  readonly searchQuery = signal('');

  readonly salons = toSignal(
    toObservable(this.searchQuery).pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap((q) => this.discoveryService.searchSalons(null, q || null)),
    ),
    { initialValue: [] as SalonCard[] },
  );

  readonly recentPosts = toSignal(this.postsService.listRecentPublic(), {
    initialValue: [] as RecentPost[],
  });

  onSearch(): void {
    // Reactive — handled by toObservable(searchQuery).
  }

  onDiscoverAll(): void {
    this.router.navigate(['/discover']);
  }

  onProCta(): void {
    this.router.navigate(['/pricing']);
  }
}
```

- [ ] **Step 2: Verify TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

(The home template still references `miniMap`, `selectedSlug`, `onSalonCardClick` etc. — those will be removed in Task 9. TS won't error because the template is checked against the new TS file; the now-orphaned bindings will simply not match. If `strictTemplates` is on and complains, Task 9 fixes it. The template will be replaced wholesale.)

- [ ] **Step 3: Run home tests if they exist**

```bash
ls frontend/src/app/pages/home/home.spec.ts 2>/dev/null && \
  cd frontend && npm test -- --include='**/home.spec.ts' --watch=false
```

Expect either no spec file (skip) or PASS after Task 9 lands. If tests fail at this step due to template/TS mismatch, **don't commit Task 8 alone** — pair it with Task 9 in a single commit. See Task 9.

- [ ] **Step 4: Don't commit yet — Task 9 lands together**

The template needs replacement before this is shippable. Skip to Task 9.

---

## Task 9: Replace home.html and home.scss

**Files:**
- Modify: `frontend/src/app/pages/home/home.html`
- Modify: `frontend/src/app/pages/home/home.scss`

Drop the existing hero, mini-map, mini-cards, and pro-cta. Mount the new components.

- [ ] **Step 1: Replace the template**

Replace the entire content of `frontend/src/app/pages/home/home.html` with:

```html
<!-- Hero -->
<app-hero-video
  posterUrl="/hero/hero-poster.jpg"
  videoUrl="/hero/hero-loop.mp4"
>
  <div class="hero-brand">
    <span class="hero-brand-pretty">Pretty</span>
    <span class="hero-brand-face">Face</span>
  </div>
  <p class="hero-subtitle">{{ 'home.hero.subtitle' | transloco }}</p>
  <form class="hero-search" (submit)="onSearch(); $event.preventDefault()">
    <mat-icon class="hero-search-icon">search</mat-icon>
    <input
      type="text"
      class="hero-search-input"
      [placeholder]="'home.hero.search' | transloco"
      [value]="searchQuery()"
      (input)="searchQuery.set($any($event.target).value)"
    />
  </form>
</app-hero-video>

<!-- Salons (carousel) -->
<section class="landing-section landing-section-wide">
  <h2 class="section-title">{{ 'home.nearYou' | transloco }}</h2>
  @if (salons().length > 0) {
    <app-salon-carousel [salons]="salons()" />
  } @else {
    <p class="empty-hint">{{ 'home.salons.empty' | transloco }}</p>
  }
  <div class="discover-all">
    <button type="button" class="discover-all-btn" (click)="onDiscoverAll()">
      {{ 'home.salons.discoverAll' | transloco }} &rarr;
    </button>
  </div>
</section>

<!-- Recent posts (preserved) -->
@if (recentPosts().length > 0) {
  <section class="landing-section">
    <h2 class="section-title">{{ 'home.recentPosts' | transloco }}</h2>
    <app-recent-posts-viewer [posts]="recentPosts()" />
  </section>
}

<!-- Pro CTA -->
<section class="pro-cta">
  <div class="pro-cta-inner">
    <div class="pro-cta-text">
      <span class="pro-cta-eyebrow">{{ 'home.proCta.eyebrow' | transloco }}</span>
      <h3 class="pro-cta-title">{{ 'home.proCta.title' | transloco }}</h3>
      <p class="pro-cta-body">{{ 'home.proCta.body' | transloco }}</p>
    </div>
    <button type="button" class="pro-cta-button" (click)="onProCta()">
      {{ 'home.proCta.button' | transloco }} &rarr;
    </button>
  </div>
</section>
```

- [ ] **Step 2: Replace the styles**

Replace the entire content of `frontend/src/app/pages/home/home.scss` with:

```scss
// ===== Hero overlay (rendered inside <app-hero-video>'s ng-content slot) =====
.hero-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  line-height: 1;
  margin-bottom: 8px;
}

.hero-brand-pretty {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.5em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.85);
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.15);
}

.hero-brand-face {
  font-size: 3rem;
  font-weight: 200;
  letter-spacing: 0.15em;
  text-transform: uppercase;
  color: white;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);

  @media (min-width: 768px) {
    font-size: 5rem;
  }
}

.hero-subtitle {
  font-size: 1rem;
  color: rgba(255, 255, 255, 0.85);
  font-weight: 300;
  margin-bottom: 28px;
  max-width: 560px;

  @media (min-width: 768px) {
    font-size: 1.125rem;
  }
}

.hero-search {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 28px;
  padding: 12px 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 420px;
  backdrop-filter: blur(4px);

  @media (min-width: 768px) {
    max-width: 560px;
  }
}

.hero-search-icon {
  font-size: 20px;
  width: 20px;
  height: 20px;
  color: #999;
  flex-shrink: 0;
}

.hero-search-input {
  border: none;
  outline: none;
  font-size: 14px;
  color: #333;
  width: 100%;
  background: transparent;

  &::placeholder {
    color: #999;
  }
}

// ===== Landing sections =====
.landing-section {
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 24px;
}

.landing-section-wide {
  max-width: 1280px;
}

.section-title {
  font-size: 0.875rem;
  font-weight: 500;
  color: #666;
  margin-bottom: 16px;
}

.discover-all {
  text-align: center;
  margin-top: 20px;
}

.discover-all-btn {
  font-size: 13px;
  font-weight: 500;
  color: #c06;
  background: none;
  border: none;
  cursor: pointer;
  transition: color 150ms ease;

  &:hover {
    color: #a05;
  }
}

.empty-hint {
  font-size: 13px;
  color: #999;
  text-align: center;
}

// ===== Pro CTA =====
.pro-cta {
  margin: 40px 0 80px;
  padding: 48px 24px;
  background: linear-gradient(135deg, #fdf3f7 0%, #f7ece4 100%);
  border-top: 1px solid rgba(192, 0, 102, 0.08);
  border-bottom: 1px solid rgba(192, 0, 102, 0.08);
}

.pro-cta-inner {
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  text-align: center;

  @media (min-width: 768px) {
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    text-align: left;
    gap: 32px;
  }
}

.pro-cta-text {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pro-cta-eyebrow {
  font-size: 11px;
  font-weight: 600;
  color: #c06;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.pro-cta-title {
  font-size: 1.25rem;
  font-weight: 500;
  color: #222;
  margin: 0;

  @media (min-width: 768px) {
    font-size: 1.5rem;
  }
}

.pro-cta-body {
  font-size: 13px;
  color: #555;
  margin: 0;
  max-width: 540px;
}

.pro-cta-button {
  font-size: 14px;
  font-weight: 500;
  color: white;
  background: #c06;
  border: none;
  border-radius: 999px;
  padding: 12px 24px;
  cursor: pointer;
  transition: background 150ms ease;
  flex-shrink: 0;

  &:hover {
    background: #a05;
  }
}
```

- [ ] **Step 3: Verify TS compile + run home tests**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

```bash
cd frontend && npm test -- --include='**/home.spec.ts' --watch=false 2>&1 | tail -10
```

If `home.spec.ts` exists and fails because it asserts on the old template (mini-map, salon-mini-cards), update assertions to target the new structure (e.g. `app-hero-video`, `app-salon-carousel`). If the spec is small and superseded, simplify it to:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Home } from './home';

describe('Home', () => {
  let fixture: ComponentFixture<Home>;

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
        Home,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
  });

  it('renders hero, salon carousel placeholder, and pro CTA', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('app-hero-video')).not.toBeNull();
    expect(root.querySelector('.pro-cta')).not.toBeNull();
  });
});
```

- [ ] **Step 4: Commit Tasks 8 + 9 together**

```bash
git add frontend/src/app/pages/home/home.ts frontend/src/app/pages/home/home.html frontend/src/app/pages/home/home.scss frontend/src/app/pages/home/home.spec.ts
git commit -m "feat(home): replace inline mini-map with hero-video + salon carousel + new pro CTA"
```

(Stage `home.spec.ts` only if you actually edited it.)

---

## Task 10: Final integration check

**Files:** none (verification + smoke test).

- [ ] **Step 1: Run focused frontend test suite**

```bash
cd frontend && npm test -- --include='**/geocoding.service.spec.ts' --include='**/hero-video.component.spec.ts' --include='**/salon-carousel.component.spec.ts' --include='**/home.spec.ts' --watch=false
```
Expected: PASS across all Jalon 3 specs.

- [ ] **Step 2: Run wider home-adjacent tests**

```bash
cd frontend && npm test -- --include='**/home.spec.ts' --include='**/discovery*.spec.ts' --include='**/posts*.spec.ts' --watch=false
```
Expected: PASS, no regressions.

- [ ] **Step 3: TS compile**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 4: Smoke check (visual)**

Start the backend (`cd backend && ./mvnw spring-boot:run`) and frontend (`cd frontend && npm start`). Open http://localhost:4200 in a desktop browser:

1. Hero: video plays, autoplay+muted+loop. Branding "PRETTY · Face" visible. Search bar functional.
2. Carousel: 5 cards visible (center + 2 sides + 2 far-sides), centered card has the rose shadow. Click a side card → it becomes centered.
3. Click the centered card (not on the flip button) → navigate to `/salon/<slug>`.
4. Click the flip button on the centered card → 3D flip animates (700ms). Back face shows a Leaflet map with a pin. Re-click → flips back.
5. While flipped, click Next/Prev → the card un-flips before sliding.
6. Recent posts: still functional below the carousel.
7. Pro CTA: visible with rose gradient. Click "Découvrir Pretty Face Pro" → navigates to `/pricing`.

Resize the browser to < 768px:
1. Hero: poster image only, no video.
2. Carousel: only the centered card is visible (full-width).
3. Pro CTA: stacks vertically.

- [ ] **Step 5: Lighthouse check (optional but recommended)**

In Chrome DevTools → Lighthouse → Mobile + Performance + Accessibility. Run on http://localhost:4200/.

Targets:
- Performance ≥ 70 (placeholder video may hurt; production asset will improve).
- Accessibility ≥ 95.

If Performance is < 60, verify `preload="metadata"` is set on the video and the poster image is < 250 KB.

- [ ] **Step 6: Final commit (only if Step 1-3 surfaced fix-ups)**

If everything passed, skip. Otherwise:

```bash
git add -A
git commit -m "fix(home-redesign): address integration issues"
```

---

## Self-Review Notes

**Spec coverage check:**

| Spec requirement | Implemented in |
|------------------|----------------|
| Video hero on PC + image poster mobile + SSR-safe | Tasks 1, 4, 9 |
| matchMedia(`min-width:768px and hover:hover`) gate | Task 4 (`shouldShowVideo`) |
| `prefers-reduced-motion` falls back to `<img>` | Task 4 |
| Branding "PRETTY · Face" 5rem PC | Task 9 (SCSS `.hero-brand-face`) |
| Search bar `max-width: 560px` PC | Task 9 (SCSS `.hero-search`) |
| Multi-card coverflow (5 visible: center + 2 sides + 2 far) | Task 5 (`displayCards` computed) |
| Center 360×420, side 220×300 (0.55 op), far 180×240 (0.3 op) | Task 5 (SCSS `.center/.side/.side-far`) |
| Hidden cards (`display: none`) | Tasks 5, 6 |
| Mobile: only center card visible | Task 5 (mobile @media) |
| 3D flip 700ms cubic-bezier | Task 6 (SCSS `.card-flip-inner`) |
| `backface-visibility: hidden` | Task 6 |
| Lazy Leaflet map on flip | Task 7 (`buildMapFor`) |
| Geocoding cache extracted to root service | Task 2 |
| `SALON_GRADIENTS` moved into carousel | Task 5 |
| Click center → navigate `/salon/:slug` | Tasks 5, 6 (`onCardClick`) |
| Click side → become center | Tasks 5, 6 |
| Center auto-unflips on slide | Task 6 (`effect`) |
| Flip toggle on center only | Task 6 |
| `aria-pressed`, `aria-label`, `aria-current`, `aria-roledescription` | Tasks 5, 6 |
| Pro CTA section pointing to `/pricing` | Task 9 |
| Mini-map removed | Task 9 |
| Mini-cards removed | Task 9 |
| Recent posts preserved | Task 9 |
| i18n FR + EN | Tasks 3, 5 (nav arrows) |
| Hero asset placeholders | Task 1 |

**Out of scope (acceptable):**
- Touch swipe gestures on the carousel — added in a follow-up if smoke check shows mobile UX needs it. The arrow buttons + tap-to-center suffice for v1.
- 60fps measurement script — verified manually via DevTools Performance panel during smoke check.
- Production video asset — placeholder until creative is ready.
- Geolocation-based "near you" sorting — already in `discoveryService.searchSalons` flow; not changed here.

**Placeholders scan:** none — all steps contain concrete code or commands.

**Type consistency:**
- `SalonCard` from `discovery.model.ts` — used by Tasks 5, 6, 7, 8.
- `GeocodeResult { lat: number; lng: number }` from Task 2 — consumed by Task 7's `buildMapFor`.
- `DisplayCard` interface defined inside `salon-carousel.component.ts` — Task 5 introduces it, Task 6 keeps the same shape.
- `flippedSlugs` is `signal<ReadonlySet<string>>` — Tasks 6, 7 read/write via `new Set(...)` not direct mutation.

---

## Notes for the executing engineer

- **Asset URLs in Task 1**: if Pexels/Coverr links 404, pick equivalents. The plan's URLs are illustrative; the constraint is `<200KB JPG`, `<4MB MP4`.
- **Touch swipe**: the spec lists swipe as nice-to-have. Tasks 5-6 ship arrow buttons + tap-to-center which work on mobile already (one finger tap on a side card centers it). If smoke check reveals that's not enough, add a follow-up task for `pointerdown`/`pointerup` swipe detection.
- **Leaflet bundle size**: `import('leaflet')` adds ~140 KB to the carousel chunk. Acceptable since it's already a dependency on the home page.
- **Carousel + 1 salon edge case**: with a single salon, `displayCards` produces one card with `position: 'center'`. The next/prev buttons cycle to itself (no-op). Don't add UI for this — it's harmless.
- **The home spec test (Task 9 step 3)**: if no spec exists, that's fine. The carousel and hero have their own.
- **Don't forget commits between tasks**: the plan emphasizes frequent commits. Don't combine Task 5 with Task 6 — they're separate features.
