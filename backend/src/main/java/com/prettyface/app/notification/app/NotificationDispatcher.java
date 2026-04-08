package com.prettyface.app.notification.app;

import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.notification.domain.*;
import com.prettyface.app.notification.web.dto.NotificationResponse;
import com.prettyface.app.notification.web.mapper.NotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationSchemaExecutor applicationSchemaExecutor;

    public NotificationDispatcher(NotificationService notificationService,
                                   SimpMessagingTemplate messagingTemplate,
                                   ApplicationSchemaExecutor applicationSchemaExecutor) {
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
        this.applicationSchemaExecutor = applicationSchemaExecutor;
    }

    public void dispatch(List<Long> recipientIds, String tenantSlug,
                         NotificationType type, NotificationCategory category,
                         String title, String message,
                         Long referenceId, ReferenceType referenceType) {
        for (Long recipientId : recipientIds) {
            try {
                Notification notification = Notification.builder()
                        .recipientId(recipientId)
                        .tenantSlug(tenantSlug)
                        .type(type)
                        .category(category)
                        .title(title)
                        .message(message)
                        .referenceId(referenceId)
                        .referenceType(referenceType)
                        .read(false)
                        .build();

                Notification saved = applicationSchemaExecutor.call(() ->
                        notificationService.save(notification));

                NotificationResponse response = NotificationMapper.toResponse(saved);
                messagingTemplate.convertAndSendToUser(
                        recipientId.toString(),
                        "/queue/notifications",
                        response
                );
                logger.debug("Notification dispatched to user {} : {}", recipientId, type);
            } catch (Exception e) {
                logger.error("Failed to dispatch notification to user {}: {}", recipientId, e.getMessage());
            }
        }
    }
}
