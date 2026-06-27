package com.luxpretty.app.feature.app;

import com.luxpretty.app.feature.domain.FeatureKey;
import com.luxpretty.app.subscription.domain.SubscriptionTier;

public class FeatureDisabledException extends RuntimeException {
    public final FeatureKey featureKey;
    public final SubscriptionTier minimumTier;

    public FeatureDisabledException(FeatureKey key, SubscriptionTier min) {
        super("Feature disabled: " + key);
        this.featureKey = key;
        this.minimumTier = min;
    }
}
