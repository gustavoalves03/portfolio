package com.luxpretty.app.tenant.web.dto;

public record TenantReadinessResponse(
    String slug,
    boolean name,
    boolean hasCategory,
    boolean hasContact,
    boolean hasLogo,
    boolean hasActiveCare,
    boolean hasOpeningHours,
    boolean canPublish,
    String status,
    boolean employeesEnabled,
    int annualLeaveDays
) {}
