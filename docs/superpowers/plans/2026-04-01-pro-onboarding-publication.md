# Pro Onboarding & Salon Publication — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let professionals publish their salon once configured (name, category, care, hours), with a dashboard showing either a setup checklist (DRAFT) or an activity summary (ACTIVE).

**Architecture:** New `TenantReadinessService` computes publication conditions by querying tenant-scoped repositories. `TenantController` gets 3 new endpoints (readiness, publish, unpublish). Frontend replaces the placeholder dashboard with a two-view component backed by a SignalStore.

**Tech Stack:** Spring Boot 3.5 (Java 21), Angular 20 (standalone, zoneless), NgRx SignalStore, Angular Material, Transloco i18n

---

## File Structure

### Backend (new files)
- `backend/src/main/java/com/prettyface/app/tenant/app/TenantReadinessService.java` — readiness logic
- `backend/src/main/java/com/prettyface/app/tenant/web/dto/TenantReadinessResponse.java` — readiness DTO
- `backend/src/main/java/com/prettyface/app/tenant/web/dto/PublishErrorResponse.java` — 422 error DTO
- `backend/src/test/java/com/prettyface/app/tenant/app/TenantReadinessServiceTests.java` — unit tests

### Backend (modified files)
- `backend/src/main/java/com/prettyface/app/tenant/web/TenantController.java` — add 3 endpoints
- `backend/src/main/java/com/prettyface/app/care/repo/CareRepository.java` — add `countByStatus` method

### Frontend (new files)
- `frontend/src/app/features/dashboard/models/dashboard.model.ts` — TypeScript interfaces
- `frontend/src/app/features/dashboard/services/dashboard.service.ts` — HTTP service
- `frontend/src/app/features/dashboard/store/dashboard.store.ts` — SignalStore

### Frontend (modified files)
- `frontend/src/app/pages/pro/pro-dashboard.component.ts` — full rewrite
- `frontend/src/app/pages/pro/pro-dashboard.component.html` — new template (external file)
- `frontend/src/app/pages/pro/pro-dashboard.component.scss` — styles
- `frontend/public/i18n/fr.json` — add `pro.dashboard.*` keys
- `frontend/public/i18n/en.json` — add `pro.dashboard.*` keys

---

## Task 1: Backend — Readiness DTO and CareRepository count method

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/tenant/web/dto/TenantReadinessResponse.java`
- Create: `backend/src/main/java/com/prettyface/app/tenant/web/dto/PublishErrorResponse.java`
- Modify: `backend/src/main/java/com/prettyface/app/care/repo/CareRepository.java`

- [ ] **Step 1: Create TenantReadinessResponse record**

```java
package com.prettyface.app.tenant.web.dto;

public record TenantReadinessResponse(
    boolean name,
    boolean hasCategory,
    boolean hasActiveCare,
    boolean hasOpeningHours,
    boolean canPublish,
    String status
) {}
```

- [ ] **Step 2: Create PublishErrorResponse record**

```java
package com.prettyface.app.tenant.web.dto;

import java.util.List;

public record PublishErrorResponse(
    String message,
    List<String> missing
) {}
```

- [ ] **Step 3: Add countByStatus to CareRepository**

In `CareRepository.java`, add this method below the existing `countByCategoryId`:

```java
long countByStatus(com.prettyface.app.care.domain.CareStatus status);
```

- [ ] **Step 4: Verify backend compiles**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/web/dto/TenantReadinessResponse.java \
       backend/src/main/java/com/prettyface/app/tenant/web/dto/PublishErrorResponse.java \
       backend/src/main/java/com/prettyface/app/care/repo/CareRepository.java
git commit -m "feat: add readiness/publish DTOs and CareRepository countByStatus"
```

---

## Task 2: Backend — TenantReadinessService

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/tenant/app/TenantReadinessService.java`
- Create: `backend/src/test/java/com/prettyface/app/tenant/app/TenantReadinessServiceTests.java`

- [ ] **Step 1: Write the failing test — all conditions met**

```java
package com.prettyface.app.tenant.app;

import com.prettyface.app.availability.repo.OpeningHourRepository;
import com.prettyface.app.care.domain.CareStatus;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.domain.TenantStatus;
import com.prettyface.app.tenant.web.dto.TenantReadinessResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantReadinessServiceTests {

    @Mock private CategoryRepository categoryRepository;
    @Mock private CareRepository careRepository;
    @Mock private OpeningHourRepository openingHourRepository;

    private TenantReadinessService service;

    @BeforeEach
    void setUp() {
        service = new TenantReadinessService(categoryRepository, careRepository, openingHourRepository);
    }

    @Test
    void getReadiness_allConditionsMet_canPublish() {
        var tenant = Tenant.builder().name("Mon Salon").status(TenantStatus.DRAFT).build();
        when(categoryRepository.count()).thenReturn(2L);
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(3L);
        when(openingHourRepository.count()).thenReturn(5L);

        TenantReadinessResponse result = service.getReadiness(tenant);

        assertThat(result.name()).isTrue();
        assertThat(result.hasCategory()).isTrue();
        assertThat(result.hasActiveCare()).isTrue();
        assertThat(result.hasOpeningHours()).isTrue();
        assertThat(result.canPublish()).isTrue();
        assertThat(result.status()).isEqualTo("DRAFT");
    }

    @Test
    void getReadiness_missingCare_cannotPublish() {
        var tenant = Tenant.builder().name("Mon Salon").status(TenantStatus.DRAFT).build();
        when(categoryRepository.count()).thenReturn(1L);
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(0L);
        when(openingHourRepository.count()).thenReturn(3L);

        TenantReadinessResponse result = service.getReadiness(tenant);

        assertThat(result.hasActiveCare()).isFalse();
        assertThat(result.canPublish()).isFalse();
    }

    @Test
    void getReadiness_blankName_cannotPublish() {
        var tenant = Tenant.builder().name("  ").status(TenantStatus.ACTIVE).build();
        when(categoryRepository.count()).thenReturn(1L);
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(1L);
        when(openingHourRepository.count()).thenReturn(1L);

        TenantReadinessResponse result = service.getReadiness(tenant);

        assertThat(result.name()).isFalse();
        assertThat(result.canPublish()).isFalse();
    }

    @Test
    void getMissingConditions_returnsList() {
        var tenant = Tenant.builder().name("Salon").status(TenantStatus.DRAFT).build();
        when(categoryRepository.count()).thenReturn(0L);
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(0L);
        when(openingHourRepository.count()).thenReturn(0L);

        var missing = service.getMissingConditions(tenant);

        assertThat(missing).containsExactly("hasCategory", "hasActiveCare", "hasOpeningHours");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=TenantReadinessServiceTests -q`
Expected: FAIL — `TenantReadinessService` does not exist yet

- [ ] **Step 3: Implement TenantReadinessService**

```java
package com.prettyface.app.tenant.app;

import com.prettyface.app.availability.repo.OpeningHourRepository;
import com.prettyface.app.care.domain.CareStatus;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.web.dto.TenantReadinessResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TenantReadinessService {

    private final CategoryRepository categoryRepository;
    private final CareRepository careRepository;
    private final OpeningHourRepository openingHourRepository;

    public TenantReadinessService(CategoryRepository categoryRepository,
                                   CareRepository careRepository,
                                   OpeningHourRepository openingHourRepository) {
        this.categoryRepository = categoryRepository;
        this.careRepository = careRepository;
        this.openingHourRepository = openingHourRepository;
    }

    public TenantReadinessResponse getReadiness(Tenant tenant) {
        boolean name = tenant.getName() != null && !tenant.getName().isBlank();
        boolean hasCategory = categoryRepository.count() > 0;
        boolean hasActiveCare = careRepository.countByStatus(CareStatus.ACTIVE) > 0;
        boolean hasOpeningHours = openingHourRepository.count() > 0;
        boolean canPublish = name && hasCategory && hasActiveCare && hasOpeningHours;

        return new TenantReadinessResponse(
            name, hasCategory, hasActiveCare, hasOpeningHours,
            canPublish, tenant.getStatus().name()
        );
    }

    public List<String> getMissingConditions(Tenant tenant) {
        TenantReadinessResponse r = getReadiness(tenant);
        List<String> missing = new ArrayList<>();
        if (!r.name()) missing.add("name");
        if (!r.hasCategory()) missing.add("hasCategory");
        if (!r.hasActiveCare()) missing.add("hasActiveCare");
        if (!r.hasOpeningHours()) missing.add("hasOpeningHours");
        return missing;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -pl . -Dtest=TenantReadinessServiceTests -q`
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/app/TenantReadinessService.java \
       backend/src/test/java/com/prettyface/app/tenant/app/TenantReadinessServiceTests.java
git commit -m "feat: add TenantReadinessService with unit tests"
```

---

## Task 3: Backend — Publish/Unpublish endpoints on TenantController

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/TenantController.java`

- [ ] **Step 1: Add readiness, publish, and unpublish endpoints**

Add these 3 methods to `TenantController.java` after the existing `updateProfile` method. Also add the required imports and inject `TenantReadinessService`:

Add to imports:
```java
import com.prettyface.app.tenant.app.TenantReadinessService;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.domain.TenantStatus;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.tenant.web.dto.TenantReadinessResponse;
import com.prettyface.app.tenant.web.dto.PublishErrorResponse;
import com.prettyface.app.multitenancy.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
```

Add `TenantReadinessService` and `TenantRepository` as constructor dependencies alongside the existing `TenantService`.

Add these 3 endpoint methods:

```java
@GetMapping("/readiness")
public ResponseEntity<TenantReadinessResponse> getReadiness(@AuthenticationPrincipal UserPrincipal principal) {
    Tenant tenant = tenantRepository.findByOwnerId(principal.getId())
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    TenantContext.setCurrentTenant(tenant.getSlug());
    try {
        return ResponseEntity.ok(readinessService.getReadiness(tenant));
    } finally {
        TenantContext.clear();
    }
}

@PutMapping("/publish")
@Transactional
public ResponseEntity<?> publish(@AuthenticationPrincipal UserPrincipal principal) {
    Tenant tenant = tenantRepository.findByOwnerId(principal.getId())
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    TenantContext.setCurrentTenant(tenant.getSlug());
    try {
        var missing = readinessService.getMissingConditions(tenant);
        if (!missing.isEmpty()) {
            return ResponseEntity.unprocessableEntity()
                    .body(new PublishErrorResponse("Salon cannot be published", missing));
        }
        tenant.setStatus(TenantStatus.ACTIVE);
        tenantRepository.save(tenant);
        return ResponseEntity.ok().build();
    } finally {
        TenantContext.clear();
    }
}

@PutMapping("/unpublish")
@Transactional
public ResponseEntity<Void> unpublish(@AuthenticationPrincipal UserPrincipal principal) {
    Tenant tenant = tenantRepository.findByOwnerId(principal.getId())
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    tenant.setStatus(TenantStatus.DRAFT);
    tenantRepository.save(tenant);
    return ResponseEntity.ok().build();
}
```

- [ ] **Step 2: Verify backend compiles**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/web/TenantController.java
git commit -m "feat: add readiness, publish, unpublish endpoints to TenantController"
```

---

## Task 4: Frontend — Dashboard models and service

**Files:**
- Create: `frontend/src/app/features/dashboard/models/dashboard.model.ts`
- Create: `frontend/src/app/features/dashboard/services/dashboard.service.ts`

- [ ] **Step 1: Create dashboard model**

```typescript
export interface TenantReadiness {
  name: boolean;
  hasCategory: boolean;
  hasActiveCare: boolean;
  hasOpeningHours: boolean;
  canPublish: boolean;
  status: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';
}

export interface PublishError {
  message: string;
  missing: string[];
}
```

- [ ] **Step 2: Create dashboard service**

```typescript
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { TenantReadiness } from '../models/dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  private get baseUrl(): string {
    return this.apiBaseUrl?.replace(/\/$/, '') ?? '';
  }

  getReadiness(): Observable<TenantReadiness> {
    return this.http.get<TenantReadiness>(`${this.baseUrl}/api/pro/tenant/readiness`);
  }

  publish(): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/api/pro/tenant/publish`, {});
  }

  unpublish(): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/api/pro/tenant/unpublish`, {});
  }
}
```

- [ ] **Step 3: Verify frontend compiles**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: No errors related to dashboard files

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/dashboard/
git commit -m "feat: add dashboard model and service"
```

---

## Task 5: Frontend — Dashboard SignalStore

**Files:**
- Create: `frontend/src/app/features/dashboard/store/dashboard.store.ts`

- [ ] **Step 1: Create DashboardStore**

```typescript
import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, exhaustMap, pipe, switchMap, tap } from 'rxjs';
import { TenantReadiness } from '../models/dashboard.model';
import { CareBookingDetailed } from '../../bookings/models/bookings.model';
import { DashboardService } from '../services/dashboard.service';
import { BookingsService } from '../../bookings/services/bookings.service';
import {
  setError,
  setFulfilled,
  setPending,
  withRequestStatus,
} from '../../../shared/features/request.status.feature';

type DashboardState = {
  readiness: TenantReadiness | null;
  recentBookings: CareBookingDetailed[];
  todayCount: number;
  weekCount: number;
  publishSuccess: boolean;
  unpublishSuccess: boolean;
};

export const DashboardStore = signalStore(
  withState<DashboardState>({
    readiness: null,
    recentBookings: [],
    todayCount: 0,
    weekCount: 0,
    publishSuccess: false,
    unpublishSuccess: false,
  }),
  withRequestStatus(),
  withComputed((store) => ({
    isActive: computed(() => store.readiness()?.status === 'ACTIVE'),
    isDraft: computed(() => store.readiness()?.status === 'DRAFT'),
    canPublish: computed(() => store.readiness()?.canPublish ?? false),
  })),
  withMethods(
    (
      store,
      dashboardService = inject(DashboardService),
      bookingsService = inject(BookingsService)
    ) => ({
      loadReadiness: rxMethod<void>(
        pipe(
          tap(() => patchState(store, setPending())),
          switchMap(() =>
            dashboardService.getReadiness().pipe(
              tap((readiness) => patchState(store, { readiness }, setFulfilled())),
              catchError(() => {
                patchState(store, setError('Erreur de chargement'));
                return EMPTY;
              })
            )
          )
        )
      ),
      loadActivity: rxMethod<void>(
        pipe(
          switchMap(() => {
            const now = new Date();
            const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
            const dayOfWeek = now.getDay() === 0 ? 7 : now.getDay();
            const weekStart = new Date(todayStart);
            weekStart.setDate(weekStart.getDate() - (dayOfWeek - 1));
            const weekEnd = new Date(weekStart);
            weekEnd.setDate(weekEnd.getDate() + 7);

            return bookingsService
              .listDetailed(
                { from: weekStart.toISOString(), status: undefined },
                { page: 0, size: 5, sort: 'appointmentDate,asc' }
              )
              .pipe(
                tap((page) => {
                  const todayStr = todayStart.toISOString().split('T')[0];
                  const weekEndStr = weekEnd.toISOString().split('T')[0];
                  const todayCount = page.content.filter(
                    (b) => b.appointmentDate === todayStr
                  ).length;
                  const weekCount = page.content.length;
                  patchState(store, {
                    recentBookings: page.content,
                    todayCount,
                    weekCount,
                  });
                }),
                catchError(() => EMPTY)
              );
          })
        )
      ),
      publish: rxMethod<void>(
        pipe(
          tap(() => patchState(store, { publishSuccess: false }, setPending())),
          exhaustMap(() =>
            dashboardService.publish().pipe(
              switchMap(() => dashboardService.getReadiness()),
              tap((readiness) => {
                patchState(store, { readiness, publishSuccess: true }, setFulfilled());
              }),
              catchError(() => {
                patchState(store, setError('Erreur lors de la publication'));
                return EMPTY;
              })
            )
          )
        )
      ),
      unpublish: rxMethod<void>(
        pipe(
          tap(() => patchState(store, { unpublishSuccess: false }, setPending())),
          exhaustMap(() =>
            dashboardService.unpublish().pipe(
              switchMap(() => dashboardService.getReadiness()),
              tap((readiness) => {
                patchState(store, { readiness, unpublishSuccess: true }, setFulfilled());
              }),
              catchError(() => {
                patchState(store, setError('Erreur lors de la dépublication'));
                return EMPTY;
              })
            )
          )
        )
      ),
    })
  ),
  withHooks((store) => ({
    onInit() {
      store.loadReadiness();
    },
  }))
);
```

- [ ] **Step 2: Verify frontend compiles**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/dashboard/store/dashboard.store.ts
git commit -m "feat: add DashboardStore with readiness and activity methods"
```

---

## Task 6: Frontend — i18n keys

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add French dashboard keys**

In `fr.json`, replace the existing `"pro" > "dashboard"` section (`"title": "Tableau de bord"`) with:

```json
"dashboard": {
  "title": "Tableau de bord",
  "draft": "Brouillon",
  "active": "En ligne",
  "checklist": {
    "title": "Configurez votre salon",
    "name": "Nom du salon",
    "categories": "Au moins une catégorie",
    "cares": "Au moins un soin actif",
    "openingHours": "Horaires d'ouverture"
  },
  "publish": "Publier mon salon",
  "publishDisabledHint": "Complétez les étapes ci-dessus pour publier votre salon",
  "publishSuccess": "Votre salon est en ligne !",
  "publishError": "Impossible de publier le salon",
  "unpublish": "Dépublier",
  "unpublishConfirmTitle": "Dépublier votre salon ?",
  "unpublishConfirmBody": "Votre salon ne sera plus visible sur la plateforme. Les rendez-vous existants seront maintenus.",
  "unpublishConfirmAction": "Dépublier",
  "unpublishSuccess": "Votre salon a été dépublié",
  "todayBookings": "RDV aujourd'hui",
  "weekBookings": "RDV cette semaine",
  "recentBookings": "Dernières réservations",
  "noBookings": "Aucune réservation pour le moment",
  "viewSalon": "Voir mon salon"
}
```

- [ ] **Step 2: Add English dashboard keys**

In `en.json`, replace the existing `"pro" > "dashboard"` section (`"title": "Dashboard"`) with:

```json
"dashboard": {
  "title": "Dashboard",
  "draft": "Draft",
  "active": "Online",
  "checklist": {
    "title": "Set up your salon",
    "name": "Salon name",
    "categories": "At least one category",
    "cares": "At least one active care",
    "openingHours": "Opening hours"
  },
  "publish": "Publish my salon",
  "publishDisabledHint": "Complete the steps above to publish your salon",
  "publishSuccess": "Your salon is now online!",
  "publishError": "Unable to publish salon",
  "unpublish": "Unpublish",
  "unpublishConfirmTitle": "Unpublish your salon?",
  "unpublishConfirmBody": "Your salon will no longer be visible on the platform. Existing appointments will be kept.",
  "unpublishConfirmAction": "Unpublish",
  "unpublishSuccess": "Your salon has been unpublished",
  "todayBookings": "Today's appointments",
  "weekBookings": "This week's appointments",
  "recentBookings": "Recent bookings",
  "noBookings": "No bookings yet",
  "viewSalon": "View my salon"
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat: add pro dashboard i18n keys (fr + en)"
```

---

## Task 7: Frontend — Dashboard component (DRAFT view)

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-dashboard.component.ts` — full rewrite
- Create: `frontend/src/app/pages/pro/pro-dashboard.component.html`
- Create: `frontend/src/app/pages/pro/pro-dashboard.component.scss`

- [ ] **Step 1: Create the template file**

```html
<div class="dashboard-container p-6 max-w-3xl mx-auto">
  @if (store.isPending()) {
    <div class="flex justify-center py-12">
      <mat-spinner diameter="40"></mat-spinner>
    </div>
  } @else if (store.readiness()) {
    <!-- Header -->
    <div class="flex items-center gap-3 mb-6">
      <h1 class="text-2xl font-medium text-neutral-800">
        {{ 'pro.dashboard.title' | transloco }}
      </h1>
      @if (store.isDraft()) {
        <span class="mat-mdc-chip draft-chip">{{ 'pro.dashboard.draft' | transloco }}</span>
      } @else if (store.isActive()) {
        <span class="mat-mdc-chip active-chip">{{ 'pro.dashboard.active' | transloco }}</span>
      }
    </div>

    @if (store.isDraft()) {
      <!-- DRAFT: Checklist -->
      <mat-card class="mb-6">
        <mat-card-header>
          <mat-card-title>{{ 'pro.dashboard.checklist.title' | transloco }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <mat-nav-list>
            <a mat-list-item routerLink="/pro/salon">
              <mat-icon matListItemIcon [class.done]="store.readiness()!.name">
                {{ store.readiness()!.name ? 'check_circle' : 'radio_button_unchecked' }}
              </mat-icon>
              <span matListItemTitle>{{ 'pro.dashboard.checklist.name' | transloco }}</span>
              <mat-icon matListItemMeta>chevron_right</mat-icon>
            </a>
            <a mat-list-item routerLink="/pro/categories">
              <mat-icon matListItemIcon [class.done]="store.readiness()!.hasCategory">
                {{ store.readiness()!.hasCategory ? 'check_circle' : 'radio_button_unchecked' }}
              </mat-icon>
              <span matListItemTitle>{{ 'pro.dashboard.checklist.categories' | transloco }}</span>
              <mat-icon matListItemMeta>chevron_right</mat-icon>
            </a>
            <a mat-list-item routerLink="/pro/cares">
              <mat-icon matListItemIcon [class.done]="store.readiness()!.hasActiveCare">
                {{ store.readiness()!.hasActiveCare ? 'check_circle' : 'radio_button_unchecked' }}
              </mat-icon>
              <span matListItemTitle>{{ 'pro.dashboard.checklist.cares' | transloco }}</span>
              <mat-icon matListItemMeta>chevron_right</mat-icon>
            </a>
            <a mat-list-item routerLink="/pro/availability">
              <mat-icon matListItemIcon [class.done]="store.readiness()!.hasOpeningHours">
                {{ store.readiness()!.hasOpeningHours ? 'check_circle' : 'radio_button_unchecked' }}
              </mat-icon>
              <span matListItemTitle>{{ 'pro.dashboard.checklist.openingHours' | transloco }}</span>
              <mat-icon matListItemMeta>chevron_right</mat-icon>
            </a>
          </mat-nav-list>
        </mat-card-content>
      </mat-card>

      <div class="publish-section text-center">
        <button
          mat-raised-button
          color="primary"
          [disabled]="!store.canPublish()"
          (click)="onPublish()"
        >
          {{ 'pro.dashboard.publish' | transloco }}
        </button>
        @if (!store.canPublish()) {
          <p class="text-sm text-neutral-500 mt-2">
            {{ 'pro.dashboard.publishDisabledHint' | transloco }}
          </p>
        }
      </div>
    } @else if (store.isActive()) {
      <!-- ACTIVE: Activity summary -->
      <div class="grid grid-cols-2 gap-4 mb-6">
        <mat-card>
          <mat-card-content class="text-center py-4">
            <p class="text-3xl font-bold text-primary">{{ store.todayCount() }}</p>
            <p class="text-sm text-neutral-500">{{ 'pro.dashboard.todayBookings' | transloco }}</p>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content class="text-center py-4">
            <p class="text-3xl font-bold text-primary">{{ store.weekCount() }}</p>
            <p class="text-sm text-neutral-500">{{ 'pro.dashboard.weekBookings' | transloco }}</p>
          </mat-card-content>
        </mat-card>
      </div>

      <mat-card class="mb-6">
        <mat-card-header>
          <mat-card-title>{{ 'pro.dashboard.recentBookings' | transloco }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (store.recentBookings().length === 0) {
            <p class="text-neutral-500 py-4">{{ 'pro.dashboard.noBookings' | transloco }}</p>
          } @else {
            <mat-list>
              @for (booking of store.recentBookings(); track booking.id) {
                <mat-list-item>
                  <span matListItemTitle>{{ booking.care.name }}</span>
                  <span matListItemLine>
                    {{ booking.appointmentDate }} · {{ booking.appointmentTime | slice:0:5 }}
                    · {{ booking.user.name }}
                  </span>
                </mat-list-item>
              }
            </mat-list>
          }
        </mat-card-content>
      </mat-card>

      <div class="flex gap-3">
        <a mat-raised-button color="primary" [href]="salonUrl" target="_blank">
          {{ 'pro.dashboard.viewSalon' | transloco }}
        </a>
        <button mat-stroked-button color="warn" (click)="onUnpublish()">
          {{ 'pro.dashboard.unpublish' | transloco }}
        </button>
      </div>
    }
  }
</div>
```

- [ ] **Step 2: Create the styles file**

```scss
.draft-chip {
  background-color: var(--mat-sys-surface-variant);
  color: var(--mat-sys-on-surface-variant);
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 0.75rem;
  font-weight: 500;
}

.active-chip {
  background-color: #dcfce7;
  color: #166534;
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 0.75rem;
  font-weight: 500;
}

.done {
  color: #16a34a;
}

.text-primary {
  color: var(--mat-sys-primary);
}
```

- [ ] **Step 3: Rewrite the component TypeScript**

```typescript
import { Component, computed, effect, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DashboardStore } from '../../features/dashboard/store/dashboard.store';
import { ConfirmDialogComponent } from './confirm-dialog.component';

@Component({
  selector: 'app-pro-dashboard',
  standalone: true,
  imports: [
    MatCardModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    RouterLink,
    SlicePipe,
    TranslocoPipe,
  ],
  providers: [DashboardStore],
  templateUrl: './pro-dashboard.component.html',
  styleUrl: './pro-dashboard.component.scss',
})
export class ProDashboardComponent {
  readonly store = inject(DashboardStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  readonly salonUrl = computed(() => {
    const readiness = this.store.readiness();
    return readiness ? '/salon/' + readiness.slug : '';
  });

  constructor() {
    // Load activity when salon becomes active
    effect(() => {
      if (this.store.isActive()) {
        this.store.loadActivity();
      }
    });

    // Show snackbar on publish success
    effect(() => {
      if (this.store.publishSuccess()) {
        this.snackBar.open(
          this.transloco.translate('pro.dashboard.publishSuccess'),
          undefined,
          { duration: 3000 }
        );
      }
    });

    // Show snackbar on unpublish success
    effect(() => {
      if (this.store.unpublishSuccess()) {
        this.snackBar.open(
          this.transloco.translate('pro.dashboard.unpublishSuccess'),
          undefined,
          { duration: 3000 }
        );
      }
    });
  }

  onPublish(): void {
    this.store.publish();
  }

  onUnpublish(): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: this.transloco.translate('pro.dashboard.unpublishConfirmTitle'),
        body: this.transloco.translate('pro.dashboard.unpublishConfirmBody'),
        action: this.transloco.translate('pro.dashboard.unpublishConfirmAction'),
      },
    });

    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.store.unpublish();
      }
    });
  }
}
```

- [ ] **Step 4: Verify frontend compiles**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: May fail because `ConfirmDialogComponent` doesn't exist yet — that's Task 8.

- [ ] **Step 5: Commit (WIP — depends on Task 8)**

Hold commit until Task 8 is done.

---

## Task 8: Frontend — Confirm dialog component

**Files:**
- Create: `frontend/src/app/pages/pro/confirm-dialog.component.ts`

- [ ] **Step 1: Create ConfirmDialogComponent**

```typescript
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

export interface ConfirmDialogData {
  title: string;
  body: string;
  action: string;
}

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p>{{ data.body }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | transloco }}</button>
      <button mat-raised-button color="warn" [mat-dialog-close]="true">
        {{ data.action }}
      </button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
```

Wait — this uses `transloco` pipe but doesn't import it. Let me fix:

```typescript
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';

export interface ConfirmDialogData {
  title: string;
  body: string;
  action: string;
}

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p>{{ data.body }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | transloco }}</button>
      <button mat-raised-button color="warn" [mat-dialog-close]="true">
        {{ data.action }}
      </button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
```

- [ ] **Step 2: Verify frontend compiles**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit all dashboard frontend changes**

```bash
git add frontend/src/app/pages/pro/pro-dashboard.component.ts \
       frontend/src/app/pages/pro/pro-dashboard.component.html \
       frontend/src/app/pages/pro/pro-dashboard.component.scss \
       frontend/src/app/pages/pro/confirm-dialog.component.ts
git commit -m "feat: rewrite pro dashboard with checklist (DRAFT) and activity (ACTIVE) views"
```

---

## Task 9: Backend — Add slug to readiness response

The frontend needs the tenant slug to build the "View my salon" link. We need to add it to `TenantReadinessResponse`.

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/dto/TenantReadinessResponse.java`
- Modify: `backend/src/main/java/com/prettyface/app/tenant/app/TenantReadinessService.java`
- Modify: `backend/src/test/java/com/prettyface/app/tenant/app/TenantReadinessServiceTests.java`
- Modify: `frontend/src/app/features/dashboard/models/dashboard.model.ts`
- Modify: `frontend/src/app/pages/pro/pro-dashboard.component.ts`

- [ ] **Step 1: Add slug field to TenantReadinessResponse**

Replace the record with:

```java
public record TenantReadinessResponse(
    String slug,
    boolean name,
    boolean hasCategory,
    boolean hasActiveCare,
    boolean hasOpeningHours,
    boolean canPublish,
    String status
) {}
```

- [ ] **Step 2: Update TenantReadinessService to include slug**

In `getReadiness`, update the return to include `tenant.getSlug()`:

```java
return new TenantReadinessResponse(
    tenant.getSlug(),
    name, hasCategory, hasActiveCare, hasOpeningHours,
    canPublish, tenant.getStatus().name()
);
```

- [ ] **Step 3: Update tests to match new constructor**

In `TenantReadinessServiceTests`, add `.slug("mon-salon")` to each `Tenant.builder()` call. Add an assertion to the first test:

```java
assertThat(result.slug()).isEqualTo("mon-salon");
```

- [ ] **Step 4: Update frontend model**

In `dashboard.model.ts`, add `slug` to the interface:

```typescript
export interface TenantReadiness {
  slug: string;
  name: boolean;
  hasCategory: boolean;
  hasActiveCare: boolean;
  hasOpeningHours: boolean;
  canPublish: boolean;
  status: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';
}
```

- [ ] **Step 5: Fix salonUrl computed in ProDashboardComponent**

Replace the `salonUrl` computed with:

```typescript
readonly salonUrl = computed(() => {
  const readiness = this.store.readiness();
  return readiness ? '/salon/' + readiness.slug : '';
});
```

- [ ] **Step 6: Run backend tests**

Run: `cd backend && mvn test -pl . -Dtest=TenantReadinessServiceTests -q`
Expected: 4 tests PASS

- [ ] **Step 7: Verify both compile**

Run: `cd backend && mvn compile -q && cd ../frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Both BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/web/dto/TenantReadinessResponse.java \
       backend/src/main/java/com/prettyface/app/tenant/app/TenantReadinessService.java \
       backend/src/test/java/com/prettyface/app/tenant/app/TenantReadinessServiceTests.java \
       frontend/src/app/features/dashboard/models/dashboard.model.ts \
       frontend/src/app/pages/pro/pro-dashboard.component.ts
git commit -m "feat: add tenant slug to readiness response for salon link"
```

---

## Task 10: Integration — Add common.cancel i18n key if missing

**Files:**
- Modify: `frontend/public/i18n/fr.json` (if `common.cancel` missing)
- Modify: `frontend/public/i18n/en.json` (if `common.cancel` missing)

- [ ] **Step 1: Check if common.cancel exists**

Run: `grep -r '"cancel"' frontend/public/i18n/`

If it already exists under `common`, skip this task. If not, add:

In `fr.json` under `"common"`:
```json
"cancel": "Annuler"
```

In `en.json` under `"common"`:
```json
"cancel": "Cancel"
```

- [ ] **Step 2: Commit if changed**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat: add common.cancel i18n key"
```

---

## Task 11: End-to-end smoke test

- [ ] **Step 1: Start backend**

Run: `cd backend && mvn spring-boot:run`

- [ ] **Step 2: Test readiness endpoint**

Run: `curl -s -u dev:dev http://localhost:8080/api/pro/tenant/readiness | python3 -m json.tool`

Expected: JSON with `name`, `hasCategory`, `hasActiveCare`, `hasOpeningHours`, `canPublish`, `status` fields.

- [ ] **Step 3: Test publish endpoint (should fail if incomplete)**

Run: `curl -s -u dev:dev -X PUT http://localhost:8080/api/pro/tenant/publish -H 'Content-Type: application/json' | python3 -m json.tool`

Expected: 422 with `missing` array listing conditions that aren't met, OR 200 if dev tenant is fully configured.

- [ ] **Step 4: Test unpublish endpoint**

Run: `curl -s -u dev:dev -X PUT http://localhost:8080/api/pro/tenant/unpublish -H 'Content-Type: application/json' -w '\n%{http_code}'`

Expected: 200

- [ ] **Step 5: Start frontend and verify dashboard**

Open `http://localhost:4300/pro/dashboard` in browser. Verify:
- Checklist is visible with correct states
- Publish button is enabled/disabled based on readiness
- If published: activity cards and recent bookings are shown
