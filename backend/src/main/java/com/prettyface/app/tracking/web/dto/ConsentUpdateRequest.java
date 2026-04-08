package com.prettyface.app.tracking.web.dto;

public record ConsentUpdateRequest(
        boolean consentPhotos,
        boolean consentPublicShare
) {}
