# Notifications Feed — Recent Window, Swipe-to-Read, Infinite Scroll — Design Spec

**Date:** 2026-04-18
**Status:** Approved (pending user review)
**Scope:** Backend (one optional `since` param + new repo method) + Frontend (store refactor, new row component with swipe gesture, infinite scroll).

## Goal

Rework the notifications feed so it:

1. Initially loads only notifications from the **last 48 hours** (not all notifs flat).
2. Supports **infinite scroll**: once the user reaches the bottom of the recent window, older notifications load automatically in pages of 20 — no UI rupture.
3. Supports **swipe-left to mark-as-read**: the card slides left over a green-gradient background with a checkmark; on release past threshold, the notification is removed from the current feed and the unread count is decremented. The notification remains in the database (future history page).

## Decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Initial window | Last 48 hours (strict, `now - 48h`) |
| Past-end behavior | Infinite scroll continues into full history automatically |
| Swipe action | Mark-as-read (not delete) — notif stays in DB |
| Swipe direction | Left |
| Swipe visual | Green gradient background with ✓ icon (Variant A) |
| Pull-to-refresh | No (WebSocket push already delivers new notifs in realtime) |
| Page size | 20 |
| Delete endpoint | None |

## Architecture

### Backend — one optional query parameter

**File:** `backend/src/main/java/com/prettyface/app/notification/web/NotificationController.java`

Add an optional `since: Instant` parameter to `GET /api/notifications`:

```java
@GetMapping
public Page<NotificationResponse> list(
    @RequestParam(required = false) Boolean read,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
    Pageable pageable) {
  return service.list(currentRecipientId(), read, since, pageable);
}
```

**File:** `NotificationService`

Extend the `list` method signature to accept `Instant since`. Convert to `LocalDateTime` with `LocalDateTime.ofInstant(since, ZoneOffset.UTC)` before passing to the repository. Route to the appropriate repo method:
- `read` null, `since` null → existing `findByRecipientIdOrderByCreatedAtDesc`
- `read` null, `since` set → new `findByRecipientIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc`
- `read` set, `since` null → existing `findByRecipientIdAndReadOrderByCreatedAtDesc`
- `read` set, `since` set → new `findByRecipientIdAndReadAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc`

**File:** `NotificationRepository`

Add two new query methods:

```java
Page<Notification> findByRecipientIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
    Long recipientId, LocalDateTime since, Pageable pageable);

Page<Notification> findByRecipientIdAndReadAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
    Long recipientId, boolean read, LocalDateTime since, Pageable pageable);
```

No breaking change: clients that don't pass `since` get the current behavior.

### Frontend — service signature

**File:** `frontend/src/app/features/notifications/services/notifications.service.ts`

Replace the positional signature `list(read?, page, size)` with an options object:

```typescript
list(params: {
  read?: boolean;
  since?: string;   // ISO datetime, passed through as-is
  page?: number;
  size?: number;
}): Observable<Page<NotificationResponse>>;
```

The other three methods (`unreadCount`, `markAsRead`, and anything else existing) stay as-is.

### Frontend — store refactor

**File:** `frontend/src/app/features/notifications/store/notifications.store.ts`

**New state shape:**

```typescript
interface NotificationsState {
  notifications: NotificationResponse[];   // accumulated via infinite scroll
  unreadCount: number;
  latestNotification: NotificationResponse | null;
  page: number;                            // 0-based last loaded
  hasMore: boolean;
  mode: 'recent' | 'full';
}
```

**Constants:**

```typescript
const PAGE_SIZE = 20;
const RECENT_WINDOW_MS = 48 * 60 * 60 * 1000; // 48h
```

**Methods:**

- `loadInitial()` — replaces the current `loadNotifications()`:
  - Sets `mode = 'recent'`, `since = new Date(Date.now() - RECENT_WINDOW_MS).toISOString()`, resets `notifications=[]`, `page=0`.
  - Calls `service.list({ since, page: 0, size: PAGE_SIZE })`.
  - Populates `notifications`, `page = 0`, `hasMore = !res.last`.
  
- `loadNextPage()`:
  - No-op if `isPending || !hasMore`.
  - If `hasMore === true`: increment `page`, call the service with the same filter, concat items.
  - If `mode === 'recent' && hasMore === false`: switch to `mode='full'`, reset `page=0` and `notifications` stays (we don't wipe the 48h results we already showed). Then call the service **without `since`** at `page=0`, skipping items whose ids are already in `notifications` to avoid duplicates from the 48h overlap.
  
- `dismiss(id: number)`:
  - Calls `service.markAsRead(id)`.
  - On success: remove the item from `notifications`; decrement `unreadCount` if the removed item's `read === false`.
  
- Existing methods `loadUnreadCount`, `markAsRead`, `connectWebSocket`, `disconnectWebSocket`, `clearLatestNotification`, `reset` — kept.
- WebSocket-received notifications still prepend to `notifications`.

**Computed signals:**

- `emptyState: boolean` — `notifications.length === 0 && !isPending && mode === 'full'` (empty state only visible after switch to full history).
- `hasUnread`, `badgeLabel` — kept.

### Frontend — `SwipeLeftDirective`

**File:** `frontend/src/app/features/notifications/components/notification-row/swipe-left.directive.ts`

Standalone directive `[appSwipeLeft]` mirroring the existing `BottomSheetDragDirective` pattern, but horizontal and leftward-only:

- Listens to `pointerdown/move/up/cancel` on the host.
- On `pointerdown`: capture pointer, record `startX`, `startTime`.
- On `pointermove`: translate the host by `Math.min(0, clientX - startX)` (only leftward).
- On `pointerup`:
  - If `delta < -100px` OR `velocity > 0.5 px/ms`: animate to `translateX(-100%)` over 200ms then emit `swipeLeftCommitted` output.
  - Else: animate snap back to `translateX(0)`.
- Constants: `DISMISS_THRESHOLD_PX = 100`, `DISMISS_VELOCITY = 0.5`, `MIN_FLICK_DURATION_MS = 50`.
- No-op when not on mobile (`window.matchMedia('(max-width: 767px)').matches === false`) — gesture only exists on mobile, desktop users tap the row to dismiss via a future "mark-as-read" button if needed (out of scope here).

### Frontend — `NotificationRowComponent`

**File:** `frontend/src/app/features/notifications/components/notification-row/notification-row.component.ts`

Standalone component. Inputs: `notification: NotificationResponse` (required). Outputs: `swipedLeft: void`, `rowClick: void`.

Template structure:

```html
<div class="notif-wrap">
  <div class="notif-bg">
    <mat-icon>check</mat-icon>
  </div>
  <div class="notif-card"
       appSwipeLeft
       (swipeLeftCommitted)="swipedLeft.emit()"
       (click)="rowClick.emit()">
    <div class="icon-cell">{{ iconGlyph() }}</div>
    <div class="content">
      <div class="title">{{ notification().title }}</div>
      <div class="message">{{ notification().message }}</div>
      <div class="time">{{ notification().createdAt | appDateTime }}</div>
    </div>
  </div>
</div>
```

Styling (green-gradient background with ✓, Variant A):

```scss
.notif-wrap {
  position: relative;
  overflow: hidden;
  border-radius: 10px;
}
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
  touch-action: pan-y;
}
```

Icon glyph is derived from `notification.type` / `referenceType` — whatever mapping exists today in `notifications.component` moves here.

### Frontend — `NotificationsComponent` refactor

**File:** `frontend/src/app/pages/notifications/notifications.component.ts`

Template becomes:

```html
<div class="notifications-page">
  <header class="page-header">
    <app-back-button fallbackUrl="/pro/manage" />
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

IntersectionObserver on `#sentinel` → `store.loadNextPage()`. Same SSR-safe setup as `pro-booking-history`.

On `onRowClick(notif)`: call the existing "mark-read + navigate-by-referenceType" logic that already lives in `NotificationsComponent` (move it unchanged).

## i18n

Verify (or add) in `frontend/public/i18n/fr.json` and `en.json`:

- `notifications.title` → `"Notifications"` / `"Notifications"`
- `notifications.endOfHistory` → `"Plus rien à charger"` / `"End of history"`
- `notifications.empty` → `"Aucune notification"` / `"No notifications"`

## Testing

### Backend (JUnit)

- `NotificationControllerTests`:
  - `GET /api/notifications?since=2026-04-16T10:00:00Z&page=0&size=20` returns only notifications created after that instant
  - Existing tests (no `since`) keep passing
- `NotificationRepositoryTests`:
  - `findByRecipientIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc` returns items `>= since` and respects pagination/ordering

### Frontend (Jasmine/Karma)

- `NotificationsService`:
  - `list({ since: 'x' })` includes `since=x` in the HTTP params
  - `list({})` omits `since`
- `NotificationsStore`:
  - `loadInitial()` sets `mode='recent'`, calls service with `since ≈ now - 48h`, `page=0`
  - `loadNextPage()` in recent with `hasMore=true` increments page and concats items
  - `loadNextPage()` in recent with `hasMore=false` switches to `mode='full'`, reloads at `page=0` without `since`, skipping already-loaded IDs
  - `dismiss(id)` removes item from state; calls `service.markAsRead(id)`; decrements `unreadCount` when item was unread
  - WebSocket push still prepends to `notifications`
- `SwipeLeftDirective`:
  - `pointerdown + pointermove(-150px) + pointerup` emits `swipeLeftCommitted`
  - `pointerdown + pointermove(-40px) + pointerup` does NOT emit (snap-back)
  - Flick (60ms, -40px) emits
  - Desktop viewport: no-op
- `NotificationRowComponent`:
  - Renders title, message, formatted `appDateTime`
  - Clicking the card emits `rowClick`
  - Swipe emits `swipedLeft`

### Manual QA

1. Open `/notifications` after activity in the last 48h → see only those items, oldest-first at bottom of the 48h window
2. Scroll to bottom → spinner → older items from history load seamlessly
3. Swipe a notification left → green background + ✓ reveals, card slides off, unread count drops, item disappears
4. Refresh page → swiped notif is no longer in the recent window (it's read)
5. No network tab errors, no console warnings
6. Desktop viewport → swipe does not trigger (no-op), click still works

## Out of scope

- Dedicated "notification history" screen — future ticket
- Physical delete (`DELETE /api/notifications/{id}`) — explicitly not doing it
- Bulk actions (mark-all-as-read, clear-all) — YAGNI
- Pull-to-refresh — WebSocket covers
- Per-day bucketing / grouping — flat chronological list
- Desktop swipe gesture — mobile only by design

## Rollout

Single PR. Order of implementation:

1. Backend repo + service + controller + tests.
2. Frontend service `list` signature.
3. Frontend store refactor + tests.
4. `SwipeLeftDirective` + tests.
5. `NotificationRowComponent` + tests.
6. Refactor `NotificationsComponent` to use the row + sentinel.
7. i18n keys.
8. Manual QA on all six flows above.
