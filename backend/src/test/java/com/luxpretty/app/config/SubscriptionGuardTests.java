package com.luxpretty.app.config;

import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.subscription.domain.SubscriptionStatus;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionGuard filter tests")
class SubscriptionGuardTests {

    @Mock
    private TenantRepository tenantRepository;

    private SubscriptionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SubscriptionGuard(tenantRepository);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("allows /api/auth/* endpoints regardless of subscription status")
    void guard_allowsAuthEndpoints_regardless_of_status() throws Exception {
        TenantContext.setCurrentTenant("salon-rose"); // even with a tenant context, exempt path skips lookup
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        guard.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        verify(tenantRepository, never()).findBySlug(anyString());
    }

    @Test
    @DisplayName("allows /api/webhooks/* endpoints")
    void guard_allowsWebhooksEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhooks/stripe");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        guard.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        verify(tenantRepository, never()).findBySlug(anyString());
    }

    @Test
    @DisplayName("allows /api/pro/subscription/* endpoints even when subscription status is UNPAID")
    void guard_allowsSubscriptionEndpoints_even_when_unpaid() throws Exception {
        TenantContext.setCurrentTenant("salon-rose");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/pro/subscription/create");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        guard.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        // Subscription endpoints are exempt, so no tenant lookup should happen.
        verify(tenantRepository, never()).findBySlug(anyString());
    }

    @Test
    @DisplayName("allows /api/pro/* requests when subscription status is TRIALING")
    void guard_allowsProRequest_whenStatusIsTrialing() throws Exception {
        TenantContext.setCurrentTenant("salon-trial");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pro/care");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        Tenant tenant = Tenant.builder()
                .id(2L)
                .slug("salon-trial")
                .subscriptionStatus(SubscriptionStatus.TRIALING)
                .build();
        when(tenantRepository.findBySlug("salon-trial")).thenReturn(Optional.of(tenant));

        guard.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("allows /api/pro/* requests when subscription status is ACTIVE")
    void guard_allowsProRequest_whenStatusIsActive() throws Exception {
        TenantContext.setCurrentTenant("salon-active");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pro/care");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        Tenant tenant = Tenant.builder()
                .id(3L)
                .slug("salon-active")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .build();
        when(tenantRepository.findBySlug("salon-active")).thenReturn(Optional.of(tenant));

        guard.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("returns 402 Payment Required when subscription status is UNPAID and path is gated")
    void guard_returns402_whenStatusIsUnpaid() throws Exception {
        TenantContext.setCurrentTenant("salon-unpaid");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pro/care");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        Tenant tenant = Tenant.builder()
                .id(4L)
                .slug("salon-unpaid")
                .subscriptionStatus(SubscriptionStatus.UNPAID)
                .build();
        when(tenantRepository.findBySlug("salon-unpaid")).thenReturn(Optional.of(tenant));

        guard.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(402);
        assertThat(response.getContentAsString())
                .contains("SUBSCRIPTION_REQUIRED")
                .contains("/pro/onboarding/payment");
    }
}
