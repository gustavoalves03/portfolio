package com.luxpretty.app.tracking.web.dto;

public record UpdateClientProfileRequest(
        String notes,
        String skinType,
        String hairType,
        String allergies,
        String preferences
) {}
