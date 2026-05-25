package com.luxpretty.app.feature.repo;

import com.luxpretty.app.feature.domain.FeatureFlag;
import com.luxpretty.app.feature.domain.FeatureFlagSource;
import com.luxpretty.app.feature.domain.FeatureKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class FeatureFlagRepositoryTests {

    @Autowired
    FeatureFlagRepository repo;

    @Test
    void persistsAndFindsByKey() {
        FeatureFlag f = new FeatureFlag();
        f.setKey(FeatureKey.SHOP);
        f.setEnabled(true);
        f.setSource(FeatureFlagSource.ADMIN_OVERRIDE);
        f.setUpdatedAt(Instant.now());
        repo.saveAndFlush(f);

        Optional<FeatureFlag> found = repo.findByKey(FeatureKey.SHOP);
        assertTrue(found.isPresent());
        assertTrue(found.get().isEnabled());
        assertEquals(FeatureFlagSource.ADMIN_OVERRIDE, found.get().getSource());
    }

    @Test
    void findByKey_returnsEmptyWhenAbsent() {
        Optional<FeatureFlag> found = repo.findByKey(FeatureKey.LOYALTY);
        assertTrue(found.isEmpty());
    }
}
