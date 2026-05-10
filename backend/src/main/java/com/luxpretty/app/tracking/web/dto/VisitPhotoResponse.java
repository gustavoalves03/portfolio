package com.luxpretty.app.tracking.web.dto;

import com.luxpretty.app.tracking.domain.PhotoType;

public record VisitPhotoResponse(
        Long id,
        PhotoType photoType,
        String imageUrl,
        Integer imageOrder,
        String uploadedByName
) {}
