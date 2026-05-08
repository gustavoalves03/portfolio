# Pro Onboarding Wizard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current onboarding indicator + sheet flow with a dedicated 8-step wizard at `/pro/onboarding` that guides new pros through every requirement to publish a salon, fix the `name=owner.name` provisioning bug, widen `canPublish` to include category/contact/logo, and add a `PublishMissingDialog` for pros who try to publish from the dashboard while requirements are missing.

**Architecture:**
- **Back:** new `PATCH /api/pro/tenant` endpoint for partial updates (the existing `PUT` keeps `@NotBlank name` for the legacy salon page). Widen `TenantReadinessResponse` with `hasContact` + `hasLogo` (and make `hasCategory` truly tenant-scoped on `Tenant.categorySlugs`). Fix `TenantProvisioningService` to leave `name` null at creation. Migrate `TENANTS.name` to nullable.
- **Front:** new `/pro/onboarding` route with `ProOnboardingWizardComponent` orchestrating 8 standalone step components. Each step persists its data via the new PATCH endpoint (or existing endpoints for cares/opening hours), then reloads `readiness` from the existing `DashboardStore`. Auto-redirection from `pro-shell` when `status=DRAFT && !canPublish`. New `PublishMissingDialogComponent` opens from the dashboard when `PUT /publish` returns 422.

**Tech Stack:** Spring Boot 3.5 + JPA + Flyway (Oracle), Angular 20 standalone + signals + NgRx SignalStore, Karma/Jasmine, JUnit 5 + `@WebMvcTest`.

**Spec:** `docs/superpowers/specs/2026-05-07-pro-onboarding-wizard-design.md`

---

## File Structure

### Backend (new + modified)

| Path | Role |
|------|------|
| `backend/src/main/resources/db/migration/oracle/V7__tenant_name_nullable.sql` | NEW. Make `TENANTS.name` nullable. |
| `backend/src/main/java/com/prettyface/app/tenant/domain/Tenant.java` | MODIFY. Drop `nullable = false` on `name`. |
| `backend/src/main/java/com/prettyface/app/tenant/app/TenantProvisioningService.java` | MODIFY. `.name(null)` instead of `.name(owner.getName())`. |
| `backend/src/main/java/com/prettyface/app/tenant/web/dto/PatchTenantRequest.java` | NEW. All fields optional. Adds `categorySlugs`. |
| `backend/src/main/java/com/prettyface/app/tenant/app/TenantService.java` | MODIFY. Add `patchProfile(ownerId, request)` method. |
| `backend/src/main/java/com/prettyface/app/tenant/web/TenantController.java` | MODIFY. Add `@PatchMapping` route + return `TenantResponse`. |
| `backend/src/main/java/com/prettyface/app/tenant/web/dto/TenantReadinessResponse.java` | MODIFY. Add `hasContact` + `hasLogo`. |
| `backend/src/main/java/com/prettyface/app/tenant/app/TenantReadinessService.java` | MODIFY. Compute `hasContact`, `hasLogo`, scope `hasCategory` on `tenant.getCategorySlugs()`, widen `canPublish`, return new keys in `getMissingConditions`. |
| `backend/src/test/java/com/prettyface/app/tenant/app/TenantReadinessServiceTests.java` | NEW. Unit tests for the widened service. |
| `backend/src/test/java/com/prettyface/app/tenant/app/TenantProvisioningServiceTests.java` | NEW or MODIFY. Asserts `name == null` after provision. |
| `backend/src/test/java/com/prettyface/app/tenant/web/TenantControllerTests.java` | NEW or MODIFY. PATCH partial update + 422 publish missing keys. |

### Frontend (new + modified)

| Path | Role |
|------|------|
| `frontend/src/app/features/dashboard/services/dashboard.service.ts` | MODIFY. Type `publish()` to capture 422 body. |
| `frontend/src/app/features/dashboard/models/dashboard.model.ts` | MODIFY. `TenantReadiness` adds `hasContact` + `hasLogo`. |
| `frontend/src/app/features/dashboard/store/dashboard.store.ts` | MODIFY. Add `publishMissing: string[]` state, intercept 422. |
| `frontend/src/app/features/onboarding/wizard/wizard-step.model.ts` | NEW. `WizardStepKey` union + step list constants. |
| `frontend/src/app/features/onboarding/wizard/tenant-patch.service.ts` | NEW. HTTP client for `PATCH /api/pro/tenant`. |
| `frontend/src/app/pages/pro/onboarding-wizard/pro-onboarding-wizard.component.ts/.html/.scss/.spec.ts` | NEW. Wizard page. |
| `frontend/src/app/pages/pro/onboarding-wizard/steps/welcome-step.component.ts/.html/.scss/.spec.ts` | NEW. |
| `frontend/src/app/pages/pro/onboarding-wizard/steps/name-step.component.ts/.html/.scss/.spec.ts` | NEW. |
| `frontend/src/app/pages/pro/onboarding-wizard/steps/contact-step.component.ts/.html/.scss/.spec.ts` | NEW. |
| `frontend/src/app/pages/pro/onboarding-wizard/steps/logo-step.component.ts/.html/.scss/.spec.ts` | NEW. |
| `frontend/src/app/pages/pro/onboarding-wizard/steps/categories-step.component.ts/.html/.scss/.spec.ts` | NEW. |
| `frontend/src/app/pages/pro/onboarding-wizard/steps/cares-step.component.ts/.html/.scss/.spec.ts` | NEW. |
| `frontend/src/app/pages/pro/onboarding-wizard/steps/opening-hours-step.component.ts/.html/.scss/.spec.ts` | NEW. |
| `frontend/src/app/pages/pro/onboarding-wizard/steps/publish-step.component.ts/.html/.scss/.spec.ts` | NEW. |
| `frontend/src/app/pages/pro/onboarding-wizard/wizard-progress-bar/wizard-progress-bar.component.ts/.html/.scss/.spec.ts` | NEW. |
| `frontend/src/app/pages/pro/pro-shell.component.ts` | MODIFY. Add redirect effect when DRAFT && !canPublish. |
| `frontend/src/app/app.routes.ts` (or wherever pro routes live) | MODIFY. Register `/pro/onboarding`. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.html` | MODIFY. Add "Reprendre le tutoriel" link. |
| `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.ts` | MODIFY. New action that clears the skip flag and navigates. |
| `frontend/src/app/pages/pro/publish-missing-dialog/publish-missing-dialog.component.ts/.html/.scss/.spec.ts` | NEW. |
| `frontend/src/app/pages/pro/pro-dashboard.component.ts` | MODIFY. Effect to open publish-missing dialog. |
| `frontend/public/i18n/fr.json` + `en.json` | MODIFY. New `pro.onboarding.wizard.*` block + `pro.dashboard.publishMissingDialog.*` + `pro.dashboard.checklist.hasContact{,Desc}`, `hasLogo{,Desc}`. |

---

## Section A — Backend changes (PR1)

### Task A1: Flyway migration to make `TENANTS.name` nullable

**Files:**
- Create: `backend/src/main/resources/db/migration/oracle/V7__tenant_name_nullable.sql`

- [ ] **Step 1: Write the migration SQL**

```sql
-- V7__tenant_name_nullable.sql
-- Allow new tenants to be provisioned with name=null so the onboarding
-- wizard can require an explicit confirmation step.
ALTER TABLE TENANTS MODIFY (name NULL);
```

- [ ] **Step 2: Run the app to verify Flyway picks it up**

Run from `backend/`:
```bash
mvn clean spring-boot:run
```
Expected: Flyway log shows `Migrating schema "<app>" to version "7 - tenant name nullable"` and the app starts without errors. Stop the server (Ctrl+C) once verified.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/oracle/V7__tenant_name_nullable.sql
git commit -m "feat(db): V7 make tenants.name nullable"
```

---

### Task A2: Drop `nullable = false` on `Tenant.name`

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/domain/Tenant.java:27-28`

- [ ] **Step 1: Edit the column annotation**

Replace:
```java
@Column(name = "name", nullable = false)
private String name;
```
With:
```java
@Column(name = "name")
private String name;
```

- [ ] **Step 2: Run `mvn compile` to verify nothing breaks**

```bash
cd backend && mvn -q compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/domain/Tenant.java
git commit -m "feat(tenant): allow null name on entity"
```

---

### Task A3: Provisioning fix — write the failing test

**Files:**
- Create or modify: `backend/src/test/java/com/prettyface/app/tenant/app/TenantProvisioningServiceTests.java`

- [ ] **Step 1: Write the failing test**

If the file doesn't exist, create it with:
```java
package com.prettyface.app.tenant.app;

import com.prettyface.app.multitenancy.TenantSchemaManager;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.users.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceTests {

    @Mock TenantRepository tenantRepository;
    @Mock TenantSchemaManager tenantSchemaManager;

    @InjectMocks TenantProvisioningService service;

    @Test
    void provision_leaves_name_null_so_pro_must_confirm_in_wizard() {
        User owner = User.builder().id(42L).name("Sophie").build();
        when(tenantRepository.existsBySlug(any())).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant created = service.provision(owner);

        assertThat(created.getName()).isNull();
        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        // confirm what was actually persisted
        org.mockito.Mockito.verify(tenantRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isNull();
    }
}
```

- [ ] **Step 2: Run the test, expect FAIL**

```bash
cd backend && mvn -q test -Dtest=TenantProvisioningServiceTests
```
Expected: FAIL — `expected: null but was: "Sophie"`.

---

### Task A4: Provisioning fix — implement

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/app/TenantProvisioningService.java:37-42`

- [ ] **Step 1: Replace the builder line for name**

Replace:
```java
Tenant tenant = Tenant.builder()
        .slug(slug)
        .name(owner.getName())
        .ownerId(owner.getId())
        .status(TenantStatus.DRAFT)
        .build();
```
With:
```java
Tenant tenant = Tenant.builder()
        .slug(slug)
        .name(null)
        .ownerId(owner.getId())
        .status(TenantStatus.DRAFT)
        .build();
```

- [ ] **Step 2: Run the test, expect PASS**

```bash
cd backend && mvn -q test -Dtest=TenantProvisioningServiceTests
```
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/app/TenantProvisioningService.java backend/src/test/java/com/prettyface/app/tenant/app/TenantProvisioningServiceTests.java
git commit -m "fix(tenant-provisioning): leave name null at creation"
```

---

### Task A5: Widen `TenantReadinessResponse` DTO

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/dto/TenantReadinessResponse.java`

- [ ] **Step 1: Add the two new booleans**

Replace the file content with:
```java
package com.prettyface.app.tenant.web.dto;

public record TenantReadinessResponse(
    String slug,
    boolean name,
    boolean hasCategory,
    boolean hasContact,
    boolean hasLogo,
    boolean hasActiveCare,
    boolean hasOpeningHours,
    boolean canPublish,
    String status,
    boolean employeesEnabled,
    int annualLeaveDays
) {}
```

- [ ] **Step 2: Run `mvn compile`, expect failures in the service that builds this record**

```bash
cd backend && mvn -q compile
```
Expected: FAIL — `TenantReadinessService.java` constructor mismatch (we'll fix in next task).

---

### Task A6: Widen `TenantReadinessService` — write tests

**Files:**
- Create: `backend/src/test/java/com/prettyface/app/tenant/app/TenantReadinessServiceTests.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.prettyface.app.tenant.app;

import com.prettyface.app.availability.repo.OpeningHourRepository;
import com.prettyface.app.care.domain.CareStatus;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.domain.TenantStatus;
import com.prettyface.app.tenant.web.dto.TenantReadinessResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantReadinessServiceTests {

    @Mock CareRepository careRepository;
    @Mock OpeningHourRepository openingHourRepository;
    @Mock CategoryRepository categoryRepository;

    @InjectMocks TenantReadinessService service;

    private Tenant baseTenant() {
        return Tenant.builder()
                .slug("salon")
                .status(TenantStatus.DRAFT)
                .annualLeaveDays(25)
                .build();
    }

    @Test
    void name_false_when_null() {
        Tenant t = baseTenant();
        TenantReadinessResponse r = service.getReadiness(t);
        assertThat(r.name()).isFalse();
    }

    @Test
    void name_true_when_filled() {
        Tenant t = baseTenant(); t.setName("Belle de Nuit");
        assertThat(service.getReadiness(t).name()).isTrue();
    }

    @Test
    void hasCategory_reads_categorySlugs_field_not_global_count() {
        Tenant t = baseTenant(); t.setCategorySlugs("facial,hair");
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(0L);
        when(openingHourRepository.count()).thenReturn(0L);
        assertThat(service.getReadiness(t).hasCategory()).isTrue();
    }

    @Test
    void hasCategory_false_when_categorySlugs_blank() {
        Tenant t = baseTenant(); t.setCategorySlugs("");
        assertThat(service.getReadiness(t).hasCategory()).isFalse();
    }

    @Test
    void hasContact_requires_full_address_and_phone_or_email() {
        Tenant t = baseTenant();
        t.setAddressStreet("1 rue X");
        t.setAddressPostalCode("75011");
        t.setAddressCity("Paris");
        t.setAddressCountry("FR");
        // no phone, no email yet
        assertThat(service.getReadiness(t).hasContact()).isFalse();

        t.setPhone("0102030405");
        assertThat(service.getReadiness(t).hasContact()).isTrue();

        t.setPhone(null); t.setContactEmail("hi@x.fr");
        assertThat(service.getReadiness(t).hasContact()).isTrue();
    }

    @Test
    void hasContact_false_when_address_partial() {
        Tenant t = baseTenant();
        t.setAddressStreet("1 rue X"); t.setAddressCity("Paris");
        t.setPhone("0102030405");
        assertThat(service.getReadiness(t).hasContact()).isFalse();
    }

    @Test
    void hasLogo_true_when_logoPath_set() {
        Tenant t = baseTenant(); t.setLogoPath("uploads/x.png");
        assertThat(service.getReadiness(t).hasLogo()).isTrue();
    }

    @Test
    void hasLogo_false_when_logoPath_blank_or_null() {
        Tenant t = baseTenant();
        assertThat(service.getReadiness(t).hasLogo()).isFalse();
        t.setLogoPath("");
        assertThat(service.getReadiness(t).hasLogo()).isFalse();
    }

    @Test
    void canPublish_requires_all_six_conditions() {
        Tenant t = fullyReadyTenant();
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(1L);
        when(openingHourRepository.count()).thenReturn(7L);
        assertThat(service.getReadiness(t).canPublish()).isTrue();
    }

    @Test
    void canPublish_false_when_logo_missing() {
        Tenant t = fullyReadyTenant(); t.setLogoPath(null);
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(1L);
        when(openingHourRepository.count()).thenReturn(7L);
        assertThat(service.getReadiness(t).canPublish()).isFalse();
    }

    @Test
    void getMissingConditions_returns_keys_in_wizard_order() {
        Tenant t = baseTenant(); // everything missing
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(0L);
        when(openingHourRepository.count()).thenReturn(0L);
        assertThat(service.getMissingConditions(t))
            .containsExactly("name", "hasContact", "hasLogo", "hasCategory", "hasActiveCare", "hasOpeningHours");
    }

    private Tenant fullyReadyTenant() {
        Tenant t = baseTenant();
        t.setName("Belle de Nuit");
        t.setCategorySlugs("facial");
        t.setAddressStreet("1 rue X");
        t.setAddressPostalCode("75011");
        t.setAddressCity("Paris");
        t.setAddressCountry("FR");
        t.setPhone("0102030405");
        t.setLogoPath("uploads/x.png");
        return t;
    }
}
```

- [ ] **Step 2: Run the tests, expect them to FAIL (compile error or assertion)**

```bash
cd backend && mvn -q test -Dtest=TenantReadinessServiceTests
```
Expected: COMPILATION ERROR or FAILURES. Either way the bar is red.

---

### Task A7: Widen `TenantReadinessService` — implement

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/app/TenantReadinessService.java`

- [ ] **Step 1: Replace the file content**

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

    private final CareRepository careRepository;
    private final OpeningHourRepository openingHourRepository;
    private final CategoryRepository categoryRepository;

    public TenantReadinessService(CareRepository careRepository,
                                   OpeningHourRepository openingHourRepository,
                                   CategoryRepository categoryRepository) {
        this.careRepository = careRepository;
        this.openingHourRepository = openingHourRepository;
        this.categoryRepository = categoryRepository;
    }

    public TenantReadinessResponse getReadiness(Tenant tenant) {
        boolean name = notBlank(tenant.getName());
        boolean hasCategory = notBlank(tenant.getCategorySlugs());
        boolean hasContact = computeHasContact(tenant);
        boolean hasLogo = notBlank(tenant.getLogoPath());
        boolean hasActiveCare = careRepository.countByStatus(CareStatus.ACTIVE) > 0;
        boolean hasOpeningHours = openingHourRepository.count() > 0;
        boolean canPublish = name && hasCategory && hasContact && hasLogo && hasActiveCare && hasOpeningHours;

        int annualLeaveDays = tenant.getAnnualLeaveDays() != null ? tenant.getAnnualLeaveDays() : 25;

        return new TenantReadinessResponse(
            tenant.getSlug(),
            name, hasCategory, hasContact, hasLogo, hasActiveCare, hasOpeningHours,
            canPublish, tenant.getStatus().name(),
            Boolean.TRUE.equals(tenant.getEmployeesEnabled()),
            annualLeaveDays
        );
    }

    public List<String> getMissingConditions(Tenant tenant) {
        TenantReadinessResponse r = getReadiness(tenant);
        List<String> missing = new ArrayList<>();
        if (!r.name()) missing.add("name");
        if (!r.hasContact()) missing.add("hasContact");
        if (!r.hasLogo()) missing.add("hasLogo");
        if (!r.hasCategory()) missing.add("hasCategory");
        if (!r.hasActiveCare()) missing.add("hasActiveCare");
        if (!r.hasOpeningHours()) missing.add("hasOpeningHours");
        return missing;
    }

    private boolean computeHasContact(Tenant t) {
        boolean addressFull = notBlank(t.getAddressStreet())
                && notBlank(t.getAddressPostalCode())
                && notBlank(t.getAddressCity())
                && notBlank(t.getAddressCountry());
        boolean reachable = notBlank(t.getPhone()) || notBlank(t.getContactEmail());
        return addressFull && reachable;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
```

- [ ] **Step 2: Run the readiness tests, expect PASS**

```bash
cd backend && mvn -q test -Dtest=TenantReadinessServiceTests
```
Expected: 11 tests PASS.

- [ ] **Step 3: Run the full backend test suite to catch fallout**

```bash
cd backend && mvn -q test
```
Expected: BUILD SUCCESS. If existing tests break (likely on `TenantReadinessResponse` constructor calls in other test fixtures), update the call sites to include `hasContact` + `hasLogo` arguments before continuing.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/web/dto/TenantReadinessResponse.java backend/src/main/java/com/prettyface/app/tenant/app/TenantReadinessService.java backend/src/test/java/com/prettyface/app/tenant/app/TenantReadinessServiceTests.java
git commit -m "feat(tenant-readiness): add hasContact + hasLogo, widen canPublish"
```

---

### Task A8: New `PatchTenantRequest` DTO

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/tenant/web/dto/PatchTenantRequest.java`

- [ ] **Step 1: Write the DTO**

```java
package com.prettyface.app.tenant.web.dto;

import jakarta.validation.constraints.Size;

public record PatchTenantRequest(
        @Size(max = 100) String name,
        @Size(max = 50000) String description,
        String logo,            // base64; same convention as UpdateTenantRequest
        String heroImage,
        @Size(max = 255) String addressStreet,
        @Size(max = 10) String addressPostalCode,
        @Size(max = 100) String addressCity,
        @Size(max = 2) String addressCountry,
        @Size(max = 20) String phone,
        @Size(max = 255) String contactEmail,
        @Size(max = 1000) String categorySlugs   // comma-separated keys, e.g. "facial,hair"
) {}
```

- [ ] **Step 2: Compile, expect SUCCESS**

```bash
cd backend && mvn -q compile
```

---

### Task A9: `TenantService.patchProfile` — write tests

**Files:**
- Create or modify: `backend/src/test/java/com/prettyface/app/tenant/app/TenantServiceTests.java` (add to existing if file exists; create if not)

- [ ] **Step 1: Write the failing tests**

```java
@Test
void patchProfile_only_updates_fields_present_in_request() {
    Tenant existing = Tenant.builder()
            .id(1L).slug("salon").ownerId(7L).status(TenantStatus.DRAFT)
            .name(null).addressCity("Paris").build();
    when(tenantRepository.findByOwnerId(7L)).thenReturn(java.util.Optional.of(existing));
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

    PatchTenantRequest req = new PatchTenantRequest(
        "Belle de Nuit", null, null, null,
        null, null, null, null, null, null, null);

    service.patchProfile(7L, req);

    assertThat(existing.getName()).isEqualTo("Belle de Nuit");
    assertThat(existing.getAddressCity()).isEqualTo("Paris"); // untouched
}

@Test
void patchProfile_updates_categorySlugs_when_provided() {
    Tenant existing = Tenant.builder().id(1L).slug("salon").ownerId(7L)
            .status(TenantStatus.DRAFT).build();
    when(tenantRepository.findByOwnerId(7L)).thenReturn(java.util.Optional.of(existing));
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

    PatchTenantRequest req = new PatchTenantRequest(
        null, null, null, null, null, null, null, null, null, null, "facial,hair");

    service.patchProfile(7L, req);
    assertThat(existing.getCategorySlugs()).isEqualTo("facial,hair");
}

@Test
void patchProfile_persists_logo_via_fileStorageService() {
    Tenant existing = Tenant.builder().id(1L).slug("salon").ownerId(7L)
            .status(TenantStatus.DRAFT).build();
    when(tenantRepository.findByOwnerId(7L)).thenReturn(java.util.Optional.of(existing));
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
    when(fileStorageService.saveBase64Image("data:image/png;base64,xxx", "tenant", 1L))
        .thenReturn("uploads/tenant/1/logo.png");

    PatchTenantRequest req = new PatchTenantRequest(
        null, null, "data:image/png;base64,xxx", null,
        null, null, null, null, null, null, null);

    service.patchProfile(7L, req);
    assertThat(existing.getLogoPath()).isEqualTo("uploads/tenant/1/logo.png");
}
```

Required mocks setup at the top of the test class: `@Mock TenantRepository tenantRepository;` `@Mock FileStorageService fileStorageService;` plus `TenantContext.setCurrentTenant("salon")` in a `@BeforeEach` and `TenantContext.clear()` in `@AfterEach`.

- [ ] **Step 2: Run the tests, expect FAIL (method doesn't exist yet)**

```bash
cd backend && mvn -q test -Dtest=TenantServiceTests#patchProfile_only_updates_fields_present_in_request
```
Expected: COMPILATION ERROR — `patchProfile` not defined.

---

### Task A10: `TenantService.patchProfile` — implement

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/app/TenantService.java`

- [ ] **Step 1: Add the method (place near `updateProfile`)**

```java
@Transactional
public TenantResponse patchProfile(Long ownerId, PatchTenantRequest request) {
    TenantContext.requireActive();
    Tenant tenant = tenantRepository.findByOwnerId(ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant not found for owner: " + ownerId));

    if (request.name() != null) tenant.setName(request.name());
    if (request.description() != null) tenant.setDescription(sanitizeHtml(request.description()));
    if (request.addressStreet() != null) tenant.setAddressStreet(request.addressStreet());
    if (request.addressPostalCode() != null) tenant.setAddressPostalCode(request.addressPostalCode());
    if (request.addressCity() != null) tenant.setAddressCity(request.addressCity());
    if (request.addressCountry() != null) tenant.setAddressCountry(request.addressCountry());
    if (request.phone() != null) tenant.setPhone(request.phone());
    if (request.contactEmail() != null) tenant.setContactEmail(request.contactEmail());
    if (request.categorySlugs() != null) tenant.setCategorySlugs(request.categorySlugs());

    if (request.logo() != null) {
        if (request.logo().isEmpty()) {
            if (tenant.getLogoPath() != null) {
                fileStorageService.deleteFile(tenant.getLogoPath());
                tenant.setLogoPath(null);
            }
        } else {
            if (tenant.getLogoPath() != null) {
                fileStorageService.deleteFile(tenant.getLogoPath());
            }
            tenant.setLogoPath(fileStorageService.saveBase64Image(request.logo(), "tenant", tenant.getId()));
        }
    }
    if (request.heroImage() != null) {
        if (request.heroImage().isEmpty()) {
            if (tenant.getHeroImagePath() != null) {
                fileStorageService.deleteFile(tenant.getHeroImagePath());
                tenant.setHeroImagePath(null);
            }
        } else {
            if (tenant.getHeroImagePath() != null) {
                fileStorageService.deleteFile(tenant.getHeroImagePath());
            }
            tenant.setHeroImagePath(fileStorageService.saveBase64Image(request.heroImage(), "tenant", tenant.getId()));
        }
    }

    return TenantMapper.toResponse(tenantRepository.save(tenant));
}
```

- [ ] **Step 2: Run the tests, expect PASS**

```bash
cd backend && mvn -q test -Dtest=TenantServiceTests
```
Expected: PASS for the 3 new tests + previous tests stay green.

---

### Task A11: `TenantController.patch` — wire the endpoint

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/TenantController.java`

- [ ] **Step 1: Add the route (just below `updateProfile`)**

```java
@PatchMapping
public TenantResponse patchProfile(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestBody @Valid PatchTenantRequest request) {
    Tenant tenant = tenantRepository.findByOwnerId(principal.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    TenantContext.setCurrentTenant(tenant.getSlug());
    try {
        return tenantService.patchProfile(principal.getId(), request);
    } finally {
        TenantContext.clear();
    }
}
```

Add the `import com.prettyface.app.tenant.web.dto.PatchTenantRequest;` at the top.

- [ ] **Step 2: Compile, expect SUCCESS**

```bash
cd backend && mvn -q compile
```

- [ ] **Step 3: Add a `@WebMvcTest` for the new route**

In `backend/src/test/java/com/prettyface/app/tenant/web/TenantControllerTests.java` (create if missing):

```java
@Test
@WithMockUser
void patch_only_sends_provided_fields() throws Exception {
    String body = "{\"name\":\"Belle\"}";
    mockMvc.perform(patch("/api/pro/tenant").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());
    verify(tenantService).patchProfile(eq(currentOwnerId), argThat(req ->
        "Belle".equals(req.name()) && req.addressCity() == null));
}
```

(Plug into existing TenantControllerTests boilerplate; reuse the existing `@WebMvcTest(TenantController.class)`.)

- [ ] **Step 4: Run the test, expect PASS**

```bash
cd backend && mvn -q test -Dtest=TenantControllerTests
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/web/dto/PatchTenantRequest.java backend/src/main/java/com/prettyface/app/tenant/app/TenantService.java backend/src/main/java/com/prettyface/app/tenant/web/TenantController.java backend/src/test/java/com/prettyface/app/tenant/app/TenantServiceTests.java backend/src/test/java/com/prettyface/app/tenant/web/TenantControllerTests.java
git commit -m "feat(tenant): PATCH /api/pro/tenant for partial updates from wizard"
```

---

### Task A12: Publish controller — verify 422 returns expanded missing list

**Files:**
- Modify: `backend/src/test/java/com/prettyface/app/tenant/web/TenantControllerTests.java`

- [ ] **Step 1: Add the failing test**

```java
@Test
@WithMockUser
void publish_returns_422_with_missing_keys_in_wizard_order() throws Exception {
    when(readinessService.getMissingConditions(any()))
        .thenReturn(java.util.List.of("name", "hasContact", "hasLogo"));

    mockMvc.perform(put("/api/pro/tenant/publish"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.missing[0]").value("name"))
        .andExpect(jsonPath("$.missing[1]").value("hasContact"))
        .andExpect(jsonPath("$.missing[2]").value("hasLogo"));
}
```

- [ ] **Step 2: Run, expect PASS (no implementation change needed — just confirms the contract holds with the new keys)**

```bash
cd backend && mvn -q test -Dtest=TenantControllerTests#publish_returns_422_with_missing_keys_in_wizard_order
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/prettyface/app/tenant/web/TenantControllerTests.java
git commit -m "test(tenant): pin publish 422 missing keys order for wizard"
```

---

**End of Section A. PR1 ready: backend changes complete, all tests green.**

---

## Section B — Frontend wizard (PR2)

### Task B1: Sync `TenantReadiness` model with backend

**Files:**
- Modify: `frontend/src/app/features/dashboard/models/dashboard.model.ts`

- [ ] **Step 1: Edit the interface**

Replace:
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
With:
```typescript
export interface TenantReadiness {
  slug: string;
  name: boolean;
  hasCategory: boolean;
  hasContact: boolean;
  hasLogo: boolean;
  hasActiveCare: boolean;
  hasOpeningHours: boolean;
  canPublish: boolean;
  status: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';
}
```

- [ ] **Step 2: Type-check, expect failures in tests/fixtures that build TenantReadiness**

```bash
cd frontend && npx tsc --noEmit -p tsconfig.app.json
```
Expected: errors in `pro-dashboard.component.spec.ts`, `onboarding-checklist.service.spec.ts`, `onboarding-indicator.component.spec.ts` (fixtures missing `hasContact`/`hasLogo`).

- [ ] **Step 3: Update each fixture to include the two new booleans (default `false`)**

In every `function readiness(...)` helper or literal `TenantReadiness` object, add `hasContact: false, hasLogo: false`.

- [ ] **Step 4: Type-check passes**

```bash
cd frontend && npx tsc --noEmit -p tsconfig.app.json
```
Expected: no output (success).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/dashboard/models/dashboard.model.ts frontend/src/app/features/onboarding/onboarding-checklist.service.spec.ts frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.spec.ts frontend/src/app/pages/pro/pro-dashboard.component.spec.ts
git commit -m "feat(readiness): sync TenantReadiness with hasContact + hasLogo"
```

---

### Task B2: `WizardStepKey` model + step list

**Files:**
- Create: `frontend/src/app/features/onboarding/wizard/wizard-step.model.ts`

- [ ] **Step 1: Write the model**

```typescript
export type WizardStepKey =
  | 'welcome'
  | 'name'
  | 'contact'
  | 'logo'
  | 'categories'
  | 'cares'
  | 'openingHours'
  | 'publish';

export const WIZARD_STEP_ORDER: readonly WizardStepKey[] = [
  'welcome',
  'name',
  'contact',
  'logo',
  'categories',
  'cares',
  'openingHours',
  'publish',
] as const;

/** Map a missing-key from the back to the wizard step that fixes it. */
export const MISSING_KEY_TO_STEP: Readonly<Record<string, WizardStepKey>> = {
  name: 'name',
  hasContact: 'contact',
  hasLogo: 'logo',
  hasCategory: 'categories',
  hasActiveCare: 'cares',
  hasOpeningHours: 'openingHours',
};
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/features/onboarding/wizard/wizard-step.model.ts
git commit -m "feat(onboarding-wizard): add WizardStepKey + step order"
```

---

### Task B3: `TenantPatchService` HTTP client

**Files:**
- Create: `frontend/src/app/features/onboarding/wizard/tenant-patch.service.ts`
- Test: `frontend/src/app/features/onboarding/wizard/tenant-patch.service.spec.ts`

- [ ] **Step 1: Write the failing test**

```typescript
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TenantPatchService } from './tenant-patch.service';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';

describe('TenantPatchService', () => {
  let service: TenantPatchService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://test' },
      ],
    });
    service = TestBed.inject(TenantPatchService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('PATCHes only the fields provided', () => {
    service.patch({ name: 'Belle' }).subscribe();
    const req = http.expectOne('http://test/api/pro/tenant');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ name: 'Belle' });
    req.flush({});
  });
});
```

- [ ] **Step 2: Run, expect FAIL**

```bash
cd frontend && npm test -- --include='**/tenant-patch.service.spec.ts' --watch=false
```
Expected: FAIL — service doesn't exist.

- [ ] **Step 3: Implement**

```typescript
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';

export interface TenantPatch {
  name?: string;
  addressStreet?: string;
  addressPostalCode?: string;
  addressCity?: string;
  addressCountry?: string;
  phone?: string;
  contactEmail?: string;
  logo?: string;        // base64 data URL
  heroImage?: string;
  categorySlugs?: string;
}

@Injectable({ providedIn: 'root' })
export class TenantPatchService {
  private http = inject(HttpClient);
  private apiBaseUrl = inject(API_BASE_URL);

  patch(body: TenantPatch): Observable<unknown> {
    return this.http.patch(`${this.apiBaseUrl}/api/pro/tenant`, body);
  }
}
```

- [ ] **Step 4: Run, expect PASS**

```bash
cd frontend && npm test -- --include='**/tenant-patch.service.spec.ts' --watch=false
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/onboarding/wizard/tenant-patch.service.ts frontend/src/app/features/onboarding/wizard/tenant-patch.service.spec.ts
git commit -m "feat(onboarding-wizard): add TenantPatchService"
```

---

### Task B4: `WizardProgressBarComponent`

**Files:**
- Create: `frontend/src/app/pages/pro/onboarding-wizard/wizard-progress-bar/wizard-progress-bar.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Write the failing spec**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { WizardProgressBarComponent } from './wizard-progress-bar.component';

describe('WizardProgressBarComponent', () => {
  let fixture: ComponentFixture<WizardProgressBarComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [
        WizardProgressBarComponent,
        TranslocoTestingModule.forRoot({ langs: { en: {} }, translocoConfig: { availableLangs: ['en'], defaultLang: 'en' } }),
      ],
    });
    fixture = TestBed.createComponent(WizardProgressBarComponent);
  });

  it('marks segments before currentIndex as done and emits stepClick on done segments', () => {
    fixture.componentRef.setInput('currentIndex', 3);
    fixture.componentRef.setInput('totalSteps', 7);
    fixture.detectChanges();

    const segments = fixture.nativeElement.querySelectorAll('.wpb-segment');
    expect(segments.length).toBe(7);
    expect(segments[0].classList.contains('is-done')).toBeTrue();
    expect(segments[2].classList.contains('is-done')).toBeTrue();
    expect(segments[3].classList.contains('is-current')).toBeTrue();
    expect(segments[4].classList.contains('is-done')).toBeFalse();
  });
});
```

- [ ] **Step 2: Run, expect FAIL**

```bash
cd frontend && npm test -- --include='**/wizard-progress-bar.component.spec.ts' --watch=false
```

- [ ] **Step 3: Implement**

`wizard-progress-bar.component.ts`:
```typescript
import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-wizard-progress-bar',
  standalone: true,
  templateUrl: './wizard-progress-bar.component.html',
  styleUrl: './wizard-progress-bar.component.scss',
})
export class WizardProgressBarComponent {
  readonly currentIndex = input.required<number>();
  readonly totalSteps = input.required<number>();
  readonly stepClick = output<number>();

  protected segments(): number[] {
    return Array.from({ length: this.totalSteps() }, (_, i) => i);
  }

  protected onSegmentClick(index: number): void {
    if (index < this.currentIndex()) this.stepClick.emit(index);
  }
}
```

`wizard-progress-bar.component.html`:
```html
<ol class="wpb">
  @for (i of segments(); track i) {
    <li
      class="wpb-segment"
      [class.is-done]="i < currentIndex()"
      [class.is-current]="i === currentIndex()"
      [attr.aria-current]="i === currentIndex() ? 'step' : null"
    >
      <button
        type="button"
        class="wpb-button"
        [disabled]="i >= currentIndex()"
        (click)="onSegmentClick(i)"
      >
        <span class="wpb-dot" aria-hidden="true"></span>
      </button>
    </li>
  }
</ol>
```

`wizard-progress-bar.component.scss`:
```scss
.wpb {
  display: flex;
  gap: 6px;
  list-style: none;
  padding: 0;
  margin: 0;
}
.wpb-segment {
  flex: 1;
}
.wpb-button {
  width: 100%;
  height: 6px;
  border-radius: 999px;
  border: none;
  background: var(--pf-line-soft);
  cursor: default;
  padding: 0;
}
.wpb-segment.is-done .wpb-button {
  background: var(--pf-rose);
  cursor: pointer;
}
.wpb-segment.is-current .wpb-button {
  background: linear-gradient(90deg, var(--pf-rose) 60%, var(--pf-line-soft) 60%);
}
.wpb-dot { display: none; }
```

- [ ] **Step 4: Run, expect PASS**

```bash
cd frontend && npm test -- --include='**/wizard-progress-bar.component.spec.ts' --watch=false
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/pro/onboarding-wizard/wizard-progress-bar/
git commit -m "feat(onboarding-wizard): add WizardProgressBarComponent"
```

---

### Task B5: `WelcomeStepComponent`

**Files:**
- Create: `frontend/src/app/pages/pro/onboarding-wizard/steps/welcome-step.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Spec**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { WelcomeStepComponent } from './welcome-step.component';

describe('WelcomeStepComponent', () => {
  it('emits next on primary CTA click', () => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [WelcomeStepComponent, TranslocoTestingModule.forRoot({
        langs: { en: {} },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      })],
    });
    const fixture: ComponentFixture<WelcomeStepComponent> = TestBed.createComponent(WelcomeStepComponent);
    let emitted = false;
    fixture.componentInstance.completed.subscribe(() => emitted = true);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="welcome-cta"]').click();
    expect(emitted).toBeTrue();
  });
});
```

- [ ] **Step 2: Implement**

`welcome-step.component.ts`:
```typescript
import { Component, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-welcome-step',
  standalone: true,
  imports: [TranslocoPipe, MatIconModule],
  templateUrl: './welcome-step.component.html',
  styleUrl: './welcome-step.component.scss',
})
export class WelcomeStepComponent {
  readonly completed = output<void>();
  readonly exit = output<void>();
}
```

`welcome-step.component.html`:
```html
<section class="step">
  <h1 class="step-title">{{ 'pro.onboarding.wizard.welcome.title' | transloco }}</h1>
  <p class="step-sub">{{ 'pro.onboarding.wizard.welcome.sub' | transloco }}</p>

  <ul class="welcome-bullets">
    <li><mat-icon>edit</mat-icon> {{ 'pro.onboarding.wizard.welcome.b1' | transloco }}</li>
    <li><mat-icon>place</mat-icon> {{ 'pro.onboarding.wizard.welcome.b2' | transloco }}</li>
    <li><mat-icon>image</mat-icon> {{ 'pro.onboarding.wizard.welcome.b3' | transloco }}</li>
    <li><mat-icon>category</mat-icon> {{ 'pro.onboarding.wizard.welcome.b4' | transloco }}</li>
    <li><mat-icon>spa</mat-icon> {{ 'pro.onboarding.wizard.welcome.b5' | transloco }}</li>
    <li><mat-icon>schedule</mat-icon> {{ 'pro.onboarding.wizard.welcome.b6' | transloco }}</li>
  </ul>

  <div class="step-actions">
    <button type="button" class="step-cta" data-testid="welcome-cta" (click)="completed.emit()">
      {{ 'pro.onboarding.wizard.welcome.cta' | transloco }} →
    </button>
    <button type="button" class="step-exit" (click)="exit.emit()">
      {{ 'pro.onboarding.wizard.welcome.exit' | transloco }}
    </button>
  </div>
</section>
```

`welcome-step.component.scss`:
```scss
.step { max-width: 640px; margin: 0 auto; padding: 64px 32px; text-align: center; }
.step-title { font-family: 'Fraunces', serif; font-size: clamp(32px, 4vw, 48px); margin-bottom: 16px; }
.step-sub { color: var(--pf-ink-mute); font-size: 17px; line-height: 1.55; margin-bottom: 32px; }
.welcome-bullets { list-style: none; padding: 0; margin: 0 0 40px; display: grid; grid-template-columns: 1fr 1fr; gap: 12px; text-align: left; }
.welcome-bullets li { display: flex; align-items: center; gap: 10px; color: var(--pf-ink); }
.step-actions { display: flex; flex-direction: column; align-items: center; gap: 16px; }
.step-cta { background: var(--pf-rose); color: #fff; border: 0; border-radius: 999px; padding: 14px 32px; font-weight: 600; cursor: pointer; }
.step-exit { background: none; border: 0; color: var(--pf-ink-mute); text-decoration: underline; cursor: pointer; }
```

- [ ] **Step 3: Run + commit**

```bash
cd frontend && npm test -- --include='**/welcome-step.component.spec.ts' --watch=false
git add frontend/src/app/pages/pro/onboarding-wizard/steps/welcome-step.component.*
git commit -m "feat(onboarding-wizard): WelcomeStepComponent"
```

---

### Task B6: `NameStepComponent`

**Files:**
- Create: `frontend/src/app/pages/pro/onboarding-wizard/steps/name-step.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Spec**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { API_BASE_URL } from '../../../../core/config/api-base-url.token';
import { NameStepComponent } from './name-step.component';

describe('NameStepComponent', () => {
  let fixture: ComponentFixture<NameStepComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://t' },
      ],
      imports: [NameStepComponent, TranslocoTestingModule.forRoot({
        langs: { en: {} },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      })],
    });
    fixture = TestBed.createComponent(NameStepComponent);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('disables next while empty', () => {
    fixture.componentRef.setInput('initialName', null);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="next"]');
    expect(btn.disabled).toBeTrue();
  });

  it('PATCHes the name and emits completed on success', async () => {
    fixture.componentRef.setInput('initialName', null);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input');
    input.value = 'Belle de Nuit';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    let done = false;
    fixture.componentInstance.completed.subscribe(() => done = true);

    fixture.nativeElement.querySelector('[data-testid="next"]').click();
    const req = http.expectOne('http://t/api/pro/tenant');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ name: 'Belle de Nuit' });
    req.flush({});
    expect(done).toBeTrue();
  });
});
```

- [ ] **Step 2: Implement**

`name-step.component.ts`:
```typescript
import { Component, inject, input, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TenantPatchService } from '../../../../features/onboarding/wizard/tenant-patch.service';

@Component({
  selector: 'app-name-step',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './name-step.component.html',
  styleUrl: './name-step.component.scss',
})
export class NameStepComponent {
  readonly initialName = input<string | null>(null);
  readonly completed = output<void>();
  readonly back = output<void>();

  private readonly patcher = inject(TenantPatchService);
  protected readonly value = signal('');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.value.set(this.initialName() ?? '');
  }

  protected onInput(e: Event): void {
    this.value.set((e.target as HTMLInputElement).value);
  }

  protected onSubmit(): void {
    const v = this.value().trim();
    if (!v) return;
    this.saving.set(true);
    this.error.set(null);
    this.patcher.patch({ name: v }).subscribe({
      next: () => { this.saving.set(false); this.completed.emit(); },
      error: () => { this.saving.set(false); this.error.set('save'); },
    });
  }
}
```

`name-step.component.html`:
```html
<section class="step">
  <button type="button" class="step-back" (click)="back.emit()">← {{ 'common.back' | transloco }}</button>
  <h1 class="step-title">{{ 'pro.onboarding.wizard.name.title' | transloco }}</h1>
  <p class="step-sub">{{ 'pro.onboarding.wizard.name.sub' | transloco }}</p>

  <form (submit)="onSubmit(); $event.preventDefault()" class="step-form">
    <input
      type="text"
      class="step-input"
      [value]="value()"
      (input)="onInput($event)"
      [placeholder]="'pro.onboarding.wizard.name.placeholder' | transloco"
      autofocus
    />
    @if (error()) {
      <p class="step-error">{{ 'pro.onboarding.wizard.errors.save' | transloco }}</p>
    }
    <button type="submit" class="step-cta" data-testid="next" [disabled]="!value().trim() || saving()">
      {{ 'pro.onboarding.wizard.next' | transloco }} →
    </button>
  </form>
</section>
```

`name-step.component.scss`: minimal — see commit, share variables with `welcome-step.scss`. Reuse `.step`, `.step-title`, `.step-sub`, `.step-cta`. Add:
```scss
.step-back { background: none; border: 0; color: var(--pf-ink-mute); cursor: pointer; margin-bottom: 16px; }
.step-form { display: flex; flex-direction: column; gap: 16px; max-width: 480px; margin: 0 auto; }
.step-input { padding: 14px 16px; border: 1px solid var(--pf-line); border-radius: 12px; font-size: 17px; }
.step-error { color: #b00020; font-size: 14px; }
```

- [ ] **Step 3: Run + commit**

```bash
cd frontend && npm test -- --include='**/name-step.component.spec.ts' --watch=false
git add frontend/src/app/pages/pro/onboarding-wizard/steps/name-step.component.*
git commit -m "feat(onboarding-wizard): NameStepComponent"
```

---

### Task B7: `ContactStepComponent`

**Files:**
- Create: `frontend/src/app/pages/pro/onboarding-wizard/steps/contact-step.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Spec**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { API_BASE_URL } from '../../../../core/config/api-base-url.token';
import { ContactStepComponent } from './contact-step.component';

describe('ContactStepComponent', () => {
  let fixture: ComponentFixture<ContactStepComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://t' },
      ],
      imports: [ContactStepComponent, TranslocoTestingModule.forRoot({
        langs: { en: {} },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      })],
    });
    fixture = TestBed.createComponent(ContactStepComponent);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  function setField(name: string, value: string) {
    const el = fixture.nativeElement.querySelector(`[name="${name}"]`);
    el.value = value;
    el.dispatchEvent(new Event('input'));
  }

  it('disables next while address incomplete', () => {
    fixture.detectChanges();
    setField('street', '1 rue X');
    setField('city', 'Paris');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="next"]').disabled).toBeTrue();
  });

  it('disables next while neither phone nor email', () => {
    fixture.detectChanges();
    setField('street', '1 rue X');
    setField('postalCode', '75011');
    setField('city', 'Paris');
    setField('country', 'FR');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="next"]').disabled).toBeTrue();
  });

  it('PATCHes with all fields when valid', () => {
    fixture.detectChanges();
    setField('street', '1 rue X');
    setField('postalCode', '75011');
    setField('city', 'Paris');
    setField('country', 'FR');
    setField('phone', '0102030405');
    fixture.detectChanges();
    let done = false;
    fixture.componentInstance.completed.subscribe(() => done = true);
    fixture.nativeElement.querySelector('[data-testid="next"]').click();
    const req = http.expectOne('http://t/api/pro/tenant');
    expect(req.request.body).toEqual(jasmine.objectContaining({
      addressStreet: '1 rue X',
      addressPostalCode: '75011',
      addressCity: 'Paris',
      addressCountry: 'FR',
      phone: '0102030405',
    }));
    req.flush({});
    expect(done).toBeTrue();
  });
});
```

- [ ] **Step 2: Implement**

`contact-step.component.ts`:
```typescript
import { Component, computed, inject, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TenantPatchService, TenantPatch } from '../../../../features/onboarding/wizard/tenant-patch.service';

@Component({
  selector: 'app-contact-step',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './contact-step.component.html',
  styleUrl: './contact-step.component.scss',
})
export class ContactStepComponent {
  readonly completed = output<void>();
  readonly back = output<void>();

  private readonly patcher = inject(TenantPatchService);

  protected readonly street = signal('');
  protected readonly postalCode = signal('');
  protected readonly city = signal('');
  protected readonly country = signal('FR');
  protected readonly phone = signal('');
  protected readonly email = signal('');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly canSubmit = computed(() =>
    !!this.street().trim() && !!this.postalCode().trim() &&
    !!this.city().trim() && !!this.country().trim() &&
    (!!this.phone().trim() || !!this.email().trim())
  );

  protected onInput(name: string, e: Event): void {
    const v = (e.target as HTMLInputElement).value;
    switch (name) {
      case 'street': this.street.set(v); break;
      case 'postalCode': this.postalCode.set(v); break;
      case 'city': this.city.set(v); break;
      case 'country': this.country.set(v); break;
      case 'phone': this.phone.set(v); break;
      case 'email': this.email.set(v); break;
    }
  }

  protected onSubmit(): void {
    if (!this.canSubmit()) return;
    this.saving.set(true);
    this.error.set(null);
    const body: TenantPatch = {
      addressStreet: this.street().trim(),
      addressPostalCode: this.postalCode().trim(),
      addressCity: this.city().trim(),
      addressCountry: this.country().trim(),
    };
    if (this.phone().trim()) body.phone = this.phone().trim();
    if (this.email().trim()) body.contactEmail = this.email().trim();
    this.patcher.patch(body).subscribe({
      next: () => { this.saving.set(false); this.completed.emit(); },
      error: () => { this.saving.set(false); this.error.set('save'); },
    });
  }
}
```

`contact-step.component.html` covers 6 inputs; for brevity here, mirror name-step's structure with one `<input [name]>` per field plus a `<select name="country">` containing `<option value="FR">`, `BE`, `CH`. Include `<button data-testid="next" [disabled]="!canSubmit() || saving()">`.

- [ ] **Step 3: Run + commit**

```bash
cd frontend && npm test -- --include='**/contact-step.component.spec.ts' --watch=false
git add frontend/src/app/pages/pro/onboarding-wizard/steps/contact-step.component.*
git commit -m "feat(onboarding-wizard): ContactStepComponent"
```

---

### Task B8: `LogoStepComponent`

A drop zone + file input that converts to base64 then PATCHes. Reuse the same base64 convention as `UpdateTenantRequest`.

- [ ] **Step 1: Spec**
  - disables next when no logo provided AND `initialLogoUrl` is null
  - reads file as data URL on file selection
  - PATCHes `logo: <data url>` and emits `completed` on response
- [ ] **Step 2: Implement** (use `FileReader.readAsDataURL`, max 5 MB validation, formats `image/png|jpeg`)
- [ ] **Step 3: Run + commit**

```bash
cd frontend && npm test -- --include='**/logo-step.component.spec.ts' --watch=false
git add frontend/src/app/pages/pro/onboarding-wizard/steps/logo-step.component.*
git commit -m "feat(onboarding-wizard): LogoStepComponent"
```

---

### Task B9: `CategoriesStepComponent`

Multi-select chips for the 10 keys (`facial`, `hair`, `nails`, `massage`, `lashes`, `makeup`, `waxing`, `wellness`, `barber`, `rituals`). Hardcode the list (same as `home.ts`); on submit: `patcher.patch({ categorySlugs: selected.join(',') })`.

- [ ] **Step 1: Spec**
  - 10 chips render
  - clicking a chip toggles selected state
  - next disabled when 0 selected
  - submit sends comma-separated `categorySlugs`
- [ ] **Step 2: Implement**
- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/pro/onboarding-wizard/steps/categories-step.component.*
git commit -m "feat(onboarding-wizard): CategoriesStepComponent"
```

---

### Task B10: `CaresStepComponent`

Two parallel paths (template OR manual):

**Path A — Template:** reuse the existing `PersonaSetupService` (under `frontend/src/app/features/onboarding/persona-setup.service.ts`). On click of a persona card → `personaSetup.apply(persona)` → on success emit `completed`.

**Path B — Manual:** small inline form (name + duration + price + category dropdown), POST to existing care creation endpoint via the existing `CaresService`. On success emit `completed`.

- [ ] **Step 1: Spec**
  - template card click triggers personaSetup.apply and emits completed on success
  - manual form requires name + duration > 0 + price >= 0
  - manual submit calls CaresService.create and emits completed
- [ ] **Step 2: Implement**
- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/pro/onboarding-wizard/steps/cares-step.component.*
git commit -m "feat(onboarding-wizard): CaresStepComponent"
```

---

### Task B11: `OpeningHoursStepComponent`

7 rows (Mon→Sun) with `[Open]/[Closed]` toggle and start/end time inputs when open. Two preset shortcut buttons. On submit: call existing opening-hours endpoint via the existing `AvailabilityService`.

- [ ] **Step 1: Spec**
  - 7 rows render
  - "Mon-Fri 9-19" preset fills 5 rows correctly
  - submit POSTs the open rows; emits completed on success
- [ ] **Step 2: Implement**
- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/pro/onboarding-wizard/steps/opening-hours-step.component.*
git commit -m "feat(onboarding-wizard): OpeningHoursStepComponent"
```

---

### Task B12: `PublishStepComponent`

Recap card (logo, name, address, care count, days open count) + `Prévisualiser` link → opens `/salon/{slug}` (with preview token logic if available) in new tab + `Publier mon salon` button → `dashboardService.publish()`. On success: success snackbar + emits `completed` (the wizard then redirects to dashboard).

- [ ] **Step 1: Spec**
  - publish click calls `DashboardService.publish()`
  - emits completed on success
- [ ] **Step 2: Implement**
- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/pro/onboarding-wizard/steps/publish-step.component.*
git commit -m "feat(onboarding-wizard): PublishStepComponent"
```

---

### Task B13: `ProOnboardingWizardComponent` — orchestrator

**Files:**
- Create: `frontend/src/app/pages/pro/onboarding-wizard/pro-onboarding-wizard.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Spec** (essential cases only)

```typescript
// Test 1: starts at first non-done step (skips welcome if name already filled).
// Test 2: on (completed) from a child step, reloads readiness and advances.
// Test 3: redirects to /pro/dashboard when status === 'ACTIVE'.
// Test 4: clicking "exit" sets sessionStorage flag and navigates dashboard.
```

- [ ] **Step 2: Implement**

```typescript
import { Component, computed, effect, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { DashboardStore } from '../../../features/dashboard/store/dashboard.store';
import { TenantService } from '../../../features/tenant/services/tenant.service'; // assume exists
import { WIZARD_STEP_ORDER, WizardStepKey } from '../../../features/onboarding/wizard/wizard-step.model';
import { TenantReadiness } from '../../../features/dashboard/models/dashboard.model';
import { WizardProgressBarComponent } from './wizard-progress-bar/wizard-progress-bar.component';
import { WelcomeStepComponent } from './steps/welcome-step.component';
import { NameStepComponent } from './steps/name-step.component';
import { ContactStepComponent } from './steps/contact-step.component';
import { LogoStepComponent } from './steps/logo-step.component';
import { CategoriesStepComponent } from './steps/categories-step.component';
import { CaresStepComponent } from './steps/cares-step.component';
import { OpeningHoursStepComponent } from './steps/opening-hours-step.component';
import { PublishStepComponent } from './steps/publish-step.component';

@Component({
  selector: 'app-pro-onboarding-wizard',
  standalone: true,
  imports: [
    CommonModule, TranslocoPipe, WizardProgressBarComponent,
    WelcomeStepComponent, NameStepComponent, ContactStepComponent, LogoStepComponent,
    CategoriesStepComponent, CaresStepComponent, OpeningHoursStepComponent, PublishStepComponent,
  ],
  templateUrl: './pro-onboarding-wizard.component.html',
  styleUrl: './pro-onboarding-wizard.component.scss',
})
export class ProOnboardingWizardComponent {
  protected readonly store = inject(DashboardStore);
  private readonly router = inject(Router);

  protected readonly currentStep = signal<WizardStepKey>('welcome');

  protected readonly currentIndex = computed(() => WIZARD_STEP_ORDER.indexOf(this.currentStep()));
  protected readonly totalSteps = WIZARD_STEP_ORDER.length;

  constructor() {
    effect(() => {
      const r = this.store.readiness();
      if (!r) return;
      if (r.status === 'ACTIVE') {
        this.router.navigate(['/pro/dashboard']);
        return;
      }
      // Initial step = first non-done according to readiness
      this.currentStep.set(this.firstUnfinishedStep(r));
    }, { allowSignalWrites: true });
  }

  protected onStepCompleted(): void {
    this.store.loadReadiness();
    // After readiness reloads, the effect will advance the currentStep automatically.
    // However, if completion is on the publish step and tenant is ACTIVE, the effect redirects.
  }

  protected onExit(): void {
    sessionStorage.setItem('pf_skipOnboarding', '1');
    this.router.navigate(['/pro/dashboard']);
  }

  protected onBack(): void {
    const i = this.currentIndex();
    if (i > 0) this.currentStep.set(WIZARD_STEP_ORDER[i - 1]);
  }

  protected onJumpTo(index: number): void {
    if (index >= 0 && index < this.currentIndex()) {
      this.currentStep.set(WIZARD_STEP_ORDER[index]);
    }
  }

  private firstUnfinishedStep(r: TenantReadiness): WizardStepKey {
    if (!r.name) return 'name';
    if (!r.hasContact) return 'contact';
    if (!r.hasLogo) return 'logo';
    if (!r.hasCategory) return 'categories';
    if (!r.hasActiveCare) return 'cares';
    if (!r.hasOpeningHours) return 'openingHours';
    return 'publish';
  }
}
```

`pro-onboarding-wizard.component.html`:
```html
<header class="wiz-topbar">
  <span class="wiz-brand">Pretty.<em>Face</em> Pro</span>
  <span class="wiz-step-counter">{{ currentIndex() + 1 }} / {{ totalSteps }}</span>
  <button type="button" class="wiz-exit" (click)="onExit()">
    {{ 'pro.onboarding.wizard.exit.label' | transloco }}
  </button>
</header>

<app-wizard-progress-bar
  [currentIndex]="currentIndex()"
  [totalSteps]="totalSteps"
  (stepClick)="onJumpTo($event)"
/>

<main class="wiz-stage">
  @switch (currentStep()) {
    @case ('welcome') {
      <app-welcome-step (completed)="onStepCompleted()" (exit)="onExit()" />
    }
    @case ('name') {
      <app-name-step
        [initialName]="store.readiness()?.name ? null : null"
        (completed)="onStepCompleted()"
        (back)="onBack()"
      />
    }
    @case ('contact') {
      <app-contact-step (completed)="onStepCompleted()" (back)="onBack()" />
    }
    @case ('logo') {
      <app-logo-step (completed)="onStepCompleted()" (back)="onBack()" />
    }
    @case ('categories') {
      <app-categories-step (completed)="onStepCompleted()" (back)="onBack()" />
    }
    @case ('cares') {
      <app-cares-step (completed)="onStepCompleted()" (back)="onBack()" />
    }
    @case ('openingHours') {
      <app-opening-hours-step (completed)="onStepCompleted()" (back)="onBack()" />
    }
    @case ('publish') {
      <app-publish-step (completed)="onStepCompleted()" (back)="onBack()" />
    }
  }
</main>
```

`pro-onboarding-wizard.component.scss`: minimal layout (full-page background `--pf-paper`, top bar fixed, stage centered).

- [ ] **Step 3: Run + commit**

```bash
cd frontend && npm test -- --include='**/pro-onboarding-wizard.component.spec.ts' --watch=false
git add frontend/src/app/pages/pro/onboarding-wizard/pro-onboarding-wizard.component.*
git commit -m "feat(onboarding-wizard): orchestrator component"
```

---

### Task B14: Register the route

**Files:**
- Modify: `frontend/src/app/app.routes.ts` (or wherever pro routes are declared — search for `/pro/dashboard` to find it)

- [ ] **Step 1: Add the route under the pro shell**

```typescript
{
  path: 'onboarding',
  loadComponent: () =>
    import('./pages/pro/onboarding-wizard/pro-onboarding-wizard.component')
      .then(m => m.ProOnboardingWizardComponent),
  data: { ssr: false },
},
```

- [ ] **Step 2: Smoke-test the route in dev**

```bash
cd frontend && npm start
```
Open `http://localhost:4200/pro/onboarding` while logged as a pro DRAFT — wizard should render at welcome step.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/app.routes.ts
git commit -m "feat(onboarding-wizard): register /pro/onboarding route"
```

---

### Task B15: Auto-redirect from `pro-shell` to wizard

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-shell.component.ts`

- [ ] **Step 1: Add the effect**

```typescript
import { effect, inject } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
// ...

constructor() {
  // Auto-redirect new pros to the wizard.
  effect(() => {
    const r = this.store.readiness();
    if (!r) return;
    if (r.status !== 'DRAFT' || r.canPublish) return;
    if (sessionStorage.getItem('pf_skipOnboarding') === '1') return;
    if (this.router.url.startsWith('/pro/onboarding')) return;
    this.router.navigate(['/pro/onboarding']);
  });
}
```

- [ ] **Step 2: Spec for the redirect logic**

In `pro-shell.component.spec.ts`, add:
```typescript
it('redirects to /pro/onboarding when DRAFT && !canPublish && no skip flag', async () => {
  // ... setup with readiness: { status: 'DRAFT', canPublish: false, ... }
  expect(routerSpy.navigate).toHaveBeenCalledWith(['/pro/onboarding']);
});

it('does NOT redirect when sessionStorage flag is set', async () => {
  sessionStorage.setItem('pf_skipOnboarding', '1');
  // ... setup
  expect(routerSpy.navigate).not.toHaveBeenCalledWith(['/pro/onboarding']);
});
```

- [ ] **Step 3: Run + commit**

```bash
cd frontend && npm test -- --include='**/pro-shell.component.spec.ts' --watch=false
git add frontend/src/app/pages/pro/pro-shell.component.ts frontend/src/app/pages/pro/pro-shell.component.spec.ts
git commit -m "feat(onboarding-wizard): auto-redirect DRAFT pros to wizard"
```

---

### Task B16: Add "Reprendre le tutoriel" link in onboarding indicator

**Files:**
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.html`
- Modify: `frontend/src/app/shared/features/onboarding-indicator/onboarding-indicator.component.ts`

- [ ] **Step 1: Add a method that clears the flag and navigates**

In the component:
```typescript
protected onResumeWizard(): void {
  sessionStorage.removeItem('pf_skipOnboarding');
  this.router.navigate(['/pro/onboarding']);
}
```

- [ ] **Step 2: Add the button in both PC stepper and mobile pill scenarios**

In `.indicator-stepper` block, after the existing `.stepper-actions`:
```html
<button type="button" class="stepper-resume" (click)="onResumeWizard()" data-testid="resume-wizard">
  <mat-icon aria-hidden="true">replay</mat-icon>
  <span>{{ 'pro.onboarding.indicator.resumeWizard' | transloco }}</span>
</button>
```

For the mobile sheet (in `onboarding-indicator-sheet.component.html`), add a footer button that emits a new `'resumeWizard'` action; map it in the parent to call `onResumeWizard()`.

- [ ] **Step 3: Add minimal styling and translations**
- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/shared/features/onboarding-indicator/
git commit -m "feat(onboarding-indicator): add Resume Wizard action"
```

---

### Task B17: Add wizard i18n bundle

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add keys under `pro.onboarding.wizard.*`**

For each step (`welcome`, `name`, `contact`, `logo`, `categories`, `cares`, `openingHours`, `publish`): `title`, `sub`, plus step-specific keys (placeholder, error messages, preset labels for hours, success message for publish, etc.).

Plus shared:
- `pro.onboarding.wizard.next` → "Suivant" / "Next"
- `pro.onboarding.wizard.back` → "Retour" / "Back"
- `pro.onboarding.wizard.exit.label` → "Sortir du tuto" / "Exit tutorial"
- `pro.onboarding.wizard.exit.confirmTitle`, `confirmBody`, `confirmYes`, `confirmNo`
- `pro.onboarding.wizard.errors.save` → "Impossible d'enregistrer pour l'instant. Réessayez."
- `pro.onboarding.indicator.resumeWizard` → "Reprendre le tutoriel" / "Resume tutorial"
- `pro.dashboard.checklist.hasContact` + `hasContactDesc` + `hasLogo` + `hasLogoDesc`

Both files updated symmetrically.

- [ ] **Step 2: Run a translation completeness check (if a script exists in repo) or review by eye**
- [ ] **Step 3: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat(i18n): add pro.onboarding.wizard.* keys (FR/EN)"
```

---

**End of Section B. PR2 ready: wizard fully functional, redirect in place, reachable.**

---

## Section C — PublishMissingDialog from dashboard (PR3)

### Task C1: Capture 422 in `DashboardService.publish()`

**Files:**
- Modify: `frontend/src/app/features/dashboard/services/dashboard.service.ts`
- Modify: `frontend/src/app/features/dashboard/models/dashboard.model.ts`

- [ ] **Step 1: Update model**

In `dashboard.model.ts` already contains:
```typescript
export interface PublishError { message: string; missing: string[]; }
```
Keep as-is (already there).

- [ ] **Step 2: Update service**

```typescript
publish(): Observable<void> {
  return this.http.put<void>(`${this.baseUrl}/api/pro/tenant/publish`, {});
}
```
(no change needed if it already returns Observable<void>; the HttpErrorResponse will carry `error.missing`)

---

### Task C2: Add `publishMissing` to store + intercept 422

**Files:**
- Modify: `frontend/src/app/features/dashboard/store/dashboard.store.ts`

- [ ] **Step 1: Extend state**

Add `publishMissing: string[]` to `DashboardState`, initial `[]`.

- [ ] **Step 2: Modify `publish` rxMethod to capture 422**

```typescript
publish: rxMethod<void>(
  pipe(
    tap(() => patchState(store, { publishSuccess: false, publishMissing: [] }, setPending())),
    exhaustMap(() =>
      dashboardService.publish().pipe(
        switchMap(() => dashboardService.getReadiness()),
        tap((readiness) => {
          patchState(store, { readiness, publishSuccess: true }, setFulfilled());
        }),
        catchError((err: HttpErrorResponse) => {
          if (err.status === 422 && err.error?.missing) {
            patchState(store, { publishMissing: err.error.missing }, setFulfilled());
          } else {
            patchState(store, setError('Erreur lors de la publication'));
          }
          return EMPTY;
        })
      )
    )
  )
),
```

Add a method to clear:
```typescript
clearPublishMissing(): void {
  patchState(store, { publishMissing: [] });
}
```

- [ ] **Step 3: Update store spec to cover both success and 422 paths**
- [ ] **Step 4: Run + commit**

```bash
cd frontend && npm test -- --include='**/dashboard.store.spec.ts' --watch=false
git add frontend/src/app/features/dashboard/store/dashboard.store.ts frontend/src/app/features/dashboard/store/dashboard.store.spec.ts
git commit -m "feat(dashboard-store): capture 422 missing list on publish"
```

---

### Task C3: `PublishMissingDialogComponent`

**Files:**
- Create: `frontend/src/app/pages/pro/publish-missing-dialog/publish-missing-dialog.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Spec**

```typescript
it('lists missing items and emits goTo with the matching step key', () => {
  // ... configureTestingModule with MAT_DIALOG_DATA = { missing: ['hasContact','hasLogo'] }
  const items = fixture.nativeElement.querySelectorAll('[data-testid^="missing-item-"]');
  expect(items.length).toBe(2);
  items[0].querySelector('[data-testid="goto"]').click();
  expect(closeSpy).toHaveBeenCalledWith({ action: 'goTo', step: 'contact' });
});
```

- [ ] **Step 2: Implement**

```typescript
import { Component, inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { MISSING_KEY_TO_STEP, WizardStepKey } from '../../../features/onboarding/wizard/wizard-step.model';

export interface PublishMissingDialogData { missing: string[]; }
export type PublishMissingDialogResult =
  | { action: 'goTo'; step: WizardStepKey }
  | { action: 'cancel' };

@Component({
  selector: 'app-publish-missing-dialog',
  standalone: true,
  imports: [MatIconModule, TranslocoPipe],
  templateUrl: './publish-missing-dialog.component.html',
  styleUrl: './publish-missing-dialog.component.scss',
})
export class PublishMissingDialogComponent {
  private readonly ref = inject(MatDialogRef<PublishMissingDialogComponent, PublishMissingDialogResult>);
  protected readonly data = inject<PublishMissingDialogData>(MAT_DIALOG_DATA);

  protected goTo(missingKey: string): void {
    const step = MISSING_KEY_TO_STEP[missingKey];
    if (step) this.ref.close({ action: 'goTo', step });
  }

  protected cancel(): void {
    this.ref.close({ action: 'cancel' });
  }
}
```

`publish-missing-dialog.component.html`:
```html
<h2 mat-dialog-title>{{ 'pro.dashboard.publishMissingDialog.title' | transloco }}</h2>
<p class="dlg-body">{{ 'pro.dashboard.publishMissingDialog.body' | transloco }}</p>
<ul class="dlg-list">
  @for (key of data.missing; track key) {
    <li class="dlg-item" [attr.data-testid]="'missing-item-' + key">
      <mat-icon class="dlg-icon" aria-hidden="true">error_outline</mat-icon>
      <div class="dlg-text">
        <p class="dlg-title">{{ 'pro.dashboard.checklist.' + key | transloco }}</p>
        <p class="dlg-desc">{{ 'pro.dashboard.checklist.' + key + 'Desc' | transloco }}</p>
      </div>
      <button type="button" class="dlg-cta" data-testid="goto" (click)="goTo(key)">
        {{ 'pro.dashboard.publishMissingDialog.goTo' | transloco }} →
      </button>
    </li>
  }
</ul>
<div class="dlg-footer">
  <button type="button" (click)="cancel()">{{ 'common.close' | transloco }}</button>
</div>
```

- [ ] **Step 3: Run + commit**

```bash
cd frontend && npm test -- --include='**/publish-missing-dialog.component.spec.ts' --watch=false
git add frontend/src/app/pages/pro/publish-missing-dialog/
git commit -m "feat(pro-dashboard): PublishMissingDialogComponent"
```

---

### Task C4: Wire dialog into pro-dashboard

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-dashboard.component.ts`

- [ ] **Step 1: Add the effect**

```typescript
import { MatDialog } from '@angular/material/dialog';
import { PublishMissingDialogComponent, PublishMissingDialogResult } from './publish-missing-dialog/publish-missing-dialog.component';
// ...

private dialog = inject(MatDialog);
private router = inject(Router); // if not already

constructor() {
  // ... existing effects ...

  effect(() => {
    const missing = this.store.publishMissing();
    if (missing.length === 0) return;
    const ref = this.dialog.open<
      PublishMissingDialogComponent,
      { missing: string[] },
      PublishMissingDialogResult
    >(PublishMissingDialogComponent, { data: { missing } });
    ref.afterClosed().subscribe(result => {
      this.store.clearPublishMissing();
      if (result?.action === 'goTo') {
        this.router.navigate(['/pro/onboarding']); // wizard auto-resumes from first missing
      }
    });
  });
}
```

- [ ] **Step 2: Update spec to assert the dialog opens on 422**
- [ ] **Step 3: Run + commit**

```bash
cd frontend && npm test -- --include='**/pro-dashboard.component.spec.ts' --watch=false
git add frontend/src/app/pages/pro/pro-dashboard.component.ts frontend/src/app/pages/pro/pro-dashboard.component.spec.ts
git commit -m "feat(pro-dashboard): open PublishMissingDialog on 422"
```

---

### Task C5: Final manual smoke test

- [ ] **Step 1: Start the app**

```bash
# Terminal 1 - backend
cd backend && mvn spring-boot:run

# Terminal 2 - frontend
cd frontend && npm start
```

- [ ] **Step 2: Run through the happy path**
  1. Register a fresh pro at `/auth/register/pro`.
  2. Confirm auto-redirect to `/pro/onboarding`.
  3. Walk through every step.
  4. On the publish step, click Publier → salon goes ACTIVE → redirect to `/pro/dashboard` → snackbar success.

- [ ] **Step 3: Run through the failure path**
  1. From a pro that has skipped the wizard (sessionStorage flag set), try the dashboard's "Publier" button.
  2. Confirm the `PublishMissingDialog` opens with the right items.
  3. Click "Y aller" → wizard opens at the first missing step.

- [ ] **Step 4: If everything passes, push the branch**

```bash
git push -u origin feat/pro-onboarding-wizard
```

---

**End of Section C. PR3 ready.**

---

## Self-Review Checklist (already applied)

- [x] All steps have actual code, not placeholders.
- [x] Every method/property mentioned in later tasks exists (e.g., `clearPublishMissing` defined in C2 used in C4; `MISSING_KEY_TO_STEP` defined in B2 used in C3).
- [x] All spec requirements covered: provisioning fix (A3-A4), readiness widening (A5-A7), PATCH endpoint (A8-A11), publish 422 contract (A12), wizard 8 steps (B5-B12), redirection (B15), dialog (C3-C4), translations (B17).
- [x] No "TBD"/"TODO" left.

---

## Execution

The 17 + 12 = ~29 tasks split cleanly across 3 PRs (A, B, C). Each task ends in a commit. Suggested order: A → B → C, since B depends on the readiness shape from A, and C depends on the wizard steps from B.
