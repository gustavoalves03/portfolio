package com.luxpretty.app.notification.web;

import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.multitenancy.ApplicationSchemaExecutor;
import com.luxpretty.app.notification.app.NotificationService;
import com.luxpretty.app.notification.web.dto.NotificationResponse;
import com.luxpretty.app.notification.web.mapper.NotificationMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            Pageable pageable) {
        return applicationSchemaExecutor.call(() ->
                notificationService.listForRecipient(principal.getId(), read, since, pageable)
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
