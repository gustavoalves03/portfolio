# LuxPretty Rebrand Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename "Pretty Face" to "LuxPretty" across the entire codebase (Java packages, frontend, i18n, docs, email templates) and add a small LuxPretty logo to the header on salon/pro pages so clients can navigate back to the marketplace home.

**Architecture:** Big-bang rebrand in a single PR, executed in dependency order: backend Java refactor first (highest risk, IDE-driven), then a new `<lp-logo>` Angular component used in the header, then i18n + hardcoded strings, then email templates + docs. Validation via existing test suites + manual smoke test.

**Tech Stack:** Java 21, Spring Boot 3.5.4, Angular 20 (standalone, signals), Tailwind, Material 3, Transloco i18n, Karma/Jasmine, JUnit 5, Maven.

**Spec:** `docs/superpowers/specs/2026-05-10-luxpretty-rebrand-design.md` (commit `dc3967a`)

---

## File Structure

### New files

| File | Responsibility |
|---|---|
| `frontend/src/app/shared/uis/lp-logo/lp-logo.component.ts` | Standalone logo component, accepts `variant` input |
| `frontend/src/app/shared/uis/lp-logo/lp-logo.component.html` | Template with the `LUX Pretty` markup |
| `frontend/src/app/shared/uis/lp-logo/lp-logo.component.scss` | Styles using Cormorant + Italiana fonts and `--lp-accent-gold` |
| `frontend/src/app/shared/uis/lp-logo/lp-logo.component.spec.ts` | Unit tests for the 3 variants |

### Modified files (frontend)

| File | What changes |
|---|---|
| `frontend/src/index.html` | Title `App` → `LuxPretty`, add Cormorant/Italiana Google Fonts link |
| `frontend/src/styles.scss` | Add `--lp-accent-gold: #C9A961` token |
| `frontend/src/app/shared/layout/header/header.html` | Replace inline brand markup with `<lp-logo>`, add small variant on left |
| `frontend/src/app/shared/layout/header/header.ts` | Import new component, add `desktop` signal-based check if needed |
| `frontend/src/app/shared/layout/header/header.scss` | Remove `.brand-pretty` / `.brand-face` classes (moved into component) |
| `frontend/src/app/shared/layout/header/header.spec.ts` | Add small-logo test, update flicker comment |
| `frontend/src/app/i18n/transloco-http.loader.ts` | `app.title` `'Pretty Face'` → `'LuxPretty'` (FR + EN) |
| `frontend/public/i18n/fr.json` | All 17 occurrences (logo "LuxPretty", produit "LuxPretty Pro") |
| `frontend/public/i18n/en.json` | All 17 occurrences |
| `frontend/src/app/pages/about/about.html` | "Pretty Face" → "LuxPretty" |
| `frontend/src/app/pages/salon/pc-view/salon-page-pc.component.html` | "Pretty Face ✿" → "LuxPretty ✿" |
| `frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts:943` | Share title fallback |
| `frontend/src/app/features/salon-profile/salon-profile.component.spec.ts` | Test fixture name update |
| `frontend/src/app/pages/auth/register/register.component.spec.ts:14` | Test string update |
| `frontend/package.json` | `"name": "app"` → `"name": "luxpretty-web"` |

### Modified files (backend)

| File | What changes |
|---|---|
| `backend/pom.xml` | `<groupId>com.prettyface</groupId>` → `com.luxpretty`, `<artifactId>` `pretty-face-backend` → `lux-pretty-backend`, `<name>Pretty Face</name>` → `LuxPretty` |
| `backend/src/main/resources/application.properties` | `spring.application.name=pretty-face` → `lux-pretty`, `app.mail.from-name=Pretty Face` → `LuxPretty` |
| `backend/src/main/java/com/prettyface/**/*.java` | **Rename whole package tree** to `com.luxpretty/**/*.java` (289 files) |
| `backend/src/test/java/com/prettyface/**/*.java` | Same package rename |
| `backend/src/main/java/com/luxpretty/app/config/DataInitializer.java:63` | Admin name "Pretty Face Admin" → "LuxPretty Admin", email `admin@prettyface.com` → `admin@luxpretty.com` |
| `backend/src/main/java/com/luxpretty/app/notification/app/EmailService.java:57` | Subject `Welcome to Pretty Face / Bienvenue sur Pretty Face !` → `Welcome to LuxPretty / Bienvenue sur LuxPretty !` |
| `backend/src/main/resources/templates/booking-notification-pro.html` | Title + `<h1>` + footer copyright |
| `backend/src/main/resources/templates/booking-confirmation.html` | Title + `<h1>` + footer copyright |
| `backend/src/main/resources/templates/welcome-pro.html` | Title + `<h1>` + body + footer copyright |
| `backend/src/main/resources/templates/password-reset.html` | `<h1>` + footer copyright |

### Modified files (docs/config)

| File | What changes |
|---|---|
| `CLAUDE.md` | All "Pretty Face" → "LuxPretty", path `com/prettyface/` → `com/luxpretty/` |
| `AGENTS.md` | Brand references |
| `OAUTH2_SETUP.md` | Name + paths to Java packages |
| `.env.example` | Header comment |

### Out of scope

- `_bmad/`, `_bmad-output/`, `mockup-*.html` (legacy artifacts)
- `memory/` (user's auto-memory)
- Git history rewrite
- Database schema (no `prettyface`-named tables)
- `uploads/` (user content)
- Google Cloud Console OAuth consent screen (manual post-merge action)

---

## Pre-flight: ensure clean working tree

- [ ] **Step 1: Verify clean git state**

Run: `git status`
Expected: `working tree clean` on `main`. If not, stash or commit current work first.

- [ ] **Step 2: Create rebrand branch**

Run: `git checkout -b feat/luxpretty-rebrand`

- [ ] **Step 3: Run baseline tests to confirm green starting point**

Run: `cd backend && mvn test -q` and `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless`
Expected: both green. If any test fails on `main`, stop and fix that first — we need a clean baseline.

---

## Task 1: Backend Java package rename

**Files:**
- Modify: `backend/pom.xml` (groupId, artifactId, name)
- Modify: `backend/src/main/resources/application.properties:1` (spring.application.name)
- Rename: `backend/src/main/java/com/prettyface/` → `backend/src/main/java/com/luxpretty/` (recursive, 289 files)
- Rename: `backend/src/test/java/com/prettyface/` → `backend/src/test/java/com/luxpretty/`

- [ ] **Step 1: Rename source package directory**

Run from repo root:
```bash
git mv backend/src/main/java/com/prettyface backend/src/main/java/com/luxpretty
git mv backend/src/test/java/com/prettyface backend/src/test/java/com/luxpretty
```

- [ ] **Step 2: Replace package declarations and imports in Java files**

Run from repo root:
```bash
find backend/src -name "*.java" -exec sed -i '' 's/com\.prettyface/com.luxpretty/g' {} +
```

(macOS sed syntax; on Linux drop the `''` after `-i`.)

- [ ] **Step 3: Verify no stale references remain**

Run: `grep -rn "com\.prettyface" backend/src`
Expected: zero output.

- [ ] **Step 4: Update pom.xml**

Edit `backend/pom.xml` — change three lines:
```xml
<groupId>com.luxpretty</groupId>
<artifactId>lux-pretty-backend</artifactId>
<name>LuxPretty</name>
```

- [ ] **Step 5: Update application.properties**

Edit `backend/src/main/resources/application.properties` line 1:
```
spring.application.name=lux-pretty
```

- [ ] **Step 6: Run backend tests**

Run: `cd backend && mvn clean test -q`
Expected: BUILD SUCCESS, all tests pass. If any test fails because of a hardcoded `com.prettyface` string (component scan, reflection), fix the failing test/source.

- [ ] **Step 7: Smoke test the boot**

Run: `cd backend && mvn spring-boot:run` (let it boot, then Ctrl+C)
Expected: log contains `Starting application 'lux-pretty'`, no startup errors. If JPA/component scan complains, search for any remaining `com.prettyface` reference.

- [ ] **Step 8: Commit**

```bash
git add backend/
git commit -m "refactor(backend): rename package com.prettyface to com.luxpretty"
```

---

## Task 2: Backend hardcoded brand strings

**Files:**
- Modify: `backend/src/main/resources/application.properties:82`
- Modify: `backend/src/main/java/com/luxpretty/app/config/DataInitializer.java:63`
- Modify: `backend/src/main/java/com/luxpretty/app/notification/app/EmailService.java:57`

- [ ] **Step 1: Update mail from-name**

Edit `backend/src/main/resources/application.properties` line 82:
```
app.mail.from-name=LuxPretty
```

- [ ] **Step 2: Update DataInitializer admin seed**

Edit `backend/src/main/java/com/luxpretty/app/config/DataInitializer.java` around line 63 — change the literal:
```java
"LuxPretty Admin", "admin@luxpretty.com", "Admin2026!", Role.ADMIN);
```

- [ ] **Step 3: Update EmailService welcome subject**

Edit `backend/src/main/java/com/luxpretty/app/notification/app/EmailService.java` around line 57:
```java
helper.setSubject("Welcome to LuxPretty / Bienvenue sur LuxPretty !");
```

- [ ] **Step 4: Verify no remaining backend brand strings**

Run: `grep -rn "Pretty Face\|pretty-face\|PrettyFace" backend/src`
Expected: zero output.

- [ ] **Step 5: Run backend tests**

Run: `cd backend && mvn test -q`
Expected: all green. If a test asserts on the old admin email or subject string, update the assertion.

- [ ] **Step 6: Commit**

```bash
git add backend/
git commit -m "chore(backend): replace Pretty Face brand strings with LuxPretty"
```

---

## Task 3: Backend email templates

**Files:**
- Modify: `backend/src/main/resources/templates/booking-notification-pro.html`
- Modify: `backend/src/main/resources/templates/booking-confirmation.html`
- Modify: `backend/src/main/resources/templates/welcome-pro.html`
- Modify: `backend/src/main/resources/templates/password-reset.html`

- [ ] **Step 1: Replace brand strings in all 4 templates**

Run from repo root:
```bash
sed -i '' 's/Pretty Face/LuxPretty/g' backend/src/main/resources/templates/booking-notification-pro.html backend/src/main/resources/templates/booking-confirmation.html backend/src/main/resources/templates/welcome-pro.html backend/src/main/resources/templates/password-reset.html
```

- [ ] **Step 2: Update copyright year notation if needed**

The templates currently say `© 2025 Pretty Face`. After substitution they will say `© 2025 LuxPretty`. **Leave the year as-is** — bumping years is out of scope for this rebrand.

- [ ] **Step 3: Verify**

Run: `grep -rn "Pretty Face\|prettyface" backend/src/main/resources`
Expected: zero output.

- [ ] **Step 4: Run backend tests**

Run: `cd backend && mvn test -q`
Expected: all green. Email tests (if they assert on rendered HTML containing "LuxPretty") should pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/templates/
git commit -m "chore(backend): rebrand email templates to LuxPretty"
```

---

## Task 4: Frontend — add Cormorant/Italiana fonts and accent gold token

**Files:**
- Modify: `frontend/src/index.html`
- Modify: `frontend/src/styles.scss`

- [ ] **Step 1: Add Google Fonts link to index.html**

Edit `frontend/src/index.html` — change `<title>App</title>` to `<title>LuxPretty</title>` and add a fonts link below the existing Fraunces/DM Sans line:

```html
<link href="https://fonts.googleapis.com/css2?family=Cormorant+Garamond:ital,wght@0,400;0,500;0,600;1,400&family=Italiana&display=swap" rel="stylesheet">
```

- [ ] **Step 2: Add accent gold token to styles.scss**

Edit `frontend/src/styles.scss` — inside the `:root` block, after `--pf-burgundy-soft` line, add:

```scss
  /* --- LuxPretty accent (decorative only) --- */
  --lp-accent-gold:  #C9A961;
```

- [ ] **Step 3: Verify the dev server still builds**

Run: `cd frontend && npm start` (let it compile, then Ctrl+C once ready)
Expected: Angular CLI reports `Application bundle generation complete`, no SCSS errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/index.html frontend/src/styles.scss
git commit -m "feat(frontend): add Cormorant/Italiana fonts and lp-accent-gold token"
```

---

## Task 5: Create `<lp-logo>` component (test first)

**Files:**
- Create: `frontend/src/app/shared/uis/lp-logo/lp-logo.component.ts`
- Create: `frontend/src/app/shared/uis/lp-logo/lp-logo.component.html`
- Create: `frontend/src/app/shared/uis/lp-logo/lp-logo.component.scss`
- Create: `frontend/src/app/shared/uis/lp-logo/lp-logo.component.spec.ts`

- [ ] **Step 1: Write the failing spec**

Create `frontend/src/app/shared/uis/lp-logo/lp-logo.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { LpLogoComponent } from './lp-logo.component';

describe('LpLogoComponent', () => {
  let fixture: ComponentFixture<LpLogoComponent>;
  let component: LpLogoComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LpLogoComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(LpLogoComponent);
    component = fixture.componentInstance;
  });

  it('renders both LUX and Pretty words by default', () => {
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.textContent).toContain('LUX');
    expect(root.textContent).toContain('Pretty');
  });

  it('applies the small variant class when variant="small"', () => {
    fixture.componentRef.setInput('variant', 'small');
    fixture.detectChanges();
    const host = fixture.debugElement.query(By.css('.lp-logo'));
    expect(host.nativeElement.classList).toContain('lp-logo--small');
  });

  it('shows the tagline when variant="with-tagline"', () => {
    fixture.componentRef.setInput('variant', 'with-tagline');
    fixture.detectChanges();
    const tagline = fixture.debugElement.query(By.css('.lp-logo__tagline'));
    expect(tagline).withContext('expected tagline element').not.toBeNull();
    expect(tagline.nativeElement.textContent).toContain('Beauté');
  });

  it('does not show the tagline by default', () => {
    fixture.detectChanges();
    const tagline = fixture.debugElement.query(By.css('.lp-logo__tagline'));
    expect(tagline).toBeNull();
  });
});
```

- [ ] **Step 2: Run the test to see it fail**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/lp-logo.component.spec.ts'`
Expected: FAIL — `Cannot find module './lp-logo.component'`.

- [ ] **Step 3: Create the component TS**

Create `frontend/src/app/shared/uis/lp-logo/lp-logo.component.ts`:

```typescript
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type LpLogoVariant = 'default' | 'small' | 'with-tagline';

@Component({
  selector: 'app-lp-logo',
  standalone: true,
  templateUrl: './lp-logo.component.html',
  styleUrl: './lp-logo.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.lp-logo--small]': 'variant() === "small"',
    '[class.lp-logo--with-tagline]': 'variant() === "with-tagline"',
    'class': 'lp-logo',
  },
})
export class LpLogoComponent {
  readonly variant = input<LpLogoVariant>('default');
  protected readonly showTagline = computed(() => this.variant() === 'with-tagline');
}
```

- [ ] **Step 4: Create the component template**

Create `frontend/src/app/shared/uis/lp-logo/lp-logo.component.html`:

```html
<span class="lp-logo__lux">LUX</span>
<span class="lp-logo__pretty">Pretty</span>
@if (showTagline()) {
  <span class="lp-logo__tagline">Beauté · Luxembourg</span>
}
```

- [ ] **Step 5: Create the component styles**

Create `frontend/src/app/shared/uis/lp-logo/lp-logo.component.scss`:

```scss
:host.lp-logo {
  display: inline-flex;
  align-items: baseline;
  gap: 12px;
  text-decoration: none;
  line-height: 1;
  position: relative;
}

:host.lp-logo--with-tagline {
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.lp-logo__lux {
  font-family: 'Cormorant Garamond', Georgia, serif;
  font-size: 22px;
  font-weight: 500;
  letter-spacing: 0.25em;
  text-transform: uppercase;
  color: var(--pf-ink);
  transition: color 200ms ease;

  @media (min-width: 768px) {
    font-size: 26px;
  }
}

.lp-logo__pretty {
  font-family: 'Italiana', 'Cormorant Garamond', Georgia, serif;
  font-size: 22px;
  font-weight: 400;
  font-style: italic;
  color: var(--pf-rose);
  position: relative;

  @media (min-width: 768px) {
    font-size: 26px;
  }
}

/* Decorative gold underline under "Pretty" — default variant only */
:host.lp-logo:not(.lp-logo--small):not(.lp-logo--with-tagline) .lp-logo__pretty::after {
  content: '';
  position: absolute;
  left: 4%;
  right: 4%;
  bottom: -3px;
  height: 1px;
  background: var(--lp-accent-gold);
  opacity: 0.4;
}

/* Hover: LUX glows gold for 200ms */
:host.lp-logo:hover .lp-logo__lux {
  color: var(--lp-accent-gold);
}

/* Small variant — used in header left column on salon/pro pages */
:host.lp-logo--small .lp-logo__lux,
:host.lp-logo--small .lp-logo__pretty {
  font-size: 15px;

  @media (min-width: 768px) {
    font-size: 17px;
  }
}
:host.lp-logo--small {
  gap: 6px;
}

/* Tagline */
.lp-logo__tagline {
  font-family: 'Cormorant Garamond', Georgia, serif;
  font-size: 9px;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--lp-accent-gold);
  opacity: 0.85;
}
```

- [ ] **Step 6: Run the spec to see it pass**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/lp-logo.component.spec.ts'`
Expected: 4 specs pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/shared/uis/lp-logo/
git commit -m "feat(frontend): add lp-logo component with default/small/tagline variants"
```

---

## Task 6: Wire `<lp-logo>` into the header

**Files:**
- Modify: `frontend/src/app/shared/layout/header/header.html`
- Modify: `frontend/src/app/shared/layout/header/header.ts`
- Modify: `frontend/src/app/shared/layout/header/header.scss`
- Modify: `frontend/src/app/shared/layout/header/header.spec.ts`
- Modify: `frontend/public/i18n/fr.json` (add `nav.backToHome` key)
- Modify: `frontend/public/i18n/en.json` (add `nav.backToHome` key)

- [ ] **Step 1: Add `nav.backToHome` i18n key to French**

Edit `frontend/public/i18n/fr.json` — locate the `nav` object (search for `"nav": {`) and add the key `"backToHome": "Retour à l'accueil LuxPretty"` alongside other `nav.*` keys. Preserve JSON formatting (2-space indent, trailing comma rules).

- [ ] **Step 2: Add `nav.backToHome` i18n key to English**

Edit `frontend/public/i18n/en.json` — same nav object, add `"backToHome": "Back to LuxPretty home"`.

- [ ] **Step 3: Add a header spec for the small logo on salon pages**

Edit `frontend/src/app/shared/layout/header/header.spec.ts` — append a new `describe` block at the end (before the file's closing brace if any; this file already has multiple `describe` blocks). Use the existing `salonService` mock pattern. Add this block:

```typescript
describe('Header — small LuxPretty logo on salon pages', () => {
  let salonService: jasmine.SpyObj<SalonProfileService>;

  beforeEach(async () => {
    salonService = jasmine.createSpyObj('SalonProfileService', ['getProfile', 'getPublicSalon']);
    salonService.getPublicSalon.and.returnValue(of({ name: 'Le Salon Rose', slug: 'le-salon-rose' } as any));

    await TestBed.configureTestingModule({
      imports: [Header],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: SalonProfileService, useValue: salonService },
      ],
    }).compileComponents();
  });

  it('renders a small logo navigating to / when visiting a salon', async () => {
    const fixture = TestBed.createComponent(Header);
    // simulate navigation to a salon page
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/salon/le-salon-rose');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const smallLogoLink: HTMLAnchorElement | null =
      fixture.nativeElement.querySelector('a[data-testid="header-home-link"]');
    expect(smallLogoLink).withContext('expected small logo link').not.toBeNull();
    expect(smallLogoLink!.getAttribute('href')).toBe('/');
  });
});
```

(If the spec file imports above don't yet include `provideRouter`, `provideNoopAnimations`, `Router`, `of`, or `SalonProfileService`, add them. The existing file already imports most of these — verify before the test run.)

- [ ] **Step 4: Run the new spec to see it fail**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/header.spec.ts'`
Expected: the new test fails (`expected small logo link` not found).

- [ ] **Step 5: Update the header TS to import the new component**

Edit `frontend/src/app/shared/layout/header/header.ts` — add to the imports list of the `@Component` decorator:

```typescript
import { LpLogoComponent } from '../../uis/lp-logo/lp-logo.component';
```

And in the `imports` array of `@Component`:
```typescript
imports: [RouterLink, SidenavOverlay, MatMenuModule, MatButtonModule, MatIconModule, TranslocoPipe, LpLogoComponent],
```

Also: the Spec docstring at line 70-71 mentions "Pretty Face" — update it to "LuxPretty":
```typescript
// (around line 70 in header.ts)
// which used to make the brand fall back to "LuxPretty".
```

- [ ] **Step 6: Update the header template**

Edit `frontend/src/app/shared/layout/header/header.html`:

**(a)** In the **left column** (currently containing burger + manage back arrow + discover/about links), add the small logo after the desktop burger button. Wrap the existing `discover/about` links in a condition so they hide when there's a `headerBrand`. Replace the left-column block with:

```html
<div class="flex items-center gap-4">
  @if (isManagePage()) {
    <!-- Mobile: back arrow on manage/settings pages -->
    <button
      type="button"
      aria-label="Retour"
      (click)="goBack()"
      class="p-2 rounded-full hover:bg-neutral-100 focus:outline-none transition-colors duration-150 show-on-mobile-only"
    >
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="w-6 h-6">
        <path stroke-linecap="round" stroke-linejoin="round" d="M19 12H5M12 19l-7-7 7-7"/>
      </svg>
    </button>
  }
  <!-- Desktop: burger menu -->
  <button
    type="button"
    aria-label="Ouvrir le menu"
    (click)="toggleSidenav()"
    class="p-2 rounded-full hover:bg-neutral-100 focus:outline-none focus:ring-1 focus:ring-neutral-300 transition-colors duration-150 hide-on-mobile"
  >
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="w-6 h-6">
      <path stroke-linecap="round" stroke-linejoin="round" d="M3 6h18M3 12h18M3 18h18"/>
    </svg>
  </button>
  @if (headerBrand()) {
    <!-- Salon/pro context: small logo on the left so users can return home -->
    <a
      routerLink="/"
      data-testid="header-home-link"
      [attr.aria-label]="'nav.backToHome' | transloco"
      class="hide-on-mobile"
    >
      <app-lp-logo variant="small" />
    </a>
  } @else if (!isPro()) {
    <a routerLink="/discover" class="hidden md:inline text-[12px] tracking-widest uppercase text-neutral-700 hover:underline underline-offset-4">{{ 'nav.discover' | transloco }}</a>
    <a routerLink="/about" class="hidden md:inline text-[12px] tracking-widest uppercase text-neutral-700 hover:underline underline-offset-4">{{ 'nav.about' | transloco }}</a>
  }
</div>
```

**(b)** In the **center column**, replace the `<span class="brand-pretty">Pretty</span><span class="brand-face">Face</span>` markup with `<app-lp-logo />`. The whole center block becomes:

```html
<div class="justify-self-center">
  @if (headerBrand(); as brand) {
    <a [routerLink]="['/salon', brand.slug]" class="salon-brand">
      {{ brand.name }}
    </a>
  } @else {
    <a routerLink="/" class="select-none" data-testid="header-home-link">
      <app-lp-logo />
    </a>
  }
</div>
```

- [ ] **Step 7: Clean up obsolete header SCSS**

Edit `frontend/src/app/shared/layout/header/header.scss` — delete the now-unused blocks `.brand-logo`, `.brand-pretty`, `.brand-face` (the styles are now encapsulated in `lp-logo.component.scss`). Keep `.salon-brand`, `.view-salon-btn`, `.notification-badge`, `.lang-*`, `@keyframes badgeAppear`, `.hide-on-mobile`, `.show-on-mobile-only`.

- [ ] **Step 8: Run the header tests to see them pass**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/header.spec.ts'`
Expected: all specs pass, including the new "small LuxPretty logo on salon pages" test.

- [ ] **Step 9: Run the full frontend test suite**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless`
Expected: all green. If any spec asserts on the old `.brand-pretty` / `.brand-face` markup, update the assertion to look for `app-lp-logo` instead.

- [ ] **Step 10: Commit**

```bash
git add frontend/
git commit -m "feat(header): wire lp-logo + small logo on salon/pro pages for home navigation"
```

---

## Task 7: Replace "Pretty Face" in i18n files

**Files:**
- Modify: `frontend/public/i18n/fr.json` (17 occurrences)
- Modify: `frontend/public/i18n/en.json` (17 occurrences)
- Modify: `frontend/src/app/i18n/transloco-http.loader.ts` (2 occurrences in embedded SSR fallback)

- [ ] **Step 1: Replace in i18n JSON files**

Run from repo root:
```bash
sed -i '' 's/Pretty Face/LuxPretty/g' frontend/public/i18n/fr.json frontend/public/i18n/en.json
```

- [ ] **Step 2: Replace in the SSR transloco loader**

Edit `frontend/src/app/i18n/transloco-http.loader.ts` — change the two embedded fallbacks:
```typescript
fr: { app: { title: 'LuxPretty' }, ... }
en: { app: { title: 'LuxPretty' }, ... }
```

- [ ] **Step 3: Verify no `Pretty Face` left in i18n**

Run: `grep -rn "Pretty Face" frontend/public/i18n frontend/src/app/i18n`
Expected: zero output.

- [ ] **Step 4: Run frontend tests**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless`
Expected: green. (Some test specs hardcode i18n strings in their fixtures — update those in Task 9.)

- [ ] **Step 5: Commit**

```bash
git add frontend/
git commit -m "i18n: replace Pretty Face with LuxPretty in fr/en translations + SSR fallback"
```

---

## Task 8: Replace remaining hardcoded "Pretty Face" strings in frontend

**Files:**
- Modify: `frontend/src/app/pages/about/about.html`
- Modify: `frontend/src/app/pages/salon/pc-view/salon-page-pc.component.html`
- Modify: `frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts`
- Modify: `frontend/package.json`

- [ ] **Step 1: Update `about.html`**

Edit `frontend/src/app/pages/about/about.html` line 3:
```html
<p>Bienvenue chez LuxPretty. Nous proposons des soins relaxants et des prestations bien-être.</p>
```

- [ ] **Step 2: Update `salon-page-pc.component.html`**

Edit `frontend/src/app/pages/salon/pc-view/salon-page-pc.component.html` line 195 — change `Pretty Face ✿` to `LuxPretty ✿`.

- [ ] **Step 3: Update `salon-posts-viewer.component.ts`**

Edit `frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts` around line 943 — change the share fallback:
```typescript
navigator.share({ title: post.caption ?? 'LuxPretty', url }).catch(() => {});
```

- [ ] **Step 4: Update `package.json` name**

Edit `frontend/package.json` line 2:
```json
"name": "luxpretty-web",
```

- [ ] **Step 5: Verify no hardcoded brand strings remain in frontend source**

Run: `grep -rn "Pretty Face\|prettyface\|PrettyFace" frontend/src frontend/public frontend/package.json`
Expected: zero output. If `header.spec.ts` still has comments mentioning old behavior, those get updated in the next task.

- [ ] **Step 6: Run frontend tests**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless`
Expected: green.

- [ ] **Step 7: Commit**

```bash
git add frontend/
git commit -m "chore(frontend): replace remaining Pretty Face hardcodes with LuxPretty"
```

---

## Task 9: Update test fixtures and spec strings

**Files:**
- Modify: `frontend/src/app/features/salon-profile/salon-profile.component.spec.ts:22,138`
- Modify: `frontend/src/app/pages/auth/register/register.component.spec.ts:14`
- Modify: `frontend/src/app/shared/layout/header/header.spec.ts:77,166` (comments)

- [ ] **Step 1: Update salon-profile spec fixture**

Edit `frontend/src/app/features/salon-profile/salon-profile.component.spec.ts`:
- Line 22: change the fixture name from `'Pretty Face Atelier'` to `'LuxPretty Atelier'`
- Line 138: same — update the assertion `expect((component as any).name()).toBe('LuxPretty Atelier');`

- [ ] **Step 2: Update register spec fixture**

Edit `frontend/src/app/pages/auth/register/register.component.spec.ts` line 14 — the `auth.register.subtitle` translation fixture should match the new i18n: `'Join LuxPretty'`.

- [ ] **Step 3: Update header spec comments**

Edit `frontend/src/app/shared/layout/header/header.spec.ts` — replace the two doc comments referencing "Pretty Face":
- Line ~77: `* used to flicker back to "LuxPretty" because each effect re-fetched the`
- Line ~166: `* to "LuxPretty". With the fix, an error keeps the cached name.`

- [ ] **Step 4: Run all frontend tests**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless`
Expected: green.

- [ ] **Step 5: Final grep across the frontend**

Run: `grep -rn "Pretty Face\|PrettyFace\|prettyface" frontend/`
Expected: zero matches.

- [ ] **Step 6: Commit**

```bash
git add frontend/
git commit -m "test(frontend): align fixtures and comments with LuxPretty rebrand"
```

---

## Task 10: Update root documentation

**Files:**
- Modify: `CLAUDE.md`
- Modify: `AGENTS.md`
- Modify: `OAUTH2_SETUP.md`
- Modify: `.env.example`

- [ ] **Step 1: Replace "Pretty Face" → "LuxPretty" in docs**

Run from repo root:
```bash
sed -i '' 's/Pretty Face/LuxPretty/g' CLAUDE.md AGENTS.md OAUTH2_SETUP.md .env.example
```

- [ ] **Step 2: Replace `com/prettyface/` paths in docs**

Run from repo root:
```bash
sed -i '' 's|com/prettyface|com/luxpretty|g' CLAUDE.md OAUTH2_SETUP.md
```

- [ ] **Step 3: Verify**

Run: `grep -rn "Pretty Face\|prettyface" CLAUDE.md AGENTS.md OAUTH2_SETUP.md .env.example README.md`
Expected: zero output.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md AGENTS.md OAUTH2_SETUP.md .env.example
git commit -m "docs: rebrand Pretty Face references to LuxPretty"
```

---

## Task 11: Final cross-repo verification

**Files:** none modified — verification only.

- [ ] **Step 1: Final cross-repo grep (excluding legacy artifacts)**

Run from repo root:
```bash
grep -rn "Pretty Face\|prettyface\|PrettyFace\|pretty-face" \
  --include="*.ts" --include="*.html" --include="*.scss" --include="*.java" \
  --include="*.properties" --include="*.json" --include="*.md" --include="*.xml" \
  --include="*.yml" \
  --exclude-dir=node_modules --exclude-dir=target --exclude-dir=.git \
  --exclude-dir=_bmad --exclude-dir=_bmad-output --exclude-dir=memory \
  --exclude-dir=mockups \
  . 2>/dev/null | grep -v "mockup-" | grep -v "docs/superpowers/specs/" | grep -v "docs/superpowers/plans/"
```

Expected: zero matches. (Specs and plans intentionally retain "Pretty Face" as historical context — that's why they're excluded.)

- [ ] **Step 2: Backend full test run**

Run: `cd backend && mvn clean test -q`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 3: Frontend full test run**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless`
Expected: all specs pass.

- [ ] **Step 4: Frontend production build**

Run: `cd frontend && npm run build`
Expected: build succeeds, no SCSS errors, output in `dist/`.

- [ ] **Step 5: Manual smoke test (UI verification)**

Run backend in one terminal:
```bash
cd backend && mvn spring-boot:run
```
Run frontend in another terminal:
```bash
cd frontend && npm start
```

Open http://localhost:4200 and verify:
- [ ] Home: header center shows `LUX Pretty` logo (Cormorant + Italiana, gold underline visible under "Pretty"). Browser tab title is `LuxPretty`.
- [ ] Hover the logo: "LUX" briefly turns gold.
- [ ] Click logo → stays on `/` (already home).
- [ ] Navigate to `/discover` → click any salon → on the salon page, header shows: small `LUX Pretty` on the left **and** the salon name centered. Discover/About text links no longer in the left column.
- [ ] Click the small logo on the left → returns to `/`.
- [ ] Resize the window to <767px (mobile) on the salon page → small logo is hidden, only the salon name remains centered (sidenav burger still works for going home).
- [ ] Switch language to English (lang-switcher) → no remaining French/English "Pretty Face" strings on home, about, footer, register subtitle, etc.
- [ ] Login as a pro account → header shows small LuxPretty logo on left + their salon name centered.

If any item fails, fix it and re-test before continuing.

- [ ] **Step 6: Commit any fixes from smoke test**

If steps above required fixes, commit them:
```bash
git add -A
git commit -m "fix: address LuxPretty rebrand smoke-test findings"
```

---

## Task 12: Open the PR

- [ ] **Step 1: Push the branch**

Run: `git push -u origin feat/luxpretty-rebrand`

- [ ] **Step 2: Create the PR**

Run:
```bash
gh pr create --title "feat: rebrand Pretty Face → LuxPretty" --body "$(cat <<'EOF'
## Summary
- Rename Java package `com.prettyface.app` → `com.luxpretty.app` (289 files)
- New `<app-lp-logo>` component (variants: default / small / with-tagline) — Cormorant Garamond + Italiana, palette rose conservée + accent or `#C9A961`
- Header refondu : logo small en colonne gauche sur les pages salon/pro (desktop) → permet le retour à l'accueil LuxPretty
- i18n FR/EN, email templates, doc, env example tous mis à jour

## ⚠️ Manual action post-merge
Renommer "Pretty Face" → "LuxPretty" dans **Google Cloud Console → OAuth consent screen** (le client_id reste identique, seul le nom affiché change).

## Test plan
- [x] `mvn clean test` (backend)
- [x] `npm test` (frontend)
- [x] `npm run build` (frontend prod)
- [x] Smoke manuel : home → salon → retour home via logo small (desktop)
- [x] Smoke manuel : mobile salon → logo small masqué, sidenav OK
- [x] Smoke manuel : switch FR/EN, aucune chaîne "Pretty Face" résiduelle

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Save the PR URL**

Note the URL printed by `gh pr create` — share it with the user.

---

## Self-review notes

**Spec coverage:**
- ✅ Identité visuelle (Section 1) → Tasks 4 + 5
- ✅ Header refondu (Section 2) → Task 6
- ✅ Périmètre backend (Section 3) → Tasks 1, 2, 3
- ✅ Périmètre frontend (Section 3) → Tasks 4, 5, 6, 7, 8, 9
- ✅ Doc (Section 3) → Task 10
- ✅ Stratégie d'exécution (Section 4) → ordre des tasks 1→12
- ✅ Validation (Section 4) → Task 11
- ✅ Action manuelle Google Cloud Console → mentionnée dans la PR (Task 12)

**Type consistency:** `LpLogoComponent` / selector `app-lp-logo` / variant type `LpLogoVariant` are used identically in component, template, header import, and tests.

**No placeholders:** every code step shows the exact code; every command is concrete.
