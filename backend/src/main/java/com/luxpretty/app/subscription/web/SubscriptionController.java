package com.luxpretty.app.subscription.web;

import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.subscription.app.PricingCatalog;
import com.luxpretty.app.subscription.app.SubscriptionService;
import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import com.luxpretty.app.subscription.web.dto.CreateSubscriptionRequest;
import com.luxpretty.app.subscription.web.dto.PortalSessionResponse;
import com.luxpretty.app.subscription.web.dto.PricingPlanDto;
import com.luxpretty.app.subscription.web.dto.SetupIntentResponse;
import com.luxpretty.app.subscription.web.dto.StripeConfigResponse;
import com.luxpretty.app.subscription.web.dto.SubscriptionResponse;
import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST endpoints for subscription lifecycle management.
 *
 * GET /api/pricing — public, returns all pricing plans
 * POST /api/pro/subscription/setup-intent — authenticated, returns SetupIntent client_secret
 * POST /api/pro/subscription/create — authenticated, creates subscription from Stripe token
 * GET /api/pro/subscription — authenticated, returns current subscription state
 * POST /api/pro/subscription/portal-session — authenticated, returns portal URL
 */
@RestController
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final PricingCatalog pricingCatalog;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @Value("${app.stripe.publishable-key:}")
    private String stripePublishableKey;

    // PUBLIC — returns the Stripe publishable key for frontend Stripe.js initialisation
    @GetMapping("/api/stripe/config")
    public StripeConfigResponse getStripeConfig() {
        return new StripeConfigResponse(stripePublishableKey);
    }

    // PUBLIC — no auth (mapped at /api/pricing)
    @GetMapping("/api/pricing")
    public List<PricingPlanDto> getPricing() {
        // Return all (tier, billing) combos with their price label/euros.
        // Hardcoded prices from spec: VITRINE 0€, GESTION 49.99/42.49, PREMIUM 67.99/57.79
        return List.of(
            new PricingPlanDto(SubscriptionTier.VITRINE, SubscriptionBilling.FREE,
                BigDecimal.valueOf(0.00), "EUR"),
            new PricingPlanDto(SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY,
                BigDecimal.valueOf(49.99), "EUR"),
            new PricingPlanDto(SubscriptionTier.GESTION, SubscriptionBilling.YEARLY,
                BigDecimal.valueOf(42.49), "EUR"),
            new PricingPlanDto(SubscriptionTier.PREMIUM, SubscriptionBilling.MONTHLY,
                BigDecimal.valueOf(67.99), "EUR"),
            new PricingPlanDto(SubscriptionTier.PREMIUM, SubscriptionBilling.YEARLY,
                BigDecimal.valueOf(57.79), "EUR")
        );
    }

    // PRO-only — authentication enforced by SecurityConfig (/api/pro/**)
    @PostMapping("/api/pro/subscription/setup-intent")
    public SetupIntentResponse createSetupIntent() throws Exception {
        // Resolve active tenant via TenantContext.getCurrentTenant() → findBySlug.
        String tenantSlug = TenantContext.getCurrentTenant();
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenant context");
        }

        Tenant tenant = tenantRepository.findBySlug(tenantSlug)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Tenant not found: " + tenantSlug));

        // Lazy-init the Stripe Customer if missing — covers tenants provisioned
        // before STRIPE_SECRET_KEY was configured server-side (initializeForTenant
        // is wrapped in try/catch at signup so it silently no-ops in that case).
        if (tenant.getStripeCustomerId() == null || tenant.getStripeCustomerId().isBlank()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Cannot resolve current user");
            }
            User owner = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "User not found"));
            try {
                subscriptionService.initializeForTenant(owner, tenant);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to initialize Stripe customer: " + e.getMessage(), e);
            }
        }

        var setupIntent = subscriptionService.createSetupIntent(tenant.getId());
        return new SetupIntentResponse(setupIntent.getClientSecret());
    }

    @PostMapping("/api/pro/subscription/create")
    public SubscriptionResponse createSubscription(@Valid @RequestBody CreateSubscriptionRequest body)
            throws Exception {
        // Resolve active tenant
        String tenantSlug = TenantContext.getCurrentTenant();
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenant context");
        }

        Tenant tenant = tenantRepository.findBySlug(tenantSlug)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Tenant not found: " + tenantSlug));

        // Reject if tier=VITRINE
        if (body.tier() == SubscriptionTier.VITRINE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "VITRINE tier does not require a Stripe subscription");
        }

        // Reject if pricingCatalog.priceIdFor(tier, billing) is empty
        if (pricingCatalog.priceIdFor(body.tier(), body.billing()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid subscription tier/billing combination: " + body.tier() + "/" + body.billing());
        }

        // Call subscriptionService.startCheckout
        Tenant updated = subscriptionService.startCheckout(
            tenant.getId(), body.tier(), body.billing(), body.paymentMethodId());

        return buildSubscriptionResponse(updated);
    }

    @GetMapping("/api/pro/subscription")
    public SubscriptionResponse getCurrentSubscription() {
        // Resolve active tenant
        String tenantSlug = TenantContext.getCurrentTenant();
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenant context");
        }

        Tenant tenant = tenantRepository.findBySlug(tenantSlug)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Tenant not found: " + tenantSlug));

        return buildSubscriptionResponse(tenant);
    }

    @PostMapping("/api/pro/subscription/portal-session")
    public PortalSessionResponse createPortalSession() throws Exception {
        // Resolve active tenant
        String tenantSlug = TenantContext.getCurrentTenant();
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenant context");
        }

        Tenant tenant = tenantRepository.findBySlug(tenantSlug)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Tenant not found: " + tenantSlug));

        if (tenant.getStripeCustomerId() == null || tenant.getStripeCustomerId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Stripe customer not yet initialized for this tenant");
        }

        var session = subscriptionService.createPortalSession(tenant.getId());
        return new PortalSessionResponse(session.getUrl());
    }

    private SubscriptionResponse buildSubscriptionResponse(Tenant tenant) {
        return new SubscriptionResponse(
            tenant.getSubscriptionTier() != null ? tenant.getSubscriptionTier() : SubscriptionTier.VITRINE,
            tenant.getSubscriptionBilling() != null ? tenant.getSubscriptionBilling() : SubscriptionBilling.FREE,
            tenant.getSubscriptionStatus() != null ? tenant.getSubscriptionStatus() :
                com.luxpretty.app.subscription.domain.SubscriptionStatus.VITRINE_FREE,
            tenant.getStripeCustomerId(),
            tenant.getStripeSubscriptionId(),
            tenant.getCurrentPeriodEnd(),
            tenant.getTrialEnd()
        );
    }
}
