package com.luxpretty.app.employee.repo;

import com.luxpretty.app.employee.domain.EmployeePermission;
import com.luxpretty.app.employee.domain.PermissionDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeePermissionRepository extends JpaRepository<EmployeePermission, Long> {
    List<EmployeePermission> findByEmployeeId(Long employeeId);
    Optional<EmployeePermission> findByEmployeeIdAndDomain(Long employeeId, PermissionDomain domain);
}
