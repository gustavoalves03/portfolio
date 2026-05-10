package com.luxpretty.app.care.web.dto;

import com.luxpretty.app.care.domain.CareStatus;

import java.util.List;

public record CareResponse(
        Long id,
        String name,
        Integer price,
        String description,
        Integer duration,
        CareStatus status,
        Long categoryId,
        Integer displayOrder,
        List<CareImageDto> images
) {}
