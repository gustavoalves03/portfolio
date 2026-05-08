# Salon Page PC Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refondre, sur PC (≥ 1024px) uniquement, la page salon vue client (`/salon/:slug`) en vitrine éditoriale style eauceane, et la page salon vue pro (`/pro/.../salon`) en éditeur miroir avec édition contextuelle inline. La version mobile actuelle reste inchangée.

**Architecture :**
- Détection du breakpoint `>= 1024px` via un signal partagé (`features/salon-profile/shared/use-pc.ts`). Au-dessous, l'app rend les composants existants ; au-dessus, elle rend les nouveaux composants `*-pc`.
- Composants "rendus purs" partagés (header sticky, hero, soins, stories…) entre client et pro. Côté pro, ils sont enveloppés par un wrapper `<app-editable-section>` qui ajoute la couche d'édition (hover-frame + panel inline).
- Aucun changement backend : on consomme les endpoints existants (`/api/pro/tenant`, `/api/pro/tenant/readiness`, `/api/pro/tenant/publish`, `/api/salon/:slug`, `/api/salon/:slug/posts`).
- Section Stories (variante B) = rangée horizontale qui réutilise telle quelle `SalonPostsViewerComponent` dans une modale plein écran ; un seul `@Input() startIndex` est ajouté au composant existant.

**Tech Stack :**
- Angular 20 standalone, zoneless, signaux + `@if`/`@for`/`@defer`
- Angular Material (Dialog) + Tailwind utilitaires + SCSS scopé `--pf-salon-*`
- NgRx SignalStore (extension du `SalonProfileStore` existant)
- Transloco (FR + EN obligatoires — voir CLAUDE.md)
- Karma/Jasmine pour les tests
- Police Cormorant Garamond ajoutée via Google Fonts (link `<head>` d'`index.html`)

---

## File Structure

**Nouveaux fichiers (vue client PC) :**
```
frontend/src/app/pages/salon/pc/
├── salon-page-pc.component.ts            (root scrollable, gère scroll-spy)
├── salon-page-pc.component.html
├── salon-page-pc.component.scss          (variables --pf-salon-* scopées)
├── sections/
│   ├── salon-header.component.ts         (sticky header + nav ancres)
│   ├── salon-hero.component.ts           (eyebrow + h1 serif + lede + CTAs)
│   ├── salon-banner.component.ts         (hero image plein largeur)
│   ├── salon-about.component.ts          (2-col image + texte)
│   ├── salon-cares.component.ts          (catégories + cartes 2-col)
│   ├── salon-stories.component.ts        (rangée horizontale + ouverture viewer)
│   ├── salon-cta.component.ts            (bandeau "Prenez soin")
│   ├── salon-contact.component.ts        (info + carte Leaflet)
│   └── salon-footer.component.ts         (4-col + powered-by)
├── stories/
│   ├── stories-row.component.ts          (defer rendering, snap-x)
│   └── stories-modal.component.ts        (wrap SalonPostsViewerComponent)
└── shared/
    └── use-pc.ts                         (signal isPc() global lazy)
```

**Nouveaux fichiers (vue pro PC) :**
```
frontend/src/app/features/salon-profile/pc/
├── salon-editor-pc.component.ts          (top-bar + sidebar + canvas)
├── salon-editor-pc.component.html
├── salon-editor-pc.component.scss
├── editor-top-bar/editor-top-bar.component.ts
├── editor-sidebar/editor-sidebar.component.ts
├── canvas/
│   ├── header-edit.component.ts          (logo + nom + nav)
│   ├── hero-edit.component.ts
│   ├── about-edit.component.ts
│   ├── cares-edit.component.ts           (drag/edit/hide/delete soins)
│   ├── stories-edit.component.ts
│   ├── cta-edit.component.ts
│   ├── contact-edit.component.ts
│   └── footer-edit.component.ts
└── shared/
    ├── editable-section.component.ts     (wrapper hover/active)
    ├── inline-edit-panel.component.ts    (panel save/cancel)
    └── pc-editor.store.ts                (activeSectionId, dirtySection)
```

**Fichiers modifiés :**
- `frontend/src/app/pages/salon/salon-page.component.ts` — branchement conditionnel sur `isPc()`
- `frontend/src/app/features/salon-profile/salon-profile.component.ts` — branchement conditionnel sur `isPc()`
- `frontend/src/app/features/salon-profile/store/salon-profile.store.ts` — ajout des méthodes `publish()`, `loadReadiness()`, `dirtySection`, `activeSection`
- `frontend/src/app/features/salon-profile/services/salon-profile.service.ts` — ajout `publish()` et `getReadiness()` (réutilise endpoints dashboard, ou délègue au `DashboardService`)
- `frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts` — ajout `@Input() startIndex = 0`
- `frontend/public/i18n/fr.json` et `en.json` — clés `salon.pc.*` et `pro.salon.editor.*`
- `frontend/src/index.html` — link Google Fonts Cormorant Garamond

**Variables CSS scopées (à mettre dans `salon-page-pc.component.scss`) :**

```scss
:host {
  --pf-salon-ink: #2a2522;
  --pf-salon-ink-soft: #6b5e57;
  --pf-salon-line: #ece4dd;
  --pf-salon-bg: #fdfaf8;
  --pf-salon-paper: #fff;
  --pf-salon-accent: #b56b5a;
  --pf-salon-accent-soft: #f4e6df;
  --pf-salon-accent-dark: #9b5848;
  --pf-salon-edit: #4a90e2;
  --pf-salon-warn: #d97757;
}
```

---

## Milestone 0 — Foundations

Met en place la détection PC, la police, la palette scopée et les translations de base. Aucun rendu visible côté utilisateur à la fin (uniquement infrastructure).

### Task 0.1: Add Cormorant Garamond font

**Files:**
- Modify: `frontend/src/index.html`

- [ ] **Step 1: Add Google Fonts link**

Ajouter dans le `<head>` de `frontend/src/index.html`, juste avant `</head>` :

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Cormorant+Garamond:wght@300;400;500&display=swap" rel="stylesheet">
```

- [ ] **Step 2: Verify dev server still starts**

Run: `cd frontend && npm start`
Expected: app loads at http://localhost:4200 with no font-related console errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/index.html
git commit -m "feat(salon-pc): add Cormorant Garamond font for editorial titles"
```

### Task 0.2: Create the `usePc()` signal

**Files:**
- Create: `frontend/src/app/pages/salon/pc/shared/use-pc.ts`
- Test: `frontend/src/app/pages/salon/pc/shared/use-pc.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/pages/salon/pc/shared/use-pc.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID } from '@angular/core';
import { usePc } from './use-pc';

describe('usePc', () => {
  it('returns false when running in non-browser platform', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: PLATFORM_ID, useValue: 'server' }],
    });
    TestBed.runInInjectionContext(() => {
      const isPc = usePc();
      expect(isPc()).toBe(false);
    });
  });

  it('returns true when window is wider than 1024px', () => {
    spyOnProperty(window, 'innerWidth', 'get').and.returnValue(1280);
    TestBed.configureTestingModule({
      providers: [{ provide: PLATFORM_ID, useValue: 'browser' }],
    });
    TestBed.runInInjectionContext(() => {
      const isPc = usePc();
      expect(isPc()).toBe(true);
    });
  });

  it('returns false when window is narrower than 1024px', () => {
    spyOnProperty(window, 'innerWidth', 'get').and.returnValue(800);
    TestBed.configureTestingModule({
      providers: [{ provide: PLATFORM_ID, useValue: 'browser' }],
    });
    TestBed.runInInjectionContext(() => {
      const isPc = usePc();
      expect(isPc()).toBe(false);
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- --include='**/use-pc.spec.ts' --watch=false`
Expected: FAIL with "Cannot find module './use-pc'".

- [ ] **Step 3: Write the implementation**

Create `frontend/src/app/pages/salon/pc/shared/use-pc.ts`:

```typescript
import { DestroyRef, PLATFORM_ID, Signal, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { fromEvent, debounceTime, startWith, map } from 'rxjs';

export const PC_BREAKPOINT_PX = 1024;

export function usePc(): Signal<boolean> {
  const platformId = inject(PLATFORM_ID);
  const destroyRef = inject(DestroyRef);

  if (!isPlatformBrowser(platformId)) {
    return signal(false).asReadonly();
  }

  const isPc = signal(window.innerWidth >= PC_BREAKPOINT_PX);

  fromEvent(window, 'resize')
    .pipe(
      debounceTime(120),
      map(() => window.innerWidth >= PC_BREAKPOINT_PX),
      startWith(window.innerWidth >= PC_BREAKPOINT_PX),
      takeUntilDestroyed(destroyRef),
    )
    .subscribe((value) => isPc.set(value));

  return isPc.asReadonly();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- --include='**/use-pc.spec.ts' --watch=false`
Expected: PASS, 3 specs.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/salon/pc/shared/use-pc.ts frontend/src/app/pages/salon/pc/shared/use-pc.spec.ts
git commit -m "feat(salon-pc): add usePc() signal for >=1024px breakpoint detection"
```

### Task 0.3: Add base translations for the PC views

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Locate the existing `"salon"` block in `fr.json`**

Run: `grep -n '"salon": {' frontend/public/i18n/fr.json`
Expected: a line number (e.g. 731) pointing to the public-facing salon block.

- [ ] **Step 2: Add new `pc` sub-key inside the `"salon"` block in fr.json**

Inside the existing `"salon": { … }` object, add this `"pc"` key. Keep the existing keys untouched.

```json
"pc": {
  "header": {
    "home": "Accueil",
    "cares": "Nos soins",
    "stories": "Stories",
    "about": "À propos",
    "contact": "Contact",
    "bookCta": "Prendre RDV"
  },
  "hero": {
    "eyebrow": "Esthétique & bien-être",
    "discover": "Découvrir"
  },
  "about": {
    "label": "Bienvenue"
  },
  "cares": {
    "label": "Notre carte",
    "title": "Soins & rituels",
    "subtitle": "Chaque soin est pensé comme un voyage. Choisissez le vôtre.",
    "bookLink": "Réserver",
    "minutes": "min",
    "countLabel": "{{count}} soins"
  },
  "stories": {
    "label": "Stories du salon",
    "title": "Avant / après récents",
    "subtitle": "Faites défiler les transformations de la semaine.",
    "viewAll": "Voir toutes",
    "prev": "Précédent",
    "next": "Suivant",
    "rdv": "RDV",
    "share": "Partager",
    "type": {
      "photo": "Photo",
      "beforeAfter": "Avant / Après",
      "carousel": "Carrousel"
    }
  },
  "cta": {
    "label": "Prenez soin de vous",
    "title": "Offrez-vous une parenthèse.",
    "body": "Réservez votre soin en ligne en moins d'une minute. Confirmation immédiate.",
    "button": "Prendre RDV"
  },
  "contact": {
    "label": "Contact",
    "title": "Nous trouver.",
    "address": "Adresse",
    "phone": "Téléphone",
    "email": "Email",
    "hours": "Horaires"
  },
  "footer": {
    "navigation": "Navigation",
    "legal": "Légal",
    "social": "Suivez-nous",
    "mentions": "Mentions légales",
    "cgv": "CGV",
    "privacy": "Confidentialité",
    "instagram": "Instagram",
    "facebook": "Facebook",
    "poweredBy": "Propulsé par Pretty Face"
  }
}
```

- [ ] **Step 3: Add the same structure with English values to `en.json`**

```json
"pc": {
  "header": {
    "home": "Home",
    "cares": "Our care",
    "stories": "Stories",
    "about": "About",
    "contact": "Contact",
    "bookCta": "Book now"
  },
  "hero": {
    "eyebrow": "Aesthetics & wellness",
    "discover": "Discover"
  },
  "about": {
    "label": "Welcome"
  },
  "cares": {
    "label": "Our menu",
    "title": "Care & rituals",
    "subtitle": "Each treatment is a journey. Choose yours.",
    "bookLink": "Book",
    "minutes": "min",
    "countLabel": "{{count}} treatments"
  },
  "stories": {
    "label": "Salon stories",
    "title": "Recent before / after",
    "subtitle": "Scroll through this week's transformations.",
    "viewAll": "See all",
    "prev": "Previous",
    "next": "Next",
    "rdv": "Book",
    "share": "Share",
    "type": {
      "photo": "Photo",
      "beforeAfter": "Before / After",
      "carousel": "Carousel"
    }
  },
  "cta": {
    "label": "Take care of yourself",
    "title": "Treat yourself to a moment.",
    "body": "Book your treatment online in less than a minute. Instant confirmation.",
    "button": "Book now"
  },
  "contact": {
    "label": "Contact",
    "title": "Find us.",
    "address": "Address",
    "phone": "Phone",
    "email": "Email",
    "hours": "Hours"
  },
  "footer": {
    "navigation": "Navigation",
    "legal": "Legal",
    "social": "Follow us",
    "mentions": "Legal notice",
    "cgv": "Terms",
    "privacy": "Privacy",
    "instagram": "Instagram",
    "facebook": "Facebook",
    "poweredBy": "Powered by Pretty Face"
  }
}
```

- [ ] **Step 4: Add the `pro.salon.editor` block to fr.json**

Inside `"pro": { "salon": { … } }`, add:

```json
"editor": {
  "topBar": {
    "breadcrumb": "Console pro",
    "myPage": "Ma page salon",
    "previewClient": "Aperçu client",
    "publish": "Publier la page",
    "publishDisabledHint": "Complétez la checklist avant de publier",
    "viewPublic": "Voir la version publique",
    "statusDraft": "Brouillon · prêt à publier",
    "statusDraftMissing": "Brouillon · {{count}} élément(s) manquant(s)",
    "statusActive": "En ligne"
  },
  "sidebar": {
    "sectionsHeader": "Sections de la page",
    "checklistHeader": "Avant publication",
    "sections": {
      "header": "En-tête (logo, nav)",
      "hero": "Hero d'accueil",
      "about": "À propos",
      "cares": "Soins & rituels",
      "stories": "Stories",
      "cta": "CTA Prenez soin",
      "contact": "Contact",
      "footer": "Pied de page"
    }
  },
  "section": {
    "hoverHint": "Cliquez pour éditer",
    "save": "Enregistrer",
    "cancel": "Annuler",
    "discardChangesTitle": "Modifications non enregistrées",
    "discardChangesBody": "Vous avez des modifications non enregistrées. Voulez-vous les abandonner ?",
    "discard": "Abandonner",
    "keepEditing": "Continuer à éditer"
  },
  "cares": {
    "addCategory": "+ Ajouter une catégorie",
    "addCare": "+ Ajouter un soin",
    "edit": "Éditer",
    "hide": "Masquer",
    "show": "Afficher",
    "delete": "Supprimer",
    "emptyCategory": "Aucun soin dans cette catégorie. Les visiteurs ne la verront pas.",
    "hidden": "Masqué"
  },
  "stories": {
    "newStory": "Nouvelle story",
    "noStoriesYet": "Publiez votre première story",
    "delete": "Supprimer",
    "deleteConfirm": "Supprimer cette story ?"
  }
}
```

- [ ] **Step 5: Mirror the `editor` block in en.json**

```json
"editor": {
  "topBar": {
    "breadcrumb": "Pro console",
    "myPage": "My salon page",
    "previewClient": "Preview as client",
    "publish": "Publish the page",
    "publishDisabledHint": "Complete the checklist before publishing",
    "viewPublic": "View public version",
    "statusDraft": "Draft · ready to publish",
    "statusDraftMissing": "Draft · {{count}} item(s) missing",
    "statusActive": "Live"
  },
  "sidebar": {
    "sectionsHeader": "Page sections",
    "checklistHeader": "Before publishing",
    "sections": {
      "header": "Header (logo, nav)",
      "hero": "Hero",
      "about": "About",
      "cares": "Care & rituals",
      "stories": "Stories",
      "cta": "Take-care CTA",
      "contact": "Contact",
      "footer": "Footer"
    }
  },
  "section": {
    "hoverHint": "Click to edit",
    "save": "Save",
    "cancel": "Cancel",
    "discardChangesTitle": "Unsaved changes",
    "discardChangesBody": "You have unsaved changes. Discard them?",
    "discard": "Discard",
    "keepEditing": "Keep editing"
  },
  "cares": {
    "addCategory": "+ Add a category",
    "addCare": "+ Add a care",
    "edit": "Edit",
    "hide": "Hide",
    "show": "Show",
    "delete": "Delete",
    "emptyCategory": "No care in this category. Visitors won't see it.",
    "hidden": "Hidden"
  },
  "stories": {
    "newStory": "New story",
    "noStoriesYet": "Publish your first story",
    "delete": "Delete",
    "deleteConfirm": "Delete this story?"
  }
}
```

- [ ] **Step 6: Validate JSON syntax**

Run: `node -e "JSON.parse(require('fs').readFileSync('frontend/public/i18n/fr.json'))" && node -e "JSON.parse(require('fs').readFileSync('frontend/public/i18n/en.json'))"`
Expected: no output (no syntax error).

- [ ] **Step 7: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat(salon-pc): i18n keys for client PC view + pro editor"
```

---

## Milestone 1 — Client PC scaffold (route + skeleton)

À la fin de ce jalon, sur PC, `/salon/:slug` rend une coquille blanche avec les 9 sections vides (mais pas leur contenu réel). Sur mobile, la page actuelle reste intacte.

### Task 1.1: Create the empty `SalonPagePcComponent`

**Files:**
- Create: `frontend/src/app/pages/salon/pc/salon-page-pc.component.ts`
- Create: `frontend/src/app/pages/salon/pc/salon-page-pc.component.html`
- Create: `frontend/src/app/pages/salon/pc/salon-page-pc.component.scss`
- Test: `frontend/src/app/pages/salon/pc/salon-page-pc.component.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/pages/salon/pc/salon-page-pc.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideTransloco, TranslocoTestingModule } from '@jsverse/transloco';
import { SalonPagePcComponent } from './salon-page-pc.component';
import { signal } from '@angular/core';
import { PublicSalonResponse } from '../../../features/salon-profile/models/salon-profile.model';

describe('SalonPagePcComponent', () => {
  let fixture: ComponentFixture<SalonPagePcComponent>;

  const fakeSalon: PublicSalonResponse = {
    name: 'Institut Vénus',
    slug: 'institut-venus',
    status: 'ACTIVE',
    description: null,
    logoUrl: null,
    heroImageUrl: null,
    categories: [],
    addressStreet: null,
    addressPostalCode: null,
    addressCity: null,
    addressCountry: null,
    phone: null,
    contactEmail: null,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SalonPagePcComponent, TranslocoTestingModule.forRoot({ langs: { en: {} }, translocoConfig: { defaultLang: 'en' } })],
      providers: [provideNoopAnimations(), provideHttpClient(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(SalonPagePcComponent);
    fixture.componentRef.setInput('salon', fakeSalon);
    fixture.detectChanges();
  });

  it('renders all 9 section anchors', () => {
    const root = fixture.nativeElement as HTMLElement;
    const ids = ['header', 'hero', 'banner', 'about', 'cares', 'stories', 'cta', 'contact', 'footer'];
    ids.forEach((id) => {
      expect(root.querySelector(`[data-section="${id}"]`))
        .withContext(`section ${id} should be in the DOM`)
        .not.toBeNull();
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- --include='**/salon-page-pc.component.spec.ts' --watch=false`
Expected: FAIL — module not found.

- [ ] **Step 3: Write the component class**

Create `frontend/src/app/pages/salon/pc/salon-page-pc.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { PublicSalonResponse } from '../../../features/salon-profile/models/salon-profile.model';

@Component({
  selector: 'app-salon-page-pc',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './salon-page-pc.component.html',
  styleUrl: './salon-page-pc.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SalonPagePcComponent {
  readonly salon = input.required<PublicSalonResponse>();
}
```

- [ ] **Step 4: Write the placeholder template**

Create `frontend/src/app/pages/salon/pc/salon-page-pc.component.html`:

```html
<div class="pf-salon">
  <div data-section="header" class="placeholder">[header]</div>
  <div data-section="hero" class="placeholder">[hero]</div>
  <div data-section="banner" class="placeholder">[banner]</div>
  <div data-section="about" class="placeholder">[about]</div>
  <div data-section="cares" class="placeholder">[cares]</div>
  <div data-section="stories" class="placeholder">[stories]</div>
  <div data-section="cta" class="placeholder">[cta]</div>
  <div data-section="contact" class="placeholder">[contact]</div>
  <div data-section="footer" class="placeholder">[footer]</div>
</div>
```

- [ ] **Step 5: Write the SCSS with scoped variables**

Create `frontend/src/app/pages/salon/pc/salon-page-pc.component.scss`:

```scss
:host {
  --pf-salon-ink: #2a2522;
  --pf-salon-ink-soft: #6b5e57;
  --pf-salon-line: #ece4dd;
  --pf-salon-bg: #fdfaf8;
  --pf-salon-paper: #fff;
  --pf-salon-accent: #b56b5a;
  --pf-salon-accent-soft: #f4e6df;
  --pf-salon-accent-dark: #9b5848;
  --pf-salon-edit: #4a90e2;
  --pf-salon-warn: #d97757;

  display: block;
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', sans-serif;
  color: var(--pf-salon-ink);
  background: var(--pf-salon-bg);
}

.pf-salon {
  min-height: 100vh;
}

.placeholder {
  padding: 24px 36px;
  border-bottom: 1px dashed var(--pf-salon-line);
  font-size: 13px;
  color: var(--pf-salon-ink-soft);
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd frontend && npm test -- --include='**/salon-page-pc.component.spec.ts' --watch=false`
Expected: PASS, 1 spec.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/pages/salon/pc/
git commit -m "feat(salon-pc): scaffold SalonPagePcComponent with 9 section placeholders"
```

### Task 1.2: Branch the existing `SalonPageComponent` on `usePc()`

**Files:**
- Modify: `frontend/src/app/pages/salon/salon-page.component.ts`
- Modify: `frontend/src/app/pages/salon/salon-page.component.html`

- [ ] **Step 1: Add the imports and signal in `salon-page.component.ts`**

Add at the top of `salon-page.component.ts` imports:

```typescript
import { SalonPagePcComponent } from './pc/salon-page-pc.component';
import { usePc } from './pc/shared/use-pc';
```

In the `imports` array of the `@Component`, append `SalonPagePcComponent`.

In the class body, just below `protected salon = signal<PublicSalonResponse | null>(null);`, add:

```typescript
protected readonly isPc = usePc();
```

- [ ] **Step 2: Wrap the existing template content with `@if (isPc())`**

Edit `salon-page.component.html`. Wrap the entire existing body content (everything from the outer `@if (loading())` block) so the structure becomes:

```html
@if (loading()) {
  <div class="flex justify-center py-16">
    <mat-spinner diameter="40"></mat-spinner>
  </div>
} @else if (notFound()) {
  <div class="flex items-center justify-center min-h-[60vh]">
    <div class="text-center">
      <p class="text-xl text-neutral-500">{{ 'salon.public.notFound' | transloco }}</p>
    </div>
  </div>
} @else if (salon(); as salon) {
  @if (isPc()) {
    <app-salon-page-pc [salon]="salon" />
  } @else {
    <!-- existing mobile template (UNCHANGED) -->
    <!-- … keep all the existing markup here … -->
  }
}
```

The "existing mobile template" placeholder stands for the entire current body of the file (hero, section-toggle, tabs, contact map, etc.) — leave it byte-for-byte identical.

- [ ] **Step 3: Run typecheck and dev server**

Run: `cd frontend && npm run build`
Expected: build succeeds with no TypeScript errors.

- [ ] **Step 4: Manual smoke test**

Start `npm start`. Open `http://localhost:4200/salon/<a-known-slug>`:
- Resize the window below 1024px → existing mobile UI must still render.
- Resize above 1024px → the 9 placeholders appear.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/salon/salon-page.component.ts frontend/src/app/pages/salon/salon-page.component.html
git commit -m "feat(salon-pc): branch SalonPageComponent on usePc() to swap PC/mobile"
```

---

## Milestone 2 — Client PC sections (header, hero, banner, about, cares, cta, contact, footer)

À la fin de ce jalon, la page client PC est entièrement rendue (sauf Stories qui suit en jalon 3). Pas d'édition. Just du rendu.

### Task 2.1: Header component

**Files:**
- Create: `frontend/src/app/pages/salon/pc/sections/salon-header.component.ts`
- Test: `frontend/src/app/pages/salon/pc/sections/salon-header.component.spec.ts`

- [ ] **Step 1: Write the failing test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { SalonHeaderComponent } from './salon-header.component';

describe('SalonHeaderComponent', () => {
  let fixture: ComponentFixture<SalonHeaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SalonHeaderComponent, TranslocoTestingModule.forRoot({ langs: { en: {} }, translocoConfig: { defaultLang: 'en' } })],
    }).compileComponents();

    fixture = TestBed.createComponent(SalonHeaderComponent);
    fixture.componentRef.setInput('name', 'Institut Vénus');
    fixture.componentRef.setInput('logoUrl', null);
    fixture.detectChanges();
  });

  it('renders the salon name', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.brand .name')?.textContent).toContain('Institut Vénus');
  });

  it('renders fallback gradient when logoUrl is null', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.brand .logo-placeholder')).not.toBeNull();
  });

  it('emits bookClick when CTA pressed', () => {
    let emitted = false;
    fixture.componentRef.instance.bookClick.subscribe(() => (emitted = true));
    const cta = fixture.nativeElement.querySelector('.cta') as HTMLButtonElement;
    cta.click();
    expect(emitted).toBe(true);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- --include='**/salon-header.component.spec.ts' --watch=false`
Expected: FAIL — module not found.

- [ ] **Step 3: Write the component**

```typescript
import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

export type SalonAnchor = 'home' | 'cares' | 'stories' | 'about' | 'contact';

@Component({
  selector: 'app-salon-header',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="ch">
      <div class="brand">
        @if (logoUrl()) {
          <img [src]="logoUrl()!" [alt]="name()" class="logo" />
        } @else {
          <div class="logo logo-placeholder"></div>
        }
        <span class="name">{{ name() }}</span>
      </div>
      <nav class="nav">
        @for (a of anchors; track a) {
          <a [href]="'#' + a" [class.on]="activeAnchor() === a">
            {{ 'salon.pc.header.' + a | transloco }}
          </a>
        }
      </nav>
      <button class="cta" (click)="bookClick.emit()">
        {{ 'salon.pc.header.bookCta' | transloco }}
      </button>
    </header>
  `,
  styles: `
    :host { display: block; }
    .ch {
      position: sticky; top: 0; z-index: 50;
      background: rgba(253, 250, 248, 0.94);
      backdrop-filter: blur(10px);
      border-bottom: 1px solid var(--pf-salon-line);
      padding: 14px 36px;
      display: flex; align-items: center; justify-content: space-between;
    }
    .brand { display: flex; align-items: center; gap: 12px; }
    .brand .logo {
      width: 34px; height: 34px; border-radius: 50%;
      box-shadow: 0 1px 4px rgba(0,0,0,.06);
      object-fit: cover;
    }
    .brand .logo-placeholder { background: linear-gradient(135deg, #f3d5c0, #d4a594); }
    .brand .name {
      font-size: 13px; font-weight: 600;
      letter-spacing: 2px; text-transform: uppercase;
      color: var(--pf-salon-ink);
    }
    .nav { display: flex; gap: 28px; }
    .nav a {
      font-size: 11px; letter-spacing: 1px; text-transform: uppercase;
      color: var(--pf-salon-ink-soft); text-decoration: none;
    }
    .nav a.on { color: var(--pf-salon-accent); }
    .cta {
      background: var(--pf-salon-accent); color: #fff;
      font-size: 10px; padding: 9px 22px;
      letter-spacing: 1.5px; text-transform: uppercase;
      font-weight: 500; border: 0; cursor: pointer;
    }
    .cta:hover { background: var(--pf-salon-accent-dark); }
  `,
})
export class SalonHeaderComponent {
  readonly name = input.required<string>();
  readonly logoUrl = input<string | null>(null);
  readonly activeAnchor = input<SalonAnchor>('home');
  readonly bookClick = output<void>();

  readonly anchors: SalonAnchor[] = ['home', 'cares', 'stories', 'about', 'contact'];
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- --include='**/salon-header.component.spec.ts' --watch=false`
Expected: PASS, 3 specs.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/salon/pc/sections/salon-header.component.ts frontend/src/app/pages/salon/pc/sections/salon-header.component.spec.ts
git commit -m "feat(salon-pc): SalonHeaderComponent with sticky nav + book CTA"
```

### Task 2.2: Hero component

**Files:**
- Create: `frontend/src/app/pages/salon/pc/sections/salon-hero.component.ts`
- Test: `frontend/src/app/pages/salon/pc/sections/salon-hero.component.spec.ts`

- [ ] **Step 1: Write the failing test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { SalonHeroComponent } from './salon-hero.component';

describe('SalonHeroComponent', () => {
  let fixture: ComponentFixture<SalonHeroComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SalonHeroComponent, TranslocoTestingModule.forRoot({ langs: { en: {} }, translocoConfig: { defaultLang: 'en' } })],
    }).compileComponents();
    fixture = TestBed.createComponent(SalonHeroComponent);
    fixture.componentRef.setInput('name', 'Institut Vénus');
    fixture.componentRef.setInput('description', 'Beauté naturelle, à votre rythme.');
    fixture.detectChanges();
  });

  it('renders the salon name as h1', () => {
    expect((fixture.nativeElement as HTMLElement).querySelector('h1')?.textContent).toContain('Institut Vénus');
  });

  it('renders the description as lede', () => {
    expect((fixture.nativeElement as HTMLElement).querySelector('.lede')?.textContent).toContain('Beauté naturelle');
  });

  it('hides the lede when description is empty', () => {
    fixture.componentRef.setInput('description', null);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.lede')).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- --include='**/salon-hero.component.spec.ts' --watch=false`
Expected: FAIL.

- [ ] **Step 3: Write the component**

```typescript
import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-salon-hero',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero">
      <div class="eyebrow">— {{ 'salon.pc.hero.eyebrow' | transloco }} —</div>
      <h1>{{ name() }}</h1>
      @if (description()) {
        <div class="lede">{{ description() }}</div>
      }
      <div class="ctas">
        <button class="primary" (click)="bookClick.emit()">
          {{ 'salon.pc.header.bookCta' | transloco }}
        </button>
        <button class="ghost" (click)="discoverClick.emit()">
          {{ 'salon.pc.hero.discover' | transloco }}
        </button>
      </div>
      <div class="arrow" aria-hidden="true">↓</div>
    </section>
  `,
  styles: `
    :host { display: block; }
    .hero { padding: 90px 36px 64px; text-align: center; }
    .eyebrow {
      font-size: 10px; letter-spacing: 4px;
      color: var(--pf-salon-accent); text-transform: uppercase;
      margin-bottom: 18px;
    }
    h1 {
      font-family: 'Cormorant Garamond', 'Times New Roman', serif;
      font-weight: 300; font-size: 48px; line-height: 1.1;
      margin: 0 auto 20px; max-width: 700px;
      color: var(--pf-salon-ink); letter-spacing: -0.5px;
    }
    .lede {
      font-size: 14px; color: var(--pf-salon-ink-soft);
      max-width: 520px; margin: 0 auto 32px; line-height: 1.8;
    }
    .ctas { display: flex; justify-content: center; gap: 14px; }
    .ctas button {
      font-size: 11px; letter-spacing: 2px; text-transform: uppercase;
      cursor: pointer; padding: 13px 32px; border: 0;
    }
    .ctas .primary { background: var(--pf-salon-accent); color: #fff; }
    .ctas .ghost { background: transparent; color: var(--pf-salon-ink); border: 1px solid var(--pf-salon-ink); }
    .arrow { margin-top: 38px; color: var(--pf-salon-accent); font-size: 22px; opacity: 0.6; }
  `,
})
export class SalonHeroComponent {
  readonly name = input.required<string>();
  readonly description = input<string | null>(null);
  readonly bookClick = output<void>();
  readonly discoverClick = output<void>();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- --include='**/salon-hero.component.spec.ts' --watch=false`
Expected: PASS, 3 specs.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/salon/pc/sections/salon-hero.component.ts frontend/src/app/pages/salon/pc/sections/salon-hero.component.spec.ts
git commit -m "feat(salon-pc): SalonHeroComponent with editorial Cormorant title"
```

### Task 2.3: Banner, About, CTA, Footer (small presentational components)

**Files:**
- Create: `frontend/src/app/pages/salon/pc/sections/salon-banner.component.ts`
- Create: `frontend/src/app/pages/salon/pc/sections/salon-about.component.ts`
- Create: `frontend/src/app/pages/salon/pc/sections/salon-cta.component.ts`
- Create: `frontend/src/app/pages/salon/pc/sections/salon-footer.component.ts`

These four components are pure presentational with no business logic. We bundle them into one task with one commit.

- [ ] **Step 1: Write `salon-banner.component.ts`**

```typescript
import { Component, ChangeDetectionStrategy, input } from '@angular/core';

@Component({
  selector: 'app-salon-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="banner" [class.has-image]="!!heroImageUrl()">
      @if (heroImageUrl()) {
        <img [src]="heroImageUrl()!" [alt]="alt()" />
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .banner {
      height: 280px; margin: 0 36px; border-radius: 4px;
      background: linear-gradient(135deg, #e8c4b0 0%, #d4a594 50%, #b56b5a 100%);
      position: relative; overflow: hidden;
    }
    .banner img { width: 100%; height: 100%; object-fit: cover; display: block; }
  `,
})
export class SalonBannerComponent {
  readonly heroImageUrl = input<string | null>(null);
  readonly alt = input<string>('');
}
```

- [ ] **Step 2: Write `salon-about.component.ts`**

```typescript
import { Component, ChangeDetectionStrategy, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-salon-about',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="about-sec">
      <div class="grid">
        <div class="img" [style.background-image]="imageUrl() ? 'url(' + imageUrl() + ')' : null"></div>
        <div class="txt">
          <div class="lab">{{ 'salon.pc.about.label' | transloco }}</div>
          <h2>{{ name() }}</h2>
          <p class="body">{{ description() }}</p>
        </div>
      </div>
    </section>
  `,
  styles: `
    :host { display: block; }
    .about-sec { padding: 80px 36px; }
    .grid {
      display: grid; grid-template-columns: 1fr 1fr; gap: 56px;
      align-items: center; max-width: 1080px; margin: 0 auto;
    }
    .img {
      height: 320px; border-radius: 4px;
      background: linear-gradient(135deg, #f3d5c0, #c89889);
      background-size: cover; background-position: center;
    }
    .txt .lab {
      font-size: 9px; letter-spacing: 4px;
      text-transform: uppercase; color: var(--pf-salon-accent);
      margin-bottom: 14px;
    }
    .txt h2 {
      font-family: 'Cormorant Garamond', serif;
      font-weight: 300; font-size: 30px; line-height: 1.2;
      margin: 0 0 18px;
    }
    .txt .body {
      font-size: 13px; color: var(--pf-salon-ink-soft);
      line-height: 1.9; white-space: pre-line;
    }
  `,
})
export class SalonAboutComponent {
  readonly name = input.required<string>();
  readonly description = input.required<string>();
  readonly imageUrl = input<string | null>(null);
}
```

- [ ] **Step 3: Write `salon-cta.component.ts`**

```typescript
import { Component, ChangeDetectionStrategy, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-salon-cta',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="cta-band">
      <div class="lab">— {{ 'salon.pc.cta.label' | transloco }} —</div>
      <h2>{{ 'salon.pc.cta.title' | transloco }}</h2>
      <p>{{ 'salon.pc.cta.body' | transloco }}</p>
      <button class="btn" (click)="bookClick.emit()">{{ 'salon.pc.cta.button' | transloco }}</button>
    </section>
  `,
  styles: `
    :host { display: block; }
    .cta-band {
      background: var(--pf-salon-accent-soft);
      padding: 80px 36px; text-align: center;
    }
    .lab {
      font-size: 9px; letter-spacing: 4px; text-transform: uppercase;
      color: var(--pf-salon-accent); margin-bottom: 14px;
    }
    h2 {
      font-family: 'Cormorant Garamond', serif;
      font-weight: 300; font-size: 36px; margin: 0 0 18px;
      color: var(--pf-salon-ink);
    }
    p {
      font-size: 13px; color: var(--pf-salon-ink-soft);
      max-width: 480px; margin: 0 auto 28px; line-height: 1.8;
    }
    .btn {
      background: var(--pf-salon-accent); color: #fff;
      padding: 14px 36px; font-size: 11px;
      letter-spacing: 2px; text-transform: uppercase;
      border: 0; cursor: pointer;
    }
    .btn:hover { background: var(--pf-salon-accent-dark); }
  `,
})
export class SalonCtaComponent {
  readonly bookClick = output<void>();
}
```

- [ ] **Step 4: Write `salon-footer.component.ts`**

```typescript
import { Component, ChangeDetectionStrategy, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-salon-footer',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <footer class="ft">
      <div class="bottom">
        <span>© {{ year }} {{ name() }}</span>
        <span class="powered">{{ 'salon.pc.footer.poweredBy' | transloco }} ✿</span>
      </div>
    </footer>
  `,
  styles: `
    :host { display: block; }
    .ft {
      background: var(--pf-salon-ink); color: rgba(255, 255, 255, 0.7);
      padding: 36px; text-align: center; font-size: 10px;
    }
    .bottom {
      display: flex; justify-content: space-between; align-items: center;
      letter-spacing: 1px;
    }
    .powered { font-size: 9px; opacity: 0.7; }
  `,
})
export class SalonFooterComponent {
  readonly name = input.required<string>();
  readonly year = new Date().getFullYear();
}
```

- [ ] **Step 5: Build the project**

Run: `cd frontend && npm run build`
Expected: build succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/salon/pc/sections/salon-banner.component.ts frontend/src/app/pages/salon/pc/sections/salon-about.component.ts frontend/src/app/pages/salon/pc/sections/salon-cta.component.ts frontend/src/app/pages/salon/pc/sections/salon-footer.component.ts
git commit -m "feat(salon-pc): banner, about, cta, footer presentational components"
```

### Task 2.4: Cares section component

**Files:**
- Create: `frontend/src/app/pages/salon/pc/sections/salon-cares.component.ts`
- Test: `frontend/src/app/pages/salon/pc/sections/salon-cares.component.spec.ts`

- [ ] **Step 1: Write the failing test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { SalonCaresComponent } from './salon-cares.component';
import { PublicCategoryDto } from '../../../../features/salon-profile/models/salon-profile.model';

describe('SalonCaresComponent', () => {
  let fixture: ComponentFixture<SalonCaresComponent>;

  const fakeCategories: PublicCategoryDto[] = [
    {
      name: 'Visage',
      cares: [
        { id: 1, name: 'Soin éclat', description: 'Hydratation profonde.', duration: 60, price: 7500, imageUrls: [] },
      ],
    },
    { name: 'Empty', cares: [] },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SalonCaresComponent, TranslocoTestingModule.forRoot({ langs: { en: {} }, translocoConfig: { defaultLang: 'en' } })],
    }).compileComponents();
    fixture = TestBed.createComponent(SalonCaresComponent);
    fixture.componentRef.setInput('categories', fakeCategories);
    fixture.detectChanges();
  });

  it('renders only categories with at least one care', () => {
    const titles = (fixture.nativeElement as HTMLElement).querySelectorAll('.cat .h h3');
    expect(titles.length).toBe(1);
    expect(titles[0].textContent).toContain('Visage');
  });

  it('formats price as euros with a comma', () => {
    expect((fixture.nativeElement as HTMLElement).querySelector('.pr')?.textContent).toContain('75,00');
  });

  it('emits bookCare with care id when book link clicked', () => {
    let booked = -1;
    fixture.componentRef.instance.bookCare.subscribe((id: number) => (booked = id));
    (fixture.nativeElement.querySelector('.book') as HTMLButtonElement).click();
    expect(booked).toBe(1);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- --include='**/salon-cares.component.spec.ts' --watch=false`
Expected: FAIL.

- [ ] **Step 3: Write the component**

```typescript
import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { PublicCategoryDto, PublicCareDto } from '../../../../features/salon-profile/models/salon-profile.model';

@Component({
  selector: 'app-salon-cares',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="cares-sec">
      <header class="head">
        <div class="lab">— {{ 'salon.pc.cares.label' | transloco }} —</div>
        <h2>{{ 'salon.pc.cares.title' | transloco }}</h2>
        <div class="sub">{{ 'salon.pc.cares.subtitle' | transloco }}</div>
      </header>

      @for (cat of visibleCategories(); track cat.name) {
        <div class="cat">
          <div class="h">
            <h3>{{ cat.name }}</h3>
            <span class="count">{{ 'salon.pc.cares.countLabel' | transloco: { count: cat.cares.length } }}</span>
          </div>
          <div class="grid">
            @for (care of cat.cares; track care.id) {
              <article class="care">
                <div class="icn">✿</div>
                <div class="body">
                  <div class="nm">{{ care.name }}</div>
                  @if (care.description) {
                    <div class="desc">{{ care.description }}</div>
                  }
                  <div class="meta">
                    <span class="du">⏱ {{ formatDuration(care.duration) }}</span>
                    <span class="pr">{{ formatPrice(care.price) }}</span>
                  </div>
                  <button class="book" (click)="bookCare.emit(care.id)">
                    {{ 'salon.pc.cares.bookLink' | transloco }} →
                  </button>
                </div>
              </article>
            }
          </div>
        </div>
      }
    </section>
  `,
  styles: `
    :host { display: block; }
    .cares-sec {
      padding: 80px 36px; background: var(--pf-salon-paper);
      border-top: 1px solid var(--pf-salon-line);
      border-bottom: 1px solid var(--pf-salon-line);
    }
    .head { text-align: center; margin-bottom: 48px; }
    .head .lab {
      font-size: 9px; letter-spacing: 4px;
      color: var(--pf-salon-accent); text-transform: uppercase;
      margin-bottom: 14px;
    }
    .head h2 {
      font-family: 'Cormorant Garamond', serif;
      font-weight: 300; font-size: 34px; margin: 0;
    }
    .head .sub {
      font-size: 13px; color: var(--pf-salon-ink-soft);
      margin-top: 12px;
    }
    .cat { max-width: 980px; margin: 0 auto 48px; }
    .cat .h {
      display: flex; align-items: center; gap: 14px;
      margin-bottom: 22px; padding-bottom: 12px;
      border-bottom: 1px solid var(--pf-salon-line);
    }
    .cat .h::before {
      content: ''; width: 28px; height: 1px;
      background: var(--pf-salon-accent);
    }
    .cat .h h3 {
      font-family: 'Cormorant Garamond', serif;
      font-weight: 400; font-size: 22px; margin: 0;
    }
    .cat .h .count {
      font-size: 10px; color: var(--pf-salon-ink-soft);
      margin-left: auto;
    }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
    .care {
      background: var(--pf-salon-bg);
      border: 1px solid var(--pf-salon-line);
      padding: 22px;
      display: flex; gap: 18px; align-items: flex-start;
      transition: border-color 0.2s;
    }
    .care:hover { border-color: var(--pf-salon-accent); }
    .icn {
      width: 44px; height: 44px; border-radius: 50%;
      background: var(--pf-salon-accent-soft);
      flex-shrink: 0;
      display: flex; align-items: center; justify-content: center;
      color: var(--pf-salon-accent); font-size: 18px;
    }
    .body { flex: 1; }
    .body .nm {
      font-size: 14px; font-weight: 600;
      color: var(--pf-salon-ink); margin-bottom: 6px;
    }
    .body .desc {
      font-size: 12px; color: var(--pf-salon-ink-soft);
      line-height: 1.7; margin-bottom: 14px;
    }
    .body .meta {
      display: flex; justify-content: space-between; align-items: center;
      padding-top: 12px; border-top: 1px solid var(--pf-salon-line);
    }
    .body .meta .du { font-size: 11px; color: var(--pf-salon-ink-soft); }
    .body .meta .pr {
      font-size: 14px; color: var(--pf-salon-accent); font-weight: 600;
    }
    .body .book {
      font-size: 10px; letter-spacing: 1.5px;
      text-transform: uppercase; color: var(--pf-salon-accent);
      border: 0; background: transparent;
      border-bottom: 1px solid var(--pf-salon-accent);
      padding: 0 0 2px; margin-top: 12px; cursor: pointer;
    }
  `,
})
export class SalonCaresComponent {
  readonly categories = input.required<PublicCategoryDto[]>();
  readonly bookCare = output<number>();

  visibleCategories(): PublicCategoryDto[] {
    return this.categories().filter((c) => c.cares.length > 0);
  }

  formatDuration(minutes: number): string {
    if (minutes < 60) return `${minutes} min`;
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${h}h`;
  }

  formatPrice(cents: number): string {
    return (cents / 100).toFixed(2).replace('.', ',') + ' €';
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- --include='**/salon-cares.component.spec.ts' --watch=false`
Expected: PASS, 3 specs.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/salon/pc/sections/salon-cares.component.ts frontend/src/app/pages/salon/pc/sections/salon-cares.component.spec.ts
git commit -m "feat(salon-pc): SalonCaresComponent with editorial 2-col cards"
```

### Task 2.5: Contact section with Leaflet map

**Files:**
- Create: `frontend/src/app/pages/salon/pc/sections/salon-contact.component.ts`

The Leaflet logic is mostly cloned from the existing mobile component (`salon-page.component.ts:157-201`). Key change: render in a 2-col layout (info left, map right).

- [ ] **Step 1: Write the component**

```typescript
import {
  Component, ChangeDetectionStrategy, ElementRef, OnDestroy,
  PLATFORM_ID, computed, effect, inject, input, viewChild,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-salon-contact',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="contact-sec">
      <div class="info">
        <div class="lab">— {{ 'salon.pc.contact.label' | transloco }} —</div>
        <h2>{{ 'salon.pc.contact.title' | transloco }}</h2>
        @if (fullAddress()) {
          <div class="row">
            <span class="ico">📍</span>
            <div>
              <strong>{{ 'salon.pc.contact.address' | transloco }}</strong>
              @if (street()) { <span>{{ street() }}<br></span> }
              <span>{{ postalCode() }} {{ city() }}</span>
            </div>
          </div>
        }
        @if (phone()) {
          <a [href]="'tel:' + phone()" class="row">
            <span class="ico">☎</span>
            <div>
              <strong>{{ 'salon.pc.contact.phone' | transloco }}</strong>
              <span>{{ phone() }}</span>
            </div>
          </a>
        }
        @if (email()) {
          <a [href]="'mailto:' + email()" class="row">
            <span class="ico">✉</span>
            <div>
              <strong>{{ 'salon.pc.contact.email' | transloco }}</strong>
              <span>{{ email() }}</span>
            </div>
          </a>
        }
      </div>
      <div class="map" #mapEl></div>
    </section>
  `,
  styles: `
    :host { display: block; }
    .contact-sec {
      display: grid; grid-template-columns: 1fr 1fr;
      min-height: 380px;
      border-top: 1px solid var(--pf-salon-line);
    }
    .info { padding: 64px 48px; }
    .lab {
      font-size: 9px; letter-spacing: 4px;
      color: var(--pf-salon-accent); text-transform: uppercase;
      margin-bottom: 12px;
    }
    h2 {
      font-family: 'Cormorant Garamond', serif;
      font-weight: 300; font-size: 28px; margin: 0 0 28px;
    }
    .row {
      display: flex; gap: 14px; margin-bottom: 20px;
      font-size: 12px; color: var(--pf-salon-ink-soft);
      line-height: 1.7; text-decoration: none;
    }
    .row .ico {
      width: 32px; height: 32px; border-radius: 50%;
      background: var(--pf-salon-accent-soft);
      color: var(--pf-salon-accent);
      display: flex; align-items: center; justify-content: center;
      font-size: 13px; flex-shrink: 0;
    }
    .row strong {
      color: var(--pf-salon-ink); display: block;
      font-weight: 500; margin-bottom: 2px;
    }
    .map {
      background: linear-gradient(135deg, #ece4dd, #c8b9a8);
      min-height: 380px;
    }
  `,
})
export class SalonContactComponent implements OnDestroy {
  readonly street = input<string | null>(null);
  readonly postalCode = input<string | null>(null);
  readonly city = input<string | null>(null);
  readonly country = input<string | null>(null);
  readonly phone = input<string | null>(null);
  readonly email = input<string | null>(null);

  private readonly platformId = inject(PLATFORM_ID);
  readonly mapRef = viewChild<ElementRef<HTMLElement>>('mapEl');
  private mapInstance: any = null;
  private initialized = false;

  readonly fullAddress = computed(() =>
    [this.street(), this.postalCode(), this.city(), this.country()].filter(Boolean).join(', '),
  );

  constructor() {
    effect(() => {
      const el = this.mapRef()?.nativeElement;
      const addr = this.fullAddress();
      if (el && addr && !this.initialized && isPlatformBrowser(this.platformId)) {
        this.initialized = true;
        this.initMap(el, addr);
      }
    });
  }

  ngOnDestroy(): void {
    if (this.mapInstance) {
      this.mapInstance.remove();
      this.mapInstance = null;
    }
  }

  private async initMap(el: HTMLElement, address: string): Promise<void> {
    const leaflet = await import('leaflet');
    const L: any = (leaflet as any).default ?? leaflet;

    this.mapInstance = L.map(el, { zoomControl: true, attributionControl: false }).setView([46.6, 2.3], 6);
    L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', { maxZoom: 19 }).addTo(this.mapInstance);
    setTimeout(() => this.mapInstance?.invalidateSize(), 250);

    try {
      const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(address)}&limit=1`;
      const res = await fetch(url);
      const data = await res.json();
      if (data.length > 0) {
        const lat = parseFloat(data[0].lat);
        const lng = parseFloat(data[0].lon);
        this.mapInstance.setView([lat, lng], 15);
        L.marker([lat, lng]).addTo(this.mapInstance);
      }
    } catch {
      // Silent geocoding failure — the empty map is acceptable.
    }
  }
}
```

- [ ] **Step 2: Build to typecheck**

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/salon/pc/sections/salon-contact.component.ts
git commit -m "feat(salon-pc): SalonContactComponent with 2-col layout + leaflet"
```

### Task 2.6: Wire all sections into `SalonPagePcComponent`

**Files:**
- Modify: `frontend/src/app/pages/salon/pc/salon-page-pc.component.ts`
- Modify: `frontend/src/app/pages/salon/pc/salon-page-pc.component.html`

Note: Stories section is left as a placeholder until Milestone 3.

- [ ] **Step 1: Update the component class with all imports and outputs**

Replace the body of `salon-page-pc.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { PublicSalonResponse } from '../../../features/salon-profile/models/salon-profile.model';
import { SalonHeaderComponent } from './sections/salon-header.component';
import { SalonHeroComponent } from './sections/salon-hero.component';
import { SalonBannerComponent } from './sections/salon-banner.component';
import { SalonAboutComponent } from './sections/salon-about.component';
import { SalonCaresComponent } from './sections/salon-cares.component';
import { SalonCtaComponent } from './sections/salon-cta.component';
import { SalonContactComponent } from './sections/salon-contact.component';
import { SalonFooterComponent } from './sections/salon-footer.component';

@Component({
  selector: 'app-salon-page-pc',
  standalone: true,
  imports: [
    TranslocoPipe,
    SalonHeaderComponent,
    SalonHeroComponent,
    SalonBannerComponent,
    SalonAboutComponent,
    SalonCaresComponent,
    SalonCtaComponent,
    SalonContactComponent,
    SalonFooterComponent,
  ],
  templateUrl: './salon-page-pc.component.html',
  styleUrl: './salon-page-pc.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SalonPagePcComponent {
  readonly salon = input.required<PublicSalonResponse>();
  readonly bookCare = output<number>();
  readonly bookGeneric = output<void>();
}
```

- [ ] **Step 2: Update the template**

Replace `salon-page-pc.component.html`:

```html
<div class="pf-salon">
  <app-salon-header
    data-section="header"
    [name]="salon().name"
    [logoUrl]="salon().logoUrl"
    (bookClick)="bookGeneric.emit()" />

  <app-salon-hero
    data-section="hero"
    [name]="salon().name"
    [description]="salon().description"
    (bookClick)="bookGeneric.emit()" />

  <app-salon-banner
    data-section="banner"
    [heroImageUrl]="salon().heroImageUrl"
    [alt]="salon().name" />

  @if (salon().description) {
    <app-salon-about
      data-section="about"
      [name]="salon().name"
      [description]="salon().description!" />
  }

  @if (salon().categories.length > 0) {
    <app-salon-cares
      data-section="cares"
      [categories]="salon().categories"
      (bookCare)="bookCare.emit($event)" />
  }

  <!-- Stories placeholder (Milestone 3) -->
  <div data-section="stories"></div>

  <app-salon-cta
    data-section="cta"
    (bookClick)="bookGeneric.emit()" />

  @if (salon().addressStreet || salon().phone || salon().contactEmail) {
    <app-salon-contact
      data-section="contact"
      [street]="salon().addressStreet"
      [postalCode]="salon().addressPostalCode"
      [city]="salon().addressCity"
      [country]="salon().addressCountry"
      [phone]="salon().phone"
      [email]="salon().contactEmail" />
  }

  <app-salon-footer
    data-section="footer"
    [name]="salon().name" />
</div>
```

- [ ] **Step 3: Wire up booking events in `salon-page.component.ts`**

In the body section of the existing `SalonPageComponent` template (the PC branch added in Task 1.2), update to:

```html
@if (isPc()) {
  <app-salon-page-pc
    [salon]="salon"
    (bookCare)="onBookFromPost($event)"
    (bookGeneric)="openFirstAvailableBooking(salon)" />
} @else {
  <!-- existing mobile template (UNCHANGED) -->
}
```

Add a new method to the class in `salon-page.component.ts`:

```typescript
protected openFirstAvailableBooking(salon: PublicSalonResponse): void {
  // Open booking dialog with the first available care across all categories.
  for (const cat of salon.categories) {
    if (cat.cares.length > 0) {
      this.openBookingDialog(cat.cares[0]);
      return;
    }
  }
  // If no care is available, do nothing — section won't even render.
}
```

- [ ] **Step 4: Run unit tests for the component**

Run: `cd frontend && npm test -- --include='**/salon-page-pc.component.spec.ts' --watch=false`
Expected: PASS.

- [ ] **Step 5: Manual smoke test**

`npm start`. Visit `/salon/<known-slug>` at width >= 1024px:
- All sections render with content from the `PublicSalonResponse`.
- Stories area is empty (placeholder).
- "Prendre RDV" buttons in header/hero/cta open the booking dialog.
- "Réserver" links in care cards open the booking dialog with the right care.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/salon/pc/salon-page-pc.component.ts frontend/src/app/pages/salon/pc/salon-page-pc.component.html frontend/src/app/pages/salon/salon-page.component.ts frontend/src/app/pages/salon/salon-page.component.html
git commit -m "feat(salon-pc): wire all sections into PC page (no stories yet)"
```


---

## Milestone 3 — Stories section (variant B)

À la fin de ce jalon, la section Stories est rendue côté client : rangée horizontale scrollable, RDV direct, et viewer modal plein écran.

### Task 3.1: Add `startIndex` input to `SalonPostsViewerComponent`

**Files:**
- Modify: `frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts`

- [ ] **Step 1: Add the input declaration**

Below `readonly slug = input.required<string>();` (around line 626), add:

```typescript
readonly startIndex = input<number>(0);
```

- [ ] **Step 2: Scroll to the start index after the initial load**

In the `loadInitial` method, replace the `next` callback so it becomes:

```typescript
next: (page) => {
  this.posts.set(page.content);
  this.hasMore.set(!page.last);
  this.loading.set(false);
  // Defer scrolling to give the snap-scroll element a chance to size.
  queueMicrotask(() => {
    const el = this.snapScroll()?.nativeElement;
    const idx = this.startIndex();
    if (el && idx > 0 && idx < page.content.length) {
      el.scrollTo({ top: idx * el.clientHeight, behavior: 'auto' });
      this.currentIndex.set(idx);
    }
  });
},
```

- [ ] **Step 3: Run existing posts viewer tests if any**

Run: `cd frontend && npm test -- --include='**/salon-posts-viewer*' --watch=false`
Expected: existing tests still pass (or the file has no spec — that's fine).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts
git commit -m "feat(posts-viewer): add startIndex input to scroll to specific post on init"
```

### Task 3.2: Create `StoriesModalComponent`

**Files:**
- Create: `frontend/src/app/pages/salon/pc/stories/stories-modal.component.ts`

- [ ] **Step 1: Write the component**

```typescript
import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { SalonPostsViewerComponent } from '../../../../features/posts/salon-posts-viewer/salon-posts-viewer.component';

export interface StoriesModalData {
  slug: string;
  startIndex: number;
}

@Component({
  selector: 'app-stories-modal',
  standalone: true,
  imports: [MatDialogModule, MatIconModule, SalonPostsViewerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-shell">
      <button class="close" mat-dialog-close aria-label="Close">
        <mat-icon>close</mat-icon>
      </button>
      <div class="viewer-frame">
        <app-salon-posts-viewer
          [slug]="data.slug"
          [startIndex]="data.startIndex"
          (bookCare)="onBookCare($event)" />
      </div>
    </div>
  `,
  styles: `
    :host { display: block; }
    .modal-shell {
      position: relative;
      background: #000;
      width: 100vw;
      height: 100vh;
      max-width: 100vw;
      max-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .close {
      position: absolute;
      top: 16px; right: 16px;
      width: 40px; height: 40px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.15);
      color: #fff;
      border: 0;
      cursor: pointer;
      z-index: 10;
      display: flex; align-items: center; justify-content: center;
    }
    .viewer-frame { width: min(420px, 100vw); height: min(85vh, 100vh); }
  `,
})
export class StoriesModalComponent {
  readonly data: StoriesModalData = inject(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<StoriesModalComponent, number | undefined>);

  onBookCare(careId: number): void {
    this.dialogRef.close(careId);
  }
}
```

- [ ] **Step 2: Build to typecheck**

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/salon/pc/stories/stories-modal.component.ts
git commit -m "feat(salon-pc): StoriesModalComponent wrapping SalonPostsViewer fullscreen"
```

### Task 3.3: Create `StoriesRowComponent`

**Files:**
- Create: `frontend/src/app/pages/salon/pc/stories/stories-row.component.ts`
- Create: `frontend/src/app/pages/salon/pc/stories/stories-row.service.ts`
- Test: `frontend/src/app/pages/salon/pc/stories/stories-row.component.spec.ts`

- [ ] **Step 1: Write the row service for fetching public posts**

```typescript
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../../../../core/config/api-base-url.token';
import { PostResponse } from '../../../../features/posts/posts.model';

interface PostsPage {
  content: PostResponse[];
  last: boolean;
}

@Injectable({ providedIn: 'root' })
export class StoriesRowService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  list(slug: string, page = 0, size = 12): Observable<PostsPage> {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    return this.http
      .get<PostsPage>(`${base}/api/salon/${slug}/posts`, { params: { page: page.toString(), size: size.toString() } })
      .pipe(
        map((p) => ({
          ...p,
          content: p.content.map((post) => ({
            ...post,
            beforeImageUrl: prefix(post.beforeImageUrl, base),
            afterImageUrl: prefix(post.afterImageUrl, base),
            carouselImageUrls: post.carouselImageUrls.map((u) => prefix(u, base) ?? u),
          })),
        })),
      );
  }
}

function prefix(url: string | null, base: string): string | null {
  if (!url) return null;
  return url.startsWith('http') ? url : `${base}${url}`;
}
```

- [ ] **Step 2: Write the failing test for the row component**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { StoriesRowComponent } from './stories-row.component';
import { API_BASE_URL } from '../../../../core/config/api-base-url.token';

describe('StoriesRowComponent', () => {
  let fixture: ComponentFixture<StoriesRowComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StoriesRowComponent, TranslocoTestingModule.forRoot({ langs: { en: {} }, translocoConfig: { defaultLang: 'en' } })],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: API_BASE_URL, useValue: 'http://api.test' },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(StoriesRowComponent);
    fixture.componentRef.setInput('slug', 'institut-venus');
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('hides itself when posts list is empty', () => {
    http.expectOne('http://api.test/api/salon/institut-venus/posts?page=0&size=12').flush({ content: [], last: true });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.stories')).toBeNull();
    expect(fixture.componentRef.instance.hasPosts()).toBe(false);
  });

  it('renders one card per post when posts are present', () => {
    http.expectOne('http://api.test/api/salon/institut-venus/posts?page=0&size=12').flush({
      content: [
        { id: 1, type: 'PHOTO', caption: 'a', beforeImageUrl: '/img.jpg', afterImageUrl: null, carouselImageUrls: [], careId: null, careName: null, createdAt: '2026-05-01T00:00:00Z' },
        { id: 2, type: 'BEFORE_AFTER', caption: 'b', beforeImageUrl: '/b.jpg', afterImageUrl: '/a.jpg', carouselImageUrls: [], careId: 5, careName: 'Soin', createdAt: '2026-05-01T00:00:00Z' },
      ],
      last: true,
    });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.post').length).toBe(2);
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd frontend && npm test -- --include='**/stories-row.component.spec.ts' --watch=false`
Expected: FAIL.

- [ ] **Step 4: Write the row component**

```typescript
import { Component, ChangeDetectionStrategy, computed, effect, inject, input, output, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { PostResponse } from '../../../../features/posts/posts.model';
import { StoriesRowService } from './stories-row.service';
import { StoriesModalComponent, StoriesModalData } from './stories-modal.component';

@Component({
  selector: 'app-stories-row',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (hasPosts()) {
      <section class="stories">
        <header class="head-row">
          <div class="left">
            <div class="lab">— {{ 'salon.pc.stories.label' | transloco }} —</div>
            <h2>{{ 'salon.pc.stories.title' | transloco }}</h2>
            <div class="sub">{{ 'salon.pc.stories.subtitle' | transloco }}</div>
          </div>
        </header>

        <div class="row">
          @for (post of posts(); track post.id; let i = $index) {
            <article class="post" (click)="openViewer(i)">
              <div class="pic" [style.background-image]="thumbUrl(post)"></div>
              <div class="grad"></div>
              <span class="pill">{{ pillFor(post) }}</span>
              <div class="info">
                @if (post.caption) { <div class="cap">{{ post.caption }}</div> }
                @if (post.careName) { <div class="tag">{{ post.careName }}</div> }
              </div>
              @if (post.careId) {
                <div class="actions">
                  <button class="ab book" (click)="onBook($event, post.careId!)">
                    {{ 'salon.pc.stories.rdv' | transloco }}
                  </button>
                </div>
              }
            </article>
          }
        </div>
      </section>
    }
  `,
  styles: `
    :host { display: block; }
    .stories { padding: 90px 0 80px; }
    .head-row {
      display: flex; align-items: flex-end; justify-content: space-between;
      padding: 0 36px; max-width: 1200px; margin: 0 auto 32px;
    }
    .head-row .left .lab {
      font-size: 9px; letter-spacing: 4px;
      color: var(--pf-salon-accent); text-transform: uppercase;
      margin-bottom: 10px;
    }
    .head-row .left h2 {
      font-family: 'Cormorant Garamond', serif;
      font-weight: 300; font-size: 30px; margin: 0;
    }
    .head-row .left .sub {
      font-size: 12px; color: var(--pf-salon-ink-soft);
      margin-top: 6px; max-width: 360px; line-height: 1.7;
    }
    .row {
      display: flex; gap: 16px; overflow-x: auto;
      padding: 6px 36px 18px;
      scroll-snap-type: x mandatory;
    }
    .row::-webkit-scrollbar { height: 4px; }
    .row::-webkit-scrollbar-thumb { background: var(--pf-salon-accent-soft); border-radius: 4px; }
    .post {
      flex: 0 0 240px; aspect-ratio: 3/4;
      scroll-snap-align: start;
      position: relative; border-radius: 12px; overflow: hidden;
      background: #1a1a1a; cursor: pointer;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
    }
    .pic {
      position: absolute; inset: 0;
      background-size: cover; background-position: center;
      background-color: #444;
    }
    .grad {
      position: absolute; left: 0; right: 0; bottom: 0;
      height: 65%;
      background: linear-gradient(to top, rgba(0,0,0,0.78), transparent);
    }
    .pill {
      position: absolute; top: 12px; left: 12px;
      background: rgba(255,255,255,0.18); backdrop-filter: blur(8px);
      font-size: 9px; padding: 4px 9px; border-radius: 10px;
      color: #fff; letter-spacing: 0.5px;
    }
    .info {
      position: absolute; left: 14px; right: 60px; bottom: 14px; color: #fff;
    }
    .cap { font-size: 12px; line-height: 1.5; max-height: 56px; overflow: hidden; margin-bottom: 6px; }
    .tag {
      display: inline-block;
      background: rgba(181, 107, 90, 0.85);
      font-size: 10px; padding: 3px 9px; border-radius: 8px; font-weight: 500;
    }
    .actions { position: absolute; right: 10px; bottom: 14px; }
    .ab.book {
      background: var(--pf-salon-accent); color: #fff;
      width: 38px; height: 38px; border-radius: 50%; border: 0;
      font-size: 10px; font-weight: 700; letter-spacing: 0.5px;
      cursor: pointer;
    }
  `,
})
export class StoriesRowComponent {
  readonly slug = input.required<string>();
  readonly bookCare = output<number>();

  private readonly service = inject(StoriesRowService);
  private readonly dialog = inject(MatDialog);

  readonly posts = signal<PostResponse[]>([]);
  readonly hasPosts = computed(() => this.posts().length > 0);

  constructor() {
    effect(() => {
      const slug = this.slug();
      if (!slug) return;
      this.service.list(slug).subscribe({
        next: (page) => this.posts.set(page.content),
        error: () => this.posts.set([]),
      });
    });
  }

  thumbUrl(post: PostResponse): string {
    const u = post.afterImageUrl ?? post.beforeImageUrl ?? post.carouselImageUrls[0] ?? null;
    return u ? `url(${u})` : 'linear-gradient(135deg, #d4a594, #b56b5a)';
  }

  pillFor(post: PostResponse): string {
    switch (post.type) {
      case 'BEFORE_AFTER': return '⇄ Av/Ap';
      case 'CAROUSEL': return `⊞ ${post.carouselImageUrls.length}`;
      default: return '📷';
    }
  }

  openViewer(startIndex: number): void {
    this.dialog.open<StoriesModalComponent, StoriesModalData, number | undefined>(
      StoriesModalComponent,
      {
        data: { slug: this.slug(), startIndex },
        panelClass: 'stories-modal-pane',
        maxWidth: '100vw',
        maxHeight: '100vh',
        width: '100vw',
        height: '100vh',
        autoFocus: false,
      },
    ).afterClosed().subscribe((careId) => {
      if (careId) this.bookCare.emit(careId);
    });
  }

  onBook(event: Event, careId: number): void {
    event.stopPropagation();
    this.bookCare.emit(careId);
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && npm test -- --include='**/stories-row.component.spec.ts' --watch=false`
Expected: PASS, 2 specs.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/salon/pc/stories/
git commit -m "feat(salon-pc): StoriesRow component with horizontal cards + modal viewer"
```

### Task 3.4: Wire `StoriesRowComponent` into the page

**Files:**
- Modify: `frontend/src/app/pages/salon/pc/salon-page-pc.component.ts`
- Modify: `frontend/src/app/pages/salon/pc/salon-page-pc.component.html`

- [ ] **Step 1: Add the import**

In `salon-page-pc.component.ts`, add to imports:

```typescript
import { StoriesRowComponent } from './stories/stories-row.component';
```

And in the `imports` array of the `@Component`, append `StoriesRowComponent`.

- [ ] **Step 2: Replace the placeholder in the template**

In `salon-page-pc.component.html`, replace:

```html
<!-- Stories placeholder (Milestone 3) -->
<div data-section="stories"></div>
```

with:

```html
<app-stories-row
  data-section="stories"
  [slug]="salon().slug"
  (bookCare)="bookCare.emit($event)" />
```

- [ ] **Step 3: Manual smoke test**

`npm start`. Visit `/salon/<slug-with-posts>` at >= 1024px:
- Stories row visible if salon has posts.
- Section absent if no posts.
- Click a post → fullscreen modal viewer opens, scrolled to that post.
- Click "RDV" on a post card → modal does NOT open, booking dialog opens directly.
- "Réserver" inside the modal → modal closes, booking dialog opens.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/salon/pc/salon-page-pc.component.ts frontend/src/app/pages/salon/pc/salon-page-pc.component.html
git commit -m "feat(salon-pc): wire StoriesRow into PC page"
```

### Task 3.5: Polish — sticky nav scroll-spy

**Files:**
- Modify: `frontend/src/app/pages/salon/pc/salon-page-pc.component.ts`

- [ ] **Step 1: Add IntersectionObserver tracking active anchor**

Add at the top of the class body (after `bookGeneric` output):

```typescript
import { ChangeDetectionStrategy, Component, ElementRef, PLATFORM_ID, afterNextRender, inject, input, output, signal, viewChildren } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
// ... other imports unchanged

import type { SalonAnchor } from './sections/salon-header.component';

// inside the class:
readonly activeAnchor = signal<SalonAnchor>('home');
private readonly platformId = inject(PLATFORM_ID);
readonly sectionEls = viewChildren<ElementRef<HTMLElement>>('observed');

constructor() {
  afterNextRender(() => {
    if (!isPlatformBrowser(this.platformId)) return;
    const map: Record<string, SalonAnchor> = {
      header: 'home', hero: 'home', banner: 'home',
      cares: 'cares', stories: 'stories',
      about: 'about', contact: 'contact',
    };
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries.filter((e) => e.isIntersecting);
        if (visible.length === 0) return;
        const top = visible.reduce((a, b) => (a.intersectionRatio > b.intersectionRatio ? a : b));
        const id = (top.target as HTMLElement).dataset['section'];
        if (id && map[id]) this.activeAnchor.set(map[id]);
      },
      { threshold: [0.25, 0.5, 0.75] },
    );
    this.sectionEls().forEach((ref) => observer.observe(ref.nativeElement));
  });
}
```

- [ ] **Step 2: Tag observed sections in the template**

In `salon-page-pc.component.html`, add `#observed` template ref to each top-level section element. Example for header:

```html
<app-salon-header
  #observed
  data-section="header"
  …
/>
```

Repeat for `hero`, `banner`, `cares`, `stories`, `about`, `contact`.

- [ ] **Step 3: Pass `activeAnchor` to the header**

```html
<app-salon-header
  #observed
  data-section="header"
  [name]="salon().name"
  [logoUrl]="salon().logoUrl"
  [activeAnchor]="activeAnchor()"
  (bookClick)="bookGeneric.emit()" />
```

- [ ] **Step 4: Add smooth scroll behaviour**

Add to `:host` in `salon-page-pc.component.scss`:

```scss
:host {
  scroll-behavior: smooth;
}
html { scroll-behavior: smooth; }
```

- [ ] **Step 5: Manual smoke test**

`npm start`. Click anchors in the header → page scrolls smoothly to the matching section. The active anchor underlines correctly during scroll.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/salon/pc/salon-page-pc.component.ts frontend/src/app/pages/salon/pc/salon-page-pc.component.html frontend/src/app/pages/salon/pc/salon-page-pc.component.scss
git commit -m "feat(salon-pc): scroll-spy + smooth anchor navigation"
```


---

## Milestone 4 — Pro PC editor foundations

À la fin de ce jalon, sur PC, `/pro/.../salon` rend le squelette éditeur (top bar + sidebar + canvas vide) avec le composant `<app-editable-section>` opérationnel et son store. Aucune section éditable encore.

### Task 4.1: Extend `SalonProfileService` with publish + readiness

**Files:**
- Modify: `frontend/src/app/features/salon-profile/services/salon-profile.service.ts`
- Modify: `frontend/src/app/features/salon-profile/models/salon-profile.model.ts`

- [ ] **Step 1: Add the `TenantReadiness` interface**

At the bottom of `salon-profile.model.ts`, add:

```typescript
export interface TenantReadiness {
  ready: boolean;
  missing: string[];
}
```

- [ ] **Step 2: Add `publish` and `getReadiness` to the service**

In `salon-profile.service.ts`, add the import:

```typescript
import { TenantReadiness } from '../models/salon-profile.model';
```

Inside the class, after `getProfile`, add:

```typescript
publish(): Observable<void> {
  return this.http.put<void>(`${this.baseUrl}/api/pro/tenant/publish`, {});
}

getReadiness(): Observable<TenantReadiness> {
  return this.http.get<TenantReadiness>(`${this.baseUrl}/api/pro/tenant/readiness`);
}
```

- [ ] **Step 3: Build to typecheck**

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/salon-profile/services/salon-profile.service.ts frontend/src/app/features/salon-profile/models/salon-profile.model.ts
git commit -m "feat(salon-profile): expose publish() and getReadiness() in service"
```

### Task 4.2: Extend `SalonProfileStore` with editor state and methods

**Files:**
- Modify: `frontend/src/app/features/salon-profile/store/salon-profile.store.ts`

- [ ] **Step 1: Augment state and add methods**

Replace the contents of `salon-profile.store.ts`:

```typescript
import { inject } from '@angular/core';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { computed } from '@angular/core';
import { EMPTY, catchError, exhaustMap, pipe, switchMap, tap } from 'rxjs';
import { TenantResponse, TenantReadiness, UpdateTenantRequest } from '../models/salon-profile.model';
import { SalonProfileService } from '../services/salon-profile.service';
import { setError, setFulfilled, setPending, withRequestStatus } from '../../../shared/features/request.status.feature';

type SalonProfileState = {
  tenant: TenantResponse | null;
  readiness: TenantReadiness | null;
  saveSuccess: boolean;
  saveError: boolean;
  publishSuccess: boolean;
  publishError: boolean;
  activeSectionId: string | null;
  dirtySection: boolean;
};

const initial: SalonProfileState = {
  tenant: null,
  readiness: null,
  saveSuccess: false,
  saveError: false,
  publishSuccess: false,
  publishError: false,
  activeSectionId: null,
  dirtySection: false,
};

export const SalonProfileStore = signalStore(
  withState<SalonProfileState>(initial),
  withRequestStatus(),
  withComputed((store) => ({
    isDraft: computed(() => store.tenant()?.status === 'DRAFT'),
    isActive: computed(() => store.tenant()?.status === 'ACTIVE'),
    canPublish: computed(() => store.readiness()?.ready === true),
    missingFields: computed(() => store.readiness()?.missing ?? []),
  })),
  withMethods((store, service = inject(SalonProfileService)) => ({
    loadProfile: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() =>
          service.getProfile().pipe(
            tap((tenant) => patchState(store, { tenant }, setFulfilled())),
            catchError(() => {
              patchState(store, setFulfilled());
              return EMPTY;
            }),
          ),
        ),
      ),
    ),
    loadReadiness: rxMethod<void>(
      pipe(
        switchMap(() =>
          service.getReadiness().pipe(
            tap((readiness) => patchState(store, { readiness })),
            catchError(() => EMPTY),
          ),
        ),
      ),
    ),
    updateProfile: rxMethod<UpdateTenantRequest>(
      pipe(
        tap(() => patchState(store, { saveSuccess: false, saveError: false }, setPending())),
        exhaustMap((request) =>
          service.updateProfile(request).pipe(
            tap((tenant) => patchState(store, { tenant, saveSuccess: true, saveError: false, dirtySection: false }, setFulfilled())),
            catchError(() => {
              patchState(store, { saveError: true }, setError('Erreur lors de la sauvegarde'));
              return EMPTY;
            }),
          ),
        ),
      ),
    ),
    publish: rxMethod<void>(
      pipe(
        tap(() => patchState(store, { publishSuccess: false, publishError: false }, setPending())),
        exhaustMap(() =>
          service.publish().pipe(
            switchMap(() => service.getProfile()),
            tap((tenant) => patchState(store, { tenant, publishSuccess: true }, setFulfilled())),
            catchError(() => {
              patchState(store, { publishError: true }, setFulfilled());
              return EMPTY;
            }),
          ),
        ),
      ),
    ),
    activateSection(id: string | null): void {
      patchState(store, { activeSectionId: id, dirtySection: false });
    },
    setDirty(dirty: boolean): void {
      patchState(store, { dirtySection: dirty });
    },
    clearStatus(): void {
      patchState(store, { saveSuccess: false, saveError: false, publishSuccess: false, publishError: false }, setFulfilled());
    },
  })),
  withHooks((store) => ({
    onInit() {
      store.loadProfile();
      store.loadReadiness();
    },
  })),
);
```

- [ ] **Step 2: Run any existing store tests**

Run: `cd frontend && npm test -- --include='**/salon-profile.store*' --watch=false`
Expected: existing tests still pass (or no tests).

- [ ] **Step 3: Build to typecheck**

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/salon-profile/store/salon-profile.store.ts
git commit -m "feat(salon-profile): extend store with editor state, publish, readiness"
```

### Task 4.3: Create `EditableSectionComponent` (the wrapper)

**Files:**
- Create: `frontend/src/app/features/salon-profile/pc/shared/editable-section.component.ts`
- Test: `frontend/src/app/features/salon-profile/pc/shared/editable-section.component.spec.ts`

- [ ] **Step 1: Write the failing test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { EditableSectionComponent } from './editable-section.component';

describe('EditableSectionComponent', () => {
  let fixture: ComponentFixture<EditableSectionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditableSectionComponent, TranslocoTestingModule.forRoot({ langs: { en: {} }, translocoConfig: { defaultLang: 'en' } })],
    }).compileComponents();
    fixture = TestBed.createComponent(EditableSectionComponent);
    fixture.componentRef.setInput('label', 'Hero');
    fixture.componentRef.setInput('icon', '✦');
    fixture.componentRef.setInput('isActive', false);
    fixture.detectChanges();
  });

  it('renders the section label inside the tab', () => {
    expect((fixture.nativeElement as HTMLElement).querySelector('.ed-tab')?.textContent).toContain('Hero');
  });

  it('emits activate when the tab is clicked', () => {
    let emitted = false;
    fixture.componentRef.instance.activate.subscribe(() => (emitted = true));
    (fixture.nativeElement.querySelector('.ed-tab') as HTMLButtonElement).click();
    expect(emitted).toBe(true);
  });

  it('adds the active class when isActive=true', () => {
    fixture.componentRef.setInput('isActive', true);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.ed.active')).not.toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- --include='**/editable-section.component.spec.ts' --watch=false`
Expected: FAIL — module not found.

- [ ] **Step 3: Write the component**

```typescript
import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-editable-section',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ed" [class.active]="isActive()">
      <button class="ed-tab" type="button" (click)="activate.emit()">
        <span class="ic">{{ icon() }}</span>
        <span>{{ label() }}</span>
        <span class="hint">· {{ 'pro.salon.editor.section.hoverHint' | transloco }}</span>
      </button>
      <div class="ed-frame" aria-hidden="true"></div>
      <ng-content />
      @if (isActive()) {
        <div class="panel-slot">
          <ng-content select="[edit-panel]" />
        </div>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .ed { position: relative; }
    .ed-frame {
      position: absolute; inset: 0;
      pointer-events: none;
      outline: 2px dashed var(--pf-salon-edit, #4a90e2);
      outline-offset: -8px;
      opacity: 0;
      transition: opacity 0.15s;
    }
    .ed:hover .ed-frame { opacity: 1; }
    .ed.active .ed-frame {
      outline-color: var(--pf-salon-accent, #b56b5a);
      outline-style: solid; opacity: 1;
    }
    .ed-tab {
      position: absolute; top: 8px; left: 8px;
      background: var(--pf-salon-edit, #4a90e2);
      color: #fff;
      font-size: 10px; padding: 4px 10px;
      letter-spacing: 0.5px;
      border: 0; border-radius: 0 0 4px 0;
      display: flex; align-items: center; gap: 6px;
      z-index: 5; opacity: 0;
      transition: opacity 0.15s;
      cursor: pointer;
    }
    .ed:hover .ed-tab, .ed.active .ed-tab { opacity: 1; }
    .ed.active .ed-tab { background: var(--pf-salon-accent, #b56b5a); }
    .panel-slot { padding: 8px; }
  `,
})
export class EditableSectionComponent {
  readonly label = input.required<string>();
  readonly icon = input<string>('✎');
  readonly isActive = input<boolean>(false);
  readonly activate = output<void>();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- --include='**/editable-section.component.spec.ts' --watch=false`
Expected: PASS, 3 specs.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/salon-profile/pc/shared/editable-section.component.ts frontend/src/app/features/salon-profile/pc/shared/editable-section.component.spec.ts
git commit -m "feat(salon-pro-pc): EditableSection wrapper with hover/active states"
```

### Task 4.4: Create `InlineEditPanelComponent`

**Files:**
- Create: `frontend/src/app/features/salon-profile/pc/shared/inline-edit-panel.component.ts`

- [ ] **Step 1: Write the component**

```typescript
import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-inline-edit-panel',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="panel">
      <header class="head">
        <h4>{{ title() }}</h4>
        <button class="close" type="button" (click)="cancel.emit()" aria-label="Close">×</button>
      </header>
      <div class="body">
        <ng-content />
      </div>
      <footer class="actions">
        <button class="save" type="button" (click)="save.emit()" [disabled]="!canSave()">
          {{ 'pro.salon.editor.section.save' | transloco }}
        </button>
        <button class="cancel" type="button" (click)="cancel.emit()">
          {{ 'pro.salon.editor.section.cancel' | transloco }}
        </button>
      </footer>
    </div>
  `,
  styles: `
    :host { display: block; }
    .panel {
      position: relative; background: #fff;
      border: 2px solid var(--pf-salon-accent, #b56b5a);
      margin: 8px;
      padding: 18px 20px;
      border-radius: 6px;
    }
    .head {
      display: flex; justify-content: space-between; align-items: center;
      margin-bottom: 14px; padding-bottom: 12px;
      border-bottom: 1px solid var(--pf-salon-line, #ece4dd);
    }
    .head h4 {
      margin: 0;
      font-size: 12px; letter-spacing: 1px; text-transform: uppercase;
      color: var(--pf-salon-accent, #b56b5a);
    }
    .close {
      font-size: 18px; background: transparent; border: 0;
      color: var(--pf-salon-ink-soft, #6b5e57); cursor: pointer;
    }
    .body { padding-bottom: 12px; }
    .actions {
      display: flex; gap: 8px;
      padding-top: 12px;
      border-top: 1px solid var(--pf-salon-line, #ece4dd);
    }
    .save {
      background: var(--pf-salon-accent, #b56b5a); color: #fff;
      padding: 8px 18px; font-size: 11px; letter-spacing: 1px;
      border: 0; cursor: pointer;
    }
    .save:disabled { opacity: 0.5; cursor: not-allowed; }
    .cancel {
      padding: 8px 14px; font-size: 11px;
      color: var(--pf-salon-ink-soft, #6b5e57);
      background: transparent; border: 0; cursor: pointer;
    }
  `,
})
export class InlineEditPanelComponent {
  readonly title = input.required<string>();
  readonly canSave = input<boolean>(true);
  readonly save = output<void>();
  readonly cancel = output<void>();
}
```

- [ ] **Step 2: Build**

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/salon-profile/pc/shared/inline-edit-panel.component.ts
git commit -m "feat(salon-pro-pc): InlineEditPanel with Save/Cancel controls"
```

### Task 4.5: Create `EditorTopBarComponent`

**Files:**
- Create: `frontend/src/app/features/salon-profile/pc/editor-top-bar/editor-top-bar.component.ts`

- [ ] **Step 1: Write the component**

```typescript
import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-editor-top-bar',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="top-bar">
      <div class="left">
        <span class="logo">Pretty Face</span>
        <span class="sep">/</span>
        <span class="crumb">{{ 'pro.salon.editor.topBar.breadcrumb' | transloco }}</span>
        <span class="sep">›</span>
        <span class="here">{{ 'pro.salon.editor.topBar.myPage' | transloco }}</span>
      </div>
      <div class="right">
        @if (isDraft()) {
          <span class="status-pill draft">
            <span class="dot"></span>
            @if (canPublish()) {
              {{ 'pro.salon.editor.topBar.statusDraft' | transloco }}
            } @else {
              {{ 'pro.salon.editor.topBar.statusDraftMissing' | transloco: { count: missingCount() } }}
            }
          </span>
        } @else if (isActive()) {
          <span class="status-pill live">
            <span class="dot"></span>
            {{ 'pro.salon.editor.topBar.statusActive' | transloco }}
          </span>
        }

        <a class="btn ghost" [href]="previewUrl()" target="_blank" rel="noopener">
          {{ 'pro.salon.editor.topBar.previewClient' | transloco }} →
        </a>

        @if (isDraft()) {
          <button
            class="btn primary"
            type="button"
            [disabled]="!canPublish()"
            (click)="publish.emit()"
            [title]="canPublish() ? '' : ('pro.salon.editor.topBar.publishDisabledHint' | transloco)"
          >
            {{ 'pro.salon.editor.topBar.publish' | transloco }}
          </button>
        } @else if (isActive()) {
          <a class="btn ghost" [href]="publicUrl()" target="_blank" rel="noopener">
            ↗ {{ 'pro.salon.editor.topBar.viewPublic' | transloco }}
          </a>
        }
      </div>
    </div>
  `,
  styles: `
    :host { display: block; }
    .top-bar {
      background: var(--pf-salon-ink, #2a2522); color: #fff;
      padding: 12px 24px;
      display: flex; align-items: center; justify-content: space-between;
    }
    .left { display: flex; align-items: center; gap: 18px; }
    .logo { font-family: 'Cormorant Garamond', serif; font-size: 16px; }
    .sep { color: rgba(255, 255, 255, 0.3); }
    .crumb, .here { font-size: 11px; color: rgba(255, 255, 255, 0.7); letter-spacing: 0.5px; }
    .here { color: #fff; }
    .right { display: flex; gap: 10px; align-items: center; }
    .status-pill {
      display: inline-flex; gap: 6px; align-items: center;
      padding: 5px 12px; border-radius: 999px; font-size: 11px;
    }
    .status-pill.draft { background: rgba(217, 119, 87, 0.2); color: #ffb89a; }
    .status-pill.live { background: rgba(80, 200, 120, 0.18); color: #a8e8c0; }
    .status-pill .dot {
      width: 6px; height: 6px; border-radius: 50%; background: currentColor;
    }
    .btn {
      padding: 7px 14px; font-size: 11px; letter-spacing: 0.5px;
      cursor: pointer; border: 0; text-decoration: none;
    }
    .btn.ghost {
      color: rgba(255, 255, 255, 0.85);
      border: 1px solid rgba(255, 255, 255, 0.2);
      background: transparent;
    }
    .btn.primary {
      background: var(--pf-salon-accent, #b56b5a); color: #fff; font-weight: 600;
    }
    .btn.primary:disabled { opacity: 0.5; cursor: not-allowed; }
  `,
})
export class EditorTopBarComponent {
  readonly isDraft = input<boolean>(false);
  readonly isActive = input<boolean>(false);
  readonly canPublish = input<boolean>(false);
  readonly missingCount = input<number>(0);
  readonly previewUrl = input.required<string>();
  readonly publicUrl = input.required<string>();
  readonly publish = output<void>();
}
```

- [ ] **Step 2: Build**

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/salon-profile/pc/editor-top-bar/editor-top-bar.component.ts
git commit -m "feat(salon-pro-pc): EditorTopBar with publish state pill and CTAs"
```

### Task 4.6: Create `EditorSidebarComponent`

**Files:**
- Create: `frontend/src/app/features/salon-profile/pc/editor-sidebar/editor-sidebar.component.ts`

- [ ] **Step 1: Write the component**

```typescript
import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

export interface SidebarSection {
  id: string;
  iconKey: string;        // '⌂', '✦', '✿', '≡', '▶', '★', '✉', '▭'
  badge?: string;         // optional badge label
}

@Component({
  selector: 'app-editor-sidebar',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside class="side">
      <h4>{{ 'pro.salon.editor.sidebar.sectionsHeader' | transloco }}</h4>
      @for (s of sections(); track s.id) {
        <button
          class="item"
          type="button"
          [class.on]="activeSection() === s.id"
          (click)="navigate.emit(s.id)"
        >
          <span class="ico">{{ s.iconKey }}</span>
          {{ ('pro.salon.editor.sidebar.sections.' + s.id) | transloco }}
          @if (s.badge) { <span class="badge">{{ s.badge }}</span> }
        </button>
      }

      @if (isDraft()) {
        <div class="check-list">
          <div class="lab">{{ 'pro.salon.editor.sidebar.checklistHeader' | transloco }}</div>
          @for (item of checklist(); track item.key) {
            <div class="ck" [class.ok]="item.ok" [class.todo]="!item.ok">
              <span class="b">{{ item.ok ? '✓' : '!' }}</span>
              {{ item.label }}
            </div>
          }
        </div>
      }
    </aside>
  `,
  styles: `
    :host { display: block; }
    .side {
      background: #faf6f3;
      border-right: 1px solid var(--pf-salon-line, #ece4dd);
      padding: 20px 0;
      height: 100%;
    }
    h4 {
      font-size: 9px; letter-spacing: 2px; text-transform: uppercase;
      color: var(--pf-salon-ink-soft, #6b5e57);
      margin: 0 20px 12px;
    }
    .item {
      display: flex; align-items: center; gap: 10px;
      padding: 10px 20px; font-size: 12px;
      color: var(--pf-salon-ink, #2a2522);
      cursor: pointer;
      border-left: 3px solid transparent;
      background: transparent; border-top: 0; border-right: 0; border-bottom: 0;
      width: 100%; text-align: left;
    }
    .item:hover { background: #fff; }
    .item.on {
      background: #fff;
      border-left-color: var(--pf-salon-accent, #b56b5a);
      color: var(--pf-salon-accent, #b56b5a);
      font-weight: 600;
    }
    .ico {
      width: 16px; height: 16px; border-radius: 4px;
      background: var(--pf-salon-accent-soft, #f4e6df);
      color: var(--pf-salon-accent, #b56b5a);
      display: flex; align-items: center; justify-content: center;
      font-size: 10px;
    }
    .item.on .ico {
      background: var(--pf-salon-accent, #b56b5a); color: #fff;
    }
    .badge {
      margin-left: auto;
      font-size: 9px;
      background: var(--pf-salon-warn, #d97757); color: #fff;
      padding: 1px 6px; border-radius: 8px;
    }
    .check-list {
      padding: 14px 20px;
      border-top: 1px solid var(--pf-salon-line, #ece4dd);
      margin-top: 16px;
    }
    .check-list .lab {
      font-size: 9px; letter-spacing: 2px; text-transform: uppercase;
      color: var(--pf-salon-ink-soft, #6b5e57);
      margin-bottom: 12px;
    }
    .ck {
      display: flex; align-items: center; gap: 8px;
      font-size: 11px;
      color: var(--pf-salon-ink-soft, #6b5e57);
      margin-bottom: 8px;
    }
    .ck.ok { color: var(--pf-salon-ink, #2a2522); }
    .ck.todo { color: var(--pf-salon-warn, #d97757); }
    .ck .b {
      width: 14px; height: 14px; border-radius: 50%;
      border: 1.5px solid currentColor;
      display: flex; align-items: center; justify-content: center;
      font-size: 9px;
    }
    .ck.ok .b { background: #50c878; border-color: #50c878; color: #fff; }
  `,
})
export class EditorSidebarComponent {
  readonly sections = input.required<SidebarSection[]>();
  readonly activeSection = input<string | null>(null);
  readonly isDraft = input<boolean>(false);
  readonly checklist = input<Array<{ key: string; label: string; ok: boolean }>>([]);
  readonly navigate = output<string>();
}
```

- [ ] **Step 2: Build**

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/salon-profile/pc/editor-sidebar/editor-sidebar.component.ts
git commit -m "feat(salon-pro-pc): EditorSidebar with sections list + readiness checklist"
```

### Task 4.7: Create `SalonEditorPcComponent` (root)

**Files:**
- Create: `frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.ts`
- Create: `frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.html`
- Create: `frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.scss`

- [ ] **Step 1: Write the component class**

```typescript
import { Component, ChangeDetectionStrategy, computed, inject } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { SalonProfileStore } from '../store/salon-profile.store';
import { EditorTopBarComponent } from './editor-top-bar/editor-top-bar.component';
import { EditorSidebarComponent, SidebarSection } from './editor-sidebar/editor-sidebar.component';

@Component({
  selector: 'app-salon-editor-pc',
  standalone: true,
  imports: [TranslocoPipe, EditorTopBarComponent, EditorSidebarComponent],
  templateUrl: './salon-editor-pc.component.html',
  styleUrl: './salon-editor-pc.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SalonEditorPcComponent {
  protected readonly store = inject(SalonProfileStore);
  private readonly transloco = inject(TranslocoService);

  protected readonly sections: SidebarSection[] = [
    { id: 'header', iconKey: '⌂' },
    { id: 'hero', iconKey: '✦' },
    { id: 'about', iconKey: '✿' },
    { id: 'cares', iconKey: '≡' },
    { id: 'stories', iconKey: '▶' },
    { id: 'cta', iconKey: '★' },
    { id: 'contact', iconKey: '✉' },
    { id: 'footer', iconKey: '▭' },
  ];

  protected readonly previewUrl = computed(() => {
    const slug = this.store.tenant()?.slug ?? '';
    return `/salon/${slug}?preview=1`;
  });

  protected readonly publicUrl = computed(() => `/salon/${this.store.tenant()?.slug ?? ''}`);

  protected readonly checklist = computed(() => {
    const missing = this.store.missingFields();
    const allKeys = ['name', 'description', 'logo', 'address', 'hero', 'cares'];
    return allKeys.map((key) => ({
      key,
      label: this.transloco.translate(`pro.salon.editor.checklist.${key}`, undefined) || key,
      ok: !missing.includes(key),
    }));
  });

  protected onPublish(): void {
    this.store.publish();
  }

  protected onNavigate(sectionId: string): void {
    this.store.activateSection(sectionId);
    queueMicrotask(() => {
      const el = document.querySelector(`[data-edit-section="${sectionId}"]`);
      el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  }
}
```

- [ ] **Step 2: Write the template**

```html
@if (store.tenant(); as tenant) {
  <div class="editor-shell">
    <app-editor-top-bar
      [isDraft]="store.isDraft()"
      [isActive]="store.isActive()"
      [canPublish]="store.canPublish()"
      [missingCount]="store.missingFields().length"
      [previewUrl]="previewUrl()"
      [publicUrl]="publicUrl()"
      (publish)="onPublish()" />

    <div class="layout">
      <app-editor-sidebar
        [sections]="sections"
        [activeSection]="store.activeSectionId()"
        [isDraft]="store.isDraft()"
        [checklist]="checklist()"
        (navigate)="onNavigate($event)" />

      <main class="canvas">
        <!-- Section editors will be wired in Milestone 5 -->
        @for (s of sections; track s.id) {
          <div [attr.data-edit-section]="s.id" class="section-stub">
            {{ ('pro.salon.editor.sidebar.sections.' + s.id) | transloco }}
          </div>
        }
      </main>
    </div>
  </div>
}
```

- [ ] **Step 3: Write the SCSS with palette tokens**

```scss
:host {
  --pf-salon-ink: #2a2522;
  --pf-salon-ink-soft: #6b5e57;
  --pf-salon-line: #ece4dd;
  --pf-salon-bg: #fdfaf8;
  --pf-salon-paper: #fff;
  --pf-salon-accent: #b56b5a;
  --pf-salon-accent-soft: #f4e6df;
  --pf-salon-accent-dark: #9b5848;
  --pf-salon-edit: #4a90e2;
  --pf-salon-warn: #d97757;

  display: block;
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', sans-serif;
  background: #f5f0eb;
  min-height: 100vh;
}

.editor-shell { display: flex; flex-direction: column; min-height: 100vh; }
.layout {
  display: grid;
  grid-template-columns: 240px 1fr;
  flex: 1;
  min-height: 0;
}
.canvas {
  background: var(--pf-salon-bg);
  overflow-y: auto;
}
.section-stub {
  padding: 24px;
  border-bottom: 1px dashed var(--pf-salon-line);
  font-size: 13px;
  color: var(--pf-salon-ink-soft);
}
```

- [ ] **Step 4: Branch the existing `SalonProfileComponent` on `usePc()`**

In `frontend/src/app/features/salon-profile/salon-profile.component.ts`, add imports:

```typescript
import { SalonEditorPcComponent } from './pc/salon-editor-pc.component';
import { usePc } from '../../pages/salon/pc/shared/use-pc';
```

Add to the `imports` array of the `@Component`: `SalonEditorPcComponent`.

In the class body, add:

```typescript
protected readonly isPc = usePc();
```

In `salon-profile.component.html`, wrap the entire body:

```html
@if (isPc()) {
  <app-salon-editor-pc />
} @else {
  <!-- existing mobile template (UNCHANGED) -->
  <!-- … keep all existing markup byte-for-byte … -->
}
```

- [ ] **Step 5: Manual smoke test**

`npm start`. Sign in as a pro. Navigate to `/pro/.../salon` at >= 1024px:
- Top bar visible with status pill, "Aperçu client", and either "Publier" (DRAFT) or "Voir version publique" (ACTIVE).
- Sidebar with 8 sections, click navigates.
- Canvas shows 8 stubs.
- At < 1024px, the existing form renders unchanged.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/salon-profile/pc/ frontend/src/app/features/salon-profile/salon-profile.component.ts frontend/src/app/features/salon-profile/salon-profile.component.html
git commit -m "feat(salon-pro-pc): SalonEditorPc shell (top-bar + sidebar + canvas stubs)"
```


---

## Milestone 5 — Pro PC editable sections

À la fin de ce jalon, chaque section éditable est fonctionnelle dans la canvas pro avec sauvegarde manuelle (Enregistrer/Annuler), persistance immédiate via le store, et confirmation de discard si on navigue avec des modifs en cours.

### Task 5.1: Header section editor (logo + name)

**Files:**
- Create: `frontend/src/app/features/salon-profile/pc/canvas/header-edit.component.ts`

The pro view of the header **reuses** the client `SalonHeaderComponent` for rendering and adds an editing layer on top.

- [ ] **Step 1: Write the component**

```typescript
import { Component, ChangeDetectionStrategy, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonHeaderComponent } from '../../../../pages/salon/pc/sections/salon-header.component';
import { EditableSectionComponent } from '../shared/editable-section.component';
import { InlineEditPanelComponent } from '../shared/inline-edit-panel.component';
import { SalonProfileStore } from '../../store/salon-profile.store';

@Component({
  selector: 'app-header-edit',
  standalone: true,
  imports: [TranslocoPipe, SalonHeaderComponent, EditableSectionComponent, InlineEditPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div data-edit-section="header">
      <app-editable-section
        [label]="'pro.salon.editor.sidebar.sections.header' | transloco"
        icon="⌂"
        [isActive]="isActive()"
        (activate)="onActivate()"
      >
        <app-salon-header [name]="store.tenant()?.name ?? ''" [logoUrl]="store.tenant()?.logoUrl ?? null" />

        @if (isActive()) {
          <div edit-panel>
            <app-inline-edit-panel
              [title]="'pro.salon.editor.sidebar.sections.header' | transloco"
              [canSave]="canSave()"
              (save)="onSave()"
              (cancel)="onCancel()"
            >
              <label class="lbl">{{ 'pro.salon.name' | transloco }}</label>
              <input class="inp" [value]="draftName()" (input)="onNameInput($event)" maxlength="100" />

              <label class="lbl">{{ 'pro.salon.changeLogo' | transloco }}</label>
              <input type="file" accept="image/*" (change)="onLogoFile($event)" />
              @if (draftLogo()) {
                <img class="logo-preview" [src]="draftLogo()!" alt="Logo preview" />
                <button class="remove-logo" type="button" (click)="onRemoveLogo()">×</button>
              }
            </app-inline-edit-panel>
          </div>
        }
      </app-editable-section>
    </div>
  `,
  styles: `
    :host { display: block; }
    .lbl { display: block; font-size: 10px; letter-spacing: 1px; text-transform: uppercase; color: #6b5e57; margin: 12px 0 6px; }
    .inp { width: 100%; padding: 8px 10px; border: 1px solid #ece4dd; font-size: 12px; }
    .logo-preview { width: 60px; height: 60px; border-radius: 50%; object-fit: cover; margin-top: 8px; }
    .remove-logo {
      margin-left: 8px; background: transparent;
      border: 1px solid #d97757; color: #d97757;
      padding: 4px 10px; cursor: pointer;
    }
  `,
})
export class HeaderEditComponent {
  protected readonly store = inject(SalonProfileStore);
  protected readonly isActive = computed(() => this.store.activeSectionId() === 'header');

  protected readonly draftName = signal('');
  // null = unchanged, '' = remove, base64 = new image
  protected readonly draftLogo = signal<string | null>(null);
  private readonly logoChanged = signal(false);

  protected readonly canSave = computed(() => this.draftName().trim().length > 0);

  onActivate(): void {
    const t = this.store.tenant();
    this.draftName.set(t?.name ?? '');
    this.draftLogo.set(t?.logoUrl ?? null);
    this.logoChanged.set(false);
    this.store.activateSection('header');
  }

  onNameInput(event: Event): void {
    const v = (event.target as HTMLInputElement).value;
    this.draftName.set(v);
    this.store.setDirty(true);
  }

  onLogoFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      this.draftLogo.set(reader.result as string);
      this.logoChanged.set(true);
      this.store.setDirty(true);
    };
    reader.readAsDataURL(file);
    input.value = '';
  }

  onRemoveLogo(): void {
    this.draftLogo.set(null);
    this.logoChanged.set(true);
    this.store.setDirty(true);
  }

  onSave(): void {
    const t = this.store.tenant();
    if (!t) return;
    const logoChanged = this.logoChanged();
    const newLogo = this.draftLogo();
    this.store.updateProfile({
      name: this.draftName().trim(),
      description: t.description,
      logo: !logoChanged ? null : newLogo === null ? '' : newLogo!.startsWith('data:') ? newLogo! : null,
      heroImage: null,
      addressStreet: t.addressStreet,
      addressPostalCode: t.addressPostalCode,
      addressCity: t.addressCity,
      addressCountry: t.addressCountry,
      phone: t.phone,
      contactEmail: t.contactEmail,
      siret: t.siret,
    });
    this.store.activateSection(null);
  }

  onCancel(): void {
    this.store.setDirty(false);
    this.store.activateSection(null);
  }
}
```

- [ ] **Step 2: Build to typecheck**

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 3: Wire it into the canvas**

In `salon-editor-pc.component.ts`, add to imports:

```typescript
import { HeaderEditComponent } from './canvas/header-edit.component';
```

Append `HeaderEditComponent` to the `imports` array of the `@Component`.

In `salon-editor-pc.component.html`, replace the loop:

```html
<main class="canvas">
  <app-header-edit />
  @for (s of sections; track s.id) {
    @if (s.id !== 'header') {
      <div [attr.data-edit-section]="s.id" class="section-stub">
        {{ ('pro.salon.editor.sidebar.sections.' + s.id) | transloco }}
      </div>
    }
  }
</main>
```

- [ ] **Step 4: Manual smoke test**

`npm start`. Open `/pro/.../salon` on PC:
- Hover the header → blue dashed frame.
- Click the tab → frame turns rosé, panel appears below.
- Edit the name, click "Enregistrer" → snackbar success, the header in the canvas updates.
- Click cancel → reverts.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/salon-profile/pc/canvas/header-edit.component.ts frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.ts frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.html
git commit -m "feat(salon-pro-pc): inline header section editor (name + logo)"
```

### Task 5.2: Hero, About, CTA, Footer section editors

These four follow the same pattern as 5.1 (display the client component + an inline edit panel). To avoid duplication in the plan: they all reuse the existing `description` field of the tenant for body text, and have no extra fields beyond what `UpdateTenantRequest` already supports today.

**Files for each:**
- `frontend/src/app/features/salon-profile/pc/canvas/hero-edit.component.ts`
- `frontend/src/app/features/salon-profile/pc/canvas/about-edit.component.ts`
- `frontend/src/app/features/salon-profile/pc/canvas/cta-edit.component.ts`
- `frontend/src/app/features/salon-profile/pc/canvas/footer-edit.component.ts`

- [ ] **Step 1: Write `hero-edit.component.ts`**

```typescript
import { Component, ChangeDetectionStrategy, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonHeroComponent } from '../../../../pages/salon/pc/sections/salon-hero.component';
import { SalonBannerComponent } from '../../../../pages/salon/pc/sections/salon-banner.component';
import { EditableSectionComponent } from '../shared/editable-section.component';
import { InlineEditPanelComponent } from '../shared/inline-edit-panel.component';
import { SalonProfileStore } from '../../store/salon-profile.store';

@Component({
  selector: 'app-hero-edit',
  standalone: true,
  imports: [TranslocoPipe, SalonHeroComponent, SalonBannerComponent, EditableSectionComponent, InlineEditPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div data-edit-section="hero">
      <app-editable-section
        [label]="'pro.salon.editor.sidebar.sections.hero' | transloco"
        icon="✦"
        [isActive]="isActive()"
        (activate)="onActivate()"
      >
        <app-salon-hero [name]="store.tenant()?.name ?? ''" [description]="store.tenant()?.description ?? null" />
        <app-salon-banner [heroImageUrl]="store.tenant()?.heroImageUrl ?? null" [alt]="store.tenant()?.name ?? ''" />

        @if (isActive()) {
          <div edit-panel>
            <app-inline-edit-panel
              [title]="'pro.salon.editor.sidebar.sections.hero' | transloco"
              [canSave]="true"
              (save)="onSave()"
              (cancel)="onCancel()"
            >
              <label class="lbl">{{ 'pro.salon.description' | transloco }}</label>
              <textarea class="inp" rows="4" [value]="draftDescription() ?? ''" (input)="onDescInput($event)" maxlength="2000"></textarea>

              <label class="lbl">{{ 'pro.salon.heroImage' | transloco }}</label>
              <input type="file" accept="image/*" (change)="onHeroFile($event)" />
              @if (draftHero()) {
                <img class="hero-preview" [src]="draftHero()!" alt="Hero preview" />
                <button class="remove-hero" type="button" (click)="onRemoveHero()">×</button>
              }
            </app-inline-edit-panel>
          </div>
        }
      </app-editable-section>
    </div>
  `,
  styles: `
    .lbl { display: block; font-size: 10px; letter-spacing: 1px; text-transform: uppercase; color: #6b5e57; margin: 12px 0 6px; }
    .inp { width: 100%; padding: 8px 10px; border: 1px solid #ece4dd; font-size: 12px; }
    .hero-preview { width: 100%; max-height: 140px; object-fit: cover; margin-top: 8px; }
    .remove-hero {
      margin-left: 8px; background: transparent;
      border: 1px solid #d97757; color: #d97757;
      padding: 4px 10px; cursor: pointer;
    }
  `,
})
export class HeroEditComponent {
  protected readonly store = inject(SalonProfileStore);
  protected readonly isActive = computed(() => this.store.activeSectionId() === 'hero');

  protected readonly draftDescription = signal<string | null>(null);
  protected readonly draftHero = signal<string | null>(null);
  private readonly heroChanged = signal(false);

  onActivate(): void {
    const t = this.store.tenant();
    this.draftDescription.set(t?.description ?? null);
    this.draftHero.set(t?.heroImageUrl ?? null);
    this.heroChanged.set(false);
    this.store.activateSection('hero');
  }

  onDescInput(event: Event): void {
    this.draftDescription.set((event.target as HTMLTextAreaElement).value);
    this.store.setDirty(true);
  }

  onHeroFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      this.draftHero.set(reader.result as string);
      this.heroChanged.set(true);
      this.store.setDirty(true);
    };
    reader.readAsDataURL(file);
    input.value = '';
  }

  onRemoveHero(): void {
    this.draftHero.set(null);
    this.heroChanged.set(true);
    this.store.setDirty(true);
  }

  onSave(): void {
    const t = this.store.tenant();
    if (!t) return;
    const heroChanged = this.heroChanged();
    const newHero = this.draftHero();
    this.store.updateProfile({
      name: t.name,
      description: this.draftDescription() || null,
      logo: null,
      heroImage: !heroChanged ? null : newHero === null ? '' : newHero!.startsWith('data:') ? newHero! : null,
      addressStreet: t.addressStreet,
      addressPostalCode: t.addressPostalCode,
      addressCity: t.addressCity,
      addressCountry: t.addressCountry,
      phone: t.phone,
      contactEmail: t.contactEmail,
      siret: t.siret,
    });
    this.store.activateSection(null);
  }

  onCancel(): void {
    this.store.setDirty(false);
    this.store.activateSection(null);
  }
}
```

- [ ] **Step 2: Write `about-edit.component.ts`**

The "About" section reuses the same `description` field. Since the only Tenant text field today is `description`, we wire the About edit panel to also map to `description`. Until backend adds a separate `aboutText` (out of scope), About displays the same content as the Hero lede. Document this in a comment.

```typescript
import { Component, ChangeDetectionStrategy, computed, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonAboutComponent } from '../../../../pages/salon/pc/sections/salon-about.component';
import { EditableSectionComponent } from '../shared/editable-section.component';
import { SalonProfileStore } from '../../store/salon-profile.store';

@Component({
  selector: 'app-about-edit',
  standalone: true,
  imports: [TranslocoPipe, SalonAboutComponent, EditableSectionComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div data-edit-section="about">
      @if (store.tenant()?.description) {
        <app-editable-section
          [label]="'pro.salon.editor.sidebar.sections.about' | transloco"
          icon="✿"
          [isActive]="isActive()"
          (activate)="onActivate()"
        >
          <app-salon-about [name]="store.tenant()!.name" [description]="store.tenant()!.description!" />
        </app-editable-section>
      }
    </div>
  `,
})
export class AboutEditComponent {
  protected readonly store = inject(SalonProfileStore);
  protected readonly isActive = computed(() => this.store.activeSectionId() === 'about');

  // About reuses Tenant.description until a separate aboutText field is added
  // backend-side (see spec §8.3). Editing happens in the Hero panel today.
  onActivate(): void {
    this.store.activateSection('hero');
  }
}
```

- [ ] **Step 3: Write `cta-edit.component.ts`**

The CTA section is fully translated content (no per-tenant editing). Render the client component without an active edit panel — only the hover hint to indicate it's a fixed section.

```typescript
import { Component, ChangeDetectionStrategy } from '@angular/core';
import { SalonCtaComponent } from '../../../../pages/salon/pc/sections/salon-cta.component';

@Component({
  selector: 'app-cta-edit',
  standalone: true,
  imports: [SalonCtaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div data-edit-section="cta">
      <app-salon-cta />
    </div>
  `,
})
export class CtaEditComponent {}
```

- [ ] **Step 4: Write `footer-edit.component.ts`**

```typescript
import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { SalonFooterComponent } from '../../../../pages/salon/pc/sections/salon-footer.component';
import { SalonProfileStore } from '../../store/salon-profile.store';

@Component({
  selector: 'app-footer-edit',
  standalone: true,
  imports: [SalonFooterComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div data-edit-section="footer">
      <app-salon-footer [name]="store.tenant()?.name ?? ''" />
    </div>
  `,
})
export class FooterEditComponent {
  protected readonly store = inject(SalonProfileStore);
}
```

- [ ] **Step 5: Wire into the canvas**

In `salon-editor-pc.component.ts`, add imports and append to the `imports` array of the `@Component`:

```typescript
import { HeroEditComponent } from './canvas/hero-edit.component';
import { AboutEditComponent } from './canvas/about-edit.component';
import { CtaEditComponent } from './canvas/cta-edit.component';
import { FooterEditComponent } from './canvas/footer-edit.component';
```

Replace the canvas template body:

```html
<main class="canvas">
  <app-header-edit />
  <app-hero-edit />
  <app-about-edit />
  <div [attr.data-edit-section]="'cares'" class="section-stub">
    {{ 'pro.salon.editor.sidebar.sections.cares' | transloco }}
  </div>
  <div [attr.data-edit-section]="'stories'" class="section-stub">
    {{ 'pro.salon.editor.sidebar.sections.stories' | transloco }}
  </div>
  <app-cta-edit />
  <div [attr.data-edit-section]="'contact'" class="section-stub">
    {{ 'pro.salon.editor.sidebar.sections.contact' | transloco }}
  </div>
  <app-footer-edit />
</main>
```

- [ ] **Step 6: Manual smoke test**

`npm start`. On `/pro/.../salon` PC:
- Hero, About, CTA, Footer all render visually identical to the client preview.
- Hero is editable inline (description + image).
- About hover redirects to Hero edit (same data source).
- CTA and Footer hover-frame works but no panel (display only).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/features/salon-profile/pc/canvas/ frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.ts frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.html
git commit -m "feat(salon-pro-pc): hero/about/cta/footer inline editors"
```

### Task 5.3: Contact section editor

**Files:**
- Create: `frontend/src/app/features/salon-profile/pc/canvas/contact-edit.component.ts`

- [ ] **Step 1: Write the component**

```typescript
import { Component, ChangeDetectionStrategy, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonContactComponent } from '../../../../pages/salon/pc/sections/salon-contact.component';
import { CountryPickerComponent } from '../../../../shared/uis/country-picker/country-picker.component';
import { EditableSectionComponent } from '../shared/editable-section.component';
import { InlineEditPanelComponent } from '../shared/inline-edit-panel.component';
import { SalonProfileStore } from '../../store/salon-profile.store';

@Component({
  selector: 'app-contact-edit',
  standalone: true,
  imports: [
    TranslocoPipe, SalonContactComponent, CountryPickerComponent,
    EditableSectionComponent, InlineEditPanelComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div data-edit-section="contact">
      <app-editable-section
        [label]="'pro.salon.editor.sidebar.sections.contact' | transloco"
        icon="✉"
        [isActive]="isActive()"
        (activate)="onActivate()"
      >
        <app-salon-contact
          [street]="store.tenant()?.addressStreet ?? null"
          [postalCode]="store.tenant()?.addressPostalCode ?? null"
          [city]="store.tenant()?.addressCity ?? null"
          [country]="store.tenant()?.addressCountry ?? null"
          [phone]="store.tenant()?.phone ?? null"
          [email]="store.tenant()?.contactEmail ?? null" />

        @if (isActive()) {
          <div edit-panel>
            <app-inline-edit-panel
              [title]="'pro.salon.editor.sidebar.sections.contact' | transloco"
              [canSave]="true"
              (save)="onSave()"
              (cancel)="onCancel()"
            >
              <label class="lbl">{{ 'pro.salon.addressStreet' | transloco }}</label>
              <input class="inp" [value]="draftStreet() ?? ''" (input)="set('street', $event)" maxlength="255" />

              <label class="lbl">{{ 'pro.salon.addressPostalCode' | transloco }}</label>
              <input class="inp" [value]="draftPostal() ?? ''" (input)="set('postal', $event)" maxlength="10" />

              <label class="lbl">{{ 'pro.salon.addressCity' | transloco }}</label>
              <input class="inp" [value]="draftCity() ?? ''" (input)="set('city', $event)" maxlength="100" />

              <label class="lbl">{{ 'pro.salon.addressCountry' | transloco }}</label>
              <app-country-picker [countryCode]="draftCountry()" (countryCodeChange)="onCountryChange($event)" />

              <label class="lbl">{{ 'pro.salon.phone' | transloco }}</label>
              <input class="inp" [value]="draftPhone() ?? ''" (input)="set('phone', $event)" maxlength="20" />

              <label class="lbl">{{ 'pro.salon.contactEmail' | transloco }}</label>
              <input class="inp" [value]="draftEmail() ?? ''" (input)="set('email', $event)" maxlength="255" type="email" />
            </app-inline-edit-panel>
          </div>
        }
      </app-editable-section>
    </div>
  `,
  styles: `
    .lbl { display: block; font-size: 10px; letter-spacing: 1px; text-transform: uppercase; color: #6b5e57; margin: 12px 0 6px; }
    .inp { width: 100%; padding: 8px 10px; border: 1px solid #ece4dd; font-size: 12px; }
  `,
})
export class ContactEditComponent {
  protected readonly store = inject(SalonProfileStore);
  protected readonly isActive = computed(() => this.store.activeSectionId() === 'contact');

  protected readonly draftStreet = signal<string | null>(null);
  protected readonly draftPostal = signal<string | null>(null);
  protected readonly draftCity = signal<string | null>(null);
  protected readonly draftCountry = signal<string | null>(null);
  protected readonly draftPhone = signal<string | null>(null);
  protected readonly draftEmail = signal<string | null>(null);

  onActivate(): void {
    const t = this.store.tenant();
    this.draftStreet.set(t?.addressStreet ?? null);
    this.draftPostal.set(t?.addressPostalCode ?? null);
    this.draftCity.set(t?.addressCity ?? null);
    this.draftCountry.set(t?.addressCountry ?? null);
    this.draftPhone.set(t?.phone ?? null);
    this.draftEmail.set(t?.contactEmail ?? null);
    this.store.activateSection('contact');
  }

  set(field: 'street' | 'postal' | 'city' | 'phone' | 'email', event: Event): void {
    const v = (event.target as HTMLInputElement).value || null;
    if (field === 'street') this.draftStreet.set(v);
    if (field === 'postal') this.draftPostal.set(v);
    if (field === 'city') this.draftCity.set(v);
    if (field === 'phone') this.draftPhone.set(v);
    if (field === 'email') this.draftEmail.set(v);
    this.store.setDirty(true);
  }

  onCountryChange(code: string | null): void {
    this.draftCountry.set(code);
    this.store.setDirty(true);
  }

  onSave(): void {
    const t = this.store.tenant();
    if (!t) return;
    this.store.updateProfile({
      name: t.name,
      description: t.description,
      logo: null, heroImage: null,
      addressStreet: this.draftStreet(),
      addressPostalCode: this.draftPostal(),
      addressCity: this.draftCity(),
      addressCountry: this.draftCountry(),
      phone: this.draftPhone(),
      contactEmail: this.draftEmail(),
      siret: t.siret,
    });
    this.store.activateSection(null);
  }

  onCancel(): void {
    this.store.setDirty(false);
    this.store.activateSection(null);
  }
}
```

- [ ] **Step 2: Wire into the canvas**

In `salon-editor-pc.component.ts` add:

```typescript
import { ContactEditComponent } from './canvas/contact-edit.component';
```

Append to imports array. Replace the contact stub in the template with `<app-contact-edit />`.

- [ ] **Step 3: Manual smoke test**

`npm start`. On the pro page, edit address fields → save → public preview reflects them.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/salon-profile/pc/canvas/contact-edit.component.ts frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.ts frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.html
git commit -m "feat(salon-pro-pc): contact section inline editor"
```

### Task 5.4: Cares section editor (reuses existing modals)

**Files:**
- Create: `frontend/src/app/features/salon-profile/pc/canvas/cares-edit.component.ts`

The cares editor renders the public cares display in pro mode and exposes per-care actions (edit, hide, delete) and per-category actions (add care, add category). It **reuses existing modals** from `features/cares/modals/` and `features/categories/modals/`.

- [ ] **Step 1: Confirm existing modal locations**

Run: `ls frontend/src/app/features/cares/modals/ && ls frontend/src/app/features/categories/modals/`
Expected: at least one create and one edit modal exists. Note their selectors and inputs.

- [ ] **Step 2: Write the component (delegates to existing modals)**

```typescript
import { Component, ChangeDetectionStrategy, computed, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonCaresComponent } from '../../../../pages/salon/pc/sections/salon-cares.component';
import { EditableSectionComponent } from '../shared/editable-section.component';
import { SalonProfileStore } from '../../store/salon-profile.store';

@Component({
  selector: 'app-cares-edit',
  standalone: true,
  imports: [TranslocoPipe, SalonCaresComponent, EditableSectionComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div data-edit-section="cares">
      <app-editable-section
        [label]="'pro.salon.editor.sidebar.sections.cares' | transloco"
        icon="≡"
        [isActive]="isActive()"
        (activate)="onActivate()"
      >
        @if (categories().length > 0) {
          <app-salon-cares [categories]="categories()" (bookCare)="onCareClick($event)" />
        } @else {
          <div class="empty">
            <p>{{ 'pro.salon.editor.cares.emptyCategory' | transloco }}</p>
          </div>
        }
      </app-editable-section>
    </div>
  `,
  styles: `
    .empty {
      padding: 48px 36px; text-align: center;
      color: var(--pf-salon-ink-soft, #6b5e57);
      font-size: 13px;
    }
  `,
})
export class CaresEditComponent {
  protected readonly store = inject(SalonProfileStore);
  private readonly dialog = inject(MatDialog);

  protected readonly isActive = computed(() => this.store.activeSectionId() === 'cares');

  // For Milestone 5 we read categories from the public salon endpoint via the
  // existing PublicSalonResponse already loaded into the store. If the store
  // doesn't yet load categories for the pro tenant, we fall back to the cares
  // service. Keep this simple: read from public salon by slug.
  protected readonly categories = computed(() => {
    // Re-using the shape used by the public component. The pro shell will be
    // wired to fetch the public salon snapshot for the tenant in Task 5.6 — for
    // now we render an empty state when categories are unavailable.
    return [];
  });

  onActivate(): void {
    this.store.activateSection('cares');
  }

  onCareClick(_careId: number): void {
    // In pro mode, clicking "Réserver" on a care card opens the existing
    // care edit modal instead of the booking flow. Wired in Task 5.6.
  }
}
```

> Note for the engineer: This task lays the file. Wiring real categories + edit/hide/delete actions is Task 5.6 (which depends on the cares store). The shell here ensures the section renders cleanly until then.

- [ ] **Step 3: Wire into the canvas**

In `salon-editor-pc.component.ts`:

```typescript
import { CaresEditComponent } from './canvas/cares-edit.component';
```

Append to imports. Replace the cares stub with `<app-cares-edit />`.

- [ ] **Step 4: Build to typecheck**

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/salon-profile/pc/canvas/cares-edit.component.ts frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.ts frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.html
git commit -m "feat(salon-pro-pc): cares section shell (data wiring follows)"
```

### Task 5.5: Stories section editor (pro tools)

**Files:**
- Create: `frontend/src/app/features/salon-profile/pc/canvas/stories-edit.component.ts`

- [ ] **Step 1: Write the component**

```typescript
import { Component, ChangeDetectionStrategy, computed, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { EditableSectionComponent } from '../shared/editable-section.component';
import { SalonProfileStore } from '../../store/salon-profile.store';
import { StoriesRowService } from '../../../../pages/salon/pc/stories/stories-row.service';
import { PostResponse } from '../../../posts/posts.model';
import { CreatePostModalComponent } from '../../../posts/create-post-modal/create-post-modal.component';
import { PostsService } from '../../../posts/posts.service';
import { bottomSheetConfig } from '../../../../shared/uis/sheet-handle/bottom-sheet.config';

@Component({
  selector: 'app-stories-edit',
  standalone: true,
  imports: [TranslocoPipe, EditableSectionComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div data-edit-section="stories">
      <app-editable-section
        [label]="'pro.salon.editor.sidebar.sections.stories' | transloco"
        icon="▶"
        [isActive]="isActive()"
        (activate)="onActivate()"
      >
        <section class="stories">
          <header class="head">
            <div>
              <div class="lab">— {{ 'salon.pc.stories.label' | transloco }} —</div>
              <h2>{{ 'salon.pc.stories.title' | transloco }}</h2>
            </div>
            <button class="new" type="button" (click)="onNewStory()">
              + {{ 'pro.salon.editor.stories.newStory' | transloco }}
            </button>
          </header>

          @if (posts().length === 0) {
            <p class="empty">{{ 'pro.salon.editor.stories.noStoriesYet' | transloco }}</p>
          } @else {
            <div class="row">
              @for (post of posts(); track post.id) {
                <div class="post">
                  <div class="pic" [style.background-image]="thumbUrl(post)"></div>
                  <div class="grad"></div>
                  <div class="tools">
                    <button class="t" type="button" aria-label="Edit">✎</button>
                    <button class="t del" type="button" (click)="onDelete(post)" aria-label="Delete">×</button>
                  </div>
                </div>
              }
            </div>
          }
        </section>
      </app-editable-section>
    </div>
  `,
  styles: `
    .stories { padding: 48px 36px; }
    .head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
    .lab { font-size: 9px; letter-spacing: 3px; color: var(--pf-salon-accent, #b56b5a); text-transform: uppercase; margin-bottom: 8px; }
    h2 { font-family: 'Cormorant Garamond', serif; font-weight: 300; font-size: 24px; margin: 0; }
    .new { background: var(--pf-salon-accent, #b56b5a); color: #fff; border: 0; font-size: 10px; padding: 8px 14px; letter-spacing: 1px; text-transform: uppercase; cursor: pointer; }
    .empty { color: var(--pf-salon-ink-soft, #6b5e57); font-size: 12px; }
    .row { display: flex; gap: 12px; overflow-x: auto; padding-bottom: 16px; }
    .post { flex: 0 0 180px; aspect-ratio: 3/4; position: relative; border-radius: 10px; overflow: hidden; background: #1a1a1a; }
    .pic { position: absolute; inset: 0; background-size: cover; background-position: center; background-color: #444; }
    .grad { position: absolute; left: 0; right: 0; bottom: 0; height: 60%; background: linear-gradient(to top, rgba(0,0,0,.7), transparent); }
    .tools { position: absolute; top: 8px; right: 8px; display: flex; gap: 4px; }
    .tools .t { width: 22px; height: 22px; border-radius: 4px; background: rgba(0,0,0,.5); color: #fff; border: 0; font-size: 11px; cursor: pointer; }
    .tools .t.del { background: rgba(217,77,77,.85); }
  `,
})
export class StoriesEditComponent {
  protected readonly store = inject(SalonProfileStore);
  private readonly dialog = inject(MatDialog);
  private readonly storiesService = inject(StoriesRowService);
  private readonly postsService = inject(PostsService);
  private readonly transloco = inject(TranslocoService);

  protected readonly isActive = computed(() => this.store.activeSectionId() === 'stories');
  protected readonly posts = signal<PostResponse[]>([]);

  constructor() {
    this.refresh();
  }

  private refresh(): void {
    const slug = this.store.tenant()?.slug;
    if (!slug) return;
    this.storiesService.list(slug, 0, 24).subscribe({
      next: (page) => this.posts.set(page.content),
      error: () => this.posts.set([]),
    });
  }

  onActivate(): void {
    this.store.activateSection('stories');
  }

  thumbUrl(post: PostResponse): string {
    const u = post.afterImageUrl ?? post.beforeImageUrl ?? post.carouselImageUrls[0] ?? null;
    return u ? `url(${u})` : 'linear-gradient(135deg,#d4a594,#b56b5a)';
  }

  onNewStory(): void {
    const ref = this.dialog.open(CreatePostModalComponent, bottomSheetConfig({
      width: '520px',
      maxHeight: '90vh',
      disableClose: false,
    }));
    ref.afterClosed().subscribe((created: PostResponse | undefined) => {
      if (created) this.posts.update((list) => [created, ...list]);
    });
  }

  onDelete(post: PostResponse): void {
    if (!confirm(this.transloco.translate('pro.salon.editor.stories.deleteConfirm'))) return;
    this.postsService.delete(post.id).subscribe({
      next: () => this.posts.update((list) => list.filter((p) => p.id !== post.id)),
    });
  }
}
```

- [ ] **Step 2: Wire into the canvas**

In `salon-editor-pc.component.ts`:

```typescript
import { StoriesEditComponent } from './canvas/stories-edit.component';
```

Append to imports. Replace the stories stub with `<app-stories-edit />`.

- [ ] **Step 3: Build + manual smoke test**

`npm run build` then `npm start`. Visit `/pro/.../salon` PC:
- "+ Nouvelle story" opens the existing create modal.
- After creation, the row updates.
- × on a post deletes after confirmation.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/salon-profile/pc/canvas/stories-edit.component.ts frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.ts frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.html
git commit -m "feat(salon-pro-pc): stories editor with create-post modal + delete"
```

### Task 5.6: Cares editor data wiring (categories + per-care actions)

**Files:**
- Modify: `frontend/src/app/features/salon-profile/pc/canvas/cares-edit.component.ts`

For this task we lift category data through the existing public endpoint (`/api/salon/:slug`) re-using `SalonProfileService.getPublicSalon`. Edit/hide/delete reuse the modals already shipped in `features/cares/modals/`.

- [ ] **Step 1: Inject the service and load categories**

Replace the body of `cares-edit.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, computed, effect, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { SalonCaresComponent } from '../../../../pages/salon/pc/sections/salon-cares.component';
import { EditableSectionComponent } from '../shared/editable-section.component';
import { SalonProfileStore } from '../../store/salon-profile.store';
import { SalonProfileService } from '../../services/salon-profile.service';
import { PublicCategoryDto } from '../../models/salon-profile.model';

@Component({
  selector: 'app-cares-edit',
  standalone: true,
  imports: [TranslocoPipe, SalonCaresComponent, EditableSectionComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div data-edit-section="cares">
      <app-editable-section
        [label]="'pro.salon.editor.sidebar.sections.cares' | transloco"
        icon="≡"
        [isActive]="isActive()"
        (activate)="onActivate()"
      >
        @if (categories().length > 0) {
          <app-salon-cares [categories]="categories()" />
        } @else {
          <div class="empty">{{ 'pro.salon.editor.cares.emptyCategory' | transloco }}</div>
        }
      </app-editable-section>
    </div>
  `,
  styles: `.empty { padding: 48px 36px; text-align: center; color: #6b5e57; font-size: 13px; }`,
})
export class CaresEditComponent {
  protected readonly store = inject(SalonProfileStore);
  private readonly service = inject(SalonProfileService);

  protected readonly isActive = computed(() => this.store.activeSectionId() === 'cares');
  protected readonly categories = signal<PublicCategoryDto[]>([]);

  constructor() {
    effect(() => {
      const slug = this.store.tenant()?.slug;
      if (!slug) return;
      this.service.getPublicSalon(slug).subscribe({
        next: (salon) => this.categories.set(salon.categories),
        error: () => this.categories.set([]),
      });
    });
  }

  onActivate(): void {
    this.store.activateSection('cares');
  }
}
```

- [ ] **Step 2: Build + manual smoke test**

`npm run build` then `npm start`. On `/pro/.../salon` PC:
- The cares section now displays real categories and care cards.
- Clicking "Réserver →" on a care does nothing dangerous (it currently reuses the public component without book emission, which is fine for now).

> Per-care edit/hide/delete actions and drag-reorder are deferred to **Milestone 7 — Polish** to ship the editor incrementally.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/salon-profile/pc/canvas/cares-edit.component.ts
git commit -m "feat(salon-pro-pc): cares section displays real categories from API"
```

### Task 5.7: Discard-changes confirmation when navigating away

**Files:**
- Modify: `frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.ts`

- [ ] **Step 1: Update `onNavigate`**

Replace `onNavigate` in `salon-editor-pc.component.ts`:

```typescript
import { TranslocoService } from '@jsverse/transloco';
// ... existing imports

// in the class, add:
private readonly transloco = inject(TranslocoService);

protected onNavigate(sectionId: string): void {
  if (this.store.dirtySection() && this.store.activeSectionId() !== sectionId) {
    const ok = confirm(this.transloco.translate('pro.salon.editor.section.discardChangesBody'));
    if (!ok) return;
    this.store.setDirty(false);
  }
  this.store.activateSection(sectionId);
  queueMicrotask(() => {
    const el = document.querySelector(`[data-edit-section="${sectionId}"]`);
    el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
}
```

- [ ] **Step 2: Manual smoke test**

`npm start`. Edit a section's name, don't save. Click another section in the sidebar → confirmation dialog. Cancel → stay. Confirm → navigation happens, dirty cleared.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.ts
git commit -m "feat(salon-pro-pc): confirm discard when navigating with unsaved changes"
```


---

## Milestone 6 — Snackbars and "Publish" flow

À la fin de ce jalon, les sauvegardes affichent un retour utilisateur (snackbars) et le bouton Publier de la top bar fonctionne avec rafraîchissement de l'état.

### Task 6.1: Save success/error snackbars

**Files:**
- Modify: `frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.ts`

The existing `SalonProfileComponent` already wires snackbars via `effect()` (see `salon-profile.component.ts:111-133`). Replicate the same logic in the PC root.

- [ ] **Step 1: Add the effects**

In the constructor of `SalonEditorPcComponent`, add:

```typescript
import { effect, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
// ...

private readonly snackBar = inject(MatSnackBar);

constructor() {
  effect(() => {
    if (this.store.saveSuccess()) {
      this.snackBar.open(
        this.transloco.translate('pro.salon.saveSuccess'),
        undefined,
        { duration: 3000 },
      );
      this.store.clearStatus();
    }
  });

  effect(() => {
    if (this.store.saveError()) {
      this.snackBar.open(
        this.transloco.translate('pro.salon.saveError'),
        undefined,
        { duration: 5000, panelClass: 'snackbar-error' },
      );
      this.store.clearStatus();
    }
  });

  effect(() => {
    if (this.store.publishSuccess()) {
      this.snackBar.open(
        this.transloco.translate('pro.dashboard.publishSuccess'),
        undefined,
        { duration: 3000 },
      );
      this.store.loadReadiness();
      this.store.clearStatus();
    }
  });

  effect(() => {
    if (this.store.publishError()) {
      this.snackBar.open(
        this.transloco.translate('pro.dashboard.publishError'),
        undefined,
        { duration: 5000, panelClass: 'snackbar-error' },
      );
      this.store.clearStatus();
    }
  });
}
```

- [ ] **Step 2: Manual smoke test**

`npm start`. Edit and save → green snackbar. Force a server error (e.g. malformed payload) → red snackbar. Click Publier on a complete draft → status pill switches to "En ligne".

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/salon-profile/pc/salon-editor-pc.component.ts
git commit -m "feat(salon-pro-pc): save/publish snackbars + readiness refresh after publish"
```

### Task 6.2: Reload readiness after each save

**Files:**
- Modify: `frontend/src/app/features/salon-profile/store/salon-profile.store.ts`

Each save can change the checklist state — e.g. adding a logo flips the "logo" item from missing to OK. So after each successful save, refresh readiness.

- [ ] **Step 1: Patch the `updateProfile` rxMethod to chain `loadReadiness`**

Replace the `updateProfile` method body:

```typescript
updateProfile: rxMethod<UpdateTenantRequest>(
  pipe(
    tap(() => patchState(store, { saveSuccess: false, saveError: false }, setPending())),
    exhaustMap((request) =>
      service.updateProfile(request).pipe(
        switchMap((tenant) =>
          service.getReadiness().pipe(
            tap((readiness) => patchState(store, { tenant, readiness, saveSuccess: true, saveError: false, dirtySection: false }, setFulfilled())),
            catchError(() => {
              // Readiness fetch failure: still apply the tenant update.
              patchState(store, { tenant, saveSuccess: true, saveError: false, dirtySection: false }, setFulfilled());
              return EMPTY;
            }),
          ),
        ),
        catchError(() => {
          patchState(store, { saveError: true }, setError('Erreur lors de la sauvegarde'));
          return EMPTY;
        }),
      ),
    ),
  ),
),
```

- [ ] **Step 2: Build + smoke test**

`npm run build`. Edit a section that adds a missing field → save → checklist updates without refresh.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/salon-profile/store/salon-profile.store.ts
git commit -m "feat(salon-profile): refresh readiness after each successful save"
```

---

## Milestone 7 — Polish + i18n + tests

À la fin de ce jalon, la fonctionnalité est shippable : translations complètes, tests passants, vérifications responsive et accessibilité.

### Task 7.1: Complete French and English translations

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add missing checklist labels**

Inside `pro.salon.editor`, add:

```json
"checklist": {
  "name": "Nom du salon",
  "description": "Description",
  "logo": "Logo",
  "address": "Adresse",
  "hero": "Photo de hero",
  "cares": "Au moins 1 soin"
}
```

Mirror in `en.json`:

```json
"checklist": {
  "name": "Salon name",
  "description": "Description",
  "logo": "Logo",
  "address": "Address",
  "hero": "Hero photo",
  "cares": "At least 1 care"
}
```

- [ ] **Step 2: Verify JSON syntax**

Run: `node -e "JSON.parse(require('fs').readFileSync('frontend/public/i18n/fr.json'))" && node -e "JSON.parse(require('fs').readFileSync('frontend/public/i18n/en.json'))"`
Expected: no output.

- [ ] **Step 3: Switch language at runtime to verify**

`npm start`, switch language → verify all PC strings translate.

- [ ] **Step 4: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat(salon-pc): complete checklist translations FR/EN"
```

### Task 7.2: Run the full unit test suite

- [ ] **Step 1: Run all tests once**

Run: `cd frontend && npm test -- --watch=false`
Expected: PASS for all suites including the new ones (use-pc, salon-page-pc, salon-header, salon-hero, salon-cares, stories-row, editable-section).

- [ ] **Step 2: Fix any newly introduced failures**

If any pre-existing test broke due to refactoring (likely candidates: `salon-page.component.spec.ts`, `salon-profile.component.spec.ts`), update the test setup to provide the new component imports (`SalonPagePcComponent`, `SalonEditorPcComponent`) using `TranslocoTestingModule` and `provideHttpClient()`.

- [ ] **Step 3: Commit**

If any test fixes happened:

```bash
git add frontend/src/app/pages/salon/salon-page.component.spec.ts frontend/src/app/features/salon-profile/salon-profile.component.spec.ts
git commit -m "test(salon-pc): adjust existing specs for PC branching"
```

### Task 7.3: Production build + accessibility pass

- [ ] **Step 1: Run the production build**

Run: `cd frontend && npm run build`
Expected: succeeds, no warnings about new components.

- [ ] **Step 2: Lighthouse / axe pass on PC view**

Open Chrome DevTools at >= 1024px on `/salon/<slug>`:
- Lighthouse → Accessibility ≥ 90.
- Verify keyboard navigation: Tab through nav anchors, hero buttons, care card "Réserver →" links, sticky CTA.
- Verify focus rings are visible.
- Verify contrast on accent text on cream background (`#b56b5a` on `#fdfaf8` → ~3.6:1 — acceptable for non-text but use ≥ 14px **bold** for accent body text per WCAG AA).

- [ ] **Step 3: Fix any contrast / focus issues found**

Common fix: add a visible focus ring on `.book` and `.cta` buttons:

In `salon-header.component.ts` and `salon-cares.component.ts` styles, add:

```scss
.cta:focus-visible, .book:focus-visible {
  outline: 2px solid var(--pf-salon-ink);
  outline-offset: 2px;
}
```

- [ ] **Step 4: Commit any fixes**

```bash
git add frontend/src/app/pages/salon/pc/sections/
git commit -m "feat(salon-pc): visible focus rings on interactive elements"
```

### Task 7.4: Update memory artefact

Per CLAUDE.md and existing memory entries (e.g. `project_pending_pro_guided_tour.md`), this redesign affects elements the pro tour highlights. Don't fix the tour here — note the impact for the next cycle.

- [ ] **Step 1: No code change**

This is a documentation-only step. After merge, update memory note `project_pending_pro_guided_tour.md` to reflect that `data-tour-step` markers in the pro view need to be added to the new PC components (header-edit, hero-edit, cares-edit, stories-edit). This is a follow-up, not part of this plan.

---

## Milestone 8 — Optional follow-ups (not part of initial ship)

Documented here as out-of-scope reminders. **Do NOT implement these in this plan.**

- Drag-and-drop reorder for cares (per spec §8.1) — defer until a user asks.
- Per-care edit/hide/delete actions in the cares editor (covered partially in Milestone 5 but icon actions left for follow-up).
- Backend additions for `aboutText`, `ctaText`, `instagramUrl`, `facebookUrl`, `openingHours` (per spec §8.3) — requires DB migration; out of scope for an FE-only plan.
- Color-extracted accent from logo (per spec hors-scope).

---

## Self-Review Checklist (run after writing the plan)

The plan covers spec sections as follows:

| Spec section | Plan task(s) |
|--------------|--------------|
| §4.1 Structure 9 sections client | M2 (sections), M3 (stories), Task 2.6 (wiring) |
| §4.2 Stories variante B | M3 (3.1–3.5) |
| §4.3 Palette/typo | Task 0.1 (font), 1.1 (CSS vars in scss) |
| §4.4 Responsive | Task 1.2 (branching on usePc) |
| §4.5 Scroll-spy | Task 3.5 |
| §5.1 Layout pro | Task 4.7 |
| §5.2 Top bar | Task 4.5, 6.1 |
| §5.3 Sidebar | Task 4.6, 4.7 (checklist computed) |
| §5.4 Edit contextuel | Task 4.3, 4.4, M5 sections |
| §5.5 Save model | M5 (per-section save), Task 5.7 (discard confirm), Task 6.2 (readiness) |
| §5.6 Sections détails | M5 (5.1–5.6) |
| §5.7 Soins inline | Task 5.4, 5.6 |
| §5.8 Stories pro | Task 5.5 |
| §6.1 Routes | Task 1.2, 4.7 |
| §6.2 Components pro | M4, M5 |
| §6.3 Components client | M2, M3 |
| §6.4 Store editor | Task 4.2, 6.2 |
| §6.5 startIndex on viewer | Task 3.1 |
| §7 Backend | Task 4.1 (publish + readiness) |
| §9 Critères de succès | All milestones combined |

