package com.prettyface.app.tenant.web.dto;

import java.time.LocalDateTime;

public record TenantResponse(
        Long id,
        String name,
        String slug,
        String description,
        String logoUrl,
        LocalDateTime updatedAt
) {}
