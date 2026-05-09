package com.prettyface.app.common.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFsStorageBackendTests {

    @Test
    void saveAndLoadRoundTrip(@TempDir Path tempDir) throws IOException {
        LocalFsStorageBackend backend = new LocalFsStorageBackend(tempDir.toString());

        backend.save("cares/12/abc.png", new byte[]{1, 2, 3, 4}, "image/png");

        try (InputStream in = backend.load("cares/12/abc.png")) {
            assertThat(in.readAllBytes()).containsExactly(1, 2, 3, 4);
        }
    }

    @Test
    void saveCreatesIntermediateDirectories(@TempDir Path tempDir) {
        LocalFsStorageBackend backend = new LocalFsStorageBackend(tempDir.toString());

        backend.save("cares/12/sub/deep.png", new byte[]{0}, "image/png");

        assertThat(Files.exists(tempDir.resolve("cares/12/sub/deep.png"))).isTrue();
    }

    @Test
    void saveAcceptsLegacyUploadsPrefix(@TempDir Path tempDir) {
        LocalFsStorageBackend backend = new LocalFsStorageBackend(tempDir.toString());

        backend.save("uploads/cares/12/legacy.png", new byte[]{9}, "image/png");

        // The "uploads/" prefix is stripped; file lives directly under tempDir.
        assertThat(Files.exists(tempDir.resolve("cares/12/legacy.png"))).isTrue();
    }

    @Test
    void loadMissingKeyThrowsStorageNotFound(@TempDir Path tempDir) {
        LocalFsStorageBackend backend = new LocalFsStorageBackend(tempDir.toString());

        assertThatThrownBy(() -> backend.load("cares/99/missing.png"))
                .isInstanceOf(StorageNotFoundException.class);
    }

    @Test
    void deleteRemovesFile(@TempDir Path tempDir) {
        LocalFsStorageBackend backend = new LocalFsStorageBackend(tempDir.toString());
        backend.save("cares/1/x.png", new byte[]{1}, "image/png");

        backend.delete("cares/1/x.png");

        assertThat(Files.exists(tempDir.resolve("cares/1/x.png"))).isFalse();
    }

    @Test
    void deleteMissingKeyIsNoOp(@TempDir Path tempDir) {
        LocalFsStorageBackend backend = new LocalFsStorageBackend(tempDir.toString());

        // Should not throw.
        backend.delete("cares/never/existed.png");
    }

    @Test
    void deleteFolderWipesEverythingUnderPrefix(@TempDir Path tempDir) {
        LocalFsStorageBackend backend = new LocalFsStorageBackend(tempDir.toString());
        backend.save("cares/7/a.png", new byte[]{1}, "image/png");
        backend.save("cares/7/b.png", new byte[]{2}, "image/png");
        backend.save("cares/7/sub/c.png", new byte[]{3}, "image/png");
        backend.save("cares/8/keep.png", new byte[]{9}, "image/png");

        backend.deleteFolder("cares/7");

        assertThat(Files.exists(tempDir.resolve("cares/7"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("cares/8/keep.png"))).isTrue();
    }

    @Test
    void deleteFolderOnMissingPrefixIsNoOp(@TempDir Path tempDir) {
        LocalFsStorageBackend backend = new LocalFsStorageBackend(tempDir.toString());

        // Should not throw.
        backend.deleteFolder("cares/never");
    }
}
