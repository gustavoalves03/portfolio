package com.prettyface.app.tracking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "CLIENT_REMINDERS")
@Getter
@Setter
@NoArgsConstructor
public class ClientReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "care_id")
    private Long careId;

    @Column(name = "care_name", length = 255)
    private String careName;

    @Column(name = "recommended_date")
    private LocalDate recommendedDate;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "sent", nullable = false)
    private boolean sent = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
