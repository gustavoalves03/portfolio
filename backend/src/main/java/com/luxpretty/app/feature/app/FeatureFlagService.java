package com.luxpretty.app.feature.app;

import com.luxpretty.app.feature.domain.FeatureFlag;
import com.luxpretty.app.feature.domain.FeatureFlagSource;
import com.luxpretty.app.feature.domain.FeatureKey;
import com.luxpretty.app.feature.repo.FeatureFlagRepository;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FeatureFlagService {

    private final FeatureFlagRepository repo;
    private final Clock clock;

    public FeatureFlagService(FeatureFlagRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /**
     * Returns whether the given feature is enabled for the current tenant.
     * Result is cached per tenant+key for up to 60 seconds.
     */
    @Cacheable(value = "featureFlags", key = "@tenantContextKey.current() + '::' + #key.name()")
    @Transactional(readOnly = true)
    public boolean isEnabled(FeatureKey key) {
        return repo.findByKey(key).map(FeatureFlag::isEnabled).orElse(false);
    }

    /**
     * Returns a snapshot of ALL feature keys for the current tenant.
     * Keys with no row default to false.
     */
    @Transactional(readOnly = true)
    public Map<FeatureKey, Boolean> snapshot() {
        Map<FeatureKey, Boolean> map = new EnumMap<>(FeatureKey.class);
        for (FeatureKey k : FeatureKey.values()) map.put(k, false);
        repo.findAll().forEach(f -> map.put(f.getKey(), f.isEnabled()));
        return map;
    }

    /**
     * Upserts all feature keys based on the tier catalog.
     * Rows with SOURCE = ADMIN_OVERRIDE are preserved unchanged.
     * All other rows are set to TIER_DEFAULT based on the catalog.
     * Evicts all cached entries for this tenant.
     */
    @Transactional
    @CacheEvict(value = "featureFlags", allEntries = true)
    public void applyTierDefaults(SubscriptionTier tier) {
        Set<FeatureKey> defaults = TierFeatureCatalog.featuresFor(tier);
        Map<FeatureKey, FeatureFlag> existing = repo.findAll().stream()
            .collect(Collectors.toMap(FeatureFlag::getKey, f -> f));
        List<FeatureFlag> upsert = new ArrayList<>();
        for (FeatureKey key : FeatureKey.values()) {
            FeatureFlag current = existing.get(key);
            if (current != null && current.getSource() == FeatureFlagSource.ADMIN_OVERRIDE) {
                continue; // preserve admin override — do not rewrite
            }
            FeatureFlag f = current != null ? current : new FeatureFlag();
            f.setKey(key);
            f.setEnabled(defaults.contains(key));
            f.setSource(FeatureFlagSource.TIER_DEFAULT);
            f.setUpdatedAt(clock.instant());
            upsert.add(f);
        }
        repo.saveAll(upsert);
    }

    /**
     * Admin override: forces a specific feature on or off regardless of tier.
     * Marks the row as ADMIN_OVERRIDE and evicts the cache.
     */
    @Transactional
    @CacheEvict(value = "featureFlags", allEntries = true)
    public void overrideForTenant(FeatureKey key, boolean enabled) {
        FeatureFlag f = repo.findByKey(key).orElseGet(() -> {
            FeatureFlag ne = new FeatureFlag();
            ne.setKey(key);
            return ne;
        });
        f.setEnabled(enabled);
        f.setSource(FeatureFlagSource.ADMIN_OVERRIDE);
        f.setUpdatedAt(clock.instant());
        repo.save(f);
    }
}
