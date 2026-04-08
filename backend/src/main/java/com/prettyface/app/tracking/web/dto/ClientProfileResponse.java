package com.prettyface.app.tracking.web.dto;

import java.time.LocalDateTime;

public record ClientProfileResponse(
        Long id,
        Long userId,
        String notes,
        String skinType,
        String hairType,
        String allergies,
        String preferences,
        boolean consentPhotos,
        boolean consentPublicShare,
        LocalDateTime consentGivenAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String updatedByName
) {}
