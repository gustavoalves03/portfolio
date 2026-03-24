package com.prettyface.app.tenant.web.dto;

import java.util.List;

public record PublicCategoryDto(
        String name,
        List<PublicCareDto> cares
) {}
