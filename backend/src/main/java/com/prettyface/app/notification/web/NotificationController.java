package com.prettyface.app.notification.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.notification.app.NotificationService;
import com.prettyface.app.notification.web.dto.NotificationResponse;
import com.prettyface.app.notification.web.mapper.NotificationMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final ApplicationSchemaExecutor applicationSchemaExecutor;

    public NotificationController(NotificationService notificationService,
                                   ApplicationSchemaExecutor applicationSchemaExecutor) {
        this.notificationService = notificationService;
        this.applicationSchemaExecutor = applicationSchemaExecutor;
    }

    @GetMapping
    public Page<NotificationResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Boolean read,
            Pageable pageable) {
        return applicationSchemaExecutor.call(() ->
                notificationService.listForRecipient(principal.getId(), read, pageable)
                        .map(NotificationMapper::toResponse));
    }

    @GetMapping("/unread/count")
    public long unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return applicationSchemaExecutor.call(() ->
                notificationService.countUnread(principal.getId()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        applicationSchemaExecutor.run(() ->
                notificationService.markAsRead(id, principal.getId()));
        return ResponseEntity.noContent().build();
    }
}
