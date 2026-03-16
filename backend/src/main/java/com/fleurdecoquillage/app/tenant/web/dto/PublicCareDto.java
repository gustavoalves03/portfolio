package com.fleurdecoquillage.app.tenant.web.dto;

import java.util.List;

public record PublicCareDto(
        String name,
        Integer duration,
        Integer price,
        List<String> imageUrls
) {}
