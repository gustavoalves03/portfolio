package com.luxpretty.app.tracking.app;

import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.employee.app.EmployeePermissionService;
import com.luxpretty.app.employee.domain.AccessLevel;
import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.domain.PermissionDomain;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.multitenancy.ApplicationSchemaExecutor;
import com.luxpretty.app.tracking.domain.ClientProfile;
import com.luxpretty.app.tracking.domain.VisitRecord;
import com.luxpretty.app.tracking.repo.ClientProfileRepository;
import com.luxpretty.app.tracking.repo.ClientReminderRepository;
import com.luxpretty.app.tracking.repo.VisitPhotoRepository;
import com.luxpretty.app.tracking.repo.VisitRecordRepository;
import com.luxpretty.app.tracking.web.dto.VisitRecordResponse;
import com.luxpretty.app.users.domain.AuthProvider;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Lot2 Sec1 — Scenario #103: Client cannot rate another client's visit.
 *
 * After Fix3 (service-layer access-level enforcement), {@link TrackingService#rateVisit}
 * consults {@link EmployeePermissionService} for non-self callers. A client calling
 * {@code rateVisit} for a visit that does not belong to them (and whose caller role is
 * not PRO/ADMIN) is now rejected at the service layer with 403.
 */
@ExtendWith(MockitoExtension.class)
class TrackingServiceTests {

    @Mock private ClientProfileRepository profileRepo;
    @Mock private VisitRecordRepository visitRepo;
    @Mock private VisitPhotoRepository photoRepo;
    @Mock private ClientReminderRepository reminderRepo;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationSchemaExecutor applicationSchemaExecutor;
    @Mock private EmployeePermissionService permissionService;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private com.luxpretty.app.common.storage.StorageBackend storageBackend;
    @Mock private com.luxpretty.app.users.app.UserRoleService userRoleService;

    @InjectMocks
    private TrackingService service;

    private static UserPrincipal principal(Long id) {
        return new UserPrincipal(id, "user" + id + "@example.com", "User " + id, null);
    }

    private void stubSchemaExecutor() {
        lenient().when(applicationSchemaExecutor.call(any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
    }

    private void stubCallerAsOwner(Long callerId) {
        stubSchemaExecutor();
        lenient().when(userRoleService.hasAnyRoleAcrossScopes(callerId,
                com.luxpretty.app.users.domain.Role.PRO,
                com.luxpretty.app.users.domain.Role.ADMIN)).thenReturn(true);
    }

    @Test
    @DisplayName("rateVisit — valid score (1–5) updates the visit")
    void rateVisit_validScore_updates() {
        Long callerId = 500L; // same as clientProfileId — but self-rating is matched by userId, not profileId
        Long ownerUserId = 42L;
        VisitRecord visit = new VisitRecord();
        visit.setId(10L);
        visit.setClientProfileId(500L);

        // Caller is the OWNER of the visit — looked up via ClientProfile.userId
        ClientProfile profile = new ClientProfile();
        profile.setId(500L);
        profile.setUserId(ownerUserId);
        when(profileRepo.findById(500L)).thenReturn(Optional.of(profile));

        when(visitRepo.findById(10L)).thenReturn(Optional.of(visit));
        when(visitRepo.save(any(VisitRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(photoRepo.findByVisitRecordIdOrderByImageOrderAsc(10L)).thenReturn(List.of());

        VisitRecordResponse result = service.rateVisit(10L, 5, "Excellent", principal(ownerUserId));

        assertThat(result.satisfactionScore()).isEqualTo(5);
        assertThat(result.satisfactionComment()).isEqualTo("Excellent");
    }

    @Test
    @DisplayName("rateVisit — score 0 rejected")
    void rateVisit_scoreTooLow_rejected() {
        assertThatThrownBy(() -> service.rateVisit(10L, 0, "bad", principal(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 5");
    }

    @Test
    @DisplayName("rateVisit — score 6 rejected")
    void rateVisit_scoreTooHigh_rejected() {
        assertThatThrownBy(() -> service.rateVisit(10L, 6, "?", principal(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 5");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lot2 Sec1 / Fix3: Cross-client IDOR on visit rating ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Lot2#103 / Fix3: rateVisit_crossClient_throwsForbidden — service rejects non-owner, non-PRO callers")
    void rateVisit_crossClient_throwsForbidden() {
        // Fix3: TrackingService.rateVisit now calls requireTrackingAccess, which for a
        // non-self, non-PRO, non-employee caller throws 403. An attacker USER trying to
        // rate another client's visit is blocked at the service layer.
        Long attackerId = 123L;
        Long victimUserId = 777L;
        VisitRecord victimVisit = new VisitRecord();
        victimVisit.setId(999L);
        victimVisit.setClientProfileId(555L); // profile of another client

        ClientProfile victimProfile = new ClientProfile();
        victimProfile.setId(555L);
        victimProfile.setUserId(victimUserId);
        when(profileRepo.findById(555L)).thenReturn(Optional.of(victimProfile));

        when(visitRepo.findById(999L)).thenReturn(Optional.of(victimVisit));

        // Attacker is a plain USER — no PRO/ADMIN assignment (default mock false), not an employee.
        stubSchemaExecutor();
        when(employeeRepository.findByUserId(attackerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.rateVisit(999L, 1, "cross-client drive-by rating", principal(attackerId)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        // Nothing is persisted.
        verify(visitRepo, never()).save(any(VisitRecord.class));
    }

    @Test
    @DisplayName("Fix3: rateVisit_employeeWithoutVisitsWrite_throwsForbidden — employee gate enforced at service layer")
    void rateVisit_employeeWithoutVisitsWrite_throwsForbidden() {
        Long employeeUserId = 321L;
        Long employeeId = 88L;
        Long victimUserId = 777L;
        VisitRecord victimVisit = new VisitRecord();
        victimVisit.setId(999L);
        victimVisit.setClientProfileId(555L);

        ClientProfile profile = new ClientProfile();
        profile.setId(555L);
        profile.setUserId(victimUserId);
        when(profileRepo.findById(555L)).thenReturn(Optional.of(profile));
        when(visitRepo.findById(999L)).thenReturn(Optional.of(victimVisit));

        stubSchemaExecutor();
        // Employee — no PRO/ADMIN role across scopes (default mock false).

        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setUserId(employeeUserId);
        when(employeeRepository.findByUserId(employeeUserId)).thenReturn(Optional.of(employee));

        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permission"))
                .when(permissionService).requireAccess(employeeId, PermissionDomain.VISITS, AccessLevel.WRITE);

        assertThatThrownBy(() ->
                service.rateVisit(999L, 1, "nope", principal(employeeUserId)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(visitRepo, never()).save(any(VisitRecord.class));
    }
}
