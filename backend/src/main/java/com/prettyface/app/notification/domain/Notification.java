package com.prettyface.app.notification.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "NOTIFICATIONS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "RECIPIENT_ID", nullable = false)
    private Long recipientId;

    @Column(name = "TENANT_SLUG", nullable = false, length = 100)
    private String tenantSlug;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORY", nullable = false, length = 30)
    private NotificationCategory category;

    @Column(name = "TITLE", nullable = false, length = 255)
    private String title;

    @Column(name = "MESSAGE", nullable = false, length = 500)
    private String message;

    @Column(name = "REFERENCE_ID", nullable = false)
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "REFERENCE_TYPE", nullable = false, length = 50)
    private ReferenceType referenceType;

    @Column(name = "IS_READ", nullable = false)
    private boolean read;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
