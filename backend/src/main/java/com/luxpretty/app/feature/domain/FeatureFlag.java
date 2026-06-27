package com.luxpretty.app.feature.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "TENANT_FEATURES")
public class FeatureFlag {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "FEATURE_KEY", length = 64)
    private FeatureKey key;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "SOURCE", length = 16, nullable = false)
    private FeatureFlagSource source;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;
}
