package com.luxpretty.app.tenant.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "SALON_PREVIEW_TOKENS", uniqueConstraints = {
        @UniqueConstraint(name = "UK_PREVIEW_TOKEN_TOKEN", columnNames = "token")
}, indexes = {
        @Index(name = "IX_PREVIEW_TOKEN_TENANT", columnList = "tenant_id")
})
public class SalonPreviewToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "tenant_id",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "FK_PREVIEW_TOKEN_TENANT")
    )
    private Tenant tenant;

    @Column(name = "token", nullable = false, length = 64)
    private String token;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
