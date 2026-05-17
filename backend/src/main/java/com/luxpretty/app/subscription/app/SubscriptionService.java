package com.luxpretty.app.subscription.app;

import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionStatus;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.User;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.model.billingportal.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Orchestrates Stripe subscription lifecycle for tenants.
 *
 * Responsibilities:
 * - Idempotent Customer creation at pro signup (initializeForTenant)
 * - Subscription creation and persistence (startCheckout)
 * - Syncing subscription state from webhook events (applySubscriptionUpdate)
 * - Customer Portal access (createPortalSession)
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final StripeService stripeService;
    private final PricingCatalog pricingCatalog;
    private final TenantRepository tenantRepository;

    /**
     * Idempotent: Creates a Stripe Customer for a tenant if one doesn't already exist.
     * Called immediately after tenant provisioning in AuthController.registerProWithSalonInfo.
     *
     * @param owner the User object (contains name, email)
     * @param tenant the Tenant to initialize (will have stripeCustomerId set)
     * @throws Exception if Stripe API call fails
     */
    @Transactional
    public void initializeForTenant(User owner, Tenant tenant) throws Exception {
        // Idempotent: if customer already created, skip
        if (tenant.getStripeCustomerId() != null && !tenant.getStripeCustomerId().isBlank()) {
            return;
        }

        var customer = stripeService.createCustomer(owner, tenant);
        tenant.setStripeCustomerId(customer.getId());
        tenantRepository.save(tenant);
    }

    /**
     * Creates a Stripe Subscription for a tenant and persists its ID + tier + billing to the Tenant.
     *
     * @param tenantId the tenant's ID
     * @param tier the subscription tier (VITRINE, GESTION, PREMIUM)
     * @param billing the billing period (MONTHLY, YEARLY)
     * @param paymentMethodId the Stripe Payment Method ID (e.g., pm_xxx)
     * @return the updated Tenant with subscription details
     * @throws Exception if Stripe API call fails
     */
    @Transactional
    public Tenant startCheckout(Long tenantId, SubscriptionTier tier, SubscriptionBilling billing,
                                String paymentMethodId) throws Exception {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        String priceId = pricingCatalog.priceIdFor(tier, billing)
            .orElseThrow(() -> new IllegalArgumentException(
                "No Stripe price configured for tier=" + tier + ", billing=" + billing));

        Subscription stripeSub = stripeService.createSubscription(
            tenant.getStripeCustomerId(), priceId, paymentMethodId);

        tenant.setStripeSubscriptionId(stripeSub.getId());
        tenant.setSubscriptionTier(tier);
        tenant.setSubscriptionBilling(billing);
        tenantRepository.save(tenant);

        return tenant;
    }

    /**
     * Applies a Stripe subscription update (from webhook events).
     * Finds the tenant by subscriptionId (or falls back to customerId), then syncs status, period_end, and tier.
     *
     * @param stripeSub the Stripe Subscription object from a webhook event
     * @throws Exception if tenant not found or update fails
     */
    @Transactional
    public void applySubscriptionUpdate(Subscription stripeSub) throws Exception {
        Tenant tenant = tenantRepository.findByStripeSubscriptionId(stripeSub.getId())
            .orElseGet(() -> tenantRepository.findByStripeCustomerId(stripeSub.getCustomer())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Tenant not found for subscription " + stripeSub.getId())));

        // Map Stripe status string to SubscriptionStatus enum
        SubscriptionStatus status = mapStripeStatus(stripeSub.getStatus());
        tenant.setSubscriptionStatus(status);

        // Set currentPeriodEnd if available
        if (stripeSub.getCurrentPeriodEnd() != null) {
            LocalDateTime periodEnd = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()),
                ZoneOffset.UTC);
            tenant.setCurrentPeriodEnd(periodEnd);
        }

        // Extract price ID from subscription items and reverse-lookup the tier
        if (stripeSub.getItems() != null && stripeSub.getItems().getData() != null
            && !stripeSub.getItems().getData().isEmpty()) {
            String priceId = stripeSub.getItems().getData().get(0).getPrice().getId();
            pricingCatalog.tierBillingFor(priceId).ifPresent(tierBilling -> {
                tenant.setSubscriptionTier(tierBilling.tier());
                tenant.setSubscriptionBilling(tierBilling.billing());
            });
        }

        tenantRepository.save(tenant);
    }

    /**
     * Creates a Stripe SetupIntent for payment method collection (Stripe Elements).
     * Resolves the tenant's Stripe Customer ID and delegates to StripeService.
     *
     * @param tenantId the tenant's ID
     * @return the Stripe SetupIntent with client_secret for Elements
     * @throws IllegalArgumentException if tenant not found or stripeCustomerId is null
     * @throws Exception if Stripe API call fails
     */
    @Transactional(readOnly = true)
    public SetupIntent createSetupIntent(Long tenantId) throws Exception {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        if (tenant.getStripeCustomerId() == null || tenant.getStripeCustomerId().isBlank()) {
            throw new IllegalArgumentException(
                "Stripe customer not yet initialized for tenant: " + tenantId);
        }

        return stripeService.createSetupIntent(tenant.getStripeCustomerId());
    }

    /**
     * Opens the Stripe Customer Portal for subscription management.
     *
     * @param tenantId the tenant's ID
     * @return the Stripe Session with the portal URL
     * @throws Exception if Stripe API call fails
     */
    @Transactional(readOnly = true)
    public Session createPortalSession(Long tenantId) throws Exception {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        return stripeService.createPortalSession(tenant.getStripeCustomerId());
    }

    /**
     * Maps a Stripe subscription status string to our SubscriptionStatus enum.
     * Reference: https://stripe.com/docs/api/subscriptions/object#subscription_object-status
     */
    private SubscriptionStatus mapStripeStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return SubscriptionStatus.VITRINE_FREE;
        }

        return switch (stripeStatus) {
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "active" -> SubscriptionStatus.ACTIVE;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            case "incomplete" -> SubscriptionStatus.INCOMPLETE;
            case "incomplete_expired" -> SubscriptionStatus.INCOMPLETE_EXPIRED;
            case "paused" -> SubscriptionStatus.PAUSED;
            default -> SubscriptionStatus.VITRINE_FREE;
        };
    }
}
