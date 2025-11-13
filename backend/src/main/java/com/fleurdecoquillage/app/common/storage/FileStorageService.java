package com.fleurdecoquillage.app.common.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    /**
     * Save Base64 image data to disk
     * @param base64Data Base64 string with data URL prefix (data:image/png;base64,...)
     * @param careId Care ID for folder organization
     * @return Relative file path
     */
    public String saveBase64Image(String base64Data, Long careId) {
        try {
            logger.info("====== SAVE IMAGE START ======");
            logger.info("Care ID: {}", careId);
            logger.info("Base64 data length: {}", base64Data != null ? base64Data.length() : 0);
            logger.info("Base64 data prefix: {}", base64Data != null && base64Data.length() > 50 ? base64Data.substring(0, 50) : base64Data);

            // Extract MIME type and Base64 data
            String[] parts = base64Data.split(",");
            if (parts.length != 2) {
                logger.error("Invalid Base64 format: parts.length = {}", parts.length);
                throw new IllegalArgumentException("Invalid Base64 data format");
            }

            String mimeType = extractMimeType(parts[0]);
            logger.info("MIME type extracted: {}", mimeType);

            String extension = getExtensionFromMimeType(mimeType);
            logger.info("Extension: {}", extension);

            // Validate extension
            if (!extension.equals("png") && !extension.equals("jpg") && !extension.equals("jpeg")) {
                throw new IllegalArgumentException("Only PNG and JPG images are allowed");
            }

            // Decode Base64
            byte[] imageBytes = Base64.getDecoder().decode(parts[1]);
            logger.info("Image bytes decoded: {} bytes", imageBytes.length);

            // Validate size
            if (imageBytes.length > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("File size exceeds 5MB limit");
            }

            // Generate unique filename
            String filename = UUID.randomUUID().toString() + "." + extension;
            logger.info("Generated filename: {}", filename);

            // Create directory structure: uploads/cares/{careId}/
            Path careDir = Paths.get(uploadDir, "cares", careId.toString());
            logger.info("Creating directory: {}", careDir.toAbsolutePath());
            Files.createDirectories(careDir);
            logger.info("Directory created successfully");

            // Write file
            Path filePath = careDir.resolve(filename);
            logger.info("Writing file to: {}", filePath.toAbsolutePath());
            Files.write(filePath, imageBytes);
            logger.info("File written successfully");

            // Return relative path
            String relativePath = String.format("uploads/cares/%d/%s", careId, filename);
            logger.info("Returning relative path: {}", relativePath);
            logger.info("====== SAVE IMAGE END ======");

            return relativePath;

        } catch (IOException e) {
            logger.error("====== SAVE IMAGE FAILED ======");
            logger.error("IOException: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save image: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("====== SAVE IMAGE FAILED ======");
            logger.error("Exception: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Delete a single file
     * @param filePath Relative file path
     */
    public void deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + e.getMessage(), e);
        }
    }

    /**
     * Delete all images for a care
     * @param careId Care ID
     */
    public void deleteCareImages(Long careId) {
        try {
            Path careDir = Paths.get(uploadDir, "cares", careId.toString());
            if (Files.exists(careDir)) {
                // Delete all files in directory recursively
                try (Stream<Path> paths = Files.walk(careDir)) {
                    paths.sorted(Comparator.reverseOrder())
                         .forEach(path -> {
                             try {
                                 Files.delete(path);
                             } catch (IOException e) {
                                 // Log but don't fail
                             }
                         });
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete care images: " + e.getMessage(), e);
        }
    }

    /**
     * Load file as Resource for serving
     * @param filePath Relative file path
     * @return Resource
     */
    public Resource loadFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Resource resource = new UrlResource(path.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found or not readable: " + filePath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load file: " + e.getMessage(), e);
        }
    }

    /**
     * Extract MIME type from data URL prefix
     * @param prefix Data URL prefix (e.g., "data:image/png;base64")
     * @return MIME type (e.g., "image/png")
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
     * Get file extension from MIME type
     * @param mimeType MIME type (e.g., "image/png")
     * @return Extension without dot (e.g., "png")
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
