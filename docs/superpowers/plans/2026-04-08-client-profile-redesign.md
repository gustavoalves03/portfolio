# Client Profile Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the PRO client detail page as a scrollable dashboard, fix client name, add audit trail, implement employee permissions by domain, and decompose the monolith component.

**Architecture:** Add audit fields (updatedAt/updatedBy) to existing entities, resolve user names via ApplicationSchemaExecutor, new EMPLOYEE_PERMISSIONS table for domain-based access control, decompose the 1146-line component into 5 sub-components with the Dashboard B layout.

**Tech Stack:** Spring Boot 3.5 JPA + Oracle/H2, Angular 20 standalone components + Material + Signals.

---

## File Map

### Backend — Modified Files
| File | Change |
|------|--------|
| `backend/.../tracking/domain/ClientProfile.java` | Add `updatedAt`, `updatedBy` fields |
| `backend/.../tracking/domain/VisitRecord.java` | Add `updatedAt`, `updatedBy` fields |
| `backend/.../tracking/domain/VisitPhoto.java` | Add `uploadedBy` field |
| `backend/.../tracking/domain/ClientReminder.java` | Add `createdBy` field |
| `backend/.../tracking/web/dto/ClientHistoryResponse.java` | Add `clientName`, `clientEmail` |
| `backend/.../tracking/web/dto/ClientProfileResponse.java` | Add `updatedAt`, `updatedByName` |
| `backend/.../tracking/web/dto/VisitRecordResponse.java` | Add `updatedAt`, `updatedByName` |
| `backend/.../tracking/web/dto/VisitPhotoResponse.java` | Add `uploadedByName` |
| `backend/.../tracking/web/dto/ReminderResponse.java` | Add `createdByName` |
| `backend/.../tracking/app/TrackingService.java` | Inject UserRepository + ApplicationSchemaExecutor, set audit fields, resolve names |
| `backend/.../tracking/web/TrackingController.java` | Pass `UserPrincipal` to service methods, add employee endpoints |
| `backend/.../multitenancy/TenantSchemaManager.java` | Add EMPLOYEE_PERMISSIONS table, ALTER audit columns |
| `backend/.../config/ApplicationSchemaMigrator.java` | Add migration for audit columns on existing tenant schemas |
| `backend/.../config/SecurityConfig.java` | Allow `/api/employee/tracking/**` endpoints |

### Backend — New Files
| File | Responsibility |
|------|---------------|
| `backend/.../employee/domain/EmployeePermission.java` | JPA entity |
| `backend/.../employee/domain/PermissionDomain.java` | Enum: PROFILE, VISITS, PHOTOS, REMINDERS |
| `backend/.../employee/domain/AccessLevel.java` | Enum: NONE, READ, WRITE |
| `backend/.../employee/repo/EmployeePermissionRepository.java` | Spring Data repo |
| `backend/.../employee/app/EmployeePermissionService.java` | CRUD + check logic |
| `backend/.../employee/web/dto/EmployeePermissionResponse.java` | DTO |
| `backend/.../employee/web/dto/UpdatePermissionsRequest.java` | Request DTO |
| `backend/.../employee/web/EmployeePermissionController.java` | REST endpoints |

### Frontend — New Files
| File | Responsibility |
|------|---------------|
| `frontend/.../features/tracking/components/client-header/client-header.component.ts` | Avatar, name, badges, allergy alert |
| `frontend/.../features/tracking/components/client-visits/client-visits.component.ts` | Visit list, expand, new visit form |
| `frontend/.../features/tracking/components/client-notes/client-notes.component.ts` | Editable notes + audit |
| `frontend/.../features/tracking/components/client-info/client-info.component.ts` | Info grid 2×2 editable + audit |
| `frontend/.../features/tracking/components/visit-card/visit-card.component.ts` | Single visit card |
| `frontend/.../pages/employee/employee-client-detail.component.ts` | Thin wrapper with permissions |

### Frontend — Modified Files
| File | Change |
|------|--------|
| `frontend/.../features/tracking/tracking.model.ts` | Add audit fields, clientName, permissions |
| `frontend/.../features/tracking/tracking.service.ts` | Add employee endpoints |
| `frontend/.../pages/pro/pro-client-detail.component.ts` | Rewrite as orchestrator using sub-components |
| `frontend/.../app.routes.ts` | Add `/employee/clients/:userId` route |
| `frontend/public/i18n/fr.json` | Permission labels, audit labels |
| `frontend/public/i18n/en.json` | Same in English |

---

### Task 1: Backend — Add audit fields to entities

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tracking/domain/ClientProfile.java`
- Modify: `backend/src/main/java/com/prettyface/app/tracking/domain/VisitRecord.java`
- Modify: `backend/src/main/java/com/prettyface/app/tracking/domain/VisitPhoto.java`
- Modify: `backend/src/main/java/com/prettyface/app/tracking/domain/ClientReminder.java`

- [ ] **Step 1: Add updatedAt and updatedBy to ClientProfile**

In `ClientProfile.java`, add after the `createdAt` field (line 49):

```java
@Column(name = "updated_at")
private LocalDateTime updatedAt;

@Column(name = "updated_by")
private Long updatedBy;
```

- [ ] **Step 2: Add updatedAt and updatedBy to VisitRecord**

In `VisitRecord.java`, add after the `createdAt` field (line 50):

```java
@Column(name = "updated_at")
private LocalDateTime updatedAt;

@Column(name = "updated_by")
private Long updatedBy;
```

- [ ] **Step 3: Add uploadedBy to VisitPhoto**

In `VisitPhoto.java`, add after the `createdAt` field (line 35):

```java
@Column(name = "uploaded_by")
private Long uploadedBy;
```

- [ ] **Step 4: Add createdBy to ClientReminder**

In `ClientReminder.java`, add after the `createdAt` field (line 40):

```java
@Column(name = "created_by")
private Long createdBy;
```

- [ ] **Step 5: Verify compilation**

Run: `cd backend && ./mvnw compile -q`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tracking/domain/
git commit -m "feat: add audit fields to tracking entities"
```

---

### Task 2: Backend — Schema migration for audit columns + EMPLOYEE_PERMISSIONS table

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/multitenancy/TenantSchemaManager.java`
- Modify: `backend/src/main/java/com/prettyface/app/config/ApplicationSchemaMigrator.java`

- [ ] **Step 1: Add EMPLOYEE_PERMISSIONS to TENANT_TABLES list**

In `TenantSchemaManager.java`, add `"EMPLOYEE_PERMISSIONS"` to the `TENANT_TABLES` list (after line 50 `"CLIENT_REMINDERS"`).

- [ ] **Step 2: Add EMPLOYEE_PERMISSIONS table creation in Oracle provisioning**

Find the Oracle provisioning section where CLIENT_REMINDERS is created. After it, add:

```sql
CREATE TABLE EMPLOYEE_PERMISSIONS (
    ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    EMPLOYEE_ID NUMBER(19) NOT NULL,
    DOMAIN VARCHAR2(30 CHAR) NOT NULL,
    ACCESS_LEVEL VARCHAR2(10 CHAR) NOT NULL,
    CONSTRAINT FK_EMP_PERM_EMP FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID),
    CONSTRAINT UQ_EMP_PERM UNIQUE (EMPLOYEE_ID, DOMAIN)
)
```

- [ ] **Step 3: Add EMPLOYEE_PERMISSIONS table creation in H2 provisioning**

Same table DDL adapted for H2 (use `GENERATED ALWAYS AS IDENTITY` which works in both).

- [ ] **Step 4: Add audit column migrations to ApplicationSchemaMigrator**

In `ApplicationSchemaMigrator.java`, add a new method `ensureTrackingAuditColumns()` that runs ALTER TABLE statements to add the new columns. Use try/catch to handle the case where columns already exist:

```java
private void ensureTrackingAuditColumns() throws SQLException {
    // This runs against each tenant schema via TenantSchemaMigrator
    // For now, add columns to the application-level migrator as a safety net
    // The actual columns are created by JPA auto-DDL on existing schemas
}
```

Note: Since the project uses JPA with `spring.jpa.hibernate.ddl-auto=update`, Hibernate will add the new columns automatically. The TenantSchemaManager handles new tenant provisioning. For existing tenants, the `TenantSchemaMigrator` already iterates all tenants and can add columns.

- [ ] **Step 5: Verify compilation**

Run: `cd backend && ./mvnw compile -q`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/multitenancy/TenantSchemaManager.java backend/src/main/java/com/prettyface/app/config/ApplicationSchemaMigrator.java
git commit -m "feat: add EMPLOYEE_PERMISSIONS table and audit column migrations"
```

---

### Task 3: Backend — Employee permission entity, repo, service

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/employee/domain/PermissionDomain.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/domain/AccessLevel.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/domain/EmployeePermission.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/repo/EmployeePermissionRepository.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/app/EmployeePermissionService.java`

- [ ] **Step 1: Create PermissionDomain enum**

```java
package com.prettyface.app.employee.domain;

public enum PermissionDomain {
    PROFILE, VISITS, PHOTOS, REMINDERS
}
```

- [ ] **Step 2: Create AccessLevel enum**

```java
package com.prettyface.app.employee.domain;

public enum AccessLevel {
    NONE, READ, WRITE
}
```

- [ ] **Step 3: Create EmployeePermission entity**

```java
package com.prettyface.app.employee.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "EMPLOYEE_PERMISSIONS", uniqueConstraints = {
    @UniqueConstraint(name = "UQ_EMP_PERM", columnNames = {"employee_id", "domain"})
})
@Getter
@Setter
@NoArgsConstructor
public class EmployeePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false, length = 30)
    private PermissionDomain domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 10)
    private AccessLevel accessLevel;
}
```

- [ ] **Step 4: Create EmployeePermissionRepository**

```java
package com.prettyface.app.employee.repo;

import com.prettyface.app.employee.domain.EmployeePermission;
import com.prettyface.app.employee.domain.PermissionDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeePermissionRepository extends JpaRepository<EmployeePermission, Long> {
    List<EmployeePermission> findByEmployeeId(Long employeeId);
    Optional<EmployeePermission> findByEmployeeIdAndDomain(Long employeeId, PermissionDomain domain);
}
```

- [ ] **Step 5: Create EmployeePermissionService**

```java
package com.prettyface.app.employee.app;

import com.prettyface.app.employee.domain.AccessLevel;
import com.prettyface.app.employee.domain.EmployeePermission;
import com.prettyface.app.employee.domain.PermissionDomain;
import com.prettyface.app.employee.repo.EmployeePermissionRepository;
import com.prettyface.app.employee.repo.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EmployeePermissionService {

    private final EmployeePermissionRepository permissionRepo;
    private final EmployeeRepository employeeRepo;

    public EmployeePermissionService(EmployeePermissionRepository permissionRepo,
                                      EmployeeRepository employeeRepo) {
        this.permissionRepo = permissionRepo;
        this.employeeRepo = employeeRepo;
    }

    @Transactional(readOnly = true)
    public Map<PermissionDomain, AccessLevel> getPermissions(Long employeeId) {
        List<EmployeePermission> perms = permissionRepo.findByEmployeeId(employeeId);
        Map<PermissionDomain, AccessLevel> result = perms.stream()
                .collect(Collectors.toMap(EmployeePermission::getDomain, EmployeePermission::getAccessLevel));
        // Fill missing domains with NONE
        for (PermissionDomain domain : PermissionDomain.values()) {
            result.putIfAbsent(domain, AccessLevel.NONE);
        }
        return result;
    }

    @Transactional
    public Map<PermissionDomain, AccessLevel> updatePermissions(Long employeeId,
                                                                 Map<PermissionDomain, AccessLevel> updates) {
        employeeRepo.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));

        for (var entry : updates.entrySet()) {
            EmployeePermission perm = permissionRepo.findByEmployeeIdAndDomain(employeeId, entry.getKey())
                    .orElseGet(() -> {
                        EmployeePermission p = new EmployeePermission();
                        p.setEmployeeId(employeeId);
                        p.setDomain(entry.getKey());
                        return p;
                    });
            perm.setAccessLevel(entry.getValue());
            permissionRepo.save(perm);
        }

        return getPermissions(employeeId);
    }

    @Transactional(readOnly = true)
    public AccessLevel checkAccess(Long employeeId, PermissionDomain domain) {
        return permissionRepo.findByEmployeeIdAndDomain(employeeId, domain)
                .map(EmployeePermission::getAccessLevel)
                .orElse(AccessLevel.NONE);
    }

    @Transactional(readOnly = true)
    public void requireAccess(Long employeeId, PermissionDomain domain, AccessLevel minimum) {
        AccessLevel actual = checkAccess(employeeId, domain);
        if (actual.ordinal() < minimum.ordinal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Insufficient permission for " + domain + ": requires " + minimum + ", has " + actual);
        }
    }
}
```

- [ ] **Step 6: Verify compilation**

Run: `cd backend && ./mvnw compile -q`

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/employee/domain/PermissionDomain.java backend/src/main/java/com/prettyface/app/employee/domain/AccessLevel.java backend/src/main/java/com/prettyface/app/employee/domain/EmployeePermission.java backend/src/main/java/com/prettyface/app/employee/repo/EmployeePermissionRepository.java backend/src/main/java/com/prettyface/app/employee/app/EmployeePermissionService.java
git commit -m "feat: add employee permission entity, repository, and service"
```

---

### Task 4: Backend — Update DTOs with audit fields and client name

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tracking/web/dto/ClientHistoryResponse.java`
- Modify: `backend/src/main/java/com/prettyface/app/tracking/web/dto/ClientProfileResponse.java`
- Modify: `backend/src/main/java/com/prettyface/app/tracking/web/dto/VisitRecordResponse.java`
- Modify: `backend/src/main/java/com/prettyface/app/tracking/web/dto/VisitPhotoResponse.java`
- Modify: `backend/src/main/java/com/prettyface/app/tracking/web/dto/ReminderResponse.java`

- [ ] **Step 1: Update ClientHistoryResponse**

Replace the file content:

```java
package com.prettyface.app.tracking.web.dto;

import java.util.List;

public record ClientHistoryResponse(
        String clientName,
        String clientEmail,
        ClientProfileResponse profile,
        List<VisitRecordResponse> visits,
        List<ReminderResponse> reminders
) {}
```

- [ ] **Step 2: Update ClientProfileResponse**

Replace:

```java
package com.prettyface.app.tracking.web.dto;

import java.time.LocalDateTime;

public record ClientProfileResponse(
        Long id,
        Long userId,
        String notes,
        String skinType,
        String hairType,
        String allergies,
        String preferences,
        boolean consentPhotos,
        boolean consentPublicShare,
        LocalDateTime consentGivenAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String updatedByName
) {}
```

- [ ] **Step 3: Update VisitRecordResponse**

Replace:

```java
package com.prettyface.app.tracking.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record VisitRecordResponse(
        Long id,
        Long clientProfileId,
        Long bookingId,
        Long careId,
        String careName,
        LocalDate visitDate,
        String practitionerNotes,
        String productsUsed,
        Integer satisfactionScore,
        String satisfactionComment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String updatedByName,
        List<VisitPhotoResponse> photos
) {}
```

- [ ] **Step 4: Update VisitPhotoResponse**

Replace:

```java
package com.prettyface.app.tracking.web.dto;

public record VisitPhotoResponse(
        Long id,
        com.prettyface.app.tracking.domain.PhotoType photoType,
        String imageUrl,
        Integer imageOrder,
        String uploadedByName
) {}
```

- [ ] **Step 5: Update ReminderResponse**

Replace:

```java
package com.prettyface.app.tracking.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReminderResponse(
        Long id,
        Long userId,
        Long careId,
        String careName,
        LocalDate recommendedDate,
        String message,
        boolean sent,
        LocalDateTime createdAt,
        String createdByName
) {}
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tracking/web/dto/
git commit -m "feat: add audit fields and client name to tracking DTOs"
```

---

### Task 5: Backend — Update TrackingService with audit + name resolution

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tracking/app/TrackingService.java`
- Modify: `backend/src/main/java/com/prettyface/app/tracking/web/TrackingController.java`

- [ ] **Step 1: Add dependencies to TrackingService**

Add `UserRepository` and `ApplicationSchemaExecutor` to constructor injection:

```java
private final com.prettyface.app.users.repo.UserRepository userRepository;
private final com.prettyface.app.multitenancy.ApplicationSchemaExecutor applicationSchemaExecutor;
```

Add to constructor parameters and assignment.

- [ ] **Step 2: Create helper method to resolve user name**

```java
private String resolveUserName(Long userId) {
    if (userId == null) return null;
    return applicationSchemaExecutor.call(() ->
            userRepository.findById(userId)
                    .map(com.prettyface.app.users.domain.User::getName)
                    .orElse(null));
}
```

- [ ] **Step 3: Update getClientHistory() to include client name**

In the `getClientHistory()` method, resolve the client's name and email:

```java
@Transactional
public ClientHistoryResponse getClientHistory(Long userId) {
    // Resolve client name from shared schema
    String[] clientInfo = applicationSchemaExecutor.call(() -> {
        var user = userRepository.findById(userId).orElse(null);
        return user != null ? new String[]{user.getName(), user.getEmail()} : new String[]{"Client #" + userId, null};
    });

    ClientProfile profile = profileRepo.findByUserId(userId)
            .orElseGet(() -> {
                ClientProfile p = new ClientProfile();
                p.setUserId(userId);
                return profileRepo.save(p);
            });

    List<VisitRecord> visits = visitRepo.findByClientProfileIdOrderByVisitDateDesc(profile.getId());
    List<VisitRecordResponse> visitResponses = visits.stream()
            .map(this::toVisitResponse)
            .toList();

    List<ClientReminder> reminders = reminderRepo.findByUserIdAndSentFalseOrderByRecommendedDateAsc(userId);
    List<ReminderResponse> reminderResponses = reminders.stream()
            .map(this::toReminderResponse)
            .toList();

    return new ClientHistoryResponse(
            clientInfo[0],
            clientInfo[1],
            toProfileResponse(profile),
            visitResponses,
            reminderResponses
    );
}
```

- [ ] **Step 4: Update updateProfile() to set audit fields**

Add `Long modifierId` parameter:

```java
@Transactional
public ClientProfileResponse updateProfile(Long userId, UpdateClientProfileRequest request, Long modifierId) {
    ClientProfile profile = profileRepo.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("Client profile not found for userId: " + userId));
    if (request.notes() != null) profile.setNotes(request.notes());
    if (request.skinType() != null) profile.setSkinType(request.skinType());
    if (request.hairType() != null) profile.setHairType(request.hairType());
    if (request.allergies() != null) profile.setAllergies(request.allergies());
    if (request.preferences() != null) profile.setPreferences(request.preferences());
    profile.setUpdatedAt(LocalDateTime.now());
    profile.setUpdatedBy(modifierId);
    return toProfileResponse(profileRepo.save(profile));
}
```

- [ ] **Step 5: Update createVisitRecord() to set audit fields**

Add `Long creatorId` parameter:

```java
@Transactional
public VisitRecordResponse createVisitRecord(Long userId, CreateVisitRecordRequest request, Long creatorId) {
    // ... existing logic ...
    visit.setUpdatedBy(creatorId);
    visit.setUpdatedAt(LocalDateTime.now());
    visit = visitRepo.save(visit);
    return toVisitResponse(visit);
}
```

- [ ] **Step 6: Update addVisitPhoto() to set uploadedBy**

Add `Long uploaderId` parameter:

```java
@Transactional
public VisitPhotoResponse addVisitPhoto(Long visitRecordId, MultipartFile photo, PhotoType type, Long uploaderId) {
    // ... existing logic ...
    vp.setUploadedBy(uploaderId);
    vp = photoRepo.save(vp);
    return toPhotoResponse(vp);
}
```

- [ ] **Step 7: Update createReminder() to set createdBy**

Add `Long creatorId` parameter:

```java
@Transactional
public ReminderResponse createReminder(Long userId, CreateReminderRequest request, Long creatorId) {
    // ... existing logic ...
    reminder.setCreatedBy(creatorId);
    return toReminderResponse(reminderRepo.save(reminder));
}
```

- [ ] **Step 8: Update mapper methods to include audit info**

```java
private ClientProfileResponse toProfileResponse(ClientProfile p) {
    return new ClientProfileResponse(
            p.getId(), p.getUserId(), p.getNotes(), p.getSkinType(), p.getHairType(),
            p.getAllergies(), p.getPreferences(), p.isConsentPhotos(), p.isConsentPublicShare(),
            p.getConsentGivenAt(), p.getCreatedAt(),
            p.getUpdatedAt(), resolveUserName(p.getUpdatedBy())
    );
}

private VisitRecordResponse toVisitResponse(VisitRecord v) {
    List<VisitPhoto> photos = photoRepo.findByVisitRecordIdOrderByImageOrderAsc(v.getId());
    List<VisitPhotoResponse> photoResponses = photos.stream()
            .map(this::toPhotoResponse)
            .toList();
    return new VisitRecordResponse(
            v.getId(), v.getClientProfileId(), v.getBookingId(), v.getCareId(),
            v.getCareName(), v.getVisitDate(), v.getPractitionerNotes(), v.getProductsUsed(),
            v.getSatisfactionScore(), v.getSatisfactionComment(), v.getCreatedAt(),
            v.getUpdatedAt(), resolveUserName(v.getUpdatedBy()),
            photoResponses
    );
}

private VisitPhotoResponse toPhotoResponse(VisitPhoto p) {
    return new VisitPhotoResponse(
            p.getId(), p.getPhotoType(), toImageUrl(p), p.getImageOrder(),
            resolveUserName(p.getUploadedBy())
    );
}

private ReminderResponse toReminderResponse(ClientReminder r) {
    return new ReminderResponse(
            r.getId(), r.getUserId(), r.getCareId(), r.getCareName(),
            r.getRecommendedDate(), r.getMessage(), r.isSent(), r.getCreatedAt(),
            resolveUserName(r.getCreatedBy())
    );
}
```

- [ ] **Step 9: Update TrackingController to pass UserPrincipal**

In `TrackingController.java`, add `@AuthenticationPrincipal UserPrincipal principal` to PRO endpoints and pass `principal.getId()` to service methods:

```java
@PutMapping("/api/pro/tracking/clients/{userId}/profile")
public ClientProfileResponse updateClientProfile(
        @PathVariable Long userId,
        @RequestBody UpdateClientProfileRequest request,
        @AuthenticationPrincipal UserPrincipal principal) {
    return trackingService.updateProfile(userId, request, principal.getId());
}

@PostMapping("/api/pro/tracking/clients/{userId}/visits")
@ResponseStatus(HttpStatus.CREATED)
public VisitRecordResponse createVisitRecord(
        @PathVariable Long userId,
        @RequestBody CreateVisitRecordRequest request,
        @AuthenticationPrincipal UserPrincipal principal) {
    return trackingService.createVisitRecord(userId, request, principal.getId());
}

@PostMapping("/api/pro/tracking/visits/{visitId}/photos")
@ResponseStatus(HttpStatus.CREATED)
public VisitPhotoResponse uploadVisitPhoto(
        @PathVariable Long visitId,
        @RequestParam("photo") MultipartFile photo,
        @RequestParam("type") PhotoType type,
        @AuthenticationPrincipal UserPrincipal principal) {
    return trackingService.addVisitPhoto(visitId, photo, type, principal.getId());
}

@PostMapping("/api/pro/tracking/clients/{userId}/reminders")
@ResponseStatus(HttpStatus.CREATED)
public ReminderResponse createReminder(
        @PathVariable Long userId,
        @RequestBody CreateReminderRequest request,
        @AuthenticationPrincipal UserPrincipal principal) {
    return trackingService.createReminder(userId, request, principal.getId());
}
```

- [ ] **Step 10: Verify compilation**

Run: `cd backend && ./mvnw compile -q`

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tracking/
git commit -m "feat: add audit trail and client name resolution to tracking service"
```

---

### Task 6: Backend — Employee permission controller + employee tracking endpoints

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/employee/web/dto/EmployeePermissionResponse.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/web/dto/UpdatePermissionsRequest.java`
- Create: `backend/src/main/java/com/prettyface/app/employee/web/EmployeePermissionController.java`
- Modify: `backend/src/main/java/com/prettyface/app/tracking/web/TrackingController.java`
- Modify: `backend/src/main/java/com/prettyface/app/config/SecurityConfig.java`

- [ ] **Step 1: Create EmployeePermissionResponse**

```java
package com.prettyface.app.employee.web.dto;

import java.util.Map;

public record EmployeePermissionResponse(
        Map<String, String> permissions
) {}
```

- [ ] **Step 2: Create UpdatePermissionsRequest**

```java
package com.prettyface.app.employee.web.dto;

import java.util.Map;

public record UpdatePermissionsRequest(
        Map<String, String> permissions
) {}
```

- [ ] **Step 3: Create EmployeePermissionController**

```java
package com.prettyface.app.employee.web;

import com.prettyface.app.employee.app.EmployeePermissionService;
import com.prettyface.app.employee.domain.AccessLevel;
import com.prettyface.app.employee.domain.PermissionDomain;
import com.prettyface.app.employee.web.dto.EmployeePermissionResponse;
import com.prettyface.app.employee.web.dto.UpdatePermissionsRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pro/employees")
public class EmployeePermissionController {

    private final EmployeePermissionService permissionService;

    public EmployeePermissionController(EmployeePermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/{employeeId}/permissions")
    public EmployeePermissionResponse getPermissions(@PathVariable Long employeeId) {
        Map<PermissionDomain, AccessLevel> perms = permissionService.getPermissions(employeeId);
        Map<String, String> result = perms.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), e -> e.getValue().name()));
        return new EmployeePermissionResponse(result);
    }

    @PutMapping("/{employeeId}/permissions")
    public EmployeePermissionResponse updatePermissions(
            @PathVariable Long employeeId,
            @RequestBody UpdatePermissionsRequest request) {
        Map<PermissionDomain, AccessLevel> updates = request.permissions().entrySet().stream()
                .collect(Collectors.toMap(
                        e -> PermissionDomain.valueOf(e.getKey()),
                        e -> AccessLevel.valueOf(e.getValue())
                ));
        Map<PermissionDomain, AccessLevel> result = permissionService.updatePermissions(employeeId, updates);
        Map<String, String> response = result.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), e -> e.getValue().name()));
        return new EmployeePermissionResponse(response);
    }
}
```

- [ ] **Step 4: Add employee tracking endpoints to TrackingController**

Add these endpoints after the CLIENT section:

```java
// ── EMPLOYEE endpoints (/api/employee/tracking) ──

@GetMapping("/api/employee/tracking/clients/{userId}")
public ClientHistoryResponse getClientHistoryAsEmployee(
        @PathVariable Long userId,
        @AuthenticationPrincipal UserPrincipal principal) {
    Long employeeId = resolveEmployeeId(principal.getId());
    permissionService.requireAccess(employeeId, PermissionDomain.PROFILE, AccessLevel.READ);
    return trackingService.getClientHistory(userId);
}

@PutMapping("/api/employee/tracking/clients/{userId}/profile")
public ClientProfileResponse updateClientProfileAsEmployee(
        @PathVariable Long userId,
        @RequestBody UpdateClientProfileRequest request,
        @AuthenticationPrincipal UserPrincipal principal) {
    Long employeeId = resolveEmployeeId(principal.getId());
    permissionService.requireAccess(employeeId, PermissionDomain.PROFILE, AccessLevel.WRITE);
    return trackingService.updateProfile(userId, request, principal.getId());
}

@PostMapping("/api/employee/tracking/clients/{userId}/visits")
@ResponseStatus(HttpStatus.CREATED)
public VisitRecordResponse createVisitRecordAsEmployee(
        @PathVariable Long userId,
        @RequestBody CreateVisitRecordRequest request,
        @AuthenticationPrincipal UserPrincipal principal) {
    Long employeeId = resolveEmployeeId(principal.getId());
    permissionService.requireAccess(employeeId, PermissionDomain.VISITS, AccessLevel.WRITE);
    return trackingService.createVisitRecord(userId, request, principal.getId());
}

@GetMapping("/api/employee/permissions/me")
public Map<String, String> getMyPermissions(@AuthenticationPrincipal UserPrincipal principal) {
    Long employeeId = resolveEmployeeId(principal.getId());
    return permissionService.getPermissions(employeeId).entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(e -> e.getKey().name(), e -> e.getValue().name()));
}
```

Inject `EmployeePermissionService` and `EmployeeRepository` into `TrackingController`. Add helper:

```java
private Long resolveEmployeeId(Long userId) {
    return employeeRepo.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not an employee"))
            .getId();
}
```

- [ ] **Step 5: Update SecurityConfig**

Add to the `authorizeHttpRequests` section:
```java
.requestMatchers("/api/employee/tracking/**").hasAnyRole("EMPLOYEE", "PRO", "ADMIN")
.requestMatchers("/api/employee/permissions/**").hasAnyRole("EMPLOYEE", "PRO", "ADMIN")
```

- [ ] **Step 6: Verify compilation and tests**

Run: `cd backend && ./mvnw compile -q && ./mvnw test -q`

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/employee/web/ backend/src/main/java/com/prettyface/app/tracking/web/TrackingController.java backend/src/main/java/com/prettyface/app/config/SecurityConfig.java
git commit -m "feat: add employee permission controller and tracking endpoints"
```

---

### Task 7: Frontend — Update models and services

**Files:**
- Modify: `frontend/src/app/features/tracking/tracking.model.ts`
- Modify: `frontend/src/app/features/tracking/tracking.service.ts`

- [ ] **Step 1: Update tracking.model.ts**

Add audit fields and client name to existing interfaces, plus permission types:

```typescript
// Add to ClientHistoryResponse:
clientName: string;
clientEmail: string | null;

// Add to ClientProfileResponse:
updatedAt: string | null;
updatedByName: string | null;

// Add to VisitRecordResponse:
updatedAt: string | null;
updatedByName: string | null;

// Add to VisitPhotoResponse:
uploadedByName: string | null;

// Add to ReminderResponse:
createdByName: string | null;

// New interfaces:
export type PermissionDomain = 'PROFILE' | 'VISITS' | 'PHOTOS' | 'REMINDERS';
export type AccessLevel = 'NONE' | 'READ' | 'WRITE';
export type PermissionsMap = Record<PermissionDomain, AccessLevel>;
```

- [ ] **Step 2: Update tracking.service.ts**

Add employee-specific methods:

```typescript
getClientHistoryAsEmployee(userId: number): Observable<ClientHistoryResponse> {
  return this.http.get<ClientHistoryResponse>(`${this.apiBaseUrl}/api/employee/tracking/clients/${userId}`);
}

getMyPermissions(): Observable<Record<string, string>> {
  return this.http.get<Record<string, string>>(`${this.apiBaseUrl}/api/employee/permissions/me`);
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/tracking/
git commit -m "feat: update tracking models and service with audit fields and permissions"
```

---

### Task 8: Frontend — Create sub-components (client-header, visit-card)

**Files:**
- Create: `frontend/src/app/features/tracking/components/client-header/client-header.component.ts`
- Create: `frontend/src/app/features/tracking/components/visit-card/visit-card.component.ts`

- [ ] **Step 1: Create client-header component**

Standalone component with inputs: `clientName`, `clientEmail`, `profile` (ClientProfileResponse), `visitCount`, `createdAt`. Displays avatar circle (#c06 background, white initials), name, "depuis" date, badges, allergy alert.

The engineer should read the existing header section in `pro-client-detail.component.ts` template (lines 69-100 approximately), extract it into this component, and apply the Dashboard B styling:
- Avatar: 48px circle `background: #c06`, white initials
- Card: `background: white`, `border-radius: 12px`, `box-shadow: 0 1px 4px rgba(0,0,0,0.06)`, `padding: 14px`, `display: flex`, `gap: 12px`
- Allergy alert: separate card `background: #fef2f2`, `border: 1px solid #fecaca`

- [ ] **Step 2: Create visit-card component**

Standalone component with inputs: `visit` (VisitRecordResponse), `apiBaseUrl` (string). Displays care name, date, practitioner (updatedByName), notes preview, photo thumbnails.

Styling: white card, `border-radius: 12px`, `box-shadow`, care name 13px bold, date/practitioner 11px gray.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/tracking/components/
git commit -m "feat: create client-header and visit-card sub-components"
```

---

### Task 9: Frontend — Create sub-components (client-visits, client-notes, client-info)

**Files:**
- Create: `frontend/src/app/features/tracking/components/client-visits/client-visits.component.ts`
- Create: `frontend/src/app/features/tracking/components/client-notes/client-notes.component.ts`
- Create: `frontend/src/app/features/tracking/components/client-info/client-info.component.ts`

- [ ] **Step 1: Create client-visits component**

Inputs: `visits` (VisitRecordResponse[]), `accessLevel` (AccessLevel), `cares` (Care[]), `apiBaseUrl`.
Outputs: `createVisit` (EventEmitter), `uploadPhoto` (EventEmitter).

Shows "Dernière visite" with one visit-card, "Voir tout →" link that toggles `showAll` signal to display all visits. "+ Nouvelle visite" button (visible only if `accessLevel === 'WRITE'`).

- [ ] **Step 2: Create client-notes component**

Inputs: `notes` (string), `updatedByName` (string), `updatedAt` (string), `accessLevel` (AccessLevel).
Outputs: `saveNotes` (EventEmitter<string>).

Section title "Notes praticien", editable textarea, audit line, save button (if WRITE).

- [ ] **Step 3: Create client-info component**

Inputs: `profile` (ClientProfileResponse), `accessLevel` (AccessLevel).
Outputs: `saveInfo` (EventEmitter<UpdateProfileRequest>).

Section title "Fiche client", 2×2 grid (peau, cheveux, allergies, preferences), edit mode toggle, audit line.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/tracking/components/
git commit -m "feat: create client-visits, client-notes, client-info sub-components"
```

---

### Task 10: Frontend — Rewrite pro-client-detail as orchestrator

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-client-detail.component.ts`

- [ ] **Step 1: Rewrite the component**

Replace the 1146-line monolith with a slim orchestrator that:
- Loads `ClientHistoryResponse` via `TrackingService`
- Loads available cares via `CaresService`
- Passes data to sub-components via `input()`
- Handles `output()` events (save, create, upload) by calling service methods
- Uses Dashboard B layout: `:host { background: #f5f4f2; min-height: 100vh; padding: 16px; }`

Template structure:
```html
<app-client-header [clientName]="..." [profile]="..." ... />
<app-client-visits [visits]="..." [accessLevel]="'WRITE'" ... (createVisit)="..." />
<app-client-notes [notes]="..." [accessLevel]="'WRITE'" ... (saveNotes)="..." />
<app-client-info [profile]="..." [accessLevel]="'WRITE'" ... (saveInfo)="..." />
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/pro/pro-client-detail.component.ts
git commit -m "feat: rewrite pro-client-detail as orchestrator with sub-components"
```

---

### Task 11: Frontend — Employee client detail route + permissions UI

**Files:**
- Create: `frontend/src/app/pages/employee/employee-client-detail.component.ts`
- Modify: `frontend/src/app/app.routes.ts`

- [ ] **Step 1: Create employee-client-detail component**

Thin wrapper: loads permissions via `trackingService.getMyPermissions()`, then uses the same sub-components with the resolved access levels.

- [ ] **Step 2: Add route**

In `app.routes.ts`, add to the employee children:
```typescript
{
  path: 'clients/:userId',
  loadComponent: () => import('./pages/employee/employee-client-detail.component').then(m => m.EmployeeClientDetailComponent),
},
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/employee/employee-client-detail.component.ts frontend/src/app/app.routes.ts
git commit -m "feat: add employee client detail page with permission enforcement"
```

---

### Task 12: Frontend — i18n translations + Permission UI in employee management

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add French translations**

```json
"tracking": {
  "lastVisit": "Dernière visite",
  "viewAll": "Voir tout",
  "practitionerNotes": "Notes praticien",
  "clientInfo": "Fiche client",
  "newVisit": "Nouvelle visite",
  "modifiedBy": "Modifié par",
  "addedBy": "Ajouté par",
  "createdBy": "Créé par",
  "allergiesAlert": "Allergies",
  "skinType": "Type de peau",
  "hairType": "Cheveux",
  "preferences": "Préférences",
  "reminder": "Rappel"
},
"permissions": {
  "title": "Permissions",
  "profile": "Profil client",
  "visits": "Visites",
  "photos": "Photos",
  "reminders": "Rappels",
  "none": "Aucun",
  "read": "Lecture",
  "write": "Écriture"
}
```

- [ ] **Step 2: Add English translations**

```json
"tracking": {
  "lastVisit": "Last visit",
  "viewAll": "View all",
  "practitionerNotes": "Practitioner notes",
  "clientInfo": "Client info",
  "newVisit": "New visit",
  "modifiedBy": "Modified by",
  "addedBy": "Added by",
  "createdBy": "Created by",
  "allergiesAlert": "Allergies",
  "skinType": "Skin type",
  "hairType": "Hair type",
  "preferences": "Preferences",
  "reminder": "Reminder"
},
"permissions": {
  "title": "Permissions",
  "profile": "Client profile",
  "visits": "Visits",
  "photos": "Photos",
  "reminders": "Reminders",
  "none": "None",
  "read": "Read",
  "write": "Write"
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/public/i18n/
git commit -m "feat: add tracking and permissions i18n translations"
```

---

### Task 13: Full integration verification

- [ ] **Step 1: Run all backend tests**

Run: `cd backend && ./mvnw test -q`

- [ ] **Step 2: Build frontend**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -10`

- [ ] **Step 3: Rebuild Docker**

Run: `docker compose --profile dev up -d --force-recreate frontend-dev`

- [ ] **Step 4: Commit any remaining changes**
