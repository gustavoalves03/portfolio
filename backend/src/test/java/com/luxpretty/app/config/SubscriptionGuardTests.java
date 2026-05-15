package com.luxpretty.app.config;

import com.luxpretty.app.auth.UserPrincipal;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void withAuth(UserPrincipal principal) {
        var auth = new TestingAuthenticationToken(principal, null, "ROLE_PRO");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("allows /api/auth/* endpoints regardless of subscription status")
    void guard_allowsAuthEndpoints_regardless_of_status() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        guard.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        verify(tenantRepository, never()).findByOwnerId(any());
    }

    @Test
    @DisplayName("allows /api/webhooks/* endpoints")
    void guard_allowsWebhooksEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhooks/stripe");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        guard.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        verify(tenantRepository, never()).findByOwnerId(any());
    }

    @Test
    @DisplayName("allows /api/pro/subscription/* endpoints even when subscription status is UNPAID")
    void guard_allowsSubscriptionEndpoints_even_when_unpaid() throws Exception {
        UserPrincipal principal = new UserPrincipal(1L, "test@test.com", "Test User", null);
        withAuth(principal);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/pro/subscription/create");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        guard.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        // Subscription endpoints are exempt, so no tenant lookup should happen
        verify(tenantRepository, never()).findByOwnerId(any());
    }

    @Test
    @DisplayName("allows /api/pro/* requests when subscription status is TRIALING")
    void guard_allowsProRequest_whenStatusIsTrialing() throws Exception {
        UserPrincipal principal = new UserPrincipal(2L, "pro@test.com", "Pro User", null);
        withAuth(principal);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pro/care");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        Tenant tenant = Tenant.builder()
                .id(2L)
                .ownerId(2L)
                .subscriptionStatus(SubscriptionStatus.TRIALING)
                .build();
        when(tenantRepository.findByOwnerId(2L)).thenReturn(Optional.of(tenant));

        guard.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200); // default response status when chain is called
    }

    @Test
    @DisplayName("allows /api/pro/* requests when subscription status is ACTIVE")
    void guard_allowsProRequest_whenStatusIsActive() throws Exception {
        UserPrincipal principal = new UserPrincipal(3L, "active@test.com", "Active User", null);
        withAuth(principal);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pro/care");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        Tenant tenant = Tenant.builder()
                .id(3L)
                .ownerId(3L)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .build();
        when(tenantRepository.findByOwnerId(3L)).thenReturn(Optional.of(tenant));

        guard.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200); // default response status when chain is called
    }

    @Test
    @DisplayName("returns 402 Payment Required when subscription status is UNPAID and path is gated")
    void guard_returns402_whenStatusIsUnpaid() throws Exception {
        UserPrincipal principal = new UserPrincipal(4L, "unpaid@test.com", "Unpaid User", null);
        withAuth(principal);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pro/care");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        Tenant tenant = Tenant.builder()
                .id(4L)
                .ownerId(4L)
                .subscriptionStatus(SubscriptionStatus.UNPAID)
                .build();
        when(tenantRepository.findByOwnerId(4L)).thenReturn(Optional.of(tenant));

        guard.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(402);
        assertThat(response.getContentAsString())
                .contains("SUBSCRIPTION_REQUIRED")
                .contains("/pro/onboarding/payment");
    }
}
