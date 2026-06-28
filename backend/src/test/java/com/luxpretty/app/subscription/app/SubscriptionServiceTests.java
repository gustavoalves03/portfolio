package com.luxpretty.app.subscription.app;

import com.luxpretty.app.feature.app.FeatureFlagService;
import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionStatus;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.User;
import com.stripe.model.Customer;
import com.stripe.model.Price;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTests {

    @Mock
    private StripeService stripeService;

    @Mock
    private PricingCatalog pricingCatalog;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private FeatureFlagService featureFlagService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    void initializeForTenant_createsCustomer_andPersistsId() throws Exception {
        // Given
        User owner = User.builder().id(1L).name("John Doe").email("john@example.com").build();
        Tenant tenant = Tenant.builder().id(1L).slug("test-salon").build();

        Customer customer = new Customer();
        customer.setId("cus_123");

        when(stripeService.createCustomer(owner, tenant)).thenReturn(customer);

        // When
        subscriptionService.initializeForTenant(owner, tenant);

        // Then
        verify(stripeService).createCustomer(owner, tenant);
        assertThat(tenant.getStripeCustomerId()).isEqualTo("cus_123");

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().getStripeCustomerId()).isEqualTo("cus_123");
    }

    @Test
    void initializeForTenant_isIdempotent_skipIfCustomerExists() throws Exception {
        // Given
        User owner = User.builder().id(1L).name("John Doe").email("john@example.com").build();
        Tenant tenant = Tenant.builder().id(1L).slug("test-salon").stripeCustomerId("cus_existing").build();

        // When
        subscriptionService.initializeForTenant(owner, tenant);

        // Then
        verify(stripeService, never()).createCustomer(any(), any());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void startCheckout_createsSubscription_withCorrectPrice() throws Exception {
        // Given
        Tenant tenant = Tenant.builder()
            .id(1L)
            .slug("test-salon")
            .stripeCustomerId("cus_123")
            .build();

        Subscription stripeSubscription = new Subscription();
        stripeSubscription.setId("sub_456");
        stripeSubscription.setStatus("active");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(pricingCatalog.priceIdFor(SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY))
            .thenReturn(Optional.of("price_gestion_monthly"));
        when(stripeService.createSubscription("cus_123", "price_gestion_monthly", "pm_789"))
            .thenReturn(stripeSubscription);

        // When
        Tenant result = subscriptionService.startCheckout(
            1L, SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY, "pm_789");

        // Then
        assertThat(result.getStripeSubscriptionId()).isEqualTo("sub_456");
        assertThat(result.getSubscriptionTier()).isEqualTo(SubscriptionTier.GESTION);
        assertThat(result.getSubscriptionBilling()).isEqualTo(SubscriptionBilling.MONTHLY);
        assertThat(result.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        verify(pricingCatalog).priceIdFor(SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY);
        verify(stripeService).createSubscription("cus_123", "price_gestion_monthly", "pm_789");
        verify(tenantRepository).save(tenant);
    }

    @Test
    void startCheckout_setsTrialingStatus_whenStripeReturnsTrialing() throws Exception {
        Tenant tenant = Tenant.builder()
            .id(1L)
            .slug("test-salon")
            .stripeCustomerId("cus_123")
            .build();

        Subscription stripeSubscription = new Subscription();
        stripeSubscription.setId("sub_trial");
        stripeSubscription.setStatus("trialing");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(pricingCatalog.priceIdFor(SubscriptionTier.PREMIUM, SubscriptionBilling.YEARLY))
            .thenReturn(Optional.of("price_premium_yearly"));
        when(stripeService.createSubscription("cus_123", "price_premium_yearly", "pm_789"))
            .thenReturn(stripeSubscription);

        Tenant result = subscriptionService.startCheckout(
            1L, SubscriptionTier.PREMIUM, SubscriptionBilling.YEARLY, "pm_789");

        assertThat(result.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.TRIALING);
    }

    @Test
    void startCheckout_rejectsWhenLiveSubscriptionExists() throws Exception {
        // A tenant already actively subscribed must not start a second checkout
        // (would create a duplicate Stripe subscription / double billing).
        Tenant tenant = Tenant.builder()
            .id(1L)
            .slug("test-salon")
            .stripeCustomerId("cus_123")
            .stripeSubscriptionId("sub_live")
            .subscriptionStatus(SubscriptionStatus.ACTIVE)
            .build();

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() ->
            subscriptionService.startCheckout(1L, SubscriptionTier.PREMIUM, SubscriptionBilling.MONTHLY, "pm_789")
        ).isInstanceOf(IllegalStateException.class);

        verify(stripeService, never()).createSubscription(any(), any(), any());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void startCheckout_allowsResubscribe_whenStatusIsCanceled() throws Exception {
        // A lapsed tenant (CANCELED) must be able to subscribe again.
        Tenant tenant = Tenant.builder()
            .id(1L)
            .slug("test-salon")
            .stripeCustomerId("cus_123")
            .stripeSubscriptionId("sub_old_dead")
            .subscriptionStatus(SubscriptionStatus.CANCELED)
            .build();

        Subscription stripeSubscription = new Subscription();
        stripeSubscription.setId("sub_new");
        stripeSubscription.setStatus("active");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(pricingCatalog.priceIdFor(SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY))
            .thenReturn(Optional.of("price_gestion_monthly"));
        when(stripeService.createSubscription("cus_123", "price_gestion_monthly", "pm_789"))
            .thenReturn(stripeSubscription);

        Tenant result = subscriptionService.startCheckout(
            1L, SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY, "pm_789");

        assertThat(result.getStripeSubscriptionId()).isEqualTo("sub_new");
        verify(stripeService).createSubscription("cus_123", "price_gestion_monthly", "pm_789");
    }

    @Test
    void startCheckout_failsIfTierIsVitrine() throws Exception {
        // When & Then
        assertThatThrownBy(() ->
            subscriptionService.startCheckout(1L, SubscriptionTier.VITRINE, SubscriptionBilling.FREE, "pm_789")
        ).isInstanceOf(IllegalArgumentException.class);

        verify(stripeService, never()).createSubscription(any(), any(), any());
    }

    @Test
    void applySubscriptionUpdate_setsStatusActive_whenStripeStatusIsActive() throws Exception {
        // Given
        Tenant tenant = Tenant.builder()
            .id(1L)
            .slug("test-salon")
            .stripeCustomerId("cus_123")
            .stripeSubscriptionId("sub_456")
            .build();

        Subscription stripeSub = new Subscription();
        stripeSub.setId("sub_456");
        stripeSub.setCustomer("cus_123");
        stripeSub.setStatus("active");

        when(tenantRepository.findByStripeSubscriptionId("sub_456")).thenReturn(Optional.of(tenant));

        // When
        subscriptionService.applySubscriptionUpdate(stripeSub);

        // Then
        assertThat(tenant.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(tenantRepository).save(tenant);
    }

    @Test
    void applySubscriptionUpdate_setsCurrentPeriodEnd() throws Exception {
        // Given
        Tenant tenant = Tenant.builder()
            .id(1L)
            .slug("test-salon")
            .stripeSubscriptionId("sub_456")
            .build();

        long epochSeconds = 1700000000L;
        LocalDateTime expectedDateTime = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(epochSeconds),
            ZoneOffset.UTC);

        Subscription stripeSub = new Subscription();
        stripeSub.setId("sub_456");
        stripeSub.setStatus("active");
        stripeSub.setCurrentPeriodEnd(epochSeconds);

        when(tenantRepository.findByStripeSubscriptionId("sub_456")).thenReturn(Optional.of(tenant));

        // When
        subscriptionService.applySubscriptionUpdate(stripeSub);

        // Then
        assertThat(tenant.getCurrentPeriodEnd()).isEqualTo(expectedDateTime);
        verify(tenantRepository).save(tenant);
    }

    @Test
    void applySubscriptionUpdate_setsTier_basedOnPriceId() throws Exception {
        // Given
        Tenant tenant = Tenant.builder()
            .id(1L)
            .slug("test-salon")
            .stripeSubscriptionId("sub_456")
            .build();

        Subscription stripeSub = new Subscription();
        stripeSub.setId("sub_456");
        stripeSub.setStatus("trialing");
        stripeSub.setCurrentPeriodEnd(1700000000L);

        // Build subscription items with price
        SubscriptionItem item = new SubscriptionItem();
        Price price = new Price();
        price.setId("price_premium_monthly");
        item.setPrice(price);

        SubscriptionItemCollection items = new SubscriptionItemCollection();
        List<SubscriptionItem> itemList = new ArrayList<>();
        itemList.add(item);
        items.setData(itemList);
        stripeSub.setItems(items);

        when(tenantRepository.findByStripeSubscriptionId("sub_456")).thenReturn(Optional.of(tenant));
        when(pricingCatalog.tierBillingFor("price_premium_monthly")).thenReturn(Optional.of(
            new PricingCatalog.TierBilling(SubscriptionTier.PREMIUM, SubscriptionBilling.MONTHLY)));

        // When
        subscriptionService.applySubscriptionUpdate(stripeSub);

        // Then
        assertThat(tenant.getSubscriptionTier()).isEqualTo(SubscriptionTier.PREMIUM);
        assertThat(tenant.getSubscriptionBilling()).isEqualTo(SubscriptionBilling.MONTHLY);
        verify(tenantRepository).save(tenant);
    }

    @Test
    void createSetupIntent_returnsSetupIntent_withClientSecret() throws Exception {
        // Given
        Tenant tenant = Tenant.builder()
            .id(1L)
            .slug("test-salon")
            .stripeCustomerId("cus_123")
            .build();

        SetupIntent setupIntent = new SetupIntent();
        setupIntent.setClientSecret("seti_secret_abc123");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(stripeService.createSetupIntent("cus_123")).thenReturn(setupIntent);

        // When
        SetupIntent result = subscriptionService.createSetupIntent(1L);

        // Then
        assertThat(result.getClientSecret()).isEqualTo("seti_secret_abc123");
        verify(stripeService).createSetupIntent("cus_123");
    }

    @Test
    void createSetupIntent_failsIfCustomerIdIsNull() throws Exception {
        // Given
        Tenant tenant = Tenant.builder()
            .id(1L)
            .slug("test-salon")
            .stripeCustomerId(null)
            .build();

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        // When & Then
        assertThatThrownBy(() ->
            subscriptionService.createSetupIntent(1L)
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Stripe customer not yet initialized");

        verify(stripeService, never()).createSetupIntent(any());
    }

    @Test
    void createPortalSession_returnsSession() throws Exception {
        // Given
        Tenant tenant = Tenant.builder()
            .id(1L)
            .slug("test-salon")
            .stripeCustomerId("cus_123")
            .build();

        com.stripe.model.billingportal.Session session = new com.stripe.model.billingportal.Session();
        session.setUrl("https://billing.stripe.com/p/session/test");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(stripeService.createPortalSession("cus_123")).thenReturn(session);

        // When
        com.stripe.model.billingportal.Session result = subscriptionService.createPortalSession(1L);

        // Then
        assertThat(result.getUrl()).isEqualTo("https://billing.stripe.com/p/session/test");
        verify(stripeService).createPortalSession("cus_123");
    }

    @Test
    void startCheckout_appliesTierDefaultsForNewTier() throws Exception {
        // Given
        Tenant tenant = Tenant.builder()
            .id(1L)
            .slug("test-salon")
            .stripeCustomerId("cus_123")
            .build();

        Subscription stripeSubscription = new Subscription();
        stripeSubscription.setId("sub_premium");
        stripeSubscription.setStatus("active");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(pricingCatalog.priceIdFor(SubscriptionTier.PREMIUM, SubscriptionBilling.MONTHLY))
            .thenReturn(Optional.of("price_premium_monthly"));
        when(stripeService.createSubscription("cus_123", "price_premium_monthly", "pm_x"))
            .thenReturn(stripeSubscription);

        // When
        subscriptionService.startCheckout(1L, SubscriptionTier.PREMIUM, SubscriptionBilling.MONTHLY, "pm_x");

        // Then
        verify(featureFlagService).applyTierDefaults(SubscriptionTier.PREMIUM);
    }

    @Test
    void applySubscriptionUpdate_appliesTierDefaultsWhenTierChanges() throws Exception {
        // Given
        Tenant tenant = Tenant.builder()
            .id(1L)
            .slug("test-salon")
            .stripeSubscriptionId("sub_456")
            .build();

        Subscription stripeSub = new Subscription();
        stripeSub.setId("sub_456");
        stripeSub.setStatus("active");

        SubscriptionItem item = new SubscriptionItem();
        Price price = new Price();
        price.setId("price_gestion_monthly");
        item.setPrice(price);

        SubscriptionItemCollection items = new SubscriptionItemCollection();
        List<SubscriptionItem> itemList = new ArrayList<>();
        itemList.add(item);
        items.setData(itemList);
        stripeSub.setItems(items);

        when(tenantRepository.findByStripeSubscriptionId("sub_456")).thenReturn(Optional.of(tenant));
        when(pricingCatalog.tierBillingFor("price_gestion_monthly")).thenReturn(Optional.of(
            new PricingCatalog.TierBilling(SubscriptionTier.GESTION, SubscriptionBilling.MONTHLY)));

        // When
        subscriptionService.applySubscriptionUpdate(stripeSub);

        // Then
        verify(featureFlagService).applyTierDefaults(SubscriptionTier.GESTION);
    }
}
