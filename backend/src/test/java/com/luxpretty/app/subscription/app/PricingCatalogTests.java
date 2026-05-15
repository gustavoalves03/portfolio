package com.luxpretty.app.subscription.app;

import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PricingCatalogTests {

    @Test
    void priceIdFor_returnsEmpty_forVitrineTier() {
        PricingCatalog catalog = new PricingCatalog(
            "price_gestion_monthly", "price_gestion_yearly",
            "price_premium_monthly", "price_premium_yearly");

        Optional<String> result = catalog.priceIdFor(
            SubscriptionTier.VITRINE, SubscriptionBilling.FREE);

        assertThat(result).isEmpty();
    }

    @Test
    void priceIdFor_returnsConfiguredPrice_forGestionMonthly() {
        PricingCatalog catalog = new PricingCatalog(
            "price_gestion_monthly_test", "",
            "", "");

        Optional<String> result = catalog.priceIdFor(
            SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY);

        assertThat(result).contains("price_gestion_monthly_test");
    }

    @Test
    void priceIdFor_returnsConfiguredPrice_forPremiumYearly() {
        PricingCatalog catalog = new PricingCatalog(
            "", "", "", "price_premium_yearly_test");

        Optional<String> result = catalog.priceIdFor(
            SubscriptionTier.PREMIUM, SubscriptionBilling.YEARLY);

        assertThat(result).contains("price_premium_yearly_test");
    }

    @Test
    void tierBillingFor_returnsCorrectTuple_givenPriceId() {
        PricingCatalog catalog = new PricingCatalog(
            "price_GM", "price_GY",
            "price_PM", "price_PY");

        Optional<PricingCatalog.TierBilling> result = catalog.tierBillingFor("price_GY");

        assertThat(result).isPresent();
        assertThat(result.get().tier()).isEqualTo(SubscriptionTier.GESTION);
        assertThat(result.get().billing()).isEqualTo(SubscriptionBilling.YEARLY);
    }

    @Test
    void priceIdFor_returnsEmpty_whenPropertyIsBlank() {
        // Blank/unset properties (env vars not provided) → no matching price
        PricingCatalog catalog = new PricingCatalog("", "", "", "");

        assertThat(catalog.priceIdFor(SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY)).isEmpty();
        assertThat(catalog.priceIdFor(SubscriptionTier.PREMIUM, SubscriptionBilling.YEARLY)).isEmpty();
    }
}
