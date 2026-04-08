package com.prettyface.app.tracking.web.dto;

import com.prettyface.app.tracking.domain.PhotoType;

public record VisitPhotoResponse(
        Long id,
        PhotoType photoType,
        String imageUrl,
        Integer imageOrder,
        String uploadedByName
) {}
