# Unified Back-Button Navigation — Design Spec

**Date:** 2026-04-18
**Status:** Approved (pending user review)
**Scope:** Frontend only. Introduces a shared `BackButtonComponent` and a small
navigation-history tracker, then migrates the two pages that currently hardcode
their back destination.

## Goal

Make every in-app "back" action respect the user's real navigation history
(`location.back()` when there IS history within the app), with a per-page
fallback URL when the user arrived via a deep link and has no history to go
back to. Replace the two pages that currently hardcode a fixed back target with
a single reusable component.

## Decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Back behavior | Cascade: `location.back()` if internal history exists, else route to a per-page `fallbackUrl` |
| Scope | Fix the two buggy pages + introduce a reusable pattern; leave the shared header alone |
| Fallback source | Per-page, declared via `fallbackUrl` input on the component |
| History detection | `NavigationHistoryService` counts Router `NavigationEnd` events since app boot |

## Architecture

Three new artifacts, two small migrations. No changes to the shared `Header`.

### 1. `NavigationHistoryService`

Location: `frontend/src/app/core/navigation/navigation-history.service.ts`

```typescript
@Injectable({ providedIn: 'root' })
export class NavigationHistoryService {
  private readonly router = inject(Router);
  private count = 0;

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(() => this.count++);
  }

  hasInternalHistory(): boolean {
    return this.count > 1;
  }
}
```

**Semantics:**
- `count = 0` before the first navigation
- `count = 1` after the initial landing (deep-link or normal load)
- `count >= 2` after any further in-app navigation
- `hasInternalHistory()` returns `true` only when the user has navigated at
  least once within the app (i.e. `location.back()` has a valid target)

### 2. `BackButtonComponent`

Location: `frontend/src/app/shared/uis/back-button/back-button.component.ts`

```typescript
@Component({
  selector: 'app-back-button',
  standalone: true,
  imports: [MatIconModule, TranslocoPipe],
  template: `
    <button type="button" class="back-btn" (click)="onClick()">
      <mat-icon>arrow_back</mat-icon>
      @if (showLabel()) {
        <span>{{ 'common.back' | transloco }}</span>
      }
    </button>
  `,
  styles: [`
    .back-btn {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 6px 10px;
      background: transparent;
      border: none;
      color: #666;
      cursor: pointer;
      font-size: 13px;
    }
    .back-btn:hover { color: #c06; }
    .back-btn mat-icon { font-size: 20px; width: 20px; height: 20px; }
  `],
})
export class BackButtonComponent {
  private readonly location = inject(Location);
  private readonly router = inject(Router);
  private readonly history = inject(NavigationHistoryService);

  readonly fallbackUrl = input<string>('/');
  readonly showLabel = input<boolean>(true);

  onClick(): void {
    if (this.history.hasInternalHistory()) {
      this.location.back();
    } else {
      this.router.navigate([this.fallbackUrl()]);
    }
  }
}
```

**API examples:**

```html
<!-- Icon + label, fallback = '/' -->
<app-back-button />

<!-- Icon only -->
<app-back-button [showLabel]="false" />

<!-- Custom deep-link fallback -->
<app-back-button fallbackUrl="/pro/manage" />
```

### 3. i18n key

Add (or reuse if present) in `frontend/public/i18n/fr.json` and `en.json`:

- `common.back` → `"Retour"` / `"Back"`

Check first: if a key already exists with this value, use it and skip the
addition.

### 4. Migration — `ProBookingHistoryComponent`

File: `frontend/src/app/pages/pro/pro-booking-history/pro-booking-history.component.ts`

- Remove the inline `<button class="back" (click)="goBack()">...<mat-icon>arrow_back</mat-icon></button>` in the header.
- Remove the `goBack()` method and its `router.navigate(['/pro/manage'])` call.
- Remove the now-unused `router` injection if it was only used by `goBack`.
- Replace the header markup with `<app-back-button fallbackUrl="/pro/manage" [showLabel]="false" />` to preserve the current icon-only visual.
- Add `BackButtonComponent` to the component's `imports` array.

### 5. Migration — `EmployeeClientDetailComponent`

File: `frontend/src/app/pages/employee/employee-client-detail.component.ts`

- Remove the `<a routerLink="/employee/bookings">...<mat-icon>arrow_back</mat-icon>...</a>` block.
- Replace with `<app-back-button fallbackUrl="/employee/bookings" />`.
- Add `BackButtonComponent` to the component's `imports` array.
- Remove the `RouterLink` import + `imports`-array entry if no longer used.

## Testing

### Unit (Jasmine/Karma)

**`navigation-history.service.spec.ts`:**
- `hasInternalHistory()` is `false` before any navigation
- `hasInternalHistory()` is `false` after exactly one `NavigationEnd`
- `hasInternalHistory()` is `true` after two or more `NavigationEnd`s
- Multiple consumers see the same state (shared service)

**`back-button.component.spec.ts`:**
- Click with `hasInternalHistory() === true` calls `Location.back()` and does
  not navigate
- Click with `hasInternalHistory() === false` and `fallbackUrl="/pro/manage"`
  calls `router.navigate(['/pro/manage'])` and does not call `Location.back()`
- Click with no `fallbackUrl` input navigates to `'/'` as default
- Renders `mat-icon` always; renders the label only when `showLabel()` is true

### Manual QA

1. Deep-link to `/pro/settings/history` (paste URL in a fresh tab). Click
   back arrow. Expected: navigates to `/pro/manage`.
2. Start at `/pro/manage`, tap the "Historique" card, then click back.
   Expected: returns to `/pro/manage` via browser history.
3. Deep-link to `/employee/clients/:id`. Click back. Expected: navigates to
   `/employee/bookings`.
4. Start at `/employee/bookings`, open a client, then click back. Expected:
   returns to `/employee/bookings` via browser history.

## Out of scope

- No refactor of `shared/layout/header/header.ts` — it already calls
  `location.back()` correctly for the pages it covers
- No change to the `arrow_back` icons used inside carousels, calendars, or
  form steppers (those are in-component interactions, not page navigation)
- No migration of other pages beyond the two buggy ones
- No "breadcrumb" feature or route hierarchy config

## Rollout

Single PR. Order:

1. Add `NavigationHistoryService` + unit tests.
2. Add `BackButtonComponent` + unit tests.
3. Check / add `common.back` i18n key.
4. Migrate `ProBookingHistoryComponent`.
5. Migrate `EmployeeClientDetailComponent`.
6. Manual QA on all four flows above.
