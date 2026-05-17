# Legal Pages + Cookie Banner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship 5 legal pages (CGU, CGV pros, Privacy, Legal Notice, Cookies) + a non-blocking cookie info banner on LuxPretty, fully wired in routing/footer/consent flows, FR+EN, ready for a LU lawyer to review before real billing.

**Architecture:** Lazy-loaded standalone Angular page components under `pages/legal/`, all sharing a single `LegalLayoutComponent`. Long-form content lives in `i18n/{lang}.json` under `legal.*` keys (HTML-light via `[innerHTML]`, sanitized by Angular). A `CookieBannerComponent` mounted in `app.html` reads/writes `localStorage` via `CookieBannerService` with SSR guards. Footer "legal" text replaced by 5 `routerLink`s.

**Tech Stack:** Angular 20 standalone + zoneless + SSR, Transloco i18n, Angular Material, Tailwind, Jasmine/Karma for unit tests, Playwright for E2E.

**Reference spec:** `docs/superpowers/specs/2026-05-17-cgu-rgpd-cookies-design.md`

---

## File Structure

**Create:**
- `frontend/src/app/pages/legal/legal-layout/legal-layout.component.ts`
- `frontend/src/app/pages/legal/legal-layout/legal-layout.component.html`
- `frontend/src/app/pages/legal/legal-layout/legal-layout.component.scss`
- `frontend/src/app/pages/legal/legal-layout/legal-layout.component.spec.ts`
- `frontend/src/app/pages/legal/cgu/cgu-page.component.ts`
- `frontend/src/app/pages/legal/cgu/cgu-page.component.html`
- `frontend/src/app/pages/legal/cgu/cgu-page.component.spec.ts`
- `frontend/src/app/pages/legal/cgv/cgv-page.component.ts`
- `frontend/src/app/pages/legal/cgv/cgv-page.component.html`
- `frontend/src/app/pages/legal/cgv/cgv-page.component.spec.ts`
- `frontend/src/app/pages/legal/privacy/privacy-page.component.ts`
- `frontend/src/app/pages/legal/privacy/privacy-page.component.html`
- `frontend/src/app/pages/legal/privacy/privacy-page.component.spec.ts`
- `frontend/src/app/pages/legal/legal-notice/legal-notice-page.component.ts`
- `frontend/src/app/pages/legal/legal-notice/legal-notice-page.component.html`
- `frontend/src/app/pages/legal/legal-notice/legal-notice-page.component.spec.ts`
- `frontend/src/app/pages/legal/cookies/cookies-page.component.ts`
- `frontend/src/app/pages/legal/cookies/cookies-page.component.html`
- `frontend/src/app/pages/legal/cookies/cookies-page.component.spec.ts`
- `frontend/src/app/shared/components/cookie-banner/cookie-banner.component.ts`
- `frontend/src/app/shared/components/cookie-banner/cookie-banner.component.html`
- `frontend/src/app/shared/components/cookie-banner/cookie-banner.component.scss`
- `frontend/src/app/shared/components/cookie-banner/cookie-banner.component.spec.ts`
- `frontend/src/app/shared/components/cookie-banner/cookie-banner.service.ts`
- `frontend/src/app/shared/components/cookie-banner/cookie-banner.service.spec.ts`
- `frontend/e2e/legal/legal-pages.spec.ts`
- `frontend/e2e/legal/cookie-banner.spec.ts`

**Modify:**
- `frontend/src/app/app.routes.ts` — add 5 lazy routes
- `frontend/src/app/app.ts` — import `CookieBannerComponent`
- `frontend/src/app/app.html` — mount `<app-cookie-banner>` before footer
- `frontend/src/app/shared/layout/footer/footer.html` — replace legal line with 5 routerLinks (visitor branch only)
- `frontend/src/app/shared/layout/footer/footer.scss` — add `.lp-footer__legal-nav` styling
- `frontend/src/app/shared/modals/pro-signup-modal/pro-signup-modal.component.html` — wrap consent label in `[innerHTML]`
- `frontend/public/i18n/fr.json` — add `legal.*`, `cookieBanner.*`, footer legal links keys, update `proSignup.modal.fields.consent`
- `frontend/public/i18n/en.json` — same, English version

---

## Implementation Strategy

5 PRs, in this order:

- **PR1 — Technical foundation** (Tasks 1–6) — layout, banner, service, routes, footer, modal. Pages render with placeholder i18n. Smallest safe shippable unit.
- **PR2 — FR content** (Task 7) — write the 5 French documents.
- **PR3 — EN content** (Task 8) — write the 5 English documents.
- **PR4 — E2E + polish** (Tasks 9–11) — Playwright E2E, a11y check, SEO meta tags.

Each PR ends with `npm run build`, `npm test`, and a manual smoke check.

---

## Task 1: Cookie banner service

**Files:**
- Create: `frontend/src/app/shared/components/cookie-banner/cookie-banner.service.ts`
- Test: `frontend/src/app/shared/components/cookie-banner/cookie-banner.service.spec.ts`

- [ ] **Step 1: Write failing tests for the service**

```typescript
// cookie-banner.service.spec.ts
import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID } from '@angular/core';
import { CookieBannerService } from './cookie-banner.service';

describe('CookieBannerService', () => {
  const STORAGE_KEY = 'lp_cookie_banner_v1';

  beforeEach(() => {
    localStorage.removeItem(STORAGE_KEY);
  });

  afterEach(() => {
    localStorage.removeItem(STORAGE_KEY);
  });

  function createService(platformId: 'browser' | 'server' = 'browser') {
    TestBed.configureTestingModule({
      providers: [
        CookieBannerService,
        { provide: PLATFORM_ID, useValue: platformId },
      ],
    });
    return TestBed.inject(CookieBannerService);
  }

  it('starts not dismissed when storage is empty in browser', () => {
    const service = createService('browser');
    expect(service.dismissed()).toBe(false);
  });

  it('starts dismissed when localStorage already has the flag', () => {
    localStorage.setItem(STORAGE_KEY, 'dismissed');
    const service = createService('browser');
    expect(service.dismissed()).toBe(true);
  });

  it('starts dismissed on the server (SSR-safe, avoids hydration flash)', () => {
    const service = createService('server');
    expect(service.dismissed()).toBe(true);
  });

  it('dismiss() persists to localStorage and updates the signal', () => {
    const service = createService('browser');
    service.dismiss();
    expect(service.dismissed()).toBe(true);
    expect(localStorage.getItem(STORAGE_KEY)).toBe('dismissed');
  });

  it('dismiss() is a no-op on the server', () => {
    const service = createService('server');
    service.dismiss();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npm test -- --include='**/cookie-banner.service.spec.ts' --watch=false`
Expected: FAIL with "Cannot find module './cookie-banner.service'".

- [ ] **Step 3: Implement the service**

```typescript
// cookie-banner.service.ts
import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

const STORAGE_KEY = 'lp_cookie_banner_v1';

@Injectable({ providedIn: 'root' })
export class CookieBannerService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  readonly dismissed = signal<boolean>(this.loadInitial());

  private loadInitial(): boolean {
    if (!this.isBrowser) return true;
    try {
      return localStorage.getItem(STORAGE_KEY) === 'dismissed';
    } catch {
      return true;
    }
  }

  dismiss(): void {
    if (!this.isBrowser) return;
    try {
      localStorage.setItem(STORAGE_KEY, 'dismissed');
    } catch {
      /* localStorage unavailable (private mode, quota) — silently ignore */
    }
    this.dismissed.set(true);
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd frontend && npm test -- --include='**/cookie-banner.service.spec.ts' --watch=false`
Expected: PASS (5 specs).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/components/cookie-banner/cookie-banner.service.ts \
        frontend/src/app/shared/components/cookie-banner/cookie-banner.service.spec.ts
git commit -m "feat(legal): cookie banner service with SSR guard and signal state"
```

---

## Task 2: Cookie banner component

**Files:**
- Create: `frontend/src/app/shared/components/cookie-banner/cookie-banner.component.ts`
- Create: `frontend/src/app/shared/components/cookie-banner/cookie-banner.component.html`
- Create: `frontend/src/app/shared/components/cookie-banner/cookie-banner.component.scss`
- Test: `frontend/src/app/shared/components/cookie-banner/cookie-banner.component.spec.ts`

- [ ] **Step 1: Add i18n keys (FR + EN, minimal)**

Add to `frontend/public/i18n/fr.json` at the root level (insert before the closing `}`):

```json
"cookieBanner": {
  "message": "Ce site utilise uniquement des cookies nécessaires à son fonctionnement.",
  "learnMore": "En savoir plus",
  "dismiss": "J'ai compris",
  "ariaLabel": "Information sur l'utilisation des cookies"
}
```

Same block in `frontend/public/i18n/en.json`:

```json
"cookieBanner": {
  "message": "This site only uses cookies necessary for its operation.",
  "learnMore": "Learn more",
  "dismiss": "Got it",
  "ariaLabel": "Cookie usage notice"
}
```

- [ ] **Step 2: Write failing component test**

```typescript
// cookie-banner.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { CookieBannerComponent } from './cookie-banner.component';
import { CookieBannerService } from './cookie-banner.service';

describe('CookieBannerComponent', () => {
  let fixture: ComponentFixture<CookieBannerComponent>;
  let service: CookieBannerService;

  beforeEach(async () => {
    localStorage.removeItem('lp_cookie_banner_v1');
    await TestBed.configureTestingModule({
      imports: [
        CookieBannerComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: { cookieBanner: { message: 'msg', learnMore: 'more', dismiss: 'ok', ariaLabel: 'aria' } } },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
      providers: [provideRouter([]), provideNoopAnimations(), provideHttpClient()],
    }).compileComponents();

    fixture = TestBed.createComponent(CookieBannerComponent);
    service = TestBed.inject(CookieBannerService);
    fixture.detectChanges();
  });

  it('renders the banner when not dismissed', () => {
    expect(service.dismissed()).toBe(false);
    const el = fixture.nativeElement.querySelector('.cookie-banner');
    expect(el).toBeTruthy();
  });

  it('hides the banner after dismiss() is called', () => {
    service.dismiss();
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.cookie-banner');
    expect(el).toBeNull();
  });

  it('the dismiss button calls service.dismiss()', () => {
    const spy = spyOn(service, 'dismiss').and.callThrough();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.cookie-banner__btn');
    button.click();
    expect(spy).toHaveBeenCalled();
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd frontend && npm test -- --include='**/cookie-banner.component.spec.ts' --watch=false`
Expected: FAIL with "Cannot find module".

- [ ] **Step 4: Implement the component**

`cookie-banner.component.ts`:

```typescript
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { CookieBannerService } from './cookie-banner.service';

@Component({
  selector: 'app-cookie-banner',
  standalone: true,
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './cookie-banner.component.html',
  styleUrl: './cookie-banner.component.scss',
})
export class CookieBannerComponent {
  readonly service = inject(CookieBannerService);
}
```

`cookie-banner.component.html`:

```html
@if (!service.dismissed()) {
  <div
    class="cookie-banner"
    role="region"
    [attr.aria-label]="'cookieBanner.ariaLabel' | transloco"
  >
    <p class="cookie-banner__message">
      <span aria-hidden="true" class="cookie-banner__icon">🍪</span>
      {{ 'cookieBanner.message' | transloco }}
      <a routerLink="/cookies" class="cookie-banner__link">
        {{ 'cookieBanner.learnMore' | transloco }}
      </a>
    </p>
    <button
      type="button"
      class="cookie-banner__btn"
      (click)="service.dismiss()"
    >
      {{ 'cookieBanner.dismiss' | transloco }}
    </button>
  </div>
}
```

`cookie-banner.component.scss`:

```scss
.cookie-banner {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 900;
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1.25rem;
  background: rgba(17, 24, 39, 0.96);
  color: #f9fafb;
  font-size: 0.875rem;
  line-height: 1.4;
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.15);
  animation: cookie-banner-in 220ms ease-out;
}

.cookie-banner__message {
  flex: 1;
  margin: 0;
}

.cookie-banner__icon {
  margin-right: 0.35rem;
}

.cookie-banner__link {
  color: #fbcfe8;
  text-decoration: underline;
  margin-left: 0.4rem;

  &:hover {
    color: #fff;
  }
}

.cookie-banner__btn {
  background: #fff;
  color: #111827;
  border: 0;
  border-radius: 6px;
  padding: 0.4rem 1rem;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 150ms ease;

  &:hover {
    background: #f3f4f6;
  }

  &:focus-visible {
    outline: 2px solid #ec4899;
    outline-offset: 2px;
  }
}

@keyframes cookie-banner-in {
  from { transform: translateY(100%); opacity: 0; }
  to   { transform: translateY(0);    opacity: 1; }
}

@media (max-width: 640px) {
  .cookie-banner {
    flex-direction: column;
    align-items: stretch;
    text-align: center;
    padding: 0.85rem 1rem;
  }
  .cookie-banner__btn {
    align-self: center;
  }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd frontend && npm test -- --include='**/cookie-banner.component.spec.ts' --watch=false`
Expected: PASS (3 specs).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/shared/components/cookie-banner/ \
        frontend/public/i18n/fr.json \
        frontend/public/i18n/en.json
git commit -m "feat(legal): cookie banner component with i18n + a11y"
```

---

## Task 3: Legal layout component

**Files:**
- Create: `frontend/src/app/pages/legal/legal-layout/legal-layout.component.ts`
- Create: `frontend/src/app/pages/legal/legal-layout/legal-layout.component.html`
- Create: `frontend/src/app/pages/legal/legal-layout/legal-layout.component.scss`
- Test: `frontend/src/app/pages/legal/legal-layout/legal-layout.component.spec.ts`

- [ ] **Step 1: Add the `legal.common.lastUpdated` key in i18n**

Add to `frontend/public/i18n/fr.json` (root level, alphabetical between existing keys):

```json
"legal": {
  "common": {
    "lastUpdated": "Dernière mise à jour :"
  }
}
```

And to `frontend/public/i18n/en.json`:

```json
"legal": {
  "common": {
    "lastUpdated": "Last updated:"
  }
}
```

(These objects will grow in subsequent tasks — keep them in alphabetical position so future additions are predictable.)

- [ ] **Step 2: Write failing layout test**

```typescript
// legal-layout.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { LegalLayoutComponent } from './legal-layout.component';

@Component({
  standalone: true,
  imports: [LegalLayoutComponent],
  template: `
    <app-legal-layout titleKey="legal.cgu.title" [updatedAt]="'2026-05-17'">
      <section data-testid="slot">slot content</section>
    </app-legal-layout>
  `,
})
class HostComponent {}

describe('LegalLayoutComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        HostComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              legal: {
                common: { lastUpdated: 'Last updated:' },
                cgu: { title: 'Terms' },
              },
            },
          },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
      providers: [provideRouter([]), provideHttpClient()],
    }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders the translated title', () => {
    const h1 = fixture.nativeElement.querySelector('h1');
    expect(h1.textContent.trim()).toBe('Terms');
  });

  it('renders the last-updated date', () => {
    const updated = fixture.nativeElement.querySelector('.legal-page__updated');
    expect(updated.textContent).toContain('Last updated:');
    expect(updated.textContent).toContain('2026');
  });

  it('projects ng-content into the article', () => {
    const slot = fixture.nativeElement.querySelector('[data-testid="slot"]');
    expect(slot).toBeTruthy();
    expect(slot.textContent).toContain('slot content');
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd frontend && npm test -- --include='**/legal-layout.component.spec.ts' --watch=false`
Expected: FAIL with "Cannot find module".

- [ ] **Step 4: Implement the layout**

`legal-layout.component.ts`:

```typescript
import { Component, Input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-legal-layout',
  standalone: true,
  imports: [DatePipe, TranslocoPipe],
  templateUrl: './legal-layout.component.html',
  styleUrl: './legal-layout.component.scss',
})
export class LegalLayoutComponent {
  @Input({ required: true }) titleKey!: string;
  @Input({ required: true }) updatedAt!: string;
}
```

`legal-layout.component.html`:

```html
<main class="legal-page">
  <article class="legal-page__article">
    <header class="legal-page__header">
      <h1>{{ titleKey | transloco }}</h1>
      <p class="legal-page__updated">
        {{ 'legal.common.lastUpdated' | transloco }} {{ updatedAt | date:'longDate' }}
      </p>
    </header>
    <div class="legal-page__content">
      <ng-content></ng-content>
    </div>
  </article>
</main>
```

`legal-layout.component.scss`:

```scss
.legal-page {
  padding: 2rem 1rem 4rem;
}

.legal-page__article {
  max-width: 720px;
  margin: 0 auto;
  color: var(--mat-sys-on-surface, #1f2937);
}

.legal-page__header {
  margin-bottom: 2rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--mat-sys-outline-variant, #e5e7eb);

  h1 {
    font-size: 2rem;
    font-weight: 600;
    margin: 0 0 0.5rem;
  }
}

.legal-page__updated {
  margin: 0;
  font-size: 0.875rem;
  color: var(--mat-sys-on-surface-variant, #6b7280);
}

.legal-page__content {
  line-height: 1.7;

  ::ng-deep {
    h2 {
      font-size: 1.35rem;
      font-weight: 600;
      margin: 2.25rem 0 0.75rem;
    }

    h3 {
      font-size: 1.1rem;
      font-weight: 600;
      margin: 1.5rem 0 0.5rem;
    }

    p {
      margin: 0 0 1rem;
    }

    ul, ol {
      margin: 0 0 1rem;
      padding-left: 1.5rem;
    }

    li {
      margin-bottom: 0.35rem;
    }

    a {
      color: var(--mat-sys-primary, #be185d);
      text-decoration: underline;
    }

    table {
      width: 100%;
      border-collapse: collapse;
      margin: 1rem 0;
      font-size: 0.9rem;
    }

    th, td {
      border: 1px solid var(--mat-sys-outline-variant, #e5e7eb);
      padding: 0.5rem 0.75rem;
      text-align: left;
      vertical-align: top;
    }

    th {
      background: var(--mat-sys-surface-container-low, #f9fafb);
      font-weight: 600;
    }
  }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd frontend && npm test -- --include='**/legal-layout.component.spec.ts' --watch=false`
Expected: PASS (3 specs).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/legal/legal-layout/ \
        frontend/public/i18n/fr.json \
        frontend/public/i18n/en.json
git commit -m "feat(legal): legal-layout component for static legal pages"
```

---

## Task 4: Five legal page components (skeletons with placeholder i18n)

This task creates all 5 page components with the same pattern. The i18n keys point to single-paragraph placeholders that will be filled in PR2 (FR) and PR3 (EN). Each page declares its `sections` array statically — that array drives the template loop.

**Files:**
- Create: `pages/legal/{cgu,cgv,privacy,legal-notice,cookies}/*.component.{ts,html,spec.ts}`

- [ ] **Step 1: Add the page-level i18n stubs (FR)**

Extend the `legal` block in `frontend/public/i18n/fr.json` to include each page's `title` and a single placeholder section. Final shape after this step:

```json
"legal": {
  "common": {
    "lastUpdated": "Dernière mise à jour :"
  },
  "cgu": {
    "title": "Conditions Générales d'Utilisation",
    "sections": {
      "placeholder": {
        "title": "Section à rédiger",
        "body": "<p>Contenu à rédiger en PR2.</p>"
      }
    }
  },
  "cgv": {
    "title": "Conditions Générales de Vente",
    "sections": {
      "placeholder": {
        "title": "Section à rédiger",
        "body": "<p>Contenu à rédiger en PR2.</p>"
      }
    }
  },
  "privacy": {
    "title": "Politique de confidentialité",
    "sections": {
      "placeholder": {
        "title": "Section à rédiger",
        "body": "<p>Contenu à rédiger en PR2.</p>"
      }
    }
  },
  "notice": {
    "title": "Mentions légales",
    "preLaunchBanner": "⚠️ LuxPretty est actuellement un projet en pré-lancement opéré à titre personnel. La société éditrice est en cours d'immatriculation.",
    "sections": {
      "placeholder": {
        "title": "Section à rédiger",
        "body": "<p>Contenu à rédiger en PR2.</p>"
      }
    }
  },
  "cookies": {
    "title": "Politique cookies",
    "sections": {
      "placeholder": {
        "title": "Section à rédiger",
        "body": "<p>Contenu à rédiger en PR2.</p>"
      }
    }
  }
}
```

- [ ] **Step 2: Add the page-level i18n stubs (EN)**

Same shape in `frontend/public/i18n/en.json` with English titles:

```json
"legal": {
  "common": { "lastUpdated": "Last updated:" },
  "cgu": {
    "title": "Terms of Use",
    "sections": { "placeholder": { "title": "Section to write", "body": "<p>Content to be written in PR3.</p>" } }
  },
  "cgv": {
    "title": "Terms of Sale (Pros)",
    "sections": { "placeholder": { "title": "Section to write", "body": "<p>Content to be written in PR3.</p>" } }
  },
  "privacy": {
    "title": "Privacy Policy",
    "sections": { "placeholder": { "title": "Section to write", "body": "<p>Content to be written in PR3.</p>" } }
  },
  "notice": {
    "title": "Legal Notice",
    "preLaunchBanner": "⚠️ LuxPretty is currently a pre-launch project run as a personal venture. The publishing entity is being incorporated.",
    "sections": { "placeholder": { "title": "Section to write", "body": "<p>Content to be written in PR3.</p>" } }
  },
  "cookies": {
    "title": "Cookies Policy",
    "sections": { "placeholder": { "title": "Section to write", "body": "<p>Content to be written in PR3.</p>" } }
  }
}
```

- [ ] **Step 3: Write a smoke test for the CGU page (used as the template for all 5)**

`cgu-page.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { CguPageComponent } from './cgu-page.component';

describe('CguPageComponent', () => {
  let fixture: ComponentFixture<CguPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        CguPageComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              legal: {
                common: { lastUpdated: 'Last updated:' },
                cgu: {
                  title: 'Terms of Use',
                  sections: { placeholder: { title: 'Section', body: '<p>body</p>' } },
                },
              },
            },
          },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
      providers: [provideRouter([]), provideHttpClient()],
    }).compileComponents();

    fixture = TestBed.createComponent(CguPageComponent);
    fixture.detectChanges();
  });

  it('renders the page title', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Terms of Use');
  });

  it('renders each section heading and HTML body', () => {
    const sections = fixture.nativeElement.querySelectorAll('.legal-section');
    expect(sections.length).toBeGreaterThan(0);
  });
});
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `cd frontend && npm test -- --include='**/cgu-page.component.spec.ts' --watch=false`
Expected: FAIL with "Cannot find module".

- [ ] **Step 5: Implement `CguPageComponent`**

`cgu-page.component.ts`:

```typescript
import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LegalLayoutComponent } from '../legal-layout/legal-layout.component';

@Component({
  selector: 'app-cgu-page',
  standalone: true,
  imports: [LegalLayoutComponent, TranslocoPipe],
  templateUrl: './cgu-page.component.html',
})
export class CguPageComponent {
  readonly updatedAt = '2026-05-17';
  readonly sections: ReadonlyArray<string> = ['placeholder'];
}
```

`cgu-page.component.html`:

```html
<app-legal-layout titleKey="legal.cgu.title" [updatedAt]="updatedAt">
  @for (section of sections; track section) {
    <section class="legal-section">
      <h2>{{ 'legal.cgu.sections.' + section + '.title' | transloco }}</h2>
      <div [innerHTML]="'legal.cgu.sections.' + section + '.body' | transloco"></div>
    </section>
  }
</app-legal-layout>
```

- [ ] **Step 6: Run CGU test to confirm it passes**

Run: `cd frontend && npm test -- --include='**/cgu-page.component.spec.ts' --watch=false`
Expected: PASS (2 specs).

- [ ] **Step 7: Repeat steps 3–6 for CGV (`cgv-page.component.{ts,html,spec.ts}`)**

`cgv-page.component.ts`:

```typescript
import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LegalLayoutComponent } from '../legal-layout/legal-layout.component';

@Component({
  selector: 'app-cgv-page',
  standalone: true,
  imports: [LegalLayoutComponent, TranslocoPipe],
  templateUrl: './cgv-page.component.html',
})
export class CgvPageComponent {
  readonly updatedAt = '2026-05-17';
  readonly sections: ReadonlyArray<string> = ['placeholder'];
}
```

`cgv-page.component.html`:

```html
<app-legal-layout titleKey="legal.cgv.title" [updatedAt]="updatedAt">
  @for (section of sections; track section) {
    <section class="legal-section">
      <h2>{{ 'legal.cgv.sections.' + section + '.title' | transloco }}</h2>
      <div [innerHTML]="'legal.cgv.sections.' + section + '.body' | transloco"></div>
    </section>
  }
</app-legal-layout>
```

`cgv-page.component.spec.ts` (copy of CGU spec with `cgu` → `cgv` throughout, and title "Terms of Sale (Pros)" in the testing-module langs).

Run: `cd frontend && npm test -- --include='**/cgv-page.component.spec.ts' --watch=false` → PASS.

- [ ] **Step 8: Implement `PrivacyPageComponent`**

`privacy-page.component.ts`:

```typescript
import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LegalLayoutComponent } from '../legal-layout/legal-layout.component';

@Component({
  selector: 'app-privacy-page',
  standalone: true,
  imports: [LegalLayoutComponent, TranslocoPipe],
  templateUrl: './privacy-page.component.html',
})
export class PrivacyPageComponent {
  readonly updatedAt = '2026-05-17';
  readonly sections: ReadonlyArray<string> = ['placeholder'];
}
```

`privacy-page.component.html`:

```html
<app-legal-layout titleKey="legal.privacy.title" [updatedAt]="updatedAt">
  @for (section of sections; track section) {
    <section class="legal-section">
      <h2>{{ 'legal.privacy.sections.' + section + '.title' | transloco }}</h2>
      <div [innerHTML]="'legal.privacy.sections.' + section + '.body' | transloco"></div>
    </section>
  }
</app-legal-layout>
```

Spec mirrors the CGU spec with `cgu` → `privacy` and title "Privacy Policy".

- [ ] **Step 9: Implement `LegalNoticePageComponent`** (slightly different — has the pre-launch banner)

`legal-notice-page.component.ts`:

```typescript
import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LegalLayoutComponent } from '../legal-layout/legal-layout.component';

@Component({
  selector: 'app-legal-notice-page',
  standalone: true,
  imports: [LegalLayoutComponent, TranslocoPipe],
  templateUrl: './legal-notice-page.component.html',
})
export class LegalNoticePageComponent {
  readonly updatedAt = '2026-05-17';
  readonly sections: ReadonlyArray<string> = ['placeholder'];
}
```

`legal-notice-page.component.html`:

```html
<app-legal-layout titleKey="legal.notice.title" [updatedAt]="updatedAt">
  <div class="legal-prelaunch-banner" role="status">
    {{ 'legal.notice.preLaunchBanner' | transloco }}
  </div>
  @for (section of sections; track section) {
    <section class="legal-section">
      <h2>{{ 'legal.notice.sections.' + section + '.title' | transloco }}</h2>
      <div [innerHTML]="'legal.notice.sections.' + section + '.body' | transloco"></div>
    </section>
  }
</app-legal-layout>
```

Add to `frontend/src/app/pages/legal/legal-notice/legal-notice-page.component.scss`:

```scss
.legal-prelaunch-banner {
  background: #fef3c7;
  color: #78350f;
  border: 1px solid #fde68a;
  padding: 0.85rem 1rem;
  border-radius: 8px;
  margin-bottom: 1.5rem;
  font-size: 0.9rem;
  line-height: 1.4;
}
```

(Update `legal-notice-page.component.ts` to declare `styleUrl: './legal-notice-page.component.scss'`.)

Spec mirrors CGU spec with `cgu` → `notice`, title "Legal Notice"; add a second `it()` checking the banner is rendered.

- [ ] **Step 10: Implement `CookiesPageComponent`**

Same pattern as CGU, namespace `cookies`.

- [ ] **Step 11: Run all legal page tests**

Run: `cd frontend && npm test -- --include='**/pages/legal/**/*.spec.ts' --watch=false`
Expected: PASS (all 5 page specs + layout spec).

- [ ] **Step 12: Commit**

```bash
git add frontend/src/app/pages/legal/ \
        frontend/public/i18n/fr.json \
        frontend/public/i18n/en.json
git commit -m "feat(legal): 5 legal page skeletons (placeholder content)"
```

---

## Task 5: Routes + mount in app shell

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/app.ts`
- Modify: `frontend/src/app/app.html`

- [ ] **Step 1: Add the 5 lazy routes**

In `frontend/src/app/app.routes.ts`, after the existing public routes (after the `oauth2/redirect` route), add:

```typescript
  {
    path: 'cgu',
    loadComponent: () =>
      import('./pages/legal/cgu/cgu-page.component').then((m) => m.CguPageComponent),
  },
  {
    path: 'cgv',
    loadComponent: () =>
      import('./pages/legal/cgv/cgv-page.component').then((m) => m.CgvPageComponent),
  },
  {
    path: 'confidentialite',
    loadComponent: () =>
      import('./pages/legal/privacy/privacy-page.component').then((m) => m.PrivacyPageComponent),
  },
  {
    path: 'mentions-legales',
    loadComponent: () =>
      import('./pages/legal/legal-notice/legal-notice-page.component').then(
        (m) => m.LegalNoticePageComponent,
      ),
  },
  {
    path: 'cookies',
    loadComponent: () =>
      import('./pages/legal/cookies/cookies-page.component').then((m) => m.CookiesPageComponent),
  },
```

- [ ] **Step 2: Mount the cookie banner in the app shell**

In `frontend/src/app/app.ts`, add `CookieBannerComponent` to imports:

```typescript
import { CookieBannerComponent } from './shared/components/cookie-banner/cookie-banner.component';
// ...
@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    Header,
    Footer,
    BottomNavComponent,
    NotificationToastComponent,
    CookieBannerComponent,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
```

In `frontend/src/app/app.html`, add the banner element just before `<app-footer>`:

```html
<app-header></app-header>
<app-notification-toast></app-notification-toast>

<main class="min-h-[70vh] container mx-auto px-4 py-8 main-content">
  <router-outlet></router-outlet>
</main>

<app-cookie-banner></app-cookie-banner>
<app-footer class="hide-on-mobile"></app-footer>
<app-bottom-nav></app-bottom-nav>
```

- [ ] **Step 3: Smoke-test the build**

Run: `cd frontend && npm run build`
Expected: success, no errors.

- [ ] **Step 4: Manual smoke test in dev**

Run: `cd frontend && npm start`
Open:
- `http://localhost:4200/cgu` → CGU placeholder page renders
- `http://localhost:4200/cgv` → CGV placeholder
- `http://localhost:4200/confidentialite` → Privacy placeholder
- `http://localhost:4200/mentions-legales` → Legal notice + pre-launch banner
- `http://localhost:4200/cookies` → Cookies placeholder
- Cookie banner visible at bottom on first visit; clicking "J'ai compris" hides it; refresh keeps it hidden.

Stop the dev server (Ctrl+C).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/app.routes.ts frontend/src/app/app.ts frontend/src/app/app.html
git commit -m "feat(legal): wire 5 legal routes and mount cookie banner"
```

---

## Task 6: Footer legal links + pro signup consent

**Files:**
- Modify: `frontend/src/app/shared/layout/footer/footer.html`
- Modify: `frontend/src/app/shared/layout/footer/footer.scss`
- Modify: `frontend/src/app/shared/modals/pro-signup-modal/pro-signup-modal.component.html`
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add footer-link i18n keys**

In `frontend/public/i18n/fr.json`, inside the existing `home.v1.footer` block, add a `legalLinks` object next to `legal`:

```json
"footer": {
  "tag": "...",
  ...existing keys...
  "rights": "© 2026 LuxPretty. Tous droits réservés.",
  "legal": "Mentions légales · Confidentialité · Cookies",
  "legalLinks": {
    "cgu": "CGU",
    "cgv": "CGV",
    "privacy": "Confidentialité",
    "legalNotice": "Mentions légales",
    "cookies": "Cookies",
    "ariaLabel": "Liens légaux"
  }
}
```

Same in `frontend/public/i18n/en.json`:

```json
"legalLinks": {
  "cgu": "Terms",
  "cgv": "Terms of Sale",
  "privacy": "Privacy",
  "legalNotice": "Legal Notice",
  "cookies": "Cookies",
  "ariaLabel": "Legal links"
}
```

(Keep `"legal"` key around for now; we'll remove it once nothing references it.)

- [ ] **Step 2: Replace the legal line in the visitor footer**

In `frontend/src/app/shared/layout/footer/footer.html`, replace the existing `lp-footer__bottom` block:

```html
      <!-- Bottom bar: rights + legal -->
      <div class="lp-footer__bottom">
        <span class="lp-footer__rights">{{ 'home.v1.footer.rights' | transloco }}</span>
        <span class="lp-footer__legal">{{ 'home.v1.footer.legal' | transloco }}</span>
      </div>
```

with:

```html
      <!-- Bottom bar: rights + legal links -->
      <div class="lp-footer__bottom">
        <span class="lp-footer__rights">{{ 'home.v1.footer.rights' | transloco }}</span>
        <nav class="lp-footer__legal-nav" [attr.aria-label]="'home.v1.footer.legalLinks.ariaLabel' | transloco">
          <a routerLink="/cgu" class="lp-footer__legal-link">{{ 'home.v1.footer.legalLinks.cgu' | transloco }}</a>
          <a routerLink="/cgv" class="lp-footer__legal-link">{{ 'home.v1.footer.legalLinks.cgv' | transloco }}</a>
          <a routerLink="/confidentialite" class="lp-footer__legal-link">{{ 'home.v1.footer.legalLinks.privacy' | transloco }}</a>
          <a routerLink="/mentions-legales" class="lp-footer__legal-link">{{ 'home.v1.footer.legalLinks.legalNotice' | transloco }}</a>
          <a routerLink="/cookies" class="lp-footer__legal-link">{{ 'home.v1.footer.legalLinks.cookies' | transloco }}</a>
        </nav>
      </div>
```

- [ ] **Step 3: Style the new nav**

Append to `frontend/src/app/shared/layout/footer/footer.scss`:

```scss
.lp-footer__legal-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 0.85rem;
  align-items: center;
}

.lp-footer__legal-link {
  color: inherit;
  opacity: 0.8;
  font-size: 0.85rem;
  text-decoration: none;
  position: relative;
  transition: opacity 150ms ease;

  &:not(:last-child)::after {
    content: '·';
    margin-left: 0.85rem;
    opacity: 0.4;
  }

  &:hover {
    opacity: 1;
    text-decoration: underline;
  }
}
```

- [ ] **Step 4: Update pro signup consent label**

In `frontend/src/app/shared/modals/pro-signup-modal/pro-signup-modal.component.html`, find around line 27:

```html
<mat-checkbox required [ngModel]="consent()" (ngModelChange)="consent.set($event)" name="consent">
  {{ 'proSignup.modal.fields.consent' | transloco }}
</mat-checkbox>
```

Replace the inner text with an HTML span (so the link in the i18n value is interpreted):

```html
<mat-checkbox required [ngModel]="consent()" (ngModelChange)="consent.set($event)" name="consent">
  <span [innerHTML]="'proSignup.modal.fields.consent' | transloco"></span>
</mat-checkbox>
```

- [ ] **Step 5: Update the `proSignup.modal.fields.consent` value (FR + EN)**

In `frontend/public/i18n/fr.json`, find the existing key and replace its value:

```json
"consent": "J'accepte les <a href='/cgu' target='_blank' rel='noopener'>Conditions Générales d'Utilisation</a> et les <a href='/cgv' target='_blank' rel='noopener'>Conditions Générales de Vente</a> de LuxPretty"
```

Same in `frontend/public/i18n/en.json`:

```json
"consent": "I accept LuxPretty's <a href='/cgu' target='_blank' rel='noopener'>Terms of Use</a> and <a href='/cgv' target='_blank' rel='noopener'>Terms of Sale</a>"
```

- [ ] **Step 6: Run existing tests to confirm no regression**

Run: `cd frontend && npm test -- --include='**/footer.spec.ts' --include='**/pro-signup-modal.component.spec.ts' --watch=false`
Expected: PASS. If a snapshot or text assertion in `footer.spec.ts` checks the old legal string, update the assertion.

- [ ] **Step 7: Smoke test in dev**

Run: `cd frontend && npm start`. On the home page footer (logged-out), confirm the 5 legal links are visible and clickable. Open the pro signup modal from `/pricing`, confirm the consent label now shows two clickable links.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/shared/layout/footer/ \
        frontend/src/app/shared/modals/pro-signup-modal/pro-signup-modal.component.html \
        frontend/public/i18n/fr.json \
        frontend/public/i18n/en.json
git commit -m "feat(legal): footer legal nav + pro-signup consent links"
```

**End of PR1.** Open PR with title `feat(legal): legal pages foundation (PR1/4)`.

---

## Task 7: Write FR content for the 5 documents

This task fills the five `legal.*.sections.*` blocks in `frontend/public/i18n/fr.json` per the plan §7 of the spec. **No code changes**, only i18n + the `sections` array of each page component updated to reflect the new section slugs.

For each document below, the section order in i18n must match the array assigned to the `sections` field of the corresponding component. Use HTML-light: `<p>`, `<ul>`, `<ol>`, `<li>`, `<strong>`, `<em>`, `<a>`, `<table>`, `<thead>`, `<tbody>`, `<tr>`, `<th>`, `<td>`.

- [ ] **Step 1: Write CGU sections (FR)**

Replace `legal.cgu` in `fr.json` with:

```json
"cgu": {
  "title": "Conditions Générales d'Utilisation",
  "sections": {
    "objet": {
      "title": "1. Objet et acceptation",
      "body": "<p>Les présentes Conditions Générales d'Utilisation (« CGU ») régissent l'accès et l'utilisation de la plateforme LuxPretty accessible à l'adresse <a href='https://luxpretty.lu'>luxpretty.lu</a> (la « Plateforme »). En créant un compte ou en utilisant la Plateforme, vous reconnaissez avoir lu, compris et accepté sans réserve les présentes CGU.</p>"
    },
    "definitions": {
      "title": "2. Définitions",
      "body": "<ul><li><strong>Plateforme</strong> : le site et l'application LuxPretty.</li><li><strong>Utilisateur</strong> : toute personne accédant à la Plateforme, qu'elle soit Cliente ou Pro.</li><li><strong>Cliente</strong> : utilisateur final souhaitant réserver un soin.</li><li><strong>Pro</strong> ou <strong>Salon</strong> : professionnel ou établissement proposant des soins via la Plateforme.</li><li><strong>Compte</strong> : espace personnel sécurisé créé par l'Utilisateur.</li><li><strong>Contenu</strong> : tout élément textuel, photographique ou autre publié sur la Plateforme par les Utilisateurs.</li><li><strong>Service</strong> : ensemble des fonctionnalités proposées par la Plateforme.</li></ul>"
    },
    "role": {
      "title": "3. Rôle de LuxPretty",
      "body": "<p>LuxPretty exploite une <strong>plateforme de mise en relation</strong> entre Clientes et Pros. LuxPretty n'est en aucun cas partie au contrat de soin conclu entre la Cliente et le Salon. LuxPretty ne fournit aucun soin et n'apporte aucune garantie quant à la qualité, la disponibilité ou l'exécution des prestations proposées par les Salons. Toute réclamation relative à un soin doit être adressée directement au Salon concerné.</p>"
    },
    "inscription": {
      "title": "4. Inscription et compte",
      "body": "<p>L'inscription est ouverte à toute personne âgée d'au moins 16 ans. L'Utilisateur s'engage à fournir des informations exactes, complètes et à jour, et à les maintenir telles. Le mot de passe est strictement personnel et confidentiel. Un seul compte par personne est autorisé. La validation de l'adresse e-mail est requise.</p>"
    },
    "engagementsUser": {
      "title": "5. Engagements de l'Utilisateur",
      "body": "<p>L'Utilisateur s'engage à un usage loyal et conforme aux lois en vigueur. Sont notamment interdits : contenus illicites, diffamatoires, injurieux, à caractère sexuel ou violent ; usurpation d'identité ; collecte de données d'autres utilisateurs ; toute tentative d'accès non autorisé aux systèmes.</p>"
    },
    "engagementsPro": {
      "title": "6. Engagements du Salon",
      "body": "<p>Le Salon s'engage à disposer de toutes les autorisations légales nécessaires à son activité au Luxembourg (autorisation d'établissement, qualifications professionnelles requises). Il garantit l'exactitude des informations publiées (prix, durées, photos, descriptions), le respect des rendez-vous confirmés via la Plateforme, et une attitude professionnelle envers les Clientes.</p>"
    },
    "contenus": {
      "title": "7. Contenus publiés par les Utilisateurs",
      "body": "<p>L'Utilisateur reste titulaire des droits sur les contenus qu'il publie (avis, photos). En les publiant, il accorde à LuxPretty une licence non exclusive, gratuite et mondiale d'utilisation, de reproduction et d'affichage de ces contenus dans le cadre du Service. LuxPretty se réserve le droit de modérer ou supprimer tout contenu manifestement illicite ou contraire aux CGU sans préavis.</p>"
    },
    "pi": {
      "title": "8. Propriété intellectuelle LuxPretty",
      "body": "<p>La marque LuxPretty, son logo, son code source, son design, sa charte graphique et l'ensemble des éléments composant la Plateforme sont la propriété exclusive de LuxPretty et protégés par les lois en vigueur. Toute reproduction ou utilisation non autorisée est strictement interdite.</p>"
    },
    "responsabilite": {
      "title": "9. Limitation de responsabilité",
      "body": "<p>LuxPretty fournit le Service en l'état et selon une obligation de moyens. LuxPretty ne garantit ni une disponibilité ininterrompue, ni l'absence de bug, ni la qualité ou l'exécution des soins (cf. §3). LuxPretty ne saurait être tenue responsable des dommages indirects résultant de l'utilisation de la Plateforme. La responsabilité de LuxPretty est exclue en cas de force majeure.</p>"
    },
    "suspension": {
      "title": "10. Suspension et résiliation du compte",
      "body": "<p>LuxPretty peut suspendre ou résilier un compte en cas de violation des CGU, fraude, abus ou comportement portant atteinte aux autres Utilisateurs. Sauf urgence, l'Utilisateur dispose d'un délai de 7 jours après notification pour régulariser sa situation. La suspension entraîne la perte temporaire d'accès au Service ; la résiliation entraîne la suppression définitive du compte selon les modalités prévues par la Politique de confidentialité.</p>"
    },
    "donnees": {
      "title": "11. Données personnelles",
      "body": "<p>Le traitement des données personnelles est décrit dans notre <a href='/confidentialite'>Politique de confidentialité</a>.</p>"
    },
    "modification": {
      "title": "12. Modification des CGU",
      "body": "<p>LuxPretty peut modifier les CGU à tout moment. Les modifications sont notifiées par e-mail et/ou bandeau sur la Plateforme au moins 30 jours avant leur entrée en vigueur. L'usage continu de la Plateforme après cette date vaut acceptation des nouvelles CGU.</p>"
    },
    "droit": {
      "title": "13. Droit applicable et juridiction",
      "body": "<p>Les présentes CGU sont régies par le droit luxembourgeois. En cas de litige, et après tentative de règlement amiable, les tribunaux de Luxembourg-Ville seront seuls compétents.</p>"
    },
    "contact": {
      "title": "14. Contact",
      "body": "<p>Pour toute question relative aux présentes CGU : <a href='mailto:contact@luxpretty.lu'>contact@luxpretty.lu</a>.</p>"
    }
  }
}
```

Update `cgu-page.component.ts` `sections` array:

```typescript
readonly sections: ReadonlyArray<string> = [
  'objet', 'definitions', 'role', 'inscription', 'engagementsUser', 'engagementsPro',
  'contenus', 'pi', 'responsabilite', 'suspension', 'donnees', 'modification',
  'droit', 'contact',
];
```

- [ ] **Step 2: Write CGV sections (FR)**

Replace `legal.cgv` in `fr.json`. Sections per spec §7.2:

```json
"cgv": {
  "title": "Conditions Générales de Vente",
  "sections": {
    "objet": {
      "title": "1. Objet",
      "body": "<p>Les présentes Conditions Générales de Vente (« CGV ») régissent la souscription par un professionnel (« le Pro ») à un abonnement payant à la plateforme LuxPretty Pro.</p>"
    },
    "b2b": {
      "title": "2. Qualification B2B et renoncement à la rétractation",
      "body": "<p>Le Pro souscrit en qualité de professionnel agissant dans le cadre de son activité économique. À ce titre, et conformément au droit luxembourgeois et à la directive 2011/83/UE, <strong>le droit de rétractation de 14 jours applicable aux consommateurs ne s'applique pas</strong>. Le Pro renonce expressément à toute demande à ce titre.</p>"
    },
    "service": {
      "title": "3. Description du service",
      "body": "<p>L'abonnement donne accès aux fonctionnalités de la Plateforme LuxPretty Pro : gestion du planning, des soins, des clientes, des paiements, et toute fonctionnalité incluse dans le plan souscrit. Les fonctionnalités peuvent évoluer dans le temps.</p>"
    },
    "prix": {
      "title": "4. Prix et facturation",
      "body": "<p>Les prix sont exprimés en euros, hors TVA. La TVA luxembourgeoise applicable (17 % par défaut) est ajoutée sur la facture. Le paiement est effectué par carte bancaire via notre prestataire <a href='https://stripe.com' target='_blank' rel='noopener'>Stripe</a>. Une facture est émise à chaque cycle de facturation et accessible depuis l'espace du Pro.</p>"
    },
    "essai": {
      "title": "5. Essai gratuit",
      "body": "<p>Une période d'essai gratuite de 7 jours est proposée à chaque nouveau Pro. À l'issue de cette période, l'abonnement bascule automatiquement en abonnement payant selon le plan choisi. Un e-mail de rappel est envoyé 2 jours avant la fin de l'essai.</p>"
    },
    "duree": {
      "title": "6. Durée et renouvellement",
      "body": "<p>L'abonnement est conclu pour une durée mensuelle ou annuelle selon le plan souscrit. Il est tacitement reconduit à chaque échéance, sauf annulation dans les conditions prévues à l'article 7.</p>"
    },
    "annulation": {
      "title": "7. Annulation",
      "body": "<p>Le Pro peut annuler son abonnement à tout moment depuis son espace de gestion (rubrique abonnement). L'annulation prend effet à la fin de la période en cours. <strong>La période déjà payée ne fait l'objet d'aucun remboursement.</strong> Le Pro conserve l'accès au Service jusqu'à la fin de la période.</p>"
    },
    "defautPaiement": {
      "title": "8. Défaut de paiement",
      "body": "<p>En cas d'échec de prélèvement, Stripe effectue jusqu'à 4 nouvelles tentatives sur 14 jours. À l'issue de cette période sans régularisation, l'abonnement est automatiquement suspendu. La régularisation du paiement entraîne la restauration immédiate du Service.</p>"
    },
    "evolution": {
      "title": "9. Évolution du service et des prix",
      "body": "<p>LuxPretty peut modifier les fonctionnalités, les conditions ou les prix de ses abonnements. Toute hausse de prix est notifiée par e-mail au moins 30 jours avant son entrée en vigueur ; le Pro peut alors annuler son abonnement sans frais avant cette date.</p>"
    },
    "sla": {
      "title": "10. Niveau de service",
      "body": "<p>LuxPretty s'engage à fournir le Service selon une obligation de moyens. Aucun engagement de disponibilité chiffré n'est contractualisé. Les opérations de maintenance planifiée sont annoncées, dans la mesure du possible, au moins 48 heures à l'avance.</p>"
    },
    "responsabilite": {
      "title": "11. Responsabilité",
      "body": "<p>La responsabilité de LuxPretty est plafonnée au montant payé par le Pro au titre de l'abonnement sur les 12 mois précédant le fait générateur. Sont exclus les dommages indirects tels que perte de clientèle, manque à gagner, atteinte à l'image.</p>"
    },
    "donnees": {
      "title": "12. Données du Pro et réversibilité",
      "body": "<p>Le Pro peut à tout moment exporter ses données (fiche salon, clients, bookings, soins) depuis son espace. En cas de résiliation, les données sont conservées 90 jours puis supprimées, sauf obligation légale contraire (factures conservées 10 ans).</p>"
    },
    "forceMajeure": {
      "title": "13. Force majeure",
      "body": "<p>Aucune partie ne saurait être tenue responsable d'un manquement résultant d'un événement de force majeure tel que défini par la jurisprudence luxembourgeoise.</p>"
    },
    "droit": {
      "title": "14. Droit applicable et juridiction",
      "body": "<p>Les présentes CGV sont régies par le droit luxembourgeois. Les tribunaux de Luxembourg-Ville sont seuls compétents pour tout litige.</p>"
    }
  }
}
```

Update `cgv-page.component.ts` `sections`:

```typescript
readonly sections: ReadonlyArray<string> = [
  'objet', 'b2b', 'service', 'prix', 'essai', 'duree', 'annulation',
  'defautPaiement', 'evolution', 'sla', 'responsabilite', 'donnees',
  'forceMajeure', 'droit',
];
```

- [ ] **Step 3: Write Privacy sections (FR)**

Replace `legal.privacy` in `fr.json`. Sections per spec §7.3:

```json
"privacy": {
  "title": "Politique de confidentialité",
  "sections": {
    "responsable": {
      "title": "1. Responsable du traitement",
      "body": "<p>Le responsable du traitement des données personnelles est LuxPretty, contact <a href='mailto:privacy@luxpretty.lu'>privacy@luxpretty.lu</a>. Les coordonnées complètes (raison sociale, siège, numéro RCS) sont précisées dans nos <a href='/mentions-legales'>mentions légales</a>.</p>"
    },
    "donneesCollectees": {
      "title": "2. Données collectées",
      "body": "<ul><li><strong>Compte</strong> : e-mail, mot de passe (hashé), nom, prénom, rôle, langue, date de création.</li><li><strong>Profil pro</strong> : nom du salon, adresse, téléphone, photos, soins, horaires.</li><li><strong>Bookings</strong> : date, soin, cliente, salon, notes, statut.</li><li><strong>Paiements</strong> : traités intégralement par Stripe. LuxPretty ne stocke aucune donnée bancaire.</li><li><strong>Contenus</strong> : avis, photos uploadées, posts publiés.</li><li><strong>Logs techniques</strong> : adresse IP, user-agent, horodatage (sécurité, anti-fraude).</li></ul>"
    },
    "baseLegale": {
      "title": "3. Base légale des traitements",
      "body": "<ul><li><strong>Exécution du contrat</strong> : création de compte, réservations, accès au Service.</li><li><strong>Consentement</strong> : communications marketing (opt-in séparé, à venir).</li><li><strong>Intérêt légitime</strong> : journalisation pour sécurité et lutte contre la fraude.</li><li><strong>Obligation légale</strong> : facturation, comptabilité (conservation 10 ans).</li></ul>"
    },
    "finalites": {
      "title": "4. Finalités",
      "body": "<p>Fourniture du Service, gestion des paiements, sécurité de la Plateforme, lutte contre la fraude, communications transactionnelles (confirmation de rendez-vous, etc.), respect des obligations légales.</p>"
    },
    "destinataires": {
      "title": "5. Destinataires et sous-traitants",
      "body": "<ul><li><strong>Stripe</strong> (Irlande / États-Unis) — traitement des paiements.</li><li><strong>Postmark</strong> (États-Unis) — envoi des e-mails transactionnels.</li><li><strong>OVH</strong> (France) — hébergement des serveurs et bases de données.</li><li><strong>Cloudflare</strong> (États-Unis) — CDN et protection DDoS.</li><li><strong>Cloudflare R2</strong> — stockage des images uploadées.</li><li><strong>Google</strong> (États-Unis) — connexion OAuth optionnelle.</li><li><strong>Meta / Facebook</strong> (États-Unis) — connexion OAuth optionnelle (prévue).</li></ul>"
    },
    "transferts": {
      "title": "6. Transferts hors UE",
      "body": "<p>Certains sous-traitants (Stripe, Postmark, Cloudflare, Google, Meta) sont établis aux États-Unis. Les transferts sont encadrés par les clauses contractuelles types adoptées par la Commission européenne, garantissant un niveau de protection équivalent à celui de l'UE.</p>"
    },
    "conservation": {
      "title": "7. Durées de conservation",
      "body": "<ul><li>Compte actif : jusqu'à demande de suppression.</li><li>Compte inactif depuis 3 ans : suppression automatique.</li><li>Données de facturation : 10 ans (obligation comptable luxembourgeoise).</li><li>Logs techniques : 6 mois.</li><li>Données du Pro après résiliation : 90 jours puis suppression.</li></ul>"
    },
    "droits": {
      "title": "8. Vos droits RGPD",
      "body": "<p>Vous disposez à tout moment des droits suivants : accès, rectification, effacement, portabilité, opposition, limitation du traitement, retrait du consentement, directives post-mortem.</p>"
    },
    "exercice": {
      "title": "9. Modalités d'exercice de vos droits",
      "body": "<p>Pour exercer vos droits, contactez-nous à <a href='mailto:privacy@luxpretty.lu'>privacy@luxpretty.lu</a> en joignant un justificatif d'identité. Nous répondrons sous un délai d'un mois, prolongeable à trois mois pour les demandes complexes.</p>"
    },
    "reclamation": {
      "title": "10. Réclamation",
      "body": "<p>Si vous estimez que vos droits ne sont pas respectés, vous pouvez déposer une réclamation auprès de la Commission Nationale pour la Protection des Données (CNPD), 15 boulevard du Jazz, L-4370 Belvaux, Luxembourg — <a href='https://cnpd.public.lu' target='_blank' rel='noopener'>cnpd.public.lu</a>.</p>"
    },
    "securite": {
      "title": "11. Sécurité",
      "body": "<p>Vos données sont chiffrées en transit (HTTPS) et stockées de manière sécurisée. Les mots de passe sont hashés (BCrypt). Les accès administratifs sont limités au strict nécessaire et journalisés.</p>"
    },
    "mineurs": {
      "title": "12. Mineurs",
      "body": "<p>L'inscription à la Plateforme est réservée aux personnes âgées d'au moins 16 ans. L'accord d'un représentant légal est requis pour les mineurs de 16 à 18 ans. Tout compte signalé comme appartenant à un mineur non autorisé sera supprimé.</p>"
    },
    "modification": {
      "title": "13. Modifications de la politique",
      "body": "<p>Cette politique peut être modifiée. Toute modification matérielle sera notifiée 30 jours avant son entrée en vigueur.</p>"
    }
  }
}
```

Update `privacy-page.component.ts` `sections`:

```typescript
readonly sections: ReadonlyArray<string> = [
  'responsable', 'donneesCollectees', 'baseLegale', 'finalites', 'destinataires',
  'transferts', 'conservation', 'droits', 'exercice', 'reclamation', 'securite',
  'mineurs', 'modification',
];
```

- [ ] **Step 4: Write Legal Notice sections (FR)**

Replace `legal.notice` in `fr.json`:

```json
"notice": {
  "title": "Mentions légales",
  "preLaunchBanner": "⚠️ LuxPretty est actuellement un projet en pré-lancement opéré à titre personnel. La société éditrice est en cours d'immatriculation.",
  "sections": {
    "editeur": {
      "title": "1. Éditeur du site",
      "body": "<p><strong>Nom</strong> : LuxPretty [À COMPLÉTER — raison sociale après immatriculation]<br><strong>Statut</strong> : [À COMPLÉTER]<br><strong>Adresse</strong> : [À COMPLÉTER]<br><strong>Contact</strong> : <a href='mailto:contact@luxpretty.lu'>contact@luxpretty.lu</a><br><strong>RCS Luxembourg</strong> : [À COMPLÉTER]<br><strong>N° TVA</strong> : [À COMPLÉTER]</p>"
    },
    "directeur": {
      "title": "2. Directeur de la publication",
      "body": "<p>Gustavo Alves.</p>"
    },
    "hebergeur": {
      "title": "3. Hébergeur",
      "body": "<p>OVH SAS<br>2 rue Kellermann, 59100 Roubaix, France<br>Téléphone : +33 9 72 10 10 07<br><a href='https://www.ovh.com' target='_blank' rel='noopener'>www.ovh.com</a></p>"
    },
    "cdn": {
      "title": "4. CDN et sécurité",
      "body": "<p>Cloudflare, Inc.<br>101 Townsend Street, San Francisco, CA 94107, États-Unis<br><a href='https://www.cloudflare.com' target='_blank' rel='noopener'>www.cloudflare.com</a></p>"
    },
    "conception": {
      "title": "5. Conception et développement",
      "body": "<p>LuxPretty.</p>"
    },
    "credits": {
      "title": "6. Crédits photos",
      "body": "<p>Photos d'illustration : [À COMPLÉTER — Unsplash, Pexels, photos pros sous licence, etc.]. Les photos publiées par les Salons restent la propriété de leurs auteurs.</p>"
    },
    "signalement": {
      "title": "7. Signalement de contenu illicite",
      "body": "<p>Tout contenu manifestement illicite peut être signalé à <a href='mailto:contact@luxpretty.lu'>contact@luxpretty.lu</a>. Conformément à la loi luxembourgeoise sur le commerce électronique, LuxPretty agira promptement après notification.</p>"
    }
  }
}
```

Update `legal-notice-page.component.ts` `sections`:

```typescript
readonly sections: ReadonlyArray<string> = [
  'editeur', 'directeur', 'hebergeur', 'cdn', 'conception', 'credits', 'signalement',
];
```

- [ ] **Step 5: Write Cookies sections (FR)**

Replace `legal.cookies` in `fr.json`:

```json
"cookies": {
  "title": "Politique cookies",
  "sections": {
    "definition": {
      "title": "1. Qu'est-ce qu'un cookie ?",
      "body": "<p>Un cookie est un petit fichier déposé par un site sur le terminal d'un utilisateur (ordinateur, smartphone, tablette) pour mémoriser certaines informations entre les visites. Nous utilisons également des technologies de stockage local du navigateur (<em>localStorage</em>) à des fins similaires.</p>"
    },
    "utilises": {
      "title": "2. Cookies et stockages utilisés sur LuxPretty",
      "body": "<table><thead><tr><th>Nom</th><th>Type</th><th>Finalité</th><th>Durée</th><th>Base légale</th></tr></thead><tbody><tr><td>XSRF-TOKEN</td><td>Cookie de session</td><td>Protection contre les attaques CSRF</td><td>Session</td><td>Nécessaire</td></tr><tr><td>JSESSIONID (ou équivalent)</td><td>Cookie de session</td><td>Authentification utilisateur</td><td>Session</td><td>Nécessaire</td></tr><tr><td>lp_auth_token</td><td>localStorage</td><td>Jeton d'authentification</td><td>Jusqu'à déconnexion</td><td>Nécessaire</td></tr><tr><td>lp_lang</td><td>localStorage</td><td>Préférence de langue</td><td>Persistant</td><td>Nécessaire</td></tr><tr><td>lp_cookie_banner_v1</td><td>localStorage</td><td>Mémorisation de la fermeture du bandeau cookies</td><td>Persistant</td><td>Nécessaire</td></tr><tr><td>__stripe_mid, __stripe_sid</td><td>Cookies tiers (stripe.com)</td><td>Sécurisation des paiements, prévention fraude</td><td>1 an / session</td><td>Nécessaire à l'exécution du paiement</td></tr></tbody></table>"
    },
    "aucunTracking": {
      "title": "3. Aucun cookie de mesure d'audience ou marketing",
      "body": "<p>LuxPretty <strong>n'utilise aucun outil de mesure d'audience tiers</strong> (Google Analytics, Matomo, etc.), <strong>aucun pixel publicitaire</strong> et <strong>aucun cookie de profilage</strong>. Si cela venait à changer, nous mettrions en place un mécanisme de consentement préalable explicite.</p>"
    },
    "gestion": {
      "title": "4. Comment gérer les cookies",
      "body": "<p>Vous pouvez configurer votre navigateur pour accepter, refuser ou supprimer les cookies. <strong>La désactivation des cookies nécessaires empêche le bon fonctionnement de la Plateforme</strong> (authentification, paiement). Liens d'aide : <a href='https://support.google.com/chrome/answer/95647' target='_blank' rel='noopener'>Chrome</a>, <a href='https://support.mozilla.org/fr/kb/protection-renforcee-contre-pistage-firefox-ordinateur' target='_blank' rel='noopener'>Firefox</a>, <a href='https://support.apple.com/fr-fr/guide/safari/sfri11471/mac' target='_blank' rel='noopener'>Safari</a>, <a href='https://support.microsoft.com/fr-fr/microsoft-edge' target='_blank' rel='noopener'>Edge</a>.</p>"
    },
    "modification": {
      "title": "5. Modifications de la politique",
      "body": "<p>Cette politique peut évoluer. Toute modification matérielle (notamment l'ajout d'un cookie non strictement nécessaire) sera notifiée 30 jours avant son entrée en vigueur et donnera lieu à l'affichage d'un nouveau bandeau de consentement.</p>"
    }
  }
}
```

Update `cookies-page.component.ts` `sections`:

```typescript
readonly sections: ReadonlyArray<string> = [
  'definition', 'utilises', 'aucunTracking', 'gestion', 'modification',
];
```

- [ ] **Step 6: Update existing component spec files**

Each page spec currently asserts a `placeholder` section; update its `langs` block in `TranslocoTestingModule.forRoot` to provide at least one of the new section keys (e.g., `objet` for CGU) and update the assertion to check it.

- [ ] **Step 7: Run all legal tests**

Run: `cd frontend && npm test -- --include='**/pages/legal/**/*.spec.ts' --watch=false`
Expected: PASS.

- [ ] **Step 8: Manual smoke**

Run `cd frontend && npm start`, navigate to each of the 5 pages and confirm full content renders correctly in French. Verify in-page anchor links (`/confidentialite` from CGU §11, `/mentions-legales` from Privacy §1).

- [ ] **Step 9: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/src/app/pages/legal/
git commit -m "feat(legal): write FR content for 5 legal documents"
```

**End of PR2.**

---

## Task 8: Write EN content for the 5 documents

Mirror Task 7 but for `frontend/public/i18n/en.json`. Translate the FR content into clear, sober legal English. Same section keys, same hierarchy. Keep URLs, e-mails, and addresses identical (Luxembourg jurisdiction, CNPD, OVH, Cloudflare).

- [ ] **Step 1: Translate `legal.cgu` to EN**

Replace `legal.cgu` in `en.json` with English equivalents of all 14 sections from Task 7 Step 1. Section keys identical to FR (`objet`, `definitions`, ..., `contact`). Translate full text — no shortcuts.

- [ ] **Step 2: Translate `legal.cgv` to EN**

Same as Task 7 Step 2, English text. Section keys identical.

- [ ] **Step 3: Translate `legal.privacy` to EN**

Same as Task 7 Step 3.

- [ ] **Step 4: Translate `legal.notice` to EN**

Same as Task 7 Step 4, including the `preLaunchBanner` value already drafted in Task 4 Step 2.

- [ ] **Step 5: Translate `legal.cookies` to EN**

Same as Task 7 Step 5.

- [ ] **Step 6: Manual smoke**

Run `cd frontend && npm start`, switch language to EN via the language switcher, navigate the 5 pages, confirm everything renders correctly.

- [ ] **Step 7: Commit**

```bash
git add frontend/public/i18n/en.json
git commit -m "feat(legal): write EN content for 5 legal documents"
```

**End of PR3.**

---

## Task 9: E2E tests (Playwright)

**Files:**
- Create: `frontend/e2e/legal/legal-pages.spec.ts`
- Create: `frontend/e2e/legal/cookie-banner.spec.ts`

- [ ] **Step 1: Confirm Playwright config and dev URL**

Run: `cat frontend/playwright.config.ts | head -40`
Expected: baseURL is `http://localhost:4200` (or whatever dev server the existing E2E uses). Note that value for the tests below — replace `BASE_URL` placeholder if necessary. If baseURL differs, use it directly in `page.goto()` calls.

- [ ] **Step 2: Write the legal-pages E2E spec**

`frontend/e2e/legal/legal-pages.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';

const PAGES = [
  { path: '/cgu', title_fr: "Conditions Générales d'Utilisation" },
  { path: '/cgv', title_fr: 'Conditions Générales de Vente' },
  { path: '/confidentialite', title_fr: 'Politique de confidentialité' },
  { path: '/mentions-legales', title_fr: 'Mentions légales' },
  { path: '/cookies', title_fr: 'Politique cookies' },
];

test.describe('Legal pages', () => {
  for (const p of PAGES) {
    test(`renders ${p.path}`, async ({ page }) => {
      await page.goto(p.path);
      await expect(page.getByRole('heading', { level: 1 })).toContainText(p.title_fr);
      await expect(page.locator('.legal-page__updated')).toContainText(/\d{4}/);
      await expect(page.locator('.legal-section').first()).toBeVisible();
    });
  }

  test('footer exposes the 5 legal links and they navigate correctly', async ({ page }) => {
    await page.goto('/');
    const legalNav = page.locator('.lp-footer__legal-nav');
    await expect(legalNav.getByRole('link', { name: /CGU/i })).toBeVisible();
    await legalNav.getByRole('link', { name: /CGU/i }).click();
    await expect(page).toHaveURL(/\/cgu$/);
  });

  test('pre-launch banner shows on /mentions-legales', async ({ page }) => {
    await page.goto('/mentions-legales');
    await expect(page.locator('.legal-prelaunch-banner')).toBeVisible();
  });
});
```

- [ ] **Step 3: Write the cookie banner E2E spec**

`frontend/e2e/legal/cookie-banner.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';

test.describe('Cookie banner', () => {
  test.beforeEach(async ({ page }) => {
    await page.context().clearCookies();
    await page.addInitScript(() => localStorage.removeItem('lp_cookie_banner_v1'));
  });

  test('banner is visible on first visit', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.cookie-banner')).toBeVisible();
  });

  test('clicking the dismiss button hides the banner', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: /j'ai compris|got it/i }).click();
    await expect(page.locator('.cookie-banner')).toHaveCount(0);
  });

  test('dismissal persists across reloads', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: /j'ai compris|got it/i }).click();
    await page.reload();
    await expect(page.locator('.cookie-banner')).toHaveCount(0);
  });

  test('the "learn more" link goes to /cookies', async ({ page }) => {
    await page.goto('/');
    await page.locator('.cookie-banner__link').click();
    await expect(page).toHaveURL(/\/cookies$/);
  });
});
```

- [ ] **Step 4: Run the E2E suite**

Make sure backend is running (Spring on :8080) and frontend dev server is up.

Run: `cd frontend && npx playwright test e2e/legal/`
Expected: all specs pass.

If a spec fails because the dev server isn't reachable, follow the same procedure as the existing `e2e/auth/register.spec.ts`.

- [ ] **Step 5: Commit**

```bash
git add frontend/e2e/legal/
git commit -m "test(legal): e2e for legal pages + cookie banner"
```

---

## Task 10: SEO meta + canonical tags

**Files:**
- Modify: each of the 5 legal page components — add title/description metadata via `Title`/`Meta` Angular services.

- [ ] **Step 1: Add a helper in each page component to set SEO meta**

Pattern, applied identically to all 5 pages (example for CGU):

`cgu-page.component.ts`:

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LegalLayoutComponent } from '../legal-layout/legal-layout.component';

@Component({
  selector: 'app-cgu-page',
  standalone: true,
  imports: [LegalLayoutComponent, TranslocoPipe],
  templateUrl: './cgu-page.component.html',
})
export class CguPageComponent implements OnInit {
  private readonly titleService = inject(Title);
  private readonly metaService = inject(Meta);
  private readonly transloco = inject(TranslocoService);

  readonly updatedAt = '2026-05-17';
  readonly sections: ReadonlyArray<string> = [/* same array as Task 7 */];

  ngOnInit(): void {
    const title = this.transloco.translate('legal.cgu.title');
    this.titleService.setTitle(`${title} · LuxPretty`);
    this.metaService.updateTag({
      name: 'description',
      content: this.transloco.translate('legal.cgu.title') + ' — LuxPretty',
    });
  }
}
```

Apply the same pattern to `CgvPageComponent`, `PrivacyPageComponent`, `LegalNoticePageComponent`, `CookiesPageComponent`, each pointing to its own `legal.{ns}.title` key.

- [ ] **Step 2: Build to confirm no regression**

Run: `cd frontend && npm run build`
Expected: success.

- [ ] **Step 3: Run all unit tests**

Run: `cd frontend && npm test -- --watch=false`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/legal/
git commit -m "feat(legal): SEO title and meta description on legal pages"
```

---

## Task 11: A11y, build, cleanup

**Files:**
- Modify: `frontend/public/i18n/fr.json` and `en.json` — remove the now-unused `home.v1.footer.legal` key.

- [ ] **Step 1: Remove the obsolete `legal` key**

Grep first: `grep -rn "home.v1.footer.legal[^L]" frontend/src` — confirm no usage remains. If clean, remove `"legal": "Mentions légales · Confidentialité · Cookies"` from `fr.json` and the EN equivalent from `en.json`.

- [ ] **Step 2: Final full build + tests**

Run: `cd frontend && npm run build && npm test -- --watch=false`
Expected: build succeeds, all tests pass.

- [ ] **Step 3: Manual a11y check**

In Chrome DevTools → Lighthouse → Accessibility audit. Run on `/cgu`, `/cookies`, and the home page (with banner visible). Expected score ≥ 95. Fix any contrast/aria warnings encountered (likely none if the styles in Task 2/3 were respected).

- [ ] **Step 4: Verify the post-merge checklist of the spec**

Re-read `docs/superpowers/specs/2026-05-17-cgu-rgpd-cookies-design.md` §14 ("Définition de terminé") and tick off each item.

- [ ] **Step 5: Commit and open final PR**

```bash
git add frontend/public/i18n/
git commit -m "chore(legal): drop unused legal footer key after migration"
```

**End of PR4.**

---

## Post-merge follow-ups (out of plan scope)

- [ ] Create the e-mail aliases `contact@luxpretty.lu` and `privacy@luxpretty.lu`.
- [ ] Have a Luxembourg lawyer review the drafted documents before real billing is enabled.
- [ ] Fill the `[À COMPLÉTER]` placeholders in `legal.notice.sections.editeur.body` (FR + EN) once the entity is incorporated.
- [ ] Remove the pre-launch banner from the legal notice page once the entity exists.
