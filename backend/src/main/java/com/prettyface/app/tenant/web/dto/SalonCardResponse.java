package com.prettyface.app.tenant.web.dto;

public record SalonCardResponse(
        String name,
        String slug,
        String description,
        String logoUrl,
        String categoryNames,
        String addressCity,
        String fullAddress
) {}
