# Salon Clients — Design Spec (Sub-project B)

## Overview

Enable PROs to create client records manually (without an app account), search/select clients during booking creation, and auto-detect when a manual client creates an account. Introduces a stepper-based booking flow and birthday notifications.

## 1. Data Model — SALON_CLIENTS

New table in **tenant schema**:

```sql
CREATE TABLE SALON_CLIENTS (
    ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    NAME VARCHAR2(255 CHAR) NOT NULL,
    PHONE VARCHAR2(20 CHAR) NOT NULL,
    EMAIL VARCHAR2(255 CHAR),
    DATE_OF_BIRTH DATE,
    NOTES VARCHAR2(500 CHAR),
    USER_ID NUMBER(19),
    IS_MANUAL NUMBER(1) DEFAULT 1 NOT NULL,
    CREATED_AT TIMESTAMP NOT NULL,
    CREATED_BY NUMBER(19)
);
```

- `is_manual = true`: created by PRO manually, no app account
- `is_manual = false`: auto-created when a client with an account books via the app
- `user_id`: nullable, filled when associated with a User account (either auto-created or linked later)

`CARE_BOOKINGS` gets a new column `SALON_CLIENT_ID NUMBER(19)` (FK → SALON_CLIENTS). The existing `USER_ID` column on CARE_BOOKINGS remains for backward compatibility. `SALON_CLIENT_ID` becomes the primary client reference.

When a client with an account books via the app, the system auto-creates/finds a `SALON_CLIENTS` entry with `is_manual = false` and `user_id` set.

## 2. Booking Creation — Stepper 3 Steps

The current single-form booking modal is replaced by a stepper.

### Step 1 — Care & Employee
- Select care (list of salon services)
- Select employee (optional)
- Same fields as current form

### Step 2 — Date & Time
- Calendar + available time slots
- Same as current form

### Step 3 — Client
Two choice cards:

**"Client existant"** (search):
- Autocomplete search by name or phone
- Results show avatar (initials), name, phone, visit count
- Click to select

**"Nouveau client"** (create — Design B compact with avatar preview):
- Live avatar preview with initials
- Fields: Name* (required), Phone* (required), Email (optional), Date of Birth (optional), Notes (optional)
- 2-column grid for Name + Phone
- Recap of the booking below the form
- Button "Créer le client et confirmer"

Navigation: Next/Back buttons + 3-circle progress indicator.

## 3. Association — Account Matching

### Automatic detection
When a new user registers via `AuthController.registerClient()`:
1. Search all tenant schemas for `SALON_CLIENTS` where `phone` matches, `is_manual = true`, and `user_id IS NULL`
2. For each match, send a WebSocket notification to the tenant's PRO

### Notification
- Type: `NotificationType.CLIENT_ACCOUNT_MATCH`
- Category: `NotificationCategory.CLIENT`
- Message: "{clientName} ({phone}) vient de créer un compte. Associer ?"
- `referenceType = SALON_CLIENT`, `referenceId = salonClient.id`
- In the notifications page: show an "Associer" action button on this notification type
- Click "Associer" → `POST /api/pro/clients/{salonClientId}/link/{userId}` → sets `SALON_CLIENTS.user_id`

### Manual association
- From client profile page: "Associer à un compte" button visible when `user_id IS NULL`
- Search Users by email/name
- Select → association

## 4. Birthday Notification

- Spring `@Scheduled` job runs daily at 8:00 AM
- Queries `SALON_CLIENTS` where `date_of_birth` month+day matches today
- Sends notification to PRO per match
- Type: `NotificationType.CLIENT_BIRTHDAY`
- Category: `NotificationCategory.CLIENT`
- Message: "{clientName} fête son anniversaire aujourd'hui !"
- No automatic email/SMS to client — PRO decides how to wish

## 5. Backend Architecture

### New Files
| File | Responsibility |
|------|---------------|
| `tracking/domain/SalonClient.java` | JPA entity |
| `tracking/repo/SalonClientRepository.java` | Spring Data repo (search by name/phone) |
| `tracking/app/SalonClientService.java` | CRUD, search, link, auto-create |
| `tracking/web/SalonClientController.java` | REST endpoints |
| `tracking/web/dto/SalonClientResponse.java` | Response DTO |
| `tracking/web/dto/CreateSalonClientRequest.java` | Request DTO |
| `tracking/web/dto/SalonClientSearchResult.java` | Search result DTO |
| `config/BirthdayScheduler.java` | @Scheduled daily job |

### Modified Files
| File | Change |
|------|--------|
| `multitenancy/TenantSchemaManager.java` | Add SALON_CLIENTS table + ALTER CARE_BOOKINGS |
| `bookings/domain/CareBooking.java` | Add `salonClientId` field |
| `bookings/app/CareBookingService.java` | Auto-create SalonClient on client booking, accept salonClientId on PRO booking |
| `bookings/web/CareBookingController.java` | Accept salonClientId in PRO booking request |
| `auth/AuthController.java` | After registerClient(), search for phone matches |
| `notification/domain/NotificationType.java` | Add CLIENT_ACCOUNT_MATCH, CLIENT_BIRTHDAY |
| `notification/domain/NotificationCategory.java` | Add CLIENT |
| `notification/domain/ReferenceType.java` | Add SALON_CLIENT |

### REST Endpoints
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/pro/clients/search?q=` | Search salon clients by name or phone |
| POST | `/api/pro/clients` | Create manual salon client |
| GET | `/api/pro/clients/{id}` | Get salon client details |
| POST | `/api/pro/clients/{id}/link/{userId}` | Associate salon client with user account |
| GET | `/api/pro/clients/recent` | List recent salon clients (for stepper suggestions) |

## 6. Frontend Architecture

### New Files
| File | Responsibility |
|------|---------------|
| `features/salon-clients/salon-client.model.ts` | TypeScript interfaces |
| `features/salon-clients/salon-client.service.ts` | REST calls |
| `features/bookings/components/booking-stepper/booking-stepper.component.ts` | 3-step stepper |
| `features/bookings/components/step-care/step-care.component.ts` | Step 1: care + employee |
| `features/bookings/components/step-datetime/step-datetime.component.ts` | Step 2: date + time |
| `features/bookings/components/step-client/step-client.component.ts` | Step 3: search/create client |
| `features/bookings/components/client-create-form/client-create-form.component.ts` | Inline creation form (Design B) |

### Modified Files
| File | Change |
|------|--------|
| `features/bookings/modals/create/create-booking.component.ts` | Replace with stepper or open stepper |
| `features/tracking/tracking.model.ts` | Add SalonClient types |
| `pages/notifications/notifications.component.ts` | Handle CLIENT_ACCOUNT_MATCH with "Associer" button |
| `public/i18n/fr.json` | Stepper labels, client form labels, notification messages |
| `public/i18n/en.json` | Same in English |

## 7. Enum Updates

```java
// NotificationType — add:
CLIENT_ACCOUNT_MATCH, CLIENT_BIRTHDAY

// NotificationCategory — add:
CLIENT

// ReferenceType — add:
SALON_CLIENT
```

## Out of Scope

- SMS/email to client on birthday (PRO decides)
- Client self-management of their SalonClient record
- Merging duplicate SalonClient entries
- Bulk import of clients
