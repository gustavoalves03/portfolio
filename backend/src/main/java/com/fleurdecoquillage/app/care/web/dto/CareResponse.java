package com.fleurdecoquillage.app.care.web.dto;

import com.fleurdecoquillage.app.care.domain.CareStatus;

import java.util.List;

public record CareResponse(
        Long id,
        String name,
        Integer price,
        String description,
        Integer duration,
        CareStatus status,
        Long categoryId,
        List<CareImageDto> images
) {}
