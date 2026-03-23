# Landing Page Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the mono-salon home page with a public Pretty Face landing page (hero, category cards, demo salons carousel, pro CTA) and a discover placeholder page.

**Architecture:** Frontend-only changes. Rewrite the Home component (remove all booking/store logic, replace with static sections). Add a DiscoverPage placeholder. Add `/discover` route. All data is hardcoded — no backend changes.

**Tech Stack:** Angular 20 (standalone, zoneless, signals), Angular Material, Tailwind CSS, Transloco i18n, SCSS

**Spec:** `docs/superpowers/specs/2026-03-23-landing-page-design.md`

---

## Chunk 1: i18n & Routing

### Task 1: Add i18n translation keys

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add French translations**

Add a `"home"` block and a `"discover"` block at the root level of `fr.json`:

```json
"home": {
  "hero": {
    "title": "Pretty Face",
    "subtitle": "Trouve ton prochain soin beauté",
    "search": "Rechercher un soin, un salon..."
  },
  "categories": {
    "title": "Catégories"
  },
  "salons": {
    "title": "Découvre les artistes près de toi"
  },
  "cta": {
    "question": "Tu es pro de la beauté ?",
    "action": "Crée ta vitrine gratuitement"
  }
},
"discover": {
  "placeholder": "Bientôt disponible",
  "message": "Découvrez les salons {{category}}",
  "searchMessage": "Résultats pour « {{query}} »",
  "defaultMessage": "Explorez tous les salons de beauté près de chez vous",
  "backHome": "Retour à l'accueil"
}
```

- [ ] **Step 2: Add English translations**

Add matching `"home"` and `"discover"` blocks in `en.json`:

```json
"home": {
  "hero": {
    "title": "Pretty Face",
    "subtitle": "Find your next beauty treatment",
    "search": "Search for a treatment, a salon..."
  },
  "categories": {
    "title": "Categories"
  },
  "salons": {
    "title": "Discover artists near you"
  },
  "cta": {
    "question": "Are you a beauty professional?",
    "action": "Create your storefront for free"
  }
},
"discover": {
  "placeholder": "Coming soon",
  "message": "Discover {{category}} salons",
  "searchMessage": "Results for '{{query}}'",
  "defaultMessage": "Explore all beauty salons near you",
  "backHome": "Back to home"
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat: add i18n keys for landing page and discover placeholder"
```

---

### Task 2: Add `/discover` route and create DiscoverPage placeholder

**Files:**
- Create: `frontend/src/app/pages/discover/discover-page.component.ts`
- Modify: `frontend/src/app/app.routes.ts`

- [ ] **Step 1: Create DiscoverPage component**

```typescript
import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { map } from 'rxjs';

@Component({
  selector: 'app-discover-page',
  standalone: true,
  imports: [TranslocoPipe, RouterLink],
  template: `
    <section class="flex flex-col items-center justify-center min-h-[60vh] px-6 text-center">
      <div class="mb-6">
        <span class="text-5xl">🔍</span>
      </div>
      <h1 class="text-2xl font-light tracking-wide text-neutral-800 mb-3">
        {{ 'discover.placeholder' | transloco }}
      </h1>
      <p class="text-neutral-500 font-light max-w-md mb-8">
        {{ message() }}
      </p>
      <a routerLink="/" class="text-sm text-rose-400 hover:text-rose-500 underline underline-offset-4 transition-colors duration-150">
        {{ 'discover.backHome' | transloco }}
      </a>
    </section>
  `,
})
export class DiscoverPageComponent {
  private route = inject(ActivatedRoute);

  private params = toSignal(
    this.route.queryParamMap.pipe(
      map((params) => ({
        category: params.get('category'),
        q: params.get('q'),
      }))
    ),
    { initialValue: { category: null, q: null } }
  );

  message = computed(() => {
    const { category, q } = this.params();
    if (category) return `Découvrez les salons ${category}`;
    if (q) return `Résultats pour « ${q} »`;
    return 'Explorez tous les salons de beauté près de chez vous';
  });
}
```

- [ ] **Step 2: Add route to app.routes.ts**

Add after the `{ path: 'video-games', ... }` line in the public routes section:

```typescript
{
  path: 'discover',
  loadComponent: () => import('./pages/discover/discover-page.component').then(m => m.DiscoverPageComponent),
},
```

- [ ] **Step 3: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/discover/discover-page.component.ts \
       frontend/src/app/app.routes.ts
git commit -m "feat: add discover placeholder page with route"
```

---

## Chunk 2: Home Component Rewrite

### Task 3: Rewrite Home component TypeScript

**Files:**
- Rewrite: `frontend/src/app/pages/home/home.ts`

- [ ] **Step 1: Replace the entire Home component**

Replace `frontend/src/app/pages/home/home.ts` with:

```typescript
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

interface CategoryCard {
  name: string;
  slug: string;
  emoji: string;
  color: string;
  count: number;
}

interface FeaturedSalon {
  name: string;
  slug: string;
  category: string;
  city: string;
  rating: number;
  gradient: string;
}

const CATEGORIES: CategoryCard[] = [
  { name: 'Soins visage', slug: 'soins-visage', emoji: '💆', color: '#f4e1d2', count: 12 },
  { name: 'Ongles', slug: 'ongles', emoji: '💅', color: '#f9d5d3', count: 8 },
  { name: 'Coiffure', slug: 'coiffure', emoji: '✂️', color: '#dce8d2', count: 15 },
  { name: 'Épilation', slug: 'epilation', emoji: '🧖', color: '#d5e5f0', count: 6 },
];

const FEATURED_SALONS: FeaturedSalon[] = [
  { name: 'Atelier Lumière', slug: 'atelier-lumiere', category: 'Soins visage', city: 'Paris 11', rating: 4.8, gradient: 'linear-gradient(135deg, #e8d5c4, #f0e0d0)' },
  { name: 'Rose & Thé', slug: 'rose-et-the', category: 'Ongles', city: 'Lyon 6', rating: 4.9, gradient: 'linear-gradient(135deg, #d5e0d2, #e0ead5)' },
  { name: 'Belle Époque', slug: 'belle-epoque', category: 'Coiffure', city: 'Bordeaux', rating: 4.7, gradient: 'linear-gradient(135deg, #d5d5e8, #e0e0f0)' },
  { name: 'Douceur de Soi', slug: 'douceur-de-soi', category: 'Épilation', city: 'Nantes', rating: 4.6, gradient: 'linear-gradient(135deg, #e0d5d8, #f0e5e8)' },
  { name: "Les Mains d'Or", slug: 'les-mains-dor', category: 'Ongles', city: 'Toulouse', rating: 4.8, gradient: 'linear-gradient(135deg, #f0e6cc, #f5ecd5)' },
];

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private router = inject(Router);

  readonly categories = CATEGORIES;
  readonly salons = FEATURED_SALONS;
  readonly searchQuery = signal('');

  onSearch(): void {
    const q = this.searchQuery().trim();
    if (q) {
      this.router.navigate(['/discover'], { queryParams: { q } });
    }
  }

  onCategoryClick(slug: string): void {
    this.router.navigate(['/discover'], { queryParams: { category: slug } });
  }

  onSalonClick(slug: string): void {
    this.router.navigate(['/salon', slug]);
  }

  onProCta(): void {
    this.router.navigate(['/register']);
  }
}
```

- [ ] **Step 2: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: Errors about template (expected — template not updated yet)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/home/home.ts
git commit -m "feat: rewrite Home component for Pretty Face landing page"
```

---

### Task 4: Rewrite Home template

**Files:**
- Rewrite: `frontend/src/app/pages/home/home.html`

- [ ] **Step 1: Replace the entire template**

Replace `frontend/src/app/pages/home/home.html` with:

```html
<!-- Hero Section -->
<section class="hero">
  <div class="hero-bg">
    <svg class="hero-blobs" viewBox="0 0 800 400" xmlns="http://www.w3.org/2000/svg">
      <circle cx="400" cy="200" r="160" fill="#f9d5d3" opacity="0.25"/>
      <circle cx="200" cy="300" r="120" fill="#dce8d2" opacity="0.25"/>
      <circle cx="650" cy="120" r="100" fill="#d5e5f0" opacity="0.25"/>
    </svg>
  </div>
  <div class="hero-content">
    <h1 class="hero-title">{{ 'home.hero.title' | transloco }}</h1>
    <p class="hero-subtitle">{{ 'home.hero.subtitle' | transloco }}</p>
    <form class="hero-search" (submit)="onSearch(); $event.preventDefault()">
      <span class="hero-search-icon">🔍</span>
      <input
        type="text"
        class="hero-search-input"
        [placeholder]="'home.hero.search' | transloco"
        [value]="searchQuery()"
        (input)="searchQuery.set($any($event.target).value)"
      />
    </form>
  </div>
</section>

<!-- Categories Section -->
<section class="landing-section">
  <h2 class="section-title">{{ 'home.categories.title' | transloco }}</h2>
  <div class="categories-grid">
    @for (cat of categories; track cat.slug) {
      <button
        type="button"
        class="category-card"
        [style.background-color]="cat.color"
        (click)="onCategoryClick(cat.slug)"
      >
        <span class="category-emoji">{{ cat.emoji }}</span>
        <div>
          <span class="category-name">{{ cat.name }}</span>
          <span class="category-count">{{ cat.count }} salons</span>
        </div>
      </button>
    }
  </div>
</section>

<!-- Featured Salons Section -->
<section class="landing-section">
  <h2 class="section-title">{{ 'home.salons.title' | transloco }}</h2>
  <div class="salons-carousel">
    @for (salon of salons; track salon.slug) {
      <button type="button" class="salon-card" (click)="onSalonClick(salon.slug)">
        <div class="salon-image" [style.background]="salon.gradient"></div>
        <div class="salon-info">
          <span class="salon-name">{{ salon.name }}</span>
          <span class="salon-category">{{ salon.category }}</span>
          <span class="salon-meta">⭐ {{ salon.rating }} · {{ salon.city }}</span>
        </div>
      </button>
    }
  </div>
</section>

<!-- Pro CTA Section -->
<section class="pro-cta">
  <p class="pro-cta-question">{{ 'home.cta.question' | transloco }}</p>
  <button type="button" class="pro-cta-action" (click)="onProCta()">
    {{ 'home.cta.action' | transloco }} →
  </button>
</section>
```

- [ ] **Step 2: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/home/home.html
git commit -m "feat: add landing page template with hero, categories, salons, CTA"
```

---

### Task 5: Rewrite Home SCSS

**Files:**
- Rewrite: `frontend/src/app/pages/home/home.scss`

- [ ] **Step 1: Replace the entire SCSS file**

Replace `frontend/src/app/pages/home/home.scss` with:

```scss
// ===== Hero =====
.hero {
  position: relative;
  padding: 80px 24px 60px;
  text-align: center;
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, #fff5f5 0%, #ffffff 100%);
  z-index: 0;
}

.hero-blobs {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.hero-content {
  position: relative;
  z-index: 1;
  max-width: 600px;
  margin: 0 auto;
}

.hero-title {
  font-size: 2rem;
  font-weight: 300;
  letter-spacing: 1px;
  color: #333;
  margin-bottom: 8px;
}

.hero-subtitle {
  font-size: 1rem;
  color: #666;
  font-weight: 300;
  margin-bottom: 28px;
}

.hero-search {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  background: white;
  border-radius: 28px;
  padding: 12px 24px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  width: 100%;
  max-width: 420px;
}

.hero-search-icon {
  font-size: 16px;
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

// ===== Sections =====
.landing-section {
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 24px;
}

.section-title {
  font-size: 0.875rem;
  font-weight: 500;
  color: #666;
  margin-bottom: 16px;
}

// ===== Categories =====
.categories-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;

  @media (min-width: 768px) {
    grid-template-columns: repeat(4, 1fr);
  }
}

.category-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 14px;
  border: none;
  cursor: pointer;
  transition: filter 150ms ease;
  text-align: left;

  &:hover {
    filter: brightness(0.97);
  }
}

.category-emoji {
  font-size: 24px;
  flex-shrink: 0;
}

.category-name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.category-count {
  display: block;
  font-size: 11px;
  color: #888;
}

// ===== Salons Carousel =====
.salons-carousel {
  display: flex;
  gap: 14px;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  -webkit-overflow-scrolling: touch;
  padding-bottom: 8px;

  // Hide scrollbar
  scrollbar-width: none;
  &::-webkit-scrollbar {
    display: none;
  }
}

.salon-card {
  flex: 0 0 160px;
  scroll-snap-align: start;
  background: white;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border: none;
  cursor: pointer;
  text-align: left;
  transition: box-shadow 150ms ease;

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  }
}

.salon-image {
  height: 80px;
  width: 100%;
}

.salon-info {
  padding: 12px;
}

.salon-name {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #333;
}

.salon-category {
  display: block;
  font-size: 11px;
  color: #999;
  margin-top: 2px;
}

.salon-meta {
  display: block;
  font-size: 11px;
  color: #bbb;
  margin-top: 4px;
}

// ===== Pro CTA =====
.pro-cta {
  max-width: 900px;
  margin: 20px auto 60px;
  padding: 24px;
  background: #fafafa;
  border-radius: 14px;
  text-align: center;

  @media (min-width: 768px) {
    margin: 20px auto 80px;
  }
}

.pro-cta-question {
  font-size: 14px;
  color: #555;
  margin-bottom: 6px;
}

.pro-cta-action {
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
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/pages/home/home.scss
git commit -m "feat: add landing page styles (hero, categories, salons, CTA)"
```

---

## Chunk 3: Tests

### Task 6: Write Home component tests

**Files:**
- Rewrite: `frontend/src/app/pages/home/home.spec.ts` (if exists, otherwise create)

- [ ] **Step 1: Write tests**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTransloco } from '@jsverse/transloco';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Home } from './home';

describe('Home (Landing Page)', () => {
  let component: Home;
  let fixture: ComponentFixture<Home>;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        Home,
        TranslocoTestingModule.forRoot({
          langs: {
            fr: {
              home: {
                hero: { title: 'Pretty Face', subtitle: 'Trouve ton prochain soin beauté', search: 'Rechercher...' },
                categories: { title: 'Catégories' },
                salons: { title: 'Découvre les artistes près de toi' },
                cta: { question: 'Tu es pro ?', action: 'Crée ta vitrine' },
              },
            },
          },
          defaultLang: 'fr',
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([
          { path: 'discover', children: [] },
          { path: 'salon/:slug', children: [] },
          { path: 'register', children: [] },
        ]),
        provideNoopAnimations(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Home);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render hero section with title', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.hero-title')?.textContent).toContain('Pretty Face');
  });

  it('should render 4 category cards', () => {
    const el = fixture.nativeElement as HTMLElement;
    const cards = el.querySelectorAll('.category-card');
    expect(cards.length).toBe(4);
  });

  it('should navigate to discover on category click', () => {
    spyOn(router, 'navigate');
    component.onCategoryClick('soins-visage');
    expect(router.navigate).toHaveBeenCalledWith(['/discover'], {
      queryParams: { category: 'soins-visage' },
    });
  });

  it('should render 5 salon cards', () => {
    const el = fixture.nativeElement as HTMLElement;
    const cards = el.querySelectorAll('.salon-card');
    expect(cards.length).toBe(5);
  });

  it('should navigate to salon on salon click', () => {
    spyOn(router, 'navigate');
    component.onSalonClick('atelier-lumiere');
    expect(router.navigate).toHaveBeenCalledWith(['/salon', 'atelier-lumiere']);
  });

  it('should render pro CTA', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.pro-cta')).toBeTruthy();
  });

  it('should navigate to register on pro CTA', () => {
    spyOn(router, 'navigate');
    component.onProCta();
    expect(router.navigate).toHaveBeenCalledWith(['/register']);
  });

  it('should navigate to discover on search', () => {
    spyOn(router, 'navigate');
    component.searchQuery.set('visage');
    component.onSearch();
    expect(router.navigate).toHaveBeenCalledWith(['/discover'], {
      queryParams: { q: 'visage' },
    });
  });

  it('should not navigate on empty search', () => {
    spyOn(router, 'navigate');
    component.searchQuery.set('  ');
    component.onSearch();
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/home/home.spec.ts
git commit -m "test: add Home landing page tests"
```

---

### Task 7: Write DiscoverPage tests

**Files:**
- Create: `frontend/src/app/pages/discover/discover-page.component.spec.ts`

- [ ] **Step 1: Write tests**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { DiscoverPageComponent } from './discover-page.component';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

function createRoute(params: Record<string, string> = {}) {
  return {
    queryParamMap: of(convertToParamMap(params)),
  };
}

describe('DiscoverPageComponent', () => {
  function setup(params: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      imports: [
        DiscoverPageComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            fr: {
              discover: {
                placeholder: 'Bientôt disponible',
                backHome: 'Retour',
              },
            },
          },
          defaultLang: 'fr',
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: createRoute(params) },
      ],
    });

    const fixture = TestBed.createComponent(DiscoverPageComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('should create', () => {
    const fixture = setup();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show placeholder title', () => {
    const fixture = setup();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Bientôt disponible');
  });

  it('should show category message when category param is present', () => {
    const fixture = setup({ category: 'ongles' });
    expect(fixture.componentInstance.message()).toContain('ongles');
  });

  it('should show search message when q param is present', () => {
    const fixture = setup({ q: 'visage' });
    expect(fixture.componentInstance.message()).toContain('visage');
  });

  it('should show default message when no params', () => {
    const fixture = setup();
    expect(fixture.componentInstance.message()).toContain('Explorez');
  });
});
```

- [ ] **Step 2: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/discover/discover-page.component.spec.ts
git commit -m "test: add DiscoverPage placeholder tests"
```

---

### Task 8: Final verification

- [ ] **Step 1: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 2: Verify no missing translation keys**

Check that all `home.*` and `discover.*` keys exist in both `fr.json` and `en.json`.

- [ ] **Step 3: Verify routing is correct**

Check that `app.routes.ts` has `/discover` in the public section and that `/` still points to `Home`.
