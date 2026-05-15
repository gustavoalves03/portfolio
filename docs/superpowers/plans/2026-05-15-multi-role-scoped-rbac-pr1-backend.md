# Multi-Role Scoped RBAC (PR1 Backend) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single `User.role` field with a `USER_ROLE_ASSIGNMENTS` join table that supports multiple scoped roles (PRO/EMPLOYEE on N tenants, COMMERCIAL/ADMIN globally), expose `roles[]` + `activeTenantId` + `availableTenants[]` in the JWT, and add `/api/me/tenants` + `/api/me/switch-tenant`.

**Architecture:** Scoped RBAC (GCP IAM / GitHub style). `Role` enum stays 4 values: PRO/EMPLOYEE (TENANT scope) + COMMERCIAL/ADMIN (GLOBAL scope). USER is removed → absence of any assignment = CLIENT implicite. JWT-driven tenant context replaces URL-driven for `/api/pro/**` and `/api/me/**`. Backfill via Flyway V10 (shared) + Java one-shot runner (tenant-scoped EMPLOYEEs).

**Tech Stack:** Spring Boot 3.5.4, JPA/Hibernate, Flyway (Oracle), JWT (jjwt), JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-05-11-multi-role-scoped-rbac-pr1-backend.md`

---

## File Structure

### New files

| Path | Responsibility |
|---|---|
| `backend/src/main/java/com/luxpretty/app/users/domain/ScopeType.java` | Enum GLOBAL/TENANT |
| `backend/src/main/java/com/luxpretty/app/users/domain/UserRoleAssignment.java` | JPA junction entity |
| `backend/src/main/java/com/luxpretty/app/users/repo/UserRoleAssignmentRepository.java` | Spring Data finders |
| `backend/src/main/java/com/luxpretty/app/users/app/UserRoleService.java` | assign/revoke/resolve roles, validate scope |
| `backend/src/main/java/com/luxpretty/app/me/web/MeController.java` | GET /api/me/tenants, POST /api/me/switch-tenant |
| `backend/src/main/java/com/luxpretty/app/me/web/dto/TenantSummary.java` | record for /api/me/tenants response |
| `backend/src/main/java/com/luxpretty/app/me/web/dto/SwitchTenantRequest.java` | record for switch payload |
| `backend/src/main/java/com/luxpretty/app/multitenancy/EmployeeRoleBackfillRunner.java` | One-shot idempotent backfill of EMPLOYEE assignments |
| `backend/src/main/resources/db/migration/oracle/V10__user_role_assignments.sql` | DDL + backfill PRO/ADMIN + drop USERS.ROLE |
| `backend/src/test/java/com/luxpretty/app/users/domain/RoleTests.java` | Enum invariants |
| `backend/src/test/java/com/luxpretty/app/users/app/UserRoleServiceTests.java` | 10 tests assign/resolve |
| `backend/src/test/java/com/luxpretty/app/me/web/MeControllerTests.java` | 4 tests controller slice |

### Modified files

| Path | Reason |
|---|---|
| `backend/src/main/java/com/luxpretty/app/users/domain/Role.java` | Drop USER, add COMMERCIAL, add `expectedScopeType()` |
| `backend/src/main/java/com/luxpretty/app/users/domain/User.java` | Remove `role` field |
| `backend/src/main/java/com/luxpretty/app/auth/TokenService.java` | Emit `roles[]`, `activeTenantId`, `availableTenants[]` |
| `backend/src/main/java/com/luxpretty/app/auth/JwtAuthenticationFilter.java` | Parse `roles[]`, set TenantContext from JWT |
| `backend/src/main/java/com/luxpretty/app/auth/AuthController.java` | Use UserRoleService, build JWT via new signature |
| `backend/src/main/java/com/luxpretty/app/auth/CustomOAuth2UserService.java` | Assign role via UserRoleService (no `role` on User) |
| `backend/src/main/java/com/luxpretty/app/auth/OAuth2AuthenticationSuccessHandler.java` | Use new TokenService signature |
| `backend/src/main/java/com/luxpretty/app/auth/dto/UserDto.java` | Replace `Role role` with `List<String> roles` + legacy `role` |
| `backend/src/main/java/com/luxpretty/app/tenant/app/TenantProvisioningService.java` | Inject UserRoleService; assign PRO + EMPLOYEE on owner |
| `backend/src/main/java/com/luxpretty/app/employee/app/EmployeeService.java` | Assign EMPLOYEE on `create()` + `createSelfEmployee()` |
| `backend/src/main/java/com/luxpretty/app/config/CustomUserDetailsService.java` | Authorities from UserRoleService, not user.role |
| `backend/src/main/java/com/luxpretty/app/config/DataInitializer.java` | Drop `.role(...)` builder calls; assign via service |
| `backend/src/main/java/com/luxpretty/app/test/SmokeTestSeedController.java` | Same: no more user.role |
| `backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java:543,565` | `role == PRO \|\| role == ADMIN` → check via Set<Role> |
| `backend/src/main/java/com/luxpretty/app/tracking/app/TrackingService.java:87` | Same |
| `backend/src/main/java/com/luxpretty/app/employee/app/LeaveRequestService.java:160,181` | Same |
| `backend/src/main/java/com/luxpretty/app/employee/app/EmployeePermissionService.java:89` | Same |
| Multiple test files (Role.USER builder calls) | Drop `.role(...)` from User.builder(); add assignment via service mock |

---

## Cross-cutting decisions (already settled in spec)

- **Legacy `role` field in AuthResponse/UserDto**: keep it to avoid breaking the frontend in this PR. Populate from `roles[0]` if any (priority order: ADMIN > COMMERCIAL > PRO > EMPLOYEE), else null.
- **Backfill of EMPLOYEEs**: Java runner (cross-tenant); Flyway V10 covers PRO + ADMIN only.
- **Drop `USERS.ROLE` column in V10**: yes. Rollback = restore DB from backup if needed.
- **`Role.USER` removal**: drop immediately in Task 1 (no deprecated phase) — we fix every caller in the same PR.

---

## Task 1: Add `ScopeType` enum + `UserRoleAssignment` entity + `expectedScopeType()` on Role

**Goal:** Establish the new domain vocabulary while keeping the project compiling. Strategy: keep `Role.USER` **temporarily** (annotated `@Deprecated`, throws on `expectedScopeType()`) so existing callers still build. Tasks 2–8 migrate callers off `Role.USER`. Task 9 removes the deprecated entry.

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/users/domain/ScopeType.java`
- Create: `backend/src/main/java/com/luxpretty/app/users/domain/UserRoleAssignment.java`
- Create: `backend/src/test/java/com/luxpretty/app/users/domain/RoleTests.java`
- Modify: `backend/src/main/java/com/luxpretty/app/users/domain/Role.java`

### Steps

- [ ] **Step 1: Write the failing test for Role.expectedScopeType()**

Create `backend/src/test/java/com/luxpretty/app/users/domain/RoleTests.java`:

```java
package com.luxpretty.app.users.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleTests {

    @Test
    void pro_isTenantScoped() {
        assertThat(Role.PRO.expectedScopeType()).isEqualTo(ScopeType.TENANT);
    }

    @Test
    void employee_isTenantScoped() {
        assertThat(Role.EMPLOYEE.expectedScopeType()).isEqualTo(ScopeType.TENANT);
    }

    @Test
    void commercial_isGlobalScoped() {
        assertThat(Role.COMMERCIAL.expectedScopeType()).isEqualTo(ScopeType.GLOBAL);
    }

    @Test
    void admin_isGlobalScoped() {
        assertThat(Role.ADMIN.expectedScopeType()).isEqualTo(ScopeType.GLOBAL);
    }

    @Test
    void deprecatedUser_throwsOnExpectedScopeType() {
        assertThatThrownBy(() -> Role.USER.expectedScopeType())
            .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd backend && mvn test -Dtest=RoleTests
```

Expected: COMPILE FAIL on `ScopeType` (doesn't exist), `Role.COMMERCIAL` (doesn't exist), `Role.expectedScopeType()` (doesn't exist).

- [ ] **Step 3: Create `ScopeType` enum**

Create `backend/src/main/java/com/luxpretty/app/users/domain/ScopeType.java`:

```java
package com.luxpretty.app.users.domain;

public enum ScopeType {
    GLOBAL,
    TENANT
}
```

- [ ] **Step 4: Update `Role` enum**

Replace the contents of `backend/src/main/java/com/luxpretty/app/users/domain/Role.java`:

```java
package com.luxpretty.app.users.domain;

public enum Role {
    PRO,
    EMPLOYEE,
    COMMERCIAL,
    ADMIN,
    /** @deprecated remove in Task 9 once all callers migrated. */
    @Deprecated USER;

    public ScopeType expectedScopeType() {
        return switch (this) {
            case PRO, EMPLOYEE -> ScopeType.TENANT;
            case COMMERCIAL, ADMIN -> ScopeType.GLOBAL;
            case USER -> throw new IllegalStateException("USER is deprecated; absence of assignment = CLIENT implicite");
        };
    }
}
```

- [ ] **Step 5: Create `UserRoleAssignment` entity**

Create `backend/src/main/java/com/luxpretty/app/users/domain/UserRoleAssignment.java`:

```java
package com.luxpretty.app.users.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "USER_ROLE_ASSIGNMENTS", uniqueConstraints = {
        @UniqueConstraint(name = "UK_USER_ROLE_SCOPE",
                columnNames = {"user_id", "role", "scope_type", "scope_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 16)
    private ScopeType scopeType;

    /** Tenant id when scopeType=TENANT, null when scopeType=GLOBAL. */
    @Column(name = "scope_id")
    private Long scopeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 6: Run the tests and full compile**

```bash
cd backend && mvn test -Dtest=RoleTests
```

Expected: 5/5 green.

```bash
cd backend && mvn compile
```

Expected: BUILD SUCCESS (because `Role.USER` is still present as `@Deprecated`).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/users/domain/ScopeType.java \
        backend/src/main/java/com/luxpretty/app/users/domain/UserRoleAssignment.java \
        backend/src/main/java/com/luxpretty/app/users/domain/Role.java \
        backend/src/test/java/com/luxpretty/app/users/domain/RoleTests.java
git commit -m "feat(users): ScopeType + UserRoleAssignment entity + Role.COMMERCIAL"
```

---

## Task 2: Add `UserRoleAssignmentRepository`

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/users/repo/UserRoleAssignmentRepository.java`

### Steps

- [ ] **Step 1: Create the repository interface**

```java
package com.luxpretty.app.users.repo;

import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.ScopeType;
import com.luxpretty.app.users.domain.UserRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, Long> {

    List<UserRoleAssignment> findByUserId(Long userId);

    Optional<UserRoleAssignment> findByUserIdAndRoleAndScopeTypeAndScopeId(
            Long userId, Role role, ScopeType scopeType, Long scopeId);

    List<UserRoleAssignment> findByUserIdAndScopeType(Long userId, ScopeType scopeType);

    void deleteByUserIdAndRoleAndScopeTypeAndScopeId(
            Long userId, Role role, ScopeType scopeType, Long scopeId);
}
```

- [ ] **Step 2: Verify compile**

```bash
cd backend && mvn compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/users/repo/UserRoleAssignmentRepository.java
git commit -m "feat(users): UserRoleAssignmentRepository finders"
```

---

## Task 3: Implement `UserRoleService` (TDD, 10 tests)

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/users/app/UserRoleService.java`
- Create: `backend/src/test/java/com/luxpretty/app/users/app/UserRoleServiceTests.java`

### Steps

- [ ] **Step 1: Write the full failing test class**

Create `backend/src/test/java/com/luxpretty/app/users/app/UserRoleServiceTests.java`:

```java
package com.luxpretty.app.users.app;

import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.ScopeType;
import com.luxpretty.app.users.domain.UserRoleAssignment;
import com.luxpretty.app.users.repo.UserRoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserRoleServiceTests {

    private UserRoleAssignmentRepository repo;
    private UserRoleService service;

    @BeforeEach
    void setUp() {
        repo = mock(UserRoleAssignmentRepository.class);
        service = new UserRoleService(repo);
    }

    @Test
    void assign_createsAssignment_whenAbsent() {
        when(repo.findByUserIdAndRoleAndScopeTypeAndScopeId(1L, Role.PRO, ScopeType.TENANT, 42L))
                .thenReturn(Optional.empty());
        when(repo.save(any(UserRoleAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        UserRoleAssignment result = service.assign(1L, Role.PRO, ScopeType.TENANT, 42L);

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getRole()).isEqualTo(Role.PRO);
        assertThat(result.getScopeType()).isEqualTo(ScopeType.TENANT);
        assertThat(result.getScopeId()).isEqualTo(42L);
        verify(repo).save(any(UserRoleAssignment.class));
    }

    @Test
    void assign_isIdempotent_returnsExisting_whenPresent() {
        UserRoleAssignment existing = UserRoleAssignment.builder()
                .id(7L).userId(1L).role(Role.PRO).scopeType(ScopeType.TENANT).scopeId(42L).build();
        when(repo.findByUserIdAndRoleAndScopeTypeAndScopeId(1L, Role.PRO, ScopeType.TENANT, 42L))
                .thenReturn(Optional.of(existing));

        UserRoleAssignment result = service.assign(1L, Role.PRO, ScopeType.TENANT, 42L);

        assertThat(result).isSameAs(existing);
        verify(repo, never()).save(any());
    }

    @Test
    void assign_rejectsProWithGlobalScope() {
        assertThatThrownBy(() -> service.assign(1L, Role.PRO, ScopeType.GLOBAL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRO");
    }

    @Test
    void assign_rejectsAdminWithTenantScope() {
        assertThatThrownBy(() -> service.assign(1L, Role.ADMIN, ScopeType.TENANT, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ADMIN");
    }

    @Test
    void assign_rejectsTenantScopeWithoutScopeId() {
        assertThatThrownBy(() -> service.assign(1L, Role.PRO, ScopeType.TENANT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scopeId required");
    }

    @Test
    void assign_rejectsGlobalScopeWithScopeId() {
        assertThatThrownBy(() -> service.assign(1L, Role.ADMIN, ScopeType.GLOBAL, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be null");
    }

    @Test
    void resolveRoles_returnsGlobalRoles_whenActiveTenantNull() {
        when(repo.findByUserId(1L)).thenReturn(List.of(
                UserRoleAssignment.builder().userId(1L).role(Role.ADMIN).scopeType(ScopeType.GLOBAL).build(),
                UserRoleAssignment.builder().userId(1L).role(Role.PRO).scopeType(ScopeType.TENANT).scopeId(42L).build()
        ));

        Set<Role> roles = service.resolveRoles(1L, null);

        assertThat(roles).containsExactlyInAnyOrder(Role.ADMIN);
    }

    @Test
    void resolveRoles_returnsGlobalRolesPlusActiveTenantRoles() {
        when(repo.findByUserId(1L)).thenReturn(List.of(
                UserRoleAssignment.builder().userId(1L).role(Role.COMMERCIAL).scopeType(ScopeType.GLOBAL).build(),
                UserRoleAssignment.builder().userId(1L).role(Role.PRO).scopeType(ScopeType.TENANT).scopeId(42L).build(),
                UserRoleAssignment.builder().userId(1L).role(Role.EMPLOYEE).scopeType(ScopeType.TENANT).scopeId(42L).build()
        ));

        Set<Role> roles = service.resolveRoles(1L, 42L);

        assertThat(roles).containsExactlyInAnyOrder(Role.COMMERCIAL, Role.PRO, Role.EMPLOYEE);
    }

    @Test
    void resolveRoles_excludesOtherTenantRoles() {
        when(repo.findByUserId(1L)).thenReturn(List.of(
                UserRoleAssignment.builder().userId(1L).role(Role.PRO).scopeType(ScopeType.TENANT).scopeId(42L).build(),
                UserRoleAssignment.builder().userId(1L).role(Role.PRO).scopeType(ScopeType.TENANT).scopeId(43L).build()
        ));

        Set<Role> roles = service.resolveRoles(1L, 42L);

        assertThat(roles).containsExactly(Role.PRO);
        assertThat(roles).doesNotContain(Role.EMPLOYEE);
    }

    @Test
    void findUserTenantIds_returnsDistinctTenants() {
        when(repo.findByUserIdAndScopeType(1L, ScopeType.TENANT)).thenReturn(List.of(
                UserRoleAssignment.builder().userId(1L).role(Role.PRO).scopeType(ScopeType.TENANT).scopeId(42L).build(),
                UserRoleAssignment.builder().userId(1L).role(Role.EMPLOYEE).scopeType(ScopeType.TENANT).scopeId(42L).build(),
                UserRoleAssignment.builder().userId(1L).role(Role.PRO).scopeType(ScopeType.TENANT).scopeId(43L).build()
        ));

        List<Long> ids = service.findUserTenantIds(1L);

        assertThat(ids).containsExactlyInAnyOrder(42L, 43L);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd backend && mvn test -Dtest=UserRoleServiceTests
```

Expected: COMPILE FAIL — `UserRoleService` doesn't exist.

- [ ] **Step 3: Implement `UserRoleService`**

Create `backend/src/main/java/com/luxpretty/app/users/app/UserRoleService.java`:

```java
package com.luxpretty.app.users.app;

import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.ScopeType;
import com.luxpretty.app.users.domain.UserRoleAssignment;
import com.luxpretty.app.users.repo.UserRoleAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserRoleService {

    private final UserRoleAssignmentRepository repo;

    public UserRoleService(UserRoleAssignmentRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public UserRoleAssignment assign(Long userId, Role role, ScopeType scopeType, Long scopeId) {
        validateScope(role, scopeType, scopeId);
        return repo.findByUserIdAndRoleAndScopeTypeAndScopeId(userId, role, scopeType, scopeId)
                .orElseGet(() -> repo.save(UserRoleAssignment.builder()
                        .userId(userId)
                        .role(role)
                        .scopeType(scopeType)
                        .scopeId(scopeId)
                        .build()));
    }

    public UserRoleAssignment assignGlobal(Long userId, Role role) {
        return assign(userId, role, ScopeType.GLOBAL, null);
    }

    public UserRoleAssignment assignOnTenant(Long userId, Role role, Long tenantId) {
        return assign(userId, role, ScopeType.TENANT, tenantId);
    }

    @Transactional
    public void revoke(Long userId, Role role, ScopeType scopeType, Long scopeId) {
        repo.deleteByUserIdAndRoleAndScopeTypeAndScopeId(userId, role, scopeType, scopeId);
    }

    @Transactional(readOnly = true)
    public Set<Role> resolveRoles(Long userId, Long activeTenantId) {
        return repo.findByUserId(userId).stream()
                .filter(a -> a.getScopeType() == ScopeType.GLOBAL
                        || (a.getScopeType() == ScopeType.TENANT
                                && Objects.equals(a.getScopeId(), activeTenantId)))
                .map(UserRoleAssignment::getRole)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public List<Long> findUserTenantIds(Long userId) {
        return repo.findByUserIdAndScopeType(userId, ScopeType.TENANT).stream()
                .map(UserRoleAssignment::getScopeId)
                .distinct()
                .toList();
    }

    private void validateScope(Role role, ScopeType scopeType, Long scopeId) {
        if (role.expectedScopeType() != scopeType) {
            throw new IllegalArgumentException(
                    "Role " + role + " expects scope " + role.expectedScopeType()
                            + ", got " + scopeType);
        }
        if (scopeType == ScopeType.TENANT && scopeId == null) {
            throw new IllegalArgumentException("scopeId required for TENANT scope");
        }
        if (scopeType == ScopeType.GLOBAL && scopeId != null) {
            throw new IllegalArgumentException("scopeId must be null for GLOBAL scope");
        }
    }
}
```

- [ ] **Step 4: Run the test to verify all green**

```bash
cd backend && mvn test -Dtest=UserRoleServiceTests
```

Expected: 10/10 PASS.

- [ ] **Step 5: Run the full suite (sanity check, nothing else should regress)**

```bash
cd backend && mvn test
```

Expected: same green count as before this PR + 5 (RoleTests) + 10 (UserRoleServiceTests) = +15.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/users/app/UserRoleService.java \
        backend/src/test/java/com/luxpretty/app/users/app/UserRoleServiceTests.java
git commit -m "feat(users): UserRoleService — assign/revoke/resolve roles (10 tests)"
```

---

## Task 4: Flyway V10 migration + drop `User.role` field + fix immediate compile fallout

**Goal:** Atomic schema migration + Java model alignment. After this task, the project compiles and tests pass with `User` no longer having a `role` field. Code that read `user.getRole()` is migrated to either `userRoleService.resolveRoles(...)` or to using the Set<Role> from the security context.

This is the riskiest task — proceed in small increments.

**Files:**
- Create: `backend/src/main/resources/db/migration/oracle/V10__user_role_assignments.sql`
- Modify: `backend/src/main/java/com/luxpretty/app/users/domain/User.java` (remove `role` field)
- Modify: `backend/src/main/java/com/luxpretty/app/auth/AuthController.java` (don't `.role()` builder; assign via service)
- Modify: `backend/src/main/java/com/luxpretty/app/auth/CustomOAuth2UserService.java` (same)
- Modify: `backend/src/main/java/com/luxpretty/app/auth/dto/UserDto.java` (legacy `role` becomes derived String)
- Modify: `backend/src/main/java/com/luxpretty/app/config/CustomUserDetailsService.java`
- Modify: `backend/src/main/java/com/luxpretty/app/config/DataInitializer.java`
- Modify: `backend/src/main/java/com/luxpretty/app/test/SmokeTestSeedController.java`
- Modify: `backend/src/main/java/com/luxpretty/app/employee/app/EmployeeService.java` (drop `.role(Role.EMPLOYEE)` on User.builder)
- Modify: `backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java` (line 543, 565)
- Modify: `backend/src/main/java/com/luxpretty/app/tracking/app/TrackingService.java` (line 87)
- Modify: `backend/src/main/java/com/luxpretty/app/employee/app/LeaveRequestService.java` (line 160, 181)
- Modify: `backend/src/main/java/com/luxpretty/app/employee/app/EmployeePermissionService.java` (line 89)
- Modify: every test file with `.role(Role.XXX)` on User.builder (~10 files)

> Note: `TokenService`, `JwtAuthenticationFilter`, `OAuth2AuthenticationSuccessHandler` rely on `user.getRole().name()` — they will be properly rewritten in Tasks 5 + 6 + 7. For Task 4, give them a temporary path: pass an empty `Set<Role>` or use `userRoleService.resolveRoles(user.id, null)` for the token string. The cleanest approach: introduce the new TokenService signature in this task as a minimal shim (single-role string parameter `null`able) and let Tasks 5/6 wire the full claims.

### Steps

- [ ] **Step 1: Write the V10 migration**

Create `backend/src/main/resources/db/migration/oracle/V10__user_role_assignments.sql`:

```sql
-- V10: introduce USER_ROLE_ASSIGNMENTS junction, backfill PRO + ADMIN, drop USERS.ROLE.
--
-- EMPLOYEEs are NOT backfilled here because EMPLOYEES live in tenant schemas;
-- the Java EmployeeRoleBackfillRunner takes care of them at boot (Task 9).

-- 1. Table
CREATE TABLE USER_ROLE_ASSIGNMENTS (
    ID         NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    USER_ID    NUMBER(19) NOT NULL,
    ROLE       VARCHAR2(32 CHAR) NOT NULL,
    SCOPE_TYPE VARCHAR2(16 CHAR) NOT NULL,
    SCOPE_ID   NUMBER(19),
    CREATED_AT TIMESTAMP NOT NULL,
    CONSTRAINT FK_URA_USER FOREIGN KEY (USER_ID) REFERENCES USERS(ID),
    CONSTRAINT UK_USER_ROLE_SCOPE UNIQUE (USER_ID, ROLE, SCOPE_TYPE, SCOPE_ID),
    CONSTRAINT CK_URA_SCOPE_TYPE CHECK (SCOPE_TYPE IN ('GLOBAL', 'TENANT')),
    CONSTRAINT CK_URA_SCOPE_MATCH CHECK (
        (SCOPE_TYPE = 'TENANT' AND SCOPE_ID IS NOT NULL) OR
        (SCOPE_TYPE = 'GLOBAL' AND SCOPE_ID IS NULL)
    )
);

CREATE INDEX IX_URA_USER ON USER_ROLE_ASSIGNMENTS (USER_ID);
CREATE INDEX IX_URA_SCOPE ON USER_ROLE_ASSIGNMENTS (SCOPE_TYPE, SCOPE_ID);

-- 2. Backfill PRO@TENANT: one row per (user, owned tenant)
INSERT INTO USER_ROLE_ASSIGNMENTS (USER_ID, ROLE, SCOPE_TYPE, SCOPE_ID, CREATED_AT)
SELECT u.ID, 'PRO', 'TENANT', t.ID, CURRENT_TIMESTAMP
FROM USERS u
JOIN TENANTS t ON t.OWNER_ID = u.ID
WHERE u.ROLE = 'PRO';

-- 3. Backfill ADMIN@GLOBAL
INSERT INTO USER_ROLE_ASSIGNMENTS (USER_ID, ROLE, SCOPE_TYPE, SCOPE_ID, CREATED_AT)
SELECT ID, 'ADMIN', 'GLOBAL', NULL, CURRENT_TIMESTAMP
FROM USERS WHERE ROLE = 'ADMIN';

-- 4. Drop the CK_USERS_ROLE constraint (created by V2) before dropping the column.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'USERS' AND CONSTRAINT_NAME = 'CK_USERS_ROLE';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE USERS DROP CONSTRAINT CK_USERS_ROLE';
    END IF;
END;
/

-- 5. Drop the USERS.ROLE column
ALTER TABLE USERS SET UNUSED COLUMN ROLE;
ALTER TABLE USERS DROP UNUSED COLUMNS;
```

- [ ] **Step 2: Remove `role` from `User` entity**

In `backend/src/main/java/com/luxpretty/app/users/domain/User.java`, delete lines 70-73:

```java
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "role", nullable = false)
    private Role role = Role.USER;
```

Then delete the unused import: `import com.luxpretty.app.users.domain.Role;` if it appears (it's in the same package — already not imported).

- [ ] **Step 3: Update `UserDto` (legacy role becomes a derived String)**

Replace `backend/src/main/java/com/luxpretty/app/auth/dto/UserDto.java`:

```java
package com.luxpretty.app.auth.dto;

import com.luxpretty.app.users.domain.AuthProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private String imageUrl;
    private AuthProvider provider;
    /** Legacy single-role field for backwards compat with the current frontend.
     *  Populated as the highest-priority role from {@link #roles} (ADMIN > COMMERCIAL > PRO > EMPLOYEE).
     *  Frontend (PR2) will migrate to {@link #roles}. */
    private String role;
    private List<String> roles;
}
```

- [ ] **Step 4: Add a helper to TokenService (single new method)**

This is a *shim* to get the project compiling. The full TokenService refactor lands in Task 5.

In `backend/src/main/java/com/luxpretty/app/auth/TokenService.java`, add (do NOT remove the existing methods yet):

```java
    public String generateToken(Long userId, String email, java.util.List<String> roles, Long activeTenantId) {
        java.util.Date now = new java.util.Date();
        java.util.Date expiryDate = new java.util.Date(now.getTime() + tokenExpirationMs);

        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("email", email)
            .claim("roles", roles)
            .claim("activeTenantId", activeTenantId)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(getSigningKey())
            .compact();
    }
```

Keep the 3-arg `generateToken(Long, String, String)` for now — it's still used by `OAuth2AuthenticationSuccessHandler` and a few tests. Task 5 will remove it.

- [ ] **Step 5: Migrate AuthController to UserRoleService**

In `backend/src/main/java/com/luxpretty/app/auth/AuthController.java`:

a. Add field + constructor injection of `UserRoleService`:

```java
    private final com.luxpretty.app.users.app.UserRoleService userRoleService;
```

Update the constructor parameter list and assignment.

b. In `registerWithRole(...)`:

Replace the User.builder block (lines 90-98) — remove `.role(role)`:

```java
        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .consentGivenAt(LocalDateTime.now())
                .build();
```

After `User savedUser = userRepository.save(user);`:

```java
        Long activeTenantId = null;
        if (provisionTenant) {
            var tenant = tenantProvisioningService.provision(savedUser);
            // TenantProvisioningService now assigns PRO + EMPLOYEE (Task 7)
            activeTenantId = tenant.getId();
        }
        // For /register/client we add no assignment → CLIENT implicite.
```

Replace token generation:

```java
        java.util.Set<com.luxpretty.app.users.domain.Role> resolved =
                userRoleService.resolveRoles(savedUser.getId(), activeTenantId);
        java.util.List<String> roleNames = resolved.stream().map(Enum::name).toList();
        String legacyRole = pickLegacyRole(resolved);
        String token = tokenService.generateToken(savedUser.getId(), savedUser.getEmail(), roleNames, activeTenantId);

        UserDto userDto = UserDto.builder()
                .id(savedUser.getId())
                .name(savedUser.getName())
                .email(savedUser.getEmail())
                .imageUrl(savedUser.getImageUrl())
                .provider(savedUser.getProvider())
                .role(legacyRole)
                .roles(roleNames)
                .build();

        return ResponseEntity.ok(new AuthResponse(token, userDto));
```

c. Add the private helper at the bottom of the class:

```java
    private static String pickLegacyRole(java.util.Set<com.luxpretty.app.users.domain.Role> roles) {
        if (roles.contains(com.luxpretty.app.users.domain.Role.ADMIN)) return "ADMIN";
        if (roles.contains(com.luxpretty.app.users.domain.Role.COMMERCIAL)) return "COMMERCIAL";
        if (roles.contains(com.luxpretty.app.users.domain.Role.PRO)) return "PRO";
        if (roles.contains(com.luxpretty.app.users.domain.Role.EMPLOYEE)) return "EMPLOYEE";
        return null;
    }
```

d. Apply the same pattern to `registerProWithSalonInfo` and `login` and `getCurrentUser`. The `login` path passes `null` for `activeTenantId` here — Task 5 makes TokenService resolve "first tenant of the user" automatically; for Task 4 we keep it simple:

In `login(...)`:

```java
        Long activeTenantId = userRoleService.findUserTenantIds(user.getId()).stream().findFirst().orElse(null);
        java.util.Set<com.luxpretty.app.users.domain.Role> resolved =
                userRoleService.resolveRoles(user.getId(), activeTenantId);
        java.util.List<String> roleNames = resolved.stream().map(Enum::name).toList();
        String legacyRole = pickLegacyRole(resolved);
        String token = tokenService.generateToken(user.getId(), user.getEmail(), roleNames, activeTenantId);

        UserDto userDto = UserDto.builder()
                .id(user.getId()).name(user.getName()).email(user.getEmail())
                .imageUrl(user.getImageUrl()).provider(user.getProvider())
                .role(legacyRole).roles(roleNames).build();
```

In `getCurrentUser`:

```java
        Long activeTenantId = userRoleService.findUserTenantIds(user.getId()).stream().findFirst().orElse(null);
        java.util.Set<com.luxpretty.app.users.domain.Role> resolved =
                userRoleService.resolveRoles(user.getId(), activeTenantId);
        java.util.List<String> roleNames = resolved.stream().map(Enum::name).toList();
        String legacyRole = pickLegacyRole(resolved);
        UserDto userDto = UserDto.builder()
                .id(user.getId()).name(user.getName()).email(user.getEmail())
                .imageUrl(user.getImageUrl()).provider(user.getProvider())
                .role(legacyRole).roles(roleNames).build();
```

- [ ] **Step 6: Migrate CustomOAuth2UserService**

In `backend/src/main/java/com/luxpretty/app/auth/CustomOAuth2UserService.java`:

a. Inject `UserRoleService`:

```java
    private final com.luxpretty.app.users.app.UserRoleService userRoleService;
```

Update constructor.

b. In `createNewUser(...)`, drop `.role(role)` from the builder:

```java
    private User createNewUser(AuthProvider provider, OAuth2UserInfo oAuth2UserInfo) {
        return User.builder()
            .name(oAuth2UserInfo.getName())
            .email(oAuth2UserInfo.getEmail())
            .imageUrl(oAuth2UserInfo.getImageUrl())
            .provider(provider)
            .providerId(oAuth2UserInfo.getId())
            .emailVerified(true)
            .build();
    }
```

c. In `processOAuth2User(...)`, after `userRepository.save(newUser)` and the optional `tenantProvisioningService.provision(savedUser)` block, the PRO assignment is now handled inside `TenantProvisioningService` (Task 7). For non-pro signups, nothing to add (CLIENT implicite).

Update the call site:

```java
        User newUser = createNewUser(provider, oAuth2UserInfo);
        User savedUser = userRepository.save(newUser);

        if (isPro) {
            boolean tenantExists = tenantRepository.findByOwnerId(savedUser.getId()).isPresent();
            if (!tenantExists) {
                logger.info("Provisioning tenant for new OAuth2 user: {}", savedUser.getEmail());
                tenantProvisioningService.provision(savedUser);
            }
        }
        // else: CLIENT implicite, no assignment row needed.
        return savedUser;
```

Remove the now-unused `Role role = isPro ? ... : ...` and `Role` import if no longer used.

- [ ] **Step 7: Migrate OAuth2AuthenticationSuccessHandler**

In `backend/src/main/java/com/luxpretty/app/auth/OAuth2AuthenticationSuccessHandler.java:64`, replace:

```java
String token = tokenService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
```

with:

```java
java.util.List<Long> tenantIds = userRoleService.findUserTenantIds(user.getId());
Long activeTenantId = tenantIds.stream().findFirst().orElse(null);
java.util.Set<com.luxpretty.app.users.domain.Role> resolved =
        userRoleService.resolveRoles(user.getId(), activeTenantId);
java.util.List<String> roleNames = resolved.stream().map(Enum::name).toList();
String token = tokenService.generateToken(user.getId(), user.getEmail(), roleNames, activeTenantId);
```

Inject `UserRoleService` in the handler (add field + constructor arg).

- [ ] **Step 8: Migrate JwtAuthenticationFilter**

In `backend/src/main/java/com/luxpretty/app/auth/JwtAuthenticationFilter.java:46`, replace:

```java
var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
```

with (temporary shim — Task 6 finishes the migration):

```java
java.util.Set<com.luxpretty.app.users.domain.Role> resolved =
        userRoleService.resolveRoles(user.getId(), null); // null = global roles only; Task 6 will read activeTenantId from JWT
var authorities = resolved.stream()
        .map(r -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.name()))
        .toList();
```

Inject `UserRoleService`:

```java
    private final com.luxpretty.app.users.app.UserRoleService userRoleService;

    public JwtAuthenticationFilter(TokenService tokenService, UserRepository userRepository,
                                   com.luxpretty.app.users.app.UserRoleService userRoleService) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.userRoleService = userRoleService;
    }
```

Then update `SecurityConfig` where the filter is built — find `new JwtAuthenticationFilter(...)` and pass `userRoleService`. Look in `backend/src/main/java/com/luxpretty/app/config/SecurityConfig.java`; inject `UserRoleService` there and add it to the constructor call.

- [ ] **Step 9: Migrate CustomUserDetailsService**

In `backend/src/main/java/com/luxpretty/app/config/CustomUserDetailsService.java:30`, replace:

```java
.authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
```

with:

```java
.authorities(userRoleService.resolveRoles(user.getId(), null).stream()
        .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r.name()))
        .toList())
```

Inject `UserRoleService` into the class. Note: this path is for Basic Auth (dev only); accepting global-roles-only here is fine because no tenant is in scope.

- [ ] **Step 10: Migrate DataInitializer**

In `backend/src/main/java/com/luxpretty/app/config/DataInitializer.java`:

a. Drop `Role` arg from `createUserIfMissing` (or wherever the builder is). For each `.role(Role.XXX)` line, remove it.

b. After the User is created and saved, call `userRoleService.assignGlobal(user.getId(), Role.ADMIN)` for the admin user. For PROs, `tenantProvisioningService.provision(user)` already creates PRO + EMPLOYEE assignments (after Task 7). For the `Role.USER` test clients, do nothing (CLIENT implicite).

Inspect the file's helper method signatures and adjust. Concretely:

- Admin: after save, call `userRoleService.assignGlobal(savedAdmin.getId(), Role.ADMIN);`
- Pros (Sophie, Camille, Isabelle): tenant provisioning handles assignment — drop any other role-related code.
- Clients (Marie, Julie, Clara): nothing.

Inject `UserRoleService` into `DataInitializer`.

- [ ] **Step 11: Migrate SmokeTestSeedController**

In `backend/src/main/java/com/luxpretty/app/test/SmokeTestSeedController.java`:

- Line 115: drop `.role(Role.PRO)` from builder; instead, after save, call `userRoleService.assignOnTenant(pro.getId(), Role.PRO, tenant.getId())` once the tenant exists. If a tenant is provisioned via `tenantProvisioningService`, the assignment is already created — verify the flow and skip the redundant call.
- Line 133: drop `.role(Role.USER)` — CLIENT implicite.
- Lines 194-195: same token issue. Refactor to:

```java
java.util.List<String> proRoles = userRoleService.resolveRoles(pro.getId(),
        userRoleService.findUserTenantIds(pro.getId()).stream().findFirst().orElse(null))
        .stream().map(Enum::name).toList();
Long proActiveTenantId = userRoleService.findUserTenantIds(pro.getId()).stream().findFirst().orElse(null);
String proToken = tokenService.generateToken(pro.getId(), pro.getEmail(), proRoles, proActiveTenantId);

java.util.List<String> clientRoles = java.util.List.of(); // CLIENT implicite
String clientToken = tokenService.generateToken(client.getId(), client.getEmail(), clientRoles, null);
```

Inject `UserRoleService` into the controller.

- [ ] **Step 12: Migrate EmployeeService — drop `.role(Role.EMPLOYEE)` on User builder**

In `backend/src/main/java/com/luxpretty/app/employee/app/EmployeeService.java:126`, remove `.role(Role.EMPLOYEE)` from the User builder block. Drop the unused `Role` import if no longer needed.

The actual EMPLOYEE assignment for that User is added at Task 7. For Task 4, the goal is just to compile — leaving the User saved without an assignment is acceptable because Task 7 adds the assignment in the same method.

- [ ] **Step 13: Migrate role checks in business services**

In each of these files, replace `if (role == Role.PRO || role == Role.ADMIN)` with reading the resolved roles from the security context. Concretely, the methods that read `role` get the user's ID and call `userRoleService.resolveRoles(userId, currentTenantId)`:

For each of the 4 files below, inject `UserRoleService`, find each `role ==` check, and change to use a `Set<Role>` resolved from the JWT context (the principal carries the user id). The current tenant comes from `TenantContext.getCurrentTenant()` → resolve to tenant id via `TenantRepository.findBySlug(...)`.

a. `backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java:543` and `:565`:

Around the existing check, the caller has access to a user/role parameter (look at the method signature). If `role` is currently a method parameter (e.g., `Role role`), change the call sites and propagate a `Set<Role> roles` instead. If `role` is computed inside via `principal.getRole()` or similar, inject `UserRoleService` and resolve. Verify the exact current signature; then:

```java
// Before:
if (role == Role.PRO || role == Role.ADMIN) { ... }
// After:
if (roles.contains(Role.PRO) || roles.contains(Role.ADMIN)) { ... }
```

b. `backend/src/main/java/com/luxpretty/app/tracking/app/TrackingService.java:87`: same pattern.

c. `backend/src/main/java/com/luxpretty/app/employee/app/LeaveRequestService.java:160` and `:181`: same.

d. `backend/src/main/java/com/luxpretty/app/employee/app/EmployeePermissionService.java:89`: same.

For each of these, the existing signature is the source of truth — adjust callers to pass `Set<Role> roles` instead of `Role role`. Where a single check is inside the service and it currently fetches the role from `User.getRole()`, replace with `userRoleService.resolveRoles(userId, tenantId)`.

- [ ] **Step 14: Migrate the existing test files that use `.role(Role.XXX)`**

For every test file with `.role(Role.USER)`, `.role(Role.PRO)`, `.role(Role.EMPLOYEE)`, or `.role(Role.ADMIN)` on a `User.builder()`, delete that line. If the test then needs the user to have a role at runtime, add a `userRoleService` mock (or wire in the bean for integration tests) to return the expected roles.

Files to edit (already located in pre-plan grep):

- `backend/src/test/java/com/luxpretty/app/auth/AuthControllerTests.java` (lines 82, 170, 199, 219, 243, 269, 300, 320, 339, 360, 384, 423, 446)
- `backend/src/test/java/com/luxpretty/app/auth/CustomOAuth2UserServiceTests.java` (lines 163, 172, 193, 204, 218, 246) — also lines 172 and 204 assert `getRole()` returns a value; replace those assertions with assertions on `userRoleService.assign(...)` having been called.
- `backend/src/test/java/com/luxpretty/app/auth/OAuth2AuthenticationSuccessHandlerTests.java` (lines 60, 119)
- `backend/src/test/java/com/luxpretty/app/tracking/app/TrackingServiceTests.java` (lines 78, 154, 191)
- `backend/src/test/java/com/luxpretty/app/tracking/app/TrackingAccessLevelSecurityTests.java` (line 96)
- `backend/src/test/java/com/luxpretty/app/bookings/app/CareBookingServiceTests.java` (lines 1100, 1158)
- `backend/src/test/java/com/luxpretty/app/bookings/app/BookingServiceLimitsIntegrationTests.java` (line 51)
- `backend/src/test/java/com/luxpretty/app/bookings/integration/CareBookingCancelRebookIntegrationTests.java` (lines 98, 109)
- `backend/src/test/java/com/luxpretty/app/bookings/integration/CareBookingConcurrencyIntegrationTests.java` (lines 109, 120)
- `backend/src/test/java/com/luxpretty/app/employee/app/EmployeeServiceTests.java` (line 140 — assertion to migrate)

For `EmployeeServiceTests.java:140` (`assertThat(savedUser.getRole()).isEqualTo(Role.EMPLOYEE)`):

Replace with:

```java
verify(userRoleService).assignOnTenant(eq(savedUser.getId()), eq(Role.EMPLOYEE), any());
```

(after wiring a mock `UserRoleService` into the test setup — see Task 7).

- [ ] **Step 15: Compile**

```bash
cd backend && mvn compile
```

Expected: BUILD SUCCESS. If anything still references `user.getRole()` or `User.builder()....role(...)`, grep and fix:

```bash
grep -rn "\.getRole()\|\.role(Role\." backend/src/main/java backend/src/test/java
```

Expected output: zero lines (other than `Role.java` itself).

- [ ] **Step 16: Run the full test suite**

```bash
cd backend && mvn test
```

Expected: all green. If integration tests that bring up the full Spring context fail because `userRoleService` isn't autowired in some place, add it. If a test mocks `userRepository` and tries to set `user.role`, that's the test from Step 14 — adjust.

- [ ] **Step 17: Manual smoke (local DB, optional but recommended)**

```bash
# Bring up Oracle locally
docker compose --profile dev up -d oracle-db

# Run backend
cd backend && mvn spring-boot:run
```

Open `http://localhost:8080/actuator/health` — should be UP. Connect via SQL client and check:

```sql
SELECT COUNT(*) FROM USER_ROLE_ASSIGNMENTS;
-- Should be ≥ 1 (1 row per pro tenant owner) on dev seed data

SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'USERS' AND COLUMN_NAME = 'ROLE';
-- Should return zero rows (column dropped)
```

Stop the backend.

- [ ] **Step 18: Commit**

```bash
git add backend/src/main/resources/db/migration/oracle/V10__user_role_assignments.sql \
        backend/src/main/java/com/luxpretty/app/users/domain/User.java \
        backend/src/main/java/com/luxpretty/app/auth/AuthController.java \
        backend/src/main/java/com/luxpretty/app/auth/CustomOAuth2UserService.java \
        backend/src/main/java/com/luxpretty/app/auth/OAuth2AuthenticationSuccessHandler.java \
        backend/src/main/java/com/luxpretty/app/auth/JwtAuthenticationFilter.java \
        backend/src/main/java/com/luxpretty/app/auth/TokenService.java \
        backend/src/main/java/com/luxpretty/app/auth/dto/UserDto.java \
        backend/src/main/java/com/luxpretty/app/config/CustomUserDetailsService.java \
        backend/src/main/java/com/luxpretty/app/config/SecurityConfig.java \
        backend/src/main/java/com/luxpretty/app/config/DataInitializer.java \
        backend/src/main/java/com/luxpretty/app/test/SmokeTestSeedController.java \
        backend/src/main/java/com/luxpretty/app/employee/app/EmployeeService.java \
        backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java \
        backend/src/main/java/com/luxpretty/app/tracking/app/TrackingService.java \
        backend/src/main/java/com/luxpretty/app/employee/app/LeaveRequestService.java \
        backend/src/main/java/com/luxpretty/app/employee/app/EmployeePermissionService.java \
        backend/src/test/java/com/luxpretty/app/
git commit -m "feat(users): drop User.role + V10 migration to USER_ROLE_ASSIGNMENTS"
```

---

## Task 5: TokenService refactor (TDD, 6 tests)

**Goal:** Promote the shim added in Task 4 into the canonical API. TokenService now resolves roles and the active tenant itself; callers pass only the user. Old 3-arg signatures are removed.

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/TokenService.java`
- Modify: existing test file `backend/src/test/java/com/luxpretty/app/auth/TokenServiceTests.java` (or create if missing)
- Modify: every caller of the old 3-arg `generateToken(Long, String, String)` (mostly tests + `OAuth2AuthenticationSuccessHandler` if Task 4 didn't already migrate it)

### Steps

- [ ] **Step 1: Check whether `TokenServiceTests.java` exists**

```bash
ls backend/src/test/java/com/luxpretty/app/auth/TokenServiceTests.java 2>/dev/null
```

If absent, create it with the test skeleton below. If present, append the new tests.

- [ ] **Step 2: Write the failing tests**

In `backend/src/test/java/com/luxpretty/app/auth/TokenServiceTests.java`:

```java
package com.luxpretty.app.auth;

import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.jsonwebtoken.security.Keys.hmacShaKeyFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenServiceTests {

    private static final String SECRET = "test-secret-must-be-at-least-32-bytes-long-12345";
    private TokenService service;
    private UserRoleService userRoleService;
    private TenantRepository tenantRepository;

    @BeforeEach
    void setUp() {
        userRoleService = mock(UserRoleService.class);
        tenantRepository = mock(TenantRepository.class);
        service = new TokenService(userRoleService, tenantRepository);
        ReflectionTestUtils.setField(service, "tokenSecret", SECRET);
        ReflectionTestUtils.setField(service, "tokenExpirationMs", 3600_000L);
    }

    private Claims parse(String token) {
        SecretKey key = hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    @Test
    void generateToken_includesRolesClaim() {
        User user = User.builder().id(1L).email("a@a.com").build();
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of(42L));
        when(userRoleService.resolveRoles(1L, 42L)).thenReturn(Set.of(Role.PRO, Role.EMPLOYEE));
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(tenant(42L, "salon-x", "Salon X")));

        String token = service.generateToken(user);

        Claims c = parse(token);
        assertThat(c.get("roles", List.class)).containsExactlyInAnyOrder("PRO", "EMPLOYEE");
    }

    @Test
    void generateToken_includesActiveTenantIdClaim() {
        User user = User.builder().id(1L).email("a@a.com").build();
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of(42L));
        when(userRoleService.resolveRoles(1L, 42L)).thenReturn(Set.of(Role.PRO));
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(tenant(42L, "salon-x", "Salon X")));

        String token = service.generateToken(user);

        Claims c = parse(token);
        assertThat(c.get("activeTenantId", Long.class)).isEqualTo(42L);
    }

    @Test
    void generateToken_includesAvailableTenantsClaim() {
        User user = User.builder().id(1L).email("a@a.com").build();
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of(42L, 43L));
        when(userRoleService.resolveRoles(1L, 42L)).thenReturn(Set.of(Role.PRO));
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(tenant(42L, "salon-x", "Salon X")));
        when(tenantRepository.findById(43L)).thenReturn(Optional.of(tenant(43L, "salon-y", "Salon Y")));

        String token = service.generateToken(user);

        Claims c = parse(token);
        List<?> tenants = c.get("availableTenants", List.class);
        assertThat(tenants).hasSize(2);
    }

    @Test
    void generateToken_chooseFirstTenant_whenUserHasNoActiveYet() {
        User user = User.builder().id(1L).email("a@a.com").build();
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of(99L));
        when(userRoleService.resolveRoles(1L, 99L)).thenReturn(Set.of(Role.PRO));
        when(tenantRepository.findById(99L)).thenReturn(Optional.of(tenant(99L, "first", "First")));

        String token = service.generateToken(user);

        assertThat(parse(token).get("activeTenantId", Long.class)).isEqualTo(99L);
    }

    @Test
    void generateToken_returnsEmptyRoles_forClientWithNoAssignments() {
        User user = User.builder().id(2L).email("client@a.com").build();
        when(userRoleService.findUserTenantIds(2L)).thenReturn(List.of());
        when(userRoleService.resolveRoles(2L, null)).thenReturn(Set.of());

        String token = service.generateToken(user);

        Claims c = parse(token);
        assertThat(c.get("roles", List.class)).isEmpty();
        assertThat(c.get("activeTenantId", Long.class)).isNull();
    }

    @Test
    void generateToken_overrideActiveTenant_returnsRolesForThatTenant() {
        User user = User.builder().id(1L).email("a@a.com").build();
        when(userRoleService.resolveRoles(1L, 43L)).thenReturn(Set.of(Role.PRO));
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of(42L, 43L));
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(tenant(42L, "salon-x", "Salon X")));
        when(tenantRepository.findById(43L)).thenReturn(Optional.of(tenant(43L, "salon-y", "Salon Y")));

        String token = service.generateToken(user, 43L);

        assertThat(parse(token).get("activeTenantId", Long.class)).isEqualTo(43L);
        assertThat(parse(token).get("roles", List.class)).containsExactly("PRO");
    }

    private static Tenant tenant(Long id, String slug, String name) {
        Tenant t = new Tenant();
        t.setId(id);
        t.setSlug(slug);
        t.setName(name);
        return t;
    }
}
```

- [ ] **Step 3: Run, expect fail**

```bash
cd backend && mvn test -Dtest=TokenServiceTests
```

Expected: COMPILE FAIL (TokenService constructor signature mismatch) or RED.

- [ ] **Step 4: Refactor TokenService**

Replace `backend/src/main/java/com/luxpretty/app/auth/TokenService.java`:

```java
package com.luxpretty.app.auth;

import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TokenService {
    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    @Value("${app.auth.token.secret}")
    private String tokenSecret;

    @Value("${app.auth.token.expiration-ms}")
    private long tokenExpirationMs;

    private final UserRoleService userRoleService;
    private final TenantRepository tenantRepository;

    public TokenService(UserRoleService userRoleService, TenantRepository tenantRepository) {
        this.userRoleService = userRoleService;
        this.tenantRepository = tenantRepository;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(tokenSecret.getBytes(StandardCharsets.UTF_8));
    }

    /** Generate a token, picking the user's first tenant as active. */
    public String generateToken(User user) {
        Long activeTenantId = userRoleService.findUserTenantIds(user.getId())
                .stream().findFirst().orElse(null);
        return generateToken(user, activeTenantId);
    }

    /** Generate a token with an explicit active tenant id (used by switch-tenant). */
    public String generateToken(User user, Long activeTenantId) {
        Set<Role> resolved = userRoleService.resolveRoles(user.getId(), activeTenantId);
        List<String> roleNames = resolved.stream().map(Enum::name).toList();
        List<Map<String, Object>> availableTenants = userRoleService.findUserTenantIds(user.getId()).stream()
                .map(tenantRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(this::toTenantSummaryMap)
                .toList();

        Date now = new Date();
        Date expiry = new Date(now.getTime() + tokenExpirationMs);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("roles", roleNames)
                .claim("activeTenantId", activeTenantId)
                .claim("availableTenants", availableTenants)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    private Map<String, Object> toTenantSummaryMap(Tenant t) {
        return Map.of(
                "id", t.getId(),
                "slug", t.getSlug() == null ? "" : t.getSlug(),
                "name", t.getName() == null ? "" : t.getName()
        );
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(parse(token).getSubject());
    }

    public Long getActiveTenantIdFromToken(String token) {
        Object value = parse(token).get("activeTenantId");
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Object value = parse(token).get("roles");
        if (value == null) return List.of();
        return (List<String>) value;
    }

    public boolean validateToken(String authToken) {
        try {
            parse(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            logger.warn("Invalid JWT: {}", ex.getMessage());
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

The old 3-arg and 4-arg methods are gone — callers go through `generateToken(User)` or `generateToken(User, Long)`.

- [ ] **Step 5: Update every caller of removed signatures**

Find them:

```bash
grep -rn "tokenService\.generateToken" backend/src
```

For each caller:

- `AuthController.registerWithRole` / `registerProWithSalonInfo` / `login`: change to `tokenService.generateToken(savedUser)` or `tokenService.generateToken(savedUser, tenant.getId())` for the pro register paths where we want the just-provisioned tenant as active immediately.
- `OAuth2AuthenticationSuccessHandler:64`: change to `tokenService.generateToken(user)`.
- `SmokeTestSeedController:194-195`: change to `tokenService.generateToken(pro)` / `tokenService.generateToken(client)`.

Simplify the AuthController helpers — we no longer manually build `roleNames` / `pickLegacyRole` for the token. We still need them for `UserDto`. Refactor:

```java
private static String pickLegacyRole(java.util.Set<Role> roles) { /* unchanged */ }
```

And in each endpoint, after generating the token, also build the UserDto using `userRoleService.resolveRoles(...)` directly. The token generation is now opaque from the controller's perspective.

- [ ] **Step 6: Run the failing tests**

```bash
cd backend && mvn test -Dtest=TokenServiceTests
```

Expected: 6/6 PASS.

- [ ] **Step 7: Run the full suite**

```bash
cd backend && mvn test
```

Expected: all green.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/auth/TokenService.java \
        backend/src/test/java/com/luxpretty/app/auth/TokenServiceTests.java \
        backend/src/main/java/com/luxpretty/app/auth/AuthController.java \
        backend/src/main/java/com/luxpretty/app/auth/OAuth2AuthenticationSuccessHandler.java \
        backend/src/main/java/com/luxpretty/app/test/SmokeTestSeedController.java
git commit -m "feat(auth): TokenService emits roles + activeTenantId + availableTenants (6 tests)"
```

---

## Task 6: JwtAuthenticationFilter refactor (TDD, 4 tests)

**Goal:** The filter now reads `roles` from the JWT (not from the database) and sets `TenantContext` from `activeTenantId`. Authorities are derived from the JWT directly, which makes `@PreAuthorize("hasRole('PRO')")` consistent with the user's selected tenant.

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/JwtAuthenticationFilter.java`
- Create or modify: `backend/src/test/java/com/luxpretty/app/auth/JwtAuthenticationFilterTests.java`

### Steps

- [ ] **Step 1: Check whether `JwtAuthenticationFilterTests.java` exists**

```bash
ls backend/src/test/java/com/luxpretty/app/auth/JwtAuthenticationFilterTests.java 2>/dev/null
```

- [ ] **Step 2: Write the failing tests**

In `backend/src/test/java/com/luxpretty/app/auth/JwtAuthenticationFilterTests.java`:

```java
package com.luxpretty.app.auth;

import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTests {

    private TokenService tokenService;
    private UserRepository userRepository;
    private TenantRepository tenantRepository;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenService = mock(TokenService.class);
        userRepository = mock(UserRepository.class);
        tenantRepository = mock(TenantRepository.class);
        filter = new JwtAuthenticationFilter(tokenService, userRepository, tenantRepository);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void filter_setsMultipleAuthorities_fromRolesClaim() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer x.y.z");
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(tokenService.validateToken("x.y.z")).thenReturn(true);
        when(tokenService.getUserIdFromToken("x.y.z")).thenReturn(1L);
        when(tokenService.getActiveTenantIdFromToken("x.y.z")).thenReturn(null);
        when(tokenService.getRolesFromToken("x.y.z")).thenReturn(List.of("PRO", "COMMERCIAL"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).email("a@a.com").build()));

        filter.doFilter(req, res, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_PRO", "ROLE_COMMERCIAL");
    }

    @Test
    void filter_setsTenantContext_whenActiveTenantIdPresent() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer x.y.z");
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(tokenService.validateToken("x.y.z")).thenReturn(true);
        when(tokenService.getUserIdFromToken("x.y.z")).thenReturn(1L);
        when(tokenService.getActiveTenantIdFromToken("x.y.z")).thenReturn(42L);
        when(tokenService.getRolesFromToken("x.y.z")).thenReturn(List.of("PRO"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).email("a@a.com").build()));
        Tenant t = new Tenant();
        t.setId(42L);
        t.setSlug("salon-x");
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(t));

        ArgumentCaptor<String> tenantInChain = ArgumentCaptor.forClass(String.class);
        doAnswer(inv -> {
            tenantInChain.getAllValues().add(TenantContext.getCurrentTenant());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(req, res, chain);

        assertThat(tenantInChain.getAllValues()).containsExactly("salon-x");
    }

    @Test
    void filter_clearsTenantContext_inFinally() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer x.y.z");
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(tokenService.validateToken("x.y.z")).thenReturn(true);
        when(tokenService.getUserIdFromToken("x.y.z")).thenReturn(1L);
        when(tokenService.getActiveTenantIdFromToken("x.y.z")).thenReturn(42L);
        when(tokenService.getRolesFromToken("x.y.z")).thenReturn(List.of("PRO"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).email("a@a.com").build()));
        Tenant t = new Tenant();
        t.setId(42L);
        t.setSlug("salon-x");
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(t));

        filter.doFilter(req, res, chain);

        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void filter_skipsTenantContext_whenActiveTenantIdNull() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer x.y.z");
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(tokenService.validateToken("x.y.z")).thenReturn(true);
        when(tokenService.getUserIdFromToken("x.y.z")).thenReturn(1L);
        when(tokenService.getActiveTenantIdFromToken("x.y.z")).thenReturn(null);
        when(tokenService.getRolesFromToken("x.y.z")).thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).email("client@a.com").build()));

        ArgumentCaptor<String> tenantInChain = ArgumentCaptor.forClass(String.class);
        doAnswer(inv -> {
            tenantInChain.getAllValues().add(TenantContext.getCurrentTenant());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(req, res, chain);

        assertThat(tenantInChain.getAllValues()).containsExactly((String) null);
        verify(tenantRepository, never()).findById(any());
    }
}
```

- [ ] **Step 3: Run, expect fail**

```bash
cd backend && mvn test -Dtest=JwtAuthenticationFilterTests
```

Expected: COMPILE FAIL (constructor signature, methods that don't exist on TokenService yet were added in Task 5).

- [ ] **Step 4: Refactor the filter**

Replace `backend/src/main/java/com/luxpretty/app/auth/JwtAuthenticationFilter.java`:

```java
package com.luxpretty.app.auth;

import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public JwtAuthenticationFilter(TokenService tokenService,
                                   UserRepository userRepository,
                                   TenantRepository tenantRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean tenantContextSet = false;
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenService.validateToken(jwt)) {
                Long userId = tokenService.getUserIdFromToken(jwt);

                User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

                UserPrincipal userPrincipal = UserPrincipal.create(user);
                List<String> roleNames = tokenService.getRolesFromToken(jwt);
                List<GrantedAuthority> authorities = roleNames.stream()
                        .map(n -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + n))
                        .toList();
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userPrincipal,
                    null,
                    authorities
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                Long activeTenantId = tokenService.getActiveTenantIdFromToken(jwt);
                if (activeTenantId != null) {
                    tenantRepository.findById(activeTenantId).ifPresent(t -> {
                        TenantContext.setCurrentTenant(t.getSlug());
                    });
                    tenantContextSet = TenantContext.getCurrentTenant() != null;
                }
            }

            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
            filterChain.doFilter(request, response);
        } finally {
            if (tenantContextSet) {
                TenantContext.clear();
            }
        }
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

> Important: the filter previously did NOT touch `TenantContext`. Public endpoints `/api/salon/{slug}/**` rely on `TenantFilter` to set the slug from the URL. We must not let the JWT-driven setter conflict with the URL-driven one for those public endpoints. Check `TenantFilter` ordering and ensure the URL-based filter wins for public endpoints. Concretely, the public endpoint sets the slug from the URL; for `/api/pro/**` and `/api/me/**`, the JWT sets it. The two paths are disjoint, so they won't clash. If there's ambiguity, prefer the URL-driven path: skip the JWT-driven setter when `TenantContext.getCurrentTenant()` is already non-null at filter entry.

Adjust the filter to be defensive:

```java
                Long activeTenantId = tokenService.getActiveTenantIdFromToken(jwt);
                if (activeTenantId != null && TenantContext.getCurrentTenant() == null) {
                    tenantRepository.findById(activeTenantId).ifPresent(t -> {
                        TenantContext.setCurrentTenant(t.getSlug());
                    });
                    tenantContextSet = TenantContext.getCurrentTenant() != null;
                }
```

- [ ] **Step 5: Update `SecurityConfig` to pass `TenantRepository`**

Find `new JwtAuthenticationFilter(...)` in `backend/src/main/java/com/luxpretty/app/config/SecurityConfig.java` and adjust. Also undo the temporary `UserRoleService` injection added in Task 4 (no longer needed by the filter — it reads roles from the JWT now).

```java
new JwtAuthenticationFilter(tokenService, userRepository, tenantRepository)
```

Inject `TenantRepository` into `SecurityConfig`.

- [ ] **Step 6: Run the tests**

```bash
cd backend && mvn test -Dtest=JwtAuthenticationFilterTests
```

Expected: 4/4 PASS.

- [ ] **Step 7: Run the full suite**

```bash
cd backend && mvn test
```

Expected: all green.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/auth/JwtAuthenticationFilter.java \
        backend/src/test/java/com/luxpretty/app/auth/JwtAuthenticationFilterTests.java \
        backend/src/main/java/com/luxpretty/app/config/SecurityConfig.java
git commit -m "feat(auth): JwtAuthenticationFilter parses roles + sets TenantContext from JWT (4 tests)"
```

---

## Task 7: Update TenantProvisioningService + EmployeeService (assignments)

**Goal:** Bake the role assignments into the provisioning and employee-creation flows so that any new pro signup or employee invite ends with the right `USER_ROLE_ASSIGNMENTS` rows.

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/tenant/app/TenantProvisioningService.java`
- Modify: `backend/src/main/java/com/luxpretty/app/employee/app/EmployeeService.java`
- Modify: tests (`TenantProvisioningServiceTests`, `EmployeeServiceTests`)

### Steps

- [ ] **Step 1: Write the failing test for TenantProvisioningService**

In `backend/src/test/java/com/luxpretty/app/tenant/app/TenantProvisioningServiceTests.java` (or create if missing), add:

```java
@Test
void provision_assignsProAndEmployeeRoles() {
    User owner = User.builder().id(1L).name("Sophie").email("sophie@a.com").build();
    when(tenantRepository.existsBySlug("sophie")).thenReturn(false);
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
        Tenant t = inv.getArgument(0);
        t.setId(42L);
        return t;
    });

    Tenant result = service.provision(owner);

    verify(userRoleService).assignOnTenant(1L, Role.PRO, 42L);
    verify(userRoleService).assignOnTenant(1L, Role.EMPLOYEE, 42L);
}
```

Adjust imports + the test's existing setup to mock `UserRoleService`. Verify the test file's existing mocks and add `UserRoleService` to the constructor call.

- [ ] **Step 2: Run, expect fail**

```bash
cd backend && mvn test -Dtest=TenantProvisioningServiceTests
```

Expected: COMPILE FAIL (`userRoleService` not a field of `TenantProvisioningService` yet) or RED.

- [ ] **Step 3: Modify TenantProvisioningService**

In `backend/src/main/java/com/luxpretty/app/tenant/app/TenantProvisioningService.java`:

a. Add field + constructor injection of `UserRoleService`:

```java
    private final com.luxpretty.app.users.app.UserRoleService userRoleService;
```

Update constructor signature.

b. In `provision(...)`, after `Tenant saved = tenantRepository.save(tenant);`:

```java
        // Assign PRO + EMPLOYEE roles on this new tenant for the owner.
        userRoleService.assignOnTenant(owner.getId(), com.luxpretty.app.users.domain.Role.PRO, saved.getId());
        userRoleService.assignOnTenant(owner.getId(), com.luxpretty.app.users.domain.Role.EMPLOYEE, saved.getId());
```

(Place these BEFORE the `TenantContext.setCurrentTenant(slug)` block to keep the assignments visible regardless of the tenant context.)

- [ ] **Step 4: Write the failing test for EmployeeService.create**

In `backend/src/test/java/com/luxpretty/app/employee/app/EmployeeServiceTests.java`, find the existing `create` test (around line 140). Add a new test or modify the existing one to assert the assignment:

```java
@Test
void create_assignsEmployeeRoleToUser() {
    TenantContext.setCurrentTenant("salon-x");
    try {
        Tenant currentTenant = new Tenant();
        currentTenant.setId(42L);
        currentTenant.setSlug("salon-x");
        when(tenantRepository.findBySlug("salon-x")).thenReturn(Optional.of(currentTenant));
        // ... arrange the rest of the test (CreateEmployeeRequest, user save, employee save)

        EmployeeResponse response = service.create(new CreateEmployeeRequest(...));

        verify(userRoleService).assignOnTenant(any(Long.class), eq(Role.EMPLOYEE), eq(42L));
    } finally {
        TenantContext.clear();
    }
}
```

> Concrete adjustment: the existing test at line 140 asserts `savedUser.getRole() == Role.EMPLOYEE`. Replace with the verify above. Also, the existing service test must inject the new `UserRoleService` mock and `TenantRepository` mock into the service constructor.

Also delete the obsolete assertion at line 140 (`assertThat(savedUser.getRole()).isEqualTo(Role.EMPLOYEE)`).

- [ ] **Step 5: Run, expect fail**

```bash
cd backend && mvn test -Dtest=EmployeeServiceTests
```

Expected: COMPILE FAIL.

- [ ] **Step 6: Modify EmployeeService**

In `backend/src/main/java/com/luxpretty/app/employee/app/EmployeeService.java`:

a. Inject `UserRoleService` and `TenantRepository`:

```java
    private final com.luxpretty.app.users.app.UserRoleService userRoleService;
    private final com.luxpretty.app.tenant.repo.TenantRepository tenantRepository;
```

Update the constructor.

b. In `create(...)`, after `Employee saved = employeeRepository.save(employee);` and BEFORE returning, add:

```java
        com.luxpretty.app.tenant.domain.Tenant tenant = tenantRepository.findBySlug(tenantSlug)
                .orElseThrow(() -> new IllegalStateException("Tenant not found for slug " + tenantSlug));
        userRoleService.assignOnTenant(savedUser.getId(), com.luxpretty.app.users.domain.Role.EMPLOYEE, tenant.getId());
```

c. In `createSelfEmployee(User owner)`, after the employee is created (only when newly created, i.e. inside the `orElseGet` branch), the assignment is already handled by `TenantProvisioningService.provision()`. So no change needed here — but verify by reading the method.

Actually, `createSelfEmployee` is called from `TenantProvisioningService` AFTER `userRoleService.assignOnTenant(..., Role.EMPLOYEE, ...)` runs (in our new code in Step 3). So we don't need a second assignment.

But: `createSelfEmployee` is idempotent (`findByUserId(...).orElseGet(...)`), so a future caller could create the self-employee outside of `provision()`. Defensive: in `createSelfEmployee`, also call `userRoleService.assignOnTenant(owner.getId(), Role.EMPLOYEE, tenant.getId())` (it's idempotent in the service). Need the tenant id:

```java
@Transactional
public Employee createSelfEmployee(User owner) {
    String slug = TenantContext.requireActive();
    Tenant tenant = tenantRepository.findBySlug(slug)
            .orElseThrow(() -> new IllegalStateException("Tenant not found for slug " + slug));

    Employee employee = employeeRepository.findByUserId(owner.getId())
            .orElseGet(() -> {
                Employee e = new Employee();
                e.setUserId(owner.getId());
                e.setName(owner.getName());
                e.setEmail(owner.getEmail());
                e.setPhone(null);
                e.setActive(true);
                return employeeRepository.save(e);
            });

    // Ensure EMPLOYEE assignment exists for the owner (idempotent).
    userRoleService.assignOnTenant(owner.getId(), com.luxpretty.app.users.domain.Role.EMPLOYEE, tenant.getId());
    return employee;
}
```

- [ ] **Step 7: Run both failing tests, watch them pass**

```bash
cd backend && mvn test -Dtest=TenantProvisioningServiceTests,EmployeeServiceTests
```

Expected: all PASS (including the 2 new ones).

- [ ] **Step 8: Run the full suite**

```bash
cd backend && mvn test
```

Expected: all green.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/tenant/app/TenantProvisioningService.java \
        backend/src/main/java/com/luxpretty/app/employee/app/EmployeeService.java \
        backend/src/test/java/com/luxpretty/app/tenant/app/TenantProvisioningServiceTests.java \
        backend/src/test/java/com/luxpretty/app/employee/app/EmployeeServiceTests.java
git commit -m "feat(auth): register/provisioning flows now create UserRoleAssignment"
```

---

## Task 8: MeController — /api/me/tenants + /api/me/switch-tenant (TDD, 4 tests)

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/me/web/MeController.java`
- Create: `backend/src/main/java/com/luxpretty/app/me/web/dto/TenantSummary.java`
- Create: `backend/src/main/java/com/luxpretty/app/me/web/dto/SwitchTenantRequest.java`
- Create: `backend/src/test/java/com/luxpretty/app/me/web/MeControllerTests.java`

### Steps

- [ ] **Step 1: Create the DTO records**

`backend/src/main/java/com/luxpretty/app/me/web/dto/TenantSummary.java`:

```java
package com.luxpretty.app.me.web.dto;

public record TenantSummary(Long id, String slug, String name) {}
```

`backend/src/main/java/com/luxpretty/app/me/web/dto/SwitchTenantRequest.java`:

```java
package com.luxpretty.app.me.web.dto;

import jakarta.validation.constraints.NotNull;

public record SwitchTenantRequest(@NotNull Long tenantId) {}
```

- [ ] **Step 2: Write the failing test**

Create `backend/src/test/java/com/luxpretty/app/me/web/MeControllerTests.java`:

```java
package com.luxpretty.app.me.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeController.class)
class MeControllerTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockBean UserRoleService userRoleService;
    @MockBean TenantRepository tenantRepository;
    @MockBean UserRepository userRepository;
    @MockBean TokenService tokenService;

    private UserPrincipal principal(Long id) {
        User u = User.builder().id(id).email("a@a.com").name("A").build();
        return UserPrincipal.create(u);
    }

    @Test
    void myTenants_returnsTenantSummaries_forUserWithAssignments() throws Exception {
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of(42L, 43L));
        Tenant t1 = new Tenant(); t1.setId(42L); t1.setSlug("salon-x"); t1.setName("Salon X");
        Tenant t2 = new Tenant(); t2.setId(43L); t2.setSlug("salon-y"); t2.setName("Salon Y");
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(t1));
        when(tenantRepository.findById(43L)).thenReturn(Optional.of(t2));

        mvc.perform(MockMvcRequestBuilders.get("/api/me/tenants").with(user(principal(1L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(42))
            .andExpect(jsonPath("$[0].slug").value("salon-x"))
            .andExpect(jsonPath("$[1].id").value(43));
    }

    @Test
    void myTenants_returnsEmpty_forClientWithoutAssignments() throws Exception {
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of());

        mvc.perform(MockMvcRequestBuilders.get("/api/me/tenants").with(user(principal(1L))))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void switchTenant_returnsNewToken_whenUserHasAssignment() throws Exception {
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of(42L));
        User u = User.builder().id(1L).email("a@a.com").name("A").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(tokenService.generateToken(any(User.class), eq(42L))).thenReturn("new.jwt.token");

        mvc.perform(MockMvcRequestBuilders.post("/api/me/switch-tenant")
                .with(user(principal(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\": 42}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("new.jwt.token"));
    }

    @Test
    void switchTenant_rejectsWith403_whenUserHasNoAssignment() throws Exception {
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of(42L));

        mvc.perform(MockMvcRequestBuilders.post("/api/me/switch-tenant")
                .with(user(principal(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\": 999}"))
            .andExpect(status().isForbidden());
    }
}
```

Use `import static org.mockito.ArgumentMatchers.eq;` (add to the imports).

- [ ] **Step 3: Run, expect fail**

```bash
cd backend && mvn test -Dtest=MeControllerTests
```

Expected: COMPILE FAIL (controller doesn't exist).

- [ ] **Step 4: Implement MeController**

Create `backend/src/main/java/com/luxpretty/app/me/web/MeController.java`:

```java
package com.luxpretty.app.me.web;

import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.auth.dto.AuthResponse;
import com.luxpretty.app.auth.dto.UserDto;
import com.luxpretty.app.me.web.dto.SwitchTenantRequest;
import com.luxpretty.app.me.web.dto.TenantSummary;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRoleService userRoleService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;

    public MeController(UserRoleService userRoleService,
                        TenantRepository tenantRepository,
                        UserRepository userRepository,
                        TokenService tokenService) {
        this.userRoleService = userRoleService;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    @GetMapping("/tenants")
    public List<TenantSummary> myTenants(@AuthenticationPrincipal UserPrincipal principal) {
        return userRoleService.findUserTenantIds(principal.getId()).stream()
                .map(tenantRepository::findById)
                .flatMap(Optional::stream)
                .map(t -> new TenantSummary(t.getId(), t.getSlug(), t.getName()))
                .toList();
    }

    @PostMapping("/switch-tenant")
    public ResponseEntity<AuthResponse> switchTenant(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SwitchTenantRequest req) {

        List<Long> allowed = userRoleService.findUserTenantIds(principal.getId());
        if (!allowed.contains(req.tenantId())) {
            throw new AccessDeniedException("User has no role on tenant " + req.tenantId());
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("User missing"));
        String newToken = tokenService.generateToken(user, req.tenantId());

        Set<Role> resolved = userRoleService.resolveRoles(user.getId(), req.tenantId());
        List<String> roleNames = resolved.stream().map(Enum::name).toList();
        String legacyRole = pickLegacyRole(resolved);

        UserDto dto = UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .imageUrl(user.getImageUrl())
                .provider(user.getProvider())
                .role(legacyRole)
                .roles(roleNames)
                .build();

        return ResponseEntity.ok(new AuthResponse(newToken, dto));
    }

    private static String pickLegacyRole(Set<Role> roles) {
        if (roles.contains(Role.ADMIN)) return "ADMIN";
        if (roles.contains(Role.COMMERCIAL)) return "COMMERCIAL";
        if (roles.contains(Role.PRO)) return "PRO";
        if (roles.contains(Role.EMPLOYEE)) return "EMPLOYEE";
        return null;
    }
}
```

- [ ] **Step 5: Run the tests**

```bash
cd backend && mvn test -Dtest=MeControllerTests
```

Expected: 4/4 PASS. If `switchTenant` returns 500 instead of 403, check `SecurityConfig` — `AccessDeniedException` must propagate as 403. If it's caught by a global handler, adjust the controller to return `ResponseEntity.status(HttpStatus.FORBIDDEN).build()` explicitly. (Inspect `backend/src/main/java/com/luxpretty/app/common/error/` for any `@ExceptionHandler`.)

- [ ] **Step 6: Ensure `/api/me/**` is protected in SecurityConfig**

Open `backend/src/main/java/com/luxpretty/app/config/SecurityConfig.java`. Find the authorization rules block. Make sure `/api/me/**` requires authentication (any authenticated user). If the existing config is `requestMatchers("/api/**").authenticated()`, it's already covered. If there's an explicit allowlist for unauthenticated endpoints, ensure `/api/me/**` is NOT in it.

- [ ] **Step 7: Run the full suite**

```bash
cd backend && mvn test
```

Expected: all green.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/me/ \
        backend/src/test/java/com/luxpretty/app/me/ \
        backend/src/main/java/com/luxpretty/app/config/SecurityConfig.java
git commit -m "feat(me): GET /api/me/tenants + POST /api/me/switch-tenant (4 tests)"
```

---

## Task 9: EmployeeRoleBackfillRunner + drop deprecated `Role.USER`

**Goal:** One-shot idempotent runner that ensures every existing `EMPLOYEES.USER_ID` has a matching `USER_ROLE_ASSIGNMENTS(EMPLOYEE, TENANT, tenant.id)` row. Then remove the `@Deprecated Role.USER` and final cleanup.

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/multitenancy/EmployeeRoleBackfillRunner.java`
- Modify: `backend/src/main/java/com/luxpretty/app/users/domain/Role.java` (drop `USER`)
- Possibly modify: leftover test references to `Role.USER`

### Steps

- [ ] **Step 1: Create the runner**

Create `backend/src/main/java/com/luxpretty/app/multitenancy/EmployeeRoleBackfillRunner.java`:

```java
package com.luxpretty.app.multitenancy;

import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.users.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Backfill EMPLOYEE role assignments for users that have an Employee row in
 * any tenant schema but no matching assignment in USER_ROLE_ASSIGNMENTS yet.
 * Idempotent: re-running has no effect (UserRoleService.assignOnTenant is
 * idempotent at the DB level via UK_USER_ROLE_SCOPE).
 */
@Configuration
public class EmployeeRoleBackfillRunner {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeRoleBackfillRunner.class);

    @Bean
    ApplicationRunner backfillEmployeeRoles(
            TenantRepository tenantRepository,
            EmployeeRepository employeeRepository,
            UserRoleService userRoleService) {

        return args -> {
            int total = 0;
            for (Tenant tenant : tenantRepository.findAll()) {
                TenantContext.setCurrentTenant(tenant.getSlug());
                try {
                    for (Employee e : employeeRepository.findAll()) {
                        userRoleService.assignOnTenant(e.getUserId(), Role.EMPLOYEE, tenant.getId());
                        total++;
                    }
                } catch (Exception ex) {
                    logger.warn("Backfill failed for tenant {} ({}): {}", tenant.getId(), tenant.getSlug(), ex.getMessage());
                } finally {
                    TenantContext.clear();
                }
            }
            logger.info("EmployeeRoleBackfillRunner: ensured {} EMPLOYEE assignments", total);
        };
    }
}
```

- [ ] **Step 2: Manual smoke (local DB)**

```bash
cd backend && mvn spring-boot:run
```

Watch logs for `EmployeeRoleBackfillRunner: ensured N EMPLOYEE assignments`.

Restart and confirm the same N (idempotent).

Stop the backend.

- [ ] **Step 3: Drop `Role.USER`**

Edit `backend/src/main/java/com/luxpretty/app/users/domain/Role.java`:

```java
package com.luxpretty.app.users.domain;

public enum Role {
    PRO,
    EMPLOYEE,
    COMMERCIAL,
    ADMIN;

    public ScopeType expectedScopeType() {
        return switch (this) {
            case PRO, EMPLOYEE -> ScopeType.TENANT;
            case COMMERCIAL, ADMIN -> ScopeType.GLOBAL;
        };
    }
}
```

Update `RoleTests.java`: delete the `deprecatedUser_throwsOnExpectedScopeType` test.

- [ ] **Step 4: Find and fix any leftover Role.USER**

```bash
grep -rn "Role\.USER" backend/src
```

Expected: zero hits. If hits remain, fix them now — likely a test that wasn't touched in Task 4.

- [ ] **Step 5: Compile + run full suite**

```bash
cd backend && mvn test
```

Expected: all green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/multitenancy/EmployeeRoleBackfillRunner.java \
        backend/src/main/java/com/luxpretty/app/users/domain/Role.java \
        backend/src/test/java/com/luxpretty/app/users/domain/RoleTests.java
git commit -m "feat(users): EmployeeRoleBackfillRunner + drop Role.USER"
```

---

## Post-implementation verification

### 1. Test count

Before this PR: ~596 tests (per spec). After: +15 (Tasks 1, 3) +6 (Task 5) +4 (Task 6) +2 (Task 7) +4 (Task 8) = +31. Expected total: ~627.

Run:

```bash
cd backend && mvn test
```

### 2. Manual smoke (local DB)

```bash
cd backend && mvn spring-boot:run
```

In a SQL client:

```sql
SELECT COUNT(*) FROM USER_ROLE_ASSIGNMENTS;        -- ≥ 1
SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME='USERS' AND COLUMN_NAME='ROLE';  -- 0 rows
```

With a logged-in pro user:

```bash
curl -H "Authorization: Bearer <jwt>" http://localhost:8080/api/me/tenants
# Expected: [{"id":42,"slug":"...","name":"..."}]

curl -X POST -H "Authorization: Bearer <jwt>" -H "Content-Type: application/json" \
  -d '{"tenantId": 42}' \
  http://localhost:8080/api/me/switch-tenant
# Expected: {"accessToken":"<new>", "user":{...}}

curl -X POST -H "Authorization: Bearer <jwt>" -H "Content-Type: application/json" \
  -d '{"tenantId": 999}' \
  http://localhost:8080/api/me/switch-tenant
# Expected: 403
```

### 3. Frontend compatibility (non-blocking)

Login flow returns `AuthResponse` with legacy `role` populated. The frontend continues to work without changes. The PR2 (frontend) will switch to `roles[]` + `availableTenants` + `activeTenantId`.

### 4. Rollback plan

If something breaks in prod:
1. Revert the merge commit
2. Restore `USERS.ROLE` column from DB backup (V10 dropped it)
3. Re-run with the prior JAR

Mitigation: stage on dev/staging Oracle first with realistic seed data before merging to main.

---

## Open follow-ups (out of scope for this PR)

- **PR2 frontend**: tenant selector, switch UI, replace `user.role === 'PRO'` with `roles.includes('PRO')`, `authService.hasRole()` helper, remove the legacy `role` field from `UserDto`.
- **COMMERCIAL UI**: this PR introduces the role; the commercial workspace is a separate effort.
- **`/api/me/switch-tenant` audit log**: not in this PR; nice-to-have for security review.
