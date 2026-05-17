package com.luxpretty.app.auth.dto;

import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import jakarta.validation.constraints.NotNull;

public record ProUpgradeRequest(
    @NotNull SubscriptionTier tier,
    @NotNull SubscriptionBilling billing
) {}
