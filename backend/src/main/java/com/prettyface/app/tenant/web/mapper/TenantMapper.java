package com.prettyface.app.tenant.web.mapper;

import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.domain.CareImage;
import com.prettyface.app.care.domain.CareStatus;
import com.prettyface.app.category.domain.Category;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.web.dto.*;

import java.util.Comparator;
import java.util.List;

public class TenantMapper {

    private TenantMapper() {}

    public static TenantResponse toResponse(Tenant tenant) {
        String logoUrl = tenant.getLogoPath() != null
                ? "/api/images/tenant/" + tenant.getId() + "/" + extractFilename(tenant.getLogoPath())
                : null;

        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getDescription(),
                logoUrl,
                tenant.getAddressStreet(),
                tenant.getAddressPostalCode(),
                tenant.getAddressCity(),
                tenant.getPhone(),
                tenant.getContactEmail(),
                tenant.getSiret(),
                tenant.getUpdatedAt()
        );
    }

    public static PublicSalonResponse toPublicResponse(Tenant tenant, List<Category> categories) {
        String logoUrl = tenant.getLogoPath() != null
                ? "/api/images/tenant/" + tenant.getId() + "/" + extractFilename(tenant.getLogoPath())
                : null;

        List<PublicCategoryDto> categoryDtos = categories.stream()
                .filter(cat -> cat.getCares() != null && cat.getCares().stream()
                        .anyMatch(c -> c.getStatus() == CareStatus.ACTIVE))
                .sorted(Comparator.comparing(Category::getName))
                .map(TenantMapper::toCategoryDto)
                .toList();

        return new PublicSalonResponse(
                tenant.getName(),
                tenant.getSlug(),
                tenant.getDescription(),
                logoUrl,
                categoryDtos
        );
    }

    private static PublicCategoryDto toCategoryDto(Category category) {
        List<PublicCareDto> careDtos = category.getCares().stream()
                .filter(c -> c.getStatus() == CareStatus.ACTIVE)
                .sorted(Comparator.comparing(Care::getName))
                .map(TenantMapper::toCareDto)
                .toList();

        return new PublicCategoryDto(category.getName(), careDtos);
    }

    private static PublicCareDto toCareDto(Care care) {
        List<String> imageUrls = care.getImages() != null
                ? care.getImages().stream()
                    .sorted(Comparator.comparingInt(CareImage::getImageOrder))
                    .map(img -> "/api/images/cares/" + care.getId() + "/" + img.getFilename())
                    .toList()
                : List.of();

        return new PublicCareDto(care.getId(), care.getName(), care.getDescription(), care.getDuration(), care.getPrice(), imageUrls);
    }

    private static String extractFilename(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }
}
