package com.luxpretty.app.subscription.web.dto;

import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionTier;

import java.math.BigDecimal;

public record PricingPlanDto(
    SubscriptionTier tier,
    SubscriptionBilling billing,
    BigDecimal monthlyPriceEuros,  // displayed monthly equivalent (yearly plans show /12)
    String currency                 // "EUR"
) {}
