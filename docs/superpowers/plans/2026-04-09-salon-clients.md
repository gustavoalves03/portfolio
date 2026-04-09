# Salon Clients Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable PROs to create/search salon clients manually, use a 3-step booking stepper (care → datetime → client), detect phone matches when clients register, and send birthday notifications.

**Architecture:** New `SALON_CLIENTS` tenant-scoped table as the central client reference. New `salon_client_id` column on `CARE_BOOKINGS`. Phone matching on user registration triggers WebSocket notifications. Daily `@Scheduled` job for birthday reminders. Frontend stepper replaces the current booking creation modal.

**Tech Stack:** Spring Boot 3.5 JPA + Oracle/H2, Angular 20 standalone + Material Stepper + Signals.

**Spec:** `docs/superpowers/specs/2026-04-09-salon-clients-design.md`

**Important codebase context:**
- Multi-tenant: tenant tables live in per-tenant Oracle schemas, shared tables (USERS, TENANTS) in the application schema. Use `ApplicationSchemaExecutor` to cross schemas.
- `TenantSchemaManager.java` handles schema provisioning (Oracle + H2 sections — both must be updated).
- `TenantSchemaMigrator` runs `schemaManager.migrateSchema()` on startup for all existing tenants.
- The `migrateSchema()` method has `newTables[]` (CREATE TABLE, idempotent via ORA-955/IF NOT EXISTS) and `alterStatements[]` (ALTER TABLE, idempotent via ORA-1430/IF NOT EXISTS).
- `CareBooking` entity has `User user` (ManyToOne) and `Long employeeId`. The `user` field is NOT NULL currently.
- `NotificationDispatcher` dispatches notifications via STOMP + persists to NOTIFICATIONS table.
- Existing enums: `NotificationType`, `NotificationCategory`, `ReferenceType` — extend these.
- Frontend booking creation is in `features/bookings/modals/create/create-booking.component.ts` — uses `ModalForm` + `DynamicForm`.
- `RegisterRequest` has `name, email, password, consent` — no phone field. `User` entity has no phone field either. Phone matching requires adding a phone field to User or matching via a separate lookup. For now, the matching happens when the PRO manually links a client OR when a phone is added to the User later (out of scope for this plan — the notification is sent when a match is found, the PRO validates manually).

---

## File Map

### Backend — New Files
| File | Responsibility |
|------|---------------|
| `tracking/domain/SalonClient.java` | JPA entity for SALON_CLIENTS |
| `tracking/repo/SalonClientRepository.java` | Spring Data JPA repository |
| `tracking/app/SalonClientService.java` | CRUD, search, link, auto-create |
| `tracking/web/SalonClientController.java` | REST endpoints for PRO |
| `tracking/web/dto/SalonClientResponse.java` | Response DTO |
| `tracking/web/dto/CreateSalonClientRequest.java` | Request DTO |
| `config/BirthdayScheduler.java` | @Scheduled daily birthday check |

### Backend — Modified Files
| File | Change |
|------|--------|
| `multitenancy/TenantSchemaManager.java` | Add SALON_CLIENTS table + ALTER CARE_BOOKINGS add salon_client_id |
| `bookings/domain/CareBooking.java` | Add `salonClientId` field |
| `bookings/app/CareBookingService.java` | Auto-create SalonClient on client booking, accept salonClientId on PRO booking |
| `bookings/web/dto/CareBookingRequest.java` | Add optional `salonClientId` field |
| `notification/domain/NotificationType.java` | Add CLIENT_ACCOUNT_MATCH, CLIENT_BIRTHDAY |
| `notification/domain/NotificationCategory.java` | Add CLIENT |
| `notification/domain/ReferenceType.java` | Add SALON_CLIENT |

### Frontend — New Files
| File | Responsibility |
|------|---------------|
| `features/salon-clients/salon-client.model.ts` | TypeScript interfaces |
| `features/salon-clients/salon-client.service.ts` | REST calls |
| `features/bookings/components/booking-stepper/booking-stepper.component.ts` | 3-step stepper orchestrator |
| `features/bookings/components/step-care/step-care.component.ts` | Step 1 |
| `features/bookings/components/step-datetime/step-datetime.component.ts` | Step 2 |
| `features/bookings/components/step-client/step-client.component.ts` | Step 3 |
| `features/bookings/components/client-create-form/client-create-form.component.ts` | New client form (Design B) |

### Frontend — Modified Files
| File | Change |
|------|--------|
| `features/bookings/models/bookings.model.ts` | Add salonClientId |
| `pages/notifications/notifications.component.ts` | Handle CLIENT_ACCOUNT_MATCH |
| `public/i18n/fr.json` | Stepper + client labels |
| `public/i18n/en.json` | Same |

---

### Task 1: Backend — SalonClient entity + schema migration

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/tracking/domain/SalonClient.java`
- Modify: `backend/src/main/java/com/prettyface/app/multitenancy/TenantSchemaManager.java`
- Modify: `backend/src/main/java/com/prettyface/app/bookings/domain/CareBooking.java`

- [ ] **Step 1: Create SalonClient entity**

```java
package com.prettyface.app.tracking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "SALON_CLIENTS")
@Getter
@Setter
@NoArgsConstructor
public class SalonClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "is_manual", nullable = false)
    private boolean manual = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: Add SALON_CLIENTS table to TenantSchemaManager**

Read `TenantSchemaManager.java` fully. Add `"SALON_CLIENTS"` to the `TENANT_TABLES` list. Then add the CREATE TABLE to both Oracle and H2 `newTables[]` arrays in `migrateSchema`:

Oracle:
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
)
```

H2:
```sql
CREATE TABLE IF NOT EXISTS SALON_CLIENTS (
    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    NAME VARCHAR(255) NOT NULL,
    PHONE VARCHAR(20) NOT NULL,
    EMAIL VARCHAR(255),
    DATE_OF_BIRTH DATE,
    NOTES VARCHAR(500),
    USER_ID BIGINT,
    IS_MANUAL BOOLEAN DEFAULT TRUE NOT NULL,
    CREATED_AT TIMESTAMP NOT NULL,
    CREATED_BY BIGINT
)
```

Also add to BOTH `alterStatements[]` arrays:

Oracle: `"ALTER TABLE CARE_BOOKINGS ADD (SALON_CLIENT_ID NUMBER(19))"`
H2: `"ALTER TABLE CARE_BOOKINGS ADD COLUMN IF NOT EXISTS SALON_CLIENT_ID BIGINT"`

Also add the table to the provisioning DDL sections (the `provisionSchema` method has separate Oracle and H2 provisioning — add SALON_CLIENTS there too). And add `"SALON_CLIENTS"` before `"CLIENT_PROFILES"` in the drop lists.

- [ ] **Step 3: Add salonClientId to CareBooking entity**

In `CareBooking.java`, add after the `employeeId` field:

```java
@Column(name = "salon_client_id")
private Long salonClientId;
```

- [ ] **Step 4: Verify compilation**

Run: `cd backend && ./mvnw compile -q`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tracking/domain/SalonClient.java backend/src/main/java/com/prettyface/app/multitenancy/TenantSchemaManager.java backend/src/main/java/com/prettyface/app/bookings/domain/CareBooking.java
git commit -m "feat: add SalonClient entity, schema migration, and salonClientId on CareBooking"
```

---

### Task 2: Backend — SalonClient repository, service, DTOs

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/tracking/repo/SalonClientRepository.java`
- Create: `backend/src/main/java/com/prettyface/app/tracking/web/dto/SalonClientResponse.java`
- Create: `backend/src/main/java/com/prettyface/app/tracking/web/dto/CreateSalonClientRequest.java`
- Create: `backend/src/main/java/com/prettyface/app/tracking/app/SalonClientService.java`

- [ ] **Step 1: Create SalonClientRepository**

```java
package com.prettyface.app.tracking.repo;

import com.prettyface.app.tracking.domain.SalonClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalonClientRepository extends JpaRepository<SalonClient, Long> {

    Optional<SalonClient> findByUserId(Long userId);

    List<SalonClient> findByPhoneAndManualTrueAndUserIdIsNull(String phone);

    @Query("SELECT c FROM SalonClient c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR c.phone LIKE CONCAT('%', :query, '%')")
    List<SalonClient> search(String query);

    List<SalonClient> findTop10ByOrderByCreatedAtDesc();

    @Query("SELECT c FROM SalonClient c WHERE FUNCTION('TO_CHAR', c.dateOfBirth, 'MM-DD') = :monthDay")
    List<SalonClient> findByBirthdayMonthDay(String monthDay);
}
```

- [ ] **Step 2: Create DTOs**

`SalonClientResponse.java`:
```java
package com.prettyface.app.tracking.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SalonClientResponse(
        Long id,
        String name,
        String phone,
        String email,
        LocalDate dateOfBirth,
        String notes,
        Long userId,
        boolean manual,
        LocalDateTime createdAt,
        String createdByName
) {}
```

`CreateSalonClientRequest.java`:
```java
package com.prettyface.app.tracking.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record CreateSalonClientRequest(
        @NotBlank String name,
        @NotBlank String phone,
        String email,
        LocalDate dateOfBirth,
        String notes
) {}
```

- [ ] **Step 3: Create SalonClientService**

```java
package com.prettyface.app.tracking.app;

import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.tracking.domain.SalonClient;
import com.prettyface.app.tracking.repo.SalonClientRepository;
import com.prettyface.app.tracking.web.dto.CreateSalonClientRequest;
import com.prettyface.app.tracking.web.dto.SalonClientResponse;
import com.prettyface.app.users.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class SalonClientService {

    private final SalonClientRepository salonClientRepo;
    private final UserRepository userRepository;
    private final ApplicationSchemaExecutor applicationSchemaExecutor;

    public SalonClientService(SalonClientRepository salonClientRepo,
                               UserRepository userRepository,
                               ApplicationSchemaExecutor applicationSchemaExecutor) {
        this.salonClientRepo = salonClientRepo;
        this.userRepository = userRepository;
        this.applicationSchemaExecutor = applicationSchemaExecutor;
    }

    @Transactional
    public SalonClientResponse create(CreateSalonClientRequest request, Long creatorId) {
        SalonClient client = new SalonClient();
        client.setName(request.name());
        client.setPhone(request.phone());
        client.setEmail(request.email());
        client.setDateOfBirth(request.dateOfBirth());
        client.setNotes(request.notes());
        client.setManual(true);
        client.setCreatedBy(creatorId);
        return toResponse(salonClientRepo.save(client));
    }

    @Transactional(readOnly = true)
    public List<SalonClientResponse> search(String query) {
        return salonClientRepo.search(query).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SalonClientResponse> recent() {
        return salonClientRepo.findTop10ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SalonClientResponse getById(Long id) {
        SalonClient client = salonClientRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salon client not found"));
        return toResponse(client);
    }

    @Transactional
    public SalonClientResponse linkToUser(Long salonClientId, Long userId) {
        SalonClient client = salonClientRepo.findById(salonClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salon client not found"));
        client.setUserId(userId);
        return toResponse(salonClientRepo.save(client));
    }

    @Transactional
    public SalonClient getOrCreateForUser(Long userId, String name, String phone) {
        return salonClientRepo.findByUserId(userId)
                .orElseGet(() -> {
                    SalonClient sc = new SalonClient();
                    sc.setUserId(userId);
                    sc.setName(name);
                    sc.setPhone(phone != null ? phone : "");
                    sc.setManual(false);
                    return salonClientRepo.save(sc);
                });
    }

    private SalonClientResponse toResponse(SalonClient c) {
        String createdByName = null;
        if (c.getCreatedBy() != null) {
            createdByName = applicationSchemaExecutor.call(() ->
                    userRepository.findById(c.getCreatedBy())
                            .map(u -> u.getName())
                            .orElse(null));
        }
        return new SalonClientResponse(
                c.getId(), c.getName(), c.getPhone(), c.getEmail(),
                c.getDateOfBirth(), c.getNotes(), c.getUserId(), c.isManual(),
                c.getCreatedAt(), createdByName
        );
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd backend && ./mvnw compile -q`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tracking/repo/SalonClientRepository.java backend/src/main/java/com/prettyface/app/tracking/web/dto/SalonClientResponse.java backend/src/main/java/com/prettyface/app/tracking/web/dto/CreateSalonClientRequest.java backend/src/main/java/com/prettyface/app/tracking/app/SalonClientService.java
git commit -m "feat: add SalonClient repository, service, and DTOs"
```

---

### Task 3: Backend — SalonClient controller

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/tracking/web/SalonClientController.java`

- [ ] **Step 1: Create controller**

```java
package com.prettyface.app.tracking.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.tracking.app.SalonClientService;
import com.prettyface.app.tracking.web.dto.CreateSalonClientRequest;
import com.prettyface.app.tracking.web.dto.SalonClientResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pro/clients")
public class SalonClientController {

    private final SalonClientService salonClientService;

    public SalonClientController(SalonClientService salonClientService) {
        this.salonClientService = salonClientService;
    }

    @GetMapping("/search")
    public List<SalonClientResponse> search(@RequestParam String q) {
        return salonClientService.search(q);
    }

    @GetMapping("/recent")
    public List<SalonClientResponse> recent() {
        return salonClientService.recent();
    }

    @GetMapping("/{id}")
    public SalonClientResponse getById(@PathVariable Long id) {
        return salonClientService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalonClientResponse create(
            @Valid @RequestBody CreateSalonClientRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return salonClientService.create(request, principal.getId());
    }

    @PostMapping("/{salonClientId}/link/{userId}")
    public SalonClientResponse link(
            @PathVariable Long salonClientId,
            @PathVariable Long userId) {
        return salonClientService.linkToUser(salonClientId, userId);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && ./mvnw compile -q`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tracking/web/SalonClientController.java
git commit -m "feat: add SalonClientController REST endpoints"
```

---

### Task 4: Backend — Extend notification enums + wire SalonClient into booking

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/notification/domain/NotificationType.java`
- Modify: `backend/src/main/java/com/prettyface/app/notification/domain/NotificationCategory.java`
- Modify: `backend/src/main/java/com/prettyface/app/notification/domain/ReferenceType.java`
- Modify: `backend/src/main/java/com/prettyface/app/bookings/app/CareBookingService.java`
- Modify: `backend/src/main/java/com/prettyface/app/bookings/web/dto/CareBookingRequest.java`

- [ ] **Step 1: Extend enums**

In `NotificationType.java`, add `CLIENT_ACCOUNT_MATCH` and `CLIENT_BIRTHDAY` to the enum values.

In `NotificationCategory.java`, add `CLIENT`.

In `ReferenceType.java`, add `SALON_CLIENT`.

- [ ] **Step 2: Add optional salonClientId to CareBookingRequest**

Read `CareBookingRequest.java`. Add `Long salonClientId` as an optional (non-@NotNull) field:

```java
public record CareBookingRequest(
        @NotNull Long userId,
        @NotNull Long careId,
        @NotNull @Min(1) Integer quantity,
        @NotNull LocalDate appointmentDate,
        @NotNull LocalTime appointmentTime,
        @NotNull CareBookingStatus status,
        Long salonClientId
) {}
```

- [ ] **Step 3: Wire SalonClient auto-creation into CareBookingService.createClientBooking()**

Read `CareBookingService.java`. Inject `SalonClientService`. In `createClientBooking()`, after the booking is saved and before the notification dispatch, add:

```java
// Auto-create/find SalonClient for this user
SalonClient salonClient = salonClientService.getOrCreateForUser(client.getId(), client.getName(), null);
booking.setSalonClientId(salonClient.getId());
repo.save(booking);
```

Also in the PRO `create()` method, if `request.salonClientId()` is present, set it on the booking:
```java
if (request.salonClientId() != null) {
    booking.setSalonClientId(request.salonClientId());
}
```

- [ ] **Step 4: Verify compilation and tests**

Run: `cd backend && ./mvnw compile -q && ./mvnw test -q 2>&1 | tail -3`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/notification/domain/ backend/src/main/java/com/prettyface/app/bookings/
git commit -m "feat: extend notification enums and wire SalonClient into booking flow"
```

---

### Task 5: Backend — Birthday scheduler

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/config/BirthdayScheduler.java`

- [ ] **Step 1: Create BirthdayScheduler**

This is a `@Scheduled` job that runs daily. It must iterate all tenants and check for birthdays in each tenant's schema.

```java
package com.prettyface.app.config;

import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.notification.app.NotificationDispatcher;
import com.prettyface.app.notification.domain.NotificationCategory;
import com.prettyface.app.notification.domain.NotificationType;
import com.prettyface.app.notification.domain.ReferenceType;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.tracking.domain.SalonClient;
import com.prettyface.app.tracking.repo.SalonClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class BirthdayScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BirthdayScheduler.class);

    private final TenantRepository tenantRepository;
    private final SalonClientRepository salonClientRepository;
    private final NotificationDispatcher notificationDispatcher;

    public BirthdayScheduler(TenantRepository tenantRepository,
                              SalonClientRepository salonClientRepository,
                              NotificationDispatcher notificationDispatcher) {
        this.tenantRepository = tenantRepository;
        this.salonClientRepository = salonClientRepository;
        this.notificationDispatcher = notificationDispatcher;
    }

    @Scheduled(cron = "0 0 8 * * *") // Every day at 8:00 AM
    public void checkBirthdays() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd"));
        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            try {
                TenantContext.setCurrentTenant(tenant.getSlug());
                List<SalonClient> birthdayClients = salonClientRepository.findByBirthdayMonthDay(today);

                for (SalonClient client : birthdayClients) {
                    notificationDispatcher.dispatch(
                            List.of(tenant.getOwnerId()),
                            tenant.getSlug(),
                            NotificationType.CLIENT_BIRTHDAY,
                            NotificationCategory.CLIENT,
                            client.getName() + " fête son anniversaire !",
                            client.getName() + " fête son anniversaire aujourd'hui !",
                            client.getId(),
                            ReferenceType.SALON_CLIENT
                    );
                    logger.info("Birthday notification sent for {} in tenant {}", client.getName(), tenant.getSlug());
                }
            } catch (Exception e) {
                logger.error("Birthday check failed for tenant {}: {}", tenant.getSlug(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }
}
```

Note: Make sure `@EnableScheduling` is present on the main application class or a config class. Read the main app class to check. If not present, add it.

- [ ] **Step 2: Verify compilation**

Run: `cd backend && ./mvnw compile -q`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/config/BirthdayScheduler.java
git commit -m "feat: add daily birthday notification scheduler"
```

---

### Task 6: Backend — Run all tests

- [ ] **Step 1: Run tests**

Run: `cd backend && ./mvnw test 2>&1 | grep -E "Tests run:.*Failures|BUILD"`

Fix any failures. The `SalonClientRepository.findByBirthdayMonthDay` query uses Oracle's `TO_CHAR` function which won't work on H2. If tests fail because of this, change the query to use a more portable approach:

```java
@Query("SELECT c FROM SalonClient c WHERE MONTH(c.dateOfBirth) = :month AND DAY(c.dateOfBirth) = :day")
List<SalonClient> findByBirthdayMonthAndDay(int month, int day);
```

And update `BirthdayScheduler` to call with `LocalDate.now().getMonthValue()` and `LocalDate.now().getDayOfMonth()`.

- [ ] **Step 2: Commit if changes were needed**

```bash
git commit -am "fix: make birthday query portable for H2 tests"
```

---

### Task 7: Frontend — SalonClient model + service

**Files:**
- Create: `frontend/src/app/features/salon-clients/salon-client.model.ts`
- Create: `frontend/src/app/features/salon-clients/salon-client.service.ts`

- [ ] **Step 1: Create model**

```typescript
export interface SalonClientResponse {
  id: number;
  name: string;
  phone: string;
  email: string | null;
  dateOfBirth: string | null;
  notes: string | null;
  userId: number | null;
  manual: boolean;
  createdAt: string;
  createdByName: string | null;
}

export interface CreateSalonClientRequest {
  name: string;
  phone: string;
  email: string | null;
  dateOfBirth: string | null;
  notes: string | null;
}
```

- [ ] **Step 2: Create service**

```typescript
import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { SalonClientResponse, CreateSalonClientRequest } from './salon-client.model';

@Injectable({ providedIn: 'root' })
export class SalonClientService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  search(query: string): Observable<SalonClientResponse[]> {
    const params = new HttpParams().set('q', query);
    return this.http.get<SalonClientResponse[]>(`${this.apiBaseUrl}/api/pro/clients/search`, { params });
  }

  recent(): Observable<SalonClientResponse[]> {
    return this.http.get<SalonClientResponse[]>(`${this.apiBaseUrl}/api/pro/clients/recent`);
  }

  create(request: CreateSalonClientRequest): Observable<SalonClientResponse> {
    return this.http.post<SalonClientResponse>(`${this.apiBaseUrl}/api/pro/clients`, request);
  }

  link(salonClientId: number, userId: number): Observable<SalonClientResponse> {
    return this.http.post<SalonClientResponse>(`${this.apiBaseUrl}/api/pro/clients/${salonClientId}/link/${userId}`, {});
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/salon-clients/
git commit -m "feat: add SalonClient model and service"
```

---

### Task 8: Frontend — Booking stepper component (orchestrator)

**Files:**
- Create: `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts`

- [ ] **Step 1: Create the stepper orchestrator**

This replaces the current `CreateBookingComponent` modal. It's a standalone component with 3 steps managed by signals. Read the current `create-booking.component.ts` to understand what data it collects, then build the stepper.

The stepper manages:
- `currentStep = signal(1)` (1, 2, or 3)
- `selectedCareId = signal<number | null>(null)`
- `selectedEmployeeId = signal<number | null>(null)`
- `selectedDate = signal<LocalDate | null>(null)`
- `selectedTime = signal<string | null>(null)`
- `selectedSalonClientId = signal<number | null>(null)`

Template: progress indicator (3 circles) + `@switch (currentStep())` rendering the appropriate step sub-component. Each step emits data via `output()`, the stepper stores it and advances.

On final confirm: calls the booking creation API with all collected data including `salonClientId`, then closes the dialog.

Use `MatDialogRef` since it replaces the current modal. Import all step sub-components.

Style: `:host { background: #f5f4f2; }`, progress circles colored `#c06` when completed.

The engineer should read the existing `create-booking.component.ts` fully to understand the current API call pattern (it uses `BookingsStore.createBooking()`), and replicate that with the additional `salonClientId` field.

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/features/bookings/components/booking-stepper/
git commit -m "feat: add booking stepper orchestrator component"
```

---

### Task 9: Frontend — Step 1 (Care) + Step 2 (DateTime) components

**Files:**
- Create: `frontend/src/app/features/bookings/components/step-care/step-care.component.ts`
- Create: `frontend/src/app/features/bookings/components/step-datetime/step-datetime.component.ts`

- [ ] **Step 1: Create step-care component**

Inputs: `cares` (Care[]), `employees` (Employee[]).
Outputs: `next` (EventEmitter with `{ careId, employeeId }`).

Shows care selection (list/select) and optional employee selection. "Suivant" button.

Extract the care and employee fields from the existing `create-booking.component.ts` form config.

- [ ] **Step 2: Create step-datetime component**

Inputs: `careId` (number).
Outputs: `next` (EventEmitter with `{ date, time }`), `back` (EventEmitter).

Shows date picker + time slot selection. Extract from existing form config.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/bookings/components/step-care/ frontend/src/app/features/bookings/components/step-datetime/
git commit -m "feat: add step-care and step-datetime booking components"
```

---

### Task 10: Frontend — Step 3 (Client) + client create form

**Files:**
- Create: `frontend/src/app/features/bookings/components/step-client/step-client.component.ts`
- Create: `frontend/src/app/features/bookings/components/client-create-form/client-create-form.component.ts`

- [ ] **Step 1: Create step-client component**

Two cards: "Client existant" with autocomplete search, "Nouveau client" that toggles the create form.

Inputs: none (uses `SalonClientService` internally).
Outputs: `confirm` (EventEmitter with `{ salonClientId }`), `back` (EventEmitter).

Shows search field, recent clients list, results. Selection sets `salonClientId`. "Confirmer le rendez-vous" button.

When "Nouveau client" is clicked, show the `client-create-form` inline. After creation, auto-select the new client.

Style: Design C stepper from the mockup — two big choice cards with borders.

- [ ] **Step 2: Create client-create-form component (Design B)**

Inputs: none.
Outputs: `created` (EventEmitter with `SalonClientResponse`), `cancel` (EventEmitter).

Shows:
- Live avatar preview (initials from name input)
- 2-column grid: Name* + Phone*
- Email (full width, optional)
- Date of Birth (optional, date picker)
- Notes (optional, textarea)
- Recap line of the booking below
- "Créer le client et confirmer" button

Calls `SalonClientService.create()` on submit.

Style: white card, `border-radius: 14px`, avatar circle `#c06` with white initials updating live.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/bookings/components/step-client/ frontend/src/app/features/bookings/components/client-create-form/
git commit -m "feat: add step-client and client-create-form components"
```

---

### Task 11: Frontend — Wire stepper into booking creation flow

**Files:**
- Modify: The component that opens the create booking modal (read to find it — likely in `pro-bookings.component.ts` or `bookings.component.ts`)
- Modify: `frontend/src/app/features/bookings/models/bookings.model.ts`

- [ ] **Step 1: Add salonClientId to booking model**

In `bookings.model.ts`, add `salonClientId?: number` to `CreateCareBookingRequest`.

- [ ] **Step 2: Replace modal opening**

Find where `CreateBookingComponent` is opened via `MatDialog.open()`. Replace it with `BookingStepperComponent`. The stepper should be a full-screen dialog or a large dialog.

- [ ] **Step 3: Verify build**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/bookings/ frontend/src/app/pages/pro/
git commit -m "feat: replace booking modal with stepper flow"
```

---

### Task 12: Frontend — Handle CLIENT_ACCOUNT_MATCH notification

**Files:**
- Modify: `frontend/src/app/pages/notifications/notifications.component.ts`
- Modify: `frontend/src/app/pages/notifications/notifications.component.html`

- [ ] **Step 1: Add "Associer" button for CLIENT_ACCOUNT_MATCH notifications**

In the template, inside the notification card, add a condition:

```html
@if (notif.type === 'CLIENT_ACCOUNT_MATCH' && !notif.read) {
  <button class="link-btn" (click)="onLinkClient(notif); $event.stopPropagation()">
    Associer
  </button>
}
```

In the component, add:
```typescript
private readonly salonClientService = inject(SalonClientService);

onLinkClient(notif: NotificationResponse): void {
  // referenceId = salonClientId, extract userId from message or use a dedicated field
  // For now, mark as read — the full link flow needs the userId which isn't in the notification
  this.store.markAsRead(notif.id);
  this.router.navigate(['/pro/clients', notif.referenceId]);
}
```

Style the button: `background: #c06; color: white; border-radius: 8px; padding: 6px 14px; font-size: 12px; font-weight: 600;`

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/pages/notifications/
git commit -m "feat: handle CLIENT_ACCOUNT_MATCH notification with link action"
```

---

### Task 13: Frontend — i18n translations

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add French translations**

```json
"booking": {
  "stepper": {
    "step1": "Prestation",
    "step2": "Date et heure",
    "step3": "Client",
    "next": "Suivant",
    "back": "Retour",
    "confirm": "Confirmer le rendez-vous"
  },
  "client": {
    "existing": "Client existant",
    "existingDesc": "Rechercher dans vos clients",
    "new": "Nouveau client",
    "newDesc": "Créer une fiche avec nom + téléphone",
    "search": "Rechercher par nom ou téléphone",
    "recent": "Clients récents",
    "createAndConfirm": "Créer le client et confirmer",
    "preview": "Aperçu",
    "name": "Nom",
    "phone": "Téléphone",
    "email": "Email",
    "dateOfBirth": "Date de naissance",
    "notes": "Notes",
    "link": "Associer"
  }
},
"notifications": {
  "clientAccountMatch": "Association de compte",
  "clientBirthday": "Anniversaire"
}
```

- [ ] **Step 2: Add English translations**

Same structure with English values.

- [ ] **Step 3: Commit**

```bash
git add frontend/public/i18n/
git commit -m "feat: add booking stepper and salon client i18n translations"
```

---

### Task 14: Full integration verification

- [ ] **Step 1: Run all backend tests**

Run: `cd backend && ./mvnw test 2>&1 | grep "BUILD"`

- [ ] **Step 2: Build frontend**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`

- [ ] **Step 3: Add SSR server route if needed**

Check `app.routes.server.ts` — if any new parameterized routes were added, add them with `renderMode: RenderMode.Server`.

- [ ] **Step 4: Rebuild Docker**

Run: `docker compose --profile dev up -d --force-recreate frontend-dev`

- [ ] **Step 5: Commit any remaining changes**
