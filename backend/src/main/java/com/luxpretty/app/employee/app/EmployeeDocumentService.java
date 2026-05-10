package com.luxpretty.app.employee.app;


import com.luxpretty.app.common.error.ResourceNotFoundException;
import com.luxpretty.app.common.storage.StorageBackend;
import com.luxpretty.app.employee.domain.DocumentType;
import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.domain.EmployeeDocument;
import com.luxpretty.app.employee.repo.EmployeeDocumentRepository;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.employee.web.dto.DocumentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class EmployeeDocumentService {

    private final EmployeeDocumentRepository docRepo;
    private final EmployeeRepository employeeRepo;
    private final StorageBackend storageBackend;

    public EmployeeDocumentService(EmployeeDocumentRepository docRepo,
                                   EmployeeRepository employeeRepo,
                                   StorageBackend storageBackend) {
        this.docRepo = docRepo;
        this.employeeRepo = employeeRepo;
        this.storageBackend = storageBackend;
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
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                : "";
        String storedFilename = UUID.randomUUID() + ext;

        String key = String.format("employees/%d/documents/%s", employeeId, storedFilename);
        try {
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            storageBackend.save(key, file.getBytes(), contentType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store document", e);
        }

        EmployeeDocument doc = new EmployeeDocument();
        doc.setEmployee(employee);
        doc.setType(type);
        doc.setTitle(title);
        doc.setFilename(originalFilename != null ? originalFilename : storedFilename);
        doc.setFilePath("uploads/" + key);
        doc.setUploadedByUserId(uploadedByUserId);

        return toResponse(docRepo.save(doc));
    }

    @Transactional
    public void delete(Long documentId) {
        EmployeeDocument doc = docRepo.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        try {
            storageBackend.delete(doc.getFilePath());
        } catch (RuntimeException e) {
            // Log but don't fail — DB record cleanup is more important
        }
        docRepo.deleteById(documentId);
    }

    /**
     * Open a stream to the stored document. Caller is responsible for closing it.
     */
    public InputStream openFile(Long documentId) {
        EmployeeDocument doc = docRepo.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        return storageBackend.load(doc.getFilePath());
    }

    public String getFilename(Long documentId) {
        return docRepo.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId))
                .getFilename();
    }

    private DocumentResponse toResponse(EmployeeDocument doc) {
        return new DocumentResponse(
                doc.getId(), doc.getEmployee().getId(), doc.getType(),
                doc.getTitle(), doc.getFilename(), doc.getCreatedAt()
        );
    }
}
