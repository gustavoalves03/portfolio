package com.luxpretty.app.feature.repo;

import com.luxpretty.app.feature.domain.FeatureFlag;
import com.luxpretty.app.feature.domain.FeatureKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, FeatureKey> {
    Optional<FeatureFlag> findByKey(FeatureKey key);
}
