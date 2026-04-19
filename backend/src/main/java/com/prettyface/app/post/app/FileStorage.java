package com.prettyface.app.post.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Thin abstraction over the local filesystem for post image storage.
 * Extracted so service tests can verify file-level side effects via
 * plain Mockito instead of resorting to {@code mockStatic}.
 */
@Service
public class FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);

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
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            log.warn("Failed to delete file {}: {}", path, e.getMessage());
        }
    }
}
