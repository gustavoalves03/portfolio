package com.prettyface.app.employee.app;

import com.prettyface.app.employee.domain.DocumentType;
import com.prettyface.app.employee.domain.Employee;
import com.prettyface.app.employee.domain.EmployeeDocument;
import com.prettyface.app.employee.repo.EmployeeDocumentRepository;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.employee.web.dto.DocumentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class EmployeeDocumentService {

    private static final String UPLOAD_BASE = "uploads/employees";

    private final EmployeeDocumentRepository docRepo;
    private final EmployeeRepository employeeRepo;

    public EmployeeDocumentService(EmployeeDocumentRepository docRepo, EmployeeRepository employeeRepo) {
        this.docRepo = docRepo;
        this.employeeRepo = employeeRepo;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listByEmployee(Long employeeId) {
        return docRepo.findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public DocumentResponse upload(Long employeeId, DocumentType type, String title,
                                   MultipartFile file, Long uploadedByUserId) {
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                : "";
        String storedFilename = UUID.randomUUID() + ext;

        Path dir = Paths.get(UPLOAD_BASE, String.valueOf(employeeId), "documents");
        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(storedFilename));
        } catch (IOException e) {
            throw new RuntimeException("Failed to store document", e);
        }

        EmployeeDocument doc = new EmployeeDocument();
        doc.setEmployee(employee);
        doc.setType(type);
        doc.setTitle(title);
        doc.setFilename(originalFilename != null ? originalFilename : storedFilename);
        doc.setFilePath(dir.resolve(storedFilename).toString());
        doc.setUploadedByUserId(uploadedByUserId);

        return toResponse(docRepo.save(doc));
    }

    @Transactional
    public void delete(Long documentId) {
        EmployeeDocument doc = docRepo.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        try {
            Files.deleteIfExists(Paths.get(doc.getFilePath()));
        } catch (IOException e) {
            // Log but don't fail — DB record cleanup is more important
        }
        docRepo.deleteById(documentId);
    }

    public Path getFilePath(Long documentId) {
        EmployeeDocument doc = docRepo.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        return Paths.get(doc.getFilePath());
    }

    public String getFilename(Long documentId) {
        return docRepo.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId))
                .getFilename();
    }

    private DocumentResponse toResponse(EmployeeDocument doc) {
        return new DocumentResponse(
                doc.getId(), doc.getEmployee().getId(), doc.getType(),
                doc.getTitle(), doc.getFilename(), doc.getCreatedAt()
        );
    }
}
