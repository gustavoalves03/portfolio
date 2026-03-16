package com.fleurdecoquillage.app.tenant.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50000) String description, // HTML can be ~5x text content; text limit enforced in frontend (10000 chars)
        String logo // base64 nullable: null=no change, ""=remove
) {}
