package com.luxpretty.app.subscription.web.dto;

import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSubscriptionRequest(
    @NotNull SubscriptionTier tier,
    @NotNull SubscriptionBilling billing,
    @NotBlank String paymentMethodId
) {}
