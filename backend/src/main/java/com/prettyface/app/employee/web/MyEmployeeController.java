package com.prettyface.app.employee.web;

import com.prettyface.app.employee.app.EmployeeDocumentService;
import com.prettyface.app.employee.app.LeaveRequestService;
import com.prettyface.app.employee.domain.DocumentType;
import com.prettyface.app.employee.domain.Employee;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.employee.web.dto.*;
import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employee/me")
public class MyEmployeeController {

    private final EmployeeRepository employeeRepo;
    private final LeaveRequestService leaveService;
    private final EmployeeDocumentService documentService;
    private final TenantRepository tenantRepo;

    public MyEmployeeController(EmployeeRepository employeeRepo, LeaveRequestService leaveService,
                                EmployeeDocumentService documentService, TenantRepository tenantRepo) {
        this.employeeRepo = employeeRepo;
        this.leaveService = leaveService;
        this.documentService = documentService;
        this.tenantRepo = tenantRepo;
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

    @GetMapping("/documents")
    public List<DocumentResponse> myDocuments(@AuthenticationPrincipal UserPrincipal principal) {
        Employee emp = resolveEmployee(principal.getId());
        return documentService.listByEmployee(emp.getId());
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse uploadDocument(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam("type") DocumentType type,
                                           @RequestParam("title") String title) {
        Employee emp = resolveEmployee(principal.getId());
        return documentService.upload(emp.getId(), type, title, file, principal.getId());
    }

    @GetMapping("/settings")
    public Map<String, Object> getSettings() {
        String slug = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepo.findBySlug(slug).orElse(null);
        int annualLeaveDays = (tenant != null && tenant.getAnnualLeaveDays() != null)
                ? tenant.getAnnualLeaveDays() : 25;
        return Map.of("annualLeaveDays", annualLeaveDays);
    }

    private Employee resolveEmployee(Long userId) {
        return employeeRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("No employee profile found for user"));
    }
}
