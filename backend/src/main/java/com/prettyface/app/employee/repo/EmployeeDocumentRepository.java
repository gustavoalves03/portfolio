package com.prettyface.app.employee.repo;

import com.prettyface.app.employee.domain.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {
    List<EmployeeDocument> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
}
