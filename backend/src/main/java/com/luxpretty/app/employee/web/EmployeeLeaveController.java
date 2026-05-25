package com.luxpretty.app.employee.web;

import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.employee.app.LeaveRequestService;
import com.luxpretty.app.employee.domain.LeaveType;
import com.luxpretty.app.employee.web.dto.LeaveResponse;
import com.luxpretty.app.employee.web.dto.LeaveReviewDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// TODO(chantier-T): add @RequiresFeature(ABSENCE_MGMT) at class level once tier gating ships
@RestController
@RequestMapping("/api/pro/leaves")
public class EmployeeLeaveController {

    private final LeaveRequestService service;

    public EmployeeLeaveController(LeaveRequestService service) {
        this.service = service;
    }

    @GetMapping("/history")
    public List<LeaveResponse> listHistory(@RequestParam(required = false) LeaveType type) {
        return service.listReviewed(type);
    }

    @GetMapping("/pending")
    public List<LeaveResponse> listPending() {
        return service.listPending();
    }

    @GetMapping("/employee/{employeeId}")
    public List<LeaveResponse> listByEmployee(@PathVariable Long employeeId,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return service.listByEmployee(employeeId, principal.getId());
    }

    @PutMapping("/{leaveId}/review")
    public LeaveResponse review(@PathVariable Long leaveId,
                                @RequestBody @Valid LeaveReviewDto dto,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return service.reviewLeave(leaveId, dto, principal.getId());
    }
}
