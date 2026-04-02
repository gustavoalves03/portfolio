package com.prettyface.app.tenant.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50000) String description,
        String logo,
        String heroImage,
        @Size(max = 255) String addressStreet,
        @Size(max = 10) String addressPostalCode,
        @Size(max = 100) String addressCity,
        @Size(max = 20) String phone,
        @Size(max = 255) String contactEmail,
        @Size(max = 14) String siret,
        Boolean employeesEnabled
) {}
