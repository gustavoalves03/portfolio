# Bottom Sheet Modals — Design Spec

**Date:** 2026-04-18
**Status:** Approved (pending user review)
**Scope:** Frontend only (Angular 20 + Angular Material)

## Goal

Transform all modales of the Pretty Face app into **bottom sheets** on mobile
(viewport width < 768px): they slide up from the bottom, occupy 75% of the
viewport height, display a small drag-handle at the top, and can be dismissed
by swiping down. Desktop behavior (≥ 768px) remains unchanged — modales stay
centered as today.

## Decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Scope | Mobile only (`max-width: 767px`). Desktop unchanged. |
| Height | Fixed 75vh. |
| Handle | Visible top bar, enables drag-to-dismiss gesture. |
| Backdrop | Dark + blurred (`backdrop-filter: blur(6px)`). No tap-to-close change (Material default kept). |
| Animation | Standard slide-up, 250ms ease-out. |
| Visual style | Minimal flat: `16px` top corners, `#fafafa` background, grey handle `#ddd`. |

## Architecture

Three new artifacts, zero new library, no migration of the `MatDialog` API.

### 1. Global SCSS block

Added to `frontend/src/styles.scss`:

```scss
.bottom-sheet {
  .sheet-handle { display: none; } // hidden on desktop

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

The `panelClass: 'bottom-sheet'` on `MatDialog.open()` activates the styling.
The `backdropClass: 'bottom-sheet-backdrop'` activates the blurred backdrop.

### 2. `SheetHandleComponent`

Location: `frontend/src/app/shared/uis/sheet-handle/sheet-handle.component.ts`

```typescript
@Component({
  selector: 'app-sheet-handle',
  standalone: true,
  imports: [BottomSheetDragDirective],
  template: `<div class="sheet-handle" appBottomSheetDrag></div>`,
  styles: [`:host { display: block; }`],
})
export class SheetHandleComponent {}
```

The visual styling lives in the global `.bottom-sheet .sheet-handle` block —
this component only renders the element and attaches the drag directive.

### 3. `BottomSheetDragDirective`

Location: `frontend/src/app/shared/uis/sheet-handle/bottom-sheet-drag.directive.ts`

```typescript
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
  onPointerDown(e: PointerEvent): void { /* ... */ }

  @HostListener('pointermove', ['$event'])
  onPointerMove(e: PointerEvent): void { /* ... */ }

  @HostListener('pointerup', ['$event'])
  @HostListener('pointercancel', ['$event'])
  onPointerUp(e: PointerEvent): void { /* ... */ }
}
```

Behavior:
- Only active when `window.matchMedia('(max-width: 767px)').matches`
- Uses pointer events (unified touch/mouse/pen)
- Translates the `.mdc-dialog__surface` by `deltaY` during drag
- On release: if `deltaY > 100px` OR `velocity > 0.5 px/ms`, call
  `dialogRef.close()` after a 200ms slide-down animation; otherwise snap back
- SSR-safe: guards on `typeof window`
- Injects `MatDialogRef` as `optional: true`, so using the directive outside
  a dialog is a no-op instead of a runtime error

### 4. Helper config

Location: `frontend/src/app/shared/uis/sheet-handle/bottom-sheet.config.ts`

```typescript
export function bottomSheetConfig<T = unknown>(
  overrides: MatDialogConfig<T> = {}
): MatDialogConfig<T> {
  return {
    panelClass: ['bottom-sheet', ...asArray(overrides.panelClass)],
    backdropClass: 'bottom-sheet-backdrop',
    maxWidth: '100vw',
    width: '480px',
    ...overrides,
  };
}
```

Avoids repeating the `panelClass` / `backdropClass` pair at every call site.

## Migration

For each of the 17 existing modales:

1. Add `<app-sheet-handle />` as the first element in the component template.
2. Import `SheetHandleComponent` in the component's `imports` array.
3. At every call site (`this.dialog.open(X, {...})`), wrap the options in
   `bottomSheetConfig({...})`.

Affected files:

**Feature modales:**
- `features/bookings/components/booking-stepper/booking-stepper.component.ts`
- `features/bookings/modals/create/create-booking.component.ts`
- `features/cares/modals/create/create-care.component.ts`
- `features/cares/modals/delete/delete-care.component.ts`
- `features/categories/modals/create/create-category.component.ts`
- `features/categories/modals/reassign-category/reassign-category-dialog.component.ts`
- `features/users/modals/create/create-user.component.ts`
- `features/employees/modals/create-employee/create-employee.component.ts`
- `features/employees/modals/employee-detail/employee-detail.component.ts`
- `features/leaves/modals/review-leave-dialog/review-leave-dialog.component.ts`
- `features/posts/create-post-modal/create-post-modal.component.ts`
- `features/tracking/components/client-bookings/no-show-confirm-dialog.component.ts`

**Page-level dialogs:**
- `pages/salon/booking-dialog/booking-dialog.component.ts`
- `pages/client-evolution/rate-visit-dialog.component.ts`

**Shared modales:**
- `shared/modals/auth-modal/auth-modal.component.ts`
- `shared/modals/login-modal/login-modal.component.ts`

The existing `.booking-stepper-dialog` class in `styles.scss` (already added
during the stepper work) will be removed — the new global `.bottom-sheet`
supersedes it.

## Accessibility

- **Focus trap, Escape:** unchanged (native `MatDialog` behavior)
- **ARIA:** no role on the handle (it's a gesture surface, not a button);
  `aria-label="Close"` added to the handle element
- **Reduced motion:** `@media (prefers-reduced-motion: reduce)` disables the
  slide animation
- **Scroll containment:** `touch-action: none` on the handle prevents the
  page scroll from competing with the drag gesture

## Testing

### Unit (Jasmine/Karma)

- `BottomSheetDragDirective`:
  - Simulates `pointerdown` + `pointermove` + `pointerup`
  - Asserts `dialogRef.close()` called when `deltaY > 100px`
  - Asserts `dialogRef.close()` called when velocity > 0.5 px/ms
  - Asserts no close + snap-back when below threshold
  - Asserts no-op on desktop (viewport ≥ 768px)
- `SheetHandleComponent`:
  - Renders the `.sheet-handle` div
  - Directive is present on the rendered element

### Manual

Tested in Chrome DevTools responsive mode:
- iPhone SE (375×667): slide-up animation, drag-to-dismiss, blur backdrop,
  close button, backdrop click
- Desktop (1280×800): modale centered, handle hidden, no behavior change

## Out of scope

- No multi-snap points (half / full height) — fixed 75vh
- No replacement of `MatDialog` with `MatBottomSheet`
- No redesign of modale contents — only the container/wrapper behavior
- No new animation curves beyond what's specified

## Rollout

Single PR. Order of operations:

1. Add the 3 files (SCSS block, component, directive, helper).
2. Write unit tests.
3. Migrate modales in batches by feature folder (track progress visually).
4. Remove the temporary `.booking-stepper-dialog` SCSS override.
5. Manual QA on iPhone SE viewport.
