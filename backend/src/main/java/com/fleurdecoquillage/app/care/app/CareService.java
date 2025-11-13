package com.fleurdecoquillage.app.care.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.fleurdecoquillage.app.care.repo.CareRepository;
import com.fleurdecoquillage.app.care.domain.Care;
import com.fleurdecoquillage.app.care.domain.CareImage;
import com.fleurdecoquillage.app.care.web.dto.CareImageDto;
import com.fleurdecoquillage.app.care.web.dto.CareRequest;
import com.fleurdecoquillage.app.care.web.dto.CareResponse;
import com.fleurdecoquillage.app.care.web.mapper.CareMapper;
import com.fleurdecoquillage.app.category.repo.CategoryRepository;
import com.fleurdecoquillage.app.common.storage.FileStorageService;

@Service
public class CareService {

    private static final Logger logger = LoggerFactory.getLogger(CareService.class);

    private final CareRepository repo;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;

    public CareService(CareRepository repo, CategoryRepository categoryRepository, FileStorageService fileStorageService) {
        this.repo = repo;
        this.categoryRepository = categoryRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public Page<CareResponse> list(Pageable pageable) {
        return repo.findAll(pageable).map(CareMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CareResponse get(Long id) {
        return repo.findById(id).map(CareMapper::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Care not found: " + id));
    }

    @Transactional
    public CareResponse create(CareRequest req) {
        logger.info("====== CREATE CARE START ======");
        logger.info("Care name: {}", req.name());
        logger.info("Images count: {}", req.images() != null ? req.images().size() : 0);

        Care entity = CareMapper.toEntity(req);
        var category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + req.categoryId()));
        entity.setCategory(category);

        // Save Care first to get ID
        Care saved = repo.save(entity);
        logger.info("Care saved with ID: {}", saved.getId());

        // Process images if present
        if (req.images() != null && !req.images().isEmpty()) {
            logger.info("Processing {} images", req.images().size());
            for (int i = 0; i < req.images().size(); i++) {
                CareImageDto imageDto = req.images().get(i);
                logger.info("Image {}: name={}, has base64={}", i, imageDto.name(),
                    imageDto.base64Data() != null && !imageDto.base64Data().isEmpty());

                if (imageDto.base64Data() != null && !imageDto.base64Data().isEmpty()) {
                    logger.info("Calling fileStorageService.saveBase64Image for image {}", i);
                    // Save file to disk
                    String filePath = fileStorageService.saveBase64Image(imageDto.base64Data(), saved.getId());
                    logger.info("File saved at: {}", filePath);

                    // Extract filename from path
                    String filename = extractFilename(filePath);
                    logger.info("Filename extracted: {}", filename);

                    // Create CareImage entity
                    CareImage careImage = CareMapper.toImageEntity(imageDto, saved);
                    careImage.setFilename(filename);
                    careImage.setFilePath(filePath);

                    saved.getImages().add(careImage);
                    logger.info("CareImage entity added to care");
                }
            }
            // Save again with images
            logger.info("Saving care with images...");
            saved = repo.save(saved);
            logger.info("Care saved with {} images", saved.getImages().size());
        } else {
            logger.info("No images to process");
        }

        logger.info("====== CREATE CARE END ======");
        return CareMapper.toResponse(saved);
    }

    @Transactional
    public CareResponse update(Long id, CareRequest req) {
        logger.info("====== UPDATE CARE START ======");
        logger.info("Care ID: {}", id);
        logger.info("Care name: {}", req.name());
        logger.info("Images count: {}", req.images() != null ? req.images().size() : 0);

        Care c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Care not found: " + id));
        CareMapper.updateEntity(c, req);
        var category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + req.categoryId()));
        c.setCategory(category);

        // Handle images update
        // Remove old images (files and entities)
        logger.info("Removing {} old images", c.getImages().size());
        for (CareImage oldImage : c.getImages()) {
            fileStorageService.deleteFile(oldImage.getFilePath());
        }
        c.getImages().clear();

        // Add new images
        if (req.images() != null && !req.images().isEmpty()) {
            logger.info("Processing {} new images", req.images().size());
            for (int i = 0; i < req.images().size(); i++) {
                CareImageDto imageDto = req.images().get(i);
                logger.info("Image {}: name={}, has base64={}", i, imageDto.name(),
                    imageDto.base64Data() != null && !imageDto.base64Data().isEmpty());

                if (imageDto.base64Data() != null && !imageDto.base64Data().isEmpty()) {
                    logger.info("Calling fileStorageService.saveBase64Image for image {}", i);
                    // Save file to disk
                    String filePath = fileStorageService.saveBase64Image(imageDto.base64Data(), id);
                    logger.info("File saved at: {}", filePath);

                    // Extract filename from path
                    String filename = extractFilename(filePath);
                    logger.info("Filename extracted: {}", filename);

                    // Create CareImage entity
                    CareImage careImage = CareMapper.toImageEntity(imageDto, c);
                    careImage.setFilename(filename);
                    careImage.setFilePath(filePath);

                    c.getImages().add(careImage);
                    logger.info("CareImage entity added to care");
                }
            }
        } else {
            logger.info("No images to process");
        }

        logger.info("Saving care...");
        Care saved = repo.save(c);
        logger.info("Care saved with {} images", saved.getImages().size());
        logger.info("====== UPDATE CARE END ======");

        return CareMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        // Delete all images first
        fileStorageService.deleteCareImages(id);
        // Delete Care entity (cascade will delete CareImage entities)
        repo.deleteById(id);
    }

    private String extractFilename(String filePath) {
        // Extract filename from path like "uploads/cares/1/uuid.png"
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }
}
