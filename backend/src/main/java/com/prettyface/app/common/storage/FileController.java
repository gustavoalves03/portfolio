package com.prettyface.app.common.storage;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/images")
public class FileController {

    private final StorageBackend backend;

    public FileController(StorageBackend backend) {
        this.backend = backend;
    }

    @GetMapping("/cares/{careId}/{filename}")
    public ResponseEntity<Resource> serveImage(
            @PathVariable Long careId,
            @PathVariable String filename
    ) {
        return serve(String.format("cares/%d/%s", careId, filename), filename);
    }

    @GetMapping("/posts/{filename}")
    public ResponseEntity<Resource> servePostImage(@PathVariable String filename) {
        return serve("posts/" + filename, filename);
    }

    @GetMapping("/visits/{visitId}/{filename}")
    public ResponseEntity<Resource> serveVisitPhoto(
            @PathVariable Long visitId,
            @PathVariable String filename
    ) {
        return serve(String.format("visits/%d/%s", visitId, filename), filename);
    }

    @GetMapping("/tenant/{tenantId}/{filename}")
    public ResponseEntity<Resource> serveTenantImage(
            @PathVariable Long tenantId,
            @PathVariable String filename
    ) {
        return serve(String.format("tenant/%d/%s", tenantId, filename), filename);
    }

    private ResponseEntity<Resource> serve(String key, String filename) {
        try {
            Resource resource = new InputStreamResource(backend.load(key));
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(determineContentType(filename)))
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000") // 1 year
                    .body(resource);
        } catch (StorageNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private String determineContentType(String filename) {
        int lastDot = filename.lastIndexOf('.');
        String extension = lastDot > 0 ? filename.substring(lastDot + 1) : "";
        return switch (extension.toLowerCase()) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }
}
