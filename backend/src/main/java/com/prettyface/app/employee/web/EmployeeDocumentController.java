package com.prettyface.app.employee.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.employee.app.EmployeeDocumentService;
import com.prettyface.app.employee.domain.DocumentType;
import com.prettyface.app.employee.web.dto.DocumentResponse;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/pro/employees/{employeeId}/documents")
public class EmployeeDocumentController {

    private final EmployeeDocumentService service;

    public EmployeeDocumentController(EmployeeDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<DocumentResponse> list(@PathVariable Long employeeId) {
        return service.listByEmployee(employeeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@PathVariable Long employeeId,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam("type") DocumentType type,
                                   @RequestParam("title") String title,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return service.upload(employeeId, type, title, file, principal.getId());
    }

    @GetMapping("/{docId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long employeeId,
                                             @PathVariable Long docId) {
        try {
            Path filePath = service.getFilePath(docId);
            Resource resource = new UrlResource(filePath.toUri());
            String filename = service.getFilename(docId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{docId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long employeeId, @PathVariable Long docId) {
        service.delete(docId);
    }
}
