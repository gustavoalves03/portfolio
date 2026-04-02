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
}
