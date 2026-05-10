package com.luxpretty.app.tracking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "SALON_CLIENTS")
@Getter
@Setter
@NoArgsConstructor
public class SalonClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // TODO: real Oracle DB still has NOT NULL on PHONE — add ALTER TABLE migration
    // to drop the constraint before deploying. Test profiles use ddl-auto=create-drop
    // so this annotation takes effect there immediately.
    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "is_manual", nullable = false)
    private boolean manual = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
