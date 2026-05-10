package com.luxpretty.app.post.app;

import com.luxpretty.app.common.storage.StorageBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Thin abstraction over the storage backend for post images. Extracted so
 * service tests can verify file-level side effects via plain Mockito
 * instead of resorting to {@code mockStatic} on {@code Files}.
 *
 * <p>This class predates the {@link StorageBackend} and now just delegates
 * to it. Kept as a separate bean to preserve the existing test surface.
 */
@Service
public class FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);

    private final StorageBackend backend;

    public FileStorage(StorageBackend backend) {
        this.backend = backend;
    }

    /**
     * Delete the file at the given path if it exists. Never throws — failures
     * are logged at WARN so that delete flows remain idempotent when a file
     * is already missing (crash mid-upload, manual cleanup, etc.).
     */
    public void deleteIfExists(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            backend.delete(path);
        } catch (RuntimeException e) {
            log.warn("Failed to delete file {}: {}", path, e.getMessage());
        }
    }
}
