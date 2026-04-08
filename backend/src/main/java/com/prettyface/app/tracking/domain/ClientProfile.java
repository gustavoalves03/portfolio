package com.prettyface.app.tracking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "CLIENT_PROFILES")
@Getter
@Setter
@NoArgsConstructor
public class ClientProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "skin_type", length = 100)
    private String skinType;

    @Column(name = "hair_type", length = 100)
    private String hairType;

    @Column(name = "allergies", length = 500)
    private String allergies;

    @Column(name = "preferences", length = 500)
    private String preferences;

    @Column(name = "consent_photos", nullable = false)
    private boolean consentPhotos = false;

    @Column(name = "consent_public_share", nullable = false)
    private boolean consentPublicShare = false;

    @Column(name = "consent_given_at")
    private LocalDateTime consentGivenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
