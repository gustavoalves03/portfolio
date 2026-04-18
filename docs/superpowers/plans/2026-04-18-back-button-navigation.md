# Unified Back-Button Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a `NavigationHistoryService` + a reusable `BackButtonComponent` that respects in-app navigation history (`location.back()`) and falls back to a per-page URL when no history exists (deep-link case), then migrate the two pages currently hardcoding their back target.

**Architecture:** Root-provided service tracks `NavigationEnd` events to know if internal history exists. A standalone component delegates clicks to either `Location.back()` or `router.navigate([fallbackUrl])` based on that state. No changes to the shared `Header` (already correct).

**Tech Stack:** Angular 20 (standalone, zoneless), Angular Router `NavigationEnd` events, Angular `Location` service, Jasmine/Karma.

**Spec:** `docs/superpowers/specs/2026-04-18-back-button-navigation-design.md`

---

## File Structure

**New files (4):**
- `frontend/src/app/core/navigation/navigation-history.service.ts`
- `frontend/src/app/core/navigation/navigation-history.service.spec.ts`
- `frontend/src/app/shared/uis/back-button/back-button.component.ts`
- `frontend/src/app/shared/uis/back-button/back-button.component.spec.ts`

**Modified files (2):**
- `frontend/src/app/pages/pro/pro-booking-history/pro-booking-history.component.ts`
- `frontend/src/app/pages/employee/employee-client-detail.component.ts`

**i18n:** `common.back` already exists in `fr.json` (line 21) and `en.json` — verified. No new keys needed.

Each task below is self-contained and ends with a commit.

---

## Task 1: `NavigationHistoryService` with failing tests

**Files:**
- Create: `frontend/src/app/core/navigation/navigation-history.service.spec.ts`
- Create: `frontend/src/app/core/navigation/navigation-history.service.ts`

- [ ] **Step 1: Write the failing tests**

```typescript
// frontend/src/app/core/navigation/navigation-history.service.spec.ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { NavigationHistoryService } from './navigation-history.service';

describe('NavigationHistoryService', () => {
  let events$: Subject<unknown>;
  let routerMock: { events: Subject<unknown> };

  beforeEach(() => {
    events$ = new Subject<unknown>();
    routerMock = { events: events$ };
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: Router, useValue: routerMock },
        NavigationHistoryService,
      ],
    });
  });

  it('reports no internal history before any navigation', () => {
    const svc = TestBed.inject(NavigationHistoryService);
    expect(svc.hasInternalHistory()).toBeFalse();
  });

  it('reports no internal history after exactly one NavigationEnd', () => {
    const svc = TestBed.inject(NavigationHistoryService);
    events$.next(new NavigationEnd(1, '/a', '/a'));
    expect(svc.hasInternalHistory()).toBeFalse();
  });

  it('reports internal history after two or more NavigationEnd events', () => {
    const svc = TestBed.inject(NavigationHistoryService);
    events$.next(new NavigationEnd(1, '/a', '/a'));
    events$.next(new NavigationEnd(2, '/b', '/b'));
    expect(svc.hasInternalHistory()).toBeTrue();
  });

  it('ignores non-NavigationEnd events', () => {
    const svc = TestBed.inject(NavigationHistoryService);
    events$.next({ type: 'random' });
    events$.next({ type: 'other' });
    expect(svc.hasInternalHistory()).toBeFalse();
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/navigation-history.service.spec.ts' --watch=false`

Expected: FAIL with `Cannot find module './navigation-history.service'`.

- [ ] **Step 3: Create the service**

```typescript
// frontend/src/app/core/navigation/navigation-history.service.ts
import { Injectable, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

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

- [ ] **Step 4: Run tests — expect 4/4 PASS**

Same command. Expected: 4/4 PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/navigation/
git commit -m "feat: add NavigationHistoryService to track in-app navigation"
```

---

## Task 2: `BackButtonComponent` with failing tests

**Files:**
- Create: `frontend/src/app/shared/uis/back-button/back-button.component.spec.ts`
- Create: `frontend/src/app/shared/uis/back-button/back-button.component.ts`

- [ ] **Step 1: Write the failing tests**

```typescript
// frontend/src/app/shared/uis/back-button/back-button.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { BackButtonComponent } from './back-button.component';
import { NavigationHistoryService } from '../../../core/navigation/navigation-history.service';

describe('BackButtonComponent', () => {
  let fixture: ComponentFixture<BackButtonComponent>;
  let location: jasmine.SpyObj<Location>;
  let router: jasmine.SpyObj<Router>;
  let history: jasmine.SpyObj<NavigationHistoryService>;

  beforeEach(async () => {
    location = jasmine.createSpyObj<Location>('Location', ['back']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    history = jasmine.createSpyObj<NavigationHistoryService>('NavigationHistoryService', ['hasInternalHistory']);

    await TestBed.configureTestingModule({
      imports: [BackButtonComponent, TranslocoTestingModule.forRoot({ langs: { fr: {} }, translocoConfig: { availableLangs: ['fr'], defaultLang: 'fr' } })],
      providers: [
        provideZonelessChangeDetection(),
        { provide: Location, useValue: location },
        { provide: Router, useValue: router },
        { provide: NavigationHistoryService, useValue: history },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BackButtonComponent);
  });

  it('calls Location.back() when internal history exists', () => {
    history.hasInternalHistory.and.returnValue(true);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLElement).click();
    expect(location.back).toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('navigates to fallbackUrl when no internal history', () => {
    history.hasInternalHistory.and.returnValue(false);
    fixture.componentRef.setInput('fallbackUrl', '/pro/manage');
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLElement).click();
    expect(router.navigate).toHaveBeenCalledWith(['/pro/manage']);
    expect(location.back).not.toHaveBeenCalled();
  });

  it('defaults fallbackUrl to "/"', () => {
    history.hasInternalHistory.and.returnValue(false);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLElement).click();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('renders label by default and hides it when showLabel is false', () => {
    history.hasInternalHistory.and.returnValue(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('span')).not.toBeNull();

    fixture.componentRef.setInput('showLabel', false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('span')).toBeNull();
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/back-button.component.spec.ts' --watch=false`

Expected: FAIL with `Cannot find module './back-button.component'`.

- [ ] **Step 3: Create the component**

```typescript
// frontend/src/app/shared/uis/back-button/back-button.component.ts
import { Component, inject, input } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { NavigationHistoryService } from '../../../core/navigation/navigation-history.service';

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

- [ ] **Step 4: Run tests — expect 4/4 PASS**

Same command. Expected: 4/4 PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/uis/back-button/
git commit -m "feat: add reusable BackButtonComponent"
```

---

## Task 3: Migrate `ProBookingHistoryComponent`

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-booking-history/pro-booking-history.component.ts`

The current page has an inline back button in its header: `<button class="back" (click)="goBack()"><mat-icon>arrow_back</mat-icon></button>`. The `goBack()` method hardcodes `router.navigate(['/pro/manage'])`. Replace both with `<app-back-button fallbackUrl="/pro/manage" [showLabel]="false" />`.

- [ ] **Step 1: Add the import**

At the top of the file (near other shared imports):

```typescript
import { BackButtonComponent } from '../../../shared/uis/back-button/back-button.component';
```

- [ ] **Step 2: Add to the `imports` array**

Inside the `@Component` decorator's `imports: [...]`, add `BackButtonComponent` (alphabetical or at the end — pick the file's existing convention).

- [ ] **Step 3: Replace the inline button in the template**

Current markup around line 36-39:

```html
<header class="page-header">
  <button class="back" (click)="goBack()"><mat-icon>arrow_back</mat-icon></button>
  <h1>{{ 'pro.history.title' | transloco }}</h1>
</header>
```

Change to:

```html
<header class="page-header">
  <app-back-button fallbackUrl="/pro/manage" [showLabel]="false" />
  <h1>{{ 'pro.history.title' | transloco }}</h1>
</header>
```

- [ ] **Step 4: Delete the `goBack()` method**

Find the method in the class (around line 197):

```typescript
protected goBack(): void {
  this.router.navigate(['/pro/manage']);
}
```

Delete it entirely.

- [ ] **Step 5: Check whether `Router` is still used**

Run:
```
grep -n "this.router\." /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/pages/pro/pro-booking-history/pro-booking-history.component.ts
```

If there are other uses (e.g. `router.navigate(['/pro/clients', b.user.id])` from `openClient`), keep the `Router` injection. Otherwise remove the injection line + the import.

- [ ] **Step 6: Remove the `.back` SCSS rule from the component styles**

In the component's `styles` array, search for `.back {` and remove that block (and its nested rules, if any). Keep all other styles unchanged.

- [ ] **Step 7: Build**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -15
```

Expected: build succeeds.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/pages/pro/pro-booking-history/pro-booking-history.component.ts
git commit -m "refactor: use BackButtonComponent in pro-booking-history"
```

---

## Task 4: Migrate `EmployeeClientDetailComponent`

**Files:**
- Modify: `frontend/src/app/pages/employee/employee-client-detail.component.ts`

The current page has a `<a routerLink="/employee/bookings" class="back-link">` block that hardcodes the target. Replace with `<app-back-button fallbackUrl="/employee/bookings" />`.

- [ ] **Step 1: Add the import**

At the top of the file:

```typescript
import { BackButtonComponent } from '../../shared/uis/back-button/back-button.component';
```

- [ ] **Step 2: Update the `imports` array**

Remove `RouterLink` from the import from `@angular/router` if it's no longer used elsewhere in the file (check with grep first). Remove `RouterLink` from the `imports` array.

Add `BackButtonComponent` to the `imports` array.

- [ ] **Step 3: Replace the inline anchor in the template**

Current markup around line 30-33:

```html
<a routerLink="/employee/bookings" class="back-link">
  <mat-icon>arrow_back</mat-icon>
  <span>{{ 'common.back' | transloco }}</span>
</a>
```

Change to:

```html
<app-back-button fallbackUrl="/employee/bookings" />
```

- [ ] **Step 4: Remove the `.back-link` SCSS rule**

In the component's `styles` array, find the `.back-link { ... }` block (around line 77) and remove it entirely (including any nested rules). Keep all other styles.

- [ ] **Step 5: Check `RouterLink` usage**

Run:
```
grep -n "routerLink\|RouterLink" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/pages/employee/employee-client-detail.component.ts
```

If no other usage exists, remove:
- `RouterLink` from the `import { ActivatedRoute, RouterLink } from '@angular/router';` line (leaving `ActivatedRoute`).

- [ ] **Step 6: Check `MatIconModule` usage**

Run:
```
grep -n "mat-icon" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/pages/employee/employee-client-detail.component.ts
```

If the only `<mat-icon>` was inside the removed back-link, also remove `MatIconModule` from the imports and the `imports` array. If other `<mat-icon>` usages remain, keep it.

- [ ] **Step 7: Build**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -15
```

Expected: build succeeds.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/pages/employee/employee-client-detail.component.ts
git commit -m "refactor: use BackButtonComponent in employee-client-detail"
```

---

## Task 5: Manual QA

**Files:** none.

- [ ] **Step 1: Start the dev server (if not running)**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm start -- --port 4200
```

Wait for `Local: http://localhost:4200/`.

- [ ] **Step 2: Test ProBookingHistory — history case**

In the browser:
- Log in as a pro
- Navigate `/pro/manage` → click the "Historique" card (lands on `/pro/settings/history`)
- Click the back-button arrow in the page header

Expected: returns to `/pro/manage` via browser history.

- [ ] **Step 3: Test ProBookingHistory — deep-link case**

- Open a fresh tab, paste `http://localhost:4200/pro/settings/history` directly
- Log in if prompted — you should land on the page
- Click the back-button arrow

Expected: navigates to `/pro/manage` (fallback).

- [ ] **Step 4: Test EmployeeClientDetail — history case**

- Log in as an employee
- Navigate `/employee/bookings` → click a client card (lands on `/employee/clients/:id`)
- Click the back-button

Expected: returns to `/employee/bookings` via browser history.

- [ ] **Step 5: Test EmployeeClientDetail — deep-link case**

- Open a fresh tab, paste `http://localhost:4200/employee/clients/<some-id>` directly
- Log in if prompted
- Click the back-button

Expected: navigates to `/employee/bookings` (fallback).

- [ ] **Step 6: No commit if all checks pass**

If any case fails, fix in a new commit. If all pass, QA is complete.

---

## Self-Review

**Spec coverage:**
- ✅ `NavigationHistoryService` with count-based detection → Task 1
- ✅ `BackButtonComponent` with `fallbackUrl` + `showLabel` inputs → Task 2
- ✅ Migration of `ProBookingHistoryComponent` → Task 3
- ✅ Migration of `EmployeeClientDetailComponent` → Task 4
- ✅ i18n key `common.back` — already exists, no task needed (noted in File Structure)
- ✅ Shared `Header` left unchanged — no task for it (as specified in out-of-scope)
- ✅ Unit tests for service + component → Tasks 1, 2
- ✅ Manual QA on 4 flows → Task 5

**Placeholder check:** None — every step has actual code, actual commands, or concrete inspection instructions.

**Type consistency:** `NavigationHistoryService`, `hasInternalHistory()`, `BackButtonComponent`, `fallbackUrl`, `showLabel` — consistent across all tasks.

**Signal input defaults:** `fallbackUrl = input<string>('/')` and `showLabel = input<boolean>(true)` — defaults match the tested cases in Task 2 and the migration instructions in Tasks 3 and 4.
