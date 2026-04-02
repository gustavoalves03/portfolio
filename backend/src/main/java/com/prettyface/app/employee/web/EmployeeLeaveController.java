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
