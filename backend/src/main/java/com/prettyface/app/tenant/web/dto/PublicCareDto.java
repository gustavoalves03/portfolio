package com.prettyface.app.tenant.web.dto;

import java.util.List;

public record PublicCareDto(
        Long id,
        String name,
        Integer duration,
        Integer price,
        List<String> imageUrls
) {}
