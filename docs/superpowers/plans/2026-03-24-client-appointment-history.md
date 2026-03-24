# Story 6.1 — Client Appointment History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable clients to view all their appointments (across multiple salons) in a single page with "Upcoming" and "Past" tabs, backed by a denormalized mirror table in the public schema.

**Architecture:** New `ClientBookingHistory` entity in APPUSER schema with `@Table(schema = "APPUSER")`. Mirror row written from the controller layer after `TenantContext.clear()` (not inside the Hibernate multi-tenant transaction). New `GET /api/client/me/bookings` endpoint. Frontend replaces admin CRUD `/bookings` with a client-friendly tabbed appointment list.

**Tech Stack:** Spring Boot 3.5 (Java 21), Angular 20 (signals, standalone, NgRx SignalStore), Angular Material tabs, Transloco i18n.

---

### Task 1: Backend — ClientBookingHistory entity + repository + DTO

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/bookings/domain/ClientBookingHistory.java`
- Create: `backend/src/main/java/com/prettyface/app/bookings/repo/ClientBookingHistoryRepository.java`
- Create: `backend/src/main/java/com/prettyface/app/bookings/web/dto/ClientBookingHistoryResponse.java`

- [ ] **Step 1: Create ClientBookingHistory entity**

```java
package com.prettyface.app.bookings.domain;

import com.prettyface.app.users.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "CLIENT_BOOKING_HISTORY", schema = "APPUSER")
public class ClientBookingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tenant_slug", nullable = false, length = 100)
    private String tenantSlug;

    @Column(name = "salon_name", nullable = false)
    private String salonName;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "care_name", nullable = false)
    private String careName;

    @Column(name = "care_price", nullable = false)
    private Integer carePrice;

    @Column(name = "care_duration", nullable = false)
    private Integer careDuration;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "appointment_time", nullable = false)
    private LocalTime appointmentTime;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
```

Note: `userId` is stored as a plain Long (not a `@ManyToOne` to User) to keep this entity simple and avoid lazy loading issues. The `schema = "APPUSER"` ensures Hibernate always targets the public schema.

- [ ] **Step 2: Create ClientBookingHistoryRepository**

```java
package com.prettyface.app.bookings.repo;

import com.prettyface.app.bookings.domain.ClientBookingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClientBookingHistoryRepository extends JpaRepository<ClientBookingHistory, Long> {

    List<ClientBookingHistory> findByUserIdAndStatusAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAscAppointmentTimeAsc(
            Long userId, String status, LocalDate fromDate);

    List<ClientBookingHistory> findByUserIdAndAppointmentDateBeforeOrderByAppointmentDateDescAppointmentTimeDesc(
            Long userId, LocalDate beforeDate);

    Optional<ClientBookingHistory> findByTenantSlugAndBookingId(String tenantSlug, Long bookingId);
}
```

- [ ] **Step 3: Create ClientBookingHistoryResponse DTO**

```java
package com.prettyface.app.bookings.web.dto;

public record ClientBookingHistoryResponse(
        Long id,
        Long bookingId,
        String tenantSlug,
        String salonName,
        String careName,
        Integer carePrice,
        Integer careDuration,
        String appointmentDate,
        String appointmentTime,
        String status,
        String createdAt
) {}
```

- [ ] **Step 4: Verify compilation**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/bookings/domain/ClientBookingHistory.java backend/src/main/java/com/prettyface/app/bookings/repo/ClientBookingHistoryRepository.java backend/src/main/java/com/prettyface/app/bookings/web/dto/ClientBookingHistoryResponse.java
git commit -m "feat: add ClientBookingHistory entity, repository, and DTO"
```

---

### Task 2: Backend — ClientBookingHistoryService + mirror write in controller

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/bookings/app/ClientBookingHistoryService.java`
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java`

- [ ] **Step 1: Create ClientBookingHistoryService**

```java
package com.prettyface.app.bookings.app;

import com.prettyface.app.bookings.domain.ClientBookingHistory;
import com.prettyface.app.bookings.repo.ClientBookingHistoryRepository;
import com.prettyface.app.bookings.web.dto.ClientBookingHistoryResponse;
import com.prettyface.app.bookings.web.dto.ClientBookingResponse;
import com.prettyface.app.users.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ClientBookingHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(ClientBookingHistoryService.class);

    private final ClientBookingHistoryRepository repo;

    public ClientBookingHistoryService(ClientBookingHistoryRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void createMirror(User client, ClientBookingResponse bookingResult,
                              String tenantSlug, String salonName) {
        try {
            ClientBookingHistory history = new ClientBookingHistory();
            history.setUserId(client.getId());
            history.setTenantSlug(tenantSlug);
            history.setSalonName(salonName);
            history.setBookingId(bookingResult.bookingId());
            history.setCareName(bookingResult.careName());
            history.setCarePrice(bookingResult.carePrice());
            history.setCareDuration(bookingResult.careDuration());
            history.setAppointmentDate(LocalDate.parse(bookingResult.appointmentDate()));
            history.setAppointmentTime(java.time.LocalTime.parse(bookingResult.appointmentTime()));
            history.setStatus(bookingResult.status());
            repo.save(history);
        } catch (Exception e) {
            logger.error("Failed to create booking mirror for user {} booking {}: {}",
                    client.getId(), bookingResult.bookingId(), e.getMessage());
        }
    }

    @Transactional
    public void updateMirrorStatus(String tenantSlug, Long bookingId, String newStatus) {
        repo.findByTenantSlugAndBookingId(tenantSlug, bookingId).ifPresent(history -> {
            history.setStatus(newStatus);
            repo.save(history);
        });
    }

    @Transactional(readOnly = true)
    public List<ClientBookingHistoryResponse> getUpcoming(Long userId) {
        return repo.findByUserIdAndStatusAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAscAppointmentTimeAsc(
                userId, "CONFIRMED", LocalDate.now()
        ).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ClientBookingHistoryResponse> getPast(Long userId) {
        return repo.findByUserIdAndAppointmentDateBeforeOrderByAppointmentDateDescAppointmentTimeDesc(
                userId, LocalDate.now()
        ).stream().map(this::toResponse).toList();
    }

    private ClientBookingHistoryResponse toResponse(ClientBookingHistory h) {
        return new ClientBookingHistoryResponse(
                h.getId(),
                h.getBookingId(),
                h.getTenantSlug(),
                h.getSalonName(),
                h.getCareName(),
                h.getCarePrice(),
                h.getCareDuration(),
                h.getAppointmentDate().toString(),
                h.getAppointmentTime().toString(),
                h.getStatus(),
                h.getCreatedAt().toString()
        );
    }
}
```

- [ ] **Step 2: Update PublicSalonController.book() to write mirror**

In `PublicSalonController.java`, add `ClientBookingHistoryService` to constructor injection.

Add import:
```java
import com.prettyface.app.bookings.app.ClientBookingHistoryService;
```

Update the constructor to include `ClientBookingHistoryService clientBookingHistoryService`.

Replace the `book()` method's try/finally block to add the mirror write AFTER `TenantContext.clear()`:

```java
@PostMapping("/{slug}/book")
@ResponseStatus(HttpStatus.CREATED)
public ClientBookingResponse book(@PathVariable String slug,
                                   @Valid @RequestBody ClientBookingRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
    var tenant = tenantService.findBySlug(slug)
            .filter(t -> t.getStatus() == TenantStatus.ACTIVE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salon not found"));

    User client = userRepository.findById(principal.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    User owner = userRepository.findById(tenant.getOwnerId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salon owner not found"));
    String salonName = tenant.getName();

    ClientBookingResponse result;
    TenantContext.setCurrentTenant(slug);
    try {
        result = careBookingService.createClientBooking(client, owner, salonName, request);
    } finally {
        TenantContext.clear();
    }

    // Mirror write in public schema (after TenantContext cleared → APPUSER)
    clientBookingHistoryService.createMirror(client, result, slug, salonName);

    return result;
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/bookings/app/ClientBookingHistoryService.java backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java
git commit -m "feat: add booking mirror service and write mirror from controller"
```

---

### Task 3: Backend — ClientBookingHistoryController + SecurityConfig

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/bookings/web/ClientBookingHistoryController.java`
- Modify: `backend/src/main/java/com/prettyface/app/config/SecurityConfig.java`

- [ ] **Step 1: Create ClientBookingHistoryController**

```java
package com.prettyface.app.bookings.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.bookings.app.ClientBookingHistoryService;
import com.prettyface.app.bookings.web.dto.ClientBookingHistoryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/client/me/bookings")
public class ClientBookingHistoryController {

    private final ClientBookingHistoryService service;

    public ClientBookingHistoryController(ClientBookingHistoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<ClientBookingHistoryResponse> getMyBookings(
            @RequestParam(defaultValue = "upcoming") String tab,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal.getId();
        return "past".equals(tab)
                ? service.getPast(userId)
                : service.getUpcoming(userId);
    }
}
```

- [ ] **Step 2: Add security rule for /api/client/**

In `SecurityConfig.java`, add this line BEFORE the `/api/pro/**` rule (before line 152):

```java
.requestMatchers("/api/client/**").authenticated()
```

So the section becomes:
```java
.requestMatchers(HttpMethod.GET, "/api/public/**").permitAll() // Public discovery
.requestMatchers("/api/client/**").authenticated() // Client endpoints
// Admin-only endpoints (create, update, delete)
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/bookings/web/ClientBookingHistoryController.java backend/src/main/java/com/prettyface/app/config/SecurityConfig.java
git commit -m "feat: add GET /api/client/me/bookings endpoint with security rule"
```

---

### Task 4: Frontend — client bookings model, service, and store

**Files:**
- Create: `frontend/src/app/features/client-bookings/client-bookings.model.ts`
- Create: `frontend/src/app/features/client-bookings/client-bookings.service.ts`
- Create: `frontend/src/app/features/client-bookings/client-bookings.store.ts`

- [ ] **Step 1: Create model**

```typescript
export interface ClientBookingHistoryResponse {
  id: number;
  bookingId: number;
  tenantSlug: string;
  salonName: string;
  careName: string;
  carePrice: number;
  careDuration: number;
  appointmentDate: string;
  appointmentTime: string;
  status: string;
  createdAt: string;
}
```

- [ ] **Step 2: Create service**

```typescript
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { ClientBookingHistoryResponse } from './client-bookings.model';

@Injectable({ providedIn: 'root' })
export class ClientBookingsService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  getMyBookings(tab: 'upcoming' | 'past'): Observable<ClientBookingHistoryResponse[]> {
    return this.http.get<ClientBookingHistoryResponse[]>(
      `${this.apiBaseUrl}/api/client/me/bookings`,
      { params: { tab } }
    );
  }
}
```

- [ ] **Step 3: Create store**

```typescript
import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';
import { setError, setFulfilled, setPending, withRequestStatus } from '../../shared/features/request.status.feature';
import { ClientBookingsService } from './client-bookings.service';
import { ClientBookingHistoryResponse } from './client-bookings.model';

type ClientBookingsState = {
  upcoming: ClientBookingHistoryResponse[];
  past: ClientBookingHistoryResponse[];
};

export const ClientBookingsStore = signalStore(
  withState<ClientBookingsState>({ upcoming: [], past: [] }),
  withRequestStatus(),
  withMethods((store, service = inject(ClientBookingsService)) => ({
    loadUpcoming: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() => service.getMyBookings('upcoming')),
        tap({
          next: (upcoming) => patchState(store, { upcoming }, setFulfilled()),
          error: (err) => patchState(store, setError(err?.message ?? 'Error loading bookings')),
        })
      )
    ),
    loadPast: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() => service.getMyBookings('past')),
        tap({
          next: (past) => patchState(store, { past }, setFulfilled()),
          error: (err) => patchState(store, setError(err?.message ?? 'Error loading bookings')),
        })
      )
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.loadUpcoming();
    },
  }))
);
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/client-bookings/
git commit -m "feat: add client bookings model, service, and NgRx SignalStore"
```

---

### Task 5: Frontend — ClientBookingsComponent (page with tabs)

**Files:**
- Create: `frontend/src/app/pages/client-bookings/client-bookings.component.ts`
- Create: `frontend/src/app/pages/client-bookings/client-bookings.component.html`
- Create: `frontend/src/app/pages/client-bookings/client-bookings.component.scss`

- [ ] **Step 1: Create component TypeScript**

```typescript
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { ClientBookingsStore } from '../../features/client-bookings/client-bookings.store';

@Component({
  selector: 'app-client-bookings',
  standalone: true,
  imports: [MatTabsModule, MatButtonModule, MatProgressSpinnerModule, TranslocoPipe],
  providers: [ClientBookingsStore],
  templateUrl: './client-bookings.component.html',
  styleUrl: './client-bookings.component.scss',
})
export class ClientBookingsComponent {
  protected readonly store = inject(ClientBookingsStore);
  private readonly router = inject(Router);

  onTabChange(index: number): void {
    if (index === 1) {
      this.store.loadPast();
    }
  }

  onDiscover(): void {
    this.router.navigate(['/discover']);
  }

  onSalonClick(slug: string): void {
    this.router.navigate(['/salon', slug]);
  }

  formatDuration(minutes: number): string {
    if (minutes < 60) return `${minutes} min`;
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${h}h`;
  }

  formatPrice(cents: number): string {
    return (cents / 100).toFixed(2).replace('.', ',') + ' \u20AC';
  }

  formatDate(dateStr: string): string {
    const date = new Date(dateStr + 'T00:00:00');
    return date.toLocaleDateString('fr-FR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
  }
}
```

- [ ] **Step 2: Create template**

```html
<div class="client-bookings-page">
  <h1 class="page-title">{{ 'clientBookings.title' | transloco }}</h1>

  @if (store.isPending()) {
    <div class="loading">
      <mat-spinner diameter="32"></mat-spinner>
    </div>
  } @else {
    <mat-tab-group (selectedIndexChange)="onTabChange($event)">
      <!-- Upcoming tab -->
      <mat-tab [label]="'clientBookings.upcoming' | transloco">
        @if (store.upcoming().length > 0) {
          <div class="bookings-list">
            @for (booking of store.upcoming(); track booking.id) {
              <div class="booking-card" (click)="onSalonClick(booking.tenantSlug)">
                <div class="booking-header">
                  <span class="salon-name">{{ booking.salonName }}</span>
                  <span class="status-badge confirmed">{{ 'clientBookings.status.CONFIRMED' | transloco }}</span>
                </div>
                <div class="booking-body">
                  <p class="care-name">{{ booking.careName }}</p>
                  <p class="booking-datetime">{{ formatDate(booking.appointmentDate) }} — {{ booking.appointmentTime }}</p>
                  <p class="booking-meta">{{ formatDuration(booking.careDuration) }} · {{ formatPrice(booking.carePrice) }}</p>
                </div>
              </div>
            }
          </div>
        } @else {
          <div class="empty-state">
            <p class="empty-text">{{ 'clientBookings.emptyUpcoming' | transloco }}</p>
            <button mat-flat-button class="discover-btn" (click)="onDiscover()">
              {{ 'clientBookings.discoverCta' | transloco }}
            </button>
          </div>
        }
      </mat-tab>

      <!-- Past tab -->
      <mat-tab [label]="'clientBookings.past' | transloco">
        @if (store.past().length > 0) {
          <div class="bookings-list">
            @for (booking of store.past(); track booking.id) {
              <div class="booking-card past">
                <div class="booking-header">
                  <span class="salon-name">{{ booking.salonName }}</span>
                  <span class="status-badge" [class.confirmed]="booking.status === 'CONFIRMED'" [class.cancelled]="booking.status === 'CANCELLED'">
                    {{ 'clientBookings.status.' + booking.status | transloco }}
                  </span>
                </div>
                <div class="booking-body">
                  <p class="care-name">{{ booking.careName }}</p>
                  <p class="booking-datetime">{{ formatDate(booking.appointmentDate) }} — {{ booking.appointmentTime }}</p>
                  <p class="booking-meta">{{ formatDuration(booking.careDuration) }} · {{ formatPrice(booking.carePrice) }}</p>
                </div>
              </div>
            }
          </div>
        } @else {
          <div class="empty-state">
            <p class="empty-text">{{ 'clientBookings.emptyPast' | transloco }}</p>
          </div>
        }
      </mat-tab>
    </mat-tab-group>
  }
</div>
```

- [ ] **Step 3: Create styles**

```scss
.client-bookings-page {
  max-width: 700px;
  margin: 0 auto;
  padding: 32px 24px;
}

.page-title {
  font-size: 1.25rem;
  font-weight: 400;
  color: #333;
  margin-bottom: 24px;
}

.loading {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.bookings-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 0;
}

.booking-card {
  background: white;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: box-shadow 150ms ease;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  }

  &.past {
    opacity: 0.75;
  }
}

.booking-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.salon-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.status-badge {
  font-size: 11px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 12px;

  &.confirmed {
    background: #e8f5e9;
    color: #2e7d32;
  }

  &.cancelled {
    background: #f5f5f5;
    color: #757575;
  }
}

.care-name {
  font-size: 14px;
  color: #555;
  margin: 0 0 4px;
}

.booking-datetime {
  font-size: 13px;
  color: #777;
  margin: 0 0 4px;
}

.booking-meta {
  font-size: 12px;
  color: #999;
  margin: 0;
}

.empty-state {
  text-align: center;
  padding: 48px 24px;
}

.empty-text {
  font-size: 14px;
  color: #888;
  margin-bottom: 16px;
}

.discover-btn {
  background-color: #e91e63;
  color: white;
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/client-bookings/
git commit -m "feat: add ClientBookingsComponent with upcoming/past tabs"
```

---

### Task 6: Frontend — route changes + navigation + drawer

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/shared/layout/navigation/navigation-routes.ts`
- Modify: `frontend/src/app/shared/layout/header/header.html`

- [ ] **Step 1: Update app.routes.ts**

Replace the existing `/bookings` route (lines 67-71) with:

```typescript
{
  path: 'bookings',
  canActivate: [authGuard],
  loadComponent: () => import('./pages/client-bookings/client-bookings.component').then(m => m.ClientBookingsComponent),
},
```

Add admin CRUD bookings under the `pro` children (after the calendar route, before the redirect):

```typescript
{ path: 'bookings', component: BookingsComponent },
```

Remove the top-level `BookingsComponent` import if it's no longer used at the top level (it's still used in the pro children).

- [ ] **Step 2: Update navigation-routes.ts**

The `CLIENT_NAVIGATION_ROUTES` already points to `/bookings` which is correct. No change needed for the path.

- [ ] **Step 3: Hide bookings drawer for CLIENT users in header.html**

Wrap the bookings drawer toggle button (lines 69-78) with an auth + role check. The drawer should only show for PRO/ADMIN users.

Replace lines 69-78:
```html
        <button
          type="button"
          (click)="toggleBookingsDrawer()"
          aria-label="Mes rendez-vous"
          class="p-2 rounded-full hover:bg-neutral-100 focus:outline-none focus:ring-1 focus:ring-neutral-300 transition-colors duration-150">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="w-6 h-6">
            <path stroke-linecap="round" stroke-linejoin="round" d="M7 2v3M17 2v3M3 9h18"/>
            <rect x="3" y="5" width="18" height="16" rx="2"/>
          </svg>
        </button>
```

With (add role check):
```html
        @if (authService.user()?.role === 'PRO' || authService.user()?.role === 'ADMIN') {
          <button
            type="button"
            (click)="toggleBookingsDrawer()"
            aria-label="Mes rendez-vous"
            class="p-2 rounded-full hover:bg-neutral-100 focus:outline-none focus:ring-1 focus:ring-neutral-300 transition-colors duration-150">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="w-6 h-6">
              <path stroke-linecap="round" stroke-linejoin="round" d="M7 2v3M17 2v3M3 9h18"/>
              <rect x="3" y="5" width="18" height="16" rx="2"/>
            </svg>
          </button>
        }
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/app.routes.ts frontend/src/app/shared/layout/navigation/navigation-routes.ts frontend/src/app/shared/layout/header/header.html
git commit -m "feat: route /bookings to client history, add pro/bookings, hide drawer for clients"
```

---

### Task 7: i18n — add client bookings translation keys

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add keys to fr.json**

Add a new top-level `"clientBookings"` object:

```json
"clientBookings": {
  "title": "Mes rendez-vous",
  "upcoming": "À venir",
  "past": "Passés",
  "emptyUpcoming": "Aucun rendez-vous à venir",
  "emptyPast": "Aucun rendez-vous passé",
  "discoverCta": "Découvrir les salons",
  "status": {
    "CONFIRMED": "Confirmé",
    "CANCELLED": "Annulé"
  }
}
```

- [ ] **Step 2: Add keys to en.json**

```json
"clientBookings": {
  "title": "My Appointments",
  "upcoming": "Upcoming",
  "past": "Past",
  "emptyUpcoming": "No upcoming appointments",
  "emptyPast": "No past appointments",
  "discoverCta": "Discover salons",
  "status": {
    "CONFIRMED": "Confirmed",
    "CANCELLED": "Cancelled"
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat: add client bookings i18n keys (fr + en)"
```

---

### Task 8: Integration verification

- [ ] **Step 1: Verify backend compiles**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Manual smoke test**

With backend + frontend running:

1. Login as `marie@test.com` / `Password1!`
2. Navigate to `/bookings` → should see "Mes rendez-vous" with empty upcoming state
3. Go to `/salon/sophie-martin` → book a care → booking confirmed
4. Go back to `/bookings` → should see the booking in "À venir" tab
5. Click "Passés" tab → should be empty (booking is today = upcoming)
6. Verify the bookings drawer icon is hidden in the header (client role)
7. Login as `sophie@prettyface.com` → bookings drawer should be visible
8. Navigate to `/pro/bookings` → should see the admin CRUD table
