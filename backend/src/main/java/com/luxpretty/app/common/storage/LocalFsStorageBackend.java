package com.luxpretty.app.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Filesystem-backed {@link StorageBackend} used in dev and tests. Writes
 * relative to the working directory (typically {@code backend/}) under
 * the configured upload root ({@code app.upload.dir}, default {@code uploads}).
 *
 * <p>Active when {@code app.storage.backend=local} (the default).
 */
@Component
@ConditionalOnProperty(name = "app.storage.backend", havingValue = "local", matchIfMissing = true)
public class LocalFsStorageBackend implements StorageBackend {

    private final Path root;

    public LocalFsStorageBackend(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir);
    }

    @Override
    public void save(String key, byte[] data, String contentType) {
        try {
            Path dest = resolve(key);
            Files.createDirectories(dest.getParent());
            Files.write(dest, data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + key, e);
        }
    }

    @Override
    public InputStream load(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (NoSuchFileException e) {
            throw new StorageNotFoundException(key);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete " + key, e);
        }
    }

    @Override
    public void deleteFolder(String prefix) {
        Path dir = resolve(prefix);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best-effort: keep walking; partial cleanup is acceptable on
                    // local filesystem (the disk recycles space when the entity
                    // row is gone)
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete folder " + prefix, e);
        }
    }

    private Path resolve(String key) {
        // Normalize: callers may pass "uploads/cares/12/abc.png" (legacy) or
        // "cares/12/abc.png" (new). Strip the leading "uploads/" if present
        // so we never duplicate it.
        String stripped = key.startsWith("uploads/") ? key.substring("uploads/".length()) : key;
        return root.resolve(stripped).normalize();
    }
}
