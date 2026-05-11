# Pro booking stepper — employee selection + stable modal height (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add employee selection to Step 1 of the pro booking stepper and stabilize the modal height at ≥768px, per spec `2026-05-11-pro-booking-stepper-redesign-design.md`.

**Architecture:** Backend exposes a `careId` query param on `GET /api/pro/employees` and propagates `employeeId` end-to-end (DTO + mapper + entity). Frontend updates the model + service, then refactors `StepCareComponent` to fetch employees after a care is chosen, with mono-employee silent auto-select and a pre-selected "Premier dispo" virtual entry when multiple are available. CSS media query `≥768px` on the stepper host fixes the modal height envelope; the per-step content area scrolls internally.

**Tech Stack:** Spring Boot 3.5 + JPA (backend), Angular 20 standalone + signals + Material Dialog + Karma (frontend), Transloco (i18n).

---

## File Structure

**Backend — modified:**
- `backend/src/main/java/com/luxpretty/app/employee/web/EmployeeController.java` — `list()` accepts optional `careId` query param
- `backend/src/main/java/com/luxpretty/app/bookings/web/dto/CareBookingRequest.java` — add `Long employeeId`
- `backend/src/main/java/com/luxpretty/app/bookings/web/mapper/CareBookingMapper.java` — write `employeeId` on entity in `updateEntity`

**Backend — tests (created or augmented):**
- `backend/src/test/java/com/luxpretty/app/bookings/web/CareBookingControllerTests.java` (extend if exists, create otherwise) — verifies `employeeId` round-trips through create

**Frontend — modified:**
- `frontend/src/app/features/bookings/models/bookings.model.ts` — add `employeeId?: number | null` on `CreateCareBookingRequest`
- `frontend/src/app/features/bookings/services/bookings.service.ts` — add `getEmployeesForCare(careId)` method
- `frontend/src/app/features/bookings/components/step-care/step-care.component.ts` — full refactor: employee mini-list, mono-employee auto-select, pre-selected "Premier dispo"
- `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts` — receive + forward `employeeId`, CSS media query for fixed envelope
- `frontend/src/assets/i18n/fr.json` — 3 new keys
- `frontend/src/assets/i18n/en.json` — 3 new keys

**Frontend — tests (modified or created):**
- `frontend/src/app/features/bookings/components/step-care/step-care.component.spec.ts` (create — doesn't exist yet)
- `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.spec.ts` (create — doesn't exist yet)

---

## Task 1: Backend — `careId` filter on `GET /api/pro/employees`

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/employee/web/EmployeeController.java`
- Test: `backend/src/test/java/com/luxpretty/app/employee/web/EmployeeControllerTests.java` (create if absent)

**Existing context:** `EmployeeService.listForCare(careId)` already exists and returns `List<EmployeeSlimResponse>`. Public endpoint `/api/salon/{slug}/employees?careId=…` already wires it for the client booking flow. The pro endpoint just needs the same plumbing.

- [ ] **Step 1: Read the existing `EmployeeControllerTests.java` if present, otherwise create it**

Run: `ls backend/src/test/java/com/luxpretty/app/employee/web/EmployeeControllerTests.java`
If it exists, read it to follow the test style (@WebMvcTest convention from CLAUDE.md). If not, create a new one with this shell:

```java
package com.luxpretty.app.employee.web;

import com.luxpretty.app.employee.app.EmployeeService;
import com.luxpretty.app.employee.web.dto.EmployeeSlimResponse;
import com.luxpretty.app.employee.web.dto.EmployeeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeController.class)
class EmployeeControllerTests {

    @Autowired MockMvc mvc;
    @MockBean EmployeeService service;

    @Test
    void list_withoutCareId_returnsAllEmployees() throws Exception {
        when(service.listAll()).thenReturn(List.of());
        mvc.perform(get("/api/pro/employees"))
           .andExpect(status().isOk());
        verify(service).listAll();
    }

    @Test
    void list_withCareId_filtersByCare() throws Exception {
        when(service.listForCare(42L))
            .thenReturn(List.of(new EmployeeSlimResponse(7L, "Alice", null)));
        mvc.perform(get("/api/pro/employees").param("careId", "42"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(7))
           .andExpect(jsonPath("$[0].name").value("Alice"));
        verify(service).listForCare(eq(42L));
    }
}
```

- [ ] **Step 2: Run tests to confirm `list_withCareId_filtersByCare` FAILS**

Run: `cd backend && mvn test -Dtest=EmployeeControllerTests`
Expected: `list_withCareId_filtersByCare` fails (the controller's `list()` ignores the param so `listAll` is called instead of `listForCare`).

- [ ] **Step 3: Modify `EmployeeController.list()` to accept optional `careId`**

Open `backend/src/main/java/com/luxpretty/app/employee/web/EmployeeController.java`. Replace the `list()` method and add the imports if missing.

```java
import com.luxpretty.app.employee.web.dto.EmployeeSlimResponse;
import org.springframework.web.bind.annotation.RequestParam;
// ... existing imports ...

@GetMapping
public Object list(@RequestParam(required = false) Long careId) {
    if (careId != null) {
        return service.listForCare(careId);
    }
    return service.listAll();
}
```

**Note on return type:** `Object` keeps backward compatibility (existing callers receive `List<EmployeeResponse>`; new `careId` callers receive `List<EmployeeSlimResponse>`). The two DTOs share `id` and `name`, which is all the frontend uses.

- [ ] **Step 4: Run tests to verify both pass**

Run: `cd backend && mvn test -Dtest=EmployeeControllerTests`
Expected: both tests green.

- [ ] **Step 5: Commit**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add backend/src/main/java/com/luxpretty/app/employee/web/EmployeeController.java backend/src/test/java/com/luxpretty/app/employee/web/EmployeeControllerTests.java
git commit -m "$(cat <<'EOF'
feat(employees): GET /api/pro/employees accepts optional careId filter

Backend now mirrors the public /api/salon/{slug}/employees?careId=… contract
for the pro side, so the booking stepper can list employees qualified for a
given care. EmployeeService.listForCare already existed; the controller just
delegates to it when careId is present.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Backend — propagate `employeeId` through `CareBookingRequest` + mapper

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/bookings/web/dto/CareBookingRequest.java`
- Modify: `backend/src/main/java/com/luxpretty/app/bookings/web/mapper/CareBookingMapper.java`
- Test: `backend/src/test/java/com/luxpretty/app/bookings/web/CareBookingControllerTests.java` (create or extend)

**Existing context:** The entity `CareBooking` already has a `private Long employeeId` field (cf. `bookings/domain/CareBooking.java:52`). The DTO `CareBookingRequest` and the `CareBookingMapper.updateEntity` simply ignore it today, so even when a frontend sends `employeeId`, the booking ends up with `employeeId = null`.

- [ ] **Step 1: Locate or create the controller test file**

Run: `ls backend/src/test/java/com/luxpretty/app/bookings/web/CareBookingControllerTests.java`

If absent, create a minimal `@WebMvcTest` skeleton (follow the same pattern as `EmployeeControllerTests` from Task 1). Inject `CareBookingService` as `@MockBean` and `MockMvc`.

- [ ] **Step 2: Add a failing test for `employeeId` round-trip**

Append this test (adjust the existing skeleton if a class exists). The point is: POST `/api/pro/bookings` with `employeeId: 17` must reach the entity. We assert by capturing the entity passed to `repo.save` via the service spy.

Actually a simpler integration: assert the DTO carries `employeeId` and the mapper writes it. Skip MVC layer here; do a direct mapper test in the same file or a new `CareBookingMapperTests.java`:

Create `backend/src/test/java/com/luxpretty/app/bookings/web/mapper/CareBookingMapperTests.java`:

```java
package com.luxpretty.app.bookings.web.mapper;

import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.bookings.web.dto.CareBookingRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class CareBookingMapperTests {

    @Test
    void updateEntity_writesEmployeeId_whenProvided() {
        CareBooking b = new CareBooking();
        CareBookingRequest req = new CareBookingRequest(
                1L, 2L, 1,
                LocalDate.of(2026, 6, 1),
                LocalTime.of(10, 0),
                CareBookingStatus.PENDING,
                null,
                17L
        );

        CareBookingMapper.updateEntity(b, req);

        assertThat(b.getEmployeeId()).isEqualTo(17L);
    }

    @Test
    void updateEntity_leavesEmployeeIdNull_whenRequestHasNone() {
        CareBooking b = new CareBooking();
        CareBookingRequest req = new CareBookingRequest(
                1L, 2L, 1,
                LocalDate.of(2026, 6, 1),
                LocalTime.of(10, 0),
                CareBookingStatus.PENDING,
                null,
                null
        );

        CareBookingMapper.updateEntity(b, req);

        assertThat(b.getEmployeeId()).isNull();
    }
}
```

This will not compile yet because `CareBookingRequest` has no 8th `employeeId` arg — that's the point.

- [ ] **Step 3: Run the test to confirm a compilation failure pinpointing the DTO**

Run: `cd backend && mvn test -Dtest=CareBookingMapperTests`
Expected: compilation error on `CareBookingRequest(... , 17L)` because the record has only 7 components.

- [ ] **Step 4: Add `employeeId` to `CareBookingRequest`**

Open `backend/src/main/java/com/luxpretty/app/bookings/web/dto/CareBookingRequest.java`. Add `Long employeeId` (nullable, no `@NotNull`) at the end:

```java
package com.luxpretty.app.bookings.web.dto;

import com.luxpretty.app.bookings.domain.CareBookingStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CareBookingRequest(
        @NotNull Long userId,
        @NotNull Long careId,
        @NotNull @Min(1) Integer quantity,
        @FutureOrPresent @NotNull LocalDate appointmentDate,
        @NotNull LocalTime appointmentTime,
        @NotNull CareBookingStatus status,
        Long salonClientId,
        Long employeeId  // null = unassigned (let the service / scheduling pick)
) {}
```

- [ ] **Step 5: Update `CareBookingMapper.updateEntity` to write `employeeId`**

Open `backend/src/main/java/com/luxpretty/app/bookings/web/mapper/CareBookingMapper.java`. Add one line in `updateEntity`:

```java
public static void updateEntity(CareBooking b, CareBookingRequest req) {
    b.setQuantity(req.quantity());
    b.setAppointmentDate(req.appointmentDate());
    b.setAppointmentTime(req.appointmentTime());
    b.setStatus(req.status());
    b.setEmployeeId(req.employeeId());
}
```

- [ ] **Step 6: Run the mapper tests to verify they pass**

Run: `cd backend && mvn test -Dtest=CareBookingMapperTests`
Expected: both tests green.

- [ ] **Step 7: Run the full backend test suite to catch collateral damage**

Run: `cd backend && mvn test`
Expected: all green. If a test elsewhere constructs `CareBookingRequest` positionally with 7 args, it will fail to compile — add `null` as the 8th arg.

If failures occur, fix by appending `null` (= no employee preference) to every positional `CareBookingRequest(...)` call in tests. Search:

```bash
grep -rn "new CareBookingRequest(" backend/src/test
```

- [ ] **Step 8: Commit**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add backend/src/main/java/com/luxpretty/app/bookings/web/dto/CareBookingRequest.java \
        backend/src/main/java/com/luxpretty/app/bookings/web/mapper/CareBookingMapper.java \
        backend/src/test/java/com/luxpretty/app/bookings/web/mapper/CareBookingMapperTests.java
# Also stage any test file fixes from Step 7 if needed:
git add -A backend/src/test
git commit -m "$(cat <<'EOF'
feat(bookings): propagate employeeId through CareBookingRequest + mapper

The CareBooking entity already had an employeeId column (nullable = unassigned)
but the pro create endpoint silently dropped it because the DTO and mapper
ignored it. Add the field to CareBookingRequest as a nullable Long, write it
in CareBookingMapper.updateEntity, and pin the behaviour with a mapper test.

Unblocks the pro booking stepper which now wants to designate an employee
when a care has multiple qualified staff.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Frontend — add `employeeId` to `CreateCareBookingRequest`

**Files:**
- Modify: `frontend/src/app/features/bookings/models/bookings.model.ts`

This is a tiny structural change to unblock Tasks 4-6. No test added on its own (it's just a type addition).

- [ ] **Step 1: Add `employeeId?: number | null` to `CreateCareBookingRequest`**

Open `frontend/src/app/features/bookings/models/bookings.model.ts`. Modify the interface:

```ts
export interface CreateCareBookingRequest {
  userId: number;
  careId: number;
  quantity: number;
  appointmentDate: string; // ISO date string (YYYY-MM-DD)
  appointmentTime: string; // ISO time string (HH:mm:ss)
  status: CareBookingStatus;
  salonClientId?: number;
  employeeId?: number | null; // null = unassigned ("Premier dispo")
}
```

- [ ] **Step 2: Run TypeScript compiler to verify no type errors elsewhere**

Run: `cd frontend && npx tsc --noEmit -p tsconfig.app.json`
Expected: no errors. (Adding an optional field is backward-compatible.)

- [ ] **Step 3: Commit**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add frontend/src/app/features/bookings/models/bookings.model.ts
git commit -m "$(cat <<'EOF'
feat(bookings): add optional employeeId to CreateCareBookingRequest

Mirrors the backend DTO addition. null/undefined = "let the system pick".

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Frontend — `BookingsService.getEmployeesForCare(careId)`

**Files:**
- Modify: `frontend/src/app/features/bookings/services/bookings.service.ts`

**Existing context:** `EmployeeSlim` is already defined in `features/salon-profile/models/salon-profile.model.ts:89`. Reuse it (no new type).

- [ ] **Step 1: Read the current `bookings.service.ts` to see its imports and base URL pattern**

Run: `cat frontend/src/app/features/bookings/services/bookings.service.ts | head -30`

Note the imports and the `apiBaseUrl` / `http` injection pattern used by existing methods.

- [ ] **Step 2: Add the new method**

Open `frontend/src/app/features/bookings/services/bookings.service.ts`. Add this import near the top:

```ts
import { EmployeeSlim } from '../../salon-profile/models/salon-profile.model';
```

Add this method inside the `BookingsService` class (placement: next to the other GETs):

```ts
getEmployeesForCare(careId: number): Observable<EmployeeSlim[]> {
  return this.http.get<EmployeeSlim[]>(
    `${this.baseUrl}/api/pro/employees`,
    { params: { careId: careId.toString() } }
  );
}
```

If the class uses `private baseUrl` differently, match the existing style — verify with the file content.

- [ ] **Step 3: Run TypeScript compiler**

Run: `cd frontend && npx tsc --noEmit -p tsconfig.app.json`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add frontend/src/app/features/bookings/services/bookings.service.ts
git commit -m "$(cat <<'EOF'
feat(bookings-service): getEmployeesForCare(careId)

Calls GET /api/pro/employees?careId=… which now filters by care.
Returns EmployeeSlim[] (id, name, imageUrl) — same shape the client-side
booking dialog already consumes from the public endpoint.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Frontend — i18n keys

**Files:**
- Modify: `frontend/src/assets/i18n/fr.json`
- Modify: `frontend/src/assets/i18n/en.json`

- [ ] **Step 1: Locate the existing `booking.stepper.*` keys in both files**

Run: `grep -n "stepper" frontend/src/assets/i18n/fr.json | head` and `grep -n "stepper" frontend/src/assets/i18n/en.json | head`.

Confirm the `booking.stepper` object exists. We'll add `step1Employee` to it, and a new `booking.employees` sibling object.

- [ ] **Step 2: Add 3 keys to `fr.json`**

In `frontend/src/assets/i18n/fr.json`, inside the `booking.stepper` object, add (next to `step1`, `step2`, `step3`):

```json
"step1Employee": "Avec qui ?"
```

Then inside the `booking` object (sibling of `stepper`), add a new `employees` block:

```json
"employees": {
  "anyAvailable": "Premier employé disponible",
  "empty": "Aucun employé disponible pour ce soin"
}
```

- [ ] **Step 3: Mirror the same keys in `en.json`**

```json
"step1Employee": "With whom?"
```

```json
"employees": {
  "anyAvailable": "First available employee",
  "empty": "No employee available for this care"
}
```

- [ ] **Step 4: Validate both JSON files parse**

Run: `cd frontend && node -e "JSON.parse(require('fs').readFileSync('src/assets/i18n/fr.json'))" && node -e "JSON.parse(require('fs').readFileSync('src/assets/i18n/en.json'))"`
Expected: no output (success). Any parse error → fix the trailing comma / missing brace.

- [ ] **Step 5: Commit**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add frontend/src/assets/i18n/fr.json frontend/src/assets/i18n/en.json
git commit -m "$(cat <<'EOF'
feat(i18n): booking stepper — employee selection keys (fr + en)

- booking.stepper.step1Employee — "Avec qui ?" / "With whom?"
- booking.employees.anyAvailable — "Premier employé disponible" / "First available employee"
- booking.employees.empty — "Aucun employé disponible pour ce soin" / "No employee available for this care"

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Frontend — refactor `StepCareComponent` (with tests)

**Files:**
- Modify: `frontend/src/app/features/bookings/components/step-care/step-care.component.ts`
- Test: `frontend/src/app/features/bookings/components/step-care/step-care.component.spec.ts` (create)

**TDD approach:** write the spec first, watch it fail, then implement.

### 6a — Write the failing spec

- [ ] **Step 1: Create `step-care.component.spec.ts`**

Path: `frontend/src/app/features/bookings/components/step-care/step-care.component.spec.ts`

```ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';

import { StepCareComponent } from './step-care.component';
import { BookingsService } from '../../services/bookings.service';
import { CaresStore } from '../../../cares/store/cares.store';
import { CareStatus } from '../../../cares/models/cares.model';
import { EmployeeSlim } from '../../../salon-profile/models/salon-profile.model';

function care(id: number, name = 'Soin') {
  return {
    id,
    name,
    description: '',
    price: 5000,
    duration: 60,
    images: [],
    status: CareStatus.ACTIVE,
    categoryId: 1,
  };
}

function emp(id: number, name = 'Alice'): EmployeeSlim {
  return { id, name, imageUrl: null };
}

describe('StepCareComponent', () => {
  let bookingsService: jasmine.SpyObj<BookingsService>;
  let caresStoreStub: any;

  function setup() {
    bookingsService = jasmine.createSpyObj<BookingsService>('BookingsService', [
      'getEmployeesForCare',
    ]);
    caresStoreStub = {
      availableCares: jasmine.createSpy().and.returnValue([care(1), care(2)]),
    };

    return TestBed.configureTestingModule({
      imports: [
        StepCareComponent,
        TranslocoTestingModule.forRoot({
          langs: { fr: {} },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: BookingsService, useValue: bookingsService },
        { provide: CaresStore, useValue: caresStoreStub },
      ],
    }).compileComponents();
  }

  it('does not fetch employees until a care is selected', async () => {
    await setup();
    const fixture = TestBed.createComponent(StepCareComponent);
    fixture.detectChanges();
    expect(bookingsService.getEmployeesForCare).not.toHaveBeenCalled();
  });

  it('fetches employees after selecting a care and pre-selects "Premier dispo" when >1 employees', async () => {
    await setup();
    bookingsService.getEmployeesForCare.and.returnValue(of([emp(7), emp(8)]));
    const fixture = TestBed.createComponent(StepCareComponent);
    fixture.detectChanges();

    const component = fixture.componentInstance as any;
    component.selectCare(1);
    fixture.detectChanges();

    expect(bookingsService.getEmployeesForCare).toHaveBeenCalledWith(1);
    expect(component.availableEmployees()).toEqual([emp(7), emp(8)]);
    expect(component.selectedEmployeeId()).toBeNull(); // "Premier dispo"
  });

  it('auto-selects and hides the employee list when exactly 1 employee is returned', async () => {
    await setup();
    bookingsService.getEmployeesForCare.and.returnValue(of([emp(9)]));
    const fixture = TestBed.createComponent(StepCareComponent);
    fixture.detectChanges();

    const component = fixture.componentInstance as any;
    component.selectCare(1);
    fixture.detectChanges();

    expect(component.availableEmployees()).toEqual([emp(9)]);
    expect(component.selectedEmployeeId()).toBe(9);
    expect(component.shouldShowEmployeeList()).toBeFalse();
  });

  it('emits { careId, employeeId: null } when "Premier dispo" is active', async () => {
    await setup();
    bookingsService.getEmployeesForCare.and.returnValue(of([emp(7), emp(8)]));
    const fixture = TestBed.createComponent(StepCareComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    let emitted: any = null;
    component.careSelected.subscribe((e: any) => (emitted = e));

    component.selectCare(1);
    fixture.detectChanges();
    component.onNext();

    expect(emitted).toEqual({ careId: 1, employeeId: null });
  });

  it('emits { careId, employeeId: 8 } when the user picks employee 8', async () => {
    await setup();
    bookingsService.getEmployeesForCare.and.returnValue(of([emp(7), emp(8)]));
    const fixture = TestBed.createComponent(StepCareComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    let emitted: any = null;
    component.careSelected.subscribe((e: any) => (emitted = e));

    component.selectCare(1);
    fixture.detectChanges();
    component.selectEmployee(8);
    component.onNext();

    expect(emitted).toEqual({ careId: 1, employeeId: 8 });
  });

  it('resets employee selection when the care changes', async () => {
    await setup();
    bookingsService.getEmployeesForCare.and.returnValues(
      of([emp(7), emp(8)]),
      of([emp(9), emp(10)])
    );
    const fixture = TestBed.createComponent(StepCareComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;

    component.selectCare(1);
    fixture.detectChanges();
    component.selectEmployee(8);
    component.selectCare(2);
    fixture.detectChanges();

    expect(component.selectedEmployeeId()).toBeNull(); // back to "Premier dispo"
    expect(component.availableEmployees()).toEqual([emp(9), emp(10)]);
  });

  it('shows the empty message when no employees can do the care', async () => {
    await setup();
    bookingsService.getEmployeesForCare.and.returnValue(of([]));
    const fixture = TestBed.createComponent(StepCareComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;

    component.selectCare(1);
    fixture.detectChanges();

    expect(component.availableEmployees()).toEqual([]);
    expect(component.shouldShowEmployeeList()).toBeTrue();
    expect(component.shouldShowEmptyState()).toBeTrue();
  });
});
```

- [ ] **Step 2: Run the new spec — confirm it fails**

Run: `cd frontend && npx ng test --include='**/step-care.component.spec.ts' --watch=false --browsers=ChromeHeadless`
Expected: multiple failures because the current `StepCareComponent` has no `availableEmployees`, `selectedEmployeeId`, `selectEmployee`, `shouldShowEmployeeList`, `shouldShowEmptyState` symbols. That's the test pinning down the API we need.

### 6b — Implement the refactor

- [ ] **Step 3: Replace `step-care.component.ts` with the refactored version**

Open `frontend/src/app/features/bookings/components/step-care/step-care.component.ts` and replace the **entire file** with:

```ts
import { Component, computed, effect, inject, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatIconModule } from '@angular/material/icon';
import { CaresStore } from '../../../cares/store/cares.store';
import { BookingsService } from '../../services/bookings.service';
import { EmployeeSlim } from '../../../salon-profile/models/salon-profile.model';

@Component({
  selector: 'app-step-care',
  standalone: true,
  imports: [CommonModule, TranslocoPipe, MatIconModule],
  template: `
    <div class="step-care">
      <h3>{{ 'booking.stepper.step1' | transloco }}</h3>

      <div class="care-list">
        @for (care of caresStore.availableCares(); track care.id) {
          <div
            class="care-card"
            data-testid="step-care-item"
            [class.selected]="selectedCareId() === care.id"
            (click)="selectCare(care.id)"
          >
            <div class="care-name">{{ care.name }}</div>
            <div class="care-details">
              <span>{{ care.price }}&euro;</span>
              <span>{{ care.duration }} min</span>
            </div>
          </div>
        }
      </div>

      @if (selectedCareId() && shouldShowEmployeeList()) {
        <div class="employee-section">
          <h4>{{ 'booking.stepper.step1Employee' | transloco }}</h4>

          @if (shouldShowEmptyState()) {
            <div class="employee-empty">
              {{ 'booking.employees.empty' | transloco }}
            </div>
          } @else {
            <div class="employee-list">
              <!-- "Premier dispo" virtual card -->
              <div
                class="employee-card"
                data-testid="step-employee-any"
                [class.selected]="selectedEmployeeId() === null"
                (click)="selectEmployee(null)"
              >
                <div class="emp-avatar emp-avatar-any">
                  <mat-icon>groups</mat-icon>
                </div>
                <div class="emp-info">
                  <div class="emp-name">
                    {{ 'booking.employees.anyAvailable' | transloco }}
                  </div>
                </div>
              </div>

              @for (e of availableEmployees(); track e.id) {
                <div
                  class="employee-card"
                  data-testid="step-employee-item"
                  [class.selected]="selectedEmployeeId() === e.id"
                  (click)="selectEmployee(e.id)"
                >
                  <div class="emp-avatar">
                    @if (e.imageUrl) {
                      <img [src]="e.imageUrl" [alt]="e.name" />
                    } @else {
                      <span>{{ initials(e.name) }}</span>
                    }
                  </div>
                  <div class="emp-info">
                    <div class="emp-name">{{ e.name }}</div>
                  </div>
                </div>
              }
            </div>
          }
        </div>
      }

      <button
        class="btn-next"
        data-testid="step-next-btn"
        [disabled]="!canProceed()"
        (click)="onNext()"
      >
        {{ 'booking.stepper.next' | transloco }}
      </button>
    </div>
  `,
  styles: [
    `
      .step-care {
        display: flex;
        flex-direction: column;
        gap: 12px;
      }

      h3 {
        margin: 0;
        font-size: 15px;
        font-weight: 600;
        color: #333;
      }

      h4 {
        margin: 4px 0 0;
        font-size: 13px;
        font-weight: 600;
        color: #555;
      }

      .care-list {
        display: flex;
        flex-direction: column;
        gap: 8px;
        max-height: 240px;
        overflow-y: auto;
      }

      .care-card {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 10px 14px;
        border: 1.5px solid #e0e0e0;
        border-radius: 10px;
        background: white;
        cursor: pointer;
        transition: border-color 200ms ease, background 200ms ease, box-shadow 200ms ease;
      }

      .care-card:hover {
        border-color: #f0a0c0;
        box-shadow: 0 2px 8px rgba(204, 0, 102, 0.08);
      }

      .care-card.selected {
        border-color: var(--pf-rose);
        background: #fff5f7;
      }

      .care-name {
        font-weight: 500;
        font-size: 14px;
        color: #333;
      }

      .care-details {
        display: flex;
        gap: 10px;
        font-size: 12px;
        color: #888;
      }

      .employee-section {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }

      .employee-list {
        display: flex;
        flex-direction: column;
        gap: 6px;
        max-height: 200px;
        overflow-y: auto;
      }

      .employee-card {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 8px 10px;
        border: 1.5px solid #e0e0e0;
        border-radius: 10px;
        background: white;
        cursor: pointer;
        transition: border-color 200ms ease, background 200ms ease;
      }

      .employee-card:hover {
        border-color: #f0a0c0;
      }

      .employee-card.selected {
        border-color: var(--pf-rose);
        background: #fff5f7;
      }

      .emp-avatar {
        width: 32px;
        height: 32px;
        border-radius: 50%;
        background: #f1d5dc;
        color: var(--pf-rose);
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: 600;
        font-size: 12px;
        flex-shrink: 0;
        overflow: hidden;
      }

      .emp-avatar img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      .emp-avatar-any mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }

      .emp-info {
        display: flex;
        flex-direction: column;
      }

      .emp-name {
        font-size: 13px;
        font-weight: 500;
        color: #333;
      }

      .employee-empty {
        padding: 12px;
        background: #fef2f2;
        border-radius: 8px;
        color: #c0392b;
        font-size: 13px;
        text-align: center;
      }

      .btn-next {
        align-self: stretch;
        background: var(--pf-rose);
        color: white;
        border: none;
        border-radius: 8px;
        padding: 10px 24px;
        font-size: 13px;
        font-weight: 500;
        cursor: pointer;
        transition: background 200ms ease;
        margin-top: 4px;
      }

      .btn-next:hover:not(:disabled) {
        background: #a00554;
      }

      .btn-next:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    `,
  ],
})
export class StepCareComponent {
  readonly caresStore = inject(CaresStore);
  private readonly bookingsService = inject(BookingsService);

  readonly selectedCareId = signal<number | null>(null);
  readonly availableEmployees = signal<EmployeeSlim[]>([]);
  readonly selectedEmployeeId = signal<number | null>(null);
  readonly loadingEmployees = signal(false);

  readonly careSelected = output<{ careId: number; employeeId: number | null }>();

  // ── derived UI gates ─────────────────────────────────────────────────
  readonly shouldShowEmployeeList = computed(() => {
    const list = this.availableEmployees();
    // Hide entirely when exactly one employee is available (silent auto-select).
    return list.length !== 1;
  });

  readonly shouldShowEmptyState = computed(() => this.availableEmployees().length === 0);

  readonly canProceed = computed(() => {
    if (!this.selectedCareId()) return false;
    if (this.loadingEmployees()) return false;
    if (this.shouldShowEmptyState()) return false;
    return true;
  });

  constructor() {
    effect(() => {
      const careId = this.selectedCareId();
      if (careId === null) return;
      this.loadingEmployees.set(true);
      this.bookingsService.getEmployeesForCare(careId).subscribe({
        next: (list) => {
          this.availableEmployees.set(list);
          // Mono-employee → auto-select silently; otherwise default to "Premier dispo" (null).
          if (list.length === 1) {
            this.selectedEmployeeId.set(list[0].id);
          } else {
            this.selectedEmployeeId.set(null);
          }
          this.loadingEmployees.set(false);
        },
        error: () => {
          this.availableEmployees.set([]);
          this.selectedEmployeeId.set(null);
          this.loadingEmployees.set(false);
        },
      });
    });
  }

  selectCare(id: number): void {
    this.selectedCareId.set(id);
  }

  selectEmployee(id: number | null): void {
    this.selectedEmployeeId.set(id);
  }

  initials(name: string): string {
    return name
      .split(' ')
      .map((n) => n[0])
      .filter(Boolean)
      .join('')
      .slice(0, 2)
      .toUpperCase();
  }

  onNext(): void {
    const careId = this.selectedCareId();
    if (careId === null) return;
    if (!this.canProceed()) return;
    this.careSelected.emit({ careId, employeeId: this.selectedEmployeeId() });
  }
}
```

- [ ] **Step 4: Run the spec — all 7 tests pass**

Run: `cd frontend && npx ng test --include='**/step-care.component.spec.ts' --watch=false --browsers=ChromeHeadless`
Expected: 7 SUCCESS, 0 FAILED.

If a test fails, read the assertion, compare with the implementation, fix the implementation (not the test). The test names define the spec — don't water them down.

- [ ] **Step 5: Commit**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add frontend/src/app/features/bookings/components/step-care/step-care.component.ts \
        frontend/src/app/features/bookings/components/step-care/step-care.component.spec.ts
git commit -m "$(cat <<'EOF'
feat(booking-stepper): step-care fetches and selects an employee

After a care is chosen, fetch GET /api/pro/employees?careId=… and let
the pro pick an employee. UI rules:
- Mono-employee → section hidden, employee pre-selected silently
- >1 employees → vertical mini-cards with "Premier dispo" (null) on top, pre-selected
- 0 employees → empty-state message, "Suivant" disabled

Emits { careId, employeeId: number | null }. The Step 1 employeeId=0
sentinel is gone.

Spec: docs/superpowers/specs/2026-05-11-pro-booking-stepper-redesign-design.md

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Frontend — wire `employeeId` in the stepper + fix modal height

**Files:**
- Modify: `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts`
- Test: `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.spec.ts` (create)

### 7a — Spec for the stepper

- [ ] **Step 1: Create `booking-stepper.component.spec.ts`**

Path: `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.spec.ts`

```ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';

import { BookingStepperComponent } from './booking-stepper.component';
import { BookingsService } from '../../services/bookings.service';
import { AuthService } from '../../../../core/auth/auth.service';
import { Role, AuthProvider } from '../../../../core/auth/auth.model';

describe('BookingStepperComponent', () => {
  let dialogRef: jasmine.SpyObj<MatDialogRef<BookingStepperComponent>>;
  let bookingsService: jasmine.SpyObj<BookingsService>;
  let authService: jasmine.SpyObj<AuthService>;

  function setup() {
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    bookingsService = jasmine.createSpyObj<BookingsService>('BookingsService', [
      'create',
      'getEmployeesForCare',
    ]);
    bookingsService.create.and.returnValue(of({} as any));
    bookingsService.getEmployeesForCare.and.returnValue(of([]));

    const fakeUser = {
      id: 99,
      name: 'Pro',
      email: 'pro@x.test',
      provider: AuthProvider.LOCAL,
      role: Role.PRO,
    };
    authService = jasmine.createSpyObj('AuthService', [], {
      user: () => fakeUser,
    });

    return TestBed.configureTestingModule({
      imports: [
        BookingStepperComponent,
        TranslocoTestingModule.forRoot({
          langs: { fr: {} },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: BookingsService, useValue: bookingsService },
        { provide: AuthService, useValue: authService },
      ],
    }).compileComponents();
  }

  it('forwards employeeId from step-care to bookingsService.create', async () => {
    await setup();
    const fixture = TestBed.createComponent(BookingStepperComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();

    component.onCareSelected({ careId: 1, employeeId: 42 });
    component.onDatetimeSelected({ date: '2026-06-01', time: '10:00' });
    component.onClientSelected({ salonClientId: 5 });

    expect(bookingsService.create).toHaveBeenCalledTimes(1);
    const payload = bookingsService.create.calls.mostRecent().args[0];
    expect(payload.employeeId).toBe(42);
  });

  it('forwards employeeId = null (Premier dispo) untouched', async () => {
    await setup();
    const fixture = TestBed.createComponent(BookingStepperComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();

    component.onCareSelected({ careId: 1, employeeId: null });
    component.onDatetimeSelected({ date: '2026-06-01', time: '10:00' });
    component.onClientSelected({ salonClientId: 5 });

    expect(bookingsService.create).toHaveBeenCalledTimes(1);
    const payload = bookingsService.create.calls.mostRecent().args[0];
    expect(payload.employeeId).toBeNull();
  });
});
```

- [ ] **Step 2: Run the spec — confirm it fails**

Run: `cd frontend && npx ng test --include='**/booking-stepper.component.spec.ts' --watch=false --browsers=ChromeHeadless`
Expected: tests fail because (a) `onCareSelected` doesn't store `employeeId` from the new event shape, and (b) `confirmBooking` doesn't include `employeeId` in the payload.

### 7b — Implement the fix

- [ ] **Step 3: Update `booking-stepper.component.ts`**

Open `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts`.

**Change 1 — `onCareSelected` signature + body** (around line 173):

```ts
onCareSelected(event: { careId: number; employeeId: number | null }): void {
  this.selectedCareId.set(event.careId);
  this.selectedEmployeeId.set(event.employeeId);
  this.currentStep.set(2);
}
```

**Change 2 — `selectedEmployeeId` signal type** (around line 165): the field exists but is typed `number | null`; verify the type and replace if necessary:

```ts
readonly selectedEmployeeId = signal<number | null>(null);
```

**Change 3 — `confirmBooking()`** (around line 196): include `employeeId` in the payload, prefer `?? undefined` so JSON omits `null` when "Premier dispo":

```ts
private confirmBooking(): void {
  const careId = this.selectedCareId();
  const appointmentDate = this.selectedDate();
  const appointmentTime = this.selectedTime();

  if (careId == null || appointmentDate == null || appointmentTime == null || this.submitting()) {
    return;
  }

  this.submitting.set(true);

  const userId = this.authService.user()?.id;
  if (!userId) return;

  this.bookingsService.create({
    careId,
    userId,
    quantity: 1,
    appointmentDate,
    appointmentTime: appointmentTime + ':00',
    status: CareBookingStatus.PENDING,
    salonClientId: this.selectedSalonClientId() ?? undefined,
    employeeId: this.selectedEmployeeId(),
  }).subscribe({
    next: () => this.dialogRef.close(true),
    error: () => {
      this.submitting.set(false);
    },
  });
}
```

**Change 4 — CSS in the `styles: [` block**: replace the existing `:host` rule and add a media query.

Find:

```ts
:host {
  display: flex;
  flex-direction: column;
  background: var(--pf-paper);
  overflow-y: auto;
  max-height: 80vh;
}
```

Replace with:

```ts
:host {
  display: flex;
  flex-direction: column;
  background: var(--pf-paper);
  overflow-y: auto;
  max-height: 80vh;
}

@media (min-width: 768px) {
  :host {
    width: 480px;
    min-height: 560px;
    max-height: 85vh;
    overflow: hidden;
  }
  .step-content {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
  }
}
```

(The existing `.step-content` rule already has `flex: 1; padding: 16px; overflow-y: auto;` — the media-query block reinforces what we need at PC width without breaking mobile.)

- [ ] **Step 4: Run the stepper spec — both tests pass**

Run: `cd frontend && npx ng test --include='**/booking-stepper.component.spec.ts' --watch=false --browsers=ChromeHeadless`
Expected: 2 SUCCESS.

- [ ] **Step 5: Commit**

```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts \
        frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.spec.ts
git commit -m "$(cat <<'EOF'
feat(booking-stepper): forward employeeId + fix modal height ≥768px

- onCareSelected now accepts { careId, employeeId: number | null }
  and feeds the existing selectedEmployeeId signal.
- confirmBooking includes employeeId in the create payload.
- Modal envelope ≥768px: width 480px, min-height 560px, max-height 85vh,
  overflow hidden on :host so step transitions don't resize the dialog.
  .step-content scrolls internally. Mobile <768px unchanged.

Spec: docs/superpowers/specs/2026-05-11-pro-booking-stepper-redesign-design.md

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Frontend — full suite + visual smoke

**Files:** none modified — verification only.

- [ ] **Step 1: Run the full Karma suite**

Run: `cd frontend && npx ng test --watch=false --browsers=ChromeHeadless`
Expected: TOTAL ≥622 SUCCESS, 0 FAILED. The new step-care (7) and booking-stepper (2) tests bring it to ~631.

If any pre-existing test breaks, it's likely a positional `CareBookingRequest` or `CreateCareBookingRequest` constructor in a spec that now needs an `employeeId` field. Add it (`employeeId: null` for the default case) and re-run.

- [ ] **Step 2: Type-check the whole frontend**

Run: `cd frontend && npx tsc --noEmit -p tsconfig.app.json`
Expected: no errors.

- [ ] **Step 3: Backend full suite**

Run: `cd backend && mvn test`
Expected: all green.

- [ ] **Step 4: Manual visual smoke (PC)**

1. Start backend: `cd backend && mvn spring-boot:run` (if not already running locally — per CLAUDE.md).
2. Start frontend dev container: `docker compose --profile dev up -d --force-recreate frontend-dev`. Wait ~15s.
3. Open Chrome ≥1024px wide → log in as PRO → navigate to `/pro/bookings` → click "Ajouter une réservation".
4. Verify:
   - Modal opens at exactly 480px wide, hovers between min-height 560 and max-height 85vh.
   - Step 1 (Care) renders with the care list. After clicking a care:
     - If 1 employee in the salon → the "Avec qui ?" section does NOT appear.
     - If >1 employees → "Premier dispo" is highlighted in rose at the top of the list.
   - Click "Suivant" → modal stays the same size (no resize jolt).
   - Step 2 (datetime) and Step 3 (client) — modal box size is constant; only the inner area scrolls if content overflows.
5. Resize Chrome to <768px → the modal switches back to bottom-sheet (unchanged behaviour).
6. Complete a full flow: pick care, pick a named employee, pick date/time, pick a client, confirm. Open Network tab → the `POST /api/pro/bookings` payload contains `employeeId: <int>`. Open `/pro/bookings` after creation and verify the new booking is associated with that employee (check the booking card's `employeeName` field).

- [ ] **Step 5: Manual visual smoke (mobile)**

Resize to <768px (or use DevTools mobile preset). Re-open the stepper from `/pro/bookings`. Verify:
- Bottom-sheet renders full-width, full-height (as before).
- The "Avec qui ?" employee selection appears inline inside the sheet.
- No CSS regressions (sheet-handle, header, progress bar, back button visible).

- [ ] **Step 6: Final acceptance**

If all above pass: feature ready. No commit needed (verification only).

If any test or visual step fails: stop, investigate, fix at root cause (per `superpowers:systematic-debugging` discipline), and re-run from the failing step.

---

## Recap

8 tasks, ~30-35 micro-steps, fully sequential. Backend first (Tasks 1-2), frontend wiring (Tasks 3-5), then the meaty refactor (Task 6), then the stepper wiring (Task 7), then full verification (Task 8). Each task ends with its own commit so the history reads cleanly and rollback is trivial.

**Total estimated effort:** 4-5 hours of focused implementation + ~30 min visual smoke.
