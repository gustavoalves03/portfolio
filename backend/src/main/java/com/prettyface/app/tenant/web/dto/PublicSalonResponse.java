package com.prettyface.app.tenant.web.dto;

import java.util.List;

public record PublicSalonResponse(
        String name,
        String slug,
        String status,
        String description,
        String logoUrl,
        String heroImageUrl,
        List<PublicCategoryDto> categories,
        String addressStreet,
        String addressPostalCode,
        String addressCity,
        String addressCountry,
        String phone,
        String contactEmail
) {}
