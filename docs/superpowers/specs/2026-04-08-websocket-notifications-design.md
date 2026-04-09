# WebSocket Notifications System - Design Spec

## Overview

Real-time notification system for Pretty Face salon management app. When a client creates or cancels a booking, the salon PRO and assigned EMPLOYEE are notified in real-time via WebSocket (STOMP over SockJS). Notifications are persisted in the database for later consultation.

The system is designed to be **extensible** — future notification types (leave requests, sick leave, post likes, etc.) plug into the same infrastructure.

## Recipients

| Event              | Notified                  | Not notified (email only) |
|--------------------|---------------------------|---------------------------|
| New client booking | PRO + assigned EMPLOYEE   | CLIENT (email)            |
| Booking cancelled  | PRO + assigned EMPLOYEE   | CLIENT (email)            |

## Data Model

Table `NOTIFICATIONS` in the **shared application schema** (not tenant-scoped).

```sql
CREATE TABLE NOTIFICATIONS (
    id              NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id    NUMBER NOT NULL,            -- FK → USERS.id
    tenant_slug     VARCHAR2(100) NOT NULL,     -- salon context
    type            VARCHAR2(50) NOT NULL,      -- NEW_BOOKING, BOOKING_CANCELLED, LEAVE_REQUEST, SICK_LEAVE, POST_LIKED, ...
    category        VARCHAR2(30) NOT NULL,      -- BOOKING, LEAVE, SOCIAL
    title           VARCHAR2(255) NOT NULL,
    message         VARCHAR2(500) NOT NULL,
    reference_id    NUMBER NOT NULL,            -- generic FK (booking ID, leave ID, post ID, ...)
    reference_type  VARCHAR2(50) NOT NULL,      -- BOOKING, LEAVE_REQUEST, POST
    is_read         NUMBER(1) DEFAULT 0 NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_notifications_recipient ON NOTIFICATIONS(recipient_id, is_read, created_at DESC);
```

**Design decisions:**
- `recipient_id` per user (not per tenant) so mark-as-read is independent per user.
- `reference_id` + `reference_type` instead of `booking_id` — polymorphic, supports any future entity type.
- `category` groups notification types into functional domains (for filtering in the UI).
- Shared schema (via `ApplicationSchemaExecutor`) because notifications are tied to users, not tenants.

### Enums

```java
enum NotificationType { NEW_BOOKING, BOOKING_CANCELLED, LEAVE_REQUEST, SICK_LEAVE, POST_LIKED }
enum NotificationCategory { BOOKING, LEAVE, SOCIAL }
enum ReferenceType { BOOKING, LEAVE_REQUEST, POST }
```

## Backend Architecture

### Package Structure

```
notification/
├── domain/
│   ├── Notification.java
│   ├── NotificationType.java
│   ├── NotificationCategory.java
│   └── ReferenceType.java
├── repo/
│   └── NotificationRepository.java
├── app/
│   ├── NotificationService.java          -- CRUD + business logic
│   └── NotificationDispatcher.java       -- persist + send via STOMP
├── web/
│   ├── dto/
│   │   └── NotificationResponse.java
│   ├── mapper/
│   │   └── NotificationMapper.java
│   └── NotificationController.java
config/
├── WebSocketConfig.java
└── WebSocketAuthInterceptor.java
```

### WebSocket Configuration

- **Protocol:** STOMP over SockJS
- **Endpoint:** `/ws` (SockJS)
- **Broker:** Simple in-memory broker (sufficient for single-instance deployment)
- **Dependency:** `spring-boot-starter-websocket`

### STOMP Topics

- `/user/{userId}/queue/notifications` — per-user personal queue (Spring maps automatically via `Principal`)
- No per-tenant topic needed: notifications are already targeted by `recipient_id`.

### Notification Flow

1. Client creates/cancels a booking via `CareBookingService`
2. `CareBookingService` calls `NotificationDispatcher.dispatch(recipients, notificationData)`
3. `NotificationDispatcher`:
   - Persists notification in DB (shared schema via `ApplicationSchemaExecutor`)
   - Sends via `SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/notifications", payload)`
4. Frontend STOMP client receives the message → toast + badge increment

### REST Endpoints

| Method | Path                              | Description                    |
|--------|-----------------------------------|--------------------------------|
| GET    | `/api/notifications?read=false`   | List notifications (filterable)|
| GET    | `/api/notifications/unread/count` | Unread count (for badge)       |
| PATCH  | `/api/notifications/{id}/read`    | Mark one notification as read  |

### WebSocket Authentication

- JWT passed as query param on SockJS connection: `/ws?token=xxx`
- `WebSocketAuthInterceptor` (implements `ChannelInterceptor`) intercepts STOMP `CONNECT` frame, validates JWT, sets `Principal` on the session
- The `Principal.getName()` must return the user's `id` (as String), not the email — this is what `SimpMessagingTemplate.convertAndSendToUser()` uses to route messages
- Invalid/expired token → connection refused
- `/user/queue/notifications` subscriptions are automatically scoped by Spring to the authenticated `Principal`

## Frontend Architecture

### Package Structure

```
frontend/src/app/
├── features/
│   └── notifications/
│       ├── models/
│       │   └── notification.model.ts
│       ├── services/
│       │   ├── notifications.service.ts     -- REST calls
│       │   └── websocket.service.ts         -- STOMP connection
│       ├── store/
│       │   └── notifications.store.ts       -- global SignalStore
│       └── components/
│           └── notification-toast/          -- toast component
├── pages/
│   └── notifications/
│       ├── notifications.component.ts
│       ├── notifications.component.html
│       └── notifications.component.scss
├── shared/
│   └── layout/
│       ├── header/                          -- bell icon + badge added
│       └── bottom-nav/                      -- badge on Bookings icon added
```

### Dependencies

- `@stomp/stompjs` — STOMP client (no SockJS client needed, stompjs v7+ supports native WebSocket with SockJS fallback)

### WebSocketService

- Connects to `/ws?token=xxx` on login
- Subscribes to `/user/queue/notifications`
- On each message: updates store + triggers toast
- Auto-reconnect with exponential backoff (1s, 2s, 4s, 8s... max 30s)
- Disconnects on logout

### NotificationsStore (global)

Provided at `app.config.ts` level (not component-scoped) because the badge must be visible everywhere (header + bottom nav).

```typescript
{
  notifications: NotificationResponse[],
  unreadCount: number
}
```

- `unreadCount` loaded on app init via REST `GET /api/notifications/unread/count`
- Incremented in real-time via WebSocket
- Decremented when a notification is marked as read

### Notification Toast

- Standalone component injected in `app.component.ts`
- Listens to the store for new notifications
- Slides in from top, stays 3 seconds, slides out
- X button to dismiss immediately
- Max 1 toast at a time (new replaces old)

### Navigation from Notification

1. User clicks a notification in the notifications page
2. `PATCH /api/notifications/{id}/read` → mark as read, store updates
3. `router.navigate()` based on `referenceType`:
   - `BOOKING` → `/pro/bookings?highlight={referenceId}` (or `/employee/bookings?highlight=...`)
   - `LEAVE_REQUEST` → `/pro/leaves?highlight={referenceId}` (future)
   - `POST` → `/feed?highlight={referenceId}` (future)
4. Target page reads `highlight` query param, scrolls to item, applies highlight animation

## UI Specifications

### Badge

- **Position:** top-right corner of the icon (header bell + bottom nav Bookings icon)
- **1 notification:** red dot only (12px diameter, white 2px border, shadow)
- **2+ notifications:** red badge with number (min-width 18px, font 10px bold white)
- **99+ cap:** displays "99+" for counts above 99
- **0 notifications:** badge hidden entirely

### Badge Animations

- **Appear:** scale 0 → 1 with slight bounce (200ms)
- **Value change:** pulse scale 1 → 1.2 → 1 (150ms)
- **Disappear:** scale 1 → 0 (150ms)

### Toast

- **Position:** top of screen, full width with padding
- **Enter:** `translateY(-100%) → translateY(0)`, 250ms ease-out
- **Duration:** 3 seconds
- **Exit:** reverse animation, 200ms
- **Content:** title + short message + close X button
- **Stacking:** max 1 at a time, new replaces old

### Booking Highlight (after navigation)

- `scrollIntoView({ behavior: 'smooth', block: 'center' })`
- Background `#fdf2f8` fading to transparent over 2 seconds
- CSS: `transition: background-color 2s ease-out`

## Security

- **WebSocket:** JWT validated on STOMP CONNECT. Subscriptions scoped to authenticated Principal. No cross-user access possible.
- **REST:** Same `JwtAuthenticationFilter` as existing endpoints. Each request verifies `notification.recipientId == currentUser.id`.
- **Multi-tenancy:** Notifications persisted via `ApplicationSchemaExecutor` in shared schema. `tenant_slug` stored for context only.
- **Content safety:** Notification messages are server-generated, never from raw user input. No injection risk.
- **JWT in WS query param:** In-memory only, not logged by default.

## Reconnection Strategy

- Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s (cap)
- Silent reconnection (no error toast)
- On reconnect after prolonged disconnection (>30s): REST call `GET /api/notifications/unread/count` to resync badge

## Out of Scope (for now)

- Push notifications (mobile/browser)
- Notification preferences/settings (mute, categories)
- Email notification for new types (only booking emails exist today)
- Batch mark-all-as-read
- Notification deletion
