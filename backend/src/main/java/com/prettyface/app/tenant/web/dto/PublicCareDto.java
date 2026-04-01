package com.prettyface.app.tenant.web.dto;

import java.util.List;

public record PublicCareDto(
        Long id,
        String name,
        String description,
        Integer duration,
        Integer price,
        List<String> imageUrls
) {}
