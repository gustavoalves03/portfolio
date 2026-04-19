package com.prettyface.app.employee.app;

import com.prettyface.app.employee.domain.Employee;
import com.prettyface.app.employee.domain.LeaveRequest;
import com.prettyface.app.employee.domain.LeaveStatus;
import com.prettyface.app.employee.domain.LeaveType;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.employee.repo.LeaveRequestRepository;
import com.prettyface.app.employee.web.dto.LeaveRequestDto;
import com.prettyface.app.employee.web.dto.LeaveResponse;
import com.prettyface.app.employee.web.dto.LeaveReviewDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTests {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @InjectMocks
    private LeaveRequestService leaveRequestService;

    private Employee employee;
    private LeaveRequest pendingLeave;
    private LeaveRequest approvedLeave;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(1L);
        employee.setUserId(10L);
        employee.setName("Sophie Martin");
        employee.setEmail("sophie@prettyface.com");

        pendingLeave = new LeaveRequest();
        pendingLeave.setId(1L);
        pendingLeave.setEmployee(employee);
        pendingLeave.setType(LeaveType.VACATION);
        pendingLeave.setStatus(LeaveStatus.PENDING);
        pendingLeave.setStartDate(LocalDate.of(2025, 7, 1));
        pendingLeave.setEndDate(LocalDate.of(2025, 7, 14));
        pendingLeave.setReason("Summer holiday");

        approvedLeave = new LeaveRequest();
        approvedLeave.setId(2L);
        approvedLeave.setEmployee(employee);
        approvedLeave.setType(LeaveType.VACATION);
        approvedLeave.setStatus(LeaveStatus.APPROVED);
        approvedLeave.setStartDate(LocalDate.of(2025, 7, 1));
        approvedLeave.setEndDate(LocalDate.of(2025, 7, 14));
        approvedLeave.setReviewerNote("Approved.");
        approvedLeave.setReviewedAt(LocalDateTime.now().minusDays(1));
    }

    // ── createLeave ──

    @Test
    void createLeave_vacation_setsPending() {
        LeaveRequestDto dto = new LeaveRequestDto(
                LeaveType.VACATION,
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2025, 8, 10),
                "Beach vacation"
        );

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveRequestRepository.save(any())).thenAnswer(inv -> {
            LeaveRequest saved = inv.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        LeaveResponse response = leaveRequestService.createLeave(1L, dto);

        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);
        assertThat(response.type()).isEqualTo(LeaveType.VACATION);
        assertThat(response.employeeId()).isEqualTo(1L);
        assertThat(response.employeeName()).isEqualTo("Sophie Martin");
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2025, 8, 1));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2025, 8, 10));
        assertThat(response.reason()).isEqualTo("Beach vacation");
        assertThat(response.hasDocument()).isFalse();
    }

    @Test
    void createLeave_endBeforeStart_throws() {
        LeaveRequestDto dto = new LeaveRequestDto(
                LeaveType.VACATION,
                LocalDate.of(2025, 8, 10),
                LocalDate.of(2025, 8, 1),
                null
        );

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> leaveRequestService.createLeave(1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after start");

        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void createLeave_sameDay_succeeds() {
        LocalDate singleDay = LocalDate.of(2025, 9, 5);
        LeaveRequestDto dto = new LeaveRequestDto(
                LeaveType.SICKNESS,
                singleDay,
                singleDay,
                "Not feeling well"
        );

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveRequestRepository.save(any())).thenAnswer(inv -> {
            LeaveRequest saved = inv.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        LeaveResponse response = leaveRequestService.createLeave(1L, dto);

        assertThat(response.startDate()).isEqualTo(singleDay);
        assertThat(response.endDate()).isEqualTo(singleDay);
        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);
    }

    // ── reviewLeave ──

    @Test
    void reviewLeave_approve_setsApproved() {
        LeaveReviewDto dto = new LeaveReviewDto(LeaveStatus.APPROVED, "Approved — enjoy your holiday!");

        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(pendingLeave));
        when(leaveRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveResponse response = leaveRequestService.reviewLeave(1L, dto);

        assertThat(response.status()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(response.reviewerNote()).isEqualTo("Approved — enjoy your holiday!");
        assertThat(response.reviewedAt()).isNotNull();
    }

    @Test
    void reviewLeave_reject_setsRejected() {
        LeaveReviewDto dto = new LeaveReviewDto(LeaveStatus.REJECTED, "Cannot approve due to peak season.");

        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(pendingLeave));
        when(leaveRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveResponse response = leaveRequestService.reviewLeave(1L, dto);

        assertThat(response.status()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(response.reviewerNote()).isEqualTo("Cannot approve due to peak season.");
        assertThat(response.reviewedAt()).isNotNull();
    }

    @Test
    void reviewLeave_alreadyReviewed_throws() {
        LeaveReviewDto dto = new LeaveReviewDto(LeaveStatus.REJECTED, "Changed mind.");

        when(leaveRequestRepository.findById(2L)).thenReturn(Optional.of(approvedLeave));

        assertThatThrownBy(() -> leaveRequestService.reviewLeave(2L, dto))
                .isInstanceOf(IllegalStateException.class);

        verify(leaveRequestRepository, never()).save(any());
    }

    // ── listPending ──

    @Test
    void listPending_returnsPendingOnly() {
        when(leaveRequestRepository.findByStatusOrderByCreatedAtAsc(LeaveStatus.PENDING))
                .thenReturn(List.of(pendingLeave));

        List<LeaveResponse> result = leaveRequestService.listPending();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(LeaveStatus.PENDING);
    }

    // ── listByEmployee ──

    @Test
    void listByEmployee_returnsEmployeeLeaves() {
        when(leaveRequestRepository.findByEmployeeIdOrderByStartDateDesc(1L))
                .thenReturn(List.of(pendingLeave, approvedLeave));

        List<LeaveResponse> result = leaveRequestService.listByEmployee(1L);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.employeeId().equals(1L));
    }

    // ── isOnLeave ──

    @Test
    void isOnLeave_approvedLeaveOnDate_returnsTrue() {
        LocalDate dateInRange = LocalDate.of(2025, 7, 7);

        when(leaveRequestRepository
                .findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        1L, LeaveStatus.APPROVED, dateInRange, dateInRange))
                .thenReturn(List.of(approvedLeave));

        boolean result = leaveRequestService.isOnLeave(1L, dateInRange);

        assertThat(result).isTrue();
    }

    @Test
    void isOnLeave_noLeave_returnsFalse() {
        LocalDate date = LocalDate.of(2025, 6, 1);

        when(leaveRequestRepository
                .findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        1L, LeaveStatus.APPROVED, date, date))
                .thenReturn(List.of());

        boolean result = leaveRequestService.isOnLeave(1L, date);

        assertThat(result).isFalse();
    }

    // ── attachDocument ──

    @Test
    void attachDocument_setsPath() {
        String docPath = "uploads/leaves/1/medical-cert.pdf";

        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(pendingLeave));
        when(leaveRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveResponse response = leaveRequestService.attachDocument(1L, docPath);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getDocumentPath()).isEqualTo(docPath);
        assertThat(response.hasDocument()).isTrue();
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lot2 Sec1: Cross-tenant IDOR on leave requests ──
    // ══════════════════════════════════════════════════════════════
    // NOTE-SEC: LeaveRequest has no tenantSlug/tenantId column and is stored in
    // the per-tenant schema routed by TenantContext. LeaveRequestService
    // methods (listByEmployee, reviewLeave, createLeave, attachDocument) take
    // an arbitrary employeeId / leaveId and perform NO tenant or caller-scope
    // verification. If the schema router is bypassed OR a caller in salon A
    // knows the leaveId of a request in salon B, the service trusts the
    // schema-level isolation only.

    @Test
    @DisplayName("Lot2#41: listByEmployee_WARN_doesNotCheckTenantOwnership — NO tenant check (FINDING)")
    void listByEmployee_WARN_doesNotCheckTenantOwnership() {
        // TODO-SEC: LeaveRequestService.listByEmployee(employeeId) takes any
        // employeeId and returns that employee's leave requests. No ownership
        // check, no tenant check, no caller-scope check at the service layer.
        // If the schema router is bypassed, cross-tenant read of leaves is
        // possible.
        Long crossTenantEmployeeId = 777L;
        when(leaveRequestRepository.findByEmployeeIdOrderByStartDateDesc(crossTenantEmployeeId))
                .thenReturn(List.of(approvedLeave));

        // Service runs without verifying caller has access to employeeId=777
        List<LeaveResponse> result = leaveRequestService.listByEmployee(crossTenantEmployeeId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(LeaveStatus.APPROVED);
        // No tenant/caller lookup collaborator consulted — documents the gap.
    }

    @Test
    @DisplayName("Lot2#41b: reviewLeave_WARN_doesNotCheckTenantOwnership — can approve arbitrary leaveId")
    void reviewLeave_WARN_doesNotCheckTenantOwnership() {
        // TODO-SEC: LeaveRequestService.reviewLeave(leaveId, dto) looks up the
        // leave by id only. If an attacker guesses a leaveId from another
        // salon (and the schema router is bypassed), they can approve/reject it.
        LeaveReviewDto dto = new LeaveReviewDto(LeaveStatus.APPROVED, "cross-tenant review");
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(pendingLeave));
        when(leaveRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveResponse result = leaveRequestService.reviewLeave(1L, dto);

        assertThat(result.status()).isEqualTo(LeaveStatus.APPROVED);
        // No cross-check against caller's tenant/employee scope.
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lot3 Sec74: Self-approve leave ──
    // ══════════════════════════════════════════════════════════════
    // NOTE-SEC: LeaveRequestService.reviewLeave(leaveId, dto) performs no check
    // that the caller is distinct from the leave's owner employee. The
    // EmployeeLeaveController exposes PUT /api/pro/leaves/{leaveId}/review —
    // Spring Security's URL-prefix rule ("/api/pro/**" requires pro/owner role)
    // is the *only* guard. There is no service-layer protection against the
    // scenario where:
    //   - the "pro" role is shared by multiple people in the same salon,
    //   - a future /api/employee/... endpoint is added (same service),
    //   - a developer reuses the service from a non-"pro" controller.
    // In all those cases an employee could call reviewLeave(myOwnLeaveId, APPROVED)
    // and self-approve vacation. The service trusts that the controller did the
    // right thing. That is a brittle invariant — the service must also check.

    @Test
    @DisplayName("Lot3#74: reviewLeave_WARN_allowsSelfApproveBecauseNoCallerCheck — employee can approve own leave at service layer")
    void reviewLeave_WARN_allowsSelfApproveBecauseNoCallerCheck() {
        // TODO-SEC: reviewLeave has NO caller/principal argument. An employee
        // whose employeeId == pendingLeave.getEmployee().getId() can self-approve
        // their own leave if they reach this service. Guard should live here,
        // not only in the URL prefix of the controller.
        Long selfEmployeeId = employee.getId();          // the leave owner
        assertThat(pendingLeave.getEmployee().getId()).isEqualTo(selfEmployeeId);

        LeaveReviewDto selfApproveDto = new LeaveReviewDto(
                LeaveStatus.APPROVED, "approved by me, for me");
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(pendingLeave));
        when(leaveRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Service proceeds unconditionally — no "callerId != leave.owner" check
        LeaveResponse result = leaveRequestService.reviewLeave(1L, selfApproveDto);

        assertThat(result.status()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(result.reviewerNote()).isEqualTo("approved by me, for me");
        assertThat(result.employeeId()).isEqualTo(selfEmployeeId);
        // Documents the gap: the saved leave is APPROVED, owned by the same
        // employee who (hypothetically) just called reviewLeave.
        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(captor.getValue().getEmployee().getId()).isEqualTo(selfEmployeeId);
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lot3 Sec75: Read colleague's leave ──
    // ══════════════════════════════════════════════════════════════
    // NOTE-SEC: LeaveRequestService.listByEmployee(employeeId) takes any
    // employeeId and returns all their leaves. No check that the caller is
    // either (a) that employee, or (b) a pro/owner. Combined with the tenant
    // gap already documented in Lot2#41, this is also a *horizontal*
    // privilege-escalation gap: even inside the same salon, employee A could
    // read employee B's leave history (sick days, etc.) if they reach the
    // service.

    @Test
    @DisplayName("Lot3#75: listByEmployee_WARN_allowsColleagueReadBecauseNoCallerCheck — employee A can read B's leaves at service layer")
    void listByEmployee_WARN_allowsColleagueReadBecauseNoCallerCheck() {
        // TODO-SEC: listByEmployee has NO caller/principal argument. Employee A
        // (callerId=1L) asking for employee B's (targetId=2L) leaves gets them
        // back unconditionally. Guard should compare caller == target OR
        // caller.role == PRO/OWNER at the service layer.
        Long colleagueEmployeeId = 2L;                    // NOT the caller
        Employee colleague = new Employee();
        colleague.setId(colleagueEmployeeId);
        colleague.setUserId(20L);
        colleague.setName("Claire Durand");
        colleague.setEmail("claire@prettyface.com");

        LeaveRequest colleagueSickLeave = new LeaveRequest();
        colleagueSickLeave.setId(99L);
        colleagueSickLeave.setEmployee(colleague);
        colleagueSickLeave.setType(LeaveType.SICKNESS);
        colleagueSickLeave.setStatus(LeaveStatus.APPROVED);
        colleagueSickLeave.setStartDate(LocalDate.of(2025, 3, 10));
        colleagueSickLeave.setEndDate(LocalDate.of(2025, 3, 12));
        colleagueSickLeave.setReason("Flu");

        when(leaveRequestRepository.findByEmployeeIdOrderByStartDateDesc(colleagueEmployeeId))
                .thenReturn(List.of(colleagueSickLeave));

        // Service has no idea who is asking — returns the colleague's leaves
        List<LeaveResponse> result = leaveRequestService.listByEmployee(colleagueEmployeeId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).employeeId()).isEqualTo(colleagueEmployeeId);
        assertThat(result.get(0).employeeName()).isEqualTo("Claire Durand");
        assertThat(result.get(0).type()).isEqualTo(LeaveType.SICKNESS);
        assertThat(result.get(0).reason()).isEqualTo("Flu");
        // Documents the gap: sensitive data (reason="Flu", sick-leave dates)
        // exposed without a caller-identity check.
    }
}
