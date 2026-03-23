# Opening Hours Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow professionals to configure weekly opening hours with multiple time slots per day, stored per tenant schema.

**Architecture:** New `availability` feature package (backend + frontend). Backend: `OpeningHour` JPA entity, repository, service with overlap validation, pro controller (GET/PUT bulk) and public endpoint. Frontend: new page at `/pro/availability` with inline time editing, `AvailabilityStore`, `AvailabilityService`.

**Tech Stack:** Spring Boot 3.5.4 / Java 21, Angular 20 (standalone, zoneless, signals), NgRx SignalStore, Transloco i18n

**Spec:** `docs/superpowers/specs/2026-03-23-opening-hours-design.md`

---

## Chunk 1: Backend

### Task 1: Create OpeningHour entity and repository

**Files:**
- Create: `backend/src/main/java/com/fleurdecoquillage/app/availability/domain/OpeningHour.java`
- Create: `backend/src/main/java/com/fleurdecoquillage/app/availability/repo/OpeningHourRepository.java`

- [ ] **Step 1: Create entity**

```java
package com.fleurdecoquillage.app.availability.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "OPENING_HOURS")
public class OpeningHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek; // 1=Monday ... 7=Sunday

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;
}
```

- [ ] **Step 2: Create repository**

```java
package com.fleurdecoquillage.app.availability.repo;

import com.fleurdecoquillage.app.availability.domain.OpeningHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpeningHourRepository extends JpaRepository<OpeningHour, Long> {
    List<OpeningHour> findAllByOrderByDayOfWeekAscOpenTimeAsc();
    void deleteAllInBatch();
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/availability/
git commit -m "feat: create OpeningHour entity and repository"
```

---

### Task 2: Create DTOs and mapper

**Files:**
- Create: `backend/src/main/java/com/fleurdecoquillage/app/availability/web/dto/OpeningHourRequest.java`
- Create: `backend/src/main/java/com/fleurdecoquillage/app/availability/web/dto/OpeningHourResponse.java`
- Create: `backend/src/main/java/com/fleurdecoquillage/app/availability/web/mapper/OpeningHourMapper.java`

- [ ] **Step 1: Create request DTO**

```java
package com.fleurdecoquillage.app.availability.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OpeningHourRequest(
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull String openTime,
        @NotNull String closeTime
) {}
```

- [ ] **Step 2: Create response DTO**

```java
package com.fleurdecoquillage.app.availability.web.dto;

public record OpeningHourResponse(
        Long id,
        Integer dayOfWeek,
        String openTime,
        String closeTime
) {}
```

- [ ] **Step 3: Create mapper**

```java
package com.fleurdecoquillage.app.availability.web.mapper;

import com.fleurdecoquillage.app.availability.domain.OpeningHour;
import com.fleurdecoquillage.app.availability.web.dto.OpeningHourRequest;
import com.fleurdecoquillage.app.availability.web.dto.OpeningHourResponse;

import java.time.LocalTime;

public class OpeningHourMapper {

    public static OpeningHour toEntity(OpeningHourRequest req) {
        OpeningHour h = new OpeningHour();
        h.setDayOfWeek(req.dayOfWeek());
        h.setOpenTime(LocalTime.parse(req.openTime()));
        h.setCloseTime(LocalTime.parse(req.closeTime()));
        return h;
    }

    public static OpeningHourResponse toResponse(OpeningHour h) {
        return new OpeningHourResponse(
                h.getId(),
                h.getDayOfWeek(),
                h.getOpenTime().toString(),
                h.getCloseTime().toString()
        );
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/availability/web/
git commit -m "feat: add OpeningHour DTOs and mapper"
```

---

### Task 3: Create AvailabilityService with validation

**Files:**
- Create: `backend/src/main/java/com/fleurdecoquillage/app/availability/app/AvailabilityService.java`

- [ ] **Step 1: Create service**

```java
package com.fleurdecoquillage.app.availability.app;

import com.fleurdecoquillage.app.availability.domain.OpeningHour;
import com.fleurdecoquillage.app.availability.repo.OpeningHourRepository;
import com.fleurdecoquillage.app.availability.web.dto.OpeningHourRequest;
import com.fleurdecoquillage.app.availability.web.dto.OpeningHourResponse;
import com.fleurdecoquillage.app.availability.web.mapper.OpeningHourMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {

    private final OpeningHourRepository repo;

    public AvailabilityService(OpeningHourRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<OpeningHourResponse> list() {
        return repo.findAllByOrderByDayOfWeekAscOpenTimeAsc()
                .stream()
                .map(OpeningHourMapper::toResponse)
                .toList();
    }

    @Transactional
    public List<OpeningHourResponse> replaceAll(List<OpeningHourRequest> requests) {
        // Validate all entries
        for (OpeningHourRequest req : requests) {
            LocalTime open = LocalTime.parse(req.openTime());
            LocalTime close = LocalTime.parse(req.closeTime());
            if (!close.isAfter(open)) {
                throw new IllegalArgumentException(
                        "Close time must be after open time for day " + req.dayOfWeek());
            }
        }

        // Check for overlaps per day
        Map<Integer, List<OpeningHourRequest>> byDay = requests.stream()
                .collect(Collectors.groupingBy(OpeningHourRequest::dayOfWeek));

        for (var entry : byDay.entrySet()) {
            List<OpeningHourRequest> daySlots = entry.getValue().stream()
                    .sorted(Comparator.comparing(r -> LocalTime.parse(r.openTime())))
                    .toList();

            for (int i = 1; i < daySlots.size(); i++) {
                LocalTime prevClose = LocalTime.parse(daySlots.get(i - 1).closeTime());
                LocalTime currOpen = LocalTime.parse(daySlots.get(i).openTime());
                if (currOpen.isBefore(prevClose)) {
                    throw new IllegalArgumentException(
                            "Overlapping time slots on day " + entry.getKey());
                }
            }
        }

        // Delete all and re-insert
        repo.deleteAllInBatch();
        repo.flush();

        List<OpeningHour> entities = requests.stream()
                .map(OpeningHourMapper::toEntity)
                .toList();

        List<OpeningHour> saved = repo.saveAll(entities);

        return saved.stream()
                .sorted(Comparator.comparingInt(OpeningHour::getDayOfWeek)
                        .thenComparing(OpeningHour::getOpenTime))
                .map(OpeningHourMapper::toResponse)
                .toList();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/availability/app/
git commit -m "feat: add AvailabilityService with overlap validation"
```

---

### Task 4: Create pro controller and public endpoint

**Files:**
- Create: `backend/src/main/java/com/fleurdecoquillage/app/availability/web/AvailabilityController.java`
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/tenant/web/PublicSalonController.java`

- [ ] **Step 1: Create pro controller**

```java
package com.fleurdecoquillage.app.availability.web;

import com.fleurdecoquillage.app.availability.app.AvailabilityService;
import com.fleurdecoquillage.app.availability.web.dto.OpeningHourRequest;
import com.fleurdecoquillage.app.availability.web.dto.OpeningHourResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pro/opening-hours")
public class AvailabilityController {

    private final AvailabilityService service;

    public AvailabilityController(AvailabilityService service) {
        this.service = service;
    }

    @GetMapping
    public List<OpeningHourResponse> list() {
        return service.list();
    }

    @PutMapping
    public List<OpeningHourResponse> replaceAll(@RequestBody @Valid List<OpeningHourRequest> requests) {
        return service.replaceAll(requests);
    }
}
```

- [ ] **Step 2: Add public endpoint to PublicSalonController**

Add to `PublicSalonController.java` after the existing `getSalon` method:

```java
@GetMapping("/{slug}/opening-hours")
@Transactional(readOnly = true)
public ResponseEntity<List<com.fleurdecoquillage.app.availability.web.dto.OpeningHourResponse>> getOpeningHours(@PathVariable String slug) {
    return tenantService.findBySlug(slug)
            .filter(tenant -> tenant.getStatus() == com.fleurdecoquillage.app.tenant.domain.TenantStatus.ACTIVE)
            .map(tenant -> {
                TenantContext.setCurrentTenant(tenant.getSlug());
                try {
                    var hours = availabilityService.list();
                    return ResponseEntity.ok(hours);
                } finally {
                    TenantContext.clear();
                }
            })
            .orElse(ResponseEntity.notFound().build());
}
```

Inject `AvailabilityService` in the constructor:

```java
private final AvailabilityService availabilityService;

public PublicSalonController(TenantService tenantService, CategoryRepository categoryRepository, AvailabilityService availabilityService) {
    this.tenantService = tenantService;
    this.categoryRepository = categoryRepository;
    this.availabilityService = availabilityService;
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/availability/web/ \
       backend/src/main/java/com/fleurdecoquillage/app/tenant/web/PublicSalonController.java
git commit -m "feat: add pro and public opening hours endpoints"
```

---

## Chunk 2: Frontend

### Task 5: Add i18n keys

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add French keys**

Add inside the `"pro"` block after `"categories"`:

```json
"availability": {
  "title": "Mes disponibilités",
  "closed": "Fermé",
  "addSlot": "Ajouter une plage",
  "save": "Enregistrer",
  "saveSuccess": "Horaires mis à jour",
  "saveError": "Erreur lors de la sauvegarde",
  "clickToOpen": "Cliquez pour ouvrir",
  "overlapError": "Les plages horaires se chevauchent",
  "invalidTime": "L'heure de fin doit être après l'heure de début",
  "days": {
    "1": "Lundi",
    "2": "Mardi",
    "3": "Mercredi",
    "4": "Jeudi",
    "5": "Vendredi",
    "6": "Samedi",
    "7": "Dimanche"
  }
}
```

- [ ] **Step 2: Add English keys**

Add inside the `"pro"` block after `"categories"`:

```json
"availability": {
  "title": "My availability",
  "closed": "Closed",
  "addSlot": "Add time slot",
  "save": "Save",
  "saveSuccess": "Hours updated",
  "saveError": "Error saving hours",
  "clickToOpen": "Click to open",
  "overlapError": "Time slots overlap",
  "invalidTime": "End time must be after start time",
  "days": {
    "1": "Monday",
    "2": "Tuesday",
    "3": "Wednesday",
    "4": "Thursday",
    "5": "Friday",
    "6": "Saturday",
    "7": "Sunday"
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/public/i18n/
git commit -m "feat: add i18n keys for opening hours"
```

---

### Task 6: Create AvailabilityService and models

**Files:**
- Create: `frontend/src/app/features/availability/availability.model.ts`
- Create: `frontend/src/app/features/availability/availability.service.ts`

- [ ] **Step 1: Create models**

```typescript
export interface TimeSlot {
  openTime: string;  // "09:00"
  closeTime: string; // "18:00"
}

export interface DaySlots {
  dayOfWeek: number; // 1-7
  slots: TimeSlot[];
}

export interface OpeningHourRequest {
  dayOfWeek: number;
  openTime: string;
  closeTime: string;
}

export interface OpeningHourResponse {
  id: number;
  dayOfWeek: number;
  openTime: string;
  closeTime: string;
}
```

- [ ] **Step 2: Create service**

```typescript
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { OpeningHourRequest, OpeningHourResponse } from './availability.model';

@Injectable({ providedIn: 'root' })
export class AvailabilityService {
  private http = inject(HttpClient);
  private apiBaseUrl = inject(API_BASE_URL);

  loadHours(): Observable<OpeningHourResponse[]> {
    return this.http.get<OpeningHourResponse[]>(`${this.apiBaseUrl}/api/pro/opening-hours`);
  }

  saveHours(hours: OpeningHourRequest[]): Observable<OpeningHourResponse[]> {
    return this.http.put<OpeningHourResponse[]>(`${this.apiBaseUrl}/api/pro/opening-hours`, hours);
  }

  loadPublicHours(slug: string): Observable<OpeningHourResponse[]> {
    return this.http.get<OpeningHourResponse[]>(`${this.apiBaseUrl}/api/salon/${slug}/opening-hours`);
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/availability/
git commit -m "feat: add AvailabilityService and opening hours models"
```

---

### Task 7: Create AvailabilityStore

**Files:**
- Create: `frontend/src/app/features/availability/availability.store.ts`

- [ ] **Step 1: Create store**

```typescript
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, exhaustMap, pipe, switchMap, tap } from 'rxjs';
import { setError, setFulfilled, setPending, withRequestStatus } from '../../shared/features/request.status.feature';
import { AvailabilityService } from './availability.service';
import { OpeningHourRequest, OpeningHourResponse } from './availability.model';

type AvailabilityState = {
  hours: OpeningHourResponse[];
};

export const AvailabilityStore = signalStore(
  withState<AvailabilityState>({ hours: [] }),
  withRequestStatus(),
  withMethods((store, service = inject(AvailabilityService)) => ({
    loadHours: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() =>
          service.loadHours().pipe(
            tap((hours) => patchState(store, { hours }, setFulfilled())),
            catchError((err) => {
              patchState(store, setError(err?.message ?? 'Erreur de chargement'));
              return EMPTY;
            })
          )
        )
      )
    ),
    saveHours: rxMethod<OpeningHourRequest[]>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap((requests) =>
          service.saveHours(requests).pipe(
            tap((hours) => patchState(store, { hours }, setFulfilled())),
            catchError((err) => {
              patchState(store, setError(err?.error?.error ?? err?.message ?? 'Erreur de sauvegarde'));
              return EMPTY;
            })
          )
        )
      )
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.loadHours();
    },
  }))
);
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/features/availability/availability.store.ts
git commit -m "feat: add AvailabilityStore with load and save methods"
```

---

### Task 8: Create AvailabilityComponent

**Files:**
- Create: `frontend/src/app/features/availability/availability.component.ts`
- Create: `frontend/src/app/features/availability/availability.component.html`
- Create: `frontend/src/app/features/availability/availability.component.scss`

- [ ] **Step 1: Create component TypeScript**

```typescript
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AvailabilityStore } from './availability.store';
import { DaySlots, OpeningHourRequest, OpeningHourResponse, TimeSlot } from './availability.model';

const DEFAULT_SLOT: TimeSlot = { openTime: '09:00', closeTime: '18:00' };

@Component({
  selector: 'app-availability',
  standalone: true,
  imports: [FormsModule, TranslocoPipe, MatSnackBarModule, MatIconModule, MatButtonModule],
  templateUrl: './availability.component.html',
  styleUrl: './availability.component.scss',
  providers: [AvailabilityStore],
})
export class AvailabilityComponent {
  readonly store = inject(AvailabilityStore);
  private snackBar = inject(MatSnackBar);
  private i18n = inject(TranslocoService);

  readonly weekDays = [1, 2, 3, 4, 5, 6, 7];

  // Local editable state derived from store
  readonly week = signal<DaySlots[]>(this.buildEmptyWeek());

  constructor() {
    // Sync store → local state when hours load
    const storeHours = this.store.hours;
    // Use effect-like pattern: watch for store changes
    setTimeout(() => this.syncFromStore(), 0);
  }

  syncFromStore(): void {
    const hours = this.store.hours();
    const week = this.buildEmptyWeek();
    for (const h of hours) {
      const day = week.find((d) => d.dayOfWeek === h.dayOfWeek);
      if (day) {
        day.slots.push({ openTime: h.openTime, closeTime: h.closeTime });
      }
    }
    this.week.set(week);
  }

  getDaySlots(dayOfWeek: number): TimeSlot[] {
    return this.week().find((d) => d.dayOfWeek === dayOfWeek)?.slots ?? [];
  }

  isDayClosed(dayOfWeek: number): boolean {
    return this.getDaySlots(dayOfWeek).length === 0;
  }

  openDay(dayOfWeek: number): void {
    this.week.update((w) =>
      w.map((d) =>
        d.dayOfWeek === dayOfWeek ? { ...d, slots: [{ ...DEFAULT_SLOT }] } : d
      )
    );
  }

  addSlot(dayOfWeek: number): void {
    this.week.update((w) =>
      w.map((d) => {
        if (d.dayOfWeek !== dayOfWeek) return d;
        const lastSlot = d.slots[d.slots.length - 1];
        const newOpen = lastSlot ? lastSlot.closeTime : '09:00';
        const newClose = '18:00';
        return { ...d, slots: [...d.slots, { openTime: newOpen, closeTime: newClose }] };
      })
    );
  }

  removeSlot(dayOfWeek: number, index: number): void {
    this.week.update((w) =>
      w.map((d) => {
        if (d.dayOfWeek !== dayOfWeek) return d;
        return { ...d, slots: d.slots.filter((_, i) => i !== index) };
      })
    );
  }

  updateSlotTime(dayOfWeek: number, index: number, field: 'openTime' | 'closeTime', value: string): void {
    this.week.update((w) =>
      w.map((d) => {
        if (d.dayOfWeek !== dayOfWeek) return d;
        return {
          ...d,
          slots: d.slots.map((s, i) => (i === index ? { ...s, [field]: value } : s)),
        };
      })
    );
  }

  onSave(): void {
    const requests: OpeningHourRequest[] = [];
    for (const day of this.week()) {
      for (const slot of day.slots) {
        requests.push({
          dayOfWeek: day.dayOfWeek,
          openTime: slot.openTime,
          closeTime: slot.closeTime,
        });
      }
    }
    this.store.saveHours(requests);
    this.snackBar.open(this.i18n.translate('pro.availability.saveSuccess'), 'OK', { duration: 3000 });
  }

  private buildEmptyWeek(): DaySlots[] {
    return this.weekDays.map((d) => ({ dayOfWeek: d, slots: [] }));
  }
}
```

- [ ] **Step 2: Create template**

```html
<div class="availability-page">
  <h1 class="page-title">{{ 'pro.availability.title' | transloco }}</h1>

  <div class="week-list">
    @for (dayOfWeek of weekDays; track dayOfWeek) {
      <div class="day-row" [class.closed]="isDayClosed(dayOfWeek)">
        <div class="day-name">
          {{ 'pro.availability.days.' + dayOfWeek | transloco }}
        </div>

        @if (isDayClosed(dayOfWeek)) {
          <button type="button" class="closed-label" (click)="openDay(dayOfWeek)">
            <span class="closed-text">{{ 'pro.availability.closed' | transloco }}</span>
            <span class="open-hint">{{ 'pro.availability.clickToOpen' | transloco }}</span>
          </button>
        } @else {
          <div class="slots-container">
            @for (slot of getDaySlots(dayOfWeek); track $index; let i = $index) {
              <div class="slot-row">
                <input
                  type="time"
                  class="time-input"
                  [value]="slot.openTime"
                  (change)="updateSlotTime(dayOfWeek, i, 'openTime', $any($event.target).value)"
                />
                <span class="time-separator">→</span>
                <input
                  type="time"
                  class="time-input"
                  [value]="slot.closeTime"
                  (change)="updateSlotTime(dayOfWeek, i, 'closeTime', $any($event.target).value)"
                />
                <button type="button" class="remove-slot" (click)="removeSlot(dayOfWeek, i)">
                  <mat-icon>close</mat-icon>
                </button>
              </div>
            }
            <button type="button" class="add-slot-btn" (click)="addSlot(dayOfWeek)">
              <mat-icon>add</mat-icon>
            </button>
          </div>
        }
      </div>
    }
  </div>

  <div class="save-bar">
    <button type="button" class="save-btn" (click)="onSave()">
      {{ 'pro.availability.save' | transloco }}
    </button>
  </div>
</div>
```

- [ ] **Step 3: Create styles**

```scss
.availability-page {
  max-width: 700px;
  margin: 0 auto;
  padding: 24px;
}

.page-title {
  font-size: 1.5rem;
  font-weight: 300;
  letter-spacing: 0.5px;
  color: #333;
  margin-bottom: 24px;
}

.week-list {
  display: flex;
  flex-direction: column;
}

.day-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid #f0f0f0;

  &.closed {
    opacity: 0.5;
  }
}

.day-name {
  width: 80px;
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 500;
  color: #333;
  padding-top: 6px;
}

.closed-label {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 6px 0;

  .closed-text {
    font-size: 13px;
    color: #bbb;
    font-style: italic;
  }

  .open-hint {
    font-size: 11px;
    color: #ddd;
    transition: color 150ms ease;
  }

  &:hover .open-hint {
    color: #c06;
  }
}

.slots-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.slot-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.time-input {
  background: #f7f5f3;
  border: none;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: #333;
  outline: none;
  width: 110px;

  &:focus {
    box-shadow: 0 0 0 2px rgba(192, 0, 102, 0.15);
  }
}

.time-separator {
  font-size: 12px;
  color: #999;
}

.remove-slot {
  background: none;
  border: none;
  cursor: pointer;
  color: #ccc;
  padding: 2px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 150ms ease;

  &:hover {
    color: #c06;
  }

  mat-icon {
    font-size: 18px;
    width: 18px;
    height: 18px;
  }
}

.add-slot-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #ccc;
  display: flex;
  align-items: center;
  padding: 2px;
  transition: color 150ms ease;

  &:hover {
    color: #c06;
  }

  mat-icon {
    font-size: 18px;
    width: 18px;
    height: 18px;
  }
}

.save-bar {
  text-align: center;
  margin-top: 24px;
}

.save-btn {
  background: linear-gradient(135deg, #a8385d, #c06);
  color: white;
  border: none;
  padding: 12px 32px;
  border-radius: 24px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 150ms ease;

  &:hover {
    opacity: 0.9;
  }
}
```

- [ ] **Step 4: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/availability/availability.component.ts \
       frontend/src/app/features/availability/availability.component.html \
       frontend/src/app/features/availability/availability.component.scss
git commit -m "feat: add AvailabilityComponent with inline time editing"
```

---

### Task 9: Add route and navigation link

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/shared/layout/navigation/navigation-routes.ts`

- [ ] **Step 1: Add route**

Add to `pro` children in `app.routes.ts`, after the `categories` entry:

```typescript
{
  path: 'availability',
  loadComponent: () => import('./features/availability/availability.component').then(m => m.AvailabilityComponent),
},
```

- [ ] **Step 2: Add nav link**

Add to `PRO_NAVIGATION_ROUTES` in `navigation-routes.ts`, after the "Catégories" entry:

```typescript
{
  label: 'Disponibilités',
  path: '/pro/availability',
  icon: 'schedule',
  requiresAuth: true,
  requiredRole: 'PRO',
},
```

- [ ] **Step 3: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/app.routes.ts \
       frontend/src/app/shared/layout/navigation/navigation-routes.ts
git commit -m "feat: add /pro/availability route and sidenav link"
```

---

## Chunk 3: Sync & Verification

### Task 10: Wire store sync from loaded data

The `AvailabilityComponent` needs to call `syncFromStore()` after the store finishes loading. Update the component to use an `effect()`:

**Files:**
- Modify: `frontend/src/app/features/availability/availability.component.ts`

- [ ] **Step 1: Replace constructor with effect**

Replace the constructor in the component:

```typescript
import { Component, computed, effect, inject, signal } from '@angular/core';
```

Replace the constructor body:

```typescript
constructor() {
  effect(() => {
    const hours = this.store.hours();
    if (hours) {
      this.syncFromStoreData(hours);
    }
  });
}

private syncFromStoreData(hours: OpeningHourResponse[]): void {
  const week = this.buildEmptyWeek();
  for (const h of hours) {
    const day = week.find((d) => d.dayOfWeek === h.dayOfWeek);
    if (day) {
      day.slots.push({ openTime: h.openTime, closeTime: h.closeTime });
    }
  }
  this.week.set(week);
}
```

Remove the old `syncFromStore()` method.

- [ ] **Step 2: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/availability/availability.component.ts
git commit -m "fix: use effect to sync store data into editable week state"
```

---

### Task 11: Final verification

- [ ] **Step 1: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 2: Verify translations**

Check that all `pro.availability.*` keys exist in both `fr.json` and `en.json`.

- [ ] **Step 3: Verify routing**

Check that `/pro/availability` is in the `pro` children routes and that the sidenav shows "Disponibilités".
