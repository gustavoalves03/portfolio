package com.prettyface.app.tenant.web.dto;

import java.util.List;

public record PublicSalonResponse(
        String name,
        String slug,
        String description,
        String logoUrl,
        List<PublicCategoryDto> categories
) {}
