package com.prettyface.app.tenant.web.dto;

import java.time.LocalDateTime;

public record PreviewTokenResponse(
        Long id,
        String token,
        String shareUrl,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt
) {}
