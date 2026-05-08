package com.prettyface.app.tenant.web.dto;

import jakarta.validation.constraints.Size;

public record PatchTenantRequest(
        @Size(max = 100) String name,
        @Size(max = 50000) String description,
        String logo,
        String heroImage,
        @Size(max = 255) String addressStreet,
        @Size(max = 10) String addressPostalCode,
        @Size(max = 100) String addressCity,
        @Size(max = 2) String addressCountry,
        @Size(max = 20) String phone,
        @Size(max = 255) String contactEmail,
        @Size(max = 1000) String categorySlugs
) {}
