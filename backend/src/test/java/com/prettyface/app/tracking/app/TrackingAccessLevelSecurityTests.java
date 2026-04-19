package com.prettyface.app.tracking.app;

import com.prettyface.app.employee.app.EmployeePermissionService;
import com.prettyface.app.employee.domain.AccessLevel;
import com.prettyface.app.employee.domain.EmployeePermission;
import com.prettyface.app.employee.domain.PermissionDomain;
import com.prettyface.app.employee.repo.EmployeePermissionRepository;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.tracking.domain.ClientProfile;
import com.prettyface.app.tracking.repo.ClientProfileRepository;
import com.prettyface.app.tracking.repo.ClientReminderRepository;
import com.prettyface.app.tracking.repo.VisitPhotoRepository;
import com.prettyface.app.tracking.repo.VisitRecordRepository;
import com.prettyface.app.tracking.web.dto.CreateVisitRecordRequest;
import com.prettyface.app.tracking.web.dto.UpdateClientProfileRequest;
import com.prettyface.app.users.repo.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Sec2 — Access level on tracking permissions.
 *
 * The authorization model is split: {@link EmployeePermissionService#requireAccess} is the
 * gate, invoked from {@link com.prettyface.app.tracking.web.TrackingController}. The
 * {@link TrackingService} itself performs NO per-accessLevel check. These tests:
 *   1) Confirm the gate rejects NONE / READ callers trying to perform higher-level actions.
 *   2) Document the gap: if the service is called directly (another controller, a scheduled
 *      job, a developer mistake) there is no second line of defense.
 */
@ExtendWith(MockitoExtension.class)
class TrackingAccessLevelSecurityTests {

    // ── Dependencies for EmployeePermissionService (the gate) ──
    @Mock private EmployeePermissionRepository permissionRepo;
    @Mock private EmployeeRepository employeeRepo;

    @InjectMocks
    private EmployeePermissionService permissionService;

    // ── Dependencies for TrackingService (the gap) ──
    @Mock private ClientProfileRepository profileRepo;
    @Mock private VisitRecordRepository visitRepo;
    @Mock private VisitPhotoRepository photoRepo;
    @Mock private ClientReminderRepository reminderRepo;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationSchemaExecutor applicationSchemaExecutor;

    private TrackingService trackingService() {
        lenient().when(applicationSchemaExecutor.call(any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        return new TrackingService(profileRepo, visitRepo, photoRepo, reminderRepo,
                userRepository, applicationSchemaExecutor);
    }

    // ══════════════════════════════════════════════════════════════
    // (1) The gate — EmployeePermissionService.requireAccess
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Sec2: addVisit_employeeWithReadOnlyVisitAccess_throwsForbidden — gate rejects READ when WRITE required")
    void addVisit_employeeWithReadOnlyVisitAccess_throwsForbidden() {
        // NOTE-SEC: permission gate correctly rejects READ < WRITE for VISITS domain.
        // This is what TrackingController.createVisitRecordAsEmployee relies on.
        EmployeePermission perm = new EmployeePermission();
        perm.setEmployeeId(42L);
        perm.setDomain(PermissionDomain.VISITS);
        perm.setAccessLevel(AccessLevel.READ);
        when(permissionRepo.findByEmployeeIdAndDomain(42L, PermissionDomain.VISITS))
                .thenReturn(Optional.of(perm));

        assertThatThrownBy(() ->
                permissionService.requireAccess(42L, PermissionDomain.VISITS, AccessLevel.WRITE))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("Insufficient permission");
    }

    @Test
    @DisplayName("Sec2: addNote_employeeWithNoProfileAccess_throwsForbidden — gate rejects NONE for PROFILE write")
    void addNote_employeeWithNoProfileAccess_throwsForbidden() {
        // NOTE-SEC: employee with NO permission row for PROFILE falls back to AccessLevel.NONE
        // (see EmployeePermissionService.checkAccess). requireAccess(..., WRITE) must reject.
        when(permissionRepo.findByEmployeeIdAndDomain(42L, PermissionDomain.PROFILE))
                .thenReturn(Optional.empty()); // no row -> NONE

        assertThatThrownBy(() ->
                permissionService.requireAccess(42L, PermissionDomain.PROFILE, AccessLevel.WRITE))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("requires WRITE")
                .hasMessageContaining("has NONE");
    }

    @Test
    @DisplayName("Sec2: getPermissions_fillsMissingDomainsWithNone — employee with no rows gets all NONE")
    void getPermissions_fillsMissingDomainsWithNone() {
        // NOTE-SEC: when iterating domains an unset entry becomes NONE — safe default.
        when(permissionRepo.findByEmployeeId(42L)).thenReturn(List.of());

        var perms = permissionService.getPermissions(42L);

        assertThat(perms).containsEntry(PermissionDomain.PROFILE, AccessLevel.NONE);
        assertThat(perms).containsEntry(PermissionDomain.VISITS, AccessLevel.NONE);
        assertThat(perms).containsEntry(PermissionDomain.PHOTOS, AccessLevel.NONE);
        assertThat(perms).containsEntry(PermissionDomain.REMINDERS, AccessLevel.NONE);
    }

    // ══════════════════════════════════════════════════════════════
    // (2) The gap — TrackingService itself performs no accessLevel check
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Sec2: getClientHistory_WARN_serviceHasNoAccessLevelCheck — any caller reaching the service gets full history")
    void getClientHistory_WARN_serviceHasNoAccessLevelCheck() {
        // TODO-SEC: TrackingService has no per-accessLevel gate. Authorization lives only
        // in TrackingController.getClientHistoryAsEmployee via permissionService.requireAccess(...).
        // If another entry point (a scheduled job, an admin controller, a future endpoint)
        // calls the service directly, visits and profile data are returned with no check.
        TrackingService service = trackingService();

        ClientProfile profile = new ClientProfile();
        profile.setId(1L);
        profile.setUserId(7L);
        when(profileRepo.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(visitRepo.findByClientProfileIdOrderByVisitDateDesc(1L)).thenReturn(List.of());
        when(reminderRepo.findByUserIdAndSentFalseOrderByRecommendedDateAsc(7L))
                .thenReturn(List.of());
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        var history = service.getClientHistory(7L);

        // No ForbiddenException, no AccessLevel check — returns data unconditionally.
        assertThat(history).isNotNull();
        assertThat(history.visits()).isEmpty();
    }

    @Test
    @DisplayName("Sec2: createVisitRecord_WARN_serviceHasNoAccessLevelCheck — WRITE is enforced only by the controller")
    void createVisitRecord_WARN_serviceHasNoAccessLevelCheck() {
        // TODO-SEC: TrackingService.createVisitRecord does not consult
        // EmployeePermissionService — an employee with VISITS=NONE/READ would be blocked
        // at the controller, but the service itself will happily persist a visit record
        // if called from any other place.
        TrackingService service = trackingService();

        ClientProfile profile = new ClientProfile();
        profile.setId(1L);
        profile.setUserId(7L);
        when(profileRepo.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(visitRepo.save(any())).thenAnswer(inv -> {
            var v = (com.prettyface.app.tracking.domain.VisitRecord) inv.getArgument(0);
            v.setId(100L);
            return v;
        });
        when(photoRepo.findByVisitRecordIdOrderByImageOrderAsc(100L)).thenReturn(List.of());

        CreateVisitRecordRequest req = new CreateVisitRecordRequest(
                null, null, "Soin visage",
                LocalDate.now(), "notes", "products");

        var result = service.createVisitRecord(7L, req, 42L);

        assertThat(result).isNotNull();
        assertThat(result.careName()).isEqualTo("Soin visage");
    }

    @Test
    @DisplayName("Sec2: updateProfile_WARN_serviceHasNoAccessLevelCheck — profile write bypass if service called directly")
    void updateProfile_WARN_serviceHasNoAccessLevelCheck() {
        // TODO-SEC: TrackingService.updateProfile does not check PROFILE=WRITE.
        TrackingService service = trackingService();

        ClientProfile profile = new ClientProfile();
        profile.setId(1L);
        profile.setUserId(7L);
        when(profileRepo.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(profileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateClientProfileRequest req = new UpdateClientProfileRequest(
                "injected notes", null, null, null, null);

        var result = service.updateProfile(7L, req, 42L);

        assertThat(result.notes()).isEqualTo("injected notes");
    }

    // ══════════════════════════════════════════════════════════════
    // Lot3 Sec66-69 — Full access-level matrix on EmployeePermissionService.requireAccess
    // ──────────────────────────────────────────────────────────────
    // NOTE-SEC (scope): The spec mentions a (VISITS, PROFILE, NOTES) × (READ, WRITE)
    // matrix. The actual production PermissionDomain enum defines
    //   PROFILE, VISITS, PHOTOS, REMINDERS
    // There is NO "NOTES" domain. Rather than skip coverage we exercise the full
    // matrix against the real domains. Any future addition of a NOTES domain
    // should mechanically extend the @CsvSource below.
    //
    // The three-cell truth table per (domain × required):
    //   actual = NONE  → always throws (0 < 1, 0 < 2)
    //   actual = READ  → passes for READ, throws for WRITE (1 < 2)
    //   actual = WRITE → always passes (2 >= 1, 2 >= 2)
    //
    // We parameterise the full 8×3 = 24 cases. Existing Sec2 tests already pin
    // two individual cells (VISITS+WRITE actual=READ, PROFILE+WRITE actual=NONE);
    // the matrix re-confirms them alongside the 22 new combinations.

    @ParameterizedTest(name = "Lot3 matrix: domain={0}, required={1}, actual={2} → shouldThrow={3}")
    @CsvSource({
            // VISITS × READ
            "VISITS,    READ,  NONE,  true",    // Sec66
            "VISITS,    READ,  READ,  false",
            "VISITS,    READ,  WRITE, false",
            // VISITS × WRITE
            "VISITS,    WRITE, NONE,  true",
            "VISITS,    WRITE, READ,  true",    // already covered by Sec2 (redundant, OK)
            "VISITS,    WRITE, WRITE, false",
            // PROFILE × READ
            "PROFILE,   READ,  NONE,  true",    // Sec67
            "PROFILE,   READ,  READ,  false",
            "PROFILE,   READ,  WRITE, false",
            // PROFILE × WRITE
            "PROFILE,   WRITE, NONE,  true",    // already covered by Sec2 (redundant, OK)
            "PROFILE,   WRITE, READ,  true",
            "PROFILE,   WRITE, WRITE, false",
            // PHOTOS × READ  (Sec68 — NEW domain, previously uncovered)
            "PHOTOS,    READ,  NONE,  true",
            "PHOTOS,    READ,  READ,  false",
            "PHOTOS,    READ,  WRITE, false",
            // PHOTOS × WRITE
            "PHOTOS,    WRITE, NONE,  true",
            "PHOTOS,    WRITE, READ,  true",
            "PHOTOS,    WRITE, WRITE, false",
            // REMINDERS × READ  (Sec69 — NEW domain, previously uncovered)
            "REMINDERS, READ,  NONE,  true",
            "REMINDERS, READ,  READ,  false",
            "REMINDERS, READ,  WRITE, false",
            // REMINDERS × WRITE
            "REMINDERS, WRITE, NONE,  true",
            "REMINDERS, WRITE, READ,  true",
            "REMINDERS, WRITE, WRITE, false"
    })
    @DisplayName("Lot3 Sec66-69: requireAccess enforces (domain × required × actual) monotonic ordinal check")
    void requireAccess_matrix_coversAllDomainLevelCombinations(
            String domainStr, String requiredStr, String actualStr, boolean shouldThrow) {
        PermissionDomain domain = PermissionDomain.valueOf(domainStr);
        AccessLevel required = AccessLevel.valueOf(requiredStr);
        AccessLevel actual = AccessLevel.valueOf(actualStr);

        // Stub: actual==NONE is represented by an ABSENT row (the production default).
        // actual==READ or WRITE is represented by a stored row at that level.
        if (actual == AccessLevel.NONE) {
            when(permissionRepo.findByEmployeeIdAndDomain(42L, domain))
                    .thenReturn(Optional.empty());
        } else {
            EmployeePermission perm = new EmployeePermission();
            perm.setEmployeeId(42L);
            perm.setDomain(domain);
            perm.setAccessLevel(actual);
            when(permissionRepo.findByEmployeeIdAndDomain(42L, domain))
                    .thenReturn(Optional.of(perm));
        }

        if (shouldThrow) {
            assertThatThrownBy(() -> permissionService.requireAccess(42L, domain, required))
                    .as("domain=%s required=%s actual=%s", domain, required, actual)
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN))
                    .hasMessageContaining("Insufficient permission for " + domain)
                    .hasMessageContaining("requires " + required)
                    .hasMessageContaining("has " + actual);
        } else {
            // Must complete without throwing
            permissionService.requireAccess(42L, domain, required);
        }
    }

    @Test
    @DisplayName("Lot3 Sec66-69b: requireAccess — missing permission row is treated as NONE (safe default)")
    void requireAccess_missingRow_isTreatedAsNone_forEveryDomain() {
        // NOTE-SEC: this is the anchor for the NONE-fallback behaviour. Every
        // domain, when there is no stored row, must behave as if actual=NONE.
        // The matrix above already exercises it; this test makes the intent
        // explicit and names it for the security review.
        for (PermissionDomain domain : PermissionDomain.values()) {
            when(permissionRepo.findByEmployeeIdAndDomain(99L, domain))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    permissionService.requireAccess(99L, domain, AccessLevel.READ))
                    .as("missing row for %s must throw when READ is required", domain)
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("has NONE");

            assertThatThrownBy(() ->
                    permissionService.requireAccess(99L, domain, AccessLevel.WRITE))
                    .as("missing row for %s must throw when WRITE is required", domain)
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("has NONE");
        }
    }
}
