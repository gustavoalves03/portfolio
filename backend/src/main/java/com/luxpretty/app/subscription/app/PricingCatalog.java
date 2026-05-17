package com.luxpretty.app.subscription.app;

import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps (SubscriptionTier, SubscriptionBilling) tuples to Stripe Price IDs.
 *
 * <p>The Price IDs themselves are configured via {@code app.stripe.price.*}
 * properties (from env vars in prod), so they can differ between test and
 * live mode without code changes.
 *
 * <p>All tiers (VITRINE, GESTION, PREMIUM) are paid tiers. If a Price ID
 * is not configured for a given tier/billing combo, lookups return
 * {@link Optional#empty()}.
 */
@Component
public class PricingCatalog {

    private final Map<TierBilling, String> priceMap;

    public PricingCatalog(
        @Value("${app.stripe.price.vitrine-monthly:}") String vitrineMonthly,
        @Value("${app.stripe.price.vitrine-yearly:}") String vitrineYearly,
        @Value("${app.stripe.price.gestion-monthly:}") String gestionMonthly,
        @Value("${app.stripe.price.gestion-yearly:}") String gestionYearly,
        @Value("${app.stripe.price.premium-monthly:}") String premiumMonthly,
        @Value("${app.stripe.price.premium-yearly:}") String premiumYearly
    ) {
        // We use a mutable HashMap (not Map.of) so blank values can be
        // omitted; lookups for blank entries naturally return empty.
        this.priceMap = new HashMap<>();
        putIfPresent(SubscriptionTier.VITRINE, SubscriptionBilling.MONTHLY, vitrineMonthly);
        putIfPresent(SubscriptionTier.VITRINE, SubscriptionBilling.YEARLY, vitrineYearly);
        putIfPresent(SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY, gestionMonthly);
        putIfPresent(SubscriptionTier.GESTION, SubscriptionBilling.YEARLY, gestionYearly);
        putIfPresent(SubscriptionTier.PREMIUM, SubscriptionBilling.MONTHLY, premiumMonthly);
        putIfPresent(SubscriptionTier.PREMIUM, SubscriptionBilling.YEARLY, premiumYearly);
    }

    private void putIfPresent(SubscriptionTier tier, SubscriptionBilling billing, String priceId) {
        if (priceId != null && !priceId.isBlank()) {
            priceMap.put(new TierBilling(tier, billing), priceId);
        }
    }

    /**
     * Returns the Stripe Price ID for the (tier, billing) combo, or empty if
     * the matching property isn't configured.
     */
    public Optional<String> priceIdFor(SubscriptionTier tier, SubscriptionBilling billing) {
        return Optional.ofNullable(priceMap.get(new TierBilling(tier, billing)));
    }

    /**
     * Reverse lookup: given a Stripe Price ID (e.g. from a webhook event),
     * find the matching (tier, billing) tuple. Used by SubscriptionService
     * when applying webhook updates that carry only price IDs.
     */
    public Optional<TierBilling> tierBillingFor(String priceId) {
        if (priceId == null) return Optional.empty();
        return priceMap.entrySet().stream()
            .filter(e -> e.getValue().equals(priceId))
            .map(Map.Entry::getKey)
            .findFirst();
    }

    public record TierBilling(SubscriptionTier tier, SubscriptionBilling billing) {}
}
