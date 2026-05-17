package com.luxpretty.app.tenant.web;

import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionStatus;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import com.luxpretty.app.tenant.app.TenantReadinessService;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.domain.TenantStatus;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.tenant.web.dto.PublishErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantControllerPublishTests {

    @Mock private TenantService tenantService;
    @Mock private TenantReadinessService readinessService;
    @Mock private TenantRepository tenantRepository;

    @InjectMocks private TenantController controller;

    private static final long OWNER_ID = 1L;

    private UserPrincipal principal() {
        return new UserPrincipal(OWNER_ID, "pro@example.com", "Pro", null);
    }

    /** Builds a fully-publishable tenant (all readiness conditions satisfied). */
    private Tenant publishableTenant(SubscriptionStatus status) {
        return Tenant.builder()
                .id(10L)
                .slug("mon-salon")
                .ownerId(OWNER_ID)
                .name("Mon Salon")
                .status(TenantStatus.DRAFT)
                .subscriptionStatus(status)
                .subscriptionTier(SubscriptionTier.GESTION)
                .subscriptionBilling(SubscriptionBilling.MONTHLY)
                .build();
    }

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------------------
    // Subscription gate — 402
    // -------------------------------------------------------------------------

    @Test
    void publish_returns402_whenSubscriptionIsVitrineFree() {
        Tenant tenant = publishableTenant(SubscriptionStatus.VITRINE_FREE);
        when(tenantRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant));
        when(readinessService.getMissingConditions(tenant)).thenReturn(List.of());

        ResponseEntity<?> response = controller.publish(principal());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        @SuppressWarnings("unchecked")
        var body = (java.util.Map<String, Object>) response.getBody();
        assertThat(body).containsKey("message");
        assertThat(body).containsKey("tier");
        assertThat(body).containsKey("billing");
        // Tenant must NOT have been activated
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void publish_returns402_whenSubscriptionIsCanceled() {
        Tenant tenant = publishableTenant(SubscriptionStatus.CANCELED);
        when(tenantRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant));
        when(readinessService.getMissingConditions(tenant)).thenReturn(List.of());

        ResponseEntity<?> response = controller.publish(principal());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        verify(tenantRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Subscription gate — allowed statuses
    // -------------------------------------------------------------------------

    @Test
    void publish_returns200_whenSubscriptionIsActive() {
        Tenant tenant = publishableTenant(SubscriptionStatus.ACTIVE);
        when(tenantRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant));
        when(readinessService.getMissingConditions(tenant)).thenReturn(List.of());

        ResponseEntity<?> response = controller.publish(principal());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        verify(tenantRepository).save(tenant);
    }

    @Test
    void publish_returns200_whenSubscriptionIsTrialing() {
        Tenant tenant = publishableTenant(SubscriptionStatus.TRIALING);
        when(tenantRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant));
        when(readinessService.getMissingConditions(tenant)).thenReturn(List.of());

        ResponseEntity<?> response = controller.publish(principal());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        verify(tenantRepository).save(tenant);
    }

    // -------------------------------------------------------------------------
    // Readiness check still fires first (subscription gate is second)
    // -------------------------------------------------------------------------

    @Test
    void publish_returns422_whenReadinessIncomplete_evenIfSubscriptionActive() {
        Tenant tenant = publishableTenant(SubscriptionStatus.ACTIVE);
        when(tenantRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant));
        when(readinessService.getMissingConditions(tenant)).thenReturn(List.of("hasLogo", "hasActiveCare"));

        ResponseEntity<?> response = controller.publish(principal());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isInstanceOf(PublishErrorResponse.class);
        verify(tenantRepository, never()).save(any());
    }
}
