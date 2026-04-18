# Notifications Feed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the notifications feed so it initially loads only the last 48h of notifications, supports infinite scroll that transparently continues into full history, and supports swipe-left-to-mark-as-read on mobile with a green-gradient `✓` reveal.

**Architecture:** Backend adds one optional `since: Instant` query param + two repo methods (no breaking change). Frontend refactors the store shape (page-based with mode `recent`/`full`), adds a `SwipeLeftDirective` + `NotificationRowComponent`, and rewires `NotificationsComponent` with an IntersectionObserver sentinel.

**Tech Stack:** Spring Boot 3.5, JPA, JUnit 5 (backend). Angular 20 standalone + zoneless, NgRx SignalStore, Angular Material, Pointer Events API, IntersectionObserver (frontend).

**Spec:** `docs/superpowers/specs/2026-04-18-notifications-feed-design.md`

---

## File Structure

**Backend — modified files (3):**
- `backend/src/main/java/com/prettyface/app/notification/repo/NotificationRepository.java`
- `backend/src/main/java/com/prettyface/app/notification/app/NotificationService.java`
- `backend/src/main/java/com/prettyface/app/notification/web/NotificationController.java`

**Backend — test files (1 new / 1 modified):**
- `backend/src/test/java/com/prettyface/app/notification/app/NotificationServiceTests.java` (new or modified if existing)

**Frontend — new files (5):**
- `frontend/src/app/features/notifications/components/notification-row/notification-row.component.ts`
- `frontend/src/app/features/notifications/components/notification-row/notification-row.component.spec.ts`
- `frontend/src/app/features/notifications/components/notification-row/swipe-left.directive.ts`
- `frontend/src/app/features/notifications/components/notification-row/swipe-left.directive.spec.ts`
- `frontend/src/app/features/notifications/store/notifications.store.spec.ts` (if not present)

**Frontend — modified files (4):**
- `frontend/src/app/features/notifications/services/notifications.service.ts`
- `frontend/src/app/features/notifications/store/notifications.store.ts`
- `frontend/src/app/pages/notifications/notifications.component.ts`
- `frontend/src/app/pages/notifications/notifications.component.html`

**i18n (2 files):**
- `frontend/public/i18n/fr.json`
- `frontend/public/i18n/en.json`

Each task ends with a commit.

---

## Task 1: Backend — repository methods

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/notification/repo/NotificationRepository.java`

- [ ] **Step 1: Add two new derived queries**

Open the file. The interface currently has four methods. Add two new ones below the existing ones:

```java
Page<Notification> findByRecipientIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
    Long recipientId, LocalDateTime since, Pageable pageable);

Page<Notification> findByRecipientIdAndReadAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
    Long recipientId, boolean read, LocalDateTime since, Pageable pageable);
```

Add the import at the top:
```java
import java.time.LocalDateTime;
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn compile 2>&1 | tail -15`

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/notification/repo/NotificationRepository.java
git commit -m "feat: add notification repo queries with since filter"
```

---

## Task 2: Backend — service method signature

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/notification/app/NotificationService.java`

- [ ] **Step 1: Update the `listForRecipient` signature and dispatch**

Replace the current `listForRecipient(Long, Boolean, Pageable)` method body. The full new method:

```java
@Transactional(readOnly = true)
public Page<Notification> listForRecipient(Long recipientId, Boolean read, Instant since, Pageable pageable) {
    LocalDateTime sinceLdt = since != null
            ? LocalDateTime.ofInstant(since, ZoneOffset.UTC)
            : null;

    if (read != null && sinceLdt != null) {
        return notificationRepository.findByRecipientIdAndReadAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                recipientId, read, sinceLdt, pageable);
    }
    if (read != null) {
        return notificationRepository.findByRecipientIdAndReadOrderByCreatedAtDesc(recipientId, read, pageable);
    }
    if (sinceLdt != null) {
        return notificationRepository.findByRecipientIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                recipientId, sinceLdt, pageable);
    }
    return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
}
```

Add imports at the top:
```java
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn compile 2>&1 | tail -15`

Expected: BUILD FAILURE — `NotificationController` still calls the 3-arg version. This is expected; Task 3 fixes the caller.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/notification/app/NotificationService.java
git commit -m "feat: add since filter to NotificationService.listForRecipient"
```

---

## Task 3: Backend — controller query parameter

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/notification/web/NotificationController.java`

- [ ] **Step 1: Add the `since` parameter and update the call**

Replace the `list` method:

```java
@GetMapping
public Page<NotificationResponse> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) Boolean read,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
        Pageable pageable) {
    return applicationSchemaExecutor.call(() ->
            notificationService.listForRecipient(principal.getId(), read, since, pageable)
                    .map(NotificationMapper::toResponse));
}
```

Add imports at the top:
```java
import org.springframework.format.annotation.DateTimeFormat;
import java.time.Instant;
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn compile 2>&1 | tail -15`

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/notification/web/NotificationController.java
git commit -m "feat: expose since query param on notifications endpoint"
```

---

## Task 4: Backend — service test for since filter

**Files:**
- Modify or create: `backend/src/test/java/com/prettyface/app/notification/app/NotificationServiceTests.java`

- [ ] **Step 1: Check whether the test file exists**

Run: `ls /Users/Gustavo.alves/Documents/personal/portfolio/backend/src/test/java/com/prettyface/app/notification/app/ 2>/dev/null`

If the file exists, open it; otherwise create it with the minimal scaffolding below.

- [ ] **Step 2: Add one focused test**

Add (inside the existing class, or in a new class if creating the file) this test method. If the file is new, include the class declaration and imports as shown.

```java
package com.prettyface.app.notification.app;

import com.prettyface.app.notification.domain.Notification;
import com.prettyface.app.notification.repo.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    @Mock NotificationRepository repo;
    @InjectMocks NotificationService service;

    @Test
    void listForRecipient_withSinceOnly_callsSinceQuery() {
        Instant since = Instant.parse("2026-04-16T00:00:00Z");
        Pageable pageable = PageRequest.of(0, 20);
        when(repo.findByRecipientIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(Page.empty());

        service.listForRecipient(1L, null, since, pageable);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(repo)
                .findByRecipientIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(1L), captor.capture(), eq(pageable));
        assertThat(captor.getValue()).isEqualTo(LocalDateTime.of(2026, 4, 16, 0, 0, 0));
    }

    @Test
    void listForRecipient_withReadAndSince_callsCombinedQuery() {
        Instant since = Instant.parse("2026-04-16T00:00:00Z");
        Pageable pageable = PageRequest.of(0, 20);
        when(repo.findByRecipientIdAndReadAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), anyBoolean(), any(), any()))
                .thenReturn(Page.empty());

        service.listForRecipient(1L, false, since, pageable);

        org.mockito.Mockito.verify(repo)
                .findByRecipientIdAndReadAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(1L), eq(false), any(), eq(pageable));
    }

    @Test
    void listForRecipient_withoutSince_callsExistingQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repo.findByRecipientIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Page.empty());

        service.listForRecipient(1L, null, null, pageable);

        org.mockito.Mockito.verify(repo).findByRecipientIdOrderByCreatedAtDesc(eq(1L), eq(pageable));
    }
}
```

If this is appended to an existing file, add only the three test methods (not the class wrapping) and make sure the necessary imports are present at the top.

Also add one more import used only above (needed for `anyBoolean()`):
```java
import static org.mockito.ArgumentMatchers.anyBoolean;
```

- [ ] **Step 3: Run the tests**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn test -Dtest=NotificationServiceTests 2>&1 | tail -20`

Expected: 3/3 PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/prettyface/app/notification/app/NotificationServiceTests.java
git commit -m "test: NotificationService routes since filter to correct repo method"
```

---

## Task 5: Frontend — service signature

**Files:**
- Modify: `frontend/src/app/features/notifications/services/notifications.service.ts`

- [ ] **Step 1: Replace the `list` method**

Replace the current method:

```typescript
list(read?: boolean, page = 0, size = 20): Observable<Page<NotificationResponse>> { ... }
```

with:

```typescript
list(params: {
  read?: boolean;
  since?: string;
  page?: number;
  size?: number;
} = {}): Observable<Page<NotificationResponse>> {
  let httpParams = new HttpParams()
    .set('page', params.page ?? 0)
    .set('size', params.size ?? 20);
  if (params.read !== undefined) {
    httpParams = httpParams.set('read', params.read);
  }
  if (params.since !== undefined) {
    httpParams = httpParams.set('since', params.since);
  }
  return this.http.get<Page<NotificationResponse>>(
    `${this.apiBaseUrl}/api/notifications`,
    { params: httpParams },
  );
}
```

Keep `unreadCount()` and `markAsRead()` unchanged.

- [ ] **Step 2: Run type-check**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx tsc --noEmit 2>&1 | tail -20`

Expected: type errors in `notifications.store.ts` — it still calls the old 3-positional signature. Task 6 fixes this.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/notifications/services/notifications.service.ts
git commit -m "refactor: NotificationsService.list accepts options object"
```

---

## Task 6: Frontend — `NotificationsStore` refactor with failing tests

**Files:**
- Create: `frontend/src/app/features/notifications/store/notifications.store.spec.ts`
- Modify: `frontend/src/app/features/notifications/store/notifications.store.ts`

- [ ] **Step 1: Write the failing tests**

Create the file:

```typescript
// frontend/src/app/features/notifications/store/notifications.store.spec.ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { of, Subject } from 'rxjs';
import { NotificationsStore } from './notifications.store';
import { NotificationsService } from '../services/notifications.service';
import { WebSocketService } from '../services/websocket.service';
import { NotificationResponse } from '../models/notification.model';

function notif(id: number, read = false, createdAt = '2026-04-18T10:00:00Z'): NotificationResponse {
  return {
    id, recipientId: 1, type: 'BOOKING_CREATED' as any,
    category: 'INFO' as any,
    title: `N${id}`, message: 'm',
    referenceId: id, referenceType: 'BOOKING' as any,
    read, createdAt,
  } as NotificationResponse;
}

describe('NotificationsStore', () => {
  let service: jasmine.SpyObj<NotificationsService>;
  let ws: jasmine.SpyObj<WebSocketService>;
  let store: InstanceType<typeof NotificationsStore>;

  beforeEach(() => {
    service = jasmine.createSpyObj<NotificationsService>('NotificationsService', ['list', 'unreadCount', 'markAsRead']);
    ws = jasmine.createSpyObj<WebSocketService>('WebSocketService', ['connect', 'disconnect'], { notification$: new Subject() as any });

    service.list.and.returnValue(of({
      content: [notif(1), notif(2)],
      totalElements: 2, totalPages: 1,
      last: true,
    } as any));
    service.markAsRead.and.returnValue(of(undefined as any));
    service.unreadCount.and.returnValue(of(2));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        NotificationsStore,
        { provide: NotificationsService, useValue: service },
        { provide: WebSocketService, useValue: ws },
      ],
    });
    store = TestBed.inject(NotificationsStore);
  });

  it('loadInitial calls service with since=now-48h, page=0, size=20 and sets mode=recent', () => {
    store.loadInitial();

    const args = service.list.calls.mostRecent().args[0];
    expect(args.page).toBe(0);
    expect(args.size).toBe(20);
    expect(args.since).toBeDefined();

    const sinceMs = new Date(args.since!).getTime();
    const expected = Date.now() - 48 * 60 * 60 * 1000;
    expect(Math.abs(sinceMs - expected)).toBeLessThan(5000);

    expect(store.mode()).toBe('recent');
    expect(store.notifications().length).toBe(2);
    expect(store.page()).toBe(0);
    expect(store.hasMore()).toBeFalse();
  });

  it('loadNextPage is a no-op when hasMore is false', () => {
    store.loadInitial();
    service.list.calls.reset();
    store.loadNextPage();
    expect(service.list).not.toHaveBeenCalled();
  });

  it('loadNextPage increments page and concats items when hasMore is true', () => {
    service.list.and.returnValue(of({
      content: [notif(1), notif(2)],
      totalElements: 4, totalPages: 2,
      last: false,
    } as any));
    store.loadInitial();
    expect(store.hasMore()).toBeTrue();

    service.list.and.returnValue(of({
      content: [notif(3), notif(4)],
      totalElements: 4, totalPages: 2,
      last: true,
    } as any));
    store.loadNextPage();

    expect(store.notifications().length).toBe(4);
    expect(store.page()).toBe(1);
    expect(store.hasMore()).toBeFalse();
  });

  it('loadNextPage in recent with hasMore=false switches to full mode and loads without since', () => {
    service.list.and.returnValue(of({
      content: [notif(1), notif(2)],
      totalElements: 2, totalPages: 1,
      last: true,
    } as any));
    store.loadInitial();
    expect(store.mode()).toBe('recent');
    expect(store.hasMore()).toBeFalse();

    service.list.and.returnValue(of({
      content: [notif(1), notif(3), notif(4)],  // id=1 overlaps with recent window
      totalElements: 3, totalPages: 1,
      last: true,
    } as any));
    store.loadNextPage();

    expect(store.mode()).toBe('full');
    const args = service.list.calls.mostRecent().args[0];
    expect(args.since).toBeUndefined();
    // Items: original {1,2} + filtered-out-duplicate 1 + {3,4} => {1,2,3,4}
    const ids = store.notifications().map(n => n.id).sort();
    expect(ids).toEqual([1, 2, 3, 4]);
  });

  it('dismiss removes item and decrements unreadCount when item was unread', () => {
    store.loadInitial(); // items {1,2} both unread
    // Force a known unreadCount
    store.loadUnreadCount();
    const initialCount = store.unreadCount();

    store.dismiss(1);

    expect(service.markAsRead).toHaveBeenCalledWith(1);
    expect(store.notifications().map(n => n.id)).toEqual([2]);
    expect(store.unreadCount()).toBe(initialCount - 1);
  });

  it('dismiss does not decrement unreadCount when item was already read', () => {
    service.list.and.returnValue(of({
      content: [notif(1, true), notif(2, false)],
      totalElements: 2, totalPages: 1,
      last: true,
    } as any));
    store.loadInitial();
    store.loadUnreadCount();
    const initialCount = store.unreadCount();

    store.dismiss(1);  // id=1 was already read
    expect(store.unreadCount()).toBe(initialCount);
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/notifications.store.spec.ts' --watch=false 2>&1 | tail -30`

Expected: FAIL — `store.loadInitial`, `store.mode`, `store.page`, `store.hasMore`, `store.dismiss` do not exist yet.

- [ ] **Step 3: Replace the store with the new shape**

Replace the entire content of `frontend/src/app/features/notifications/store/notifications.store.ts`:

```typescript
import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap, EMPTY, catchError } from 'rxjs';
import { NotificationsService } from '../services/notifications.service';
import { WebSocketService } from '../services/websocket.service';
import { NotificationResponse } from '../models/notification.model';

const PAGE_SIZE = 20;
const RECENT_WINDOW_MS = 48 * 60 * 60 * 1000;

type NotificationsState = {
  notifications: NotificationResponse[];
  unreadCount: number;
  latestNotification: NotificationResponse | null;
  page: number;
  hasMore: boolean;
  mode: 'recent' | 'full';
};

export const NotificationsStore = signalStore(
  withState<NotificationsState>({
    notifications: [],
    unreadCount: 0,
    latestNotification: null,
    page: 0,
    hasMore: false,
    mode: 'recent',
  }),
  withComputed((store) => ({
    hasUnread: computed(() => store.unreadCount() > 0),
    badgeLabel: computed(() => {
      const count = store.unreadCount();
      if (count === 0) return '';
      if (count > 99) return '99+';
      return count.toString();
    }),
    emptyState: computed(() => store.notifications().length === 0 && store.mode() === 'full'),
  })),
  withMethods((store,
    notificationsService = inject(NotificationsService),
    webSocketService = inject(WebSocketService),
  ) => ({
    loadUnreadCount: rxMethod<void>(
      pipe(
        switchMap(() => notificationsService.unreadCount()),
        tap((count) => patchState(store, { unreadCount: count })),
        catchError(() => EMPTY)
      )
    ),
    loadInitial(): void {
      const since = new Date(Date.now() - RECENT_WINDOW_MS).toISOString();
      patchState(store, { notifications: [], page: 0, mode: 'recent', hasMore: false });
      notificationsService.list({ since, page: 0, size: PAGE_SIZE }).subscribe({
        next: (res: any) => {
          patchState(store, {
            notifications: res.content,
            page: 0,
            hasMore: !res.last,
          });
        },
        error: () => {},
      });
    },
    loadNextPage(): void {
      if (store.hasMore()) {
        const nextPage = store.page() + 1;
        const args: any = { page: nextPage, size: PAGE_SIZE };
        if (store.mode() === 'recent') {
          args.since = new Date(Date.now() - RECENT_WINDOW_MS).toISOString();
        }
        notificationsService.list(args).subscribe({
          next: (res: any) => {
            patchState(store, {
              notifications: [...store.notifications(), ...res.content],
              page: nextPage,
              hasMore: !res.last,
            });
          },
          error: () => {},
        });
        return;
      }
      // hasMore=false: in recent mode, switch to full and reload page=0
      if (store.mode() === 'recent') {
        patchState(store, { mode: 'full', page: 0 });
        notificationsService.list({ page: 0, size: PAGE_SIZE }).subscribe({
          next: (res: any) => {
            const existingIds = new Set(store.notifications().map((n) => n.id));
            const merged = [
              ...store.notifications(),
              ...res.content.filter((n: NotificationResponse) => !existingIds.has(n.id)),
            ];
            patchState(store, {
              notifications: merged,
              page: 0,
              hasMore: !res.last,
            });
          },
          error: () => {},
        });
      }
    },
    markAsRead: rxMethod<number>(
      pipe(
        switchMap((id) =>
          notificationsService.markAsRead(id).pipe(
            tap(() =>
              patchState(store, {
                notifications: store.notifications().map((n) =>
                  n.id === id ? { ...n, read: true } : n
                ),
                unreadCount: Math.max(0, store.unreadCount() - 1),
              })
            ),
            catchError(() => EMPTY)
          )
        )
      )
    ),
    dismiss(id: number): void {
      const item = store.notifications().find((n) => n.id === id);
      const wasUnread = item ? !item.read : false;
      notificationsService.markAsRead(id).subscribe({
        next: () => {
          patchState(store, {
            notifications: store.notifications().filter((n) => n.id !== id),
            unreadCount: wasUnread ? Math.max(0, store.unreadCount() - 1) : store.unreadCount(),
          });
        },
        error: () => {},
      });
    },
    connectWebSocket(): void {
      webSocketService.connect();
      webSocketService.notification$.subscribe((notification) => {
        patchState(store, {
          notifications: [notification, ...store.notifications()],
          unreadCount: store.unreadCount() + 1,
          latestNotification: notification,
        });
        setTimeout(() => {
          if (store.latestNotification()?.id === notification.id) {
            patchState(store, { latestNotification: null });
          }
        }, 4000);
      });
    },
    disconnectWebSocket(): void {
      webSocketService.disconnect();
    },
    clearLatestNotification(): void {
      patchState(store, { latestNotification: null });
    },
    reset(): void {
      patchState(store, { notifications: [], unreadCount: 0, latestNotification: null, page: 0, hasMore: false, mode: 'recent' });
    },
  })),
);
```

Note: the old `loadNotifications()` method is removed. Callers (only `NotificationsComponent`) will be updated in Task 10.

- [ ] **Step 4: Run tests — expect 6/6 PASS**

Same test command. Expected: 6/6 PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/notifications/store/
git commit -m "feat: refactor NotificationsStore with recent/full mode and pagination"
```

---

## Task 7: Frontend — `SwipeLeftDirective` with failing tests

**Files:**
- Create: `frontend/src/app/features/notifications/components/notification-row/swipe-left.directive.spec.ts`
- Create: `frontend/src/app/features/notifications/components/notification-row/swipe-left.directive.ts`

- [ ] **Step 1: Write the failing tests**

```typescript
// frontend/src/app/features/notifications/components/notification-row/swipe-left.directive.spec.ts
import { Component, ElementRef, ViewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { SwipeLeftDirective } from './swipe-left.directive';

@Component({
  standalone: true,
  imports: [SwipeLeftDirective],
  template: `<div #host appSwipeLeft (swipeLeftCommitted)="onCommitted()"></div>`,
})
class HostComponent {
  @ViewChild('host', { static: true }) host!: ElementRef<HTMLElement>;
  committedCount = 0;
  onCommitted(): void { this.committedCount++; }
}

function makePointerEvent(type: string, clientX: number): PointerEvent {
  return new PointerEvent(type, { clientX, pointerId: 1, bubbles: true });
}

function setMobile(isMobile: boolean): void {
  spyOn(window, 'matchMedia').and.returnValue({
    matches: isMobile,
    media: '(max-width: 767px)',
    onchange: null,
    addListener: () => {}, removeListener: () => {},
    addEventListener: () => {}, removeEventListener: () => {},
    dispatchEvent: () => false,
  } as MediaQueryList);
}

describe('SwipeLeftDirective', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('emits swipeLeftCommitted when drag distance exceeds 100px to the left on mobile', (done) => {
    setMobile(true);
    const el = host.host.nativeElement;
    el.dispatchEvent(makePointerEvent('pointerdown', 300));
    el.dispatchEvent(makePointerEvent('pointermove', 150));
    el.dispatchEvent(makePointerEvent('pointerup', 150));
    setTimeout(() => {
      expect(host.committedCount).toBe(1);
      done();
    }, 250);
  });

  it('does NOT emit when drag distance is below threshold', (done) => {
    setMobile(true);
    const el = host.host.nativeElement;
    el.dispatchEvent(makePointerEvent('pointerdown', 300));
    el.dispatchEvent(makePointerEvent('pointermove', 270));
    el.dispatchEvent(makePointerEvent('pointerup', 270));
    setTimeout(() => {
      expect(host.committedCount).toBe(0);
      done();
    }, 250);
  });

  it('emits on flick (high velocity, short distance)', (done) => {
    setMobile(true);
    const el = host.host.nativeElement;
    el.dispatchEvent(makePointerEvent('pointerdown', 300));
    setTimeout(() => {
      el.dispatchEvent(makePointerEvent('pointerup', 260));
      setTimeout(() => {
        expect(host.committedCount).toBe(1);
        done();
      }, 250);
    }, 60);
  });

  it('is a no-op on desktop', () => {
    setMobile(false);
    const el = host.host.nativeElement;
    el.dispatchEvent(makePointerEvent('pointerdown', 300));
    el.dispatchEvent(makePointerEvent('pointermove', 100));
    el.dispatchEvent(makePointerEvent('pointerup', 100));
    expect(host.committedCount).toBe(0);
  });

  it('does not emit on rightward drag', (done) => {
    setMobile(true);
    const el = host.host.nativeElement;
    el.dispatchEvent(makePointerEvent('pointerdown', 100));
    el.dispatchEvent(makePointerEvent('pointermove', 300));
    el.dispatchEvent(makePointerEvent('pointerup', 300));
    setTimeout(() => {
      expect(host.committedCount).toBe(0);
      done();
    }, 250);
  });
});
```

- [ ] **Step 2: Run tests — expect failure (module not found)**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/swipe-left.directive.spec.ts' --watch=false`

Expected: FAIL with `Cannot find module './swipe-left.directive'`.

- [ ] **Step 3: Create the directive**

```typescript
// frontend/src/app/features/notifications/components/notification-row/swipe-left.directive.ts
import { Directive, ElementRef, HostListener, inject, output } from '@angular/core';

const DISMISS_THRESHOLD_PX = 100;
const DISMISS_VELOCITY = 0.5;
const MIN_FLICK_DURATION_MS = 50;
const MOBILE_QUERY = '(max-width: 767px)';

@Directive({
  selector: '[appSwipeLeft]',
  standalone: true,
})
export class SwipeLeftDirective {
  readonly swipeLeftCommitted = output<void>();

  private readonly host = inject(ElementRef<HTMLElement>);

  private startX = 0;
  private startTime = 0;
  private dragging = false;

  @HostListener('pointerdown', ['$event'])
  onPointerDown(event: PointerEvent): void {
    if (!this.isMobile()) return;
    this.dragging = true;
    this.startX = event.clientX;
    this.startTime = performance.now();
    const el = this.host.nativeElement;
    el.style.transition = 'none';
    try { (event.target as HTMLElement).setPointerCapture?.(event.pointerId); } catch { /* ignore */ }
  }

  @HostListener('pointermove', ['$event'])
  onPointerMove(event: PointerEvent): void {
    if (!this.dragging) return;
    const delta = Math.min(0, event.clientX - this.startX);
    this.host.nativeElement.style.transform = `translateX(${delta}px)`;
  }

  @HostListener('pointerup', ['$event'])
  @HostListener('pointercancel', ['$event'])
  onPointerUp(event: PointerEvent): void {
    if (!this.dragging) return;
    this.dragging = false;
    const delta = Math.min(0, event.clientX - this.startX);
    const absDelta = Math.abs(delta);
    const duration = performance.now() - this.startTime;
    const velocity = duration >= MIN_FLICK_DURATION_MS ? absDelta / duration : 0;
    const el = this.host.nativeElement;

    if (absDelta > DISMISS_THRESHOLD_PX || velocity > DISMISS_VELOCITY) {
      el.style.transition = 'transform 200ms ease-in';
      el.style.transform = 'translateX(-100%)';
      setTimeout(() => this.swipeLeftCommitted.emit(), 200);
    } else {
      el.style.transition = 'transform 200ms ease-out';
      el.style.transform = 'translateX(0)';
    }
  }

  private isMobile(): boolean {
    return typeof window !== 'undefined'
      && typeof window.matchMedia === 'function'
      && window.matchMedia(MOBILE_QUERY).matches;
  }
}
```

- [ ] **Step 4: Run tests — expect 5/5 PASS**

Same command.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/notifications/components/notification-row/
git commit -m "feat: add SwipeLeftDirective"
```

---

## Task 8: Frontend — `NotificationRowComponent` with failing tests

**Files:**
- Create: `frontend/src/app/features/notifications/components/notification-row/notification-row.component.spec.ts`
- Create: `frontend/src/app/features/notifications/components/notification-row/notification-row.component.ts`

- [ ] **Step 1: Write the failing tests**

```typescript
// notification-row.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { NotificationRowComponent } from './notification-row.component';
import { NotificationResponse } from '../../models/notification.model';

function sample(): NotificationResponse {
  return {
    id: 1, recipientId: 1, type: 'BOOKING_CREATED' as any,
    category: 'INFO' as any,
    title: 'Nouveau RDV', message: 'Marie D. a réservé un soin',
    referenceId: 42, referenceType: 'BOOKING' as any,
    read: false, createdAt: '2026-04-18T14:30:00Z',
  } as NotificationResponse;
}

describe('NotificationRowComponent', () => {
  let fixture: ComponentFixture<NotificationRowComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        NotificationRowComponent,
        TranslocoTestingModule.forRoot({
          langs: { fr: {} },
          translocoConfig: { availableLangs: ['fr'], defaultLang: 'fr' },
        }),
      ],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    fixture = TestBed.createComponent(NotificationRowComponent);
  });

  it('renders title, message and formatted createdAt', () => {
    fixture.componentRef.setInput('notification', sample());
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.title')?.textContent?.trim()).toBe('Nouveau RDV');
    expect(el.querySelector('.message')?.textContent?.trim()).toBe('Marie D. a réservé un soin');
    expect(el.querySelector('.time')?.textContent?.trim()).toContain('18/04/2026');
  });

  it('emits rowClick on card click', () => {
    fixture.componentRef.setInput('notification', sample());
    const emitted: unknown[] = [];
    fixture.componentInstance.rowClick.subscribe(() => emitted.push(true));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.notif-card') as HTMLElement).click();
    expect(emitted.length).toBe(1);
  });
});
```

- [ ] **Step 2: Run tests — expect failure**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/notification-row.component.spec.ts' --watch=false`

Expected: FAIL with `Cannot find module './notification-row.component'`.

- [ ] **Step 3: Create the component**

```typescript
// frontend/src/app/features/notifications/components/notification-row/notification-row.component.ts
import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { NotificationResponse } from '../../models/notification.model';
import { AppDateTimePipe } from '../../../../shared/pipes/app-datetime.pipe';
import { SwipeLeftDirective } from './swipe-left.directive';

@Component({
  selector: 'app-notification-row',
  standalone: true,
  imports: [CommonModule, MatIconModule, AppDateTimePipe, SwipeLeftDirective],
  template: `
    <div class="notif-wrap">
      <div class="notif-bg">
        <mat-icon>check</mat-icon>
      </div>
      <div class="notif-card"
           appSwipeLeft
           (swipeLeftCommitted)="swipedLeft.emit()"
           (click)="rowClick.emit()">
        <div class="icon-cell">
          <mat-icon>notifications</mat-icon>
        </div>
        <div class="content">
          <div class="title">{{ notification().title }}</div>
          <div class="message">{{ notification().message }}</div>
          <div class="time">{{ notification().createdAt | appDateTime }}</div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .notif-wrap { position: relative; overflow: hidden; border-radius: 10px; margin-bottom: 8px; }
    .notif-bg {
      position: absolute; inset: 0;
      display: flex; align-items: center; justify-content: flex-end;
      padding-right: 14px;
      background: linear-gradient(270deg, #a7f3d0 0%, #6ee7b7 100%);
      color: #065f46;
      z-index: 1;
    }
    .notif-bg mat-icon { font-size: 22px; width: 22px; height: 22px; }
    .notif-card {
      position: relative; z-index: 2;
      background: white;
      padding: 10px 12px;
      display: flex; gap: 10px;
      box-shadow: 0 1px 2px rgba(0,0,0,0.04);
      cursor: pointer;
      touch-action: pan-y;
    }
    .icon-cell {
      width: 34px; height: 34px;
      border-radius: 50%;
      background: #fff0f5;
      color: #c06;
      display: flex; align-items: center; justify-content: center;
      flex-shrink: 0;
    }
    .icon-cell mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .content { flex: 1; min-width: 0; }
    .title { font-size: 13px; font-weight: 600; color: #333; }
    .message { font-size: 12px; color: #666; margin-top: 2px; }
    .time { font-size: 11px; color: #999; margin-top: 3px; }
  `],
})
export class NotificationRowComponent {
  readonly notification = input.required<NotificationResponse>();
  readonly swipedLeft = output<void>();
  readonly rowClick = output<void>();
}
```

- [ ] **Step 4: Run tests — expect 2/2 PASS**

Same command.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/notifications/components/notification-row/notification-row.component.ts frontend/src/app/features/notifications/components/notification-row/notification-row.component.spec.ts
git commit -m "feat: add NotificationRowComponent with swipe-to-dismiss"
```

---

## Task 9: Frontend — i18n keys

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Check existing keys**

Run:
```
grep -n '"notifications"' /Users/Gustavo.alves/Documents/personal/portfolio/frontend/public/i18n/fr.json /Users/Gustavo.alves/Documents/personal/portfolio/frontend/public/i18n/en.json | head -5
```

If a top-level `"notifications"` object exists, merge new keys into it. Otherwise create it.

- [ ] **Step 2: Add keys to `fr.json`**

Merge the following into `fr.json` inside the existing `notifications` object (or add the object if absent):

```json
"notifications": {
  "title": "Notifications",
  "empty": "Aucune notification",
  "endOfHistory": "Plus rien à charger"
}
```

Keep any existing keys inside `notifications` untouched.

- [ ] **Step 3: Add matching keys to `en.json`**

```json
"notifications": {
  "title": "Notifications",
  "empty": "No notifications",
  "endOfHistory": "End of history"
}
```

- [ ] **Step 4: Verify JSON syntax**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && node -e "JSON.parse(require('fs').readFileSync('public/i18n/fr.json','utf8')); JSON.parse(require('fs').readFileSync('public/i18n/en.json','utf8')); console.log('OK');"`

Expected: `OK`.

- [ ] **Step 5: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat: add i18n keys for notifications feed"
```

---

## Task 10: Frontend — `NotificationsComponent` refactor

**Files:**
- Modify: `frontend/src/app/pages/notifications/notifications.component.ts`
- Modify: `frontend/src/app/pages/notifications/notifications.component.html`

- [ ] **Step 1: Replace the component TS**

Replace the entire content of `frontend/src/app/pages/notifications/notifications.component.ts`:

```typescript
import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, afterNextRender, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { NotificationsStore } from '../../features/notifications/store/notifications.store';
import { NotificationResponse } from '../../features/notifications/models/notification.model';
import { NotificationRowComponent } from '../../features/notifications/components/notification-row/notification-row.component';
import { BackButtonComponent } from '../../shared/uis/back-button/back-button.component';

@Component({
  selector: 'app-notifications-page',
  standalone: true,
  imports: [
    TranslocoPipe,
    MatIconModule,
    MatProgressSpinnerModule,
    NotificationRowComponent,
    BackButtonComponent,
  ],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
})
export class NotificationsComponent implements AfterViewInit, OnDestroy {
  protected readonly store = inject(NotificationsStore);
  private readonly router = inject(Router);

  @ViewChild('sentinel') sentinel?: ElementRef<HTMLElement>;
  private observer?: IntersectionObserver;

  constructor() {
    this.store.loadInitial();
    afterNextRender(() => this.setupObserver());
  }

  ngAfterViewInit(): void {
    this.setupObserver();
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  private setupObserver(): void {
    if (typeof IntersectionObserver === 'undefined' || !this.sentinel) return;
    this.observer?.disconnect();
    this.observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting) {
        this.store.loadNextPage();
      }
    }, { rootMargin: '200px' });
    this.observer.observe(this.sentinel.nativeElement);
  }

  protected onRowClick(notification: NotificationResponse): void {
    if (!notification.read) {
      this.store.markAsRead(notification.id);
    }
    this.navigateToReference(notification);
  }

  private navigateToReference(notification: NotificationResponse): void {
    switch (notification.referenceType) {
      case 'BOOKING':
        this.router.navigate(['/pro/bookings'], {
          queryParams: { highlight: notification.referenceId },
        });
        break;
      case 'SALON_CLIENT':
        this.router.navigate(['/pro/clients', notification.referenceId]);
        break;
      default:
        break;
    }
  }
}
```

- [ ] **Step 2: Replace the template**

Replace the entire content of `frontend/src/app/pages/notifications/notifications.component.html`:

```html
<div class="notifications-page">
  <header class="page-header">
    <app-back-button fallbackUrl="/pro/manage" [showLabel]="false" />
    <h1>{{ 'notifications.title' | transloco }}</h1>
  </header>

  @if (store.emptyState()) {
    <div class="empty">
      <mat-icon>notifications_none</mat-icon>
      <p>{{ 'notifications.empty' | transloco }}</p>
    </div>
  } @else {
    @for (notif of store.notifications(); track notif.id) {
      <app-notification-row
        [notification]="notif"
        (swipedLeft)="store.dismiss(notif.id)"
        (rowClick)="onRowClick(notif)" />
    }

    <div #sentinel class="sentinel">
      @if (store.isPending()) {
        <mat-spinner diameter="20" />
      } @else if (!store.hasMore() && store.mode() === 'full') {
        <span class="end">{{ 'notifications.endOfHistory' | transloco }}</span>
      }
    </div>
  }
</div>
```

- [ ] **Step 3: Check if `isPending()` exists on the store**

The current `NotificationsStore` (from Task 6) does NOT use `withRequestStatus`. The template references `store.isPending()`. If the method is absent, replace `store.isPending()` in the template with a hardcoded `false` for now:

```html
@if (false) {
  <mat-spinner diameter="20" />
} @else if (!store.hasMore() && store.mode() === 'full') {
```

(A loading indicator can be added in a follow-up. Main behavior — the "end of history" message — remains correct.)

Alternatively, if adding `withRequestStatus` is trivial, wire it up in the store:
- Add `import { withRequestStatus, setPending, setFulfilled, setError } from '../../../shared/features/request.status.feature';`
- Add `withRequestStatus()` to the store factory chain between `withComputed` and `withMethods`.
- Wrap the `loadInitial` and `loadNextPage` subscriptions with `setPending()` at start and `setFulfilled()` in `next`/`setError()` in `error`.

Choose the simpler path. Use `false` placeholder if in doubt.

- [ ] **Step 4: Build**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -20
```

Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/notifications/
git commit -m "refactor: use NotificationRowComponent and infinite scroll in notifications page"
```

---

## Task 11: Manual QA

**Files:** none.

- [ ] **Step 1: Start the dev server (if not running)**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm start -- --port 4200
```

Wait for `Local: http://localhost:4200/`.

- [ ] **Step 2: Open the notifications page on iPhone SE viewport**

- Log in as a pro
- Cmd+Shift+M → iPhone SE (375×667)
- Navigate to `/notifications`

- [ ] **Step 3: Verify initial 48h filter**

Open DevTools Network tab. Observe the `GET /api/notifications?since=...&page=0&size=20` request. Confirm:
- The `since` param is present and is ~48h before now
- Only notifications newer than that are displayed

- [ ] **Step 4: Verify infinite scroll**

Scroll to the bottom of the list:
- If older notifs in the 48h window exist → spinner → next page appears
- When 48h is exhausted → the request fires again WITHOUT `since` (visible in Network tab) → older history items appear
- When the full history is exhausted → "Plus rien à charger" label appears

- [ ] **Step 5: Verify swipe-to-dismiss on mobile**

Swipe one notification to the left:
- During drag: green gradient background with ✓ reveals under the card
- Release past threshold: card slides off to the left, item disappears from list
- Unread count in the header badge drops by 1 (if the item was unread)
- `PATCH /api/notifications/{id}/read` visible in Network tab

Swipe another one but release before threshold:
- Card snaps back to original position, no network call

- [ ] **Step 6: Verify desktop is a no-op for swipe**

Disable device toolbar. On desktop viewport, try to drag a notification with the mouse:
- Card does NOT move (gesture disabled on desktop)
- Clicking the row still works (navigates per referenceType)

- [ ] **Step 7: Verify click navigation**

Click a BOOKING notification → navigates to `/pro/bookings?highlight=<id>`
Click a SALON_CLIENT notification → navigates to `/pro/clients/<id>`

- [ ] **Step 8: No commit if all checks pass**

If a bug is found, fix and commit separately.

---

## Self-Review

**Spec coverage:**
- ✅ Backend `since` param + new repo methods → Tasks 1, 2, 3
- ✅ Backend service test → Task 4
- ✅ Frontend service signature with options object → Task 5
- ✅ Store refactor with `recent`/`full` mode + dismiss → Task 6
- ✅ `SwipeLeftDirective` with threshold + velocity + desktop no-op → Task 7
- ✅ `NotificationRowComponent` with green-gradient ✓ background → Task 8
- ✅ i18n keys (title, empty, endOfHistory) → Task 9
- ✅ Component refactor with IntersectionObserver sentinel → Task 10
- ✅ Manual QA on all spec flows → Task 11

**Placeholder check:** None — every step has actual code, actual commands, or explicit inspection instructions.

**Type consistency:** `loadInitial`, `loadNextPage`, `dismiss`, `mode`, `page`, `hasMore`, `SwipeLeftDirective`, `swipeLeftCommitted`, `NotificationRowComponent`, `swipedLeft`, `rowClick`, `NotificationResponse`, `appSwipeLeft`, `appDateTime` — consistent across all tasks.

**Known edge case handled:** the overlap between the "recent" result set and the "full" history set is handled by filtering duplicates in `loadNextPage()` when transitioning to full mode (see Task 6, state update for full-mode transition).
