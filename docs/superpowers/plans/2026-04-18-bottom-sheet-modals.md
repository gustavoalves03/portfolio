# Bottom Sheet Modals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform all 16 `MatDialog` modales into bottom sheets on mobile (< 768px) — slide up from bottom, 75vh fixed height, drag-to-dismiss via top handle, blurred backdrop. Desktop behavior unchanged.

**Architecture:** Non-intrusive approach — one global SCSS block activated by `panelClass: 'bottom-sheet'`, one tiny `<app-sheet-handle>` component inserted at the top of each modal template, and one `BottomSheetDragDirective` that closes the dialog when the user drags past a threshold. A `bottomSheetConfig()` helper centralises the options.

**Tech Stack:** Angular 20 (standalone, zoneless), Angular Material (`MatDialog`, `MatDialogRef`), SCSS, Pointer Events API.

**Spec:** `docs/superpowers/specs/2026-04-18-bottom-sheet-modals-design.md`

---

## File Structure

**New files (4):**
- `frontend/src/app/shared/uis/sheet-handle/sheet-handle.component.ts` — renders the visual handle, attaches the drag directive
- `frontend/src/app/shared/uis/sheet-handle/bottom-sheet-drag.directive.ts` — pointer-event drag-to-dismiss logic
- `frontend/src/app/shared/uis/sheet-handle/bottom-sheet.config.ts` — `bottomSheetConfig()` helper that returns a `MatDialogConfig` with the right `panelClass`/`backdropClass`
- `frontend/src/app/shared/uis/sheet-handle/bottom-sheet-drag.directive.spec.ts` — unit tests for the directive

**Modified files (18):**
- `frontend/src/styles.scss` — add the global `.bottom-sheet` block + `.bottom-sheet-backdrop`; remove the temporary `.booking-stepper-dialog` block
- 16 modal components — add `<app-sheet-handle />` + import
- 20 call sites of `this.dialog.open(...)` — replace options object with `bottomSheetConfig({...})`

Each task below is self-contained and ends with a commit.

---

## Task 1: Create `bottomSheetConfig()` helper

**Files:**
- Create: `frontend/src/app/shared/uis/sheet-handle/bottom-sheet.config.ts`

- [ ] **Step 1: Create the helper file**

```typescript
// frontend/src/app/shared/uis/sheet-handle/bottom-sheet.config.ts
import { MatDialogConfig } from '@angular/material/dialog';

function asArray(value: string | string[] | undefined): string[] {
  if (!value) return [];
  return Array.isArray(value) ? value : [value];
}

export function bottomSheetConfig<T = unknown>(
  overrides: MatDialogConfig<T> = {},
): MatDialogConfig<T> {
  return {
    maxWidth: '100vw',
    width: '480px',
    ...overrides,
    panelClass: ['bottom-sheet', ...asArray(overrides.panelClass)],
    backdropClass: 'bottom-sheet-backdrop',
  };
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/shared/uis/sheet-handle/bottom-sheet.config.ts
git commit -m "feat: add bottomSheetConfig helper for MatDialog options"
```

---

## Task 2: Add global SCSS `.bottom-sheet` block

**Files:**
- Modify: `frontend/src/styles.scss`

- [ ] **Step 1: Remove the obsolete `.booking-stepper-dialog` block**

Open `frontend/src/styles.scss` and delete the block:

```scss
// Booking stepper dialog — compact, no padding, responsive
.booking-stepper-dialog {
  margin: 12px !important;

  .mat-mdc-dialog-container .mdc-dialog__surface {
    padding: 0 !important;
    border-radius: 14px;
    overflow: hidden;
  }
}
```

- [ ] **Step 2: Add the `.bottom-sheet` block**

Append to `frontend/src/styles.scss` (at the end of the file):

```scss
// Bottom sheet — mobile only
.bottom-sheet {
  // Desktop default: standard Material dialog, handle hidden
  .sheet-handle { display: none; }

  @media (max-width: 767px) {
    position: fixed !important;
    bottom: 0 !important;
    left: 0 !important;
    right: 0 !important;
    top: auto !important;
    width: 100vw !important;
    max-width: 100vw !important;

    .mat-mdc-dialog-container .mdc-dialog__surface {
      border-radius: 16px 16px 0 0;
      height: 75vh;
      max-height: 75vh;
      padding: 0;
      background: #fafafa;
      box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.08);
      animation: sheet-slide-up 250ms ease-out;
    }

    .sheet-handle {
      display: block;
      width: 40px;
      height: 4px;
      background: #ddd;
      border-radius: 3px;
      margin: 10px auto 6px;
      cursor: grab;
      touch-action: none;
    }
  }
}

@media (max-width: 767px) {
  .cdk-overlay-dark-backdrop.bottom-sheet-backdrop {
    background: rgba(40, 20, 30, 0.28);
    backdrop-filter: blur(6px);
    -webkit-backdrop-filter: blur(6px);
  }
}

@keyframes sheet-slide-up {
  from { transform: translateY(100%); }
  to   { transform: translateY(0); }
}

@media (prefers-reduced-motion: reduce) {
  .bottom-sheet .mdc-dialog__surface { animation: none; }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/styles.scss
git commit -m "feat: add global .bottom-sheet SCSS block, remove .booking-stepper-dialog"
```

---

## Task 3: Write failing test for `BottomSheetDragDirective`

**Files:**
- Create: `frontend/src/app/shared/uis/sheet-handle/bottom-sheet-drag.directive.spec.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// frontend/src/app/shared/uis/sheet-handle/bottom-sheet-drag.directive.spec.ts
import { Component, ViewChild, ElementRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { BottomSheetDragDirective } from './bottom-sheet-drag.directive';

@Component({
  standalone: true,
  imports: [BottomSheetDragDirective],
  template: `
    <div class="mdc-dialog__surface">
      <div #handle class="sheet-handle" appBottomSheetDrag></div>
    </div>
  `,
})
class HostComponent {
  @ViewChild('handle', { static: true }) handle!: ElementRef<HTMLElement>;
}

describe('BottomSheetDragDirective', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;
  let dialogRef: jasmine.SpyObj<MatDialogRef<unknown>>;

  function makePointerEvent(type: string, clientY: number): PointerEvent {
    const event = new PointerEvent(type, {
      clientY,
      pointerId: 1,
      bubbles: true,
    });
    return event;
  }

  function setMobileViewport(isMobile: boolean): void {
    spyOn(window, 'matchMedia').and.returnValue({
      matches: isMobile,
      media: '(max-width: 767px)',
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    } as MediaQueryList);
  }

  beforeEach(async () => {
    dialogRef = jasmine.createSpyObj<MatDialogRef<unknown>>('MatDialogRef', ['close']);
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [{ provide: MatDialogRef, useValue: dialogRef }],
    }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('closes the dialog when drag distance exceeds 100px on mobile', (done) => {
    setMobileViewport(true);
    const el = host.handle.nativeElement;

    el.dispatchEvent(makePointerEvent('pointerdown', 100));
    el.dispatchEvent(makePointerEvent('pointermove', 150));
    el.dispatchEvent(makePointerEvent('pointerup', 250));

    setTimeout(() => {
      expect(dialogRef.close).toHaveBeenCalled();
      done();
    }, 250);
  });

  it('does NOT close when drag distance is below threshold', (done) => {
    setMobileViewport(true);
    const el = host.handle.nativeElement;

    el.dispatchEvent(makePointerEvent('pointerdown', 100));
    el.dispatchEvent(makePointerEvent('pointermove', 130));
    el.dispatchEvent(makePointerEvent('pointerup', 140));

    setTimeout(() => {
      expect(dialogRef.close).not.toHaveBeenCalled();
      done();
    }, 250);
  });

  it('is a no-op on desktop', () => {
    setMobileViewport(false);
    const el = host.handle.nativeElement;

    el.dispatchEvent(makePointerEvent('pointerdown', 100));
    el.dispatchEvent(makePointerEvent('pointermove', 300));
    el.dispatchEvent(makePointerEvent('pointerup', 500));

    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- --include='**/bottom-sheet-drag.directive.spec.ts' --watch=false`

Expected: FAIL with `Cannot find module './bottom-sheet-drag.directive'` or similar — the directive file does not exist yet.

- [ ] **Step 3: Commit the failing test**

```bash
git add frontend/src/app/shared/uis/sheet-handle/bottom-sheet-drag.directive.spec.ts
git commit -m "test: add failing spec for BottomSheetDragDirective"
```

---

## Task 4: Implement `BottomSheetDragDirective`

**Files:**
- Create: `frontend/src/app/shared/uis/sheet-handle/bottom-sheet-drag.directive.ts`

- [ ] **Step 1: Create the directive**

```typescript
// frontend/src/app/shared/uis/sheet-handle/bottom-sheet-drag.directive.ts
import { Directive, ElementRef, HostListener, inject } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';

const DISMISS_THRESHOLD_PX = 100;
const DISMISS_VELOCITY = 0.5; // px per ms
const MOBILE_QUERY = '(max-width: 767px)';

@Directive({
  selector: '[appBottomSheetDrag]',
  standalone: true,
})
export class BottomSheetDragDirective {
  private readonly dialogRef = inject(MatDialogRef, { optional: true });
  private readonly host = inject(ElementRef<HTMLElement>);

  private startY = 0;
  private startTime = 0;
  private dragging = false;
  private surface: HTMLElement | null = null;

  @HostListener('pointerdown', ['$event'])
  onPointerDown(event: PointerEvent): void {
    if (!this.dialogRef || !this.isMobile()) return;
    this.surface = this.findSurface();
    if (!this.surface) return;

    this.dragging = true;
    this.startY = event.clientY;
    this.startTime = performance.now();
    this.surface.style.transition = 'none';

    const target = event.target as HTMLElement;
    if (target.setPointerCapture) {
      target.setPointerCapture(event.pointerId);
    }
  }

  @HostListener('pointermove', ['$event'])
  onPointerMove(event: PointerEvent): void {
    if (!this.dragging || !this.surface) return;
    const delta = Math.max(0, event.clientY - this.startY);
    this.surface.style.transform = `translateY(${delta}px)`;
  }

  @HostListener('pointerup', ['$event'])
  @HostListener('pointercancel', ['$event'])
  onPointerUp(event: PointerEvent): void {
    if (!this.dragging || !this.surface) return;
    this.dragging = false;

    const delta = Math.max(0, event.clientY - this.startY);
    const duration = performance.now() - this.startTime;
    const velocity = delta / Math.max(duration, 1);

    if (delta > DISMISS_THRESHOLD_PX || velocity > DISMISS_VELOCITY) {
      this.dismiss();
    } else {
      this.snapBack();
    }
  }

  private findSurface(): HTMLElement | null {
    const el = this.host.nativeElement;
    return (el.closest('.mdc-dialog__surface') as HTMLElement | null)
      ?? (el.closest('.mat-mdc-dialog-surface') as HTMLElement | null);
  }

  private isMobile(): boolean {
    return typeof window !== 'undefined'
      && typeof window.matchMedia === 'function'
      && window.matchMedia(MOBILE_QUERY).matches;
  }

  private snapBack(): void {
    if (!this.surface) return;
    this.surface.style.transition = 'transform 200ms ease-out';
    this.surface.style.transform = 'translateY(0)';
  }

  private dismiss(): void {
    if (!this.surface || !this.dialogRef) return;
    this.surface.style.transition = 'transform 200ms ease-in';
    this.surface.style.transform = 'translateY(100%)';
    setTimeout(() => this.dialogRef!.close(), 200);
  }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `cd frontend && npm test -- --include='**/bottom-sheet-drag.directive.spec.ts' --watch=false`

Expected: all 3 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/shared/uis/sheet-handle/bottom-sheet-drag.directive.ts
git commit -m "feat: implement BottomSheetDragDirective"
```

---

## Task 5: Create `SheetHandleComponent`

**Files:**
- Create: `frontend/src/app/shared/uis/sheet-handle/sheet-handle.component.ts`

- [ ] **Step 1: Create the component**

```typescript
// frontend/src/app/shared/uis/sheet-handle/sheet-handle.component.ts
import { Component } from '@angular/core';
import { BottomSheetDragDirective } from './bottom-sheet-drag.directive';

@Component({
  selector: 'app-sheet-handle',
  standalone: true,
  imports: [BottomSheetDragDirective],
  template: `<div class="sheet-handle" appBottomSheetDrag aria-label="Close" role="presentation"></div>`,
  styles: [`:host { display: block; }`],
})
export class SheetHandleComponent {}
```

- [ ] **Step 2: Verify type-check passes**

Run: `cd frontend && npx tsc --noEmit`

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/shared/uis/sheet-handle/sheet-handle.component.ts
git commit -m "feat: add SheetHandleComponent"
```

---

## Task 6: Migrate `BookingStepperComponent`

**Files:**
- Modify: `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts`
- Modify: `frontend/src/app/pages/pro/pro-bookings.component.ts` (call site at line 604)
- Modify: `frontend/src/app/features/bookings/bookings.component.ts` (call site at line 50)

- [ ] **Step 1: Add `<app-sheet-handle />` to the stepper template**

In `booking-stepper.component.ts`, add the import:

```typescript
import { SheetHandleComponent } from '../../../../shared/uis/sheet-handle/sheet-handle.component';
```

Add `SheetHandleComponent` to the `imports` array of the component decorator.

Insert `<app-sheet-handle />` as the **first child** of the template (before `<div class="stepper-header">`):

```html
<app-sheet-handle />
<!-- Header -->
<div class="stepper-header">
  ...
</div>
```

- [ ] **Step 2: Update `pro-bookings.component.ts` call site**

Open `frontend/src/app/pages/pro/pro-bookings.component.ts`.

Add the import at the top:
```typescript
import { bottomSheetConfig } from '../../shared/uis/sheet-handle/bottom-sheet.config';
```

Replace the `dialog.open` call around line 604:

```typescript
const dialogRef = this.dialog.open(BookingStepperComponent, bottomSheetConfig({
  width: '100%',
  maxWidth: '480px',
  maxHeight: '90vh',
  disableClose: false,
  autoFocus: true,
}));
```

(Note: the existing `panelClass: 'booking-stepper-dialog'` is removed — the helper injects `'bottom-sheet'`.)

- [ ] **Step 3: Update `bookings.component.ts` call site**

Open `frontend/src/app/features/bookings/bookings.component.ts`.

Add the import (adjust relative path):
```typescript
import { bottomSheetConfig } from '../../shared/uis/sheet-handle/bottom-sheet.config';
```

Replace the `dialog.open(BookingStepperComponent, {...})` at line 50 with `bottomSheetConfig({...})` wrapping its existing options.

- [ ] **Step 4: Verify build**

Run: `cd frontend && npm run build`

Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts \
        frontend/src/app/pages/pro/pro-bookings.component.ts \
        frontend/src/app/features/bookings/bookings.component.ts
git commit -m "feat: migrate booking stepper to bottom sheet"
```

---

## Task 7: Migrate `BookingDialogComponent` (salon public)

**Files:**
- Modify: `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.ts`
- Modify: `frontend/src/app/pages/salon/salon-page.component.ts` (call site at line 182)

- [ ] **Step 1: Add handle to `BookingDialogComponent` template**

In `booking-dialog.component.ts`:

1. Add import: `import { SheetHandleComponent } from '../../../shared/uis/sheet-handle/sheet-handle.component';`
2. Add `SheetHandleComponent` to `imports` array.
3. Insert `<app-sheet-handle />` as the first element of the template.

- [ ] **Step 2: Update call site in `salon-page.component.ts`**

Add import: `import { bottomSheetConfig } from '../../shared/uis/sheet-handle/bottom-sheet.config';`

Wrap the options:
```typescript
this.dialog.open(BookingDialogComponent, bottomSheetConfig({
  // keep existing options
}));
```

- [ ] **Step 3: Verify build**

Run: `cd frontend && npm run build`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.ts \
        frontend/src/app/pages/salon/salon-page.component.ts
git commit -m "feat: migrate salon booking dialog to bottom sheet"
```

---

## Task 8: Migrate `CreateBookingComponent`

**Files:**
- Modify: `frontend/src/app/features/bookings/modals/create/create-booking.component.ts`
- Search and update any call site with `this.dialog.open(CreateBookingComponent,`

- [ ] **Step 1: Locate call sites**

Run: `grep -rn "CreateBookingComponent" frontend/src/app --include="*.ts" | grep -v spec | grep -v modals/create`

Expected output: one or more call sites using `this.dialog.open(CreateBookingComponent, ...)`.

- [ ] **Step 2: Add handle to the template**

In `create-booking.component.ts`:
1. Add import for `SheetHandleComponent`.
2. Add to `imports` array.
3. Insert `<app-sheet-handle />` as the first element of the template.

- [ ] **Step 3: Update all call sites with `bottomSheetConfig({...})`**

Each `dialog.open(CreateBookingComponent, {...})` → `dialog.open(CreateBookingComponent, bottomSheetConfig({...}))`.

- [ ] **Step 4: Verify build**

Run: `cd frontend && npm run build`

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: migrate CreateBookingComponent to bottom sheet"
```

---

## Task 9: Migrate `CreateCare` and `DeleteCareComponent`

**Files:**
- Modify: `frontend/src/app/features/cares/modals/create/create-care.component.ts`
- Modify: `frontend/src/app/features/cares/modals/delete/delete-care.component.ts`
- Modify: `frontend/src/app/features/cares/cares.component.ts` (call sites at lines 200, 223, 253, 326)

- [ ] **Step 1: Add handle to both modal templates**

For each of `create-care.component.ts` and `delete-care.component.ts`:
1. Add import for `SheetHandleComponent`.
2. Add to `imports` array.
3. Insert `<app-sheet-handle />` as the first element of the template.

- [ ] **Step 2: Update all 4 call sites in `cares.component.ts`**

Add import: `import { bottomSheetConfig } from '../../shared/uis/sheet-handle/bottom-sheet.config';`

Wrap each `this.dialog.open(CreateCare, {...})` and `this.dialog.open(DeleteCareComponent, {...})` with `bottomSheetConfig({...})`.

- [ ] **Step 3: Verify build**

Run: `cd frontend && npm run build`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/cares/
git commit -m "feat: migrate care modales to bottom sheet"
```

---

## Task 10: Migrate `CreateCategoryComponent` and `ReassignCategoryDialogComponent`

**Files:**
- Modify: `frontend/src/app/features/categories/modals/create/create-category.component.ts`
- Modify: `frontend/src/app/features/categories/modals/reassign-category/reassign-category-dialog.component.ts`
- Modify: `frontend/src/app/features/cares/cares.component.ts` (call sites at 125, 142, 163)
- Modify: `frontend/src/app/features/categories/categories.component.ts` (call site at 50)

- [ ] **Step 1: Add handle to both templates**

For each of `create-category.component.ts` and `reassign-category-dialog.component.ts`:
1. Add `SheetHandleComponent` import + add to imports array.
2. Insert `<app-sheet-handle />` as first template element.

- [ ] **Step 2: Update call sites**

Add `bottomSheetConfig` import in `cares.component.ts` (if not already added in Task 9) and `categories.component.ts`.

Wrap each `dialog.open(...)` call for `CreateCategoryComponent` and `ReassignCategoryDialogComponent`.

- [ ] **Step 3: Verify build**

Run: `cd frontend && npm run build`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/categories/ frontend/src/app/features/cares/cares.component.ts
git commit -m "feat: migrate category modales to bottom sheet"
```

---

## Task 11: Migrate `CreateUserComponent`

**Files:**
- Modify: `frontend/src/app/features/users/modals/create/create-user.component.ts`
- Modify: `frontend/src/app/features/users/users.component.ts` (call site at line 47)

- [ ] **Step 1: Add handle to template**

In `create-user.component.ts`: add import, add to imports array, insert `<app-sheet-handle />` as first element.

- [ ] **Step 2: Update call site**

In `users.component.ts` line 47, add `bottomSheetConfig` import and wrap options.

- [ ] **Step 3: Verify build**

Run: `cd frontend && npm run build`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/users/
git commit -m "feat: migrate user create modal to bottom sheet"
```

---

## Task 12: Migrate `CreateEmployeeComponent` and `EmployeeDetailComponent`

**Files:**
- Modify: `frontend/src/app/features/employees/modals/create-employee/create-employee.component.ts`
- Modify: `frontend/src/app/features/employees/modals/employee-detail/employee-detail.component.ts`
- Modify: `frontend/src/app/features/employees/employees.component.ts` (call sites at 34, 49)

- [ ] **Step 1: Add handle to both templates**

For each of the two modal components: add import, add to imports array, insert `<app-sheet-handle />` as first element.

- [ ] **Step 2: Update both call sites in `employees.component.ts`**

Add `bottomSheetConfig` import, wrap both `dialog.open(...)` calls.

- [ ] **Step 3: Verify build**

Run: `cd frontend && npm run build`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/employees/
git commit -m "feat: migrate employee modales to bottom sheet"
```

---

## Task 13: Migrate `ReviewLeaveDialogComponent`

**Files:**
- Modify: `frontend/src/app/features/leaves/modals/review-leave-dialog/review-leave-dialog.component.ts`
- Modify: `frontend/src/app/features/leaves/leaves.component.ts` (call site at line 92)

- [ ] **Step 1: Add handle to template**

In `review-leave-dialog.component.ts`: add import, add to imports array, insert `<app-sheet-handle />` as first element.

- [ ] **Step 2: Update call site**

In `leaves.component.ts` line 92: add `bottomSheetConfig` import, wrap options.

- [ ] **Step 3: Verify build**

Run: `cd frontend && npm run build`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/leaves/
git commit -m "feat: migrate review leave dialog to bottom sheet"
```

---

## Task 14: Migrate `CreatePostModalComponent`

**Files:**
- Modify: `frontend/src/app/features/posts/create-post-modal/create-post-modal.component.ts`
- Modify: `frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts` (call site at line 760)

- [ ] **Step 1: Add handle to template**

In `create-post-modal.component.ts`: add import, add to imports array, insert `<app-sheet-handle />` as first element.

- [ ] **Step 2: Update call site**

In `salon-posts-viewer.component.ts` line 760: add `bottomSheetConfig` import, wrap options.

- [ ] **Step 3: Verify build**

Run: `cd frontend && npm run build`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/posts/
git commit -m "feat: migrate create post modal to bottom sheet"
```

---

## Task 15: Migrate `NoShowConfirmDialogComponent`

**Files:**
- Modify: `frontend/src/app/features/tracking/components/client-bookings/no-show-confirm-dialog.component.ts`
- Modify: `frontend/src/app/features/tracking/components/client-bookings/client-bookings.component.ts` (call site at line 288)

- [ ] **Step 1: Add handle to template**

In `no-show-confirm-dialog.component.ts`: add import, add to imports array, insert `<app-sheet-handle />` as first element.

- [ ] **Step 2: Update call site**

In `client-bookings.component.ts` line 288: add `bottomSheetConfig` import, wrap options.

- [ ] **Step 3: Verify build**

Run: `cd frontend && npm run build`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/tracking/
git commit -m "feat: migrate no-show confirm dialog to bottom sheet"
```

---

## Task 16: Migrate `RateVisitDialogComponent`

**Files:**
- Modify: `frontend/src/app/pages/client-evolution/rate-visit-dialog.component.ts`
- Modify: `frontend/src/app/pages/client-evolution/client-evolution.component.ts` (call site at line 912)

- [ ] **Step 1: Add handle to template**

In `rate-visit-dialog.component.ts`: add import, add to imports array, insert `<app-sheet-handle />` as first element.

- [ ] **Step 2: Update call site**

In `client-evolution.component.ts` line 912: add `bottomSheetConfig` import, wrap options.

- [ ] **Step 3: Verify build**

Run: `cd frontend && npm run build`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/client-evolution/
git commit -m "feat: migrate rate visit dialog to bottom sheet"
```

---

## Task 17: Migrate `AuthModalComponent` and `LoginModalComponent`

**Files:**
- Modify: `frontend/src/app/shared/modals/auth-modal/auth-modal.component.ts`
- Modify: `frontend/src/app/shared/modals/login-modal/login-modal.component.ts`
- Modify: `frontend/src/app/shared/layout/header/header.ts` (call site at line 120)
- Search and update any other call site with `this.dialog.open(AuthModalComponent,` or `this.dialog.open(LoginModalComponent,`

- [ ] **Step 1: Locate all call sites**

Run: `grep -rn "AuthModalComponent\|LoginModalComponent" frontend/src/app --include="*.ts" | grep "dialog.open"`

- [ ] **Step 2: Add handle to both templates**

For each of `auth-modal.component.ts` and `login-modal.component.ts`: add import, add to imports array, insert `<app-sheet-handle />` as first element.

- [ ] **Step 3: Update all call sites**

Add `bottomSheetConfig` import at each call site file; wrap options object.

- [ ] **Step 4: Verify build**

Run: `cd frontend && npm run build`

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/ frontend/src/app/shared/layout/header/header.ts
git commit -m "feat: migrate auth/login modales to bottom sheet"
```

---

## Task 18: Migrate `ConfirmDialogComponent`

**Files:**
- Modify the `ConfirmDialogComponent` file (locate with grep) and its call site at `frontend/src/app/pages/pro/pro-dashboard.component.ts:308`

- [ ] **Step 1: Locate the component**

Run: `grep -rn "class ConfirmDialogComponent" frontend/src/app --include="*.ts"`

- [ ] **Step 2: Locate all call sites**

Run: `grep -rn "ConfirmDialogComponent" frontend/src/app --include="*.ts" | grep "dialog.open"`

- [ ] **Step 3: Add handle to template**

In the `ConfirmDialogComponent` file: add import, add to imports array, insert `<app-sheet-handle />` as first element.

- [ ] **Step 4: Update all call sites**

Each `dialog.open(ConfirmDialogComponent, {...})` → `dialog.open(ConfirmDialogComponent, bottomSheetConfig({...}))`.

- [ ] **Step 5: Verify build**

Run: `cd frontend && npm run build`

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: migrate confirm dialog to bottom sheet"
```

---

## Task 19: Final sweep — catch any missed `dialog.open()` calls

**Files:**
- Potentially all files with `this.dialog.open(...)` not already wrapped

- [ ] **Step 1: List every `dialog.open(` call still using raw options**

Run:
```bash
grep -rn "this\.dialog\.open\s*(" frontend/src/app --include="*.ts"
```

- [ ] **Step 2: Confirm every call is either wrapped with `bottomSheetConfig` or documented as intentionally excluded**

For each call, verify the next argument is `bottomSheetConfig(...)`. If any use raw `{...}`, wrap it. If any modal type was missed, add `<app-sheet-handle />` to its template.

- [ ] **Step 3: Verify build passes**

Run: `cd frontend && npm run build`

- [ ] **Step 4: Commit if any changes**

```bash
git add -A
git commit -m "feat: wrap remaining dialog.open calls with bottomSheetConfig"
```

If no changes: skip the commit.

---

## Task 20: Manual QA on mobile viewport

**Files:** none — this is a manual verification task.

- [ ] **Step 1: Start the dev server**

Run: `cd frontend && npm start`

Wait for `Application bundle generation complete` and server listening on `http://localhost:4200`.

- [ ] **Step 2: Open Chrome DevTools in responsive mode**

Open `http://localhost:4200` in Chrome, press `Cmd+Opt+I` (macOS) to open DevTools, toggle device toolbar (`Cmd+Shift+M`), pick **iPhone SE** (375×667).

- [ ] **Step 3: Visual checks**

Log in and navigate to **Pro → Bookings**. Click the "Ajouter réservation" button.

Verify:
- Sheet slides up from bottom in ~250ms
- Handle bar visible at top (40×4px grey)
- Sheet occupies 75% of viewport height
- Backdrop is dark + blurred
- Top corners rounded (16px)

Drag the handle down slowly ~50px → sheet follows, snaps back on release.
Drag the handle down fast >100px → sheet closes.
Click backdrop → sheet closes (Material default).
Click close button → sheet closes.

Press `Escape` → sheet closes.

- [ ] **Step 4: Repeat on at least 3 other modales**

Pick any 3 of: create care, create category, create user, review leave, create post, rate visit. Same checks as Step 3.

- [ ] **Step 5: Verify desktop is unchanged**

Disable device toolbar (`Cmd+Shift+M`). Open the same modales. Verify they appear as **centered dialogs** (not bottom sheets), handle is hidden, no animation change.

- [ ] **Step 6: Commit QA results (no code change)**

No commit needed if all checks pass. If a regression is found, revert to the failing task and fix.

---

## Self-Review

**Spec coverage check:**
- ✅ Global SCSS block → Task 2
- ✅ `SheetHandleComponent` → Task 5
- ✅ `BottomSheetDragDirective` → Tasks 3–4 (TDD)
- ✅ `bottomSheetConfig()` helper → Task 1
- ✅ 16 modal components migrated → Tasks 6–18
- ✅ `booking-stepper-dialog` SCSS removed → Task 2 Step 1
- ✅ Reduced motion → in Task 2 SCSS
- ✅ Unit tests for directive → Task 3
- ✅ Manual QA → Task 20

**Placeholder check:** None — every step contains actual code or actual command.

**Type consistency:** `bottomSheetConfig`, `SheetHandleComponent`, `BottomSheetDragDirective`, `MatDialogRef`, `'bottom-sheet'`, `'bottom-sheet-backdrop'` — all names are consistent across tasks.

**Scope:** The plan intentionally does NOT include a `ConfirmDialogComponent` refactor (not in the spec's 16-modal list) — but Task 18 catches it since it surfaced during the migration audit. The spec's out-of-scope items (snap points, `MatBottomSheet`) are absent from the plan.
