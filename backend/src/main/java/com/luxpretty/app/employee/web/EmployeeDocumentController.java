package com.luxpretty.app.employee.web;

import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.common.storage.StorageNotFoundException;
import com.luxpretty.app.employee.app.EmployeeDocumentService;
import com.luxpretty.app.employee.domain.DocumentType;
import com.luxpretty.app.employee.web.dto.DocumentResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
            Resource resource = new InputStreamResource(service.openFile(docId));
            String filename = service.getFilename(docId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (StorageNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{docId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long employeeId, @PathVariable Long docId) {
        service.delete(docId);
    }
}
