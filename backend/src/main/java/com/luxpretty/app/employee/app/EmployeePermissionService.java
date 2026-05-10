package com.luxpretty.app.employee.app;

import com.luxpretty.app.employee.domain.AccessLevel;
import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.domain.EmployeePermission;
import com.luxpretty.app.employee.domain.PermissionDomain;
import com.luxpretty.app.employee.repo.EmployeePermissionRepository;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.multitenancy.ApplicationSchemaExecutor;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EmployeePermissionService {

    private final EmployeePermissionRepository permissionRepo;
    private final EmployeeRepository employeeRepo;
    private final UserRepository userRepository;
    private final ApplicationSchemaExecutor applicationSchemaExecutor;

    public EmployeePermissionService(EmployeePermissionRepository permissionRepo,
                                      EmployeeRepository employeeRepo,
                                      UserRepository userRepository,
                                      ApplicationSchemaExecutor applicationSchemaExecutor) {
        this.permissionRepo = permissionRepo;
        this.employeeRepo = employeeRepo;
        this.userRepository = userRepository;
        this.applicationSchemaExecutor = applicationSchemaExecutor;
    }

    @Transactional(readOnly = true)
    public Map<PermissionDomain, AccessLevel> getPermissions(Long employeeId) {
        List<EmployeePermission> perms = permissionRepo.findByEmployeeId(employeeId);
        Map<PermissionDomain, AccessLevel> result = perms.stream()
                .collect(Collectors.toMap(EmployeePermission::getDomain, EmployeePermission::getAccessLevel));
        for (PermissionDomain domain : PermissionDomain.values()) {
            result.putIfAbsent(domain, AccessLevel.NONE);
        }
        return result;
    }

    @Transactional
    public Map<PermissionDomain, AccessLevel> updatePermissions(Long employeeId,
                                                                 Map<PermissionDomain, AccessLevel> updates,
                                                                 Long callerUserId) {
        requirePermissionGrantor(callerUserId, employeeId);

        employeeRepo.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));

        for (var entry : updates.entrySet()) {
            EmployeePermission perm = permissionRepo.findByEmployeeIdAndDomain(employeeId, entry.getKey())
                    .orElseGet(() -> {
                        EmployeePermission p = new EmployeePermission();
                        p.setEmployeeId(employeeId);
                        p.setDomain(entry.getKey());
                        return p;
                    });
            perm.setAccessLevel(entry.getValue());
            permissionRepo.save(perm);
        }

        return getPermissions(employeeId);
    }

    /**
     * Verify that {@code callerUserId} is authorised to grant/revoke permissions
     * on {@code targetEmployeeId}. Only tenant owners (Role.PRO or Role.ADMIN)
     * may modify permissions, and they may not modify their OWN employee record.
     *
     * @throws ResponseStatusException 403 on any failure.
     */
    private void requirePermissionGrantor(Long callerUserId, Long targetEmployeeId) {
        if (callerUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthenticated caller");
        }
        Role role = applicationSchemaExecutor.call(() -> userRepository.findById(callerUserId)
                .map(User::getRole)
                .orElse(null));
        if (role != Role.PRO && role != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only tenant owners may modify employee permissions");
        }
        // Defence-in-depth: even if a PRO somehow has an Employee record,
        // refuse attempts to grant themselves permissions via that record.
        Employee targetEmployee = employeeRepo.findById(targetEmployeeId).orElse(null);
        if (targetEmployee != null && callerUserId.equals(targetEmployee.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot modify your own employee permissions");
        }
    }

    @Transactional(readOnly = true)
    public AccessLevel checkAccess(Long employeeId, PermissionDomain domain) {
        return permissionRepo.findByEmployeeIdAndDomain(employeeId, domain)
                .map(EmployeePermission::getAccessLevel)
                .orElse(AccessLevel.NONE);
    }

    @Transactional(readOnly = true)
    public void requireAccess(Long employeeId, PermissionDomain domain, AccessLevel minimum) {
        AccessLevel actual = checkAccess(employeeId, domain);
        if (actual.ordinal() < minimum.ordinal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Insufficient permission for " + domain + ": requires " + minimum + ", has " + actual);
        }
    }
}
