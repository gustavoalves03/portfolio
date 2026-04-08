package com.prettyface.app.notification.web.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String category,
        String title,
        String message,
        Long referenceId,
        String referenceType,
        boolean read,
        String tenantSlug,
        LocalDateTime createdAt
) {}
