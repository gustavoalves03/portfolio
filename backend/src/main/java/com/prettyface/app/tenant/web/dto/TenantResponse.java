package com.prettyface.app.tenant.web.dto;

import java.time.LocalDateTime;

public record TenantResponse(
        Long id,
        String name,
        String slug,
        String description,
        String logoUrl,
        String heroImageUrl,
        String addressStreet,
        String addressPostalCode,
        String addressCity,
        String phone,
        String contactEmail,
        String siret,
        LocalDateTime updatedAt,
        Boolean employeesEnabled
) {}
