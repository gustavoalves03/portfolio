package com.luxpretty.app.employee.repo;

import com.luxpretty.app.employee.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByUserId(Long userId);
    List<Employee> findByActiveTrue();

    @Query("SELECT e FROM Employee e JOIN e.assignedCares c WHERE c.id = :careId AND e.active = true")
    List<Employee> findActiveByAssignedCareId(Long careId);
}
