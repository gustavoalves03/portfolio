package com.prettyface.app.employee.web;

import com.prettyface.app.employee.app.LeaveRequestService;
import com.prettyface.app.employee.domain.Employee;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.employee.web.dto.*;
import com.prettyface.app.auth.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employee/me")
public class MyEmployeeController {

    private final EmployeeRepository employeeRepo;
    private final LeaveRequestService leaveService;

    public MyEmployeeController(EmployeeRepository employeeRepo, LeaveRequestService leaveService) {
        this.employeeRepo = employeeRepo;
        this.leaveService = leaveService;
    }

    @GetMapping
    public EmployeeResponse getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        Employee emp = resolveEmployee(principal.getId());
        var cares = emp.getAssignedCares().stream()
                .map(c -> new EmployeeResponse.CareRef(c.getId(), c.getName())).toList();
        return new EmployeeResponse(emp.getId(), emp.getUserId(), emp.getName(), emp.getEmail(),
                emp.getPhone(), emp.isActive(), cares, emp.getCreatedAt());
    }

    @GetMapping("/leaves")
    public List<LeaveResponse> myLeaves(@AuthenticationPrincipal UserPrincipal principal) {
        Employee emp = resolveEmployee(principal.getId());
        return leaveService.listByEmployee(emp.getId());
    }

    @PostMapping("/leaves")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveResponse createLeave(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestBody @Valid LeaveRequestDto dto) {
        Employee emp = resolveEmployee(principal.getId());
        return leaveService.createLeave(emp.getId(), dto);
    }

    private Employee resolveEmployee(Long userId) {
        return employeeRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("No employee profile found for user"));
    }
}
