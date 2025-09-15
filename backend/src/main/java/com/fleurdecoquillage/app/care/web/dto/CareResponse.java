package com.fleurdecoquillage.app.care.web.dto;

import com.fleurdecoquillage.app.care.domain.CareStatus;

public record CareResponse(
        Long id,
        String name,
        Integer price,
        String description,
        Integer duration,
        CareStatus status,
        Long categoryId
) {}
