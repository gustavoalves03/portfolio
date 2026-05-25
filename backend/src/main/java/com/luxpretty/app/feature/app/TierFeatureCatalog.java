package com.luxpretty.app.feature.app;

import com.luxpretty.app.feature.domain.FeatureKey;
import com.luxpretty.app.subscription.domain.SubscriptionTier;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TierFeatureCatalog {

    private static final Map<SubscriptionTier, Set<FeatureKey>> CATALOG = Map.of(
        SubscriptionTier.VITRINE, EnumSet.noneOf(FeatureKey.class),
        SubscriptionTier.GESTION, EnumSet.of(
            FeatureKey.BOOKING, FeatureKey.EMPLOYEES, FeatureKey.PHOTOS,
            FeatureKey.SMS_REMINDER, FeatureKey.CLIENT_FILES, FeatureKey.ABSENCE_MGMT
        ),
        SubscriptionTier.PREMIUM, EnumSet.allOf(FeatureKey.class)
    );

    public static Set<FeatureKey> featuresFor(SubscriptionTier tier) {
        return Set.copyOf(CATALOG.getOrDefault(tier, Set.of()));
    }

    /** Returns the minimum tier that includes the given feature, or PREMIUM as fallback. */
    public static SubscriptionTier minimumTierFor(FeatureKey key) {
        for (SubscriptionTier tier : new SubscriptionTier[]{
                SubscriptionTier.VITRINE, SubscriptionTier.GESTION, SubscriptionTier.PREMIUM}) {
            if (CATALOG.get(tier).contains(key)) return tier;
        }
        return SubscriptionTier.PREMIUM;
    }

    private TierFeatureCatalog() {}
}
