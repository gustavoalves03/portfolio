package com.fleurdecoquillage.app.tenant.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "TENANTS", uniqueConstraints = {
        @UniqueConstraint(name = "UK_TENANT_SLUG", columnNames = "slug"),
        @UniqueConstraint(name = "UK_TENANT_OWNER", columnNames = "owner_id")
})
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "description", columnDefinition = "CLOB")
    private String description;

    @Column(name = "logo_path", length = 500)
    private String logoPath;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
