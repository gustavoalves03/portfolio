# Story 5.4 — Booking Confirmation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable clients to confirm a booking from a salon storefront, with auth gating, concurrency protection, and confirmation emails.

**Architecture:** New `POST /api/salon/{slug}/book` endpoint in `PublicSalonController` handles cross-schema user resolution then delegates to `CareBookingService`. Frontend `BookingDialogComponent` gains submission logic with auth flow (login = auto-submit, register = re-confirm). Two new async email methods with Thymeleaf templates.

**Tech Stack:** Spring Boot 3.5 (Java 21), Angular 20 (signals, standalone), Thymeleaf, Oracle DB, NgRx SignalStore patterns.

---

### Task 1: Backend DTOs — ClientBookingRequest & ClientBookingResponse

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/bookings/web/dto/ClientBookingRequest.java`
- Create: `backend/src/main/java/com/prettyface/app/bookings/web/dto/ClientBookingResponse.java`

- [ ] **Step 1: Create ClientBookingRequest**

```java
package com.prettyface.app.bookings.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ClientBookingRequest(
        @NotNull Long careId,
        @NotNull LocalDate appointmentDate,
        @NotNull String appointmentTime  // "HH:mm" format
) {}
```

- [ ] **Step 2: Create ClientBookingResponse**

```java
package com.prettyface.app.bookings.web.dto;

public record ClientBookingResponse(
        Long bookingId,
        String careName,
        Integer carePrice,
        Integer careDuration,
        String appointmentDate,
        String appointmentTime,
        String status,
        String salonName
) {}
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/bookings/web/dto/ClientBookingRequest.java backend/src/main/java/com/prettyface/app/bookings/web/dto/ClientBookingResponse.java
git commit -m "feat: add client booking DTOs for Story 5.4"
```

---

### Task 2: Add unique constraint to CareBooking entity

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/bookings/domain/CareBooking.java`

- [ ] **Step 1: Add uniqueConstraints to @Table annotation**

Replace line 16:
```java
@Table(name = "CARE_BOOKINGS")
```

With:
```java
@Table(name = "CARE_BOOKINGS", uniqueConstraints = {
        @UniqueConstraint(
                name = "UK_BOOKING_SLOT",
                columnNames = {"appointment_date", "appointment_time", "care_id"}
        )
})
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/bookings/domain/CareBooking.java
git commit -m "feat: add unique constraint on booking slot to prevent double-booking"
```

---

### Task 3: CareBookingService.createClientBooking() method

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/bookings/app/CareBookingService.java`

- [ ] **Step 1: Add new dependencies to CareBookingService**

Add to constructor injection:
- `SlotAvailabilityService slotAvailabilityService`
- `EmailService emailService`

Add imports:
```java
import com.prettyface.app.availability.app.SlotAvailabilityService;
import com.prettyface.app.bookings.web.dto.ClientBookingRequest;
import com.prettyface.app.bookings.web.dto.ClientBookingResponse;
import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.domain.CareStatus;
import com.prettyface.app.notification.app.EmailService;
import com.prettyface.app.users.domain.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalTime;
```

Updated constructor:
```java
private final CareBookingRepository repo;
private final UserRepository userRepository;
private final CareRepository careRepository;
private final SlotAvailabilityService slotAvailabilityService;
private final EmailService emailService;

public CareBookingService(CareBookingRepository repo, UserRepository userRepository,
                           CareRepository careRepository, SlotAvailabilityService slotAvailabilityService,
                           EmailService emailService) {
    this.repo = repo;
    this.userRepository = userRepository;
    this.careRepository = careRepository;
    this.slotAvailabilityService = slotAvailabilityService;
    this.emailService = emailService;
}
```

- [ ] **Step 2: Add createClientBooking method**

Add after the existing `create` method:

```java
@Transactional
public ClientBookingResponse createClientBooking(User client, User owner, String salonName,
                                                  ClientBookingRequest req) {
    Care care = careRepository.findById(req.careId())
            .filter(c -> c.getStatus() == CareStatus.ACTIVE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Care not found or inactive"));

    LocalTime time = LocalTime.parse(req.appointmentTime());

    // Re-verify slot availability (concurrency protection)
    boolean slotAvailable = slotAvailabilityService.getAvailableSlots(req.appointmentDate(), req.careId())
            .stream()
            .anyMatch(slot -> LocalTime.parse(slot.startTime()).equals(time));

    if (!slotAvailable) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot no longer available");
    }

    CareBooking booking = new CareBooking();
    booking.setUser(client);
    booking.setCare(care);
    booking.setQuantity(1);
    booking.setAppointmentDate(req.appointmentDate());
    booking.setAppointmentTime(time);
    booking.setStatus(CareBookingStatus.CONFIRMED);

    try {
        booking = repo.save(booking);
    } catch (DataIntegrityViolationException e) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot no longer available");
    }

    // Async emails (fire-and-forget)
    emailService.sendBookingConfirmationEmail(client, booking, care, salonName);
    emailService.sendNewBookingNotificationEmail(owner, booking, care, client.getName());

    return new ClientBookingResponse(
            booking.getId(),
            care.getName(),
            care.getPrice(),
            care.getDuration(),
            booking.getAppointmentDate().toString(),
            booking.getAppointmentTime().toString(),
            booking.getStatus().name(),
            salonName
    );
}
```

- [ ] **Step 3: Continue to Task 4** (compilation deferred — email methods needed first)

---

### Task 4: EmailService — booking confirmation & pro notification methods

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/notification/app/EmailService.java`
- Create: `backend/src/main/resources/templates/booking-confirmation.html`
- Create: `backend/src/main/resources/templates/booking-notification-pro.html`

- [ ] **Step 1: Add imports to EmailService**

Add at top of file:
```java
import com.prettyface.app.bookings.domain.CareBooking;
import com.prettyface.app.care.domain.Care;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
```

- [ ] **Step 2: Add sendBookingConfirmationEmail method**

Append after `sendPasswordResetEmail`:

```java
@Async
public void sendBookingConfirmationEmail(User client, CareBooking booking, Care care, String salonName) {
    try {
        String formattedDate = booking.getAppointmentDate()
                .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH));
        String formattedTime = booking.getAppointmentTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"));
        String duration = formatDuration(care.getDuration());

        Context ctx = new Context();
        ctx.setVariable("clientName", client.getName());
        ctx.setVariable("careName", care.getName());
        ctx.setVariable("appointmentDate", formattedDate);
        ctx.setVariable("appointmentTime", formattedTime);
        ctx.setVariable("duration", duration);
        ctx.setVariable("salonName", salonName);
        ctx.setVariable("bookingsUrl", frontendBaseUrl + "/bookings");

        String htmlContent = templateEngine.process("booking-confirmation", ctx);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress, fromName);
        helper.setTo(client.getEmail());
        helper.setSubject("Votre rendez-vous chez " + salonName + " est confirmé / Your appointment at " + salonName + " is confirmed");
        helper.setText(htmlContent, true);

        mailSender.send(message);
        logger.info("Booking confirmation email sent to {}", client.getEmail());

    } catch (MessagingException | java.io.UnsupportedEncodingException e) {
        logger.error("Failed to send booking confirmation email to {}: {}", client.getEmail(), e.getMessage());
    }
}
```

- [ ] **Step 3: Add sendNewBookingNotificationEmail method**

```java
@Async
public void sendNewBookingNotificationEmail(User pro, CareBooking booking, Care care, String clientName) {
    try {
        String formattedDate = booking.getAppointmentDate()
                .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH));
        String formattedTime = booking.getAppointmentTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"));
        String duration = formatDuration(care.getDuration());

        Context ctx = new Context();
        ctx.setVariable("proName", pro.getName());
        ctx.setVariable("clientName", clientName);
        ctx.setVariable("careName", care.getName());
        ctx.setVariable("appointmentDate", formattedDate);
        ctx.setVariable("appointmentTime", formattedTime);
        ctx.setVariable("duration", duration);
        ctx.setVariable("dashboardUrl", frontendBaseUrl + "/pro/dashboard");

        String htmlContent = templateEngine.process("booking-notification-pro", ctx);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress, fromName);
        helper.setTo(pro.getEmail());
        helper.setSubject("Nouveau rendez-vous — " + clientName + " / New appointment — " + clientName);
        helper.setText(htmlContent, true);

        mailSender.send(message);
        logger.info("New booking notification email sent to {}", pro.getEmail());

    } catch (MessagingException | java.io.UnsupportedEncodingException e) {
        logger.error("Failed to send booking notification email to {}: {}", pro.getEmail(), e.getMessage());
    }
}

private String formatDuration(int minutes) {
    if (minutes < 60) return minutes + " min";
    int h = minutes / 60;
    int m = minutes % 60;
    return m > 0 ? h + "h" + String.format("%02d", m) : h + "h";
}
```

- [ ] **Step 4: Create booking-confirmation.html template**

Create `backend/src/main/resources/templates/booking-confirmation.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>Confirmation de rendez-vous</title>
    <style>
        body { font-family: 'Roboto', Arial, sans-serif; background-color: #fdf6f0; margin: 0; padding: 0; }
        .container { max-width: 600px; margin: 40px auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
        .header { background-color: #e91e63; padding: 32px 40px; text-align: center; }
        .header h1 { color: #ffffff; font-size: 28px; margin: 0; font-weight: 300; letter-spacing: 2px; }
        .body { padding: 40px; color: #333333; }
        .body h2 { color: #e91e63; font-size: 22px; font-weight: 400; margin-top: 0; }
        .body p { line-height: 1.7; font-size: 15px; color: #555555; }
        .details { background: #fdf6f0; border-radius: 8px; padding: 20px; margin: 24px 0; }
        .details p { margin: 6px 0; font-size: 14px; color: #444; }
        .details strong { color: #333; }
        .cta { text-align: center; margin: 32px 0; }
        .cta a { background-color: #e91e63; color: #ffffff; padding: 14px 32px; border-radius: 50px; text-decoration: none; font-size: 15px; font-weight: 500; }
        .footer { background-color: #f9f9f9; padding: 24px 40px; text-align: center; font-size: 12px; color: #999999; }
    </style>
</head>
<body>
<div class="container">
    <div class="header">
        <h1>Pretty Face</h1>
    </div>
    <div class="body">
        <h2>Rendez-vous confirmé !</h2>
        <p>Bonjour <span th:text="${clientName}">Client</span>,</p>
        <p>Votre rendez-vous a bien été enregistré.</p>
        <div class="details">
            <p><strong>Salon :</strong> <span th:text="${salonName}">Mon Salon</span></p>
            <p><strong>Prestation :</strong> <span th:text="${careName}">Soin visage</span></p>
            <p><strong>Date :</strong> <span th:text="${appointmentDate}">lundi 1 janvier 2026</span></p>
            <p><strong>Heure :</strong> <span th:text="${appointmentTime}">10:00</span></p>
            <p><strong>Durée :</strong> <span th:text="${duration}">1h</span></p>
        </div>
        <div class="cta">
            <a th:href="${bookingsUrl}">Gérer mes rendez-vous / Manage my appointments</a>
        </div>
    </div>
    <div class="footer">
        <p>© 2026 Pretty Face — Tous droits réservés.</p>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 5: Create booking-notification-pro.html template**

Create `backend/src/main/resources/templates/booking-notification-pro.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>Nouveau rendez-vous</title>
    <style>
        body { font-family: 'Roboto', Arial, sans-serif; background-color: #fdf6f0; margin: 0; padding: 0; }
        .container { max-width: 600px; margin: 40px auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
        .header { background-color: #e91e63; padding: 32px 40px; text-align: center; }
        .header h1 { color: #ffffff; font-size: 28px; margin: 0; font-weight: 300; letter-spacing: 2px; }
        .body { padding: 40px; color: #333333; }
        .body h2 { color: #e91e63; font-size: 22px; font-weight: 400; margin-top: 0; }
        .body p { line-height: 1.7; font-size: 15px; color: #555555; }
        .details { background: #fdf6f0; border-radius: 8px; padding: 20px; margin: 24px 0; }
        .details p { margin: 6px 0; font-size: 14px; color: #444; }
        .details strong { color: #333; }
        .cta { text-align: center; margin: 32px 0; }
        .cta a { background-color: #e91e63; color: #ffffff; padding: 14px 32px; border-radius: 50px; text-decoration: none; font-size: 15px; font-weight: 500; }
        .footer { background-color: #f9f9f9; padding: 24px 40px; text-align: center; font-size: 12px; color: #999999; }
    </style>
</head>
<body>
<div class="container">
    <div class="header">
        <h1>Pretty Face</h1>
    </div>
    <div class="body">
        <h2>Nouveau rendez-vous !</h2>
        <p>Bonjour <span th:text="${proName}">Pro</span>,</p>
        <p>Un nouveau rendez-vous vient d'être confirmé.</p>
        <div class="details">
            <p><strong>Client :</strong> <span th:text="${clientName}">Marie Leroy</span></p>
            <p><strong>Prestation :</strong> <span th:text="${careName}">Soin visage</span></p>
            <p><strong>Date :</strong> <span th:text="${appointmentDate}">lundi 1 janvier 2026</span></p>
            <p><strong>Heure :</strong> <span th:text="${appointmentTime}">10:00</span></p>
            <p><strong>Durée :</strong> <span th:text="${duration}">1h</span></p>
        </div>
        <div class="cta">
            <a th:href="${dashboardUrl}">Voir mon tableau de bord / View my dashboard</a>
        </div>
    </div>
    <div class="footer">
        <p>© 2026 Pretty Face — Tous droits réservés.</p>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 6: Verify compilation**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/bookings/app/CareBookingService.java backend/src/main/java/com/prettyface/app/notification/app/EmailService.java backend/src/main/resources/templates/booking-confirmation.html backend/src/main/resources/templates/booking-notification-pro.html
git commit -m "feat: add client booking service method with slot validation and email notifications"
```

---

### Task 5: PublicSalonController — POST /{slug}/book endpoint

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java`
- Modify: `backend/src/main/java/com/prettyface/app/config/SecurityConfig.java`

- [ ] **Step 1: Add new dependencies to PublicSalonController**

Add to imports:
```java
import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.bookings.app.CareBookingService;
import com.prettyface.app.bookings.web.dto.ClientBookingRequest;
import com.prettyface.app.bookings.web.dto.ClientBookingResponse;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
```

Add `CareBookingService` and `UserRepository` to constructor:

```java
private final TenantService tenantService;
private final CategoryRepository categoryRepository;
private final AvailabilityService availabilityService;
private final BlockedSlotService blockedSlotService;
private final SlotAvailabilityService slotAvailabilityService;
private final CareBookingService careBookingService;
private final UserRepository userRepository;

public PublicSalonController(TenantService tenantService, CategoryRepository categoryRepository,
                             AvailabilityService availabilityService, BlockedSlotService blockedSlotService,
                             SlotAvailabilityService slotAvailabilityService,
                             CareBookingService careBookingService, UserRepository userRepository) {
    this.tenantService = tenantService;
    this.categoryRepository = categoryRepository;
    this.availabilityService = availabilityService;
    this.blockedSlotService = blockedSlotService;
    this.slotAvailabilityService = slotAvailabilityService;
    this.careBookingService = careBookingService;
    this.userRepository = userRepository;
}
```

- [ ] **Step 2: Add the book endpoint**

Add after the `getAvailableSlots` method:

Note: No `@Transactional` on this method — the service's `@Transactional` is the boundary, and TenantContext must be set before the transaction opens.

```java
@PostMapping("/{slug}/book")
@ResponseStatus(HttpStatus.CREATED)
public ClientBookingResponse book(@PathVariable String slug,
                                   @Valid @RequestBody ClientBookingRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
    var tenant = tenantService.findBySlug(slug)
            .filter(t -> t.getStatus() == TenantStatus.ACTIVE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salon not found"));

    // Resolve users from public schema BEFORE setting tenant context
    User client = userRepository.findById(principal.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    User owner = userRepository.findById(tenant.getOwnerId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salon owner not found"));
    String salonName = tenant.getName();

    TenantContext.setCurrentTenant(slug);
    try {
        return careBookingService.createClientBooking(client, owner, salonName, request);
    } finally {
        TenantContext.clear();
    }
}
```

- [ ] **Step 3: Add security rule for POST /api/salon/*/book**

In `backend/src/main/java/com/prettyface/app/config/SecurityConfig.java`, add this line BEFORE the existing `GET /api/salon/**` permitAll rule (before line 144):

```java
.requestMatchers(HttpMethod.POST, "/api/salon/*/book").authenticated()
```

So lines 143-145 become:
```java
.requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll() // Public categories
.requestMatchers(HttpMethod.POST, "/api/salon/*/book").authenticated() // Client booking
.requestMatchers(HttpMethod.GET, "/api/salon/**").permitAll() // Public salon storefront
```

- [ ] **Step 4: Verify compilation**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java backend/src/main/java/com/prettyface/app/config/SecurityConfig.java
git commit -m "feat: add POST /api/salon/{slug}/book endpoint with auth"
```

---

### Task 6: Frontend — models and service method

**Files:**
- Modify: `frontend/src/app/features/salon-profile/models/salon-profile.model.ts`
- Modify: `frontend/src/app/features/salon-profile/services/salon-profile.service.ts`

- [ ] **Step 1: Add client booking interfaces to salon-profile.model.ts**

Append at end of file:

```typescript
export interface ClientBookingRequest {
  careId: number;
  appointmentDate: string;  // "yyyy-MM-dd"
  appointmentTime: string;  // "HH:mm"
}

export interface ClientBookingResponse {
  bookingId: number;
  careName: string;
  carePrice: number;
  careDuration: number;
  appointmentDate: string;
  appointmentTime: string;
  status: string;
  salonName: string;
}
```

- [ ] **Step 2: Add createBooking method to SalonProfileService**

Add import:
```typescript
import { TenantResponse, UpdateTenantRequest, PublicSalonResponse, TimeSlot, ClientBookingRequest, ClientBookingResponse } from '../models/salon-profile.model';
```

Add method after `getPublicSalon`:

```typescript
createBooking(slug: string, request: ClientBookingRequest): Observable<ClientBookingResponse> {
  return this.http.post<ClientBookingResponse>(
    `${this.baseUrl}/api/salon/${slug}/book`, request
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/salon-profile/models/salon-profile.model.ts frontend/src/app/features/salon-profile/services/salon-profile.service.ts
git commit -m "feat: add client booking request/response models and service method"
```

---

### Task 7: Frontend — AuthModalComponent result type change

**Files:**
- Modify: `frontend/src/app/shared/modals/auth-modal/auth-modal.component.ts`

- [ ] **Step 1: Add AuthModalResult interface and update close calls**

Add interface export at top of file (after imports):

```typescript
export interface AuthModalResult {
  authenticated: boolean;
  action: 'login' | 'register';
}
```

In `onLogin()`, change `this.dialogRef.close(true)` to:
```typescript
this.dialogRef.close({ authenticated: true, action: 'login' } as AuthModalResult);
```

In `onRegister()`, change `this.dialogRef.close(true)` to:
```typescript
this.dialogRef.close({ authenticated: true, action: 'register' } as AuthModalResult);
```

**Verification:** Only `salon-page.component.ts` consumes `AuthModalComponent`'s result, and that consumer is removed in Task 9. The separate `login-modal.component.ts` is a different component — not affected.

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/shared/modals/auth-modal/auth-modal.component.ts
git commit -m "feat: return AuthModalResult with action type from auth modal"
```

---

### Task 8: Frontend — BookingDialogComponent confirmation flow

**Files:**
- Modify: `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.ts`
- Modify: `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.html`

- [ ] **Step 1: Update BookingDialogComponent TypeScript**

Remove the `BookingDialogResult` interface export (lines 15-19) — it is no longer used since the dialog now submits directly instead of returning data.

Add imports:
```typescript
import { MatDialog } from '@angular/material/dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthModalComponent, AuthModalResult } from '../../../shared/modals/auth-modal/auth-modal.component';
import { ClientBookingRequest } from '../../../features/salon-profile/models/salon-profile.model';
```

Add new injectables:
```typescript
private readonly authService = inject(AuthService);
private readonly matDialog = inject(MatDialog);
```

Add new signals:
```typescript
readonly submitting = signal(false);
readonly bookingSuccess = signal(false);
readonly bookingError = signal<string | null>(null);
readonly registerJustCompleted = signal(false);
```

Replace the existing `confirm()` method with:

```typescript
confirm(): void {
  const date = this.selectedDate();
  const slot = this.selectedSlot();
  if (!date || !slot) return;

  if (!this.authService.isAuthenticated()) {
    this.openAuthAndMaybeSubmit();
    return;
  }

  this.submitBooking();
}

private openAuthAndMaybeSubmit(): void {
  const authRef = this.matDialog.open(AuthModalComponent, { width: '480px' });
  authRef.afterClosed().subscribe((result: AuthModalResult) => {
    if (!result?.authenticated) return;
    if (result.action === 'login') {
      this.submitBooking();
    } else {
      // Register: keep slot selected, user re-clicks Confirm
      this.registerJustCompleted.set(true);
    }
  });
}

private submitBooking(): void {
  this.submitting.set(true);
  this.bookingError.set(null);
  this.registerJustCompleted.set(false);

  const request: ClientBookingRequest = {
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
        this.loadSlots(this.selectedDate()!);
        this.selectedSlot.set(null);
      } else {
        this.bookingError.set('booking.errors.generic');
      }
    },
  });
}
```

Remove the old `confirm()` that called `this.dialogRef.close(result)`.

- [ ] **Step 2: Update BookingDialogComponent template**

Replace the entire template content of `booking-dialog.component.html` with:

```html
<div class="booking-dialog">
  <button mat-icon-button class="close-btn" (click)="close()">
    <mat-icon>close</mat-icon>
  </button>

  @if (bookingSuccess()) {
    <!-- Success view -->
    <div class="success-view">
      <mat-icon class="success-icon">check_circle</mat-icon>
      <h2 class="success-title">{{ 'booking.confirm.success' | transloco }}</h2>
      <div class="success-details">
        <p class="success-care">{{ care.name }}</p>
        <p class="success-datetime">
          {{ selectedDate()!.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' }) }}
          — {{ selectedSlot()!.startTime }}
        </p>
      </div>
      <button mat-flat-button class="confirm-btn" (click)="close()">
        {{ 'booking.confirm.close' | transloco }}
      </button>
    </div>
  } @else {
    <!-- Booking flow -->
    <h2 class="dialog-title">{{ 'booking.dialog.title' | transloco }}</h2>

    <!-- Care summary -->
    <div class="care-summary">
      <span class="care-name">{{ care.name }}</span>
      <span class="care-meta">{{ formatDuration(care.duration) }}</span>
    </div>

    <!-- Calendar -->
    <div class="month-nav">
      <button type="button" class="nav-btn" (click)="prevMonth()">
        <mat-icon>chevron_left</mat-icon>
      </button>
      <span class="month-label">{{ monthLabel() }}</span>
      <button type="button" class="nav-btn" (click)="nextMonth()">
        <mat-icon>chevron_right</mat-icon>
      </button>
    </div>

    <div class="calendar-grid">
      @for (label of weekDayLabels; track label) {
        <div class="weekday-header">{{ label }}</div>
      }
      @for (day of calendarDays(); track day.date.getTime()) {
        <button
          type="button"
          class="day-cell"
          [class.other-month]="!day.isCurrentMonth"
          [class.today]="day.isToday"
          [class.past]="day.isPast && day.isCurrentMonth"
          [class.selected]="isSelectedDate(day.date)"
          [disabled]="!day.isCurrentMonth || day.isPast"
          (click)="selectDay(day)"
        >
          {{ day.dayOfMonth }}
        </button>
      }
    </div>

    <!-- Slot selection -->
    @if (selectedDate()) {
      <div class="slots-section">
        <h3 class="slots-title">
          {{ selectedDate()!.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' }) }}
        </h3>

        @if (loadingSlots()) {
          <div class="slots-loading">
            <mat-spinner diameter="24"></mat-spinner>
          </div>
        } @else if (slots().length === 0) {
          <p class="no-slots">{{ 'booking.dialog.noSlots' | transloco }}</p>
        } @else {
          <div class="slots-grid">
            @for (slot of slots(); track slot.startTime) {
              <button
                type="button"
                class="slot-btn"
                [class.selected]="selectedSlot()?.startTime === slot.startTime"
                (click)="selectSlot(slot)"
              >
                {{ slot.startTime }}
              </button>
            }
          </div>
        }
      </div>
    }

    <!-- Error message -->
    @if (bookingError()) {
      <p class="booking-error">{{ bookingError()! | transloco }}</p>
    }

    <!-- Register success hint -->
    @if (registerJustCompleted()) {
      <p class="register-hint">{{ 'booking.confirm.registerDone' | transloco }}</p>
    }

    <!-- Confirm button -->
    @if (selectedSlot()) {
      <button
        mat-flat-button
        class="confirm-btn"
        [disabled]="submitting()"
        (click)="confirm()"
      >
        @if (submitting()) {
          <mat-spinner diameter="20"></mat-spinner>
        } @else {
          {{ 'booking.dialog.confirm' | transloco }} — {{ selectedSlot()!.startTime }}
        }
      </button>
    }
  }
</div>
```

- [ ] **Step 3: Add styles for new elements**

Append to `booking-dialog.component.scss`:

```scss
.success-view {
  text-align: center;
  padding: 24px 0;
}

.success-icon {
  font-size: 64px;
  width: 64px;
  height: 64px;
  color: #4caf50;
}

.success-title {
  font-size: 18px;
  font-weight: 500;
  color: #333;
  margin: 16px 0 8px;
}

.success-details {
  margin: 16px 0 24px;
}

.success-care {
  font-size: 15px;
  font-weight: 500;
  color: #555;
}

.success-datetime {
  font-size: 13px;
  color: #888;
  margin-top: 4px;
}

.booking-error {
  color: #d32f2f;
  font-size: 13px;
  text-align: center;
  margin: 12px 0;
}

.register-hint {
  color: #4caf50;
  font-size: 13px;
  text-align: center;
  margin: 12px 0;
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/salon/booking-dialog/
git commit -m "feat: add booking confirmation flow with auth gating and success view"
```

---

### Task 9: Frontend — SalonPageComponent simplification

**Files:**
- Modify: `frontend/src/app/pages/salon/salon-page.component.ts`

- [ ] **Step 1: Simplify onBook method**

Replace the `onBook` method (lines 62-77) with:

```typescript
protected onBook(care: PublicCareDto): void {
  this.openBookingDialog(care);
}
```

Remove the `AuthModalComponent` import and `AuthService` import if they are no longer used elsewhere in the file. Check: `AuthService` is still imported (line 10) — remove it. `AuthModalComponent` import (line 11) — remove it.

Updated imports (remove unused):
```typescript
import { SalonProfileService } from '../../features/salon-profile/services/salon-profile.service';
import { PublicSalonResponse, PublicCareDto } from '../../features/salon-profile/models/salon-profile.model';
```

Remove `private readonly authService = inject(AuthService);` from the class.

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/pages/salon/salon-page.component.ts
git commit -m "refactor: simplify SalonPageComponent — auth gating moved to booking dialog"
```

---

### Task 10: i18n — add booking confirmation translation keys

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add keys to fr.json**

Inside the `"booking"` object (find the existing `"booking.dialog.*"` keys), add sibling keys:

```json
"confirm": {
  "success": "Votre rendez-vous est confirmé !",
  "close": "Fermer",
  "registerDone": "Compte créé ! Vous pouvez maintenant confirmer."
},
"errors": {
  "slotTaken": "Ce créneau vient d'être réservé. Veuillez en choisir un autre.",
  "generic": "Une erreur est survenue. Veuillez réessayer."
}
```

- [ ] **Step 2: Add keys to en.json**

Same structure:

```json
"confirm": {
  "success": "Your appointment is confirmed!",
  "close": "Close",
  "registerDone": "Account created! You can now confirm."
},
"errors": {
  "slotTaken": "This slot was just booked. Please choose another.",
  "generic": "An error occurred. Please try again."
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat: add booking confirmation i18n keys (fr + en)"
```

---

### Task 11: Integration test — end-to-end verification

- [ ] **Step 1: Verify backend compiles and starts**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Manual smoke test checklist**

With backend running (`mvn spring-boot:run`) and frontend running:

1. Navigate to `/salon/sophie-martin` (or the actual slug)
2. Click "Réserver" on a care
3. Booking dialog opens → select a date → select a slot
4. Click "Confirmer" without being logged in → AuthModal opens
5. Login with `marie@test.com` / `Password1!` → booking auto-submits → success view
6. Close dialog, open another care → select slot → confirm → 201 success (already logged in)
7. Try booking the exact same slot again → 409 → "Ce créneau vient d'être réservé" message → slots reload

- [ ] **Step 3: Final commit with all remaining changes**

```bash
git add -A
git status
git commit -m "feat: Story 5.4 — client booking confirmation with auth flow and emails"
```
