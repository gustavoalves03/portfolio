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
