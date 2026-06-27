package com.luxpretty.app.feature.app;

import com.luxpretty.app.feature.domain.FeatureKey;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TierFeatureCatalogTests {

    @Test
    void vitrine_hasNoFeatureFlagsEnabled() {
        assertTrue(TierFeatureCatalog.featuresFor(SubscriptionTier.VITRINE).isEmpty());
    }

    @Test
    void gestion_hasOperationalFeaturesButNotPremiumOnes() {
        var gestion = TierFeatureCatalog.featuresFor(SubscriptionTier.GESTION);
        assertTrue(gestion.contains(FeatureKey.BOOKING));
        assertTrue(gestion.contains(FeatureKey.EMPLOYEES));
        assertTrue(gestion.contains(FeatureKey.ABSENCE_MGMT));
        assertTrue(gestion.contains(FeatureKey.PHOTOS));
        assertTrue(gestion.contains(FeatureKey.SMS_REMINDER));
        assertTrue(gestion.contains(FeatureKey.CLIENT_FILES));
        assertFalse(gestion.contains(FeatureKey.SHOP));
        assertFalse(gestion.contains(FeatureKey.LOYALTY));
        assertFalse(gestion.contains(FeatureKey.ONLINE_PAYMENT));
        assertFalse(gestion.contains(FeatureKey.MULTI_LOCATION));
    }

    @Test
    void premium_hasAllFeatures() {
        var premium = TierFeatureCatalog.featuresFor(SubscriptionTier.PREMIUM);
        for (FeatureKey k : FeatureKey.values()) {
            assertTrue(premium.contains(k), "PREMIUM missing " + k);
        }
    }

    @Test
    void featuresFor_returnsImmutableCopy() {
        var set = TierFeatureCatalog.featuresFor(SubscriptionTier.PREMIUM);
        assertThrows(UnsupportedOperationException.class, () -> set.add(FeatureKey.BOOKING));
    }

    @Test
    void minimumTierFor_returnsGestionForOperationalFeatures() {
        assertEquals(SubscriptionTier.GESTION, TierFeatureCatalog.minimumTierFor(FeatureKey.BOOKING));
        assertEquals(SubscriptionTier.GESTION, TierFeatureCatalog.minimumTierFor(FeatureKey.EMPLOYEES));
        assertEquals(SubscriptionTier.GESTION, TierFeatureCatalog.minimumTierFor(FeatureKey.ABSENCE_MGMT));
    }

    @Test
    void minimumTierFor_returnsPremiumForPremiumOnlyFeatures() {
        assertEquals(SubscriptionTier.PREMIUM, TierFeatureCatalog.minimumTierFor(FeatureKey.SHOP));
        assertEquals(SubscriptionTier.PREMIUM, TierFeatureCatalog.minimumTierFor(FeatureKey.LOYALTY));
        assertEquals(SubscriptionTier.PREMIUM, TierFeatureCatalog.minimumTierFor(FeatureKey.MULTI_LOCATION));
    }
}
