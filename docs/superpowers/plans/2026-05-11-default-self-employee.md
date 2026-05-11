# Default self-employee + stepper header back arrow — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Guarantee the invariant "every tenant has ≥1 active employee, every care has ≥1 assigned employee" by auto-creating a "pro-self" employee on tenant provision and as a fallback at care creation. Backfill legacy tenants via Flyway migration V4. Fix the stepper UI by moving the Back action from a bottom button to a header arrow.

**Architecture:** A new `EmployeeService.createSelfEmployee(User owner)` method, called from `TenantProvisioningService.provision()` after the tenant is saved (idempotent, scoped to the new tenant context). `CareService.create()` checks if the saved Care has any assigned employees and, if not, attaches the pro-self employee found by `userId = tenant.ownerId`. A Flyway tenant-scoped migration `V4__seed_self_employee.sql` uses a new `${tenantSlug}` placeholder to backfill legacy tenants. Frontend: replace the bottom Back button with an `arrow_back` icon in the stepper header, visible on steps 2 and 3.

**Tech Stack:** Spring Boot 3.5.4 + JPA + Flyway (tenant-scoped) + Oracle Free / H2 test + JUnit 5 + AssertJ + Mockito (backend), Angular 20 standalone + signals + Karma/Jasmine + Transloco (frontend).

---

## File Structure

**Backend — modified:**
- `backend/src/main/java/com/luxpretty/app/employee/app/EmployeeService.java` — new method `createSelfEmployee(User owner)`
- `backend/src/main/java/com/luxpretty/app/employee/repo/EmployeeRepository.java` — new finder `findByUserId(Long userId)`
- `backend/src/main/java/com/luxpretty/app/tenant/app/TenantProvisioningService.java` — inject `EmployeeService` + `UserRepository`, call after save
- `backend/src/main/java/com/luxpretty/app/care/app/CareService.java` — fallback attach pro-self when no employees on the saved Care
- `backend/src/main/java/com/luxpretty/app/care/repo/CareRepository.java` — verify no method conflicts (read-only)
- `backend/src/main/java/com/luxpretty/app/multitenancy/TenantFlywayService.java` — add `tenantSlug` placeholder

**Backend — created:**
- `backend/src/main/resources/db/migration/tenant/V4__seed_self_employee.sql` — new migration

**Backend — tests created or extended:**
- `backend/src/test/java/com/luxpretty/app/employee/app/EmployeeServiceTests.java` — extend with 3 createSelfEmployee tests (may not exist yet — create if absent)
- `backend/src/test/java/com/luxpretty/app/tenant/app/TenantProvisioningServiceTests.java` — extend with provision-creates-employee test
- `backend/src/test/java/com/luxpretty/app/care/app/CareServiceTests.java` — extend with 2 fallback tests (may not exist yet — create if absent)

**Frontend — modified:**
- `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts` — replace bottom Back button with header arrow
- `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.spec.ts` — add 3 tests for header arrow visibility/click

---

## Task 1: Backend — `EmployeeRepository.findByUserId`

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/employee/repo/EmployeeRepository.java`

**Existing context:** The repository likely extends `JpaRepository<Employee, Long>`. Spring Data JPA generates implementations from method names like `findByUserId`.

- [ ] **Step 1: Read the current repository**

Run: `cat backend/src/main/java/com/luxpretty/app/employee/repo/EmployeeRepository.java`

Confirm it's a Spring Data JPA interface. If a method `findByUserId` already exists, skip Steps 2-3.

- [ ] **Step 2: Add the `findByUserId` finder**

Add to the interface, near other finders:

```java
java.util.Optional<Employee> findByUserId(Long userId);
```

Add the import if not already present: `import java.util.Optional;`

- [ ] **Step 3: Verify the project still compiles**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS, no compile errors.

- [ ] **Step 4: Commit**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add backend/src/main/java/com/luxpretty/app/employee/repo/EmployeeRepository.java
git commit -m "$(cat <<'EOF'
feat(employee-repo): findByUserId finder

Used by createSelfEmployee idempotency check and by CareService fallback
to locate the pro-self employee from a tenant's ownerId.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Backend — `EmployeeService.createSelfEmployee` (TDD)

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/employee/app/EmployeeService.java`
- Test: `backend/src/test/java/com/luxpretty/app/employee/app/EmployeeServiceTests.java`

### 2a — Write failing tests first

- [ ] **Step 1: Check if `EmployeeServiceTests.java` exists**

Run: `ls backend/src/test/java/com/luxpretty/app/employee/app/EmployeeServiceTests.java 2>/dev/null`

If it exists, read it to follow the existing test style. If not, you'll create it in Step 2.

- [ ] **Step 2: Write the test file (or extend it)**

Path: `backend/src/test/java/com/luxpretty/app/employee/app/EmployeeServiceTests.java`

If the file doesn't exist, create it with this header:

```java
package com.luxpretty.app.employee.app;

import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.users.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTests {

    @Mock EmployeeRepository employeeRepository;
    // Mock any other dependencies EmployeeService requires — check its constructor:
    //   cat backend/src/main/java/com/luxpretty/app/employee/app/EmployeeService.java | head -30

    @InjectMocks EmployeeService service;

    private User owner;

    @BeforeEach
    void setupTenant() {
        TenantContext.setCurrentTenant("test-tenant");
        owner = User.builder()
                .id(42L)
                .name("Sophie Martin")
                .email("sophie@martin.test")
                .build();
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createSelfEmployee_createsEmployee_whenAbsent() {
        when(employeeRepository.findByUserId(42L)).thenReturn(Optional.empty());
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee created = service.createSelfEmployee(owner);

        assertThat(created.getUserId()).isEqualTo(42L);
        assertThat(created.getName()).isEqualTo("Sophie Martin");
        assertThat(created.getEmail()).isEqualTo("sophie@martin.test");
        assertThat(created.getPhone()).isNull();
        assertThat(created.isActive()).isTrue();
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    void createSelfEmployee_isIdempotent_returnsExisting() {
        Employee existing = new Employee();
        existing.setId(99L);
        existing.setUserId(42L);
        existing.setName("Sophie Martin");
        existing.setEmail("sophie@martin.test");
        existing.setActive(true);
        when(employeeRepository.findByUserId(42L)).thenReturn(Optional.of(existing));

        Employee result = service.createSelfEmployee(owner);

        assertThat(result).isSameAs(existing);
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void createSelfEmployee_failsWithoutTenantContext() {
        TenantContext.clear();

        assertThatThrownBy(() -> service.createSelfEmployee(owner))
                .isInstanceOf(IllegalStateException.class);
    }
}
```

Note for the implementer: If `EmployeeService`'s constructor takes more dependencies than just `EmployeeRepository` (likely — Lombok or explicit constructor), update the `@Mock` declarations to mock all required dependencies. Use `@InjectMocks` to wire them.

- [ ] **Step 3: Run tests — expect compilation failure**

Run: `cd backend && mvn test -Dtest=EmployeeServiceTests`
Expected: compilation fails on `service.createSelfEmployee(owner)` because the method does not exist yet.

### 2b — Implement to make tests pass

- [ ] **Step 4: Read `EmployeeService.java` to find a good insertion point**

Run: `grep -n "@Transactional\|public" backend/src/main/java/com/luxpretty/app/employee/app/EmployeeService.java | head -15`

Look at the existing method signatures (e.g., `listForCare`, `listAll`, `create`). Pick a coherent placement (e.g., right before `create`).

- [ ] **Step 5: Add `createSelfEmployee`**

Add the import if missing: `import com.luxpretty.app.users.domain.User;`

Add the method:

```java
/**
 * Create the "pro-self" employee linked to the tenant owner (User).
 * Idempotent: if an employee with userId == owner.id already exists in
 * the current tenant schema, returns that one unchanged.
 *
 * Requires an active TenantContext.
 */
@Transactional
public Employee createSelfEmployee(User owner) {
    TenantContext.requireActive();

    return employeeRepository.findByUserId(owner.getId())
            .orElseGet(() -> {
                Employee e = new Employee();
                e.setUserId(owner.getId());
                e.setName(owner.getName());
                e.setEmail(owner.getEmail());
                e.setPhone(null);
                e.setActive(true);
                return employeeRepository.save(e);
            });
}
```

- [ ] **Step 6: Run tests — expect all green**

Run: `cd backend && mvn test -Dtest=EmployeeServiceTests`
Expected: 3 tests passing.

If `createSelfEmployee_failsWithoutTenantContext` doesn't pass, check that `TenantContext.requireActive()` actually throws `IllegalStateException` when no tenant is set. If it throws a different exception, update the test's assertion type accordingly.

- [ ] **Step 7: Commit**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add backend/src/main/java/com/luxpretty/app/employee/app/EmployeeService.java \
        backend/src/test/java/com/luxpretty/app/employee/app/EmployeeServiceTests.java
git commit -m "$(cat <<'EOF'
feat(employee-service): createSelfEmployee(owner) — idempotent

Creates the "pro-self" employee linked to the tenant owner User, with
name/email copied from the owner and phone left null (pro renseigne via
le profil employé). Idempotent: returns the existing row if one already
matches userId. Requires an active TenantContext.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Backend — Hook `createSelfEmployee` in `TenantProvisioningService`

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/tenant/app/TenantProvisioningService.java`
- Test: `backend/src/test/java/com/luxpretty/app/tenant/app/TenantProvisioningServiceTests.java` (check if exists; create or extend)

### 3a — Write failing test

- [ ] **Step 1: Check whether the test file exists**

Run: `ls backend/src/test/java/com/luxpretty/app/tenant/app/TenantProvisioningServiceTests.java 2>/dev/null`

If yes, read it. If no, create one.

- [ ] **Step 2: Add a test that asserts the provision flow calls `createSelfEmployee`**

This is a Mockito-based test that asserts the **collaboration**, not the persistence (persistence is tested in Task 2). The test stubs `EmployeeService.createSelfEmployee` and verifies it's called with the right argument inside the tenant context.

Append (or create) this test:

```java
package com.luxpretty.app.tenant.app;

import com.luxpretty.app.employee.app.EmployeeService;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.multitenancy.TenantSchemaManager;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceTests {

    @Mock TenantRepository tenantRepository;
    @Mock TenantSchemaManager tenantSchemaManager;
    @Mock EmployeeService employeeService;

    @InjectMocks TenantProvisioningService service;

    @Test
    void provision_createsSelfEmployee_forNewTenant() {
        User owner = User.builder()
                .id(7L)
                .name("Alice")
                .email("alice@x.test")
                .build();

        when(tenantRepository.existsBySlug(any())).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(123L);
            return t;
        });

        Tenant result = service.provision(owner);

        assertThat(result.getOwnerId()).isEqualTo(7L);
        ArgumentCaptor<User> ownerCaptor = ArgumentCaptor.forClass(User.class);
        verify(employeeService).createSelfEmployee(ownerCaptor.capture());
        assertThat(ownerCaptor.getValue().getId()).isEqualTo(7L);
        // After provision, the tenant context should be cleared (no leak).
        // We can't directly assert internals, but TenantContext.getCurrentTenant() should be empty.
    }
}
```

Note: if the existing `TenantProvisioningServiceTests` has more setup (Spring context, etc.), follow that pattern instead.

- [ ] **Step 3: Run — expect failure or compile error**

Run: `cd backend && mvn test -Dtest=TenantProvisioningServiceTests`
Expected: the new test fails because the production code doesn't inject `EmployeeService` yet. If compilation fails on `@Mock EmployeeService`, that's also expected — it'll be fixed by the implementation.

### 3b — Implement

- [ ] **Step 4: Modify `TenantProvisioningService.java`**

Replace the file content with:

```java
package com.luxpretty.app.tenant.app;

import com.luxpretty.app.employee.app.EmployeeService;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.multitenancy.TenantSchemaManager;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.domain.TenantStatus;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantProvisioningService {

    private static final Logger logger = LoggerFactory.getLogger(TenantProvisioningService.class);

    private final TenantRepository tenantRepository;
    private final TenantSchemaManager tenantSchemaManager;
    private final EmployeeService employeeService;

    public TenantProvisioningService(
            TenantRepository tenantRepository,
            TenantSchemaManager tenantSchemaManager,
            EmployeeService employeeService) {
        this.tenantRepository = tenantRepository;
        this.tenantSchemaManager = tenantSchemaManager;
        this.employeeService = employeeService;
    }

    @Transactional
    public Tenant provision(User owner) {
        String baseSlug = SlugUtils.toSlug(owner.getName());
        String slug = ensureUniqueSlug(baseSlug);

        logger.info("Provisioning tenant for user {} with slug {}", owner.getId(), slug);

        tenantSchemaManager.provisionSchema(slug);

        Tenant tenant = Tenant.builder()
                .slug(slug)
                .name(null)
                .ownerId(owner.getId())
                .status(TenantStatus.DRAFT)
                .build();

        Tenant saved = tenantRepository.save(tenant);

        // Create the "pro-self" employee inside the new tenant's schema.
        TenantContext.setCurrentTenant(slug);
        try {
            employeeService.createSelfEmployee(owner);
        } finally {
            TenantContext.clear();
        }

        logger.info("Tenant {} provisioned successfully (id={})", slug, saved.getId());
        return saved;
    }

    private String ensureUniqueSlug(String baseSlug) {
        if (!tenantRepository.existsBySlug(baseSlug)) {
            return baseSlug;
        }
        int counter = 2;
        String candidate;
        do {
            candidate = baseSlug + "-" + counter;
            counter++;
        } while (tenantRepository.existsBySlug(candidate));
        return candidate;
    }
}
```

- [ ] **Step 5: Run the test — expect pass**

Run: `cd backend && mvn test -Dtest=TenantProvisioningServiceTests`
Expected: green.

- [ ] **Step 6: Run the broader backend suite to catch ripples**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS. If any test that uses `TenantProvisioningService` is now broken due to the new constructor arg, fix it by adding `@Mock EmployeeService` / passing a stub.

- [ ] **Step 7: Commit**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add backend/src/main/java/com/luxpretty/app/tenant/app/TenantProvisioningService.java \
        backend/src/test/java/com/luxpretty/app/tenant/app/TenantProvisioningServiceTests.java
# stage any unavoidable test fixes from Step 6:
git add -A backend/src/test
git commit -m "$(cat <<'EOF'
feat(tenant-provisioning): create self-employee on tenant creation

After saving the tenant row, switch to the tenant context and call
EmployeeService.createSelfEmployee(owner) so every new tenant lands with
an active employee linked to the owner. The TenantContext is cleared in
a finally block so callers never see a leaked context.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Backend — `CareService.create` fallback (TDD)

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/care/app/CareService.java`
- Test: `backend/src/test/java/com/luxpretty/app/care/app/CareServiceTests.java` (likely doesn't exist — create)

**Context:** The `CareRequest` DTO has no field for assigned employees. The frontend never sends any. Today, `CareService.create()` just saves the Care without touching `assignedCares`/employees, leaving the join table `EMPLOYEE_CARES` empty for the new care. We add a post-save fallback: if no employees are linked, find the pro-self employee (via `tenant.ownerId` → `findByUserId`) and link it.

### 4a — Write failing tests

- [ ] **Step 1: Check `CareServiceTests.java` existence**

Run: `ls backend/src/test/java/com/luxpretty/app/care/app/CareServiceTests.java 2>/dev/null`

- [ ] **Step 2: Read `CareService.java` constructor to know what to mock**

Run: `head -50 backend/src/main/java/com/luxpretty/app/care/app/CareService.java`

Current constructor (read at plan time):
```java
public CareService(CareRepository repo, CategoryRepository categoryRepository,
                   FileStorageService fileStorageService, CareBookingRepository careBookingRepository)
```

We will add 3 dependencies: `EmployeeRepository`, `TenantRepository`. The test file must mock all 5-6 deps.

- [ ] **Step 3: Create the test file**

Path: `backend/src/test/java/com/luxpretty/app/care/app/CareServiceTests.java`

```java
package com.luxpretty.app.care.app;

import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.care.domain.Care;
import com.luxpretty.app.care.domain.CareStatus;
import com.luxpretty.app.care.repo.CareRepository;
import com.luxpretty.app.care.web.dto.CareRequest;
import com.luxpretty.app.category.domain.Category;
import com.luxpretty.app.category.repo.CategoryRepository;
import com.luxpretty.app.common.storage.FileStorageService;
import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareServiceTests {

    @Mock CareRepository repo;
    @Mock CategoryRepository categoryRepository;
    @Mock FileStorageService fileStorageService;
    @Mock CareBookingRepository careBookingRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock TenantRepository tenantRepository;

    @InjectMocks CareService service;

    @BeforeEach
    void setupTenant() {
        TenantContext.setCurrentTenant("salon-sophie");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private CareRequest sampleRequest() {
        return new CareRequest(
                "Soin visage",
                5000,
                "Description",
                60,
                CareStatus.ACTIVE,
                1L,
                null  // images
        );
    }

    private Tenant tenantWithOwner(long ownerId) {
        Tenant t = new Tenant();
        t.setSlug("salon-sophie");
        t.setOwnerId(ownerId);
        return t;
    }

    private Employee employee(long id, long userId) {
        Employee e = new Employee();
        e.setId(id);
        e.setUserId(userId);
        e.setName("Pro Self");
        e.setActive(true);
        return e;
    }

    @Test
    void create_attachesProSelfEmployee_whenCareHasNoEmployees() {
        Category cat = new Category();
        cat.setId(1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));

        Care saved = new Care();
        saved.setId(42L);
        saved.setAssignedEmployees(new HashSet<>());  // explicitly empty
        when(repo.save(any(Care.class))).thenReturn(saved);

        when(tenantRepository.findBySlug("salon-sophie"))
                .thenReturn(Optional.of(tenantWithOwner(7L)));
        Employee proSelf = employee(99L, 7L);
        when(employeeRepository.findByUserId(7L)).thenReturn(Optional.of(proSelf));

        service.create(sampleRequest());

        assertThat(saved.getAssignedEmployees()).containsExactly(proSelf);
    }

    @Test
    void create_respectsExplicitAssignedEmployees() {
        Category cat = new Category();
        cat.setId(1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));

        Employee bob = employee(50L, 50L);
        Employee alice = employee(51L, 51L);
        Care saved = new Care();
        saved.setId(42L);
        saved.setAssignedEmployees(new HashSet<>(java.util.Set.of(bob, alice)));
        when(repo.save(any(Care.class))).thenReturn(saved);

        service.create(sampleRequest());

        // Pro-self is NOT added because the care already has employees.
        assertThat(saved.getAssignedEmployees()).containsExactlyInAnyOrder(bob, alice);
    }
}
```

Note: `Care.getAssignedEmployees` must exist. Looking at `Employee.java`, the join is owned by Employee (`@ManyToMany ... Set<Care> assignedCares`). On `Care`, there should be a `@ManyToMany(mappedBy = "assignedCares") Set<Employee> assignedEmployees`. If `Care.java` doesn't have that field, this test will fail at compile time — see Step 4 below.

- [ ] **Step 4: Check that `Care.java` has `assignedEmployees`**

Run: `grep -n "assignedEmployees\|Employee" backend/src/main/java/com/luxpretty/app/care/domain/Care.java`

If `Care` already has `Set<Employee> assignedEmployees`, proceed. If not, **STOP and escalate as BLOCKED**: the inverse side of the relation needs to be added, which is a small but non-trivial JPA change requiring care (cascade, fetch, etc.). The simpler alternative is to bypass and add the link via `EmployeeRepository` save after the Care is persisted (modify the implementation accordingly).

- [ ] **Step 5: Run — expect failure**

Run: `cd backend && mvn test -Dtest=CareServiceTests`
Expected: the test fails (or fails to compile if a dependency is missing in CareService's constructor).

### 4b — Implement

- [ ] **Step 6: Modify `CareService.java` to add dependencies and the fallback**

Read the file first (`cat backend/src/main/java/com/luxpretty/app/care/app/CareService.java | head -50`). Add the imports:

```java
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.employee.domain.Employee;
import java.util.Optional;
```

Replace the constructor and inject the new deps:

```java
private final CareRepository repo;
private final CategoryRepository categoryRepository;
private final FileStorageService fileStorageService;
private final CareBookingRepository careBookingRepository;
private final EmployeeRepository employeeRepository;
private final TenantRepository tenantRepository;

public CareService(CareRepository repo, CategoryRepository categoryRepository,
                   FileStorageService fileStorageService, CareBookingRepository careBookingRepository,
                   EmployeeRepository employeeRepository, TenantRepository tenantRepository) {
    this.repo = repo;
    this.categoryRepository = categoryRepository;
    this.fileStorageService = fileStorageService;
    this.careBookingRepository = careBookingRepository;
    this.employeeRepository = employeeRepository;
    this.tenantRepository = tenantRepository;
}
```

At the **end** of `create(CareRequest req)` (just before `return CareMapper.toResponse(saved);`), insert:

```java
// Fallback: if no employees are assigned to this care, attach the
// "pro-self" employee (= tenant owner) so the booking stepper has at
// least one candidate. If the request later carries an explicit
// employees list (not in the current DTO), this branch is skipped.
if (saved.getAssignedEmployees() == null || saved.getAssignedEmployees().isEmpty()) {
    String tenantSlug = TenantContext.requireActive();
    Optional<Employee> proSelf = tenantRepository.findBySlug(tenantSlug)
            .flatMap(t -> employeeRepository.findByUserId(t.getOwnerId()));
    proSelf.ifPresent(e -> {
        if (saved.getAssignedEmployees() == null) {
            saved.setAssignedEmployees(new java.util.HashSet<>());
        }
        saved.getAssignedEmployees().add(e);
        repo.save(saved);
    });
}
```

If `Care` doesn't have `assignedEmployees` (Task 4a Step 4 escalation), use the **alternative path**: modify the `Employee` side. Add to the implementation:

```java
if (... saved has no employees ...) {
    String tenantSlug = TenantContext.requireActive();
    tenantRepository.findBySlug(tenantSlug)
        .flatMap(t -> employeeRepository.findByUserId(t.getOwnerId()))
        .ifPresent(e -> {
            e.getAssignedCares().add(saved);
            employeeRepository.save(e);
        });
}
```

Adjust the tests in 4a Step 3 accordingly (assertions move from `saved.getAssignedEmployees()` to `proSelf.getAssignedCares()`).

- [ ] **Step 7: Run — expect pass**

Run: `cd backend && mvn test -Dtest=CareServiceTests`
Expected: 2 tests passing.

- [ ] **Step 8: Run the full backend suite**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS. If any existing test for `CareService` (or test that constructs it) broke due to the new constructor args, fix it by adding the new mocks.

- [ ] **Step 9: Commit**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add backend/src/main/java/com/luxpretty/app/care/app/CareService.java \
        backend/src/test/java/com/luxpretty/app/care/app/CareServiceTests.java
git add -A backend/src/test
git commit -m "$(cat <<'EOF'
feat(care): fallback attach pro-self employee when care has no assignees

After saving a new Care, if no employee is linked, find the tenant's
pro-self employee (via tenant.ownerId → employee.userId) and attach it.
Keeps the invariant "every care has at least one employee", which the
pro-side booking stepper now requires.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Backend — Flyway V4 migration for legacy tenants

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/multitenancy/TenantFlywayService.java` — add `tenantSlug` placeholder
- Create: `backend/src/main/resources/db/migration/tenant/V4__seed_self_employee.sql`

**Context:** New tenants now get pro-self via Task 3. Legacy tenants provisioned before this PR still have no employee. We need a one-shot Flyway migration applied tenant-wide.

The current `TenantFlywayService` passes two placeholders: `${tenantSchema}` (uppercased schema name like `TENANT_SOPHIE`) and `${appSchema}`. To resolve the tenant by slug (which is kebab-case lowercase), we need a third placeholder: `${tenantSlug}`.

### 5a — Add the slug placeholder

- [ ] **Step 1: Modify `TenantFlywayService.migrate(String tenantSchema)`**

The current signature is `migrate(String tenantSchema)`. We need to thread the slug through. Read `TenantSchemaManager.toSchemaName(slug)` — it maps `slug → schemaName`. We can reverse: at the call site (where `migrate` is invoked) the slug is known.

Find the call site:

Run: `grep -rn "tenantFlywayService.migrate" backend/src/main/java`

Expected: a single call in `TenantSchemaManager.provisionSchema(slug)` around line 108. The slug is in scope there.

- [ ] **Step 2: Update `TenantFlywayService` to accept slug and pass as placeholder**

Replace the method signature:

```java
public MigrateResult migrate(String tenantSchema, String tenantSlug) {
    if (!StringUtils.hasText(tenantSchema)) {
        throw new IllegalArgumentException("Tenant schema must not be blank");
    }
    if (!StringUtils.hasText(tenantSlug)) {
        throw new IllegalArgumentException("Tenant slug must not be blank");
    }
    if (!StringUtils.hasText(provisioningUsername) || !StringUtils.hasText(provisioningPassword)) {
        throw new IllegalStateException(
                "Tenant Flyway requires app.multitenancy.provisioning.{username,password} to be set"
        );
    }

    logger.info("Running tenant Flyway migrations for schema {} (slug {})", tenantSchema, tenantSlug);

    Flyway flyway = Flyway.configure()
            .dataSource(provisioningJdbcUrl, provisioningUsername, provisioningPassword)
            .schemas(tenantSchema)
            .defaultSchema(tenantSchema)
            .locations(TENANT_LOCATIONS)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .placeholders(Map.of(
                    "tenantSchema", tenantSchema,
                    "tenantSlug", tenantSlug,
                    "appSchema", applicationSchemaName
            ))
            .load();

    MigrateResult result = flyway.migrate();
    logger.info("Tenant Flyway migration done for {} — {} migrations applied",
            tenantSchema, result.migrationsExecuted);
    return result;
}
```

- [ ] **Step 3: Update the call site in `TenantSchemaManager`**

Find the call (around line 108):

Run: `grep -n "tenantFlywayService.migrate" backend/src/main/java/com/luxpretty/app/multitenancy/TenantSchemaManager.java`

Change `tenantFlywayService.migrate(schemaName)` to `tenantFlywayService.migrate(schemaName, slug)`. The variable `slug` is the method parameter for `provisionSchema(String slug)`.

If there are other callers (`grep -rn "tenantFlywayService.migrate" backend/src`), update them too.

- [ ] **Step 4: Run all tests to confirm no compile break**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS. (No new tests yet; we just want to confirm we didn't break compile.)

- [ ] **Step 5: Commit the slug-placeholder support**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add backend/src/main/java/com/luxpretty/app/multitenancy/TenantFlywayService.java \
        backend/src/main/java/com/luxpretty/app/multitenancy/TenantSchemaManager.java
git commit -m "$(cat <<'EOF'
feat(flyway-tenant): add tenantSlug placeholder for migrations

Tenant migrations sometimes need to reference rows in the shared
USERS/TENANTS tables by tenant.slug (e.g. seeding the pro-self employee
for legacy tenants). The slug is kebab-case lowercase, while the schema
name is uppercased and prefixed (TENANT_<UPPER_UNDERSCORED>). Pass both
into Flyway placeholders so SQL migrations can use the right form.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### 5b — Write the migration

- [ ] **Step 6: Create V4 SQL file**

Path: `backend/src/main/resources/db/migration/tenant/V4__seed_self_employee.sql`

```sql
-- Seed the "pro-self" employee for legacy tenants that were provisioned
-- before TenantProvisioningService started creating one automatically.
--
-- Idempotent on both INSERTs:
--   1. EMPLOYEES insert fires only if no row with user_id = owner_id exists.
--   2. EMPLOYEE_CARES insert fires only for cares that currently have zero employees.
--
-- Placeholders resolved by TenantFlywayService:
--   ${tenantSchema} → e.g. TENANT_SOPHIE
--   ${tenantSlug}   → e.g. sophie  (kebab-case lowercase)
--   ${appSchema}    → shared app schema, e.g. APPUSER
--
-- Cross-schema access uses the synonyms USERS and TENANTS created at the V1 baseline.

-- 1. Create the pro-self Employee from the tenant's owner User if missing.
INSERT INTO "${tenantSchema}".EMPLOYEES (USER_ID, NAME, EMAIL, PHONE, ACTIVE, CREATED_AT)
SELECT u.ID, u.NAME, u.EMAIL, NULL, 1, CURRENT_TIMESTAMP
FROM "${tenantSchema}".USERS u
JOIN "${tenantSchema}".TENANTS t ON t.OWNER_ID = u.ID
WHERE t.SLUG = '${tenantSlug}'
  AND NOT EXISTS (
      SELECT 1 FROM "${tenantSchema}".EMPLOYEES e WHERE e.USER_ID = u.ID
  );

-- 2. Link the (now-existing) pro-self employee to every orphan care
--    (= care with no row in EMPLOYEE_CARES).
INSERT INTO "${tenantSchema}".EMPLOYEE_CARES (EMPLOYEE_ID, CARE_ID)
SELECT e.ID, c.ID
FROM "${tenantSchema}".EMPLOYEES e
JOIN "${tenantSchema}".USERS u ON u.ID = e.USER_ID
JOIN "${tenantSchema}".TENANTS t ON t.OWNER_ID = u.ID
CROSS JOIN "${tenantSchema}".CARES c
WHERE t.SLUG = '${tenantSlug}'
  AND NOT EXISTS (
      SELECT 1 FROM "${tenantSchema}".EMPLOYEE_CARES ec WHERE ec.CARE_ID = c.ID
  );
```

- [ ] **Step 7: Verify column casing on Oracle**

Run: `grep -nE "(USER_ID|NAME|EMAIL|PHONE|ACTIVE|CREATED_AT)" backend/src/main/resources/db/migration/tenant/V1__baseline.sql | head -10`

Confirm the column names in V1 are uppercase (`USER_ID`, etc.). If V1 uses lowercase or quoted mixed-case, adapt V4 accordingly. The convention in Oracle defaults to uppercase unless quoted.

Also verify the EMPLOYEE_CARES join table columns (`employee_id` / `care_id` per JPA mapping in `Employee.java`). Run:

```bash
grep -nE "EMPLOYEE_CARES" backend/src/main/resources/db/migration/tenant/V1__baseline.sql
```

Adjust column names in the V4 to match what V1 actually created.

- [ ] **Step 8: Verify the migration applies cleanly on a fresh tenant**

Cleanest test: provision a fresh test tenant (via integration test or via dev DB) and confirm the migration applies. Realistic check at plan time: run `mvn test` — this exercises the schema provisioning path used in tests.

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS. If a test fails because the V4 SQL references a table that doesn't exist in the test profile (e.g. H2 path doesn't include EMPLOYEE_CARES with same shape), check `TenantSchemaManager.createTenantTablesH2()` and align.

If the test DB uses H2 and the SQL syntax is incompatible (Oracle-only), the migration may need an H2 variant. Inspect `CLAUDE.md` and existing V2/V3 migrations for the pattern. If H2 ignores or fails on V4, the simplest fix is to wrap H2 in `TenantSchemaManager.createTenantTablesH2()` to also create the pro-self employee — but only if H2 tests actually exercise the Flyway path.

- [ ] **Step 9: Commit the migration**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add backend/src/main/resources/db/migration/tenant/V4__seed_self_employee.sql
git commit -m "$(cat <<'EOF'
feat(flyway): V4 seed pro-self employee for legacy tenants

Idempotent migration that backfills the "pro-self" employee (= tenant
owner) for tenants provisioned before this PR. Also links that employee
to every care that currently has no employee, so legacy salons with
existing cares stay reservable.

Uses the new ${tenantSlug} placeholder to resolve the tenant row in
TENANTS via slug, and the cross-schema synonyms USERS / TENANTS created
at the V1 baseline.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Frontend — Stepper header back arrow (TDD)

**Files:**
- Modify: `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts`
- Modify: `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.spec.ts`

### 6a — Write failing tests

- [ ] **Step 1: Open the existing spec to add tests**

Read: `cat frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.spec.ts`

Add the following 3 tests at the bottom of the describe block (before the closing `});`):

```ts
it('does not render the header back arrow on step 1', async () => {
  await setup();
  const fixture = TestBed.createComponent(BookingStepperComponent);
  fixture.detectChanges();
  const arrow = fixture.nativeElement.querySelector('[data-testid="stepper-back-header"]');
  expect(arrow).toBeNull();
});

it('renders the header back arrow on step 2 and step 3', async () => {
  await setup();
  const fixture = TestBed.createComponent(BookingStepperComponent);
  const component = fixture.componentInstance as any;
  fixture.detectChanges();

  component.currentStep.set(2);
  fixture.detectChanges();
  expect(fixture.nativeElement.querySelector('[data-testid="stepper-back-header"]')).not.toBeNull();

  component.currentStep.set(3);
  fixture.detectChanges();
  expect(fixture.nativeElement.querySelector('[data-testid="stepper-back-header"]')).not.toBeNull();
});

it('clicking the header back arrow decrements currentStep', async () => {
  await setup();
  const fixture = TestBed.createComponent(BookingStepperComponent);
  const component = fixture.componentInstance as any;
  fixture.detectChanges();
  component.currentStep.set(2);
  fixture.detectChanges();

  const arrow = fixture.nativeElement.querySelector('[data-testid="stepper-back-header"]') as HTMLElement;
  arrow.click();
  fixture.detectChanges();

  expect(component.currentStep()).toBe(1);
});

it('does not render the bottom Retour button on any step', async () => {
  await setup();
  const fixture = TestBed.createComponent(BookingStepperComponent);
  const component = fixture.componentInstance as any;
  fixture.detectChanges();

  component.currentStep.set(2);
  fixture.detectChanges();
  expect(fixture.nativeElement.querySelector('[data-testid="step-back-btn"]')).toBeNull();

  component.currentStep.set(3);
  fixture.detectChanges();
  expect(fixture.nativeElement.querySelector('[data-testid="step-back-btn"]')).toBeNull();
});
```

- [ ] **Step 2: Run tests — expect failure**

Run: `cd frontend && npx ng test --include='**/booking-stepper.component.spec.ts' --watch=false --browsers=ChromeHeadless`
Expected: at least the 4 new tests fail (existing 2 should still pass).

### 6b — Implement

- [ ] **Step 3: Read the current stepper file**

Run: `cat frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts`

Locate:
- The `.stepper-header` block in the template
- The `@if (currentStep() > 1)` bottom Back button (with `class="btn-back"`)
- The `.btn-back` / `.btn-close` CSS rules

- [ ] **Step 4: Modify the template — add header arrow**

In the template, find:

```html
<div class="stepper-header">
  <button class="btn-close" data-testid="stepper-close" (click)="dialogRef.close()">
    <mat-icon>close</mat-icon>
  </button>
  <span class="stepper-title">{{ 'booking.stepper.confirm' | transloco }}</span>
  <span class="step-counter">{{ currentStep() }}/3</span>
</div>
```

Replace with:

```html
<div class="stepper-header">
  @if (currentStep() > 1) {
    <button class="btn-header-back" data-testid="stepper-back-header" (click)="goBack()" type="button">
      <mat-icon>arrow_back</mat-icon>
    </button>
  }
  <button class="btn-close" data-testid="stepper-close" (click)="dialogRef.close()" type="button">
    <mat-icon>close</mat-icon>
  </button>
  <span class="stepper-title">{{ 'booking.stepper.confirm' | transloco }}</span>
  <span class="step-counter">{{ currentStep() }}/3</span>
</div>
```

- [ ] **Step 5: Modify the template — remove bottom Retour button**

Find and DELETE this block (located after the `.progress-bar` and `.step-content`):

```html
@if (currentStep() > 1) {
  <button class="btn-back" data-testid="step-back-btn" (click)="goBack()">
    <mat-icon>arrow_back</mat-icon>
    {{ 'booking.stepper.back' | transloco }}
  </button>
}
```

- [ ] **Step 6: Modify the styles — add `.btn-header-back`, remove `.btn-back`**

In the styles array, find the `.btn-close { ... }` rule. Add ABOVE it:

```css
.btn-header-back {
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  display: flex;
  color: #666;
}

.btn-header-back mat-icon {
  font-size: 20px;
  width: 20px;
  height: 20px;
}
```

Then find and DELETE the entire `.btn-back { ... }` rule block (and `.btn-back:hover`, `.btn-back mat-icon` if present).

- [ ] **Step 7: Run tests — expect all pass**

Run: `cd frontend && npx ng test --include='**/booking-stepper.component.spec.ts' --watch=false --browsers=ChromeHeadless`
Expected: 6 SUCCESS (2 existing + 4 new).

- [ ] **Step 8: Run the full Karma suite**

Run: `cd frontend && npx ng test --watch=false --browsers=ChromeHeadless`
Expected: ≥ 630 SUCCESS (was 630 + 4 new = 634).

If any other test referenced `[data-testid="step-back-btn"]` (the old bottom button selector), update it to use `[data-testid="stepper-back-header"]`.

- [ ] **Step 9: Commit**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts \
        frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.spec.ts
git commit -m "$(cat <<'EOF'
feat(booking-stepper): move Back from bottom button to header arrow

- Header now shows an arrow_back icon at left on steps 2-3, click goes
  back one step (same behavior as old bottom button).
- Bottom Retour button removed entirely.
- The X close button stays at right of the header on all steps.

UI is more compact and matches the fixed modal height introduced in the
previous redesign.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Verification — full suites + visual smoke

**Files:** none modified — verification only.

- [ ] **Step 1: Run the full backend suite**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS, total ≥ previous count + ~6 new tests (3 employee-service + 1 tenant-provisioning + 2 care-service).

- [ ] **Step 2: Run the full frontend Karma suite**

Run: `cd frontend && npx ng test --watch=false --browsers=ChromeHeadless`
Expected: TOTAL ≥ 634 SUCCESS (was 630 + 4 new stepper tests).

- [ ] **Step 3: Type-check the frontend**

Run: `cd frontend && ./node_modules/.bin/tsc --noEmit -p tsconfig.app.json`
Expected: no output (no errors).

- [ ] **Step 4: Verify the Flyway V4 migration locally**

Easiest: drop a tenant schema in dev DB, re-provision via `/api/auth/register/pro`, and confirm an employee row exists. If using H2 in tests, the suite above already covers it.

Alternatively, connect to the dev Oracle and run:

```sql
-- Pick a known legacy tenant slug
SELECT * FROM TENANT_SOPHIE.EMPLOYEES WHERE USER_ID = (
  SELECT OWNER_ID FROM APPUSER.TENANTS WHERE SLUG = 'sophie'
);
```

After applying V4 (boot the backend), the row should exist.

- [ ] **Step 5: Manual visual smoke**

In the browser (already running on http://localhost:4300 if frontend-dev is up):

1. Log in as a PRO.
2. Navigate to `/pro/bookings` → click "Ajouter une réservation".
3. Verify: at step 1, no header arrow at left, only X at right.
4. Pick a care → since the pro is solo (only pro-self), the employee section is hidden silently → Suivant.
5. At step 2: arrow visible top-left, clicking it goes back to step 1.
6. Pick date + time → Suivant.
7. At step 3: arrow still visible, clicking it goes back to step 2. No bottom Retour button anywhere.
8. Complete the booking. Open Network tab: `POST /api/pro/bookings` payload includes `employeeId: <pro-self.id>` (not null).

If all green: feature ready.

- [ ] **Step 6: Final acceptance**

No commit needed for this task. If any check fails, halt and follow `superpowers:systematic-debugging`.

---

## Recap

7 tasks, ~40 micro-steps, sequential. Backend dominates (Tasks 1-5), frontend is minimal (Task 6), verification closes (Task 7). Each backend task commits independently so rollback is granular. The Flyway migration (Task 5) is the highest-risk piece due to schema/slug nuances — verify the column names against V1 baseline before merging.

**Total estimated effort:** ~4-5 hours focused implementation + 30 min smoke.
