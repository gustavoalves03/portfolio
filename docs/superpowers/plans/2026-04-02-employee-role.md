# Employee Role Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an EMPLOYEE role so PROs can manage staff, assign cares, handle leave/sickness, and clients can optionally choose a collaborator when booking.

**Architecture:** New `Employee` entity in tenant schema links a `User` (public schema) to a tenant with assigned cares and active/inactive status. The PRO is also an employee (togglable). Leave requests and sickness declarations are tracked per employee with approval workflow. The slot availability engine is extended to compute per-employee availability. A document storage feature lets employees and PROs upload/view contracts, payslips, and medical certificates.

**Tech Stack:** Spring Boot 3.5 (Java 21), Angular 20 (standalone, signals, NgRx SignalStore), Oracle/H2 multi-tenant schemas, Material 3, Transloco i18n.

---

## File Structure

### Backend — New Files

| File | Responsibility |
|------|---------------|
| `backend/.../employee/domain/Employee.java` | JPA entity — links User to tenant, active flag, assigned cares |
| `backend/.../employee/domain/LeaveRequest.java` | JPA entity — leave/sickness with status, dates, type, document path |
| `backend/.../employee/domain/LeaveType.java` | Enum: `VACATION`, `SICKNESS` |
| `backend/.../employee/domain/LeaveStatus.java` | Enum: `PENDING`, `APPROVED`, `REJECTED` |
| `backend/.../employee/domain/EmployeeDocument.java` | JPA entity — document metadata (contract, payslip, medical cert) |
| `backend/.../employee/domain/DocumentType.java` | Enum: `CONTRACT`, `PAYSLIP`, `MEDICAL_CERTIFICATE`, `OTHER` |
| `backend/.../employee/repo/EmployeeRepository.java` | Spring Data repo |
| `backend/.../employee/repo/LeaveRequestRepository.java` | Spring Data repo |
| `backend/.../employee/repo/EmployeeDocumentRepository.java` | Spring Data repo |
| `backend/.../employee/app/EmployeeService.java` | CRUD + care assignment + toggle active |
| `backend/.../employee/app/LeaveRequestService.java` | Create/approve/reject leaves |
| `backend/.../employee/app/EmployeeDocumentService.java` | Upload/list/download documents |
| `backend/.../employee/web/EmployeeController.java` | PRO endpoints `/api/pro/employees` |
| `backend/.../employee/web/EmployeeLeaveController.java` | PRO endpoints `/api/pro/employees/{id}/leaves` |
| `backend/.../employee/web/EmployeeDocumentController.java` | PRO endpoints `/api/pro/employees/{id}/documents` |
| `backend/.../employee/web/MyEmployeeController.java` | EMPLOYEE self-service `/api/employee/me` |
| `backend/.../employee/web/dto/CreateEmployeeRequest.java` | DTO |
| `backend/.../employee/web/dto/UpdateEmployeeRequest.java` | DTO |
| `backend/.../employee/web/dto/EmployeeResponse.java` | DTO |
| `backend/.../employee/web/dto/LeaveRequestDto.java` | DTO for create |
| `backend/.../employee/web/dto/LeaveResponse.java` | DTO |
| `backend/.../employee/web/dto/DocumentResponse.java` | DTO |
| `backend/.../employee/web/dto/EmployeeSlimResponse.java` | Minimal DTO for client booking (id, name, imageUrl) |

### Backend — Modified Files

| File | Change |
|------|--------|
| `users/domain/Role.java` | Add `EMPLOYEE` enum value |
| `multitenancy/TenantSchemaManager.java` | Add `EMPLOYEES`, `LEAVE_REQUESTS`, `EMPLOYEE_DOCUMENTS` tables + `EMPLOYEE_CARES` join table to tenant DDL |
| `availability/app/SlotAvailabilityService.java` | Add `getAvailableSlots(date, careId, employeeId)` overload; factor out per-employee logic |
| `availability/domain/BlockedSlot.java` | Add optional `employeeId` column |
| `availability/domain/OpeningHour.java` | Add optional `employeeId` column |
| `availability/repo/BlockedSlotRepository.java` | Add `findByEmployeeIdAndDate...` query |
| `availability/repo/OpeningHourRepository.java` | Add `findByEmployeeId...` query |
| `bookings/domain/CareBooking.java` | Add optional `employeeId` column |
| `bookings/app/CareBookingService.java` | Pass employeeId through booking flow; auto-assign if not specified |
| `bookings/web/dto/ClientBookingRequest.java` | Add optional `employeeId` field |
| `bookings/web/dto/CareBookingDetailedResponse.java` | Add employee name |
| `tenant/web/PublicSalonController.java` | Add endpoint to list employees for a care |
| `config/SecurityConfig.java` | Allow EMPLOYEE role on `/api/employee/**` paths |
| `multitenancy/TenantFilter.java` | Resolve tenant for EMPLOYEE role users (lookup via Employee table) |

### Frontend — New Files

| File | Responsibility |
|------|---------------|
| `features/employees/employees.model.ts` | TypeScript interfaces |
| `features/employees/employees.service.ts` | HTTP service |
| `features/employees/employees.store.ts` | NgRx SignalStore |
| `features/employees/employees.component.ts/html/scss` | PRO employee list page |
| `features/employees/modals/create-employee/` | Create employee modal |
| `features/employees/modals/employee-detail/` | Detail/edit modal (cares assignment, toggle active) |
| `features/leaves/leaves.model.ts` | TypeScript interfaces |
| `features/leaves/leaves.service.ts` | HTTP service |
| `features/leaves/leaves.store.ts` | NgRx SignalStore |
| `features/leaves/leaves.component.ts/html/scss` | PRO leave management tab |
| `features/employee-documents/` | Document upload/list feature |
| `pages/pro/pro-employees.component.ts` | PRO page with tabs: Team / Leaves / Documents |
| `pages/employee/employee-dashboard.component.ts` | Employee home page |
| `pages/employee/employee-planning.component.ts` | Employee planning view |
| `pages/employee/employee-bookings.component.ts` | Employee bookings (filtered to self) |
| `pages/employee/employee-leaves.component.ts` | Employee leave request page |
| `pages/employee/employee-documents.component.ts` | Employee documents page |

### Frontend — Modified Files

| File | Change |
|------|--------|
| `core/auth/auth.model.ts` | Add `EMPLOYEE` to Role enum |
| `core/auth/auth.service.ts` | Add `navigateByRole` for EMPLOYEE → `/employee/dashboard` |
| `app.routes.ts` | Add `/employee/*` routes with `roleGuard(Role.EMPLOYEE)` |
| `shared/layout/navigation/navigation-routes.ts` | Add `EMPLOYEE_NAVIGATION_ROUTES` |
| `shared/layout/navigation/sidenav-menu.ts` | Handle EMPLOYEE routes |
| `shared/layout/header/header.ts` | Adapt header for EMPLOYEE |
| `public/i18n/fr.json` | Add all employee/leave/document translation keys |
| `public/i18n/en.json` | Add all employee/leave/document translation keys |
| Client booking flow components | Add optional employee selector |

---

## Implementation Tasks

### Task 1: Backend — Role Enum & Security Config

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/users/domain/Role.java`
- Modify: `backend/src/main/java/com/prettyface/app/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/prettyface/app/users/domain/RoleTests.java`

- [ ] **Step 1: Add EMPLOYEE to Role enum**

```java
// Role.java
package com.prettyface.app.users.domain;

public enum Role {
    USER,
    ADMIN,
    PRO,
    EMPLOYEE
}
```

- [ ] **Step 2: Write test for new role**

```java
// RoleTests.java
package com.prettyface.app.users.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RoleTests {
    @Test
    void employee_role_exists() {
        assertThat(Role.valueOf("EMPLOYEE")).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void all_roles_present() {
        assertThat(Role.values()).containsExactly(Role.USER, Role.ADMIN, Role.PRO, Role.EMPLOYEE);
    }
}
```

- [ ] **Step 3: Run test**

Run: `./mvnw test -Dtest="RoleTests"`
Expected: PASS

- [ ] **Step 4: Update SecurityConfig — allow EMPLOYEE endpoints**

Open `SecurityConfig.java` and add to the `securityFilterChain` method's `authorizeHttpRequests` block:

```java
.requestMatchers("/api/employee/**").hasAnyRole("EMPLOYEE", "PRO", "ADMIN")
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/users/domain/Role.java \
       backend/src/main/java/com/prettyface/app/config/SecurityConfig.java \
       backend/src/test/java/com/prettyface/app/users/domain/RoleTests.java
git commit -m "feat: add EMPLOYEE role enum and security config"
```

---

### Task 2: Backend — Employee Entity & Repository

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/employee/domain/Employee.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/repo/EmployeeRepository.java`

- [ ] **Step 1: Create Employee entity**

```java
// Employee.java
package com.prettyface.app.employee.domain;

import com.prettyface.app.care.domain.Care;
import com.prettyface.app.users.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "EMPLOYEES", uniqueConstraints = {
    @UniqueConstraint(name = "UK_EMPLOYEE_USER", columnNames = "user_id")
})
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "EMPLOYEE_CARES",
        joinColumns = @JoinColumn(name = "employee_id"),
        inverseJoinColumns = @JoinColumn(name = "care_id")
    )
    private Set<Care> assignedCares = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: Create EmployeeRepository**

```java
// EmployeeRepository.java
package com.prettyface.app.employee.repo;

import com.prettyface.app.employee.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByUserId(Long userId);
    List<Employee> findByActiveTrue();

    @Query("SELECT e FROM Employee e JOIN e.assignedCares c WHERE c.id = :careId AND e.active = true")
    List<Employee> findActiveByAssignedCareId(Long careId);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/employee/domain/Employee.java \
       backend/src/main/java/com/prettyface/app/employee/repo/EmployeeRepository.java
git commit -m "feat: add Employee entity and repository"
```

---

### Task 3: Backend — Leave Request Entities

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/employee/domain/LeaveType.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/domain/LeaveStatus.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/domain/LeaveRequest.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/repo/LeaveRequestRepository.java`

- [ ] **Step 1: Create enums**

```java
// LeaveType.java
package com.prettyface.app.employee.domain;

public enum LeaveType {
    VACATION,
    SICKNESS
}
```

```java
// LeaveStatus.java
package com.prettyface.app.employee.domain;

public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED
}
```

- [ ] **Step 2: Create LeaveRequest entity**

```java
// LeaveRequest.java
package com.prettyface.app.employee.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "LEAVE_REQUESTS")
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false,
                foreignKey = @ForeignKey(name = "FK_LEAVE_EMPLOYEE"))
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    private LeaveType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "document_path", length = 500)
    private String documentPath;

    @Column(name = "reviewer_note", length = 500)
    private String reviewerNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Create LeaveRequestRepository**

```java
// LeaveRequestRepository.java
package com.prettyface.app.employee.repo;

import com.prettyface.app.employee.domain.LeaveRequest;
import com.prettyface.app.employee.domain.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeIdOrderByStartDateDesc(Long employeeId);
    List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveStatus status);
    List<LeaveRequest> findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, LeaveStatus status, LocalDate endDate, LocalDate startDate);
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/employee/domain/LeaveType.java \
       backend/src/main/java/com/prettyface/app/employee/domain/LeaveStatus.java \
       backend/src/main/java/com/prettyface/app/employee/domain/LeaveRequest.java \
       backend/src/main/java/com/prettyface/app/employee/repo/LeaveRequestRepository.java
git commit -m "feat: add LeaveRequest entity with type and status enums"
```

---

### Task 4: Backend — Employee Document Entity

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/employee/domain/DocumentType.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/domain/EmployeeDocument.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/repo/EmployeeDocumentRepository.java`

- [ ] **Step 1: Create DocumentType enum**

```java
// DocumentType.java
package com.prettyface.app.employee.domain;

public enum DocumentType {
    CONTRACT,
    PAYSLIP,
    MEDICAL_CERTIFICATE,
    OTHER
}
```

- [ ] **Step 2: Create EmployeeDocument entity**

```java
// EmployeeDocument.java
package com.prettyface.app.employee.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "EMPLOYEE_DOCUMENTS")
public class EmployeeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false,
                foreignKey = @ForeignKey(name = "FK_DOC_EMPLOYEE"))
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private DocumentType type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private Long uploadedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Create EmployeeDocumentRepository**

```java
// EmployeeDocumentRepository.java
package com.prettyface.app.employee.repo;

import com.prettyface.app.employee.domain.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {
    List<EmployeeDocument> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/employee/domain/DocumentType.java \
       backend/src/main/java/com/prettyface/app/employee/domain/EmployeeDocument.java \
       backend/src/main/java/com/prettyface/app/employee/repo/EmployeeDocumentRepository.java
git commit -m "feat: add EmployeeDocument entity for contracts, payslips, medical certs"
```

---

### Task 5: Backend — Tenant Schema DDL for Employee Tables

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/multitenancy/TenantSchemaManager.java`

- [ ] **Step 1: Add employee tables to TENANT_TABLES list**

In `TenantSchemaManager.java`, update the `TENANT_TABLES` constant (line 33):

```java
private static final List<String> TENANT_TABLES = List.of(
        "CATEGORIES",
        "SERVICES",
        "CARE_IMAGES",
        "OPENING_HOURS",
        "BLOCKED_SLOTS",
        "CARE_BOOKINGS",
        "EMPLOYEES",
        "EMPLOYEE_CARES",
        "LEAVE_REQUESTS",
        "EMPLOYEE_DOCUMENTS"
);
```

- [ ] **Step 2: Add DDL statements for Oracle**

In the `createTenantTables` method, add these DDL statements after the CARE_BOOKINGS block:

```java
// ── EMPLOYEES ──
"""
CREATE TABLE EMPLOYEES (
    ID         NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    USER_ID    NUMBER(19) NOT NULL,
    NAME       VARCHAR2(255 CHAR) NOT NULL,
    EMAIL      VARCHAR2(255 CHAR) NOT NULL,
    PHONE      VARCHAR2(255 CHAR),
    ACTIVE     NUMBER(1) NOT NULL,
    CREATED_AT TIMESTAMP NOT NULL,
    CONSTRAINT UK_EMPLOYEE_USER UNIQUE (USER_ID)
)""",

// ── EMPLOYEE_CARES (join table) ──
"""
CREATE TABLE EMPLOYEE_CARES (
    EMPLOYEE_ID NUMBER(19) NOT NULL,
    CARE_ID     NUMBER(19) NOT NULL,
    CONSTRAINT FK_EC_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID),
    CONSTRAINT FK_EC_CARE FOREIGN KEY (CARE_ID) REFERENCES SERVICES(ID),
    CONSTRAINT PK_EMPLOYEE_CARES PRIMARY KEY (EMPLOYEE_ID, CARE_ID)
)""",

// ── LEAVE_REQUESTS ──
"""
CREATE TABLE LEAVE_REQUESTS (
    ID            NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    EMPLOYEE_ID   NUMBER(19) NOT NULL,
    LEAVE_TYPE    VARCHAR2(255 CHAR) NOT NULL,
    STATUS        VARCHAR2(255 CHAR) NOT NULL,
    START_DATE    DATE NOT NULL,
    END_DATE      DATE NOT NULL,
    REASON        VARCHAR2(500 CHAR),
    DOCUMENT_PATH VARCHAR2(500 CHAR),
    REVIEWER_NOTE VARCHAR2(500 CHAR),
    CREATED_AT    TIMESTAMP NOT NULL,
    REVIEWED_AT   TIMESTAMP,
    CONSTRAINT FK_LEAVE_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID)
)""",

// ── EMPLOYEE_DOCUMENTS ──
"""
CREATE TABLE EMPLOYEE_DOCUMENTS (
    ID                  NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    EMPLOYEE_ID         NUMBER(19) NOT NULL,
    DOC_TYPE            VARCHAR2(255 CHAR) NOT NULL,
    TITLE               VARCHAR2(255 CHAR) NOT NULL,
    FILENAME            VARCHAR2(255 CHAR) NOT NULL,
    FILE_PATH           VARCHAR2(500 CHAR) NOT NULL,
    UPLOADED_BY_USER_ID NUMBER(19) NOT NULL,
    CREATED_AT          TIMESTAMP NOT NULL,
    CONSTRAINT FK_DOC_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID)
)"""
```

- [ ] **Step 3: Update reverse drop order**

In `dropTenantTables`, update the reverse list:

```java
List<String> reverseTables = List.of(
        "EMPLOYEE_DOCUMENTS", "LEAVE_REQUESTS", "EMPLOYEE_CARES",
        "EMPLOYEES", "CARE_BOOKINGS", "BLOCKED_SLOTS", "OPENING_HOURS",
        "CARE_IMAGES", "SERVICES", "CATEGORIES"
);
```

- [ ] **Step 4: Update H2 DDL similarly** (same table definitions but with H2 syntax — `IDENTITY` instead of `GENERATED ALWAYS AS IDENTITY`, `BOOLEAN` instead of `NUMBER(1)`)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/multitenancy/TenantSchemaManager.java
git commit -m "feat: add employee tables to tenant schema DDL"
```

---

### Task 6: Backend — Employee DTOs

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/employee/web/dto/CreateEmployeeRequest.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/web/dto/UpdateEmployeeRequest.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/web/dto/EmployeeResponse.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/web/dto/EmployeeSlimResponse.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/web/dto/LeaveRequestDto.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/web/dto/LeaveReviewDto.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/web/dto/LeaveResponse.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/web/dto/DocumentResponse.java`

- [ ] **Step 1: Create all DTOs**

```java
// CreateEmployeeRequest.java
package com.prettyface.app.employee.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateEmployeeRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    String phone,
    @NotBlank String password,
    List<Long> careIds
) {}
```

```java
// UpdateEmployeeRequest.java
package com.prettyface.app.employee.web.dto;

import java.util.List;

public record UpdateEmployeeRequest(
    String name,
    String phone,
    Boolean active,
    List<Long> careIds
) {}
```

```java
// EmployeeResponse.java
package com.prettyface.app.employee.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record EmployeeResponse(
    Long id,
    Long userId,
    String name,
    String email,
    String phone,
    boolean active,
    List<CareRef> assignedCares,
    LocalDateTime createdAt
) {
    public record CareRef(Long id, String name) {}
}
```

```java
// EmployeeSlimResponse.java
package com.prettyface.app.employee.web.dto;

public record EmployeeSlimResponse(
    Long id,
    String name,
    String imageUrl
) {}
```

```java
// LeaveRequestDto.java
package com.prettyface.app.employee.web.dto;

import com.prettyface.app.employee.domain.LeaveType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record LeaveRequestDto(
    @NotNull LeaveType type,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    String reason
) {}
```

```java
// LeaveReviewDto.java
package com.prettyface.app.employee.web.dto;

import com.prettyface.app.employee.domain.LeaveStatus;
import jakarta.validation.constraints.NotNull;

public record LeaveReviewDto(
    @NotNull LeaveStatus status,
    String reviewerNote
) {}
```

```java
// LeaveResponse.java
package com.prettyface.app.employee.web.dto;

import com.prettyface.app.employee.domain.LeaveStatus;
import com.prettyface.app.employee.domain.LeaveType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveResponse(
    Long id,
    Long employeeId,
    String employeeName,
    LeaveType type,
    LeaveStatus status,
    LocalDate startDate,
    LocalDate endDate,
    String reason,
    boolean hasDocument,
    String reviewerNote,
    LocalDateTime createdAt,
    LocalDateTime reviewedAt
) {}
```

```java
// DocumentResponse.java
package com.prettyface.app.employee.web.dto;

import com.prettyface.app.employee.domain.DocumentType;
import java.time.LocalDateTime;

public record DocumentResponse(
    Long id,
    Long employeeId,
    DocumentType type,
    String title,
    String filename,
    LocalDateTime createdAt
) {}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/employee/web/dto/
git commit -m "feat: add employee, leave, and document DTOs"
```

---

### Task 7: Backend — EmployeeService + Tests

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/employee/app/EmployeeService.java`
- Create: `backend/src/test/java/com/prettyface/app/employee/app/EmployeeServiceTests.java`

- [ ] **Step 1: Write the failing tests**

```java
// EmployeeServiceTests.java
package com.prettyface.app.employee.app;

import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.employee.domain.Employee;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.employee.web.dto.CreateEmployeeRequest;
import com.prettyface.app.employee.web.dto.EmployeeResponse;
import com.prettyface.app.employee.web.dto.UpdateEmployeeRequest;
import com.prettyface.app.users.domain.AuthProvider;
import com.prettyface.app.users.domain.Role;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTests {

    @Mock private EmployeeRepository employeeRepo;
    @Mock private UserRepository userRepo;
    @Mock private CareRepository careRepo;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private EmployeeService service;

    @Test
    void create_createsUserAndEmployee() {
        CreateEmployeeRequest req = new CreateEmployeeRequest(
            "Alice", "alice@salon.fr", "0612345678", "temp123", List.of()
        );
        when(userRepo.existsByEmail("alice@salon.fr")).thenReturn(false);
        when(passwordEncoder.encode("temp123")).thenReturn("hashed");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0); u.setId(10L); return u;
        });
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0); e.setId(1L); return e;
        });

        EmployeeResponse response = service.create(req);

        assertThat(response.name()).isEqualTo("Alice");
        assertThat(response.email()).isEqualTo("alice@salon.fr");
        assertThat(response.active()).isTrue();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void create_existingEmail_throws() {
        CreateEmployeeRequest req = new CreateEmployeeRequest(
            "Alice", "alice@salon.fr", null, "temp123", List.of()
        );
        when(userRepo.existsByEmail("alice@salon.fr")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("email");
    }

    @Test
    void toggleActive_setsFlag() {
        Employee emp = new Employee();
        emp.setId(1L); emp.setActive(true); emp.setName("Alice"); emp.setEmail("a@b.com");
        when(employeeRepo.findById(1L)).thenReturn(Optional.of(emp));
        when(employeeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmployeeResponse result = service.update(1L, new UpdateEmployeeRequest(null, null, false, null));
        assertThat(result.active()).isFalse();
    }

    @Test
    void update_assignsCares() {
        Employee emp = new Employee();
        emp.setId(1L); emp.setActive(true); emp.setName("Alice"); emp.setEmail("a@b.com");
        emp.setAssignedCares(new java.util.HashSet<>());

        Care c1 = new Care(); c1.setId(10L); c1.setName("Facial");
        Care c2 = new Care(); c2.setId(11L); c2.setName("Massage");

        when(employeeRepo.findById(1L)).thenReturn(Optional.of(emp));
        when(careRepo.findAllById(List.of(10L, 11L))).thenReturn(List.of(c1, c2));
        when(employeeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmployeeResponse result = service.update(1L, new UpdateEmployeeRequest(null, null, null, List.of(10L, 11L)));
        assertThat(result.assignedCares()).hasSize(2);
    }

    @Test
    void listAll_returnsAllEmployees() {
        Employee emp = new Employee();
        emp.setId(1L); emp.setName("Alice"); emp.setEmail("a@b.com");
        emp.setActive(true); emp.setAssignedCares(Set.of());
        when(employeeRepo.findAll()).thenReturn(List.of(emp));

        assertThat(service.listAll()).hasSize(1);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest="EmployeeServiceTests"`
Expected: FAIL — EmployeeService does not exist

- [ ] **Step 3: Implement EmployeeService**

```java
// EmployeeService.java
package com.prettyface.app.employee.app;

import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.employee.domain.Employee;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.employee.web.dto.*;
import com.prettyface.app.users.domain.AuthProvider;
import com.prettyface.app.users.domain.Role;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepo;
    private final UserRepository userRepo;
    private final CareRepository careRepo;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(EmployeeRepository employeeRepo, UserRepository userRepo,
                           CareRepository careRepo, PasswordEncoder passwordEncoder) {
        this.employeeRepo = employeeRepo;
        this.userRepo = userRepo;
        this.careRepo = careRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> listAll() {
        return employeeRepo.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long id) {
        return toResponse(employeeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + id)));
    }

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            throw new IllegalArgumentException("A user with this email already exists");
        }

        User user = User.builder()
                .name(req.name())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .role(Role.EMPLOYEE)
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .build();
        user = userRepo.save(user);

        Employee employee = new Employee();
        employee.setUserId(user.getId());
        employee.setName(req.name());
        employee.setEmail(req.email());
        employee.setPhone(req.phone());
        employee.setActive(true);

        if (req.careIds() != null && !req.careIds().isEmpty()) {
            employee.setAssignedCares(new HashSet<>(careRepo.findAllById(req.careIds())));
        }

        return toResponse(employeeRepo.save(employee));
    }

    @Transactional
    public EmployeeResponse update(Long id, UpdateEmployeeRequest req) {
        Employee employee = employeeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + id));

        if (req.name() != null) employee.setName(req.name());
        if (req.phone() != null) employee.setPhone(req.phone());
        if (req.active() != null) employee.setActive(req.active());
        if (req.careIds() != null) {
            employee.setAssignedCares(new HashSet<>(careRepo.findAllById(req.careIds())));
        }

        return toResponse(employeeRepo.save(employee));
    }

    @Transactional
    public void delete(Long id) {
        employeeRepo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<EmployeeSlimResponse> listForCare(Long careId) {
        return employeeRepo.findActiveByAssignedCareId(careId).stream()
                .map(e -> new EmployeeSlimResponse(e.getId(), e.getName(), null))
                .toList();
    }

    private EmployeeResponse toResponse(Employee e) {
        var cares = e.getAssignedCares().stream()
                .map(c -> new EmployeeResponse.CareRef(c.getId(), c.getName()))
                .toList();
        return new EmployeeResponse(
                e.getId(), e.getUserId(), e.getName(), e.getEmail(),
                e.getPhone(), e.isActive(), cares, e.getCreatedAt()
        );
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./mvnw test -Dtest="EmployeeServiceTests"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/employee/app/EmployeeService.java \
       backend/src/test/java/com/prettyface/app/employee/app/EmployeeServiceTests.java
git commit -m "feat: add EmployeeService with create, update, toggle, and care assignment"
```

---

### Task 8: Backend — LeaveRequestService + Tests

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/employee/app/LeaveRequestService.java`
- Create: `backend/src/test/java/com/prettyface/app/employee/app/LeaveRequestServiceTests.java`

- [ ] **Step 1: Write the failing tests**

```java
// LeaveRequestServiceTests.java
package com.prettyface.app.employee.app;

import com.prettyface.app.employee.domain.*;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.employee.repo.LeaveRequestRepository;
import com.prettyface.app.employee.web.dto.LeaveRequestDto;
import com.prettyface.app.employee.web.dto.LeaveResponse;
import com.prettyface.app.employee.web.dto.LeaveReviewDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTests {

    @Mock private LeaveRequestRepository leaveRepo;
    @Mock private EmployeeRepository employeeRepo;

    @InjectMocks private LeaveRequestService service;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(1L);
        employee.setName("Alice");
        employee.setEmail("alice@salon.fr");
    }

    @Test
    void createLeave_vacation_setsPending() {
        when(employeeRepo.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveRepo.save(any())).thenAnswer(inv -> {
            LeaveRequest lr = inv.getArgument(0); lr.setId(10L); return lr;
        });

        LeaveRequestDto dto = new LeaveRequestDto(
            LeaveType.VACATION, LocalDate.now().plusDays(10), LocalDate.now().plusDays(15), "Vacances été"
        );
        LeaveResponse response = service.createLeave(1L, dto);

        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);
        assertThat(response.type()).isEqualTo(LeaveType.VACATION);
        assertThat(response.employeeName()).isEqualTo("Alice");
    }

    @Test
    void createLeave_endBeforeStart_throws() {
        when(employeeRepo.findById(1L)).thenReturn(Optional.of(employee));

        LeaveRequestDto dto = new LeaveRequestDto(
            LeaveType.VACATION, LocalDate.now().plusDays(15), LocalDate.now().plusDays(10), null
        );

        assertThatThrownBy(() -> service.createLeave(1L, dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("after start");
    }

    @Test
    void reviewLeave_approve_setsApproved() {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(10L); lr.setEmployee(employee); lr.setType(LeaveType.VACATION);
        lr.setStatus(LeaveStatus.PENDING);
        lr.setStartDate(LocalDate.now().plusDays(5)); lr.setEndDate(LocalDate.now().plusDays(7));
        when(leaveRepo.findById(10L)).thenReturn(Optional.of(lr));
        when(leaveRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveResponse response = service.reviewLeave(10L, new LeaveReviewDto(LeaveStatus.APPROVED, "OK"));

        assertThat(response.status()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(response.reviewerNote()).isEqualTo("OK");
    }

    @Test
    void reviewLeave_alreadyReviewed_throws() {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(10L); lr.setEmployee(employee); lr.setStatus(LeaveStatus.APPROVED);
        when(leaveRepo.findById(10L)).thenReturn(Optional.of(lr));

        assertThatThrownBy(() -> service.reviewLeave(10L, new LeaveReviewDto(LeaveStatus.REJECTED, null)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listPending_returnsPendingOnly() {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(1L); lr.setEmployee(employee); lr.setType(LeaveType.SICKNESS);
        lr.setStatus(LeaveStatus.PENDING);
        lr.setStartDate(LocalDate.now()); lr.setEndDate(LocalDate.now().plusDays(3));
        when(leaveRepo.findByStatusOrderByCreatedAtAsc(LeaveStatus.PENDING)).thenReturn(List.of(lr));

        assertThat(service.listPending()).hasSize(1);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest="LeaveRequestServiceTests"`
Expected: FAIL — LeaveRequestService does not exist

- [ ] **Step 3: Implement LeaveRequestService**

```java
// LeaveRequestService.java
package com.prettyface.app.employee.app;

import com.prettyface.app.employee.domain.*;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.employee.repo.LeaveRequestRepository;
import com.prettyface.app.employee.web.dto.LeaveRequestDto;
import com.prettyface.app.employee.web.dto.LeaveResponse;
import com.prettyface.app.employee.web.dto.LeaveReviewDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRepo;
    private final EmployeeRepository employeeRepo;

    public LeaveRequestService(LeaveRequestRepository leaveRepo, EmployeeRepository employeeRepo) {
        this.leaveRepo = leaveRepo;
        this.employeeRepo = employeeRepo;
    }

    @Transactional
    public LeaveResponse createLeave(Long employeeId, LeaveRequestDto dto) {
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        if (!dto.endDate().isAfter(dto.startDate()) && !dto.endDate().isEqual(dto.startDate())) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }

        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(employee);
        lr.setType(dto.type());
        lr.setStatus(LeaveStatus.PENDING);
        lr.setStartDate(dto.startDate());
        lr.setEndDate(dto.endDate());
        lr.setReason(dto.reason());

        return toResponse(leaveRepo.save(lr));
    }

    @Transactional
    public LeaveResponse reviewLeave(Long leaveId, LeaveReviewDto dto) {
        LeaveRequest lr = leaveRepo.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + leaveId));

        if (lr.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalStateException("Leave request has already been reviewed");
        }

        lr.setStatus(dto.status());
        lr.setReviewerNote(dto.reviewerNote());
        lr.setReviewedAt(LocalDateTime.now());

        return toResponse(leaveRepo.save(lr));
    }

    @Transactional
    public LeaveResponse attachDocument(Long leaveId, String documentPath) {
        LeaveRequest lr = leaveRepo.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + leaveId));
        lr.setDocumentPath(documentPath);
        return toResponse(leaveRepo.save(lr));
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> listByEmployee(Long employeeId) {
        return leaveRepo.findByEmployeeIdOrderByStartDateDesc(employeeId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> listPending() {
        return leaveRepo.findByStatusOrderByCreatedAtAsc(LeaveStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public boolean isOnLeave(Long employeeId, LocalDate date) {
        return !leaveRepo.findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                employeeId, LeaveStatus.APPROVED, date, date).isEmpty();
    }

    private LeaveResponse toResponse(LeaveRequest lr) {
        return new LeaveResponse(
                lr.getId(), lr.getEmployee().getId(), lr.getEmployee().getName(),
                lr.getType(), lr.getStatus(),
                lr.getStartDate(), lr.getEndDate(),
                lr.getReason(), lr.getDocumentPath() != null,
                lr.getReviewerNote(), lr.getCreatedAt(), lr.getReviewedAt()
        );
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./mvnw test -Dtest="LeaveRequestServiceTests"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/employee/app/LeaveRequestService.java \
       backend/src/test/java/com/prettyface/app/employee/app/LeaveRequestServiceTests.java
git commit -m "feat: add LeaveRequestService with create, review, and leave-check logic"
```

---

### Task 9: Backend — Employee Controllers

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/employee/web/EmployeeController.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/web/EmployeeLeaveController.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/web/MyEmployeeController.java`

- [ ] **Step 1: Create EmployeeController (PRO endpoints)**

```java
// EmployeeController.java
package com.prettyface.app.employee.web;

import com.prettyface.app.employee.app.EmployeeService;
import com.prettyface.app.employee.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pro/employees")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping
    public List<EmployeeResponse> list() {
        return service.listAll();
    }

    @GetMapping("/{id}")
    public EmployeeResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(@RequestBody @Valid CreateEmployeeRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(@PathVariable Long id, @RequestBody @Valid UpdateEmployeeRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
```

- [ ] **Step 2: Create EmployeeLeaveController (PRO endpoints)**

```java
// EmployeeLeaveController.java
package com.prettyface.app.employee.web;

import com.prettyface.app.employee.app.LeaveRequestService;
import com.prettyface.app.employee.web.dto.LeaveResponse;
import com.prettyface.app.employee.web.dto.LeaveReviewDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pro/leaves")
public class EmployeeLeaveController {

    private final LeaveRequestService service;

    public EmployeeLeaveController(LeaveRequestService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    public List<LeaveResponse> listPending() {
        return service.listPending();
    }

    @GetMapping("/employee/{employeeId}")
    public List<LeaveResponse> listByEmployee(@PathVariable Long employeeId) {
        return service.listByEmployee(employeeId);
    }

    @PutMapping("/{leaveId}/review")
    public LeaveResponse review(@PathVariable Long leaveId, @RequestBody @Valid LeaveReviewDto dto) {
        return service.reviewLeave(leaveId, dto);
    }
}
```

- [ ] **Step 3: Create MyEmployeeController (EMPLOYEE self-service)**

```java
// MyEmployeeController.java
package com.prettyface.app.employee.web;

import com.prettyface.app.employee.app.EmployeeService;
import com.prettyface.app.employee.app.LeaveRequestService;
import com.prettyface.app.employee.domain.Employee;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.employee.web.dto.*;
import com.prettyface.app.auth.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employee/me")
public class MyEmployeeController {

    private final EmployeeRepository employeeRepo;
    private final LeaveRequestService leaveService;

    public MyEmployeeController(EmployeeRepository employeeRepo, LeaveRequestService leaveService) {
        this.employeeRepo = employeeRepo;
        this.leaveService = leaveService;
    }

    @GetMapping
    public EmployeeResponse getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        Employee emp = resolveEmployee(principal.getId());
        var cares = emp.getAssignedCares().stream()
                .map(c -> new EmployeeResponse.CareRef(c.getId(), c.getName())).toList();
        return new EmployeeResponse(emp.getId(), emp.getUserId(), emp.getName(), emp.getEmail(),
                emp.getPhone(), emp.isActive(), cares, emp.getCreatedAt());
    }

    @GetMapping("/leaves")
    public List<LeaveResponse> myLeaves(@AuthenticationPrincipal UserPrincipal principal) {
        Employee emp = resolveEmployee(principal.getId());
        return leaveService.listByEmployee(emp.getId());
    }

    @PostMapping("/leaves")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveResponse createLeave(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestBody @Valid LeaveRequestDto dto) {
        Employee emp = resolveEmployee(principal.getId());
        return leaveService.createLeave(emp.getId(), dto);
    }

    private Employee resolveEmployee(Long userId) {
        return employeeRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("No employee profile found for user"));
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/employee/web/
git commit -m "feat: add employee controllers (PRO management + employee self-service)"
```

---

### Task 10: Backend — Extend Availability for Per-Employee Scheduling

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/availability/domain/OpeningHour.java`
- Modify: `backend/src/main/java/com/prettyface/app/availability/domain/BlockedSlot.java`
- Modify: `backend/src/main/java/com/prettyface/app/availability/repo/OpeningHourRepository.java`
- Modify: `backend/src/main/java/com/prettyface/app/availability/repo/BlockedSlotRepository.java`
- Modify: `backend/src/main/java/com/prettyface/app/availability/app/SlotAvailabilityService.java`
- Modify: `backend/src/main/java/com/prettyface/app/bookings/domain/CareBooking.java`
- Modify: `backend/src/main/java/com/prettyface/app/bookings/web/dto/ClientBookingRequest.java`
- Test: update `SlotAvailabilityServiceTests.java`

- [ ] **Step 1: Add optional employeeId to OpeningHour and BlockedSlot**

```java
// In OpeningHour.java, add:
@Column(name = "employee_id")
private Long employeeId; // null = salon-wide
```

```java
// In BlockedSlot.java, add:
@Column(name = "employee_id")
private Long employeeId; // null = salon-wide
```

- [ ] **Step 2: Add repository queries**

```java
// In OpeningHourRepository.java, add:
List<OpeningHour> findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(Long employeeId);
List<OpeningHour> findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc();
```

```java
// In BlockedSlotRepository.java, add:
List<BlockedSlot> findByEmployeeIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(Long employeeId, LocalDate date);
```

- [ ] **Step 3: Add employeeId to CareBooking**

```java
// In CareBooking.java, add:
@Column(name = "employee_id")
private Long employeeId; // null = unassigned
```

- [ ] **Step 4: Add optional employeeId to ClientBookingRequest**

```java
// In ClientBookingRequest.java, add the field:
Long employeeId  // optional — null means auto-assign
```

- [ ] **Step 5: Add per-employee slot calculation to SlotAvailabilityService**

Add a new overload method `getAvailableSlots(LocalDate date, Long careId, Long employeeId)` that:
1. Gets the employee's personal opening hours (fallback to salon-wide if none)
2. Gets employee-specific blocked slots + salon-wide blocked slots
3. Gets existing bookings assigned to this employee
4. Checks leave status via `LeaveRequestService.isOnLeave(employeeId, date)`
5. Computes available slots using the same algorithm

- [ ] **Step 6: Add auto-assign logic**

Add method `findFirstAvailableEmployee(LocalDate date, Long careId, String time)` that:
1. Gets all active employees assigned to the care
2. For each, checks if the specific slot is available
3. Returns the first available employee ID (or null if none)

- [ ] **Step 7: Update existing tests + add per-employee tests**

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/availability/ \
       backend/src/main/java/com/prettyface/app/bookings/ \
       backend/src/test/java/com/prettyface/app/availability/
git commit -m "feat: extend availability engine for per-employee scheduling"
```

---

### Task 11: Backend — Public Endpoint for Employee List by Care

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java`

- [ ] **Step 1: Add endpoint**

```java
// In PublicSalonController.java, add:
@GetMapping("/{slug}/employees")
public List<EmployeeSlimResponse> listEmployeesForCare(
        @PathVariable String slug,
        @RequestParam Long careId) {
    Tenant tenant = tenantService.findBySlug(slug);
    TenantContext.setCurrentTenant(slug);
    try {
        return employeeService.listForCare(careId);
    } finally {
        TenantContext.clear();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/web/PublicSalonController.java
git commit -m "feat: add public endpoint to list employees available for a care"
```

---

### Task 12: Backend — Update TenantFilter for EMPLOYEE Role

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/multitenancy/TenantFilter.java`

- [ ] **Step 1: Resolve tenant for EMPLOYEE users**

Currently `TenantFilter` resolves tenant via `tenantService.findByOwnerId(userId)`. For employees, the user is not the owner. Add logic:
1. Try `findByOwnerId` first (for PRO)
2. If not found, look up `Employee` by `userId` to get the tenant slug
3. This requires a way to map employee → tenant. Since employees live in tenant schemas, we need a public-schema mapping. **Solution:** store the `tenantSlug` on the `User` entity or create a lookup table. Simplest: add a `tenant_slug` column to `USERS` table, set when creating an employee.

```java
// In User.java, add:
@Column(name = "tenant_slug")
private String tenantSlug;
```

Then in TenantFilter:
```java
// After failing to find tenant by ownerId:
if (user.getTenantSlug() != null) {
    TenantContext.setCurrentTenant(user.getTenantSlug());
}
```

- [ ] **Step 2: Update EmployeeService.create to set tenantSlug on User**

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/multitenancy/TenantFilter.java \
       backend/src/main/java/com/prettyface/app/users/domain/User.java \
       backend/src/main/java/com/prettyface/app/employee/app/EmployeeService.java
git commit -m "feat: resolve tenant context for EMPLOYEE role via tenantSlug on User"
```

---

### Task 13: Frontend — Role Enum + Auth Updates

**Files:**
- Modify: `frontend/src/app/core/auth/auth.model.ts`
- Modify: `frontend/src/app/core/auth/auth.service.ts`
- Modify: `frontend/src/app/core/auth/role.guard.ts`

- [ ] **Step 1: Add EMPLOYEE role**

```typescript
// In auth.model.ts, update:
export enum Role {
  USER = 'USER',
  ADMIN = 'ADMIN',
  PRO = 'PRO',
  EMPLOYEE = 'EMPLOYEE',
}
```

- [ ] **Step 2: Update navigateByRole in auth.service.ts**

```typescript
navigateByRole(): void {
    const role = this.currentUser()?.role;
    if (role === Role.PRO || role === Role.ADMIN) {
      this.router.navigate(['/pro/dashboard']);
    } else if (role === Role.EMPLOYEE) {
      this.router.navigate(['/employee/dashboard']);
    } else {
      this.router.navigate(['/']);
    }
}
```

- [ ] **Step 3: Update role.guard.ts to allow EMPLOYEE**

The existing roleGuard already allows ADMIN to bypass. No change needed unless we want PRO to also access EMPLOYEE routes. The guard logic is already flexible.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/core/auth/
git commit -m "feat: add EMPLOYEE role to frontend auth model and navigation"
```

---

### Task 14: Frontend — Employee Routes + Navigation

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/shared/layout/navigation/navigation-routes.ts`
- Modify: `frontend/src/app/shared/layout/navigation/sidenav-menu.ts`
- Modify: `frontend/src/app/shared/layout/header/header.ts`

- [ ] **Step 1: Add employee routes to app.routes.ts**

```typescript
// Add after the PRO routes block:
{
  path: 'employee',
  canActivate: [authGuard, roleGuard(Role.EMPLOYEE)],
  children: [
    { path: 'dashboard', loadComponent: () => import('./pages/employee/employee-dashboard.component').then(m => m.EmployeeDashboardComponent) },
    { path: 'planning', loadComponent: () => import('./pages/employee/employee-planning.component').then(m => m.EmployeePlanningComponent) },
    { path: 'bookings', loadComponent: () => import('./pages/employee/employee-bookings.component').then(m => m.EmployeeBookingsComponent) },
    { path: 'leaves', loadComponent: () => import('./pages/employee/employee-leaves.component').then(m => m.EmployeeLeavesComponent) },
    { path: 'documents', loadComponent: () => import('./pages/employee/employee-documents.component').then(m => m.EmployeeDocumentsComponent) },
    { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  ]
},
```

- [ ] **Step 2: Add EMPLOYEE_NAVIGATION_ROUTES**

```typescript
// In navigation-routes.ts, add:
export const EMPLOYEE_NAVIGATION_ROUTES: NavigationRoute[] = [
  { label: 'Dashboard', path: '/employee/dashboard', icon: 'dashboard', requiresAuth: true },
  { label: 'Planning', path: '/employee/planning', icon: 'calendar_month', requiresAuth: true },
  { label: 'Réservations', path: '/employee/bookings', icon: 'event', requiresAuth: true },
  { label: 'Congés', path: '/employee/leaves', icon: 'beach_access', requiresAuth: true },
  { label: 'Documents', path: '/employee/documents', icon: 'folder', requiresAuth: true },
];
```

- [ ] **Step 3: Update sidenav-menu.ts to handle EMPLOYEE**

Add condition: if role === EMPLOYEE, show EMPLOYEE_NAVIGATION_ROUTES.

- [ ] **Step 4: Update header to adapt for EMPLOYEE**

Show salon name for EMPLOYEE (similar to PRO). Show bookings drawer for EMPLOYEE.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/app.routes.ts \
       frontend/src/app/shared/layout/
git commit -m "feat: add employee routes and navigation"
```

---

### Task 15: Frontend — PRO Employee Management Page

**Files:**
- Create: `frontend/src/app/features/employees/` (model, service, store, component)
- Create: `frontend/src/app/pages/pro/pro-employees.component.ts`
- Modify: `frontend/src/app/app.routes.ts` — add `/pro/employees` route

This task creates the PRO-facing employee management UI with:
- Employee list (cards showing name, email, active badge, assigned cares)
- Create employee modal (name, email, phone, password, care assignment)
- Edit employee modal (toggle active, reassign cares)
- Tab for pending leave requests with approve/reject actions

Follow the same patterns as `cares.component.ts` for the card layout and `bookings.component.ts` for the store pattern.

- [ ] **Step 1: Create employees.model.ts** with TypeScript interfaces matching backend DTOs
- [ ] **Step 2: Create employees.service.ts** extending pattern from other services
- [ ] **Step 3: Create employees.store.ts** with NgRx SignalStore
- [ ] **Step 4: Create employees.component.ts/html/scss** — employee list with cards
- [ ] **Step 5: Create create-employee modal**
- [ ] **Step 6: Create employee-detail modal** (edit cares, toggle active)
- [ ] **Step 7: Create leaves tab** (list pending, approve/reject buttons)
- [ ] **Step 8: Create pro-employees.component.ts** — wrapper page with tabs (Équipe / Congés)
- [ ] **Step 9: Add route and navigation** — `/pro/employees` in routes and PRO_NAVIGATION_ROUTES
- [ ] **Step 10: Add translations** to `fr.json` and `en.json`
- [ ] **Step 11: Commit**

---

### Task 16: Frontend — Employee Dashboard & Pages

**Files:**
- Create: `frontend/src/app/pages/employee/employee-dashboard.component.ts`
- Create: `frontend/src/app/pages/employee/employee-planning.component.ts`
- Create: `frontend/src/app/pages/employee/employee-bookings.component.ts`
- Create: `frontend/src/app/pages/employee/employee-leaves.component.ts`
- Create: `frontend/src/app/pages/employee/employee-documents.component.ts`

- [ ] **Step 1: Employee Dashboard** — shows today's bookings, upcoming leaves, quick stats
- [ ] **Step 2: Employee Planning** — reuses CalendarComponent + AvailabilityComponent (read-only)
- [ ] **Step 3: Employee Bookings** — reuses bookings drawer/list filtered to `employeeId`
- [ ] **Step 4: Employee Leaves** — create leave request form + list of own leaves with status
- [ ] **Step 5: Employee Documents** — list documents, upload medical certificates
- [ ] **Step 6: Add translations**
- [ ] **Step 7: Commit**

---

### Task 17: Frontend — Client Booking Flow: Employee Selector

**Files:**
- Modify: client booking components (the salon page / booking flow)
- Create: `frontend/src/app/features/employees/employee-selector/` component

- [ ] **Step 1: After client selects a care, fetch employees for that care**

Call `GET /api/salon/{slug}/employees?careId={careId}`

- [ ] **Step 2: Create employee-selector component**

Optional selector showing employee avatars/names. "Pas de préférence" = auto-assign.

- [ ] **Step 3: Pass employeeId in booking request**

Update `ClientBookingRequest` to include optional `employeeId`.

- [ ] **Step 4: Update available slots call**

When an employee is selected, request slots filtered to that employee.

- [ ] **Step 5: Add translations**
- [ ] **Step 6: Commit**

---

### Task 18: Backend — Document Upload/Download Endpoints

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/employee/app/EmployeeDocumentService.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/web/EmployeeDocumentController.java`

- [ ] **Step 1: Implement EmployeeDocumentService**

Upload (save file to `uploads/employees/{employeeId}/documents/`), list, delete.

- [ ] **Step 2: Implement controller endpoints**

```
POST   /api/pro/employees/{id}/documents  (multipart upload)
GET    /api/pro/employees/{id}/documents  (list)
GET    /api/pro/employees/{id}/documents/{docId}/download
DELETE /api/pro/employees/{id}/documents/{docId}
```

Also employee self-service:
```
GET    /api/employee/me/documents
POST   /api/employee/me/documents  (upload medical cert)
```

- [ ] **Step 3: Commit**

---

### Task 19: Integration Testing & Cleanup

- [ ] **Step 1: Run all backend tests**

Run: `./mvnw test`
Expected: ALL PASS

- [ ] **Step 2: Run frontend build**

Run: `cd frontend && npx ng build`
Expected: No TypeScript errors

- [ ] **Step 3: Manual integration test checklist**
- PRO can create an employee → user appears with EMPLOYEE role
- PRO can assign cares to employee
- PRO can toggle employee active/inactive
- Employee can log in and see dashboard
- Employee can create a leave request
- PRO can approve/reject leave requests
- Client can see employee selector when booking
- Auto-assign works when no employee is selected
- Slot availability respects employee leaves
- Document upload/download works

- [ ] **Step 4: Final commit**

```bash
git commit -m "feat: complete employee role with leaves, documents, and booking integration"
```

---

## Summary

| Task | Description | Estimated Steps |
|------|-------------|----------------|
| 1 | Role enum + security | 5 |
| 2 | Employee entity + repo | 3 |
| 3 | Leave entities | 4 |
| 4 | Document entity | 4 |
| 5 | Tenant DDL | 5 |
| 6 | DTOs | 2 |
| 7 | EmployeeService + tests | 5 |
| 8 | LeaveRequestService + tests | 5 |
| 9 | Controllers | 4 |
| 10 | Per-employee availability | 8 |
| 11 | Public employee endpoint | 2 |
| 12 | TenantFilter for EMPLOYEE | 3 |
| 13 | Frontend auth updates | 4 |
| 14 | Frontend routes + nav | 5 |
| 15 | PRO employee management UI | 11 |
| 16 | Employee dashboard + pages | 7 |
| 17 | Client booking employee selector | 6 |
| 18 | Document upload/download | 3 |
| 19 | Integration testing | 4 |
