package com.luxpretty.app.auth.dto;

import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import jakarta.validation.constraints.*;

public record ProRegisterRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    @NotNull @AssertTrue Boolean consent,
    @NotNull SubscriptionTier tier,
    @NotNull SubscriptionBilling billing,
    String salonName,
    String phone,
    String addressStreet,
    String addressPostalCode,
    String addressCity,
    String siret
) {}
