# Q1 PR1 — Multi-Role Scoped RBAC (Backend)

> **Document unique** combinant architecture, spec, TDD tests et tickets découpés pour exécution efficace. Le frontend (PR2) aura son propre document.

**Date :** 2026-05-11
**Pattern adopté :** Scoped RBAC (équivalent Google Cloud IAM / GitHub repo permissions)
**Scope :** Backend uniquement. Pas de changement frontend dans cette PR.
**Effort estimé :** 4-5 jours en 9 tickets indépendants.

---

## 0. Architecture cible

### 0.1 Vision

Tout user de LuxPretty est implicitement **CLIENT** (peut réserver chez n'importe quel salon). En plus, il peut cumuler des rôles **scopés** :
- `PRO` sur un tenant (= il gère ce salon)
- `EMPLOYEE` sur un tenant (= il travaille dans ce salon)
- `COMMERCIAL` au scope GLOBAL (= il vend LuxPretty)
- `ADMIN` au scope GLOBAL (= il administre la plateforme)

Un user peut avoir plusieurs `PRO@TENANT` simultanément (chaines de salons, satellite). Le JWT porte un `activeTenantId` qui détermine quel salon est "actif" pour la session. Switch de tenant via `POST /api/me/switch-tenant` → re-issue le JWT.

### 0.2 Comparaison rapide avec les patterns industrie

Le pattern choisi (Scoped RBAC) est utilisé par :
- **Google Cloud IAM** : `roles/owner` sur `project Y` (équivalent : `PRO@tenant-X`)
- **GitHub** : `admin` sur `repo A`, `read` sur `repo B` pour un même user
- **Atlassian Jira** : "Administrator on PROJ-A, Developer on PROJ-B"
- **Slack** : rôles différents par workspace

Pattern A flat (single Set<Role>) aurait été plus simple mais ferme la porte aux chaines de salons. Pattern B (role+permission) ajouterait une granularité qu'on ne sait pas exploiter encore.

### 0.3 Schéma de données

```
USERS (existing)               USER_ROLE_ASSIGNMENTS (new)
+----+------------+            +----+---------+---------+------------+----------+------------+
| id | email      |            | id | user_id | role    | scope_type | scope_id | created_at |
+----+------------+            +----+---------+---------+------------+----------+------------+
| 1  | sophie@x   |            | 1  | 1       | PRO     | TENANT     | 42       | 2026-...   |
| 2  | bob@x      |            | 2  | 1       | PRO     | TENANT     | 43       | 2026-...   |
| 3  | alice@x    |            | 3  | 2       | EMPLOYEE| TENANT     | 42       | 2026-...   |
+----+------------+            | 4  | 1       | COMMERCIAL | GLOBAL  | NULL     | 2026-...   |
                               | 5  | 3       | ADMIN   | GLOBAL     | NULL     | 2026-...   |
                               +----+---------+---------+------------+----------+------------+

USER 1 (Sophie) : PRO de tenant 42, PRO de tenant 43, COMMERCIAL LuxPretty, et CLIENT implicite partout
USER 2 (Bob)    : EMPLOYEE de tenant 42, et CLIENT implicite partout
USER 3 (Alice)  : ADMIN LuxPretty, et CLIENT implicite partout
```

**Invariants enforced par le service** (`UserRoleService`) :
- `PRO` et `EMPLOYEE` : SCOPE_TYPE = TENANT obligatoire, SCOPE_ID non-null
- `COMMERCIAL` et `ADMIN` : SCOPE_TYPE = GLOBAL obligatoire, SCOPE_ID null
- Pas de doublon (UK_USER_ROLE_SCOPE prévient au niveau DB)

### 0.4 Flow JWT et tenant actif

```
[Login]
   ↓
TokenService.generateToken(user)
   ↓
1. Lit USER_ROLE_ASSIGNMENTS pour user
2. Si user a ≥1 PRO@TENANT → activeTenantId = celui par défaut (le 1er trouvé)
   Sinon activeTenantId = null
3. Resolved roles = roles GLOBAL (COMMERCIAL/ADMIN) + roles TENANT@activeTenantId
4. JWT claims :
   - sub = user.id
   - roles = ["PRO", "COMMERCIAL"]      // resolved pour activeTenantId
   - activeTenantId = 42
   - availableTenants = [{id:42, slug:"sophie-paris"}, {id:43, slug:"sophie-lyon"}]

[Subsequent requests]
   ↓
JwtAuthenticationFilter
   ↓
1. Spring Security authorities = [ROLE_PRO, ROLE_COMMERCIAL]
2. TenantContext.setCurrentTenant(activeTenantId)
3. @PreAuthorize("hasRole('PRO')") fonctionne sans changement
4. Services qui lisent TenantContext continuent de fonctionner

[POST /api/me/switch-tenant {tenantId: 43}]
   ↓
1. Vérifie que user a un assignment sur tenant 43
2. Re-issue le JWT avec activeTenantId=43 et roles resolved pour 43
3. Renvoie le nouveau token
```

### 0.5 Fichiers touchés (récap)

```
backend/src/main/java/com/luxpretty/app/
├── users/
│   ├── domain/
│   │   ├── User.java                       [MODIFY] supprime role field
│   │   ├── Role.java                       [MODIFY] supprime USER, ajoute COMMERCIAL
│   │   ├── UserRoleAssignment.java         [NEW]    entité junction
│   │   └── ScopeType.java                  [NEW]    enum GLOBAL | TENANT
│   ├── repo/
│   │   └── UserRoleAssignmentRepository.java [NEW]
│   └── app/
│       └── UserRoleService.java            [NEW]    assign/revoke/resolve
├── auth/
│   ├── TokenService.java                   [MODIFY] roles list + activeTenantId
│   ├── JwtAuthenticationFilter.java        [MODIFY] parse roles + set TenantContext
│   ├── AuthController.java                 [MODIFY] register flow → assignment
│   └── CustomOAuth2UserService.java        [MODIFY] OAuth flow → assignment
├── me/web/
│   └── MeController.java                   [NEW]    /api/me/tenants, /api/me/switch-tenant
├── tenant/app/
│   └── TenantProvisioningService.java      [MODIFY] crée assignment au lieu de set role
├── employee/app/
│   └── EmployeeService.java                [MODIFY] crée assignment EMPLOYEE au create
└── multitenancy/
    └── EmployeeRoleBackfillRunner.java     [NEW]    one-shot runner pour legacy EMPLOYEEs

backend/src/main/resources/db/migration/oracle/
└── V8__user_role_assignments.sql           [NEW]    DDL + backfill USER/PRO/ADMIN
```

---

## 1. Spec détaillée

### 1.1 Entité `UserRoleAssignment`

```java
package com.luxpretty.app.users.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "USER_ROLE_ASSIGNMENTS", uniqueConstraints = {
    @UniqueConstraint(name = "UK_USER_ROLE_SCOPE",
        columnNames = {"user_id", "role", "scope_type", "scope_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
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

### 1.2 Enum `ScopeType`

```java
package com.luxpretty.app.users.domain;

public enum ScopeType {
    GLOBAL,  // platform-wide role (ADMIN, COMMERCIAL)
    TENANT   // role bound to a specific tenant (PRO, EMPLOYEE)
}
```

### 1.3 Enum `Role` — refactor

```java
package com.luxpretty.app.users.domain;

public enum Role {
    PRO,         // SCOPE_TYPE = TENANT
    EMPLOYEE,    // SCOPE_TYPE = TENANT
    COMMERCIAL,  // SCOPE_TYPE = GLOBAL
    ADMIN;       // SCOPE_TYPE = GLOBAL

    public ScopeType expectedScopeType() {
        return switch (this) {
            case PRO, EMPLOYEE -> ScopeType.TENANT;
            case COMMERCIAL, ADMIN -> ScopeType.GLOBAL;
        };
    }
}
```

**BREAKING :** `Role.USER` est supprimé. Tout code qui le référence doit migrer vers "absence d'assignment = CLIENT implicite".

### 1.4 Repository

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

    /** Tenant ids where the user holds at least one TENANT-scoped role. */
    List<UserRoleAssignment> findByUserIdAndScopeType(Long userId, ScopeType scopeType);

    void deleteByUserIdAndRoleAndScopeTypeAndScopeId(
        Long userId, Role role, ScopeType scopeType, Long scopeId);
}
```

### 1.5 Service métier — `UserRoleService`

```java
package com.luxpretty.app.users.app;

import com.luxpretty.app.users.domain.*;
import com.luxpretty.app.users.repo.UserRoleAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class UserRoleService {

    private final UserRoleAssignmentRepository repo;

    public UserRoleService(UserRoleAssignmentRepository repo) {
        this.repo = repo;
    }

    /**
     * Assign a role to a user. Idempotent: if the assignment already exists,
     * returns the existing one.
     *
     * @throws IllegalArgumentException if scope doesn't match role.expectedScopeType()
     */
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

    /** Convenience: assign a GLOBAL role (ADMIN/COMMERCIAL). */
    public UserRoleAssignment assignGlobal(Long userId, Role role) {
        return assign(userId, role, ScopeType.GLOBAL, null);
    }

    /** Convenience: assign a TENANT-scoped role (PRO/EMPLOYEE). */
    public UserRoleAssignment assignOnTenant(Long userId, Role role, Long tenantId) {
        return assign(userId, role, ScopeType.TENANT, tenantId);
    }

    @Transactional
    public void revoke(Long userId, Role role, ScopeType scopeType, Long scopeId) {
        repo.deleteByUserIdAndRoleAndScopeTypeAndScopeId(userId, role, scopeType, scopeId);
    }

    /**
     * Resolve the effective roles for a user when operating on a specific tenant.
     * Returns: all GLOBAL roles + all TENANT roles matching this tenantId.
     *
     * @param tenantId may be null (= user has no active tenant context, returns
     *                 only GLOBAL roles)
     */
    @Transactional(readOnly = true)
    public Set<Role> resolveRoles(Long userId, Long activeTenantId) {
        return repo.findByUserId(userId).stream()
            .filter(a -> a.getScopeType() == ScopeType.GLOBAL
                      || (a.getScopeType() == ScopeType.TENANT
                          && Objects.equals(a.getScopeId(), activeTenantId)))
            .map(UserRoleAssignment::getRole)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** Tenant ids where the user has any TENANT-scoped role. */
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

### 1.6 TokenService — adaptation

```java
// Pseudocode of the key change:

public String generateToken(User user) {
    Long activeTenantId = userRoleService.findUserTenantIds(user.getId())
        .stream().findFirst().orElse(null);
    return generateToken(user, activeTenantId);
}

public String generateToken(User user, Long activeTenantId) {
    Set<Role> roles = userRoleService.resolveRoles(user.getId(), activeTenantId);
    List<TenantSummary> availableTenants = tenantSummaryService.forUser(user.getId());

    return Jwts.builder()
        .setSubject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("roles", roles.stream().map(Role::name).toList())
        .claim("activeTenantId", activeTenantId)
        .claim("availableTenants", availableTenants)  // [{id, slug, name}]
        .setIssuedAt(...).setExpiration(...)
        .signWith(...).compact();
}
```

### 1.7 JwtAuthenticationFilter — adaptation

```java
// Pseudocode:

@Override
protected void doFilterInternal(...) {
    // Parse JWT...
    List<String> roleNames = claims.get("roles", List.class);
    Long activeTenantId = claims.get("activeTenantId", Long.class);

    var authorities = roleNames.stream()
        .map(name -> new SimpleGrantedAuthority("ROLE_" + name))
        .toList();

    var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
    SecurityContextHolder.getContext().setAuthentication(auth);

    // NEW: set the tenant context from the JWT for the entire request lifecycle.
    if (activeTenantId != null) {
        Tenant tenant = tenantRepository.findById(activeTenantId).orElse(null);
        if (tenant != null) {
            TenantContext.setCurrentTenant(tenant.getSlug());
        }
    }

    try {
        filterChain.doFilter(request, response);
    } finally {
        TenantContext.clear();  // always clean up
    }
}
```

**Note importante** : aujourd'hui, `TenantContext` est défini par le `slug` de l'URL pour les endpoints publics `/api/salon/{slug}/**`. Pour les endpoints pro `/api/pro/**`, on va maintenant le déterminer via le JWT. C'est cohérent et évite que les requêtes pro doivent porter le slug.

### 1.8 MeController — nouveau

```java
package com.luxpretty.app.me.web;

import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.auth.dto.AuthResponse;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.tenant.domain.Tenant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    public record TenantSummary(Long id, String slug, String name) {}
    public record SwitchTenantRequest(Long tenantId) {}

    @GetMapping("/tenants")
    public List<TenantSummary> myTenants(@AuthenticationPrincipal UserPrincipal principal) {
        return userRoleService.findUserTenantIds(principal.getId()).stream()
            .map(tenantRepository::findById)
            .flatMap(java.util.Optional::stream)
            .map(t -> new TenantSummary(t.getId(), t.getSlug(), t.getName()))
            .toList();
    }

    @PostMapping("/switch-tenant")
    public ResponseEntity<AuthResponse> switchTenant(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody SwitchTenantRequest req) {

        List<Long> allowed = userRoleService.findUserTenantIds(principal.getId());
        if (!allowed.contains(req.tenantId())) {
            throw new AccessDeniedException("User has no role on tenant " + req.tenantId());
        }

        User user = userRepository.findById(principal.getId()).orElseThrow();
        String newToken = tokenService.generateToken(user, req.tenantId());
        // Build full AuthResponse (mimic /login response shape)
        return ResponseEntity.ok(AuthResponse.of(newToken, user, req.tenantId(), ...));
    }
}
```

### 1.9 TenantProvisioningService — adaptation

Aujourd'hui : `User.role = Role.PRO`. Demain : crée un `UserRoleAssignment(PRO, TENANT, tenant.id)`.

```java
@Transactional
public Tenant provision(User owner) {
    // ... existing slug + schema provisioning ...
    Tenant saved = tenantRepository.save(tenant);

    // NEW: assign PRO role on this tenant
    userRoleService.assignOnTenant(owner.getId(), Role.PRO, saved.getId());

    // Existing: create the pro-self employee inside the new tenant schema
    TenantContext.setCurrentTenant(slug);
    try {
        employeeService.createSelfEmployee(owner);
        // NEW: ALSO assign EMPLOYEE role on this tenant for the owner
        // (the pro-self employee makes them an employee too)
        userRoleService.assignOnTenant(owner.getId(), Role.EMPLOYEE, saved.getId());
    } finally {
        TenantContext.clear();
    }
    return saved;
}
```

### 1.10 EmployeeService — adaptation

À la création d'un employé via le flow d'invitation, on crée aussi un `UserRoleAssignment(EMPLOYEE, TENANT, ...)` pour le User correspondant. À détailler dans le ticket 7.

### 1.11 Migration Flyway V8

```sql
-- backend/src/main/resources/db/migration/oracle/V8__user_role_assignments.sql

-- 1. New table
CREATE TABLE "${appSchema}".USER_ROLE_ASSIGNMENTS (
    ID         NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    USER_ID    NUMBER(19) NOT NULL,
    ROLE       VARCHAR2(32 CHAR) NOT NULL,
    SCOPE_TYPE VARCHAR2(16 CHAR) NOT NULL,
    SCOPE_ID   NUMBER(19),
    CREATED_AT TIMESTAMP NOT NULL,
    CONSTRAINT FK_URA_USER FOREIGN KEY (USER_ID) REFERENCES "${appSchema}".USERS(ID),
    CONSTRAINT UK_USER_ROLE_SCOPE UNIQUE (USER_ID, ROLE, SCOPE_TYPE, SCOPE_ID),
    CONSTRAINT CK_SCOPE_TYPE CHECK (SCOPE_TYPE IN ('GLOBAL', 'TENANT')),
    CONSTRAINT CK_SCOPE_ID_MATCH CHECK (
        (SCOPE_TYPE = 'TENANT' AND SCOPE_ID IS NOT NULL) OR
        (SCOPE_TYPE = 'GLOBAL' AND SCOPE_ID IS NULL)
    )
);

CREATE INDEX IX_URA_USER ON "${appSchema}".USER_ROLE_ASSIGNMENTS (USER_ID);
CREATE INDEX IX_URA_SCOPE ON "${appSchema}".USER_ROLE_ASSIGNMENTS (SCOPE_TYPE, SCOPE_ID);

-- 2. Backfill from existing User.role
--    PRO: 1 row PRO@TENANT for the tenant they own
INSERT INTO "${appSchema}".USER_ROLE_ASSIGNMENTS (USER_ID, ROLE, SCOPE_TYPE, SCOPE_ID, CREATED_AT)
SELECT u.ID, 'PRO', 'TENANT', t.ID, CURRENT_TIMESTAMP
FROM "${appSchema}".USERS u
JOIN "${appSchema}".TENANTS t ON t.OWNER_ID = u.ID
WHERE u.ROLE = 'PRO';

--    ADMIN: 1 row ADMIN@GLOBAL
INSERT INTO "${appSchema}".USER_ROLE_ASSIGNMENTS (USER_ID, ROLE, SCOPE_TYPE, SCOPE_ID, CREATED_AT)
SELECT ID, 'ADMIN', 'GLOBAL', NULL, CURRENT_TIMESTAMP
FROM "${appSchema}".USERS WHERE ROLE = 'ADMIN';

--    USER: no row (they become CLIENT implicite)
--    EMPLOYEE: backfilled by EmployeeRoleBackfillRunner (Java) because EMPLOYEES table is tenant-scoped

-- 3. Drop the User.role column
-- Oracle requires unused-then-drop pattern; also need to drop the CK_USERS_ROLE constraint first.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'USERS' AND CONSTRAINT_NAME = 'CK_USERS_ROLE';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE "${appSchema}".USERS DROP CONSTRAINT CK_USERS_ROLE';
    END IF;
END;
/

ALTER TABLE "${appSchema}".USERS SET UNUSED COLUMN ROLE;
ALTER TABLE "${appSchema}".USERS DROP UNUSED COLUMNS;
```

### 1.12 EmployeeRoleBackfillRunner — Java one-shot

```java
package com.luxpretty.app.multitenancy;

import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.users.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One-shot runner that backfills EMPLOYEE role assignments for users that
 * have an Employee row in any tenant schema, but no matching assignment in
 * USER_ROLE_ASSIGNMENTS yet. Idempotent: re-running has no effect.
 *
 * Runs at every boot but the inner check makes subsequent runs a no-op
 * (UserRoleService.assignOnTenant is idempotent).
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
                } finally {
                    TenantContext.clear();
                }
            }
            logger.info("EmployeeRoleBackfillRunner: ensured {} EMPLOYEE assignments", total);
        };
    }
}
```

---

## 2. Découpage en tickets

9 tickets séquentiels, chacun ~30 min à 6h. Chaque ticket = 1 commit. TDD partout.

### Légende
- 🟢 = trivial (~30 min)
- 🟡 = moyen (1-2h)
- 🔴 = important (3-6h)
- **TDD** = écrire test failing en premier

---

### 🟢 Ticket 1 — Enums et entité `UserRoleAssignment`

**Pourquoi en premier :** établir le vocabulaire métier sans dépendance.

**Files :**
- Modify: `backend/src/main/java/com/luxpretty/app/users/domain/Role.java`
- Create: `backend/src/main/java/com/luxpretty/app/users/domain/ScopeType.java`
- Create: `backend/src/main/java/com/luxpretty/app/users/domain/UserRoleAssignment.java`

**TDD tests :**
- Test que `Role.PRO.expectedScopeType() == ScopeType.TENANT`
- Test que `Role.ADMIN.expectedScopeType() == ScopeType.GLOBAL`
- Test que `Role.COMMERCIAL.expectedScopeType() == ScopeType.GLOBAL`
- Test que `Role.EMPLOYEE.expectedScopeType() == ScopeType.TENANT`

**Steps :**
1. Test `RoleTests.java` avec 4 assertions ci-dessus
2. Run → compile fail (Role.USER existe encore, COMMERCIAL pas encore)
3. Modify `Role.java` : drop USER, add COMMERCIAL, add `expectedScopeType()`
4. Run → toujours fail (USER référencé ailleurs dans le code, à laisser pour le ticket 2)
5. Si fail à cause de USER : laisser passer pour l'instant — ce ticket ne compile pas encore tout seul. **OU** : laisser USER dans l'enum (deprecated) pour ce ticket et l'enlever dans le ticket 8.

**Approche recommandée pour ce ticket :** laisser `Role.USER` deprecated pour ne pas casser tout le projet. On le drop au ticket 8.

```java
public enum Role {
    PRO,
    EMPLOYEE,
    COMMERCIAL,
    ADMIN,
    @Deprecated USER;  // TODO ticket 8: remove

    public ScopeType expectedScopeType() {
        return switch (this) {
            case PRO, EMPLOYEE -> ScopeType.TENANT;
            case COMMERCIAL, ADMIN -> ScopeType.GLOBAL;
            case USER -> throw new IllegalStateException("USER is deprecated");
        };
    }
}
```

6. Create `ScopeType.java`
7. Create `UserRoleAssignment.java`
8. `mvn compile` → BUILD SUCCESS
9. `mvn test -Dtest=RoleTests` → all green
10. Commit

**Commit :** `feat(users): UserRoleAssignment entity + ScopeType + Role.COMMERCIAL`

---

### 🟢 Ticket 2 — Repository `UserRoleAssignmentRepository`

**Files :**
- Create: `backend/src/main/java/com/luxpretty/app/users/repo/UserRoleAssignmentRepository.java`

**TDD tests :** rien (interface Spring Data, pas de logique métier).

**Steps :**
1. Create the interface with 4 methods (cf. section 1.4)
2. `mvn compile` → BUILD SUCCESS
3. Commit

**Commit :** `feat(users): UserRoleAssignmentRepository finders`

---

### 🔴 Ticket 3 — Service `UserRoleService` (TDD)

**Files :**
- Create: `backend/src/main/java/com/luxpretty/app/users/app/UserRoleService.java`
- Create: `backend/src/test/java/com/luxpretty/app/users/app/UserRoleServiceTests.java`

**TDD tests (10 tests) :**

```java
// assign() happy path
@Test void assign_createsAssignment_whenAbsent()
@Test void assign_isIdempotent_returnsExisting_whenPresent()

// assign() validation
@Test void assign_rejectsProWithGlobalScope()
@Test void assign_rejectsAdminWithTenantScope()
@Test void assign_rejectsTenantScopeWithoutScopeId()
@Test void assign_rejectsGlobalScopeWithScopeId()

// resolve()
@Test void resolveRoles_returnsGlobalRoles_whenActiveTenantNull()
@Test void resolveRoles_returnsGlobalRolesPlusActiveTenantRoles()
@Test void resolveRoles_excludesOtherTenantRoles()

// helpers
@Test void findUserTenantIds_returnsDistinctTenants()
```

**Steps :**
1. Write all 10 tests (failing)
2. `mvn test -Dtest=UserRoleServiceTests` → compile error or all red
3. Implement `UserRoleService` (cf. section 1.5)
4. Iterate test by test until all green
5. `mvn test -Dtest=UserRoleServiceTests` → 10/10 green
6. Full suite : `mvn test` → green
7. Commit

**Commit :** `feat(users): UserRoleService — assign/revoke/resolve roles (10 tests)`

---

### 🔴 Ticket 4 — Migration Flyway V8 + drop User.role

**Files :**
- Create: `backend/src/main/resources/db/migration/oracle/V8__user_role_assignments.sql`
- Modify: `backend/src/main/java/com/luxpretty/app/users/domain/User.java` (supprime field `role`)
- Possibly modify: any code that read `user.getRole()` — replace with `userRoleService.resolveRoles(user.id, ...)` or similar

**Steps :**

1. Write V8 SQL (cf. section 1.11)
2. **Find all callers of `user.getRole()`** :
   ```bash
   grep -rn "\.getRole()" backend/src/main/java
   ```
3. For each caller, decide:
   - If it's in `auth/` (TokenService, JwtAuthenticationFilter) → defer to tickets 5/6
   - If it's in `tenant/` (TenantProvisioningService) → defer to ticket 7
   - If it's elsewhere (DataInitializer, tests) → migrate now using `userRoleService.resolveRoles(...)`
4. Modify `User.java` :
   ```java
   // REMOVE:
   // @Enumerated(EnumType.STRING)
   // @Column(name = "role", nullable = false)
   // private Role role = Role.USER;
   ```
5. `mvn compile` → **expect compile errors** in callers from step 2. Fix them now.
6. `mvn test` → expect failures. Fix the broken tests minimally (use UserRoleService or just `@MockBean UserRoleService`).
7. Manually test the migration locally :
   - Start the backend → Flyway should apply V8
   - Check `SELECT * FROM USER_ROLE_ASSIGNMENTS;` — should have 1 PRO row per pro tenant owner, 1 ADMIN row per admin
   - Check `SELECT ROLE FROM USERS;` should fail (column dropped)
8. Commit

**Commit :** `feat(users): drop User.role + V8 migration to USER_ROLE_ASSIGNMENTS`

---

### 🔴 Ticket 5 — TokenService refactor (TDD)

**Files :**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/TokenService.java`
- Modify: `backend/src/test/java/com/luxpretty/app/auth/TokenServiceTests.java`

**TDD tests (6 tests) :**

```java
@Test void generateToken_includesRolesClaim()
@Test void generateToken_includesActiveTenantIdClaim()
@Test void generateToken_includesAvailableTenantsClaim()
@Test void generateToken_chooseFirstTenant_whenUserHasNoActiveYet()
@Test void generateToken_returnsEmptyRoles_forClientWithNoAssignments()
@Test void generateToken_overrideActiveTenant_returnsRolesForThatTenant()
```

**Steps :**
1. Append tests
2. Run → compile error or red
3. Modify `TokenService.generateToken` signature: add `Long activeTenantId` overload, inject `UserRoleService`
4. Implement the new claims
5. Run → 6/6 green
6. Full suite : `mvn test`
7. Commit

**Commit :** `feat(auth): TokenService emits roles + activeTenantId + availableTenants (6 tests)`

---

### 🔴 Ticket 6 — JwtAuthenticationFilter refactor (TDD)

**Files :**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/JwtAuthenticationFilter.java`
- Modify: `backend/src/test/java/com/luxpretty/app/auth/JwtAuthenticationFilterTests.java`

**TDD tests (4 tests) :**

```java
@Test void filter_setsMultipleAuthorities_fromRolesClaim()
@Test void filter_setsTenantContext_whenActiveTenantIdPresent()
@Test void filter_clearsTenantContext_inFinally()
@Test void filter_skipsTenantContext_whenActiveTenantIdNull()
```

**Steps :**
1. Append tests
2. Run → red
3. Modify filter (cf. section 1.7) :
   - Parse `roles` list claim, build authorities
   - Set TenantContext from activeTenantId (resolve tenant.slug from id)
   - Clear in `finally`
4. Run → 4/4 green
5. Full suite
6. Commit

**Commit :** `feat(auth): JwtAuthenticationFilter parses roles + sets TenantContext from JWT (4 tests)`

---

### 🟡 Ticket 7 — Update register flow + TenantProvisioning + EmployeeService

**Files :**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/AuthController.java`
- Modify: `backend/src/main/java/com/luxpretty/app/auth/CustomOAuth2UserService.java`
- Modify: `backend/src/main/java/com/luxpretty/app/tenant/app/TenantProvisioningService.java`
- Modify: `backend/src/main/java/com/luxpretty/app/employee/app/EmployeeService.java`
- Modify: existing tests for these

**Changes :**

1. **AuthController.registerProWithSalonInfo** :
   - After `userRepository.save(user)`, instead of `user.setRole(Role.PRO)` → `userRoleService.assignOnTenant(user.getId(), Role.PRO, tenant.getId())`
   - But wait — TenantProvisioningService already does this now (ticket 7 fixed it). So AuthController just delegates to provisioning.

2. **CustomOAuth2UserService** :
   - If `isPro`, after provisioning tenant → `userRoleService.assignOnTenant(...)` (or rely on TenantProvisioningService doing it)
   - If not pro → no assignment (CLIENT implicite)

3. **TenantProvisioningService.provision** (cf. section 1.9) — already creates PRO@TENANT and EMPLOYEE@TENANT for the owner.

4. **EmployeeService.create** (when adding a new employee via invitation flow — currently `createInternal` or similar) :
   - After saving the Employee, also call `userRoleService.assignOnTenant(employee.getUserId(), Role.EMPLOYEE, currentTenantId)`

**TDD tests :** existing tests + 2 new ones :
- `TenantProvisioningServiceTests.provision_assignsProAndEmployeeRoles`
- `EmployeeServiceTests.create_assignsEmployeeRoleToUser`

**Steps :**
1. Add the 2 new tests, watch them fail
2. Modify the services
3. Run tests, iterate
4. Full suite : `mvn test`
5. Commit

**Commit :** `feat(auth): register/provisioning flows now create UserRoleAssignment`

---

### 🟡 Ticket 8 — MeController (`/api/me/tenants`, `/api/me/switch-tenant`)

**Files :**
- Create: `backend/src/main/java/com/luxpretty/app/me/web/MeController.java`
- Create: `backend/src/test/java/com/luxpretty/app/me/web/MeControllerTests.java`

**TDD tests (4 tests) :**

```java
@Test void myTenants_returnsTenantSummaries_forUserWithAssignments()
@Test void myTenants_returnsEmpty_forClientWithoutAssignments()
@Test void switchTenant_returnsNewToken_whenUserHasAssignment()
@Test void switchTenant_rejectsWith403_whenUserHasNoAssignment()
```

**Steps :**
1. Write tests (with `@WebMvcTest(MeController.class)` + `@MockBean` for the services)
2. Run → fail (controller doesn't exist)
3. Create MeController (cf. section 1.8)
4. Run → 4/4 green
5. Full suite
6. Commit

**Commit :** `feat(me): GET /api/me/tenants + POST /api/me/switch-tenant (4 tests)`

---

### 🟢 Ticket 9 — `EmployeeRoleBackfillRunner` + final cleanup

**Files :**
- Create: `backend/src/main/java/com/luxpretty/app/multitenancy/EmployeeRoleBackfillRunner.java`
- Modify: `backend/src/main/java/com/luxpretty/app/users/domain/Role.java` (remove deprecated USER)
- Modify: any leftover code that still references `Role.USER`

**Steps :**

1. Create the runner (cf. section 1.12)
2. Remove `Role.USER` from the enum
3. `mvn compile` → if compile errors, fix the leftover references (probably tests with `@Builder ... .role(Role.USER)...`)
4. `mvn test` → full suite green
5. Manual sanity : restart backend locally, watch the runner log "ensured X EMPLOYEE assignments". Re-run, should log "ensured X" again (idempotent, X unchanged).
6. Commit

**Commit :** `feat(users): EmployeeRoleBackfillRunner + drop Role.USER`

---

## 3. Plan de vérification (post-merge)

### 3.1 Tests automatisés

- `mvn test` doit passer 100%. Total attendu : actuel (596) + nouveaux tests (≈30) = ~626.
- Type-check frontend : pas affecté par cette PR (le frontend continue de lire `user.role` du JWT — mais le JWT n'a plus `role`, il a `roles`).

⚠️ **Point d'attention important** : si le frontend continue d'attendre `user.role` (single), il faut **soit** :
- Renvoyer dans `AuthResponse` un champ legacy `role` qui = première valeur de `roles[]`, pour ne pas casser le frontend pendant cette PR
- **Soit** breaking change frontend immédiat — mais ça déborde le scope "PR1 backend pur"

**Décision proposée :** garder le champ `role` legacy dans `AuthResponse` pour cette PR. Le frontend sera nettoyé en PR2.

### 3.2 Manual smoke

1. Restart backend → V8 migration runs → check `SELECT COUNT(*) FROM USER_ROLE_ASSIGNMENTS` ≥ 1
2. Login as existing pro user → JWT now contains `roles: ["PRO"]`, `activeTenantId: <theirs>`, `availableTenants: [{...}]`
3. Hit `/api/pro/cares` → still works (because `ROLE_PRO` authority granted)
4. Hit `GET /api/me/tenants` → returns the user's tenant(s)
5. Hit `POST /api/me/switch-tenant { tenantId: <theirs> }` → returns new token, same `activeTenantId`
6. Hit `POST /api/me/switch-tenant { tenantId: <not-theirs> }` → 403
7. Restart backend → `EmployeeRoleBackfillRunner` runs once → all employees have EMPLOYEE assignments

### 3.3 Rollback plan

Si la PR explose en production :
1. Revert merge commit
2. La V8 migration a **dropped User.role** → rollback DB requise : restaurer la colonne avec valeurs initiales
   - Pour limiter ce risque, **option** : ne pas drop la colonne dans V8, juste l'ignorer. Drop dans une V9 ultérieure quand on est sûr. (À discuter au plan d'implémentation.)

---

## 4. Estimations finales

| Ticket | Effort | Cumul |
|---|---|---|
| 1 — Enums + entity | 30 min | 30 min |
| 2 — Repository | 20 min | 50 min |
| 3 — UserRoleService TDD | 3h | 3h50 |
| 4 — Migration V8 + drop role | 4h | 7h50 |
| 5 — TokenService TDD | 2h | 9h50 |
| 6 — JwtFilter TDD | 1h30 | 11h20 |
| 7 — Register/provisioning flows | 2h | 13h20 |
| 8 — MeController TDD | 1h30 | 14h50 |
| 9 — BackfillRunner + cleanup | 1h | 15h50 |

**Total : ~16h soit ~2 jours focalisés.** (J'avais dit 4-5j, c'était trop conservateur — vu le découpage TDD bien clair et le scope backend uniquement.)

---

## 5. Risques et décisions ouvertes (à trancher au plan)

1. **EMPLOYEEs migration en SQL ou Java ?** → Java (runner) car les EMPLOYEES sont en schéma tenant. Décidé : Java.

2. **Drop User.role colonne maintenant ou plus tard ?** → Considérer la sécurité du rollback. Recommandation : drop dans V8 pour pousser à la cohérence ; rollback = restaurer depuis backup DB. Acceptable.

3. **Champ `role` legacy dans AuthResponse pour la transition frontend** → OUI, on le garde dans cette PR. Le frontend lira encore `role`, mais on lui prépare `roles` aussi. PR2 nettoiera.

4. **JWT `availableTenants` payload size** → si un user a 50 tenants, le JWT devient gros. Limitation : on stocke juste `{id, slug, name}` (~ 100 bytes par tenant). À 50 tenants = 5kB. Acceptable. Si on a un cas extrême >100 tenants, refactor en endpoint séparé. Pas dans le scope PR1.

5. **`/api/me/switch-tenant` envoie le nouveau JWT comme une `AuthResponse`** → le frontend (PR2) devra savoir le stocker comme remplacement du token précédent. Backend ne fait que renvoyer, c'est le job frontend.

---

## 6. Prochaines étapes après cette PR

- **PR2 frontend** : selector salon, switch UI, mise à jour de tous les `user.role === Role.PRO` en `roles.includes('PRO')`, helper `authService.hasRole()`.
- **Q1 feature complète** : ouvrir l'UI "mode client" pour les pros (historique de bookings pris ailleurs). Probablement une PR3.
- **Préparation C1-C4** : la PR1 introduit `COMMERCIAL` comme rôle GLOBAL — c'est posé pour quand le système commercial sera développé.
