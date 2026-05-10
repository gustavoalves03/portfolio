package com.luxpretty.app.common.storage;

import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.UUID;

/**
 * Validates and stores Base64-encoded images on behalf of {@code CareService}
 * and {@code TenantService}. Validation (MIME, size) lives here; persistence
 * is delegated to the configured {@link StorageBackend}.
 *
 * <p>Returned paths look like {@code "uploads/cares/12/abc.png"} for backward
 * compatibility with existing rows in DB. The {@code uploads/} prefix is a
 * legacy convention — the backend strips it transparently — but we keep it
 * in the returned string so we don't need a data migration.
 */
@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private final StorageBackend backend;

    public FileStorageService(StorageBackend backend) {
        this.backend = backend;
    }

    /**
     * Save Base64 image data under the "cares" domain folder.
     */
    public String saveBase64Image(String base64Data, Long careId) {
        return saveBase64Image(base64Data, "cares", careId);
    }

    /**
     * Save Base64 image data under a specific domain folder.
     *
     * @param base64Data data URL with prefix (data:image/png;base64,...)
     * @param domain     folder root (e.g. "cares", "tenant")
     * @param entityId   sub-folder
     * @return logical path stored in DB (e.g. "uploads/cares/12/abc.png")
     */
    public String saveBase64Image(String base64Data, String domain, Long entityId) {
        String[] parts = base64Data.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid Base64 data format");
        }

        String mimeType = extractMimeType(parts[0]);
        String extension = getExtensionFromMimeType(mimeType);

        if (!extension.equals("png") && !extension.equals("jpg") && !extension.equals("jpeg")) {
            throw new IllegalArgumentException("Only PNG and JPG images are allowed");
        }

        byte[] imageBytes = Base64.getDecoder().decode(parts[1]);
        if (imageBytes.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 5MB limit");
        }

        String filename = UUID.randomUUID() + "." + extension;
        String key = String.format("%s/%d/%s", domain, entityId, filename);

        backend.save(key, imageBytes, mimeType);

        return "uploads/" + key;
    }

    /**
     * Delete a single object by its stored path (e.g. "uploads/cares/12/abc.png").
     */
    public void deleteFile(String filePath) {
        backend.delete(filePath);
    }

    /**
     * Delete all images for a care.
     */
    public void deleteCareImages(Long careId) {
        backend.deleteFolder("cares/" + careId);
    }

    /**
     * Extract MIME type from data URL prefix.
     */
    private String extractMimeType(String prefix) {
        // Format: data:image/png;base64
        String[] parts = prefix.split(":");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid data URL format");
        }
        String mimeWithEncoding = parts[1];
        String[] mimeParts = mimeWithEncoding.split(";");
        return mimeParts[0];
    }

    /**
     * Get file extension from MIME type.
     */
    private String getExtensionFromMimeType(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/jpg" -> "jpg";
            default -> throw new IllegalArgumentException("Unsupported MIME type: " + mimeType);
        };
    }
}
