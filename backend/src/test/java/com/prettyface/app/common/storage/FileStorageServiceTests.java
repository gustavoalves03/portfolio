package com.prettyface.app.common.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FileStorageServiceTests {

    private StorageBackend backend;
    private FileStorageService service;

    @BeforeEach
    void setUp() {
        backend = mock(StorageBackend.class);
        service = new FileStorageService(backend);
    }

    @Test
    void saveBase64ImageStripsHeaderAndDelegatesToBackend() {
        byte[] payload = {0x10, 0x20, 0x30};
        String base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(payload);

        String stored = service.saveBase64Image(base64, "cares", 12L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(backend).save(keyCaptor.capture(), bytesCaptor.capture(), eq("image/png"));

        // Returned path is the legacy "uploads/<key>" used by callers as the DB column.
        assertThat(stored).startsWith("uploads/cares/12/");
        assertThat(stored).endsWith(".png");
        // Backend receives the bare key (no "uploads/" prefix) for clean R2 layouts.
        assertThat(keyCaptor.getValue()).startsWith("cares/12/");
        assertThat(keyCaptor.getValue()).endsWith(".png");
        assertThat(bytesCaptor.getValue()).containsExactly(0x10, 0x20, 0x30);
    }

    @Test
    void saveBase64ImageRejectsNonImageMime() {
        String base64 = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(new byte[]{1});

        assertThatThrownBy(() -> service.saveBase64Image(base64, "cares", 1L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(backend, never()).save(any(), any(), any());
    }

    @Test
    void saveBase64ImageRejectsMalformedDataUrl() {
        assertThatThrownBy(() -> service.saveBase64Image("not-a-data-url", "cares", 1L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(backend, never()).save(any(), any(), any());
    }

    @Test
    void saveBase64ImageRejectsOversizedPayload() {
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
        String base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(tooBig);

        assertThatThrownBy(() -> service.saveBase64Image(base64, "cares", 1L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(backend, never()).save(any(), any(), any());
    }

    @Test
    void deleteFileDelegatesToBackend() {
        service.deleteFile("uploads/cares/12/abc.png");
        verify(backend).delete("uploads/cares/12/abc.png");
    }

    @Test
    void deleteCareImagesUsesFolderPrefix() {
        service.deleteCareImages(42L);
        verify(backend).deleteFolder("cares/42");
    }
}
