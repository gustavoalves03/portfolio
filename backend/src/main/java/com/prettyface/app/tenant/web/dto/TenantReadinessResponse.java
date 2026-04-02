package com.prettyface.app.tenant.web.dto;

public record TenantReadinessResponse(
    String slug,
    boolean name,
    boolean hasCategory,
    boolean hasActiveCare,
    boolean hasOpeningHours,
    boolean canPublish,
    String status,
    boolean employeesEnabled
) {}
