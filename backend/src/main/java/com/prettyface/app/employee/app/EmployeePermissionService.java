package com.prettyface.app.employee.app;

import com.prettyface.app.employee.domain.AccessLevel;
import com.prettyface.app.employee.domain.EmployeePermission;
import com.prettyface.app.employee.domain.PermissionDomain;
import com.prettyface.app.employee.repo.EmployeePermissionRepository;
import com.prettyface.app.employee.repo.EmployeeRepository;
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

    public EmployeePermissionService(EmployeePermissionRepository permissionRepo,
                                      EmployeeRepository employeeRepo) {
        this.permissionRepo = permissionRepo;
        this.employeeRepo = employeeRepo;
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
                                                                 Map<PermissionDomain, AccessLevel> updates) {
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
