package com.luxpretty.app.employee.app;

import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.domain.LeaveRequest;
import com.luxpretty.app.employee.domain.LeaveStatus;
import com.luxpretty.app.employee.domain.LeaveType;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.employee.repo.LeaveRequestRepository;
import com.luxpretty.app.employee.web.dto.LeaveRequestDto;
import com.luxpretty.app.employee.web.dto.LeaveResponse;
import com.luxpretty.app.employee.web.dto.LeaveReviewDto;
import com.luxpretty.app.multitenancy.ApplicationSchemaExecutor;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.users.domain.AuthProvider;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

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

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationSchemaExecutor applicationSchemaExecutor;

    @InjectMocks
    private LeaveRequestService leaveRequestService;

    private Employee employee;
    private LeaveRequest pendingLeave;
    private LeaveRequest approvedLeave;

    // Callers
    private static final Long PRO_USER_ID = 500L;
    private static final Long EMPLOYEE_USER_ID = 10L; // == employee.getUserId()

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("test-tenant");
        lenient().when(applicationSchemaExecutor.call(any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());

        // Default caller lookups (lenient — not every test uses every principal).
        User proUser = User.builder()
                .id(PRO_USER_ID)
                .name("Owner")
                .email("owner@luxpretty.com")
                .provider(AuthProvider.LOCAL)
                .build();
        lenient().when(userRepository.findById(PRO_USER_ID)).thenReturn(Optional.of(proUser));

        User employeeUser = User.builder()
                .id(EMPLOYEE_USER_ID)
                .name("Sophie Martin")
                .email("sophie@luxpretty.com")
                .provider(AuthProvider.LOCAL)
                .build();
        lenient().when(userRepository.findById(EMPLOYEE_USER_ID)).thenReturn(Optional.of(employeeUser));

        employee = new Employee();
        employee.setId(1L);
        employee.setUserId(EMPLOYEE_USER_ID);
        employee.setName("Sophie Martin");
        employee.setEmail("sophie@luxpretty.com");

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

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
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

        LeaveResponse response = leaveRequestService.reviewLeave(1L, dto, PRO_USER_ID);

        assertThat(response.status()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(response.reviewerNote()).isEqualTo("Approved — enjoy your holiday!");
        assertThat(response.reviewedAt()).isNotNull();
    }

    @Test
    void reviewLeave_reject_setsRejected() {
        LeaveReviewDto dto = new LeaveReviewDto(LeaveStatus.REJECTED, "Cannot approve due to peak season.");

        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(pendingLeave));
        when(leaveRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveResponse response = leaveRequestService.reviewLeave(1L, dto, PRO_USER_ID);

        assertThat(response.status()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(response.reviewerNote()).isEqualTo("Cannot approve due to peak season.");
        assertThat(response.reviewedAt()).isNotNull();
    }

    @Test
    void reviewLeave_alreadyReviewed_throws() {
        LeaveReviewDto dto = new LeaveReviewDto(LeaveStatus.REJECTED, "Changed mind.");

        when(leaveRequestRepository.findById(2L)).thenReturn(Optional.of(approvedLeave));

        assertThatThrownBy(() -> leaveRequestService.reviewLeave(2L, dto, PRO_USER_ID))
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
    void listByEmployee_callerIsOwnEmployee_returnsOwnLeaves() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveRequestRepository.findByEmployeeIdOrderByStartDateDesc(1L))
                .thenReturn(List.of(pendingLeave, approvedLeave));

        List<LeaveResponse> result = leaveRequestService.listByEmployee(1L, EMPLOYEE_USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.employeeId().equals(1L));
    }

    @Test
    void listByEmployee_callerIsPro_returnsLeaves() {
        when(leaveRequestRepository.findByEmployeeIdOrderByStartDateDesc(1L))
                .thenReturn(List.of(pendingLeave, approvedLeave));

        List<LeaveResponse> result = leaveRequestService.listByEmployee(1L, PRO_USER_ID);

        assertThat(result).hasSize(2);
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
    // ── Lot2 Sec1: Cross-tenant IDOR on leave requests (documented) ──
    // ══════════════════════════════════════════════════════════════
    // NOTE-SEC: LeaveRequest has no tenantSlug/tenantId column and is stored in
    // the per-tenant schema routed by TenantContext. The per-tenant schema
    // routing is the primary isolation boundary here; a caller who IS a PRO in
    // tenant A will be denied by Fix4b when reviewing tenant A's leaves only
    // because the schema router already ensured the leaveId is from tenant A's
    // schema. Cross-tenant isolation as a whole is out of scope for Fix4b —
    // documented here as a NOTE for a follow-up pass.

    @Test
    @DisplayName("Lot2#41: listByEmployee_callerCheck_protectsEvenWithoutTenantCheck — Fix4b narrows the blast radius")
    void listByEmployee_callerCheck_protectsEvenWithoutTenantCheck() {
        // Fix4b: a non-PRO caller asking for another employee's leaves is rejected
        // at the service layer, independently of tenant routing.
        Long crossTenantEmployeeId = 777L;
        Employee otherEmployee = new Employee();
        otherEmployee.setId(crossTenantEmployeeId);
        otherEmployee.setUserId(888L); // NOT the caller's userId
        when(employeeRepository.findById(crossTenantEmployeeId)).thenReturn(Optional.of(otherEmployee));

        // Caller is a plain EMPLOYEE (not PRO) asking about employeeId=777
        assertThatThrownBy(() ->
                leaveRequestService.listByEmployee(crossTenantEmployeeId, EMPLOYEE_USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(leaveRequestRepository, never()).findByEmployeeIdOrderByStartDateDesc(any());
    }

    @Test
    @DisplayName("Lot2#41b: reviewLeave_callerCheck_rejectsNonPro — service refuses non-owner callers")
    void reviewLeave_callerCheck_rejectsNonPro() {
        LeaveReviewDto dto = new LeaveReviewDto(LeaveStatus.APPROVED, "cross-tenant review");
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(pendingLeave));

        // Caller is a plain USER (not PRO/ADMIN). A leave reviewer must be PRO/ADMIN.
        Long plainUserId = 999L;
        User plainUser = User.builder()
                .id(plainUserId)
                .name("Attacker")
                .email("attacker@example.com")
                .provider(AuthProvider.LOCAL)
                .build();
        when(userRepository.findById(plainUserId)).thenReturn(Optional.of(plainUser));

        assertThatThrownBy(() -> leaveRequestService.reviewLeave(1L, dto, plainUserId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(leaveRequestRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lot3 Sec74 / Fix4b: Self-approve leave ──
    // ══════════════════════════════════════════════════════════════
    // Fix4b: reviewLeave now takes callerUserId and refuses if the caller's
    // userId matches the leave owner's userId.

    @Test
    @DisplayName("Lot3#74 / Fix4b: reviewLeave_selfApproval_throwsForbidden — service refuses self-approval")
    void reviewLeave_selfApproval_throwsForbidden() {
        // The employee owning pendingLeave has userId == EMPLOYEE_USER_ID.
        // If that user somehow had PRO role (mis-configured account) and tried
        // to self-approve, the service must still refuse.
        Long selfCallerUserId = EMPLOYEE_USER_ID;
        User selfPro = User.builder()
                .id(selfCallerUserId)
                .name("Sophie Martin")
                .email("sophie@luxpretty.com")
                .provider(AuthProvider.LOCAL)
                .build();
        when(userRepository.findById(selfCallerUserId)).thenReturn(Optional.of(selfPro));

        LeaveReviewDto selfApproveDto = new LeaveReviewDto(
                LeaveStatus.APPROVED, "approved by me, for me");
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(pendingLeave));

        assertThatThrownBy(() ->
                leaveRequestService.reviewLeave(1L, selfApproveDto, selfCallerUserId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(leaveRequestRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lot3 Sec75 / Fix4b: Read colleague's leave ──
    // ══════════════════════════════════════════════════════════════
    // Fix4b: listByEmployee now takes callerUserId. Allowed iff caller is the
    // target employee, or caller is PRO/ADMIN.

    // ══════════════════════════════════════════════════════════════
    // ── Lot5: Race conditions — concurrent leave approval ──
    // ══════════════════════════════════════════════════════════════
    // LeaveRequest carries @Version, so JPA raises OptimisticLockingFailureException
    // when two PRO reviewers try to update the same PENDING leave concurrently.
    // The service translates that exception into HTTP 409 Conflict.

    @Test
    @DisplayName("Lot5: reviewLeave_concurrentApprovals_secondCallThrowsConflict")
    void reviewLeave_concurrentApprovals_secondCallThrowsConflict() {
        LeaveReviewDto approveDto = new LeaveReviewDto(LeaveStatus.APPROVED, "approved by reviewer A");
        LeaveReviewDto rejectDto = new LeaveReviewDto(LeaveStatus.REJECTED, "rejected by reviewer B");

        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(pendingLeave));
        // First save succeeds; second save raises an optimistic lock failure,
        // mirroring a real race where JPA detects the stale @Version.
        when(leaveRequestRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(new OptimisticLockingFailureException(
                        "LeaveRequest#1 was updated by another transaction"));

        // Reviewer A (PRO) approves successfully.
        LeaveResponse resultA = leaveRequestService.reviewLeave(1L, approveDto, PRO_USER_ID);
        assertThat(resultA.status()).isEqualTo(LeaveStatus.APPROVED);

        // Reviewer B is now racing; simulate the stale view of the leave and the
        // second save throwing optimistic lock failure.
        pendingLeave.setStatus(LeaveStatus.PENDING);

        Long otherProUserId = 501L;
        User otherPro = User.builder()
                .id(otherProUserId)
                .name("Owner B")
                .email("owner-b@luxpretty.com")
                .provider(AuthProvider.LOCAL)
                .build();
        when(userRepository.findById(otherProUserId)).thenReturn(Optional.of(otherPro));

        assertThatThrownBy(() ->
                leaveRequestService.reviewLeave(1L, rejectDto, otherProUserId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(leaveRequestRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Lot5: reviewLeave_optimisticLockingFailure_translatesToConflict")
    void reviewLeave_optimisticLockingFailure_translatesToConflict() {
        LeaveReviewDto dto = new LeaveReviewDto(LeaveStatus.APPROVED, "approved");
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(pendingLeave));
        when(leaveRequestRepository.save(any()))
                .thenThrow(new OptimisticLockingFailureException(
                        "LeaveRequest#1 was updated by another transaction"));

        assertThatThrownBy(() -> leaveRequestService.reviewLeave(1L, dto, PRO_USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("Lot5: reviewLeave_dataIntegrityViolation_stillBubblesRaw")
    void reviewLeave_dataIntegrityViolation_stillBubblesRaw() {
        // DataIntegrityViolationException is NOT translated by reviewLeave because
        // the race condition this fix targets surfaces as OptimisticLockingFailure.
        // GlobalExceptionHandler handles DataIntegrityViolation at the HTTP layer.
        LeaveReviewDto dto = new LeaveReviewDto(LeaveStatus.APPROVED, "approved");
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(pendingLeave));
        when(leaveRequestRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("UK_LEAVE_REVIEWED_ONCE"));

        assertThatThrownBy(() -> leaveRequestService.reviewLeave(1L, dto, PRO_USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Lot3#75 / Fix4b: listByEmployee_colleague_throwsForbidden — employee A cannot read B's leaves")
    void listByEmployee_colleague_throwsForbidden() {
        Long colleagueEmployeeId = 2L;
        Employee colleague = new Employee();
        colleague.setId(colleagueEmployeeId);
        colleague.setUserId(20L); // different userId from the caller
        colleague.setName("Claire Durand");
        colleague.setEmail("claire@luxpretty.com");
        when(employeeRepository.findById(colleagueEmployeeId)).thenReturn(Optional.of(colleague));

        // Caller is EMPLOYEE_USER_ID (Role.EMPLOYEE), NOT the colleague and NOT a PRO
        assertThatThrownBy(() ->
                leaveRequestService.listByEmployee(colleagueEmployeeId, EMPLOYEE_USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(leaveRequestRepository, never()).findByEmployeeIdOrderByStartDateDesc(any());
    }
}
