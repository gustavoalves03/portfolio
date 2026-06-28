package com.luxpretty.app.subscription.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.subscription.app.PricingCatalog;
import com.luxpretty.app.subscription.app.SubscriptionService;
import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionStatus;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import com.luxpretty.app.subscription.web.dto.CreateSubscriptionRequest;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.stripe.model.SetupIntent;
import com.stripe.model.billingportal.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class)
@AutoConfigureMockMvc(addFilters = false)
class SubscriptionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private PricingCatalog pricingCatalog;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private com.luxpretty.app.tenant.app.TenantService tenantService;

    @MockBean
    private com.luxpretty.app.users.repo.UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // Clear tenant context before each test
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        // Clean up tenant context after each test
        TenantContext.clear();
    }

    // Test 1: getPricing_returnsAllTiers_withCorrectPrices
    @Test
    void getPricing_returnsAllTiers_withCorrectPrices() throws Exception {
        mockMvc.perform(get("/api/pricing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                // VITRINE/MONTHLY
                .andExpect(jsonPath("$[0].tier").value("VITRINE"))
                .andExpect(jsonPath("$[0].billing").value("MONTHLY"))
                .andExpect(jsonPath("$[0].monthlyPriceEuros", closeTo(9.99, 0.01)))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                // VITRINE/YEARLY
                .andExpect(jsonPath("$[1].tier").value("VITRINE"))
                .andExpect(jsonPath("$[1].billing").value("YEARLY"))
                .andExpect(jsonPath("$[1].monthlyPriceEuros", closeTo(7.99, 0.01)))
                .andExpect(jsonPath("$[1].currency").value("EUR"))
                // GESTION/MONTHLY
                .andExpect(jsonPath("$[2].tier").value("GESTION"))
                .andExpect(jsonPath("$[2].billing").value("MONTHLY"))
                .andExpect(jsonPath("$[2].monthlyPriceEuros", closeTo(49.99, 0.01)))
                .andExpect(jsonPath("$[2].currency").value("EUR"))
                // GESTION/YEARLY
                .andExpect(jsonPath("$[3].tier").value("GESTION"))
                .andExpect(jsonPath("$[3].billing").value("YEARLY"))
                .andExpect(jsonPath("$[3].monthlyPriceEuros", closeTo(42.49, 0.01)))
                .andExpect(jsonPath("$[3].currency").value("EUR"))
                // PREMIUM/MONTHLY
                .andExpect(jsonPath("$[4].tier").value("PREMIUM"))
                .andExpect(jsonPath("$[4].billing").value("MONTHLY"))
                .andExpect(jsonPath("$[4].monthlyPriceEuros", closeTo(67.99, 0.01)))
                .andExpect(jsonPath("$[4].currency").value("EUR"))
                // PREMIUM/YEARLY
                .andExpect(jsonPath("$[5].tier").value("PREMIUM"))
                .andExpect(jsonPath("$[5].billing").value("YEARLY"))
                .andExpect(jsonPath("$[5].monthlyPriceEuros", closeTo(57.79, 0.01)))
                .andExpect(jsonPath("$[5].currency").value("EUR"));
    }

    // Test 2: createSetupIntent_returnsClientSecret
    @Test
    void createSetupIntent_returnsClientSecret() throws Exception {
        // Set up tenant context
        TenantContext.setCurrentTenant("my-salon");

        // Create a mock tenant with stripeCustomerId
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSlug("my-salon");
        tenant.setStripeCustomerId("cus_abc123");

        when(tenantRepository.findBySlug("my-salon")).thenReturn(Optional.of(tenant));

        // Create a mock SetupIntent
        SetupIntent setupIntent = new SetupIntent();
        setupIntent.setClientSecret("seti_secret_xyz");

        when(subscriptionService.createSetupIntent(1L)).thenReturn(setupIntent);

        mockMvc.perform(post("/api/pro/subscription/setup-intent")
                .with(request -> {
                    request.setUserPrincipal(() -> "pro@example.com");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("seti_secret_xyz"));

        verify(subscriptionService, times(1)).createSetupIntent(1L);
    }

    // Test 3: createSetupIntent_requiresAuth
    @Test
    void createSetupIntent_requiresAuth() throws Exception {
        // Don't set tenant context, simulate unauthenticated request
        mockMvc.perform(post("/api/pro/subscription/setup-intent"))
                .andExpect(status().isForbidden());
    }

    // Test 4: createSubscription_callsService_andReturnsSummary
    @Test
    void createSubscription_callsService_andReturnsSummary() throws Exception {
        TenantContext.setCurrentTenant("my-salon");

        // Tenant has no live subscription yet (first checkout) → not blocked by
        // the double-subscription guard.
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSlug("my-salon");
        tenant.setStripeCustomerId("cus_abc123");
        tenant.setSubscriptionStatus(SubscriptionStatus.VITRINE_FREE);

        // What the service returns after the checkout completes.
        Tenant subscribed = new Tenant();
        subscribed.setId(1L);
        subscribed.setSlug("my-salon");
        subscribed.setStripeCustomerId("cus_abc123");
        subscribed.setSubscriptionTier(SubscriptionTier.GESTION);
        subscribed.setSubscriptionBilling(SubscriptionBilling.MONTHLY);
        subscribed.setSubscriptionStatus(SubscriptionStatus.TRIALING);
        subscribed.setCurrentPeriodEnd(LocalDateTime.now().plusDays(30));

        when(tenantRepository.findBySlug("my-salon")).thenReturn(Optional.of(tenant));
        when(pricingCatalog.priceIdFor(SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY))
                .thenReturn(Optional.of("price_gestion_monthly"));
        when(subscriptionService.startCheckout(1L, SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY, "pm_xyz"))
                .thenReturn(subscribed);

        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                SubscriptionTier.GESTION,
                SubscriptionBilling.MONTHLY,
                "pm_xyz"
        );

        mockMvc.perform(post("/api/pro/subscription/create")
                .with(request_ -> {
                    request_.setUserPrincipal(() -> "pro@example.com");
                    return request_;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("GESTION"))
                .andExpect(jsonPath("$.billing").value("MONTHLY"))
                .andExpect(jsonPath("$.status").value("TRIALING"))
                .andExpect(jsonPath("$.stripeCustomerId").value("cus_abc123"));

        verify(subscriptionService, times(1)).startCheckout(
                1L, SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY, "pm_xyz");
    }

    @Test
    void createSubscription_rejectsWhenLiveSubscriptionExists() throws Exception {
        TenantContext.setCurrentTenant("my-salon");

        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSlug("my-salon");
        tenant.setStripeCustomerId("cus_abc123");
        tenant.setStripeSubscriptionId("sub_live");
        tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);

        when(tenantRepository.findBySlug("my-salon")).thenReturn(Optional.of(tenant));
        when(pricingCatalog.priceIdFor(SubscriptionTier.PREMIUM, SubscriptionBilling.MONTHLY))
                .thenReturn(Optional.of("price_premium_monthly"));

        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                SubscriptionTier.PREMIUM, SubscriptionBilling.MONTHLY, "pm_xyz");

        mockMvc.perform(post("/api/pro/subscription/create")
                .with(request_ -> { request_.setUserPrincipal(() -> "pro@example.com"); return request_; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        verify(subscriptionService, never()).startCheckout(any(), any(), any(), any());
    }

    // Test 5: createSubscription_rejectsVitrineTier
    @Test
    void createSubscription_rejectsVitrineTier() throws Exception {
        TenantContext.setCurrentTenant("my-salon");

        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSlug("my-salon");

        when(tenantRepository.findBySlug("my-salon")).thenReturn(Optional.of(tenant));

        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                SubscriptionTier.VITRINE,
                SubscriptionBilling.FREE,
                "pm_xyz"
        );

        mockMvc.perform(post("/api/pro/subscription/create")
                .with(request_ -> {
                    request_.setUserPrincipal(() -> "pro@example.com");
                    return request_;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(subscriptionService, never()).startCheckout(anyLong(), any(), any(), anyString());
    }

    // Test 6: createSubscription_rejectsInvalidPriceId
    @Test
    void createSubscription_rejectsInvalidPriceId() throws Exception {
        TenantContext.setCurrentTenant("my-salon");

        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSlug("my-salon");

        when(tenantRepository.findBySlug("my-salon")).thenReturn(Optional.of(tenant));
        // Return empty for invalid combo
        when(pricingCatalog.priceIdFor(SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY))
                .thenReturn(Optional.empty());

        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                SubscriptionTier.GESTION,
                SubscriptionBilling.MONTHLY,
                "pm_xyz"
        );

        mockMvc.perform(post("/api/pro/subscription/create")
                .with(request_ -> {
                    request_.setUserPrincipal(() -> "pro@example.com");
                    return request_;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(subscriptionService, never()).startCheckout(anyLong(), any(), any(), anyString());
    }

    // Test 7: getCurrentSubscription_returnsStatusForTenant
    @Test
    void getCurrentSubscription_returnsStatusForTenant() throws Exception {
        TenantContext.setCurrentTenant("my-salon");

        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSlug("my-salon");
        tenant.setStripeCustomerId("cus_abc123");
        tenant.setStripeSubscriptionId("sub_xyz");
        tenant.setSubscriptionTier(SubscriptionTier.PREMIUM);
        tenant.setSubscriptionBilling(SubscriptionBilling.YEARLY);
        tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        LocalDateTime periodEnd = LocalDateTime.now().plusDays(365);
        tenant.setCurrentPeriodEnd(periodEnd);

        when(tenantRepository.findBySlug("my-salon")).thenReturn(Optional.of(tenant));

        mockMvc.perform(get("/api/pro/subscription")
                .with(request -> {
                    request.setUserPrincipal(() -> "pro@example.com");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.billing").value("YEARLY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.stripeCustomerId").value("cus_abc123"))
                .andExpect(jsonPath("$.stripeSubscriptionId").value("sub_xyz"));
    }

    // Test 8: getCurrentSubscription_returnsVitrineFreeForFreeTenants
    @Test
    void getCurrentSubscription_returnsVitrineFreeForFreeTenants() throws Exception {
        TenantContext.setCurrentTenant("my-salon");

        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSlug("my-salon");
        tenant.setStripeCustomerId(null);
        tenant.setStripeSubscriptionId(null);
        tenant.setSubscriptionTier(SubscriptionTier.VITRINE);
        tenant.setSubscriptionBilling(SubscriptionBilling.FREE);
        tenant.setSubscriptionStatus(SubscriptionStatus.VITRINE_FREE);

        when(tenantRepository.findBySlug("my-salon")).thenReturn(Optional.of(tenant));

        mockMvc.perform(get("/api/pro/subscription")
                .with(request -> {
                    request.setUserPrincipal(() -> "pro@example.com");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("VITRINE"))
                .andExpect(jsonPath("$.billing").value("FREE"))
                .andExpect(jsonPath("$.status").value("VITRINE_FREE"))
                .andExpect(jsonPath("$.stripeCustomerId").doesNotExist());
    }

    // Test 9: createPortalSession_returnsUrl
    @Test
    void createPortalSession_returnsUrl() throws Exception {
        TenantContext.setCurrentTenant("my-salon");

        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSlug("my-salon");
        tenant.setStripeCustomerId("cus_abc123");

        when(tenantRepository.findBySlug("my-salon")).thenReturn(Optional.of(tenant));

        Session session = new Session();
        session.setUrl("https://billing.stripe.com/p/session_xyz");

        when(subscriptionService.createPortalSession(1L)).thenReturn(session);

        mockMvc.perform(post("/api/pro/subscription/portal-session")
                .with(request -> {
                    request.setUserPrincipal(() -> "pro@example.com");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://billing.stripe.com/p/session_xyz"));

        verify(subscriptionService, times(1)).createPortalSession(1L);
    }

    // Test 10: createPortalSession_rejects_whenNoStripeCustomerYet
    @Test
    void createPortalSession_rejects_whenNoStripeCustomerYet() throws Exception {
        TenantContext.setCurrentTenant("my-salon");

        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSlug("my-salon");
        tenant.setStripeCustomerId(null);

        when(tenantRepository.findBySlug("my-salon")).thenReturn(Optional.of(tenant));

        mockMvc.perform(post("/api/pro/subscription/portal-session")
                .with(request -> {
                    request.setUserPrincipal(() -> "pro@example.com");
                    return request;
                }))
                .andExpect(status().isBadRequest());

        verify(subscriptionService, never()).createPortalSession(anyLong());
    }

    // Test 11: getStripeConfig_returnsPublishableKey_publicly
    @Test
    void getStripeConfig_returnsPublishableKey_publicly() throws Exception {
        // No auth, no tenant context — this endpoint is permitAll
        mockMvc.perform(get("/api/stripe/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishableKey").exists());
    }
}
