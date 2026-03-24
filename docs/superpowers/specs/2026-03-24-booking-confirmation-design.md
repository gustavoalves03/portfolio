# Story 5.4 — Booking Confirmation

## Overview

A client selects a care service and time slot on a salon storefront, then confirms the booking. The system creates the booking in CONFIRMED status, prevents double-booking via re-validation + DB unique constraint, and sends confirmation emails to both client and professional.

**Approach:** New `POST /api/salon/{slug}/book` endpoint in `PublicSalonController`. Frontend booking dialog gains a confirmation flow with auth gating. Two new async email methods in `EmailService` with Thymeleaf templates.

## Backend — New Endpoint

Add to existing `PublicSalonController` (`/api/salon`):

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| `POST` | `/api/salon/{slug}/book` | Authenticated (any role) | Create a confirmed booking |

### Request DTO

New record `ClientBookingRequest` in `bookings/web/dto/`:

```java
record ClientBookingRequest(
    @NotNull Long careId,
    @NotNull LocalDate appointmentDate,
    @NotNull String appointmentTime  // "HH:mm" format
) {}
```

### Response DTO

New record `ClientBookingResponse` in `bookings/web/dto/`:

```java
record ClientBookingResponse(
    Long bookingId,
    String careName,
    Integer carePrice,
    Integer careDuration,
    String appointmentDate,   // "yyyy-MM-dd"
    String appointmentTime,   // "HH:mm"
    String status,            // "CONFIRMED"
    String salonName
) {}
```

### Endpoint Logic (in `PublicSalonController`)

**Schema boundary note:** The `User` entity lives in the **public/shared schema**. The `Care` and `CareBooking` entities live in the **tenant schema**. The controller must resolve user and tenant data BEFORE setting `TenantContext`, then pass them to the service.

```
POST /{slug}/book (authenticated)
1. Resolve Tenant by slug → 404 if not found or status != ACTIVE
2. Extract authenticated userId from SecurityContext → UserPrincipal.getId()
3. Load User from public schema via userRepository.findById(userId) → 404 if not found
4. Extract salonName = tenant.getName(), ownerId = tenant.getOwnerId()
5. Load owner (pro User) from public schema via userRepository.findById(ownerId)
6. Set TenantContext.setCurrentTenant(slug) in try/finally block
7. Delegate to CareBookingService.createClientBooking(client, owner, salonName, request)
8. Return 201 Created with ClientBookingResponse
```

### New Service Method: `CareBookingService.createClientBooking()`

```
createClientBooking(User client, User owner, String salonName, ClientBookingRequest req) → ClientBookingResponse
```

The method receives already-resolved User objects from the controller (public schema), avoiding cross-schema lookups.

Steps:
1. Load Care by ID from tenant schema (404 if not found or status != ACTIVE)
2. Parse `appointmentTime` to `LocalTime`
3. Call `SlotAvailabilityService.getAvailableSlots(date, careId)` — verify the requested slot exists in the returned list. If not → throw 409 Conflict ("Slot no longer available")
4. Create `CareBooking` entity: client, care, quantity=1, date, time, status=CONFIRMED
5. `repo.save(booking)` — if `DataIntegrityViolationException` → catch and throw 409 Conflict
6. Send async emails (fire-and-forget, no tenant context needed for email sending):
   - `emailService.sendBookingConfirmationEmail(client, booking, care, salonName)`
   - `emailService.sendNewBookingNotificationEmail(owner, booking, care, client.getName())`
7. Return `ClientBookingResponse` (built from care, booking, and salonName)

### Concurrency Protection

**Double-check + unique constraint** strategy:

1. **Application-level check:** Re-verify slot availability via `SlotAvailabilityService` at booking creation time. This catches 99% of conflicts.
2. **DB-level safety net:** Add unique constraint on `CareBooking` table:

```java
@Table(name = "CARE_BOOKINGS", uniqueConstraints = {
    @UniqueConstraint(
        name = "UK_BOOKING_SLOT",
        columnNames = {"appointment_date", "appointment_time", "care_id"}
    )
})
```

Note: This constraint works because each care occupies its full duration from the start time. Two different cares can share the same date+time since they're different services at the same salon (one pro = one care at a time is handled by the availability check which considers all bookings).

**Refinement:** The unique constraint on `(date, time, care_id)` prevents exact duplicates. The broader overlap check (e.g., a 90-min care blocking the next 30-min slot) is handled by the `SlotAvailabilityService` re-verification. In the rare race condition where two requests pass the app-level check simultaneously, only the exact same start time would collide at DB level — other overlapping slots would both succeed. For a single-practitioner salon at current scale, this is acceptable; the app-level check is the primary defense.

**Known limitation / Future work:** For multi-practitioner salons or high-traffic bookings, replace the app-level check with a `SELECT ... FOR UPDATE` pessimistic lock or SERIALIZABLE transaction isolation to fully prevent overlapping-slot race conditions. Track as a tech-debt item.

### Security Config Update

Add to `SecurityConfig` the new route:

```
POST /api/salon/*/book → authenticated()
```

Place this before the catch-all `/api/**` authenticated rule. The existing `GET /api/salon/**` permitAll rule is method-scoped (GET only) and does not conflict.

## Backend — Email Templates

### Booking Confirmation (to client)

New method in `EmailService`:

```java
@Async
public void sendBookingConfirmationEmail(User client, CareBooking booking, Care care, String salonName)
```

Template: `booking-confirmation.html`

Variables: `clientName`, `careName`, `appointmentDate` (formatted "jeudi 28 mars 2026"), `appointmentTime` ("10:00"), `duration` ("1h30"), `salonName`, `bookingsUrl` (link to future "my bookings" page)

Subject: "Votre rendez-vous chez {salonName} est confirmé / Your appointment at {salonName} is confirmed"

### New Booking Notification (to pro)

New method in `EmailService`:

```java
@Async
public void sendNewBookingNotificationEmail(User pro, CareBooking booking, Care care, String clientName)
```

Template: `booking-notification-pro.html`

Variables: `proName`, `clientName`, `careName`, `appointmentDate`, `appointmentTime`, `duration`, `dashboardUrl`

Subject: "Nouveau rendez-vous — {clientName} / New appointment — {clientName}"

### Template Style

Follow the same Thymeleaf pattern as `welcome-pro.html` and `password-reset.html`. Bilingual FR/EN with simple HTML layout.

## Frontend — Booking Dialog Changes

### Current Flow (Story 5.3)

```
SalonPageComponent.onBook(care)
  ├─ Not authenticated → open AuthModal → if success → open BookingDialog
  └─ Authenticated → open BookingDialog
BookingDialog: select date → load slots → select slot → confirm → close(BookingDialogResult)
SalonPageComponent: currently ignores the result
```

### New Flow (Story 5.4)

The key change: **BookingDialogComponent handles the full booking lifecycle** including auth gating and API submission.

```
SalonPageComponent.onBook(care)
  └─ Always open BookingDialog (regardless of auth state)

BookingDialog:
  1. Select date → load slots → select slot
  2. Click "Confirm"
     ├─ Authenticated? → submit booking API call
     │   ├─ 201 → show success message → close dialog
     │   └─ 409 → show "slot taken" message → reload slots for selected date
     └─ Not authenticated? → open AuthModal
          ├─ Login success → auto-submit booking → same as above
          └─ Register success → close AuthModal, keep slot selected,
             show message "Vous pouvez maintenant confirmer"
             (client re-clicks "Confirm" manually)
```

### Auth Detection

The dialog needs to distinguish login vs registration to decide auto-submit behavior.

**Change to `AuthModalComponent`:** Close the dialog with a result object instead of just `true`:

```typescript
interface AuthModalResult {
  authenticated: boolean;
  action: 'login' | 'register';
}
```

- `onLogin()` success → `dialogRef.close({ authenticated: true, action: 'login' })`
- `onRegister()` success → `dialogRef.close({ authenticated: true, action: 'register' })`

**Note:** Verify no other component opens `AuthModalComponent` and depends on the boolean close value. If found, update those consumers.

**Known limitation:** Google OAuth login (`loginWithGoogle()`) triggers a full page redirect, losing dialog state. For MVP, this is acceptable. Future improvement: persist booking intent in localStorage before redirect.

### BookingDialogComponent Changes

New injectable dependencies (add to component):
- `MatDialog` — to open the auth modal from within the booking dialog
- `AuthService` — to check `isAuthenticated()` signal

New signals:
- `submitting = signal(false)` — loading state during booking API call
- `bookingSuccess = signal(false)` — show success view
- `bookingError = signal<string | null>(null)` — error message (slot taken, etc.)

New `confirm()` logic:

```typescript
confirm(): void {
  if (!this.selectedDate() || !this.selectedSlot()) return;

  if (!this.authService.isAuthenticated()) {
    this.openAuthAndMaybeSubmit();
    return;
  }

  this.submitBooking();
}

private openAuthAndMaybeSubmit(): void {
  const authRef = this.dialog.open(AuthModalComponent, { width: '480px' });
  authRef.afterClosed().subscribe((result: AuthModalResult) => {
    if (!result?.authenticated) return;
    if (result.action === 'login') {
      this.submitBooking();  // Auto-submit for login
    }
    // For register: do nothing — slot stays selected, user re-clicks Confirm
  });
}

private submitBooking(): void {
  this.submitting.set(true);
  this.bookingError.set(null);

  const request = {
    careId: this.care.id,
    appointmentDate: this.formatDate(this.selectedDate()!),
    appointmentTime: this.selectedSlot()!.startTime,
  };

  this.salonService.createBooking(this.slug, request).subscribe({
    next: () => {
      this.submitting.set(false);
      this.bookingSuccess.set(true);
    },
    error: (err) => {
      this.submitting.set(false);
      if (err.status === 409) {
        this.bookingError.set('booking.errors.slotTaken');
        this.reloadSlots();
      } else {
        this.bookingError.set('booking.errors.generic');
      }
    },
  });
}
```

### Success View

When `bookingSuccess()` is true, replace the calendar/slots view with:
- Checkmark icon
- "Votre rendez-vous est confirmé !" message
- Summary: care name, date, time
- "Fermer" button that closes the dialog

### SalonPageComponent Changes

Remove the auth gating from `onBook()` — the dialog handles it now:

```typescript
protected onBook(care: PublicCareDto): void {
  this.openBookingDialog(care);
}
```

### SalonProfileService Addition

New method:

```typescript
createBooking(slug: string, request: ClientBookingRequest): Observable<ClientBookingResponse> {
  return this.http.post<ClientBookingResponse>(
    `${this.apiBaseUrl}/api/salon/${slug}/book`, request
  );
}
```

## i18n Keys

Add to both `fr.json` and `en.json`:

```
booking.confirm.success       → "Votre rendez-vous est confirmé !" / "Your appointment is confirmed!"
booking.confirm.summary       → "Récapitulatif" / "Summary"
booking.confirm.close         → "Fermer" / "Close"
booking.errors.slotTaken      → "Ce créneau vient d'être réservé. Veuillez en choisir un autre." / "This slot was just booked. Please choose another."
booking.errors.generic        → "Une erreur est survenue. Veuillez réessayer." / "An error occurred. Please try again."
booking.confirm.loginRequired → "Connectez-vous pour confirmer votre rendez-vous" / "Log in to confirm your appointment"
booking.confirm.registerDone  → "Compte créé ! Vous pouvez maintenant confirmer." / "Account created! You can now confirm."
```

## Error Handling

| Scenario | HTTP Status | Frontend behavior |
|----------|-------------|-------------------|
| Care not found / inactive | 404 | Generic error message |
| Slot no longer available | 409 Conflict | Show "slot taken" message, reload slots |
| Not authenticated | 401 | Open auth modal (should not happen with current flow) |
| DB constraint violation | 409 Conflict | Same as "slot no longer available" |
| Server error | 500 | Generic error message |

## Files Changed Summary

### Backend — New Files
- `bookings/web/dto/ClientBookingRequest.java`
- `bookings/web/dto/ClientBookingResponse.java`
- `templates/booking-confirmation.html`
- `templates/booking-notification-pro.html`

### Backend — Modified Files
- `tenant/web/PublicSalonController.java` — add `POST /{slug}/book` endpoint
- `bookings/app/CareBookingService.java` — add `createClientBooking()` method
- `bookings/domain/CareBooking.java` — add unique constraint annotation
- `notification/app/EmailService.java` — add 2 email methods
- `config/SecurityConfig.java` — add POST salon/book route rule

### Frontend — Modified Files
- `pages/salon/booking-dialog/booking-dialog.component.ts` — add submission logic, auth flow, success view
- `pages/salon/booking-dialog/booking-dialog.component.html` — add success view, error messages, loading state
- `pages/salon/salon-page.component.ts` — simplify `onBook()` (remove auth gating)
- `shared/modals/auth-modal/auth-modal.component.ts` — return `AuthModalResult` with action type
- `features/salon-profile/services/salon-profile.service.ts` — add `createBooking()` method
- `features/salon-profile/models/salon-profile.model.ts` — add `ClientBookingRequest`, `ClientBookingResponse`
- `public/i18n/fr.json` — add booking confirmation keys
- `public/i18n/en.json` — add booking confirmation keys
- `app/i18n/transloco-http.loader.ts` — add SSR embedded translations
