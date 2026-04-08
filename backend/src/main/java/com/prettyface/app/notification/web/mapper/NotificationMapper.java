package com.prettyface.app.notification.web.mapper;

import com.prettyface.app.notification.domain.Notification;
import com.prettyface.app.notification.web.dto.NotificationResponse;

public class NotificationMapper {

    private NotificationMapper() {}

    public static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getCategory().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReferenceId(),
                notification.getReferenceType().name(),
                notification.isRead(),
                notification.getTenantSlug(),
                notification.getCreatedAt()
        );
    }
}
