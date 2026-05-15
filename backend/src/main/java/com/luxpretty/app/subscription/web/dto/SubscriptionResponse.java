package com.luxpretty.app.subscription.web.dto;

import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionStatus;
import com.luxpretty.app.subscription.domain.SubscriptionTier;

import java.time.LocalDateTime;

public record SubscriptionResponse(
    SubscriptionTier tier,
    SubscriptionBilling billing,
    SubscriptionStatus status,
    String stripeCustomerId,       // may be null
    String stripeSubscriptionId,   // may be null
    LocalDateTime currentPeriodEnd,  // may be null
    LocalDateTime trialEnd            // may be null
) {}
