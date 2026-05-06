# Vitrine Preview (Jalon 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow a pro owner to preview their own salon storefront at `/salon/:slug` before publication, with a banner indicating "preview mode" and quick actions (back to dashboard, publish).

**Architecture:** Backend relaxes the `ACTIVE` filter on `GET /api/salon/{slug}` only — when the salon is `DRAFT`, return the storefront DTO if (and only if) the authenticated user is the tenant owner. Other DRAFT-status endpoints (booking, slot availability) stay locked since they shouldn't be functional pre-publication. The DTO gains a `status` field so the frontend can switch into preview mode (banner + disabled booking) without a separate request.

**Tech Stack:** Spring Boot 3.5.4, JUnit 5 + Mockito; Angular 20 (signals, standalone), Transloco, Material `MatSnackBar`.

**Spec reference:** `docs/superpowers/specs/2026-05-06-vitrine-preview-onboarding-pc-design.md` — Jalon 2 section.

**Branch baseline:** Built on top of Jalon 1 (`feat/onboarding-indicator`). If Jalon 1 has been merged, branch from `main`. Otherwise, branch from `feat/onboarding-indicator` and rebase if needed.

---

## File Structure

**Backend — modified files:**

| Path | Change |
|------|--------|
| `backend/src/main/java/com/prettyface/app/tenant/web/dto/PublicSalonResponse.java` | Add `String status` field. |
| `backend/src/main/java/com/prettyface/app/tenant/web/mapper/TenantMapper.java` | Pass `tenant.getStatus().name()` into `PublicSalonResponse`. |
| `backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java` | `getSalon` accepts `@AuthenticationPrincipal UserPrincipal principal` and authorizes DRAFT for the tenant owner. |

**Backend — new tests:**

| Path | Responsibility |
|------|----------------|
| `backend/src/test/java/com/prettyface/app/tenant/web/PublicSalonControllerPreviewTests.java` | Owner DRAFT access, anonymous DRAFT 404, non-owner DRAFT 404, ACTIVE works for everyone, status field in DTO, SUSPENDED still 404. |

**Frontend — modified files:**

| Path | Change |
|------|--------|
| `frontend/src/app/features/salon-profile/models/salon-profile.model.ts` | Add `status: 'DRAFT' \| 'ACTIVE' \| 'SUSPENDED' \| 'DELETED'` to `PublicSalonResponse`. |
| `frontend/src/app/pages/salon/salon-page.component.ts` | Add `isPreviewMode` computed; conditionally render banner; disable booking flow in preview. |
| `frontend/src/app/pages/salon/salon-page.component.html` | Render `<app-preview-banner>` when in preview mode. |
| `frontend/src/app/pages/salon/salon-page.component.scss` | Add small spacing tweak when banner is present. |
| `frontend/src/app/pages/pro/pro-dashboard.component.html` | Add "Aperçu" anchor button in `publish-section`. |
| `frontend/src/app/pages/pro/pro-dashboard.component.ts` | Already exposes `salonUrl` computed; nothing to add. |
| `frontend/public/i18n/fr.json` | Add `salon.preview.banner.*` and `pro.dashboard.preview` keys. |
| `frontend/public/i18n/en.json` | Same keys translated. |

**Frontend — new files:**

| Path | Responsibility |
|------|----------------|
| `frontend/src/app/shared/uis/preview-banner/preview-banner.component.ts` | Standalone component shown above the storefront in preview mode. Provides its own `DashboardStore` instance for the publish action. |
| `frontend/src/app/shared/uis/preview-banner/preview-banner.component.html` | Banner template: icon, label, two buttons. |
| `frontend/src/app/shared/uis/preview-banner/preview-banner.component.scss` | Sticky-top, rose-pale background, rose border-bottom. |
| `frontend/src/app/shared/uis/preview-banner/preview-banner.component.spec.ts` | Unit tests. |

---

## Conventions used by this codebase

(Same as Jalon 1; condensed reminder.)

- **Frontend**: Angular 20 standalone, signals, `inject()`, `@if` / `@for`, Transloco for i18n. Both `fr.json` and `en.json` updated together. Tests with Karma/Jasmine, functional providers.
- **Backend**: Spring Boot 3.5.4, package-by-feature. Controllers under `*/web`, DTOs under `*/web/dto`, mappers under `*/web/mapper`. Tests with JUnit 5 + Mockito.
- **Multi-tenancy**: `TenantContext.setCurrentTenant(slug)` switches schema; always `TenantContext.clear()` in `finally`. Existing pattern in `PublicSalonController#getSalon`.
- **Auth**: `@AuthenticationPrincipal UserPrincipal principal` injects the principal (null when anonymous since the route is `permitAll`).
- **Conventional Commits**: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.

---

## Task 1: Add `status` to PublicSalonResponse DTO

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/dto/PublicSalonResponse.java`

The DTO is a Java `record`. Recording an extra field changes its constructor signature, so any caller that constructs it manually must be updated in lockstep — the `TenantMapper.toPublicResponse` is the only caller (see Task 2).

- [ ] **Step 1: Add the `status` field**

Open `backend/src/main/java/com/prettyface/app/tenant/web/dto/PublicSalonResponse.java`. Replace the entire content with:

```java
package com.prettyface.app.tenant.web.dto;

import java.util.List;

public record PublicSalonResponse(
        String name,
        String slug,
        String status,
        String description,
        String logoUrl,
        String heroImageUrl,
        List<PublicCategoryDto> categories,
        String addressStreet,
        String addressPostalCode,
        String addressCity,
        String addressCountry,
        String phone,
        String contactEmail
) {}
```

(Just inserts `String status` as the third field. The frontend type mirrors this position.)

- [ ] **Step 2: Verify compile fails (mapper out of sync)**

```bash
cd backend && ./mvnw compile -q
```
Expected: FAIL — `TenantMapper.toPublicResponse` constructor mismatch. This is the cue for Task 2.

- [ ] **Step 3: Do not commit yet**

Task 2 fixes the mapper. Commit both together at the end of Task 2.

---

## Task 2: Pass tenant status into the mapper

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/mapper/TenantMapper.java:64-77`

- [ ] **Step 1: Update `toPublicResponse`**

In `TenantMapper.java`, find the `toPublicResponse` method. Locate the `return new PublicSalonResponse(...)` block (around line 64-77). Replace it with:

```java
        return new PublicSalonResponse(
                tenant.getName(),
                tenant.getSlug(),
                tenant.getStatus().name(),
                tenant.getDescription(),
                logoUrl,
                heroImageUrl,
                categoryDtos,
                tenant.getAddressStreet(),
                tenant.getAddressPostalCode(),
                tenant.getAddressCity(),
                tenant.getAddressCountry(),
                tenant.getPhone(),
                tenant.getContactEmail()
        );
```

(`tenant.getStatus()` returns the `TenantStatus` enum; `.name()` gives the string `"DRAFT"`, `"ACTIVE"`, etc.)

- [ ] **Step 2: Verify compile passes**

```bash
cd backend && ./mvnw compile -q
```
Expected: success.

- [ ] **Step 3: Run existing tenant tests to confirm no regression**

```bash
cd backend && ./mvnw test -Dtest='Public*' -q
```
Expected: existing PublicSalonController tests still pass. They don't assert on `status`, so they should be unaffected.

- [ ] **Step 4: Commit DTO + mapper change together**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/web/dto/PublicSalonResponse.java backend/src/main/java/com/prettyface/app/tenant/web/mapper/TenantMapper.java
git commit -m "feat(salon-public): expose tenant status in PublicSalonResponse"
```

---

## Task 3: Allow owner to preview DRAFT salon

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java:82-97`

Only the `getSalon` method changes. All other endpoints (`opening-hours`, `closed-days`, `blocked-slots`, `available-slots`, `book`, `cancel`) keep their `status == ACTIVE` guard — booking against a DRAFT salon is forbidden, but the storefront *view* is allowed for the owner.

- [ ] **Step 1: Update `getSalon` to accept the principal**

Open `backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java`. Add the import near the existing imports (around line 36):

```java
import org.springframework.security.core.annotation.AuthenticationPrincipal;
```

(Check first — it may already be present because `cancelBooking` uses it.)

- [ ] **Step 2: Replace the `getSalon` method body**

Locate `getSalon` (lines 82-97):

```java
    @GetMapping("/{slug}")
    public ResponseEntity<PublicSalonResponse> getSalon(@PathVariable String slug) {
        return tenantService.findBySlug(slug)
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .map(tenant -> {
                    // Set tenant context to query the correct schema
                    TenantContext.setCurrentTenant(tenant.getSlug());
                    try {
                        List<Category> categories = categoryRepository.findAllWithCaresFull();
                        return ResponseEntity.ok(TenantMapper.toPublicResponse(tenant, categories));
                    } finally {
                        TenantContext.clear();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
```

Replace with:

```java
    @GetMapping("/{slug}")
    public ResponseEntity<PublicSalonResponse> getSalon(
            @PathVariable String slug,
            @AuthenticationPrincipal UserPrincipal principal) {
        return tenantService.findBySlug(slug)
                .filter(tenant -> canViewStorefront(tenant, principal))
                .map(tenant -> {
                    // Set tenant context to query the correct schema
                    TenantContext.setCurrentTenant(tenant.getSlug());
                    try {
                        List<Category> categories = categoryRepository.findAllWithCaresFull();
                        return ResponseEntity.ok(TenantMapper.toPublicResponse(tenant, categories));
                    } finally {
                        TenantContext.clear();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Storefront access policy:
     * - ACTIVE salons are visible to everyone (current behavior).
     * - DRAFT salons are visible only to their authenticated owner (preview mode).
     * - SUSPENDED / DELETED salons are not visible to anyone.
     */
    private boolean canViewStorefront(
            com.prettyface.app.tenant.domain.Tenant tenant,
            UserPrincipal principal) {
        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            return true;
        }
        if (tenant.getStatus() == TenantStatus.DRAFT
                && principal != null
                && tenant.getOwnerId().equals(principal.getId())) {
            return true;
        }
        return false;
    }
```

- [ ] **Step 3: Verify compile**

```bash
cd backend && ./mvnw compile -q
```
Expected: success.

- [ ] **Step 4: Run existing tests to confirm no regression**

```bash
cd backend && ./mvnw test -Dtest='Public*' -q
```
Expected: existing tests still pass (anonymous + ACTIVE = same response as before).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java
git commit -m "feat(salon-public): allow owner to preview their own DRAFT storefront"
```

---

## Task 4: Backend test — preview access matrix

**Files:**
- Create: `backend/src/test/java/com/prettyface/app/tenant/web/PublicSalonControllerPreviewTests.java`

Tests the `getSalon` endpoint without spinning Spring (direct controller invocation) — same pattern as `PublicSalonControllerCancelBookingTests.java`.

- [ ] **Step 1: Write the test class**

Create `backend/src/test/java/com/prettyface/app/tenant/web/PublicSalonControllerPreviewTests.java`:

```java
package com.prettyface.app.tenant.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.availability.app.AvailabilityService;
import com.prettyface.app.availability.app.BlockedSlotService;
import com.prettyface.app.availability.app.ClosedDaysService;
import com.prettyface.app.availability.app.HolidayAvailabilityService;
import com.prettyface.app.availability.app.SlotAvailabilityService;
import com.prettyface.app.bookings.app.CareBookingService;
import com.prettyface.app.bookings.app.ClientBookingHistoryService;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.employee.app.EmployeeService;
import com.prettyface.app.post.app.PostService;
import com.prettyface.app.tenant.app.TenantService;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.domain.TenantStatus;
import com.prettyface.app.tenant.web.dto.PublicSalonResponse;
import com.prettyface.app.users.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Storefront access policy tests.
 *
 * - ACTIVE salon → visible to anyone (anonymous + any logged-in user).
 * - DRAFT salon → visible only to the tenant owner (preview mode).
 * - SUSPENDED / DELETED salon → 404 for everyone.
 *
 * Direct controller invocation (no Spring context) — pins behavior at the
 * controller layer where the policy lives.
 */
@ExtendWith(MockitoExtension.class)
class PublicSalonControllerPreviewTests {

    @Mock private TenantService tenantService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AvailabilityService availabilityService;
    @Mock private BlockedSlotService blockedSlotService;
    @Mock private SlotAvailabilityService slotAvailabilityService;
    @Mock private HolidayAvailabilityService holidayAvailabilityService;
    @Mock private ClosedDaysService closedDaysService;
    @Mock private CareBookingService careBookingService;
    @Mock private UserRepository userRepository;
    @Mock private ClientBookingHistoryService clientBookingHistoryService;
    @Mock private EmployeeService employeeService;
    @Mock private PostService postService;

    @InjectMocks
    private PublicSalonController controller;

    private static final long OWNER_ID = 42L;
    private static final long OTHER_USER_ID = 99L;
    private static final String SLUG = "demo";

    @BeforeEach
    void setUp() {
        // Categories repo is invoked when we successfully serve the storefront.
        // We don't care about the content; an empty list is enough.
        lenient().when(categoryRepository.findAllWithCaresFull()).thenReturn(List.of());
    }

    private Tenant tenantWithStatus(TenantStatus status) {
        Tenant t = Tenant.builder()
                .id(1L)
                .slug(SLUG)
                .name("Demo Salon")
                .ownerId(OWNER_ID)
                .status(status)
                .build();
        return t;
    }

    private UserPrincipal principal(long id) {
        // UserPrincipal exposes getId(); use the simplest builder available.
        // If UserPrincipal is a Lombok @Builder, the .build() call works; otherwise
        // a direct constructor will be used.
        return UserPrincipal.builder().id(id).build();
    }

    @Test
    void anonymousCanViewActiveSalon() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.ACTIVE)));

        ResponseEntity<PublicSalonResponse> response = controller.getSalon(SLUG, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("ACTIVE");
    }

    @Test
    void anonymousGetsNotFoundForDraftSalon() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.DRAFT)));

        ResponseEntity<PublicSalonResponse> response = controller.getSalon(SLUG, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void ownerCanPreviewDraftSalon() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.DRAFT)));

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, principal(OWNER_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("DRAFT");
    }

    @Test
    void nonOwnerGetsNotFoundForDraftSalon() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.DRAFT)));

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, principal(OTHER_USER_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void suspendedSalonIsHiddenFromOwner() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.SUSPENDED)));

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, principal(OWNER_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownSlugReturnsNotFound() {
        when(tenantService.findBySlug("unknown")).thenReturn(Optional.empty());

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon("unknown", principal(OWNER_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
```

- [ ] **Step 2: Verify the `UserPrincipal` builder pattern matches the codebase**

```bash
grep -n 'class UserPrincipal\|builder\|@Builder' backend/src/main/java/com/prettyface/app/auth/UserPrincipal.java
```

If `UserPrincipal` is annotated with `@Builder` (Lombok), the test code as-written works.

If `UserPrincipal` does NOT have `@Builder`, replace `UserPrincipal.builder().id(id).build()` calls in the test with the appropriate constructor or factory. Common alternatives:
- `new UserPrincipal(id, null, null, null)` — fill nulls per the constructor signature.
- A static factory like `UserPrincipal.of(id)`.

Read the file to determine the right form, then edit the `principal()` helper accordingly.

- [ ] **Step 3: Run the new test class**

```bash
cd backend && ./mvnw test -Dtest=PublicSalonControllerPreviewTests -q
```
Expected: PASS — 6 tests.

- [ ] **Step 4: Run a wider scope to confirm no regression**

```bash
cd backend && ./mvnw test -Dtest='PublicSalon*' -q
```
Expected: PASS — all `PublicSalon*` tests still green (cancel, validation, closed-days, preview).

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/prettyface/app/tenant/web/PublicSalonControllerPreviewTests.java
git commit -m "test(salon-public): cover preview access matrix (owner DRAFT, others 404)"
```

---

## Task 5: Frontend — extend `PublicSalonResponse` type with `status`

**Files:**
- Modify: `frontend/src/app/features/salon-profile/models/salon-profile.model.ts:66-79`

- [ ] **Step 1: Update the TypeScript type**

Open `frontend/src/app/features/salon-profile/models/salon-profile.model.ts`. Find the `PublicSalonResponse` interface (around line 66). Replace it with:

```typescript
export interface PublicSalonResponse {
  name: string;
  slug: string;
  status: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';
  description: string | null;
  logoUrl: string | null;
  heroImageUrl: string | null;
  categories: PublicCategoryDto[];
  addressStreet: string | null;
  addressPostalCode: string | null;
  addressCity: string | null;
  addressCountry: string | null;
  phone: string | null;
  contactEmail: string | null;
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors. The existing salon-page consumes this type but doesn't yet read `status`; existing fields are unchanged so no regression.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/salon-profile/models/salon-profile.model.ts
git commit -m "feat(salon-profile): add status to PublicSalonResponse type"
```

---

## Task 6: i18n keys (FR + EN) for the preview banner

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add FR keys**

Open `frontend/public/i18n/fr.json`. Locate the existing `"salon"` block (the one with `caresTab`, `postsTab`, `contactTab`, `noContact`, `public`). Inside it, add a new sibling block `"preview"` (alongside `"public"`):

```json
    "preview": {
      "banner": {
        "title": "Mode aperçu",
        "subtitle": "Votre vitrine n'est pas encore publique",
        "backToDashboard": "Retour au tableau de bord",
        "publish": "Publier",
        "publishing": "Publication…",
        "bookingDisabled": "La réservation est désactivée en mode aperçu"
      }
    }
```

(Don't forget the comma after the previous sibling `"public": { ... }` so the JSON stays valid.)

Then locate the existing `"pro"` → `"dashboard"` block and add the key `"preview"` near `"publish"`:

```json
        "preview": "Aperçu",
```

(Place it just before or after the existing `"publish": "Publier mon salon"` line. Don't duplicate; verify the key doesn't exist yet.)

- [ ] **Step 2: Add EN keys (mirror)**

Open `frontend/public/i18n/en.json`. Add under `"salon"`:

```json
    "preview": {
      "banner": {
        "title": "Preview mode",
        "subtitle": "Your storefront is not yet public",
        "backToDashboard": "Back to dashboard",
        "publish": "Publish",
        "publishing": "Publishing…",
        "bookingDisabled": "Booking is disabled in preview mode"
      }
    }
```

And under `"pro"` → `"dashboard"`:

```json
        "preview": "Preview",
```

- [ ] **Step 3: Validate JSON**

```bash
python3 -m json.tool frontend/public/i18n/fr.json > /dev/null && echo "FR ok"
python3 -m json.tool frontend/public/i18n/en.json > /dev/null && echo "EN ok"
```
Expected: `FR ok`, `EN ok`.

- [ ] **Step 4: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat(i18n): add salon.preview.banner and pro.dashboard.preview keys"
```

---

## Task 7: PreviewBannerComponent

**Files:**
- Create: `frontend/src/app/shared/uis/preview-banner/preview-banner.component.ts`
- Create: `frontend/src/app/shared/uis/preview-banner/preview-banner.component.html`
- Create: `frontend/src/app/shared/uis/preview-banner/preview-banner.component.scss`
- Create: `frontend/src/app/shared/uis/preview-banner/preview-banner.component.spec.ts`

The banner component:
- Sticky-top, sits above the storefront content.
- Inputs: `slug` (string), `canPublish` (boolean), `isPublished` (boolean — when true, the banner's role shifts to "published — back to dashboard"; we keep this minimal for now and only render when `!isPublished`).

For Jalon 2 we keep the API simple: the banner takes `slug` and `canPublish` as inputs and emits a `published` event after a successful publish. The storefront page wires the banner's "Back to dashboard" navigation directly via routerLink.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/shared/uis/preview-banner/preview-banner.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { PreviewBannerComponent } from './preview-banner.component';
import { DashboardService } from '../../../features/dashboard/services/dashboard.service';

describe('PreviewBannerComponent', () => {
  let fixture: ComponentFixture<PreviewBannerComponent>;
  let component: PreviewBannerComponent;
  let dashboardSpy: jasmine.SpyObj<DashboardService>;

  beforeEach(() => {
    dashboardSpy = jasmine.createSpyObj<DashboardService>('DashboardService', [
      'publish',
      'getReadiness',
    ]);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DashboardService, useValue: dashboardSpy },
      ],
      imports: [
        PreviewBannerComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(PreviewBannerComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('slug', 'demo');
    fixture.componentRef.setInput('canPublish', false);
    fixture.detectChanges();
  });

  it('renders the back-to-dashboard link', () => {
    const link = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="banner-back"]'
    );
    expect(link).not.toBeNull();
    expect(link?.getAttribute('href')).toBe('/pro/dashboard');
  });

  it('hides the publish button when canPublish is false', () => {
    fixture.componentRef.setInput('canPublish', false);
    fixture.detectChanges();
    const button = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="banner-publish"]'
    );
    expect(button).toBeNull();
  });

  it('shows the publish button when canPublish is true', () => {
    fixture.componentRef.setInput('canPublish', true);
    fixture.detectChanges();
    const button = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="banner-publish"]'
    );
    expect(button).not.toBeNull();
  });

  it('emits published after successful publish', () => {
    dashboardSpy.publish.and.returnValue(of(void 0));
    let emitted = false;
    fixture.componentRef.setInput('canPublish', true);
    component.published.subscribe(() => (emitted = true));
    fixture.detectChanges();
    component.onPublish();
    expect(dashboardSpy.publish).toHaveBeenCalled();
    expect(emitted).toBe(true);
  });

  it('keeps the publish button enabled after a failed publish', () => {
    dashboardSpy.publish.and.returnValue(throwError(() => new Error('boom')));
    fixture.componentRef.setInput('canPublish', true);
    fixture.detectChanges();
    component.onPublish();
    expect(component.publishing()).toBe(false);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npm test -- --include='**/preview-banner.component.spec.ts' --watch=false
```
Expected: FAIL — module not found.

- [ ] **Step 3: Create the component TS**

Create `frontend/src/app/shared/uis/preview-banner/preview-banner.component.ts`:

```typescript
import { Component, EventEmitter, Output, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { DashboardService } from '../../../features/dashboard/services/dashboard.service';

@Component({
  selector: 'app-preview-banner',
  standalone: true,
  imports: [RouterLink, MatIconModule, MatButtonModule, TranslocoPipe],
  templateUrl: './preview-banner.component.html',
  styleUrl: './preview-banner.component.scss',
})
export class PreviewBannerComponent {
  readonly slug = input.required<string>();
  readonly canPublish = input<boolean>(false);

  /** Fires once the publish call returned successfully. */
  @Output() readonly published = new EventEmitter<void>();

  protected readonly publishing = signal(false);

  private readonly dashboardService = inject(DashboardService);

  onPublish(): void {
    if (this.publishing()) return;
    this.publishing.set(true);
    this.dashboardService.publish().subscribe({
      next: () => {
        this.publishing.set(false);
        this.published.emit();
      },
      error: () => {
        this.publishing.set(false);
      },
    });
  }
}
```

Note on store usage: the component injects `DashboardService` directly rather than `DashboardStore`. The spec discussed using a local `DashboardStore`, but inspection shows `DashboardService.publish()` is a thin HTTP wrapper — using the service avoids spinning up a local store with mostly-unused state for what is really a single fire-and-forget call. The `published` event lets the host (salon page) refresh its own state.

- [ ] **Step 4: Create the template**

Create `frontend/src/app/shared/uis/preview-banner/preview-banner.component.html`:

```html
<div class="preview-banner" role="status" aria-live="polite">
  <div class="banner-text">
    <mat-icon class="banner-icon" aria-hidden="true">visibility</mat-icon>
    <div class="banner-labels">
      <span class="banner-title">{{ 'salon.preview.banner.title' | transloco }}</span>
      <span class="banner-subtitle">{{ 'salon.preview.banner.subtitle' | transloco }}</span>
    </div>
  </div>

  <div class="banner-actions">
    <a
      routerLink="/pro/dashboard"
      class="banner-action banner-action-secondary"
      data-testid="banner-back"
    >
      {{ 'salon.preview.banner.backToDashboard' | transloco }}
    </a>
    @if (canPublish()) {
      <button
        type="button"
        class="banner-action banner-action-primary"
        (click)="onPublish()"
        [disabled]="publishing()"
        data-testid="banner-publish"
      >
        @if (publishing()) {
          {{ 'salon.preview.banner.publishing' | transloco }}
        } @else {
          {{ 'salon.preview.banner.publish' | transloco }}
        }
      </button>
    }
  </div>
</div>
```

- [ ] **Step 5: Create the styles**

Create `frontend/src/app/shared/uis/preview-banner/preview-banner.component.scss`:

```scss
:host {
  display: block;
  position: sticky;
  top: 80px; // sit just below the existing app header
  z-index: 25;
}

.preview-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 20px;
  background: #fdf3f7;
  border-bottom: 1px solid rgba(192, 0, 102, 0.18);
}

.banner-text {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.banner-icon {
  color: #c06;
  font-size: 22px;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

.banner-labels {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  min-width: 0;
}

.banner-title {
  font-size: 13px;
  font-weight: 600;
  color: #6b1d3f;
}

.banner-subtitle {
  font-size: 12px;
  color: #844962;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.banner-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.banner-action {
  display: inline-flex;
  align-items: center;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid transparent;
  text-decoration: none;
}

.banner-action-secondary {
  background: white;
  color: #6b1d3f;
  border-color: rgba(107, 29, 63, 0.25);
}

.banner-action-secondary:hover {
  background: #fff8fb;
}

.banner-action-primary {
  background: #c06;
  color: white;
  border-color: #c06;
}

.banner-action-primary:hover {
  background: #a05;
}

.banner-action-primary:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

@media (max-width: 600px) {
  .banner-subtitle { display: none; }
  .preview-banner { padding: 8px 12px; }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cd frontend && npm test -- --include='**/preview-banner.component.spec.ts' --watch=false
```
Expected: PASS — 5 specs.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/shared/uis/preview-banner/
git commit -m "feat(preview-banner): add sticky banner with back-to-dashboard and publish actions"
```

---

## Task 8: Wire the banner into the salon page

**Files:**
- Modify: `frontend/src/app/pages/salon/salon-page.component.ts`
- Modify: `frontend/src/app/pages/salon/salon-page.component.html`

The page already has a `salon = signal<PublicSalonResponse | null>(null)`. We add an `isPreviewMode` computed and render the banner above existing content.

**Important booking gating:** when in preview mode, the storefront's "Réserver" buttons should not open the booking dialog (preview = read-only). We disable the booking action conditionally.

- [ ] **Step 1: Update the component TS**

Open `frontend/src/app/pages/salon/salon-page.component.ts`. Find the existing class. After the `salon` signal declaration, add a computed:

```typescript
  protected readonly isPreviewMode = computed(() => {
    const s = this.salon();
    return !!s && s.status !== 'ACTIVE';
  });

  protected readonly canPublish = computed(() => {
    // The public DTO doesn't carry canPublish directly; we conservatively
    // show the publish button only when status is DRAFT and the storefront
    // was returned (which already implies the owner is logged in). Concrete
    // can-publish checks live on the dashboard's readiness endpoint, which
    // the banner does not call. Showing the button always-when-DRAFT is
    // acceptable: the backend will return 400 with a missing-fields list if
    // the request is invalid, and the dashboard remains the authoritative
    // place for the full checklist.
    return this.isPreviewMode();
  });
```

Make sure `computed` is imported from `@angular/core` at the top of the file (likely already there).

Add a method to handle the banner's `published` event:

```typescript
  protected onPublishedFromBanner(): void {
    // Refresh the salon DTO so the banner disappears (status flips to ACTIVE).
    const slug = this.salon()?.slug;
    if (!slug) return;
    this.salonService.getPublicBySlug(slug).subscribe({
      next: (salon) => this.salon.set(salon),
    });
  }
```

(`this.salonService` is the existing service that loaded the salon — verify the method name. If the existing fetch in the constructor uses a different method, reuse it.)

- [ ] **Step 2: Verify the salon-fetch method name**

Inspect the existing salon-page component. Find the constructor or `ngOnInit` block that fetches the salon. Note the service name and method (likely `salonProfileService.getPublicBySlug(slug)` or similar — check the actual code).

Update Step 1's `onPublishedFromBanner` to use the same service+method.

- [ ] **Step 3: Update the template**

Open `frontend/src/app/pages/salon/salon-page.component.html`. At the very top of the salon-rendering block (immediately inside `@if (salon()) { @if (salon(); as salon) { <div class="salon-page">` — BEFORE the `<section class="salon-hero">`), add:

```html
      @if (isPreviewMode()) {
        <app-preview-banner
          [slug]="salon.slug"
          [canPublish]="canPublish()"
          (published)="onPublishedFromBanner()"
        />
      }
```

Find each `<button mat-flat-button class="book-btn" (click)="onBook(care)">` (the booking buttons inside the cares section) and add a `[disabled]` binding plus a hint:

```html
                            <button
                              mat-flat-button
                              class="book-btn"
                              (click)="onBook(care)"
                              [disabled]="isPreviewMode()"
                              [attr.title]="isPreviewMode() ? ('salon.preview.banner.bookingDisabled' | transloco) : null"
                            >
                              {{ 'booking.book' | transloco }}
                            </button>
```

(Only the `[disabled]` and `[attr.title]` lines are added.)

- [ ] **Step 4: Add `PreviewBannerComponent` to the imports array**

In `salon-page.component.ts`, locate the `@Component({ imports: [...] })` array and add `PreviewBannerComponent`:

```typescript
import { PreviewBannerComponent } from '../../shared/uis/preview-banner/preview-banner.component';
// ...
@Component({
  // ...
  imports: [
    // existing imports...
    PreviewBannerComponent,
  ],
  // ...
})
```

- [ ] **Step 5: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 6: Run the salon-page tests**

```bash
cd frontend && npm test -- --include='**/salon-page.component.spec.ts' --watch=false
```
Expected: PASS. If failures complain about the new banner or `isPreviewMode`, the test setup needs to (a) mock `DashboardService` (since the banner injects it), or (b) provide the salon DTO with `status: 'ACTIVE'` so the banner doesn't render. Both are valid; pick the minimal fix.

If the test was previously rendering a salon DTO without `status`, you'll need to add `status: 'ACTIVE'` to the test data. Read the existing spec, find where the salon is mocked, add the field.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/pages/salon/salon-page.component.ts frontend/src/app/pages/salon/salon-page.component.html frontend/src/app/pages/salon/salon-page.component.spec.ts
git commit -m "feat(salon-page): show preview banner and disable booking when status is DRAFT"
```

(Only stage the spec file if you actually modified it in step 6.)

---

## Task 9: Add "Aperçu" button on the dashboard publish section

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-dashboard.component.html`

The dashboard already exposes a `salonUrl` computed (line 78-81 of the .ts file): `readiness ? '/salon/' + readiness.slug : ''`. We add an "Aperçu" anchor next to the existing Publish button.

- [ ] **Step 1: Update the publish-section template block**

Open `frontend/src/app/pages/pro/pro-dashboard.component.html`. Locate the `<div class="publish-section">` block (around line 112). Currently:

```html
        <div class="publish-section">
          <button
            mat-raised-button
            color="primary"
            data-testid="publish-btn"
            [disabled]="!store.canPublish()"
            (click)="onPublish()"
          >
            {{ 'pro.dashboard.publish' | transloco }}
          </button>
          @if (!store.canPublish()) {
            <p class="publish-hint">
              {{ 'pro.dashboard.publishDisabledHint' | transloco }}
            </p>
          }
        </div>
```

Replace with:

```html
        <div class="publish-section">
          <a
            mat-stroked-button
            data-testid="preview-btn"
            [routerLink]="salonUrl()"
          >
            <mat-icon>visibility</mat-icon>
            {{ 'pro.dashboard.preview' | transloco }}
          </a>
          <button
            mat-raised-button
            color="primary"
            data-testid="publish-btn"
            [disabled]="!store.canPublish()"
            (click)="onPublish()"
          >
            {{ 'pro.dashboard.publish' | transloco }}
          </button>
          @if (!store.canPublish()) {
            <p class="publish-hint">
              {{ 'pro.dashboard.publishDisabledHint' | transloco }}
            </p>
          }
        </div>
```

The `<a mat-stroked-button [routerLink]="salonUrl()">` will navigate to `/salon/<slug>` — which after Tasks 1-4 ships, returns the storefront in preview mode for the owner.

- [ ] **Step 2: Verify `RouterLink` is in the dashboard's imports**

```bash
grep -n 'RouterLink' frontend/src/app/pages/pro/pro-dashboard.component.ts
```

It should already be there (used by the checklist). If absent, add `RouterLink` to the `imports` array at the top of the component.

- [ ] **Step 3: Adjust the publish-section SCSS to space the two buttons**

Open `frontend/src/app/pages/pro/pro-dashboard.component.scss`. Find `.publish-section`:

```bash
grep -n 'publish-section' frontend/src/app/pages/pro/pro-dashboard.component.scss
```

If `.publish-section` is just a vertical stack, add (or merge) the following inside the rule:

```scss
.publish-section {
  // existing rules...
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
```

If the rule already has `display: flex`, leave it alone — the new anchor will fall in line.

- [ ] **Step 4: Verify TypeScript compiles and tests pass**

```bash
cd frontend && npx tsc --noEmit && npm test -- --include='**/pro-dashboard.component.spec.ts' --watch=false
```
Expected: no errors, tests pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/pro/pro-dashboard.component.html frontend/src/app/pages/pro/pro-dashboard.component.scss
git commit -m "feat(pro-dashboard): add Aperçu button next to Publier in publish section"
```

---

## Task 10: Final integration check

**Files:** none (manual verification + automated suite).

- [ ] **Step 1: Run the full backend test suite**

```bash
cd backend && ./mvnw test -q
```
Expected: PASS (no regressions in tenant, bookings, or any other module).

- [ ] **Step 2: Run the full frontend test suite (relevant scopes)**

```bash
cd frontend && npm test -- --include='**/preview-banner.component.spec.ts' --include='**/salon-page.component.spec.ts' --include='**/pro-dashboard.component.spec.ts' --include='**/onboarding-*.spec.ts' --include='**/pro-shell.component.spec.ts' --watch=false
```
Expected: PASS.

- [ ] **Step 3: TypeScript clean**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 4: Manual smoke check (DRAFT + owner)**

Start backend (`cd backend && ./mvnw spring-boot:run`) and frontend (`cd frontend && npm start`).

In a browser logged in as a pro whose tenant status is DRAFT:
1. Navigate to `/pro/dashboard`. Confirm the new "Aperçu" button appears next to "Publier".
2. Click "Aperçu". Confirm navigation to `/salon/<slug>` and that the preview banner appears at the top.
3. Confirm the "Réserver" buttons in the cares section are disabled (visually grayed out).
4. Click "Retour au tableau de bord". Confirm navigation back to `/pro/dashboard`.
5. If the dashboard reports `canPublish === true`, click "Publier" from the banner. Confirm:
   - The button shows "Publication…" then disappears once status flips to ACTIVE.
   - The banner itself disappears (auto-hide on `status === 'ACTIVE'`).
6. Reload the page (still as the same pro). Confirm no banner (status is now ACTIVE).

- [ ] **Step 5: Manual smoke check (DRAFT + anonymous)**

Open a private/incognito window. Navigate to `/salon/<slug>` for a salon currently in DRAFT status.

Expected: the page shows the "Salon introuvable" message (404 from backend). No DTO leaks.

- [ ] **Step 6: Manual smoke check (DRAFT + non-owner)**

Sign in as another user (a different pro, or a regular client). Navigate to `/salon/<other-pros-slug>` while that other pro's tenant is DRAFT.

Expected: 404. No banner, no content.

- [ ] **Step 7: Final commit (only if Step 1 or 2 surfaced fix-ups)**

If everything passed in steps 1-2 with no edits needed, skip this step. Otherwise commit any fixes:

```bash
git add -A
git commit -m "fix(vitrine-preview): address integration issues"
```

---

## Self-Review Notes

**Spec coverage check (Jalon 2 only):**

| Spec requirement | Implemented in |
|------------------|----------------|
| Owner-connected can view DRAFT storefront | Tasks 3, 4 |
| Anonymous / non-owner gets 404 on DRAFT | Tasks 3, 4 |
| ACTIVE behavior unchanged | Tasks 3, 4 (regression test) |
| SUSPENDED/DELETED stay 404 for everyone | Tasks 3, 4 |
| `status` field added to DTO | Tasks 1, 2, 5 |
| Frontend detects preview via `salon.status !== 'ACTIVE'` | Task 8 |
| Sticky banner with back-to-dashboard + publish actions | Tasks 6, 7 |
| Banner disappears after successful publish | Tasks 7, 8 |
| Booking disabled in preview | Task 8 |
| "Aperçu" button on dashboard | Task 9 |
| i18n FR + EN | Task 6 |

**Out of scope for this plan (handled in later jalons):**
- Token-based preview share (`?preview=<token>`) → Jalon 5.
- Synchronisation publish↔ProShellComponent edge case (the banner uses `DashboardService` directly so the dashboard refresh on return is a J5 follow-up if needed; for now, the dashboard's `loadReadiness` runs in the store's `onInit` whenever the dashboard component is reconstructed, which happens on navigation).
- Embedded "live preview" inside `/pro/salon` (split-view) → Jalon 5.

**Placeholders scan:** none — all steps contain concrete code or commands.

**Type consistency:**
- `PublicSalonResponse.status` is `String` on the backend (mapped from enum's `name()`) and the same TS string union on the frontend.
- The frontend type is a literal union `'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'DELETED'` — matches the backend enum's `name()` values exactly.
- `PreviewBannerComponent` inputs: `slug: string` (required), `canPublish: boolean` (default false). Output: `published` event (no payload).

---

## Notes for the executing engineer

- The banner deliberately injects `DashboardService` (not `DashboardStore`). This avoids spinning up a full SignalStore for one HTTP call. The `published` event signals the host (salon page) to refresh its DTO.
- Booking gating in preview is a UX nicety, not a security control — the backend `book` endpoint already enforces `status == ACTIVE` (line 246 of `PublicSalonController`), so an attempted booking on a DRAFT salon would 404 anyway. The disabled state just makes the UI honest.
- The `salonUrl()` computed on the dashboard component (line 78-81 in the existing TS) returns `'/salon/' + readiness.slug` — already wired. Don't recreate it.
- When the test in Task 4 mentions `UserPrincipal.builder().id(id).build()`, verify the actual constructor / builder shape and adjust accordingly. Don't fight the codebase — match its idiom.
- The salon-page test (Task 8 step 6) may need `status: 'ACTIVE'` added to the existing salon mock. Read first; edit minimally.
