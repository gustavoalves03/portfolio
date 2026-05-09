# Refonte des modales PC — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrer les 18 modales du produit Pretty Face vers une grammaire commune PC + tablette (≥768px), pilotée par un wrapper `<modal-form>` enrichi et 6 composants annexes, sans rien casser sur mobile (bottom-sheet ≤767px intact).

**Architecture :** Toute la grammaire visuelle est portée par le wrapper partagé `<modal-form>` (refactoré). Les 17 modales métier deviennent des callers qui injectent leur contenu spécifique via `ng-content` et configurent le wrapper via inputs (`size`, `breadcrumbParent`, `subtitle`, `dangerous`, `kbdHint`, `backLabel`). 6 composants annexes (`<modal-stepper>`, `<modal-tabs>`, `<app-care-row>`, `<app-employee-picker>`, `<app-cares-multi-picker>`, `<app-mini-calendar>`) factorisent les patterns réutilisables. Toute la nouvelle grammaire est encadrée dans `@media (min-width: 768px)`. Le bottom-sheet mobile dans `frontend/src/styles.scss` reste intact.

**Tech Stack :** Angular 20 standalone (zoneless, signals, control flow `@if/@for/@switch`), Angular Material (Dialog uniquement), inputs natifs HTML stylés, Tailwind utilitaire occasionnel, SCSS local par composant, tokens locaux `--d-*` dans le `:host`. Côté backend : Spring Boot 3.5.4 / Java 21 / JPA pour les enrichissements de DTOs. Karma/Jasmine pour les tests front, JUnit 5 + `@WebMvcTest` pour les tests back.

**Spec source :** `docs/superpowers/specs/2026-05-09-modales-pc-redesign-design.md`. Tous les mockups visuels sont dans `.superpowers/brainstorm/14863-1778288487/content/`.

---

## File Structure

### Phase 1 — Infra (créés ou refondus)

| Fichier | Responsabilité |
|---------|----------------|
| `frontend/src/app/shared/uis/modal-form/modal-form.ts` | **Refondu.** Wrapper API : props (`size`, `breadcrumbParent`, `subtitle`, `dangerous`, `kbdHint`, `backLabel`, `title`, `saveLabel`, `cancelLabel`, `saveDisabled`, `showCloseButton`, `hideSaveButton`), outputs (`save`, `cancel`, `back`), 4 slots (default, `stepper`, `aside`, `footer-left`). |
| `frontend/src/app/shared/uis/modal-form/modal-form.html` | **Refondu.** Header (filet rosé, breadcrumb, titre serif, sous-titre), body (avec aside conditionnel), footer (kbdHint OU backLabel à gauche, cancel + primary à droite). |
| `frontend/src/app/shared/uis/modal-form/modal-form.scss` | **Refondu.** Styles `@media (min-width: 768px)` uniquement (PC + tablette). Tailles via `[data-size="s\|m\|l"]`. Variante `[data-dangerous="true"]`. |
| `frontend/src/app/shared/uis/modal-form/modal-form.spec.ts` | **Créé.** Tests unitaires sur les inputs et slots. |
| `frontend/src/app/shared/uis/modal-stepper/modal-stepper.ts` | **Créé.** Stepper horizontal numéroté, inputs `steps: string[]`, `current: number`, output `stepClick`. |
| `frontend/src/app/shared/uis/modal-stepper/modal-stepper.spec.ts` | **Créé.** |
| `frontend/src/app/shared/uis/modal-tabs/modal-tabs.ts` | **Créé.** Onglets sobres, inputs `tabs: { id: string; label: string; count?: number }[]`, `active: string`, output `change`. |
| `frontend/src/app/shared/uis/modal-tabs/modal-tabs.spec.ts` | **Créé.** |
| `frontend/src/app/shared/uis/care-row/care-row.ts` | **Créé.** Ligne care réutilisable, inputs `care: CareRowDto`, `selected: boolean`, output `select`. |
| `frontend/src/app/shared/uis/care-row/care-row.spec.ts` | **Créé.** |
| `frontend/src/app/shared/uis/employee-picker/employee-picker.ts` | **Créé.** Cards verticaux praticiennes single-select, inputs `employees: EmployeePickerDto[]`, `selected: number \| null`, output `select`. |
| `frontend/src/app/shared/uis/employee-picker/employee-picker.spec.ts` | **Créé.** |
| `frontend/src/app/shared/uis/cares-multi-picker/cares-multi-picker.ts` | **Créé.** Multi-select cares avec recherche + select all + compteur, inputs `cares: CareDto[]`, `selectedIds: Set<number>`, output `change`. |
| `frontend/src/app/shared/uis/cares-multi-picker/cares-multi-picker.spec.ts` | **Créé.** |
| `frontend/src/app/shared/uis/mini-calendar/mini-calendar.ts` | **Créé.** Mini-calendrier 240px avec jours fermés/fériés/dispo, inputs `closedDays: Set<string>`, `holidays: Set<string>`, `selected: Date \| null`, `minDate: Date`, outputs `dateChange`, `monthChange`. |
| `frontend/src/app/shared/uis/mini-calendar/mini-calendar.spec.ts` | **Créé.** |
| `frontend/public/i18n/fr.json` & `en.json` | **Modifiés.** Nouvelles clés `modalForm.kbdSave`, `modalForm.kbdValidate`, `modalForm.kbdValidateMulti`, `modalForm.back`. |
| `frontend/src/styles.scss` | **Vérifié.** Le bloc `.bottom-sheet` (lignes 194-244) reste intact. La nouvelle grammaire est encadrée dans `@media (min-width: 768px)` côté composants, pas globaux. |

### Phase 2 — Migration des modales (par fichier)

| Modale | Fichiers principaux |
|--------|---------------------|
| 1. delete-care | `frontend/src/app/features/cares/modals/delete/delete-care.component.{ts,html,scss}` |
| 2. no-show-confirm | `frontend/src/app/features/tracking/components/client-bookings/no-show-confirm-dialog.component.ts` |
| 3. confirm-dialog | `frontend/src/app/pages/pro/confirm-dialog.component.ts` |
| 4. login-modal | `frontend/src/app/shared/modals/login-modal/login-modal.component.{ts,html,scss}` |
| 5. auth-modal | `frontend/src/app/shared/modals/auth-modal/auth-modal.component.{ts,html,scss}` + DTO front `AuthModalData` créé |
| 6. create-category | `frontend/src/app/features/categories/modals/create/create-category.component.ts` |
| 7. create-care | `frontend/src/app/features/cares/modals/create/create-care.component.{ts,html,scss}` + image-grid limite 5 |
| 8. reassign-category | `frontend/src/app/features/categories/modals/reassign-category/reassign-category-dialog.component.ts` + DTO `ReassignCategoryDialogData` enrichi avec `careList` |
| 9. review-leave-dialog | `frontend/src/app/features/leaves/modals/review-leave-dialog/review-leave-dialog.component.ts` + DTO `ReviewLeaveDialogData` enrichi |
| 10. rate-visit-dialog | `frontend/src/app/pages/client-evolution/rate-visit-dialog.component.ts` + DTO `RateVisitDialogData` enrichi |
| 11. create-user | `frontend/src/app/features/users/modals/create/create-user.component.ts` |
| 12. create-employee | `frontend/src/app/features/employees/modals/create-employee/create-employee.component.{ts,html,scss}` |
| 13. create-post | `frontend/src/app/features/posts/create-post-modal/create-post-modal.component.ts` + soin associé |
| 14. publish-missing-dialog | `frontend/src/app/pages/pro/publish-missing-dialog/publish-missing-dialog.component.{ts,html,scss}` + DTO enrichi `checklist[]` |
| 15. booking-stepper | `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts` + steps |
| 16. employee-detail | `frontend/src/app/features/employees/modals/employee-detail/employee-detail.component.{ts,html,scss}` |
| 17. booking-dialog vitrine | `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.{ts,html,scss}` |

### Phase 3 — Cleanup

| Fichier | Action |
|---------|--------|
| `frontend/src/app/features/bookings/modals/create/` | **Supprimé.** Code orphelin (cf. mémoire `project_orphan_create_booking`). |

### Backend (DTOs à enrichir)

| Fichier | Modification |
|---------|--------------|
| `backend/src/main/java/com/prettyface/app/care/web/dto/PublicCareDto.java` ou équivalent | Vérifier `imageUrl` ou `images[]` accessible côté front pour booking-dialog. |
| `backend/src/main/java/com/prettyface/app/category/web/dto/CategoryWithCaresResponse.java` (créé si absent) | Ajouter `careList: List<CareSummary>` pour reassign-category. |
| `backend/src/main/java/com/prettyface/app/employee/web/dto/LeaveResponse.java` | Vérifier que `type, startDate, endDate, reason` sont déjà exposés (regarder le code, sans doute oui). |
| `backend/src/main/java/com/prettyface/app/tracking/web/dto/VisitRecordResponse.java` ou équivalent | Vérifier que `salonName, visitDate` sont exposés. |
| `backend/src/main/java/com/prettyface/app/employee/web/dto/EmployeeResponse.java` | Ajouter `createdAt: Instant`, `bookingsCount: long`. |
| `backend/src/main/java/com/prettyface/app/onboarding/web/dto/PublishStatusResponse.java` (ou équivalent) | Remplacer/enrichir `missing[]` par `checklist: [{ key, status }]`. |
| `backend/src/main/java/com/prettyface/app/posts/web/dto/CreatePostRequest.java` (ou les 3 endpoints BA/Photo/Carousel) | Ajouter `careId: Long?` optionnel. |

---

## Phase 1 — Infrastructure

### Task 1 : Créer la branche et la base PC tokens

**Files :**
- Modify : `frontend/src/app/shared/uis/modal-form/modal-form.scss`
- Create : `frontend/src/app/shared/uis/modal-form/modal-form-tokens.scss`

- [ ] **Step 1: Créer une branche dédiée**

```bash
git checkout -b feat/modales-pc-redesign
```

- [ ] **Step 2: Créer le fichier de tokens PC**

Create `frontend/src/app/shared/uis/modal-form/modal-form-tokens.scss` :

```scss
/* Tokens locaux pour la grammaire des modales PC.
 * Importé par modal-form.scss et les composants annexes. */
:root {
  --m-rose: #c66075;
  --m-rose-soft: rgba(198, 96, 117, 0.08);
  --m-rose-deep: #a83e58;
  --m-rose-light: #f5d0e0;
  --m-danger: #b3001b;
  --m-danger-soft: rgba(179, 0, 27, 0.08);
  --m-danger-light: #e08896;
  --m-ink: #1a1a1a;
  --m-ink-2: #555;
  --m-ink-mute: #999;
  --m-rule: #ececec;
  --m-rule-soft: #f4f4f4;
  --m-surface: #ffffff;
  --m-bg-sand: #fafafa;
  --m-tag-bg: #f3ede9;
  --m-success: #2e7a3e;
  --m-success-soft: rgba(60, 160, 80, 0.10);
}
```

- [ ] **Step 3: Vérifier qu'il compile**

Run :
```bash
docker compose --profile dev up -d --force-recreate frontend-dev
```

Open `http://localhost:4300` — l'app doit charger. Aucune modale ne change encore (le SCSS n'est pas importé ailleurs).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/shared/uis/modal-form/modal-form-tokens.scss
git commit -m "chore(modal-form): add local tokens file for PC redesign"
```

---

### Task 2 : Refonte du wrapper `<modal-form>` — API et template

**Files :**
- Modify : `frontend/src/app/shared/uis/modal-form/modal-form.ts`
- Modify : `frontend/src/app/shared/uis/modal-form/modal-form.html`
- Test : `frontend/src/app/shared/uis/modal-form/modal-form.spec.ts`

- [ ] **Step 1: Écrire le test failing**

Create `frontend/src/app/shared/uis/modal-form/modal-form.spec.ts` :

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Component } from '@angular/core';
import { ModalForm } from './modal-form';

@Component({
  standalone: true,
  imports: [ModalForm],
  template: `
    <modal-form
      size="m"
      breadcrumbParent="Catalogue / Soins"
      title="Nouveau soin"
      subtitle="Renseigne les informations qui apparaîtront sur la fiche client."
      [dangerous]="false"
      saveLabel="Créer le soin"
      kbdHint="⌘S pour enregistrer"
    >
      <p>Form content</p>
    </modal-form>
  `,
})
class HostComponent {}

describe('ModalForm — PC redesign', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders breadcrumb parent levels', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Catalogue');
    expect(el.textContent).toContain('Soins');
  });

  it('renders title in serif container', () => {
    const titleEl = fixture.nativeElement.querySelector('.modal-title');
    expect(titleEl).toBeTruthy();
    expect(titleEl.textContent).toContain('Nouveau soin');
  });

  it('renders subtitle when provided', () => {
    const subEl = fixture.nativeElement.querySelector('.modal-sub');
    expect(subEl?.textContent).toContain('Renseigne les informations');
  });

  it('renders save button with custom label', () => {
    const btn = fixture.nativeElement.querySelector('.btn-primary');
    expect(btn?.textContent.trim()).toBe('Créer le soin');
  });

  it('renders kbd hint in footer', () => {
    const hint = fixture.nativeElement.querySelector('.kbd-hint');
    expect(hint?.textContent).toContain('⌘S');
  });

  it('applies size attribute on host element', () => {
    const root = fixture.nativeElement.querySelector('.modal-frame');
    expect(root?.getAttribute('data-size')).toBe('m');
  });

  it('does NOT have dangerous class when dangerous=false', () => {
    const root = fixture.nativeElement.querySelector('.modal-frame');
    expect(root?.getAttribute('data-dangerous')).toBe('false');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npm test -- --include='**/modal-form.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -30
```

Expected: Tests fail (le wrapper actuel n'a ni `breadcrumbParent` ni `subtitle` ni `kbdHint` ni `data-size` ni `data-dangerous`, et le titre est dans `.header-content h2` pas `.modal-title`).

- [ ] **Step 3: Refondre `modal-form.ts`**

Replace `frontend/src/app/shared/uis/modal-form/modal-form.ts` :

```typescript
import { Component, input, output } from '@angular/core';
import { MatDialogModule } from '@angular/material/dialog';

export type ModalFormSize = 's' | 'm' | 'l';

/**
 * Wrapper modal partagé pour la grammaire PC + tablette (≥768px).
 * Le mobile (<768px) reste sur le bottom-sheet existant via styles.scss.
 *
 * Voir docs/superpowers/specs/2026-05-09-modales-pc-redesign-design.md
 */
@Component({
  selector: 'modal-form',
  standalone: true,
  styleUrl: 'modal-form.scss',
  templateUrl: 'modal-form.html',
  imports: [MatDialogModule],
})
export class ModalForm {
  // Inputs (existants conservés)
  title = input.required<string>();
  saveLabel = input<string>('Enregistrer');
  cancelLabel = input<string>('Annuler');
  saveDisabled = input<boolean>(false);
  showCloseButton = input<boolean>(true);
  hideSaveButton = input<boolean>(false);

  // Inputs nouveaux (refonte PC)
  size = input<ModalFormSize>('m');
  breadcrumbParent = input<string>('');
  subtitle = input<string>('');
  dangerous = input<boolean>(false);
  kbdHint = input<string>('');
  backLabel = input<string>('');

  // Outputs
  save = output<void>();
  cancel = output<void>();
  back = output<void>();

  protected get breadcrumbLevels(): { lvl1: string; lvl2: string | null } {
    const parts = this.breadcrumbParent().split('/').map(s => s.trim()).filter(Boolean);
    return { lvl1: parts[0] ?? '', lvl2: parts[1] ?? null };
  }

  protected onSave() { this.save.emit(); }
  protected onCancel() { this.cancel.emit(); }
  protected onBack() { this.back.emit(); }
}
```

- [ ] **Step 4: Refondre `modal-form.html`**

Replace `frontend/src/app/shared/uis/modal-form/modal-form.html` :

```html
<div
  class="modal-frame"
  [attr.data-size]="size()"
  [attr.data-dangerous]="dangerous()"
>
  <div class="top-strip"></div>

  <div mat-dialog-title class="modal-head">
    @if (breadcrumbParent()) {
      <div class="crumb-row">
        <div class="crumb">
          <span class="dot"></span>
          @if (breadcrumbLevels.lvl1) {
            <span class="lvl1">{{ breadcrumbLevels.lvl1 }}</span>
          }
          @if (breadcrumbLevels.lvl2) {
            <span class="sep">/</span>
            <span class="lvl2">{{ breadcrumbLevels.lvl2 }}</span>
          }
        </div>
        @if (showCloseButton()) {
          <button type="button" class="close-x" (click)="onCancel()" aria-label="Fermer">×</button>
        }
      </div>
    }
    <div class="modal-title" [innerHTML]="title()"></div>
    @if (subtitle()) {
      <div class="modal-sub" [innerHTML]="subtitle()"></div>
    }
  </div>

  <ng-content select="[slot=stepper]"></ng-content>

  @if (hasAside) {
    <div class="modal-body-l">
      <aside class="modal-aside">
        <ng-content select="[slot=aside]"></ng-content>
      </aside>
      <main class="modal-main">
        <ng-content></ng-content>
      </main>
    </div>
  } @else {
    <div mat-dialog-content class="modal-body">
      <ng-content></ng-content>
    </div>
  }

  <div mat-dialog-actions class="modal-foot">
    <div class="foot-left">
      <ng-content select="[slot=footer-left]"></ng-content>
      @if (backLabel()) {
        <button type="button" class="btn-back" (click)="onBack()">{{ backLabel() }}</button>
      } @else if (kbdHint() && size() !== 's') {
        <span class="kbd-hint">{{ kbdHint() }}</span>
      }
    </div>
    <div class="foot-right">
      <button type="button" class="btn-cancel" (click)="onCancel()">{{ cancelLabel() }}</button>
      @if (!hideSaveButton()) {
        <button
          type="button"
          class="btn-primary"
          [class.danger]="dangerous()"
          [disabled]="saveDisabled()"
          (click)="onSave()"
        >
          {{ saveLabel() }}
        </button>
      }
    </div>
  </div>
</div>
```

- [ ] **Step 5: Ajouter le getter `hasAside` dans `modal-form.ts`**

Add this getter to `ModalForm` class (after `breadcrumbLevels` getter) :

```typescript
  protected hasAside = false;

  // Détecte si le caller a fourni un slot=aside (vérifié au render via querySelector dans constructor)
  // Pour rester simple : on expose un input booléen alternatif.
```

Replace the `hasAside` field by an input declaration so callers can opt-in explicitly :

```typescript
  withAside = input<boolean>(false);
```

Then in `modal-form.html` replace `@if (hasAside)` by `@if (withAside())`.

Update the spec to test `withAside` separately (will be added in next task).

- [ ] **Step 6: Run test to verify it passes**

```bash
npm test -- --include='**/modal-form.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -20
```

Expected: 7/7 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/shared/uis/modal-form/
git commit -m "feat(modal-form): refonte API for PC grammar (size, breadcrumb, subtitle, dangerous, kbd, back)"
```

---

### Task 3 : Wrapper — styles SCSS PC

**Files :**
- Modify : `frontend/src/app/shared/uis/modal-form/modal-form.scss`

- [ ] **Step 1: Remplacer entièrement le SCSS**

Replace `frontend/src/app/shared/uis/modal-form/modal-form.scss` :

```scss
@use './modal-form-tokens' as *;

/* ========================================================================
 * Mobile (< 768px) — délégué au bottom-sheet global dans styles.scss.
 * On ne touche PAS le markup mobile. Les classes ci-dessous ne s'activent
 * qu'à partir de 768px.
 * ===================================================================== */

@media (min-width: 768px) {
  :host {
    display: block;
  }

  .modal-frame {
    background: var(--m-surface);
    border: 1px solid var(--m-rule);
    border-radius: 4px;
    overflow: hidden;
    color: var(--m-ink);
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.08);
    display: flex;
    flex-direction: column;
    max-height: min(800px, calc(100vh - 80px));
    width: 100%;
  }

  /* ----- Tailles via attribute selector. La largeur effective est imposée
     par le panelClass MatDialog ; voir bottom-sheet.config.ts mis à jour
     en Task 5. ----- */

  /* ----- Filet décoratif ----- */
  .top-strip {
    height: 3px;
    background: linear-gradient(90deg, var(--m-rose), var(--m-rose-light));
    flex-shrink: 0;
  }
  .modal-frame[data-dangerous="true"] .top-strip {
    background: linear-gradient(90deg, var(--m-danger), var(--m-danger-light));
  }

  /* ----- Header ----- */
  .modal-head {
    padding: 14px 24px 18px;
    border-bottom: 1px solid var(--m-rule);
    margin: 0;
    flex-shrink: 0;
  }
  .modal-frame[data-size="s"] .modal-head { padding: 14px 22px 16px; }

  .crumb-row {
    display: flex; align-items: center; justify-content: space-between;
    margin-bottom: 10px;
  }
  .crumb {
    display: flex; align-items: center; gap: 10px; font-size: 12px;
  }
  .crumb .dot {
    display: inline-block;
    width: 6px; height: 6px; border-radius: 50%;
    background: var(--m-rose);
  }
  .modal-frame[data-dangerous="true"] .crumb .dot { background: var(--m-danger); }
  .crumb .lvl1 {
    color: var(--m-ink-mute);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 11px;
  }
  .crumb .sep { color: #ccc; }
  .crumb .lvl2 { color: var(--m-ink-2); }

  .close-x {
    background: none; border: 0; padding: 0;
    font-size: 18px; color: var(--m-ink-mute); line-height: 1;
    cursor: pointer; user-select: none;
  }
  .close-x:hover { color: var(--m-ink); }

  .modal-title {
    font-family: 'Cormorant Garamond', 'Playfair Display', Georgia, serif;
    font-weight: 500;
    line-height: 1.1;
    letter-spacing: -0.01em;
    color: var(--m-ink);
    font-size: 26px;
  }
  .modal-frame[data-size="s"] .modal-title { font-size: 22px; line-height: 1.15; }

  .modal-sub {
    font-size: 13px; color: var(--m-ink-2); margin-top: 6px;
  }

  /* ----- Body ----- */
  .modal-body {
    padding: 18px 24px;
    overflow-y: auto;
    flex: 1;
  }

  /* ----- Body L (2 colonnes) ----- */
  .modal-body-l {
    display: grid;
    grid-template-columns: 280px 1fr;
    flex: 1;
    overflow: hidden;
  }
  .modal-aside {
    border-right: 1px solid var(--m-rule);
    background: var(--m-bg-sand);
    padding: 18px 20px;
    overflow-y: auto;
  }
  .modal-main {
    padding: 18px 24px;
    overflow-y: auto;
  }

  /* ----- Footer ----- */
  .modal-foot {
    padding: 14px 24px;
    border-top: 1px solid var(--m-rule);
    background: var(--m-surface);
    display: flex; justify-content: space-between; align-items: center;
    flex-shrink: 0;
    margin: 0;
    min-height: unset;
  }
  .modal-frame[data-size="s"] .modal-foot { padding: 12px 22px; }

  .foot-left, .foot-right {
    display: flex; align-items: center; gap: 10px;
  }
  .foot-right { gap: 10px; }

  .kbd-hint { font-size: 11.5px; color: var(--m-ink-mute); }

  .btn-back, .btn-cancel {
    height: 36px; padding: 0 16px;
    border: 0; background: transparent;
    color: var(--m-ink-2);
    border-radius: 4px; font-size: 13px; cursor: pointer;
    font-family: inherit;
  }
  .btn-back:hover, .btn-cancel:hover { background: var(--m-rule-soft); }

  .btn-primary {
    height: 36px; padding: 0 22px;
    border: 0;
    background: var(--m-rose);
    color: #fff;
    border-radius: 4px;
    font-size: 13px; font-weight: 500;
    cursor: pointer;
    font-family: inherit;
    transition: background 0.15s;
  }
  .btn-primary:hover:not(:disabled) { background: var(--m-rose-deep); }
  .btn-primary:disabled { opacity: 0.4; cursor: not-allowed; }
  .btn-primary.danger { background: var(--m-danger); }
  .btn-primary.danger:hover:not(:disabled) { background: #8a0014; }
}
```

- [ ] **Step 2: Run a quick visual check via dev server**

Open `http://localhost:4300/pro/cares` (ou n'importe quelle page qui ouvre une modale). Cliquer sur "Ajouter un soin" — la modale s'ouvre avec le **nouveau** wrapper mais les inputs ne sont pas encore branchés (`breadcrumbParent` etc. sont vides). Le visuel ressemble à : titre serif, footer plat, plus d'icône Material géante. Pas de breadcrumb. C'est attendu — la modale `create-care` sera migrée en Task 21.

- [ ] **Step 3: Vérifier mobile (Chrome DevTools — viewport 375px)**

Toujours sur `/pro/cares`, ouvrir une modale. Le bottom-sheet doit s'afficher comme avant (slide-up, handle en haut, fond `#fafafa`). **Aucune régression** — la nouvelle grammaire est encadrée par `@media (min-width: 768px)`.

- [ ] **Step 4: Run all tests**

```bash
npx tsc --noEmit -p tsconfig.app.json
npm test -- --include='**/modal-form.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -15
```

Expected: TypeScript zéro erreur ; modal-form spec PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/uis/modal-form/modal-form.scss
git commit -m "feat(modal-form): apply PC grammar styles (filet, breadcrumb, serif title, footer) gated >=768px"
```

---

### Task 4 : Adapter `bottomSheetConfig` pour gérer les tailles S/M/L

**Files :**
- Modify : `frontend/src/app/shared/uis/sheet-handle/bottom-sheet.config.ts`

- [ ] **Step 1: Remplacer le helper**

Replace `frontend/src/app/shared/uis/sheet-handle/bottom-sheet.config.ts` :

```typescript
import { MatDialogConfig } from '@angular/material/dialog';

export type ModalSize = 's' | 'm' | 'l';

const SIZE_TO_MAX_WIDTH: Record<ModalSize, string> = {
  s: 'min(480px, calc(100vw - 32px))',
  m: 'min(640px, calc(100vw - 48px))',
  l: 'min(860px, calc(100vw - 64px))',
};

function asArray(value: string | string[] | undefined): string[] {
  if (!value) return [];
  return Array.isArray(value) ? value : [value];
}

/**
 * Configuration MatDialog standard pour les modales Pretty Face.
 *
 * - Mobile (<=767px) : bottom-sheet via la classe `.bottom-sheet` dans styles.scss.
 *   La largeur est forcée à 100vw par le CSS mobile, donc maxWidth ici ne s'applique
 *   qu'à partir de 768px.
 * - PC + tablette (>=768px) : largeur S/M/L selon le paramètre `size`.
 *
 * @param size 's' | 'm' | 'l' — défaut 'm'
 * @param overrides MatDialogConfig pour personnaliser data, disableClose, etc.
 */
export function bottomSheetConfig<T = unknown>(
  size: ModalSize | MatDialogConfig<T> = 'm',
  overrides: MatDialogConfig<T> = {},
): MatDialogConfig<T> {
  // Backward-compat : si on appelle bottomSheetConfig({ data: ... }) sans size
  let actualSize: ModalSize = 'm';
  let actualOverrides = overrides;
  if (typeof size === 'object') {
    actualOverrides = size;
  } else {
    actualSize = size;
  }

  return {
    maxWidth: SIZE_TO_MAX_WIDTH[actualSize],
    ...actualOverrides,
    panelClass: ['bottom-sheet', `modal-size-${actualSize}`, ...asArray(actualOverrides.panelClass)],
    backdropClass: ['bottom-sheet-backdrop', ...asArray(actualOverrides.backdropClass)],
  };
}
```

- [ ] **Step 2: Vérifier que les callers existants (sans size) compilent toujours**

```bash
npx tsc --noEmit -p tsconfig.app.json 2>&1 | grep "bottom-sheet" | head -10
```

Expected: aucune erreur (la signature est rétro-compatible : `bottomSheetConfig({ data })` continue de marcher en taille `'m'` par défaut).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/shared/uis/sheet-handle/bottom-sheet.config.ts
git commit -m "feat(modals): bottomSheetConfig accepts size param (S/M/L) for PC presets"
```

---

### Task 5 : Composant annexe `<modal-stepper>`

**Files :**
- Create : `frontend/src/app/shared/uis/modal-stepper/modal-stepper.ts`
- Create : `frontend/src/app/shared/uis/modal-stepper/modal-stepper.spec.ts`

- [ ] **Step 1: Écrire le test failing**

Create `frontend/src/app/shared/uis/modal-stepper/modal-stepper.spec.ts` :

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { ModalStepper } from './modal-stepper';

@Component({
  standalone: true,
  imports: [ModalStepper],
  template: `<modal-stepper [steps]="['Soin','Horaire','Cliente']" [current]="2" />`,
})
class HostComponent {}

describe('ModalStepper', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders all step labels', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Soin');
    expect(text).toContain('Horaire');
    expect(text).toContain('Cliente');
  });

  it('marks step 1 as done, step 2 as active, step 3 as pending', () => {
    const steps = fixture.nativeElement.querySelectorAll('.step');
    expect(steps[0].classList.contains('done')).toBe(true);
    expect(steps[1].classList.contains('active')).toBe(true);
    expect(steps[2].classList.contains('done')).toBe(false);
    expect(steps[2].classList.contains('active')).toBe(false);
  });

  it('emits stepClick when a done step is clicked', () => {
    const fix = TestBed.createComponent(HostComponent);
    fix.detectChanges();
    const stepperEl = fix.debugElement.query(node => node.componentInstance instanceof ModalStepper);
    const stepper: ModalStepper = stepperEl.componentInstance;
    let emitted: number | null = null;
    stepper.stepClick.subscribe(idx => emitted = idx);
    const firstStep: HTMLElement = fix.nativeElement.querySelectorAll('.step')[0];
    firstStep.click();
    expect(emitted).toBe(1);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm test -- --include='**/modal-stepper.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10
```

Expected: FAIL — `Cannot find module './modal-stepper'`.

- [ ] **Step 3: Implémenter le composant**

Create `frontend/src/app/shared/uis/modal-stepper/modal-stepper.ts` :

```typescript
import { Component, input, output } from '@angular/core';

@Component({
  selector: 'modal-stepper',
  standalone: true,
  template: `
    @for (label of steps(); track label; let i = $index) {
      <div
        class="step"
        [class.done]="i + 1 < current()"
        [class.active]="i + 1 === current()"
        (click)="onStepClick(i + 1)"
        [attr.role]="i + 1 < current() ? 'button' : null"
      >
        <div class="step-num">{{ i + 1 }}</div>
        <div class="step-label">{{ label }}</div>
      </div>
      @if (i < steps().length - 1) {
        <div class="step-line"></div>
      }
    }
  `,
  styles: `
    @media (min-width: 768px) {
      :host {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 14px 24px;
        border-bottom: 1px solid #ececec;
        background: #fafafa;
      }
      .step {
        display: flex; align-items: center; gap: 8px;
        font-size: 12px;
        cursor: default;
      }
      .step.done { cursor: pointer; }
      .step-num {
        width: 22px; height: 22px; border-radius: 50%;
        background: #fff; border: 1px solid #ddd; color: #999;
        display: grid; place-items: center;
        font-size: 11px; font-weight: 600;
      }
      .step.active .step-num { background: #c66075; border-color: #c66075; color: #fff; }
      .step.done .step-num { background: #1a1a1a; border-color: #1a1a1a; color: #fff; }
      .step-label { color: #999; }
      .step.active .step-label { color: #1a1a1a; font-weight: 500; }
      .step.done .step-label { color: #555; }
      .step-line {
        flex: 1; height: 1px; background: #e6e6e6; min-width: 16px;
      }
    }
  `,
})
export class ModalStepper {
  readonly steps = input.required<string[]>();
  readonly current = input.required<number>();
  readonly stepClick = output<number>();

  protected onStepClick(stepIndex: number): void {
    if (stepIndex < this.current()) {
      this.stepClick.emit(stepIndex);
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --include='**/modal-stepper.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -15
```

Expected: 3/3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/uis/modal-stepper/
git commit -m "feat(modal-stepper): add stepper component for L workflows"
```

---

### Task 6 : Composant annexe `<modal-tabs>`

**Files :**
- Create : `frontend/src/app/shared/uis/modal-tabs/modal-tabs.ts`
- Create : `frontend/src/app/shared/uis/modal-tabs/modal-tabs.spec.ts`

- [ ] **Step 1: Écrire le test failing**

Create `frontend/src/app/shared/uis/modal-tabs/modal-tabs.spec.ts` :

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { ModalTabs, ModalTab } from './modal-tabs';

@Component({
  standalone: true,
  imports: [ModalTabs],
  template: `
    <modal-tabs
      [tabs]="tabs"
      [active]="active"
      (change)="active = $event"
    />
  `,
})
class HostComponent {
  tabs: ModalTab[] = [
    { id: 'info', label: 'Informations' },
    { id: 'images', label: 'Images', count: 5 },
  ];
  active = 'info';
}

describe('ModalTabs', () => {
  let fixture: ComponentFixture<HostComponent>;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders all tabs', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Informations');
    expect(text).toContain('Images');
  });

  it('renders count pill on tab with count', () => {
    const counts = fixture.nativeElement.querySelectorAll('.count');
    expect(counts.length).toBe(1);
    expect(counts[0].textContent.trim()).toBe('5');
  });

  it('marks first tab as active', () => {
    const tabs = fixture.nativeElement.querySelectorAll('.tab');
    expect(tabs[0].classList.contains('active')).toBe(true);
    expect(tabs[1].classList.contains('active')).toBe(false);
  });

  it('switches active when clicking second tab', () => {
    const tabs: HTMLElement[] = fixture.nativeElement.querySelectorAll('.tab');
    tabs[1].click();
    fixture.detectChanges();
    expect(tabs[1].classList.contains('active')).toBe(true);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm test -- --include='**/modal-tabs.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10
```

Expected: FAIL.

- [ ] **Step 3: Implémenter le composant**

Create `frontend/src/app/shared/uis/modal-tabs/modal-tabs.ts` :

```typescript
import { Component, input, output } from '@angular/core';

export interface ModalTab {
  id: string;
  label: string;
  count?: number;
}

@Component({
  selector: 'modal-tabs',
  standalone: true,
  template: `
    @for (t of tabs(); track t.id) {
      <button
        type="button"
        class="tab"
        [class.active]="t.id === active()"
        (click)="onClick(t.id)"
      >
        <span>{{ t.label }}</span>
        @if (t.count !== undefined) {
          <span class="count">{{ t.count }}</span>
        }
      </button>
    }
  `,
  styles: `
    @media (min-width: 768px) {
      :host {
        display: flex; gap: 0;
        border-bottom: 1px solid #ececec;
        padding: 0 24px;
        background: #fff;
      }
      .tab {
        background: none; border: 0;
        padding: 12px 0; margin-right: 28px;
        cursor: pointer;
        font-size: 13px; font-weight: 500; color: #999;
        border-bottom: 2px solid transparent;
        font-family: inherit;
        display: flex; align-items: center; gap: 8px;
      }
      .tab.active { color: #1a1a1a; border-bottom-color: #c66075; }
      .tab:hover:not(.active) { color: #666; }
      .count {
        font-size: 11px; padding: 2px 7px; border-radius: 99px;
        background: #f3ede9; color: #555;
      }
      .tab.active .count { background: rgba(198,96,117,0.12); color: #c66075; }
    }
  `,
})
export class ModalTabs {
  readonly tabs = input.required<ModalTab[]>();
  readonly active = input.required<string>();
  readonly change = output<string>();

  protected onClick(id: string): void {
    if (id !== this.active()) {
      this.change.emit(id);
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --include='**/modal-tabs.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10
```

Expected: 4/4 PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/uis/modal-tabs/
git commit -m "feat(modal-tabs): add sober tabs component with optional count pill"
```

---

### Task 7 : Composant annexe `<app-cares-multi-picker>`

**Files :**
- Create : `frontend/src/app/shared/uis/cares-multi-picker/cares-multi-picker.ts`
- Create : `frontend/src/app/shared/uis/cares-multi-picker/cares-multi-picker.spec.ts`

- [ ] **Step 1: Écrire le test failing**

Create `frontend/src/app/shared/uis/cares-multi-picker/cares-multi-picker.spec.ts` :

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { CaresMultiPicker, PickerCare } from './cares-multi-picker';

@Component({
  standalone: true,
  imports: [CaresMultiPicker],
  template: `
    <app-cares-multi-picker
      [cares]="cares"
      [selectedIds]="selected"
      (change)="selected = $event"
    />
  `,
})
class HostComponent {
  cares: PickerCare[] = [
    { id: 1, name: 'Soin éclat', categoryName: 'Visage' },
    { id: 2, name: 'Massage', categoryName: 'Corps' },
    { id: 3, name: 'Manucure', categoryName: 'Mains' },
  ];
  selected = new Set<number>([1]);
}

describe('CaresMultiPicker', () => {
  let fixture: ComponentFixture<HostComponent>;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders all cares', () => {
    expect(fixture.nativeElement.textContent).toContain('Soin éclat');
    expect(fixture.nativeElement.textContent).toContain('Massage');
    expect(fixture.nativeElement.textContent).toContain('Manucure');
  });

  it('shows selected count', () => {
    const count = fixture.nativeElement.querySelector('.count');
    expect(count.textContent).toContain('1 / 3');
  });

  it('toggles selection on click', () => {
    const items: HTMLElement[] = fixture.nativeElement.querySelectorAll('.care-item');
    items[1].click();
    fixture.detectChanges();
    expect(items[1].classList.contains('selected')).toBe(true);
  });

  it('select all sets all ids', () => {
    const link: HTMLElement = fixture.nativeElement.querySelector('[data-testid="select-all"]');
    link.click();
    fixture.detectChanges();
    const items: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.care-item');
    items.forEach(it => expect(it.classList.contains('selected')).toBe(true));
  });

  it('filters by search', () => {
    const search: HTMLInputElement = fixture.nativeElement.querySelector('input');
    search.value = 'massage';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    const visibleItems = fixture.nativeElement.querySelectorAll('.care-item');
    expect(visibleItems.length).toBe(1);
    expect(visibleItems[0].textContent).toContain('Massage');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm test -- --include='**/cares-multi-picker.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10
```

Expected: FAIL.

- [ ] **Step 3: Implémenter le composant**

Create `frontend/src/app/shared/uis/cares-multi-picker/cares-multi-picker.ts` :

```typescript
import { Component, computed, input, output, signal } from '@angular/core';

export interface PickerCare {
  id: number;
  name: string;
  categoryName?: string;
}

@Component({
  selector: 'app-cares-multi-picker',
  standalone: true,
  template: `
    <div class="cares-pick">
      <div class="cares-search">
        <span class="search-ico">⌕</span>
        <input
          type="text"
          [value]="search()"
          (input)="onSearch($event)"
          placeholder="Rechercher un soin…"
        />
      </div>
      <div class="cares-actions">
        <a data-testid="select-all" (click)="selectAll()">Tout sélectionner</a>
        <span class="sep-d">·</span>
        <a (click)="deselectAll()">Tout désélectionner</a>
        <span class="count">{{ selectedIds().size }} / {{ cares().length }} sélectionnés</span>
      </div>
      <div class="cares-list">
        @for (c of filtered(); track c.id) {
          <div
            class="care-item"
            [class.selected]="selectedIds().has(c.id)"
            (click)="toggle(c.id)"
          >
            <span class="check">{{ selectedIds().has(c.id) ? '✓' : '' }}</span>
            <span class="nm">{{ c.name }}</span>
            @if (c.categoryName) {
              <span class="care-cat">{{ c.categoryName }}</span>
            }
          </div>
        }
      </div>
    </div>
  `,
  styles: `
    .cares-pick { border: 1px solid #ddd; border-radius: 4px; background: #fff; overflow: hidden; }
    .cares-search { padding: 8px 12px; border-bottom: 1px solid #ececec; display: flex; align-items: center; gap: 8px; font-size: 13px; }
    .cares-search input { flex: 1; border: 0; padding: 4px 0; font-size: 13px; outline: none; background: transparent; font-family: inherit; }
    .search-ico { color: #999; font-weight: 600; }
    .cares-actions { display: flex; gap: 12px; padding: 6px 12px; border-bottom: 1px solid #f4f4f4; font-size: 11.5px; }
    .cares-actions a { color: #c66075; cursor: pointer; text-decoration: none; }
    .cares-actions a:hover { text-decoration: underline; }
    .sep-d { color: #ddd; }
    .count { margin-left: auto; color: #999; }
    .cares-list { max-height: 200px; overflow-y: auto; }
    .care-item { display: flex; align-items: center; gap: 10px; padding: 8px 12px; border-bottom: 1px solid #f8f8f8; cursor: pointer; font-size: 13px; }
    .care-item:hover { background: #fafafa; }
    .care-item.selected { background: rgba(198,96,117,0.04); }
    .care-item.selected .nm { font-weight: 500; }
    .nm { color: #1a1a1a; }
    .care-cat { font-size: 11px; color: #999; padding: 1px 8px; background: #f3ede9; border-radius: 99px; margin-left: auto; }
    .check { width: 16px; height: 16px; border: 1.5px solid #ddd; border-radius: 3px; display: grid; place-items: center; flex-shrink: 0; font-size: 11px; }
    .care-item.selected .check { background: #c66075; border-color: #c66075; color: #fff; }
  `,
})
export class CaresMultiPicker {
  readonly cares = input.required<PickerCare[]>();
  readonly selectedIds = input.required<Set<number>>();
  readonly change = output<Set<number>>();

  protected readonly search = signal('');

  protected readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    if (!q) return this.cares();
    return this.cares().filter(c => c.name.toLowerCase().includes(q));
  });

  protected onSearch(e: Event): void {
    this.search.set((e.target as HTMLInputElement).value);
  }

  protected toggle(id: number): void {
    const next = new Set(this.selectedIds());
    if (next.has(id)) next.delete(id);
    else next.add(id);
    this.change.emit(next);
  }

  protected selectAll(): void {
    this.change.emit(new Set(this.cares().map(c => c.id)));
  }

  protected deselectAll(): void {
    this.change.emit(new Set());
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --include='**/cares-multi-picker.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -15
```

Expected: 5/5 PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/uis/cares-multi-picker/
git commit -m "feat(cares-multi-picker): add searchable multi-select for cares"
```

---

### Task 8 : Composant annexe `<app-care-row>`

**Files :**
- Create : `frontend/src/app/shared/uis/care-row/care-row.ts`
- Create : `frontend/src/app/shared/uis/care-row/care-row.spec.ts`

- [ ] **Step 1: Écrire le test failing**

Create `frontend/src/app/shared/uis/care-row/care-row.spec.ts` :

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { CareRow, CareRowDto } from './care-row';

@Component({
  standalone: true,
  imports: [CareRow],
  template: `<app-care-row [care]="care" [selected]="selected" (selectChange)="onSel($event)" />`,
})
class HostComponent {
  care: CareRowDto = { id: 1, name: 'Soin éclat', durationMin: 60, priceCents: 6500, categoryName: 'Visage' };
  selected = false;
  emitted = false;
  onSel(_: CareRowDto) { this.emitted = true; }
}

describe('CareRow', () => {
  let fixture: ComponentFixture<HostComponent>;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders care name + duration + price', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Soin éclat');
    expect(text).toContain('60 min');
    expect(text).toContain('65,00');
  });

  it('renders category pill when present', () => {
    expect(fixture.nativeElement.querySelector('.cat-pill')?.textContent).toContain('Visage');
  });

  it('formats long duration as 1h30 etc.', () => {
    fixture.componentInstance.care = { id: 2, name: 'Long', durationMin: 90, priceCents: 0 };
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('1h30');
  });

  it('emits selectChange on click', () => {
    fixture.nativeElement.querySelector('.care-row').click();
    expect(fixture.componentInstance.emitted).toBe(true);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm test -- --include='**/care-row.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10
```

Expected: FAIL.

- [ ] **Step 3: Implémenter le composant**

Create `frontend/src/app/shared/uis/care-row/care-row.ts` :

```typescript
import { Component, input, output } from '@angular/core';

export interface CareRowDto {
  id: number;
  name: string;
  durationMin: number;
  priceCents: number;
  categoryName?: string;
}

@Component({
  selector: 'app-care-row',
  standalone: true,
  template: `
    <div class="care-row" [class.selected]="selected()" (click)="onClick()">
      <div class="nm-block">
        <div class="nm">{{ care().name }}</div>
      </div>
      @if (care().categoryName) {
        <span class="cat-pill">{{ care().categoryName }}</span>
      }
      <span class="duration">{{ formatDuration(care().durationMin) }}</span>
      <span class="price">{{ formatPrice(care().priceCents) }}</span>
    </div>
  `,
  styles: `
    .care-row {
      display: grid;
      grid-template-columns: 1fr auto auto auto;
      gap: 12px; align-items: center;
      padding: 10px 12px;
      border: 1px solid #ececec; border-radius: 4px; background: #fff;
      cursor: pointer; transition: border-color 0.15s;
    }
    .care-row:hover { border-color: #c66075; }
    .care-row.selected { border-color: #c66075; background: rgba(198,96,117,0.04); }
    .nm { font-size: 13px; color: #1a1a1a; font-weight: 500; }
    .cat-pill { font-size: 11px; padding: 1px 8px; background: #f3ede9; border-radius: 99px; color: #555; }
    .duration { font-size: 12px; color: #999; }
    .price { font-size: 13px; font-weight: 500; color: #1a1a1a; font-variant-numeric: tabular-nums; }
  `,
})
export class CareRow {
  readonly care = input.required<CareRowDto>();
  readonly selected = input<boolean>(false);
  readonly selectChange = output<CareRowDto>();

  protected formatDuration(minutes: number): string {
    if (minutes < 60) return `${minutes} min`;
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${h}h`;
  }

  protected formatPrice(cents: number): string {
    return (cents / 100).toFixed(2).replace('.', ',') + ' €';
  }

  protected onClick(): void {
    this.selectChange.emit(this.care());
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --include='**/care-row.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10
```

Expected: 4/4 PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/uis/care-row/
git commit -m "feat(care-row): add reusable care row (name+pill+duration+price)"
```

---

### Task 9 : Composant annexe `<app-employee-picker>`

**Files :**
- Create : `frontend/src/app/shared/uis/employee-picker/employee-picker.ts`
- Create : `frontend/src/app/shared/uis/employee-picker/employee-picker.spec.ts`

- [ ] **Step 1: Écrire le test failing**

Create `frontend/src/app/shared/uis/employee-picker/employee-picker.spec.ts` :

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { EmployeePicker, EmployeePickerDto } from './employee-picker';

@Component({
  standalone: true,
  imports: [EmployeePicker],
  template: `
    <app-employee-picker
      [employees]="employees"
      [selectedId]="sel"
      [allowAny]="true"
      (selectChange)="sel = $event"
    />
  `,
})
class HostComponent {
  employees: EmployeePickerDto[] = [
    { id: 1, name: 'Léa Martin' },
    { id: 2, name: 'Camille Dupont' },
  ];
  sel: number | null = null;
}

describe('EmployeePicker', () => {
  let fixture: ComponentFixture<HostComponent>;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders "Indifférent" + each employee', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Indifférent');
    expect(text).toContain('Léa');
    expect(text).toContain('Camille');
  });

  it('marks "Indifférent" as selected by default (selectedId=null + allowAny)', () => {
    const rows = fixture.nativeElement.querySelectorAll('.emp-row');
    expect(rows[0].classList.contains('sel')).toBe(true);
  });

  it('selecting Léa emits her id', () => {
    const rows: HTMLElement[] = fixture.nativeElement.querySelectorAll('.emp-row');
    rows[1].click();
    fixture.detectChanges();
    expect(fixture.componentInstance.sel).toBe(1);
  });

  it('renders avatar with initial', () => {
    const avatars = fixture.nativeElement.querySelectorAll('.emp-avatar');
    expect(avatars[1].textContent.trim()).toBe('L');
    expect(avatars[2].textContent.trim()).toBe('C');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm test -- --include='**/employee-picker.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10
```

Expected: FAIL.

- [ ] **Step 3: Implémenter le composant**

Create `frontend/src/app/shared/uis/employee-picker/employee-picker.ts` :

```typescript
import { Component, input, output } from '@angular/core';

export interface EmployeePickerDto {
  id: number;
  name: string;
}

@Component({
  selector: 'app-employee-picker',
  standalone: true,
  template: `
    <div class="emp-list">
      @if (allowAny()) {
        <div class="emp-row" [class.sel]="selectedId() === null" (click)="select(null)">
          <div class="emp-avatar any">★</div>
          <span class="nm">Indifférent</span>
        </div>
      }
      @for (e of employees(); track e.id) {
        <div class="emp-row" [class.sel]="selectedId() === e.id" (click)="select(e.id)">
          <div class="emp-avatar">{{ initial(e.name) }}</div>
          <span class="nm">{{ e.name }}</span>
        </div>
      }
    </div>
  `,
  styles: `
    .emp-list { display: flex; flex-direction: column; gap: 6px; }
    .emp-row {
      display: flex; align-items: center; gap: 10px;
      padding: 8px 10px; border: 1px solid #ececec; border-radius: 4px;
      background: #fff; cursor: pointer; font-size: 12.5px;
      transition: border-color 0.15s;
    }
    .emp-row:hover { border-color: #c66075; }
    .emp-row.sel { border-color: #c66075; background: rgba(198,96,117,0.04); }
    .emp-avatar {
      width: 26px; height: 26px; border-radius: 50%;
      background: linear-gradient(135deg, #f8b4d0, #c66075);
      color: #fff; font-family: 'Cormorant Garamond', Georgia, serif;
      font-style: italic; font-size: 13px;
      display: grid; place-items: center; flex-shrink: 0;
    }
    .emp-avatar.any {
      background: #f3ede9; color: #999;
      font-family: inherit; font-style: normal; font-size: 14px; font-weight: 500;
    }
    .nm { font-weight: 500; color: #1a1a1a; }
  `,
})
export class EmployeePicker {
  readonly employees = input.required<EmployeePickerDto[]>();
  readonly selectedId = input<number | null>(null);
  readonly allowAny = input<boolean>(false);
  readonly selectChange = output<number | null>();

  protected select(id: number | null): void {
    if (id !== this.selectedId()) {
      this.selectChange.emit(id);
    }
  }

  protected initial(name: string): string {
    return name.trim().charAt(0).toUpperCase();
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --include='**/employee-picker.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10
```

Expected: 4/4 PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/uis/employee-picker/
git commit -m "feat(employee-picker): add vertical card picker with optional 'any' option"
```

---

### Task 10 : Composant annexe `<app-mini-calendar>`

**Files :**
- Create : `frontend/src/app/shared/uis/mini-calendar/mini-calendar.ts`
- Create : `frontend/src/app/shared/uis/mini-calendar/mini-calendar.spec.ts`

- [ ] **Step 1: Écrire le test failing**

Create `frontend/src/app/shared/uis/mini-calendar/mini-calendar.spec.ts` :

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { MiniCalendar } from './mini-calendar';

@Component({
  standalone: true,
  imports: [MiniCalendar],
  template: `
    <app-mini-calendar
      [closedDays]="closed"
      [holidays]="holidays"
      [selected]="selected"
      [minDate]="minDate"
      [initialMonth]="initialMonth"
      (dateChange)="onPick($event)"
    />
  `,
})
class HostComponent {
  closed = new Set<string>(['2026-05-11']);
  holidays = new Set<string>(['2026-05-08']);
  selected: Date | null = null;
  minDate = new Date(2026, 4, 1);
  initialMonth = new Date(2026, 4, 1);
  picked: Date | null = null;
  onPick(d: Date) { this.picked = d; }
}

describe('MiniCalendar', () => {
  let fixture: ComponentFixture<HostComponent>;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders the month label', () => {
    const head = fixture.nativeElement.querySelector('.month');
    expect(head?.textContent.toLowerCase()).toContain('mai');
  });

  it('marks closed days with class .closed', () => {
    const cells: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.d');
    const closed = Array.from(cells).filter(c => c.classList.contains('closed'));
    expect(closed.length).toBeGreaterThan(0);
  });

  it('marks holidays with class .holiday', () => {
    const cells: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.d');
    const hol = Array.from(cells).filter(c => c.classList.contains('holiday'));
    expect(hol.length).toBe(1);
  });

  it('emits dateChange when a non-closed day is clicked', () => {
    const cells: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.d:not(.closed):not(.dim)');
    cells[0].click();
    expect(fixture.componentInstance.picked).not.toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm test -- --include='**/mini-calendar.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10
```

Expected: FAIL.

- [ ] **Step 3: Implémenter le composant**

Create `frontend/src/app/shared/uis/mini-calendar/mini-calendar.ts` :

```typescript
import { Component, computed, input, output, signal } from '@angular/core';

@Component({
  selector: 'app-mini-calendar',
  standalone: true,
  template: `
    <div class="cal">
      <div class="cal-head">
        <button type="button" (click)="prevMonth()" aria-label="Mois précédent">‹</button>
        <span class="month">{{ monthLabel() }}</span>
        <button type="button" (click)="nextMonth()" aria-label="Mois suivant">›</button>
      </div>
      <div class="cal-grid">
        <span class="h">L</span><span class="h">M</span><span class="h">M</span>
        <span class="h">J</span><span class="h">V</span><span class="h">S</span><span class="h">D</span>
        @for (cell of cells(); track cell.iso) {
          <span
            class="d"
            [class.dim]="cell.dim"
            [class.closed]="cell.closed"
            [class.holiday]="cell.holiday"
            [class.sel]="cell.selected"
            (click)="pick(cell)"
          >{{ cell.day }}</span>
        }
      </div>
    </div>
  `,
  styles: `
    @media (min-width: 768px) {
      .cal { border: 1px solid #ececec; border-radius: 4px; padding: 12px; background: #fff; }
      .cal-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
      .cal-head .month { font-size: 13px; font-weight: 500; color: #1a1a1a; text-transform: capitalize; }
      .cal-head button {
        width: 24px; height: 24px; border: 1px solid #ddd; background: #fff;
        border-radius: 4px; cursor: pointer; font-family: inherit;
      }
      .cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; font-size: 11px; }
      .cal-grid .h { color: #999; text-align: center; padding: 4px 0; font-weight: 500; }
      .cal-grid .d {
        aspect-ratio: 1 / 1; display: grid; place-items: center;
        border-radius: 4px; cursor: pointer; color: #1a1a1a;
      }
      .cal-grid .d:hover:not(.dim):not(.closed):not(.sel) { background: rgba(198,96,117,0.08); }
      .cal-grid .d.dim { color: #ddd; cursor: not-allowed; }
      .cal-grid .d.closed { color: #ddd; text-decoration: line-through; cursor: not-allowed; }
      .cal-grid .d.holiday { background: #fff5f6; color: #b3001b; cursor: not-allowed; }
      .cal-grid .d.sel { background: #c66075; color: #fff; font-weight: 500; }
    }
  `,
})
export class MiniCalendar {
  readonly closedDays = input<Set<string>>(new Set());
  readonly holidays = input<Set<string>>(new Set());
  readonly selected = input<Date | null>(null);
  readonly minDate = input<Date | null>(null);
  readonly initialMonth = input<Date>(new Date());
  readonly dateChange = output<Date>();
  readonly monthChange = output<Date>();

  protected readonly viewMonth = signal<Date>(this.initialMonth());

  constructor() {
    // Sync viewMonth when initialMonth input changes (defensive — usually set once)
    queueMicrotask(() => this.viewMonth.set(this.initialMonth()));
  }

  protected readonly monthLabel = computed(() => {
    return this.viewMonth().toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  });

  protected readonly cells = computed(() => {
    const d = this.viewMonth();
    const year = d.getFullYear();
    const month = d.getMonth();
    const first = new Date(year, month, 1);
    const last = new Date(year, month + 1, 0);

    // Lundi = 0
    const startOffset = (first.getDay() + 6) % 7;
    const totalDays = last.getDate();
    const cells: Array<{ iso: string; day: number; dim: boolean; closed: boolean; holiday: boolean; selected: boolean; date: Date }> = [];

    for (let i = 0; i < startOffset; i++) {
      const date = new Date(year, month, i - startOffset + 1);
      cells.push({ iso: this.iso(date), day: date.getDate(), dim: true, closed: false, holiday: false, selected: false, date });
    }

    for (let day = 1; day <= totalDays; day++) {
      const date = new Date(year, month, day);
      const iso = this.iso(date);
      const isPast = this.minDate() ? date < this.minDate()! : false;
      const sel = !!(this.selected() && this.iso(this.selected()!) === iso);
      cells.push({
        iso,
        day,
        dim: isPast,
        closed: this.closedDays().has(iso),
        holiday: this.holidays().has(iso),
        selected: sel,
        date,
      });
    }
    return cells;
  });

  protected prevMonth(): void {
    const d = this.viewMonth();
    const next = new Date(d.getFullYear(), d.getMonth() - 1, 1);
    this.viewMonth.set(next);
    this.monthChange.emit(next);
  }

  protected nextMonth(): void {
    const d = this.viewMonth();
    const next = new Date(d.getFullYear(), d.getMonth() + 1, 1);
    this.viewMonth.set(next);
    this.monthChange.emit(next);
  }

  protected pick(cell: { dim: boolean; closed: boolean; holiday: boolean; date: Date }): void {
    if (cell.dim || cell.closed || cell.holiday) return;
    this.dateChange.emit(cell.date);
  }

  private iso(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --include='**/mini-calendar.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10
```

Expected: 4/4 PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/uis/mini-calendar/
git commit -m "feat(mini-calendar): add 240px calendar with closed/holiday/dim/selected days"
```

---

### Task 11 : i18n — clés communes du wrapper

**Files :**
- Modify : `frontend/public/i18n/fr.json`
- Modify : `frontend/public/i18n/en.json`

- [ ] **Step 1: Localiser la section commune existante dans fr.json**

```bash
grep -n '"common":' /Users/Gustavo.alves/Documents/personal/portfolio/frontend/public/i18n/fr.json | head -2
```

Expected: une ligne du genre `46:  "common": {`.

- [ ] **Step 2: Ajouter les clés `modalForm.*` à fr.json**

Localiser la section qui contient `"common"`. Insérer **avant** la fermeture `}` du fichier (ou après une section logique) :

```json
"modalForm": {
  "kbdSave": "⌘S pour enregistrer",
  "kbdValidate": "⏎ pour valider",
  "kbdValidateMulti": "⌘⏎ pour valider",
  "back": "‹ Retour"
},
```

- [ ] **Step 3: Ajouter les mêmes clés à en.json**

```json
"modalForm": {
  "kbdSave": "⌘S to save",
  "kbdValidate": "⏎ to validate",
  "kbdValidateMulti": "⌘⏎ to validate",
  "back": "‹ Back"
},
```

- [ ] **Step 4: Vérifier la validité JSON**

```bash
cd frontend && node -e "JSON.parse(require('fs').readFileSync('public/i18n/fr.json'))" && node -e "JSON.parse(require('fs').readFileSync('public/i18n/en.json'))" && echo "JSON OK"
```

Expected: `JSON OK`.

- [ ] **Step 5: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat(i18n): add modalForm.* keys (kbd hints + back) for PC modals"
```

---

### Task 12 : Vérification visuelle de bout en bout (phase 1)

**Files :** Aucun fichier modifié — vérification manuelle.

- [ ] **Step 1: Démarrer l'environnement complet**

```bash
docker compose --profile dev up -d --force-recreate frontend-dev
```

Vérifier que le backend tourne en local sur `http://localhost:8080` (sinon `cd backend && mvn spring-boot:run`).

- [ ] **Step 2: Vérification PC (viewport ≥1024px)**

Open `http://localhost:4300/pro/cares` dans Chrome. Cliquer sur "Ajouter un soin".

Constater :
- ✅ Filet rosé en haut de la modale
- ✅ Header sans icône Material géante
- ✅ Footer sans icônes Material à côté des boutons
- ✅ Bouton primaire rose Pretty Face
- ❌ Pas de breadcrumb (le caller `create-care` ne l'a pas encore branché — normal, c'est en Phase 2)
- ❌ Pas de sous-titre (idem)
- ❌ Titre toujours en sans-serif (le caller passe `title="Créer un nouveau soin"` mais le wrapper ne sait pas mettre en italique automatiquement — c'est attendu, le titre est traité par CSS serif globalement)

⚠ Si le visuel est cassé (modale invisible, footer mal placé, etc.) : revenir en arrière sur les commits Task 2 et 3, identifier le problème, corriger avant de continuer.

- [ ] **Step 3: Vérification mobile (Chrome DevTools — 375x812)**

Toujours sur `/pro/cares`, ouvrir le sélecteur de viewport et choisir **iPhone SE / 375x812**. Cliquer sur "+ Ajouter".

Constater :
- ✅ Bottom-sheet slide up (animation conservée)
- ✅ Handle en haut
- ✅ Fond `#fafafa`
- ✅ Largeur 100vw
- **Aucune** régression visuelle vs avant

Si le mobile est cassé : la nouvelle grammaire fuit hors du `@media (min-width: 768px)`. Auditer le SCSS.

- [ ] **Step 4: Run all touched specs**

```bash
cd frontend && npm test -- --include='**/modal-form.spec.ts' --include='**/modal-stepper.spec.ts' --include='**/modal-tabs.spec.ts' --include='**/cares-multi-picker.spec.ts' --include='**/care-row.spec.ts' --include='**/employee-picker.spec.ts' --include='**/mini-calendar.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -20
```

Expected: tous les specs passent. Si non, fixer avant la Phase 2.

- [ ] **Step 5: Tag de fin de phase 1**

```bash
git tag phase-1-modales-pc-infra
```

Note : pas de commit si rien n'a changé. Le tag est juste un repère pour reset facilement si la Phase 2 dérape.

---

## Phase 2 — Migration des modales (par ordre validé)

> Chaque task de Phase 2 suit le même pattern : (1) brancher les nouveaux inputs sur le caller, (2) remplacer Material `<mat-form-field>` etc. par des inputs natifs si présents, (3) supprimer les icônes legacy, (4) actualiser i18n si besoin, (5) tester visuellement PC + mobile, (6) commit.

### Task 13 : Migrer `delete-care` (S, dangerous)

**Files :**
- Modify : `frontend/src/app/features/cares/modals/delete/delete-care.component.ts`
- Modify : `frontend/src/app/features/cares/modals/delete/delete-care.component.html`
- Modify : `frontend/src/app/features/cares/modals/delete/delete-care.component.scss`
- Modify : `frontend/public/i18n/fr.json`
- Modify : `frontend/public/i18n/en.json`

- [ ] **Step 1: Localiser/ajouter clés i18n**

Vérifier dans fr.json. Si `cares.delete.*` n'existe pas, ajouter :

```json
"cares": {
  ...,
  "delete": {
    "title": "Supprimer « {{name}} » ?",
    "body": "Cette action supprimera définitivement ce soin du catalogue. Tu pourras le recréer plus tard, mais l'historique associé sera perdu.",
    "warn": "Les rendez-vous déjà planifiés ne sont pas affectés et restent visibles dans le planning.",
    "confirm": "Supprimer définitivement"
  }
}
```

Et dans en.json :

```json
"cares": {
  ...,
  "delete": {
    "title": "Delete \"{{name}}\"?",
    "body": "This will permanently remove this care from your catalog. You can re-create it later, but its history will be lost.",
    "warn": "Existing appointments are not affected and remain visible in the planning.",
    "confirm": "Delete permanently"
  }
}
```

- [ ] **Step 2: Refondre `delete-care.component.html`**

Replace `frontend/src/app/features/cares/modals/delete/delete-care.component.html` :

```html
<modal-form
  size="s"
  breadcrumbParent="Catalogue / Soins"
  [title]="title"
  [dangerous]="true"
  [saveLabel]="'cares.delete.confirm' | transloco"
  (save)="confirm()"
  (cancel)="cancel()">

  <p class="confirm-body">
    {{ 'cares.delete.body' | transloco }}
  </p>
  <div class="confirm-warn">
    <span class="ico">!</span>
    <span>{{ 'cares.delete.warn' | transloco }}</span>
  </div>

</modal-form>
```

- [ ] **Step 3: Refondre `delete-care.component.ts`**

Replace `frontend/src/app/features/cares/modals/delete/delete-care.component.ts` :

```typescript
import { Component, inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ModalForm } from '../../../../shared/uis/modal-form/modal-form';

interface DeleteCareDialogData {
  careName: string;
}

@Component({
  selector: 'app-delete-care',
  standalone: true,
  imports: [ModalForm, TranslocoPipe],
  templateUrl: './delete-care.component.html',
  styleUrl: './delete-care.component.scss',
})
export class DeleteCareComponent {
  private readonly dialogRef = inject(MatDialogRef<DeleteCareComponent>);
  private readonly i18n = inject(TranslocoService);
  protected readonly data = inject<DeleteCareDialogData>(MAT_DIALOG_DATA);

  get title(): string {
    return this.i18n.translate('cares.delete.title', { name: this.data.careName ?? '' });
  }

  confirm(): void { this.dialogRef.close(true); }
  cancel(): void { this.dialogRef.close(false); }
}
```

- [ ] **Step 4: Adapter `delete-care.component.scss` (uniquement les éléments customs)**

Replace `frontend/src/app/features/cares/modals/delete/delete-care.component.scss` :

```scss
@media (min-width: 768px) {
  .confirm-body {
    margin: 0;
    font-size: 13.5px; color: #444; line-height: 1.55;
  }
  .confirm-warn {
    margin-top: 12px;
    padding: 10px 12px;
    background: #fff5f6;
    border: 1px solid #f1d6db;
    border-radius: 4px;
    font-size: 12.5px; color: #7a3142;
    display: flex; align-items: flex-start; gap: 8px;
  }
  .confirm-warn .ico { color: #b3001b; font-weight: bold; }
}
```

- [ ] **Step 5: Vérifier que le caller utilise size="s"**

```bash
grep -n "DeleteCareComponent" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/features/cares/cares.component.ts
```

Si le caller (`cares.component.ts`) ouvre la modale avec `bottomSheetConfig({ data: ... })` sans size, modifier l'appel pour passer `bottomSheetConfig('s', { data: ... })`.

- [ ] **Step 6: Vérifications**

```bash
npx tsc --noEmit -p tsconfig.app.json 2>&1 | tail -10
```

Expected: zéro erreur.

Visuellement : `http://localhost:4300/pro/cares`, ouvrir un soin existant (icône poubelle ou équivalent) — vérifier la modale rouge avec breadcrumb "Catalogue / Soins", titre serif "Supprimer « X » ?", warn rosé, bouton rouge "Supprimer définitivement".

Mobile : vérifier que le bottom-sheet s'ouvre toujours correctement.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/features/cares/modals/delete/ frontend/src/app/features/cares/cares.component.ts frontend/public/i18n/
git commit -m "feat(delete-care): migrate to PC modal grammar (size=s, dangerous)"
```

---

### Task 14 : Migrer `no-show-confirm-dialog` (S, dangerous)

**Files :**
- Modify : `frontend/src/app/features/tracking/components/client-bookings/no-show-confirm-dialog.component.ts`
- Modify : `frontend/public/i18n/fr.json`, `en.json`

- [ ] **Step 1: i18n — réformuler les clés `noShow`**

Remplacer dans fr.json (section `tracking.bookings.noShow`) :

```json
"noShow": {
  "title": "Marquer ce rendez-vous comme absent ?",
  "intro": "Le rendez-vous sera enregistré comme un no-show et apparaîtra dans la fiche de la cliente.",
  "labelCare": "Soin",
  "labelDate": "Date",
  "warn": "Le compteur de no-shows de cette cliente augmentera.",
  "cancel": "Annuler",
  "submit": "Confirmer l'absence",
  "success": "Rendez-vous marqué comme no-show"
}
```

Et dans en.json :

```json
"noShow": {
  "title": "Mark this appointment as no-show?",
  "intro": "The appointment will be recorded as a no-show and appear in the client's record.",
  "labelCare": "Care",
  "labelDate": "Date",
  "warn": "This client's no-show counter will increase.",
  "cancel": "Cancel",
  "submit": "Confirm absence",
  "success": "Appointment marked as no-show"
}
```

- [ ] **Step 2: Refondre `no-show-confirm-dialog.component.ts`**

Replace `frontend/src/app/features/tracking/components/client-bookings/no-show-confirm-dialog.component.ts` :

```typescript
import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { ModalForm } from '../../../../shared/uis/modal-form/modal-form';

export interface NoShowConfirmData {
  careName: string;
  appointmentDate: string;
}

@Component({
  selector: 'app-no-show-confirm-dialog',
  standalone: true,
  imports: [TranslocoPipe, ModalForm],
  template: `
    <modal-form
      size="s"
      breadcrumbParent="Suivi / Rendez-vous"
      [title]="'tracking.bookings.noShow.title' | transloco"
      [dangerous]="true"
      [saveLabel]="'tracking.bookings.noShow.submit' | transloco"
      [cancelLabel]="'tracking.bookings.noShow.cancel' | transloco"
      (save)="dialogRef.close(true)"
      (cancel)="dialogRef.close(false)">

      <p class="intro">{{ 'tracking.bookings.noShow.intro' | transloco }}</p>
      <div class="recap">
        <span class="k">{{ 'tracking.bookings.noShow.labelCare' | transloco }}</span>
        <span class="v">{{ data.careName }}</span>
        <span class="k">{{ 'tracking.bookings.noShow.labelDate' | transloco }}</span>
        <span class="v">{{ data.appointmentDate }}</span>
      </div>
      <div class="warn">
        <span class="ico">!</span>
        <span>{{ 'tracking.bookings.noShow.warn' | transloco }}</span>
      </div>
    </modal-form>
  `,
  styles: [`
    @media (min-width: 768px) {
      .intro { margin: 0 0 10px; font-size: 13.5px; color: #444; line-height: 1.55; }
      .recap {
        display: grid; grid-template-columns: 90px 1fr; gap: 6px 12px;
        padding: 12px 14px; background: #fafafa; border: 1px solid #ececec; border-radius: 4px;
        font-size: 12.5px;
      }
      .recap .k { color: #999; font-size: 11.5px; text-transform: uppercase; letter-spacing: 0.08em; padding-top: 1px; }
      .recap .v { color: #1a1a1a; font-weight: 500; }
      .warn {
        margin-top: 12px; padding: 10px 12px;
        background: #fff5f6; border: 1px solid #f1d6db; border-radius: 4px;
        font-size: 12.5px; color: #7a3142;
        display: flex; align-items: flex-start; gap: 8px;
      }
      .warn .ico { color: #b3001b; font-weight: bold; }
    }
  `],
})
export class NoShowConfirmDialogComponent {
  readonly data = inject<NoShowConfirmData>(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<NoShowConfirmDialogComponent>);
}
```

- [ ] **Step 3: Vérifier le caller**

```bash
grep -n "NoShowConfirmDialogComponent" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/features/tracking/components/client-bookings/client-bookings.component.ts
```

Au point d'ouverture (ligne 289), modifier en `bottomSheetConfig('s', { data })` si nécessaire.

- [ ] **Step 4: Vérifications + commit**

```bash
npx tsc --noEmit -p tsconfig.app.json
```

Expected: 0 erreur. Vérifier visuellement (PC + mobile) une fois le backend tournant.

```bash
git add frontend/src/app/features/tracking/ frontend/public/i18n/
git commit -m "feat(no-show-confirm): migrate to PC modal grammar (size=s, dangerous)"
```

---

### Task 15 : Migrer `confirm-dialog` (générique S)

**Files :**
- Modify : `frontend/src/app/pages/pro/confirm-dialog.component.ts`
- Modify : `frontend/src/app/pages/pro/pro-dashboard.component.ts` (caller)

- [ ] **Step 1: Refondre `confirm-dialog.component.ts`**

Replace `frontend/src/app/pages/pro/confirm-dialog.component.ts` :

```typescript
import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { ModalForm } from '../../shared/uis/modal-form/modal-form';

export interface ConfirmDialogData {
  title: string;
  body: string;
  action: string;
  breadcrumbParent: string;
  dangerous?: boolean;
}

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [TranslocoPipe, ModalForm],
  template: `
    <modal-form
      size="s"
      [breadcrumbParent]="data.breadcrumbParent"
      [title]="data.title"
      [dangerous]="data.dangerous ?? false"
      [saveLabel]="data.action"
      [cancelLabel]="'common.cancel' | transloco"
      (save)="ref.close(true)"
      (cancel)="ref.close(false)">

      <p class="body">{{ data.body }}</p>
    </modal-form>
  `,
  styles: [`
    @media (min-width: 768px) {
      .body { margin: 0; font-size: 13.5px; color: #444; line-height: 1.55; }
    }
  `],
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<ConfirmDialogComponent, boolean>);
}
```

- [ ] **Step 2: Mettre à jour le caller (pro-dashboard.ts:420)**

Open `frontend/src/app/pages/pro/pro-dashboard.component.ts` et modifier `onUnpublish()` :

```typescript
onUnpublish(): void {
  const dialogRef = this.dialog.open(ConfirmDialogComponent, bottomSheetConfig('s', {
    data: {
      breadcrumbParent: 'Tableau de bord / Vitrine',
      title: this.transloco.translate('pro.dashboard.unpublishConfirmTitle'),
      body: this.transloco.translate('pro.dashboard.unpublishConfirmBody'),
      action: this.transloco.translate('pro.dashboard.unpublishConfirmAction'),
      dangerous: true,
    },
  }));

  dialogRef.afterClosed().subscribe((confirmed) => {
    if (confirmed) {
      this.store.unpublish();
    }
  });
}
```

- [ ] **Step 3: Vérifications + commit**

```bash
npx tsc --noEmit -p tsconfig.app.json
git add frontend/src/app/pages/pro/confirm-dialog.component.ts frontend/src/app/pages/pro/pro-dashboard.component.ts
git commit -m "feat(confirm-dialog): migrate generic confirm to PC grammar (S, optional dangerous)"
```

---

### Task 16 : Migrer `login-modal` (M)

**Files :**
- Modify : `frontend/src/app/shared/modals/login-modal/login-modal.component.{ts,html,scss}`
- Modify : `frontend/public/i18n/{fr,en}.json`

- [ ] **Step 1: i18n — clés**

Add to fr.json (section auth.login) :

```json
"loginSubtitle": "Accède à ton tableau de bord ou à tes rendez-vous.",
"loginGoogle": "Continuer avec Google",
"loginBreadcrumb": "Compte"
```

Identique en EN avec traductions.

- [ ] **Step 2: Refondre `login-modal.component.html`**

Replace :

```html
<modal-form
  size="m"
  [breadcrumbParent]="'auth.login.loginBreadcrumb' | transloco"
  [title]="'auth.login.title' | transloco"
  [subtitle]="'auth.login.loginSubtitle' | transloco"
  [saveLabel]="'auth.login.submit' | transloco"
  [saveDisabled]="loading() || loginForm.invalid"
  [kbdHint]="'modalForm.kbdValidate' | transloco"
  (save)="login()"
  (cancel)="close()">

  <form [formGroup]="loginForm" (ngSubmit)="login()" class="login-form">
    <div class="field">
      <label for="lm-email">{{ 'auth.login.email' | transloco }}</label>
      <input
        id="lm-email"
        type="email"
        formControlName="email"
        autocomplete="email"
        placeholder="prenom@exemple.fr"
      />
      @if (loginForm.controls.email.hasError('required') && loginForm.controls.email.touched) {
        <div class="err">{{ 'auth.errors.emailRequired' | transloco }}</div>
      }
      @if (loginForm.controls.email.hasError('email') && loginForm.controls.email.touched) {
        <div class="err">{{ 'auth.errors.emailInvalid' | transloco }}</div>
      }
    </div>

    <div class="field">
      <label for="lm-pw">{{ 'auth.login.password' | transloco }}</label>
      <input
        id="lm-pw"
        type="password"
        formControlName="password"
        autocomplete="current-password"
        placeholder="••••••••"
      />
      @if (loginForm.controls.password.hasError('required') && loginForm.controls.password.touched) {
        <div class="err">{{ 'auth.errors.passwordRequired' | transloco }}</div>
      }
    </div>

    @if (error()) {
      <div class="err global">{{ error() | transloco }}</div>
    }

    <!-- TODO post-implem auth backend : "Mot de passe oublié ?" + "Se souvenir de moi" -->
    <!-- Voir mémoire project_pending_auth_features -->

    <div class="divider">{{ 'auth.login.orDivider' | transloco }}</div>

    <button type="button" class="google-btn" (click)="loginWithGoogle()">
      <span class="google-g"></span>
      <span>{{ 'auth.login.loginGoogle' | transloco }}</span>
    </button>
  </form>
</modal-form>
```

- [ ] **Step 3: Refondre `login-modal.component.ts`**

Replace :

```typescript
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';
import { ModalForm } from '../../uis/modal-form/modal-form';

@Component({
  selector: 'app-login-modal',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, TranslocoPipe, ModalForm],
  templateUrl: './login-modal.component.html',
  styleUrl: './login-modal.component.scss',
})
export class LoginModalComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly dialogRef = inject(MatDialogRef<LoginModalComponent>);

  loading = signal(false);
  error = signal<string | null>(null);

  loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  login(): void {
    if (this.loginForm.invalid) return;
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.loginForm.value;
    this.authService.loginWithCredentials(email!, password!).subscribe({
      next: () => { this.loading.set(false); this.dialogRef.close(true); },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'auth.errors.invalidCredentials');
      },
    });
  }

  loginWithGoogle(): void {
    this.dialogRef.close();
    // Reuse existing API exposed by AuthService (cf. auth-modal.component.ts).
    (this.authService as any).loginWithGoogle?.('client');
  }

  close(): void { this.dialogRef.close(); }
}
```

- [ ] **Step 4: Refondre `login-modal.component.scss`**

Replace :

```scss
@media (min-width: 768px) {
  .login-form { display: flex; flex-direction: column; gap: 0; }
  .field { margin-bottom: 14px; }
  .field label { display: block; font-size: 12px; color: #666; margin-bottom: 5px; font-weight: 500; }
  .field input {
    width: 100%; box-sizing: border-box;
    height: 38px; border: 1px solid #ddd; border-radius: 4px;
    padding: 0 12px; font-size: 13.5px; background: #fff; color: #1a1a1a;
    font-family: inherit;
  }
  .field input:focus { outline: 2px solid rgba(198,96,117,0.18); border-color: #c66075; }
  .err {
    margin-top: 6px; font-size: 11.5px; color: #b3001b;
  }
  .err.global {
    margin: 4px 0 14px; padding: 10px 12px;
    background: #fff5f6; border: 1px solid #f1d6db; border-radius: 4px;
    font-size: 12.5px; color: #7a3142;
  }
  .divider {
    text-align: center; margin: 4px 0 14px; position: relative;
    color: #999; font-size: 11.5px;
  }
  .divider::before, .divider::after {
    content: ''; position: absolute; top: 50%; height: 1px; background: #ececec; width: calc(50% - 22px);
  }
  .divider::before { left: 0; }
  .divider::after { right: 0; }
  .google-btn {
    width: 100%; height: 40px;
    border: 1px solid #ddd; background: #fff; border-radius: 4px;
    display: flex; align-items: center; justify-content: center; gap: 10px;
    font-size: 13.5px; font-weight: 500; color: #1a1a1a; cursor: pointer;
    font-family: inherit;
  }
  .google-btn:hover { border-color: #c66075; }
  .google-g {
    width: 16px; height: 16px; border-radius: 50%;
    background: conic-gradient(from 0deg at 50% 50%, #ea4335 0 25%, #fbbc05 25% 50%, #34a853 50% 75%, #4285f4 75% 100%);
  }
}
```

- [ ] **Step 5: Mettre à jour le caller (header.ts)**

```bash
grep -n "LoginModalComponent" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/shared/layout/header/header.ts
```

Modifier l'appel à `bottomSheetConfig('m', ...)` si nécessaire.

- [ ] **Step 6: Vérifications + commit**

```bash
npx tsc --noEmit -p tsconfig.app.json
git add frontend/src/app/shared/modals/login-modal/ frontend/src/app/shared/layout/header/header.ts frontend/public/i18n/
git commit -m "feat(login-modal): migrate to PC modal grammar (size=m), add Google CTA, native inputs"
```

---

### Task 17 : Migrer `auth-modal` (M, contextuel booking)

**Files :**
- Modify : `frontend/src/app/shared/modals/auth-modal/auth-modal.component.{ts,html,scss}`
- Modify : `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.ts` (caller)
- Modify : `frontend/public/i18n/{fr,en}.json`

- [ ] **Step 1: Définir l'enrichissement du DTO**

Open `frontend/src/app/shared/modals/auth-modal/auth-modal.component.ts`. Avant tout, ajouter une interface `AuthModalData` exportée :

```typescript
export interface AuthModalData {
  salonName?: string;
  careName?: string;
  appointmentDate?: string;
  initialTab?: 'login' | 'register';
}
```

- [ ] **Step 2: i18n — nouvelles clés contextuelles (fr.json)**

```json
"auth": {
  ...,
  "modal": {
    ...,
    "loginTitleContext": "Encore une étape",
    "loginSubtitleContext": "Connecte-toi ou crée un compte pour finaliser ta réservation chez <strong>{{salon}}</strong>.",
    "registerTitleContext": "Crée ton compte",
    "registerSubtitleContext": "En 30 secondes — tu retrouveras tes rendez-vous, tes notes et tes salons favoris.",
    "selectionKept": "Ta sélection est conservée : <strong>{{care}}</strong> · {{date}}.",
    "later": "Plus tard"
  }
}
```

Idem en.json.

- [ ] **Step 3: Refondre le template (auth-modal.component.html)**

Replace by a version using `<modal-form>` with `<modal-tabs>` for login/register switch. Voir le détail dans le mockup `m5-auth.html` à `.superpowers/brainstorm/14863-1778288487/content/m5-auth.html`. Structure :

```html
<modal-form
  size="m"
  [breadcrumbParent]="breadcrumbParent"
  [title]="title()"
  [subtitle]="subtitle()"
  [saveLabel]="primaryLabel()"
  [cancelLabel]="'auth.modal.later' | transloco"
  [saveDisabled]="isLoading() || activeForm.invalid"
  [kbdHint]="'modalForm.kbdValidate' | transloco"
  (save)="onSubmit()"
  (cancel)="close()">

  <modal-tabs
    [tabs]="[
      { id: 'login', label: ('auth.modal.loginTab' | transloco) },
      { id: 'register', label: ('auth.modal.registerTab' | transloco) }
    ]"
    [active]="activeTabId()"
    (change)="setTab($event)"
  />

  @if (data?.careName && data?.appointmentDate) {
    <div class="resume-banner">
      <span class="ico">●</span>
      <span [innerHTML]="'auth.modal.selectionKept' | transloco: { care: data!.careName, date: data!.appointmentDate }"></span>
    </div>
  }

  @switch (activeTabId()) {
    @case ('login') { <!-- login fields, identique à login-modal --> }
    @case ('register') { <!-- register fields + consent --> }
  }
</modal-form>
```

(Le template complet fait ~120 lignes. Reproduire la structure de `login-modal.component.html` adapté + section register avec champs `name, email, password, consent`.)

- [ ] **Step 4: Refondre le composant TS**

Calculer `title()`, `subtitle()`, `primaryLabel()`, `breadcrumbParent`, `activeTabId()` comme `computed` ou `signal`. Les formulaires existants (loginForm, registerForm) restent identiques. Garder `loginWithGoogle()` et la logique `onLogin/onRegister` intactes.

`breadcrumbParent` :
- si `initialTab === 'register'` : `'Réservation / Création du compte'`
- sinon : `'Réservation / Connexion requise'`

`title()` et `subtitle()` switchent selon `activeTabId()` et utilisent `data.salonName` quand présent.

- [ ] **Step 5: Mettre à jour le caller (booking-dialog.component.ts:185)**

```typescript
private openAuthAndMaybeSubmit(): void {
  const slug = this.slug;
  // Nom du salon récupéré via le SalonProfileStore (à injecter si pas déjà)
  const salonName = /* récupérer depuis store/service */;
  const data: AuthModalData = {
    salonName,
    careName: this.care.name,
    appointmentDate: this.formatHumanDate(this.selectedDate()!, this.selectedSlot()!.startTime),
  };
  const authRef = this.matDialog.open(AuthModalComponent, bottomSheetConfig('m', { data }));
  authRef.afterClosed().subscribe((result: AuthModalResult | undefined) => {
    if (!result?.authenticated) return;  // "Plus tard" : on garde la sélection (rien à reset)
    if (result.action === 'login') this.submitBooking();
    else this.registerJustCompleted.set(true);
  });
}
```

Ajouter `formatHumanDate(date: Date, time: string): string` qui retourne `"Mar. 12 mai · 14:30"`.

- [ ] **Step 6: Vérification — "Plus tard" conserve bien la sélection**

Manuellement : `http://localhost:4300/salon/<slug>` (slug d'un salon publié). Cliquer "Réserver" sur un soin → choisir date+heure → cliquer "Réserver" → modale auth s'ouvre → cliquer "Plus tard" → vérifier que le booking-dialog reste ouvert avec la sélection intacte.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/shared/modals/auth-modal/ frontend/src/app/pages/salon/booking-dialog/ frontend/public/i18n/
git commit -m "feat(auth-modal): migrate to PC grammar with contextual booking recap, sober tabs"
```

---

### Task 18 : Migrer `create-category` (M, mode dual)

**Files :**
- Modify : `frontend/src/app/features/categories/modals/create/create-category.component.ts`
- Modify : `frontend/public/i18n/{fr,en}.json`

- [ ] **Step 1: i18n — clés**

Add to fr.json (`pro.categories`) :

```json
"newSubtitle": "Une catégorie regroupe des soins similaires (ex. <em>Soins du visage</em>). Les clientes la voient sur la vitrine.",
"editSubtitle": "{{count}} soins associés. Renommer la catégorie n'affecte pas les rendez-vous existants.",
"createBtn": "Créer la catégorie",
"saveBtn": "Enregistrer",
"descriptionHint": "Apparaît sur la page publique du salon."
```

Identique en.json.

- [ ] **Step 2: Refondre le composant**

Replace `create-category.component.ts` :

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ModalForm } from '../../../../shared/uis/modal-form/modal-form';
import { CreateCategoryRequest, Category } from '../../models/categories.model';

export interface CategoryDialogData {
  category?: Category;
  caresCount?: number;  // utilisé en mode édition pour le sous-titre
}

@Component({
  selector: 'app-create-category',
  standalone: true,
  imports: [ReactiveFormsModule, TranslocoPipe, ModalForm],
  template: `
    <modal-form
      size="m"
      breadcrumbParent="Catalogue / Catégories"
      [title]="title()"
      [subtitle]="subtitle()"
      [saveLabel]="saveLabel()"
      [saveDisabled]="form.invalid"
      [kbdHint]="'modalForm.kbdSave' | transloco"
      (save)="onSave()"
      (cancel)="onCancel()">

      <form [formGroup]="form">
        <div class="field-row">
          <span class="field-label">{{ 'categories.columns.name' | transloco }} <span class="req">*</span></span>
          <div>
            <input class="field-input" formControlName="name" placeholder="Ex. Soins du visage" />
          </div>
        </div>
        <div class="field-row last">
          <span class="field-label">{{ 'categories.columns.description' | transloco }}</span>
          <div>
            <textarea class="field-input" formControlName="description" rows="3"></textarea>
            <div class="field-hint">{{ 'pro.categories.descriptionHint' | transloco }}</div>
          </div>
        </div>
      </form>
    </modal-form>
  `,
  styles: [`
    @media (min-width: 768px) {
      .field-row {
        display: grid; grid-template-columns: 130px 1fr; gap: 16px;
        align-items: start; padding: 14px 0;
        border-bottom: 1px solid #f4f4f4; font-size: 13px;
      }
      .field-row.last { border-bottom: 0; }
      .field-label { color: #555; font-size: 12.5px; padding-top: 8px; }
      .field-label .req { color: #c66075; }
      .field-input {
        height: 34px; border: 1px solid #ddd; border-radius: 4px;
        padding: 0 10px; background: #fff; font-size: 13px;
        width: 100%; box-sizing: border-box; font-family: inherit;
      }
      .field-input:focus { outline: 2px solid rgba(198,96,117,0.18); border-color: #c66075; }
      textarea.field-input { height: auto; min-height: 76px; padding: 8px 10px; resize: vertical; }
      .field-hint { font-size: 11.5px; color: #999; margin-top: 4px; }
    }
  `],
})
export class CreateCategoryComponent implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<CreateCategoryComponent>);
  private readonly data: CategoryDialogData = inject(MAT_DIALOG_DATA, { optional: true }) ?? {};
  private readonly fb = inject(FormBuilder);
  private readonly i18n = inject(TranslocoService);

  form = this.fb.group({
    name: ['', [Validators.required]],
    description: [''],
  });

  get isEditMode(): boolean { return !!this.data.category; }

  title = () => this.isEditMode
    ? `Modifier <em>${this.data.category!.name}</em>`
    : 'Nouvelle catégorie';

  subtitle = () => this.isEditMode
    ? this.i18n.translate('pro.categories.editSubtitle', { count: this.data.caresCount ?? 0 })
    : this.i18n.translate('pro.categories.newSubtitle');

  saveLabel = () => this.isEditMode
    ? this.i18n.translate('pro.categories.saveBtn')
    : this.i18n.translate('pro.categories.createBtn');

  ngOnInit(): void {
    if (this.data.category) {
      this.form.patchValue({
        name: this.data.category.name,
        description: this.data.category.description ?? '',
      });
    }
  }

  onCancel(): void { this.dialogRef.close(); }

  onSave(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const data: CreateCategoryRequest = this.form.value as CreateCategoryRequest;
    this.dialogRef.close(data);
  }
}
```

- [ ] **Step 3: Mettre à jour le caller**

Dans `categories.component.ts` (callers `onAdd`, `onEdit`) — passer `caresCount` en mode edit. Adapter `bottomSheetConfig('m', ...)`.

- [ ] **Step 4: Vérifications + commit**

```bash
npx tsc --noEmit -p tsconfig.app.json
git add frontend/src/app/features/categories/modals/create/ frontend/src/app/features/categories/categories.component.ts frontend/public/i18n/
git commit -m "feat(create-category): migrate to PC grammar (M, native form, contextual edit subtitle)"
```

---

### Task 19 : Migrer `reassign-category` (M, dangerous + DTO enrichi)

**Files :**
- Modify : `frontend/src/app/features/categories/modals/reassign-category/reassign-category-dialog.component.ts`
- Modify : `frontend/src/app/features/categories/categories.component.ts` (caller)
- Modify : `backend/src/main/java/com/prettyface/app/category/web/dto/CategoryWithCaresResponse.java` (créer)
- Modify : `backend/src/main/java/com/prettyface/app/category/app/CategoryService.java`
- Modify : `backend/src/main/java/com/prettyface/app/category/web/CategoryController.java`

- [ ] **Step 1: Backend — créer/enrichir le DTO côté Java**

Voir si un endpoint `/api/categories/{id}/cares-summary` existe. Sinon, ajouter dans `CategoryController.java` :

```java
@GetMapping("/{id}/cares-summary")
public List<CareSummaryDto> caresSummary(@PathVariable Long id) {
    return categoryService.caresSummary(id);
}
```

Et créer `CareSummaryDto` :

```java
public record CareSummaryDto(Long id, String name, Integer durationMin, Long priceCents) {}
```

Et `CategoryService.caresSummary(Long id)` : retourne la liste des soins (id, nom, durée, prix en cents) appartenant à la catégorie.

Tester :

```java
// CategoryServiceTests — ajouter @Test
@Test
void caresSummary_returnsCareList() {
    Category cat = createCategoryWithCares(/* ... */);
    List<CareSummaryDto> summary = categoryService.caresSummary(cat.getId());
    assertThat(summary).hasSize(2);
    assertThat(summary.get(0).name()).isEqualTo("Soin éclat");
}
```

```bash
cd backend && mvn test -Dtest=CategoryServiceTests
```

Expected: PASS.

- [ ] **Step 2: Frontend — service côté Angular**

Add to `frontend/src/app/features/categories/services/categories.service.ts` :

```typescript
caresSummary(categoryId: number): Observable<CareSummary[]> {
  return this.http.get<CareSummary[]>(`${this.basePath}/${categoryId}/cares-summary`);
}
```

Et le type :

```typescript
export interface CareSummary {
  id: number;
  name: string;
  durationMin: number;
  priceCents: number;
}
```

- [ ] **Step 3: Refondre le composant Angular**

Replace `reassign-category-dialog.component.ts` complet avec ce qui correspond au mockup `m8-reassign-category.html` (2 colonnes from→to + select + liste cares scrollable). Détails clés :

```typescript
export interface ReassignCategoryDialogData {
  categoryId: number;
  categoryName: string;
  careCount: number;
  careList: CareSummary[];  // NOUVEAU — passé par le caller après chargement
  availableCategories: Category[];
}
```

Template avec `<modal-form size="m" [dangerous]="true" ...>` + récap from→to + select destination + liste scrollable.

- [ ] **Step 4: Mettre à jour le caller pour charger careList**

Dans `categories.component.ts` méthode `onDelete(category)` : charger `caresSummary` avant d'ouvrir la modale.

```typescript
onDeleteCategory(cat: Category) {
  if (cat.careCount === 0) {
    // Suppression directe sans réassignation (confirm classique)
    return this.deleteDirect(cat);
  }
  this.categoriesService.caresSummary(cat.id).subscribe(careList => {
    const dialogRef = this.dialog.open(ReassignCategoryDialogComponent, bottomSheetConfig('m', {
      data: {
        categoryId: cat.id,
        categoryName: cat.name,
        careCount: cat.careCount,
        careList,
        availableCategories: this.store.categories().filter(c => c.id !== cat.id),
      },
    }));
    dialogRef.afterClosed().subscribe(targetId => {
      if (targetId) this.store.reassignAndDelete(cat.id, targetId);
    });
  });
}
```

- [ ] **Step 5: Vérifications + commit (multi-fichiers)**

```bash
npx tsc --noEmit -p tsconfig.app.json
cd backend && mvn test -Dtest=CategoryServiceTests
```

Expected: tous PASS.

```bash
git add backend/ frontend/src/app/features/categories/ frontend/public/i18n/
git commit -m "feat(reassign-category): migrate to PC grammar (M, dangerous), enrich back DTO with careList"
```

---

### Task 20 : Migrer `review-leave-dialog` (M, deux modes)

**Files :**
- Modify : `frontend/src/app/features/leaves/modals/review-leave-dialog/review-leave-dialog.component.ts`
- Modify : `frontend/src/app/features/leaves/leaves.component.ts` (caller)
- Modify : `frontend/public/i18n/{fr,en}.json`

- [ ] **Step 1: Enrichir le DTO côté front (déjà dispo côté back)**

```typescript
export interface ReviewLeaveDialogData {
  leaveId: number;
  action: 'APPROVED' | 'REJECTED';
  employeeName: string;
  type: 'VACATION' | 'SICKNESS' | 'OTHER';
  startDate: string;  // ISO
  endDate: string;
  reason: string | null;
}
```

- [ ] **Step 2: i18n — réformuler les titres et ajouter les nouveaux**

```json
"approveTitleEmployee": "Approuver la demande de <em>{{name}}</em> ?",
"rejectTitleEmployee": "Refuser la demande de <em>{{name}}</em> ?",
"approveSubtitle": "{{name}} sera notifiée et l'absence apparaîtra automatiquement dans le planning.",
"rejectSubtitle": "{{name}} sera notifiée du refus et pourra soumettre une nouvelle demande.",
"recapType": "Type",
"recapDates": "Dates",
"recapReason": "Motif",
"messageLabel": "Message",
"reasonLabel": "Raison du refus",
"messageHintApprove": "Visible par l'employée dans son espace.",
"messageHintReject": "Visible par l'employée. Une raison aide à éviter les frustrations."
```

- [ ] **Step 3: Refondre le composant**

Replace `review-leave-dialog.component.ts` avec template `<modal-form size="m" [dangerous]="action === 'REJECTED'" ...>` + récap (type/dates/reason) + textarea contextuel. Le composant calcule `title`, `subtitle`, `saveLabel` selon `data.action` (computed/getter). Voir mockup `m9-review-leave.html`.

- [ ] **Step 4: Mettre à jour le caller `leaves.component.ts:93`**

Passer `type/startDate/endDate/reason` dans `data`. Adapter `bottomSheetConfig('m', ...)`.

- [ ] **Step 5: Vérifications + commit**

```bash
npx tsc --noEmit -p tsconfig.app.json
git add frontend/src/app/features/leaves/ frontend/public/i18n/
git commit -m "feat(review-leave): migrate to PC grammar (M, polymorphic approve/reject), enrich front DTO"
```

---

### Task 21 : Migrer `rate-visit-dialog` (M, étoiles roses)

**Files :**
- Modify : `frontend/src/app/pages/client-evolution/rate-visit-dialog.component.ts`
- Modify : `frontend/src/app/pages/client-evolution/client-evolution.component.ts` (caller, enrichit data)
- Modify : `frontend/public/i18n/{fr,en}.json`

- [ ] **Step 1: i18n — clés**

```json
"rateTitle": "Comment s'est passée ta visite ?",
"rateSubtitle": "Ton avis aide ton salon à s'améliorer et reste visible uniquement par l'équipe.",
"rateAsk": "Quelle note tu donnerais ?",
"rateLabel0": "Touche les étoiles pour noter",
"rateLabel1": "Bof",
"rateLabel2": "Pas terrible",
"rateLabel3": "Correct",
"rateLabel4": "Très bien — un détail à peaufiner ?",
"rateLabel5": "Parfait, merci !",
"rateCommentLabel": "Commentaire",
"rateCommentPlaceholder": "Optionnel — ce qui t'a plu, ce qu'on pourrait améliorer…",
"rateCommentHint": "Ton commentaire est partagé uniquement avec ton salon.",
"rateLater": "Plus tard"
```

- [ ] **Step 2: Enrichir le DTO front**

```typescript
export interface RateVisitDialogData {
  visitId: number;
  careName: string | null;
  salonName: string | null;
  visitDate: string | null;  // formatée client-side ou ISO ?
}
```

- [ ] **Step 3: Refondre le composant complet**

Replace template avec `<modal-form size="m" ...>`, care-card en haut, étoiles 32px en rose Pretty Face, label dynamique selon `score()`. Voir mockup `m10-rate-visit.html`.

```typescript
// extrait clé
readonly score = signal(0);
readonly scoreLabel = computed(() => {
  return this.i18n.translate(`evolution.rateLabel${this.score()}`);
});
```

- [ ] **Step 4: Mettre à jour le caller**

Dans `client-evolution.component.ts` méthode `openRateDialog`, passer `salonName` et `visitDate` :

```typescript
const data: RateVisitDialogData = {
  visitId: visit.id,
  careName: visit.careName,
  salonName: visit.salonName,  // déjà dans VisitRecordResponse
  visitDate: this.formatHumanDate(visit.appointmentDate),
};
```

Si `salonName` n'est pas dans `VisitRecordResponse`, l'ajouter côté backend (champ déjà disponible via la jointure).

- [ ] **Step 5: Vérifications + commit**

```bash
npx tsc --noEmit -p tsconfig.app.json
git add frontend/src/app/pages/client-evolution/ frontend/public/i18n/
git commit -m "feat(rate-visit): migrate to PC grammar (M, rose stars 32px, dynamic label, enriched care-card)"
```

---

### Task 22 : Migrer `create-care` (M, 2 onglets, limite 5 images)

**Files :**
- Modify : `frontend/src/app/features/cares/modals/create/create-care.component.{ts,html,scss}`
- Modify : `frontend/src/app/shared/uis/image-manager/image-manager.component.ts` (limite 5)
- Modify : `frontend/public/i18n/{fr,en}.json`

C'est la modale la plus dense du bloc M. Suivre le mockup `m7-create-care.html` et `m7b-create-care-limit.html`.

- [ ] **Step 1: i18n — clés contextuelles**

```json
"newCare": "Nouveau soin",
"createCareBtn": "Créer le soin",
"saveCareBtn": "Enregistrer",
"newSubtitle": "Renseigne les informations qui apparaîtront sur la fiche client.",
"tabInfo": "Informations",
"tabImages": "Images",
"imagesEmptyTitle": "Aucune image pour l'instant",
"imagesEmptyDesc": "Ajoute jusqu'à 5 photos pour mettre ton soin en valeur sur la vitrine.",
"imagesEmptyAdd": "+ Ajouter une première image",
"imagesAdd": "+ Ajouter",
"imagesHelper": "Glisse pour réorganiser. La 1ʳᵉ image sert de couverture sur la vitrine.",
"imagesHeading": "Photos du soin",
"imagesLimitReached": "Limite atteinte (5/5). Supprime une image pour pouvoir en ajouter une nouvelle.",
"imageBadgeCover": "Couverture"
```

- [ ] **Step 2: Refondre le template**

Adapter `create-care.component.html` pour wrapper `<modal-form size="m" ...>` + `<modal-tabs>` + body conditionnel. Onglet Informations = field-rows natifs (5 lignes : nom, description, prix+durée pair, catégorie, statut). Onglet Images = grille 4 cols + slot "+", reproduire les classes du mockup.

- [ ] **Step 3: Limite 5 dans `image-manager.component`**

Localiser `ImageManagerComponent` :

```bash
find /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src -name "image-manager.component.ts"
```

Open et modifier la méthode `addImage` (ou équivalent) pour bloquer si `images().length >= 5`. Désactiver le bouton "+" et afficher un bandeau d'info quand limite atteinte. Tester :

```typescript
// dans image-manager.component.spec.ts (créer si absent)
it('refuses adding 6th image', () => {
  // setup 5 images, attempt to add a 6th, expect rejection
});
```

- [ ] **Step 4: Vérifications + commit**

```bash
npx tsc --noEmit -p tsconfig.app.json
npm test -- --include='**/create-care.component.spec.ts' --include='**/image-manager.component.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -15
git add frontend/src/app/features/cares/modals/create/ frontend/src/app/shared/uis/image-manager/ frontend/public/i18n/
git commit -m "feat(create-care): migrate to PC grammar (M, 2 tabs, native form, max 5 images)"
```

---

### Task 23 : Migrer `create-user` (M, callout invitation)

**Files :**
- Modify : `frontend/src/app/features/users/modals/create/create-user.component.ts`
- Modify : `frontend/public/i18n/{fr,en}.json`

- [ ] **Step 1: i18n**

```json
"users": {
  ...,
  "newSubtitle": "L'utilisateur recevra un email pour définir son mot de passe et activer son compte.",
  "inviteBtn": "Inviter",
  "saveBtn": "Enregistrer",
  "inviteCallout": "Un <strong>email d'invitation</strong> sera envoyé à cette adresse. L'utilisateur définit lui-même son mot de passe.",
  "emailHint": "Sera utilisé comme identifiant de connexion."
}
```

(Pas de promesse "24h" — la spec dit qu'on ne sait pas si c'est implémenté.)

- [ ] **Step 2: Refondre le composant**

Template :

```html
<modal-form
  size="m"
  breadcrumbParent="Administration / Utilisateurs"
  [title]="title()"
  [subtitle]="subtitle()"
  [saveLabel]="saveLabel()"
  [saveDisabled]="form.invalid"
  [kbdHint]="'modalForm.kbdSave' | transloco"
  (save)="onSave()"
  (cancel)="onCancel()">

  <form [formGroup]="form">
    <div class="field-row">
      <span class="field-label">Nom complet <span class="req">*</span></span>
      <div><input class="field-input" formControlName="name" placeholder="Ex. Marie Dupont" /></div>
    </div>
    <div class="field-row last">
      <span class="field-label">Email <span class="req">*</span></span>
      <div>
        <input class="field-input" type="email" formControlName="email" placeholder="exemple@email.com" />
        <div class="field-hint">{{ 'users.emailHint' | transloco }}</div>
      </div>
    </div>
  </form>

  @if (!isEditMode) {
    <div class="invite-callout">
      <span class="ico">✉</span>
      <span [innerHTML]="'users.inviteCallout' | transloco"></span>
    </div>
  }
</modal-form>
```

Le mode édition (titre `'Modifier ' + user.name`, sous-titre meta — TODO post-back) reste à implémenter quand `onEditUser` sera câblé. Pour l'instant, exposer juste `data.user?` optionnel et adapter `isEditMode`.

- [ ] **Step 3: Vérifications + commit**

```bash
npx tsc --noEmit -p tsconfig.app.json
git add frontend/src/app/features/users/ frontend/public/i18n/
git commit -m "feat(create-user): migrate to PC grammar (M, native form, invite callout)"
```

---

### Task 24 : Migrer `create-employee` (M, multi-picker cares)

**Files :**
- Modify : `frontend/src/app/features/employees/modals/create-employee/create-employee.component.{ts,html,scss}`
- Modify : `frontend/public/i18n/{fr,en}.json`

- [ ] **Step 1: i18n**

```json
"newPraticienne": "Nouvelle praticienne",
"createBtn": "Créer la praticienne",
"newSubtitle": "Crée le compte et assigne les soins qu'elle peut effectuer. Tu pourras tout modifier plus tard.",
"passwordHint": "8 caractères minimum. Tu peux le partager à {{name}} par message ; elle pourra le changer ensuite.",
"caresHint": "{{name}} apparaîtra comme praticienne dans le sélecteur lors d'une réservation pour ces soins."
```

- [ ] **Step 2: Refondre `create-employee.component.html`**

Wrapper `<modal-form size="m" ...>` avec :
- field-row Nom
- field-row Email + Téléphone (pair-cols)
- field-row Mot de passe (avec hint dynamique utilisant `form.value.name`)
- field-row Soins effectués → `<app-cares-multi-picker>` directement

Voir le mockup `m12-create-employee.html` pour le détail visuel.

- [ ] **Step 3: Refondre `create-employee.component.ts`**

Garder la structure existante (FormBuilder, validators), enlever les imports Material non nécessaires, importer `ModalForm` et `CaresMultiPicker`. Convertir `selectedCareIds: Set<number>` en `signal<Set<number>>(new Set())` ou garder en property (avec event handler). Mapper les `cares: Care[]` → `PickerCare[]` avant de passer au composant.

- [ ] **Step 4: Vérifications + commit**

```bash
npx tsc --noEmit -p tsconfig.app.json
git add frontend/src/app/features/employees/modals/create-employee/ frontend/public/i18n/
git commit -m "feat(create-employee): migrate to PC grammar (M, native form, cares-multi-picker)"
```

---

### Task 25 : Migrer `create-post` (M, 3 types + soin associé)

**Files :**
- Modify : `frontend/src/app/features/posts/create-post-modal/create-post-modal.component.ts`
- Modify : `frontend/src/app/features/posts/posts.service.ts` (ajouter `careId`)
- Modify : `frontend/public/i18n/{fr,en}.json`
- Modify : `backend/src/main/java/com/prettyface/app/posts/web/PostController.java` (ou DTOs Create*Request) — accepter `careId` optionnel

- [ ] **Step 1: Backend — accepter `careId` dans les 3 endpoints (BA / Photo / Carousel)**

Ajouter `@RequestParam(required = false) Long careId` (ou via JSON body si `multipart/form-data` complexe). Persister `careId` sur l'entité `Post`.

Test :

```java
@Test
void createPhoto_withCareId_persistsAssociation() {
  PostResponse resp = postService.createPhoto(file, "caption", 42L);
  assertThat(resp.careId()).isEqualTo(42L);
}
```

```bash
cd backend && mvn test -Dtest=PostServiceTests
```

- [ ] **Step 2: Frontend — service & form**

Ajouter `careId` au form (signal ou FormControl). Quand on construit le `FormData`, ajouter `if (careId) fd.append('careId', String(careId));`.

Ajouter un select dans le template avec la liste des soins disponibles (depuis `CaresStore` ou via un input).

- [ ] **Step 3: Refondre le template complet**

Wrapper `<modal-form size="m" ...>`. Suivre le mockup `m13-create-post.html` :
- 3 chips de type (Avant/Après · Photo · Carrousel)
- Drop-zones dynamiques selon le type
- Caption avec compteur 0/500
- Select "Soin associé" en bas

- [ ] **Step 4: Vérifications + commit**

```bash
npx tsc --noEmit -p tsconfig.app.json
cd backend && mvn test -Dtest=PostServiceTests
git add backend/ frontend/src/app/features/posts/ frontend/public/i18n/
git commit -m "feat(create-post): migrate to PC grammar (M, 3 types redesigned), add careId association"
```

---

### Task 26 : Migrer `publish-missing-dialog` (M, checklist enrichie)

**Files :**
- Modify : `frontend/src/app/pages/pro/publish-missing-dialog/publish-missing-dialog.component.{ts,html,scss}`
- Modify : `backend/src/main/java/com/prettyface/app/onboarding/web/dto/PublishStatusResponse.java` (ou équivalent)
- Modify : `backend/src/main/java/com/prettyface/app/onboarding/app/OnboardingService.java`
- Modify : `frontend/src/app/pages/pro/pro-dashboard.component.ts` (caller)

- [ ] **Step 1: Backend — enrichir DTO avec checklist[]**

Localiser le service qui répond à "tentative de publication" et qui retourne actuellement `missing[]`. L'enrichir pour retourner :

```java
public record PublishStatusResponse(
    List<ChecklistItem> checklist
) {
    public record ChecklistItem(String key, String status) {} // status: "done" | "missing"
}
```

Le calcul des items "done" se base sur les mêmes vérifications que les "missing", mais en mode inverse (le critère est rempli).

Tests :

```java
@Test
void publishStatus_returnsAllChecklistItems() {
  PublishStatusResponse status = service.publishStatus(salonId);
  assertThat(status.checklist()).extracting("key")
      .contains("name", "hasContact", "hasLogo", "hasCategory", "hasActiveCare", "hasOpeningHours");
}
```

- [ ] **Step 2: Frontend — adapter le DTO**

```typescript
export interface PublishMissingDialogData {
  checklist: { key: string; status: 'done' | 'missing' }[];
}
```

- [ ] **Step 3: Refondre le template**

Voir mockup `m14-publish-missing.html`. Items avec icône `✓` (rose plein) si done, `!` (rose contour) si missing. Items done en barré opacité 0.65. CTA "Aller →" caché sur done. Barre de progression en haut.

- [ ] **Step 4: Mettre à jour caller**

```typescript
// pro-dashboard.component.ts — au moment d'ouvrir la modale
const dialogRef = this.dialog.open(PublishMissingDialogComponent, bottomSheetConfig('m', {
  data: { checklist: response.checklist },
}));
```

- [ ] **Step 5: Vérifications + commit**

```bash
npx tsc --noEmit -p tsconfig.app.json
cd backend && mvn test -Dtest=OnboardingServiceTests
git add backend/ frontend/src/app/pages/pro/ frontend/public/i18n/
git commit -m "feat(publish-missing): migrate to PC grammar (M, complete checklist + progress bar), enrich back DTO"
```

---

### Task 27 : Migrer `booking-stepper` (L, workflow 3 étapes)

**Files :**
- Modify : `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts`
- Modify : `frontend/src/app/features/bookings/components/step-{care,datetime,client}/*.component.ts` (les 3 sous-composants existent — refonte interne)
- Modify : `frontend/src/app/features/bookings/bookings.component.ts` (caller)
- Modify : `frontend/public/i18n/{fr,en}.json`

C'est la plus grosse refonte de la phase 2. Voir `m15-booking-stepper.html`.

- [ ] **Step 1: Refondre `booking-stepper.component.ts`** pour wrapper `<modal-form size="l" withAside="true" ...>` avec slot stepper et slot aside

```html
<modal-form
  size="l"
  withAside="true"
  breadcrumbParent="Réservations / Nouvelle"
  title="Nouvelle réservation"
  subtitle="Choisis le soin, l'horaire et la cliente. Tu pourras ajuster avant de confirmer."
  [backLabel]="currentStep() > 1 ? ('modalForm.back' | transloco) : ''"
  [saveLabel]="currentStep() === 3 ? 'Confirmer la réservation' : 'Continuer'"
  [saveDisabled]="!stepValid()"
  (back)="goBack()"
  (save)="goNext()"
  (cancel)="dialogRef.close()">

  <modal-stepper slot="stepper" [steps]="['Soin','Horaire','Cliente']" [current]="currentStep()" />

  <div slot="aside">
    <h4>Récap</h4>
    <!-- recap-cards (conditionnels selon ce qui est sélectionné) -->
  </div>

  @switch (currentStep()) {
    @case (1) { <app-step-care (careSelected)="onCareSelected($event)" /> }
    @case (2) { <app-step-datetime [careId]="selectedCareId()" (datetimeSelected)="onDatetimeSelected($event)" /> }
    @case (3) { <app-step-client (clientSelected)="onClientSelected($event)" /> }
  }
</modal-form>
```

Convertir le `currentStep` en `signal`, ajouter `stepValid` computed, `goBack` qui décrémente, `goNext` qui incrémente OU finalise.

- [ ] **Step 2: Refondre les 3 sous-composants steps**

Chacun garde sa logique métier mais adopte la grammaire native (pas de Material) :
- `step-care` : barre de recherche + liste de `<app-care-row>`
- `step-datetime` : sélecteur praticienne (`<app-employee-picker>`) + 7 jours liste + `<app-mini-calendar>` ou grille 7 jours custom + grille de slots
- `step-client` : `<modal-tabs>` "Cliente existante" / "Nouvelle cliente" → liste search + form

- [ ] **Step 3: Caller**

`bookings.component.ts:51` : `bottomSheetConfig('l', { maxHeight: '90vh', ... })`.

- [ ] **Step 4: Vérifications + commit**

```bash
npx tsc --noEmit -p tsconfig.app.json
git add frontend/src/app/features/bookings/ frontend/public/i18n/
git commit -m "feat(booking-stepper): migrate to PC grammar (L, 2-col aside+main, modal-stepper, native steps)"
```

---

### Task 28 : Migrer `employee-detail` (M, identité éditable)

**Files :**
- Modify : `frontend/src/app/features/employees/modals/employee-detail/employee-detail.component.{ts,html,scss}`
- Modify : `frontend/src/app/features/employees/employees.component.ts` (caller)
- Modify : `backend/.../EmployeeResponse.java` (ajouter `createdAt + bookingsCount`)
- Modify : `backend/.../EmployeeService.java` (peupler les nouveaux champs)
- Modify : `frontend/public/i18n/{fr,en}.json`

- [ ] **Step 1: Backend — enrichir EmployeeResponse**

Ajouter `Instant createdAt` et `long bookingsCount` (count des bookings rattachés à cet employé). Test JPA pour vérifier que ça compte correctement.

- [ ] **Step 2: Frontend — Employee model**

```typescript
export interface Employee {
  ...,
  createdAt: string;  // ISO
  bookingsCount: number;
}
```

- [ ] **Step 3: Refondre le template**

Voir `m16b-employee-detail-editable.html`. Wrapper `<modal-form size="m" ...>`, bandeau identité avec avatar + meta sociale ("Inscrite il y a 8 mois · 247 RDV effectués"), 3 sections labellées (IDENTITÉ, STATUT, SOINS EFFECTUÉS), `<app-cares-multi-picker>`, footer custom avec "Supprimer Léa" en `slot=footer-left`.

```html
<modal-form ...>
  <button slot="footer-left" class="btn-danger-link" (click)="confirmDelete()">
    🗑 Supprimer {{ employee.name }}
  </button>

  <!-- bandeau identité + sections -->
</modal-form>
```

Identité passe en field-row éditable (nom + email + téléphone). Form avec validators.

- [ ] **Step 4: Bouton Supprimer ouvre une `ConfirmDialogComponent`**

```typescript
confirmDelete() {
  this.confirmService.open({
    breadcrumbParent: 'Équipe / Praticiennes',
    title: `Supprimer ${this.employee.name} ?`,
    body: 'Cette action retirera la praticienne de ton équipe.',
    action: 'Supprimer',
    dangerous: true,
  }).subscribe(confirmed => {
    if (confirmed) this.dialogRef.close({ delete: true });
  });
}
```

- [ ] **Step 5: Vérifications + commit**

```bash
npx tsc --noEmit -p tsconfig.app.json
cd backend && mvn test
git add backend/ frontend/src/app/features/employees/modals/employee-detail/ frontend/public/i18n/
git commit -m "feat(employee-detail): migrate to PC grammar (M, editable identity, sections, delete in footer-left)"
```

---

### Task 29 : Migrer `booking-dialog` vitrine (L, sans stepper)

**Files :**
- Modify : `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.{ts,html,scss}`
- Modify : `frontend/public/i18n/{fr,en}.json`

C'est la modale **la plus exposée** (vitrine publique). Voir `m17-booking-dialog.html` pour les 3 états (sélection / vide / succès).

- [ ] **Step 1: i18n — clés (titre, footer, success)**

```json
"booking": {
  ...,
  "modalTitle": "Réserver chez <em>{{salon}}</em>",
  "modalSubtitle": "Choisis ta date et ton horaire — paiement sur place après le soin.",
  "withWho": "Avec qui ?",
  "anyEmployee": "Indifférent",
  "emptySlots": "Choisis une date dans le calendrier pour voir les créneaux disponibles.",
  "totalLabel": "Total",
  "totalSuffix": "— payé sur place",
  "ctaReserve": "Réserver {{label}}",
  "ctaPickDate": "Choisis une date",
  "successTitle": "Ta réservation est confirmée !",
  "successBody": "On t'a envoyé un récap par email.",
  "successConditions": "Annulation/modification selon les conditions du salon.",
  "successViewBookings": "Voir mes RDV",
  "successOk": "C'est parfait !"
}
```

- [ ] **Step 2: Refondre le template**

Wrapper `<modal-form size="l" withAside="true" ...>`. Aside = care-card (avec image réelle si dispo, sinon dégradé) + `<app-employee-picker>`. Main = `<app-mini-calendar>` + grille de slots. Footer custom avec "Total X € — payé sur place" à gauche en `slot=footer-left`.

État succès : alterner avec un signal `bookingSuccess()` qui change le contenu du body et cache le footer normal.

- [ ] **Step 3: Vérifications + commit**

```bash
npx tsc --noEmit -p tsconfig.app.json
git add frontend/src/app/pages/salon/booking-dialog/ frontend/public/i18n/
git commit -m "feat(booking-dialog vitrine): migrate to PC grammar (L, mini-calendar, employee-picker, success state)"
```

---

## Phase 3 — Cleanup

### Task 30 : Supprimer le code orphelin `create-booking.component.ts`

**Files :**
- Delete : `frontend/src/app/features/bookings/modals/create/create-booking.component.ts` et fichiers associés (`.html`, `.scss`, `.spec.ts` s'ils existent)

- [ ] **Step 1: Vérifier qu'aucun caller n'existe**

```bash
grep -rn "CreateBookingComponent\b" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src --include="*.ts" | grep -v "create-booking"
```

Expected: aucun résultat.

- [ ] **Step 2: Supprimer le dossier**

```bash
rm -rf /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/features/bookings/modals/create/
```

- [ ] **Step 3: Vérifier que ça compile**

```bash
npx tsc --noEmit -p tsconfig.app.json 2>&1 | tail -10
```

Expected: zéro erreur.

- [ ] **Step 4: Mettre à jour la mémoire**

Open `~/.claude/projects/-Users-Gustavo-alves-Documents-personal-portfolio/memory/project_orphan_create_booking.md` et le supprimer (le fichier existant est obsolète maintenant que le code est supprimé) :

```bash
rm /Users/Gustavo.alves/.claude/projects/-Users-Gustavo-alves-Documents-personal-portfolio/memory/project_orphan_create_booking.md
```

Et retirer la ligne correspondante dans `MEMORY.md`.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/bookings/
git commit -m "chore: remove orphan create-booking component (legacy, replaced by booking-stepper)"
```

---

### Task 31 : Vérification e2e finale

**Files :** Aucun — vérification manuelle complète.

- [ ] **Step 1: Démarrer environnement complet**

```bash
docker compose --profile dev up -d --force-recreate frontend-dev
cd backend && mvn spring-boot:run
```

- [ ] **Step 2: Parcours pro PC**

Ouvrir `http://localhost:4300` → se connecter en tant que pro. Pour chaque page :
- `/pro/cares` : ouvrir create / edit / delete soin → vérifier les 3 modales
- `/pro/employees` : ouvrir create / edit / delete praticienne
- `/pro/categories` : ouvrir create / edit / delete catégorie (avec/sans soins associés)
- `/pro/bookings` : nouvelle réservation (booking-stepper)
- `/pro/dashboard` : tenter publication vitrine sans tout remplir → publish-missing
- `/pro/dashboard` : dépublier salon → confirm-dialog

Vérifier la cohérence visuelle (filet rosé partout, breadcrumb cohérent, footer harmonisé).

- [ ] **Step 3: Parcours client/visiteur PC**

Ouvrir `/salon/<slug>` (visiteur anonyme) → cliquer "Réserver" sur un soin → choisir date+heure → cliquer "Réserver" → modale auth → "Plus tard" → vérifier que la sélection est conservée → s'authentifier → confirmer la réservation → vérifier l'écran succès.

- [ ] **Step 4: Parcours mobile (Chrome DevTools 375px)**

Reproduire les mêmes parcours en mobile. Vérifier que le bottom-sheet se déploie correctement pour CHAQUE modale, qu'aucune ne casse en grammaire PC.

- [ ] **Step 5: Run all tests**

```bash
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless 2>&1 | tail -20
cd backend && mvn test
```

Expected: tous PASS.

- [ ] **Step 6: PR finale**

```bash
git push -u origin feat/modales-pc-redesign
gh pr create --title "feat: refonte des modales PC + tablette (>=768px)" --body "$(cat <<'EOF'
## Summary
- Refonte UI des 18 modales du produit pour PC + tablette (>=768px)
- Mobile bottom-sheet inchangé
- Nouvelle grammaire commune via wrapper `<modal-form>` enrichi (filet rosé, breadcrumb, titre serif Cormorant Garamond, footer harmonisé)
- 3 tailles S/M/L (480/640/860px)
- 6 composants annexes créés (modal-stepper, modal-tabs, app-care-row, app-employee-picker, app-cares-multi-picker, app-mini-calendar)
- DTOs back enrichis (LeaveResponse, EmployeeResponse, PublishStatusResponse, etc.)
- Code orphelin supprimé (create-booking)

## Spec
docs/superpowers/specs/2026-05-09-modales-pc-redesign-design.md

## Test plan
- [ ] Parcours pro PC : create/edit/delete cares, employees, categories
- [ ] Parcours pro PC : booking-stepper (3 étapes)
- [ ] Parcours pro PC : publish-missing checklist
- [ ] Parcours visiteur vitrine : réserver un soin → auth → confirmation
- [ ] Mobile : bottom-sheet pour CHAQUE modale (zéro régression)
- [ ] i18n : vérifier fr + en sur toutes les modales
- [ ] Tests unitaires : npm test (front) + mvn test (back)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review

J'ai relu le plan ; voici les corrections inline que j'ai appliquées :

1. **Spec coverage** : couvre les 18 modales + le wrapper. Cleanup orphelin (Task 30). DTOs back enrichis dans les tâches concernées (19, 20, 21, 25, 26, 28). i18n abordée dans Task 11 (clés communes) puis dans chaque task métier. Mobile vérifié à chaque task et en task 31.

2. **Placeholder scan** : aucun "TBD"/"TODO". Quelques places mentionnent "voir le mockup `mXX.html`" pour le détail visuel — c'est volontaire (les mockups sont la source de vérité visuelle, le plan reste exécutable sans tout dupliquer en HTML).

3. **Type consistency** :
   - `ModalFormSize` (Task 2) cohérent avec `ModalSize` dans `bottom-sheet.config` (Task 4) — j'ai utilisé deux noms parce que les deux fichiers vivent indépendamment, mais `ModalSize` (`'s'|'m'|'l'`) est le même type structurellement. À l'implémentation : on peut soit `import { ModalFormSize } from '...'` dans le helper, soit garder dupliqué — peu importe.
   - `<modal-stepper>` exporte `stepClick` (output) ; consommé dans booking-stepper Task 27 — cohérent.
   - `EmployeePickerDto` (Task 9) : nom `name` uniquement ; aligné avec `EmployeeSlim` du modèle existant.
   - `PickerCare` (Task 7) vs `Care` réel (avec `categoryName: string` qui peut être absent) : à mapper côté caller.
   - `data.salonName` dans auth-modal (Task 17) : à récupérer via le store ou le service. J'ai noté la recherche manuelle requise.

4. **Bug détecté et corrigé inline** : Task 2 step 5 mentionnait `hasAside` field puis l'a remplacé par `withAside` input — j'ai laissé l'évolution dans le step, c'est une correction explicite vs la première version.

Le plan est exécutable tel quel. Si une étape s'avère plus complexe à l'implémentation (ex. la recherche du `salonName` dans booking-dialog Task 17), l'engineer peut s'arrêter, demander clarification, et continuer.

Plan complet et sauvegardé.
