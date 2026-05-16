package com.luxpretty.app.subscription.app;

import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.users.domain.User;
import com.stripe.model.Customer;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.model.billingportal.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.billingportal.SessionCreateParams;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class StripeServiceTests {

    private StripeService newService() {
        StripeService svc = new StripeService();
        ReflectionTestUtils.setField(svc, "secretKey", "sk_test_dummy");
        ReflectionTestUtils.setField(svc, "portalReturnUrl", "https://test.luxpretty.lu/pro/settings");
        return svc;
    }

    @Test
    void createCustomer_callsStripeWithCorrectParams() throws Exception {
        StripeService svc = newService();
        User owner = new User();
        owner.setEmail("pro@example.com");
        owner.setName("Pro User");
        Tenant tenant = new Tenant();
        tenant.setId(42L);
        tenant.setSlug("salon-rose");

        try (MockedStatic<Customer> mocked = mockStatic(Customer.class)) {
            Customer fake = new Customer();
            fake.setId("cus_test123");
            mocked.when(() -> Customer.create(any(CustomerCreateParams.class))).thenReturn(fake);

            Customer result = svc.createCustomer(owner, tenant);

            ArgumentCaptor<CustomerCreateParams> captor = ArgumentCaptor.forClass(CustomerCreateParams.class);
            mocked.verify(() -> Customer.create(captor.capture()));
            Map<String, Object> map = captor.getValue().toMap();
            assertThat(map).containsEntry("email", "pro@example.com");
            assertThat(map).containsEntry("name", "Pro User");
            assertThat(map).containsEntry("description", "LuxPretty tenant: salon-rose");
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) map.get("metadata");
            assertThat(meta).containsEntry("tenant_id", "42");
            assertThat(meta).containsEntry("tenant_slug", "salon-rose");
            assertThat(result.getId()).isEqualTo("cus_test123");
        }
    }

    @Test
    void createSetupIntent_buildsOffSessionCardIntent() throws Exception {
        StripeService svc = newService();

        try (MockedStatic<SetupIntent> mocked = mockStatic(SetupIntent.class)) {
            SetupIntent fake = new SetupIntent();
            fake.setClientSecret("seti_secret_xyz");
            mocked.when(() -> SetupIntent.create(any(SetupIntentCreateParams.class))).thenReturn(fake);

            SetupIntent result = svc.createSetupIntent("cus_abc");

            ArgumentCaptor<SetupIntentCreateParams> captor = ArgumentCaptor.forClass(SetupIntentCreateParams.class);
            mocked.verify(() -> SetupIntent.create(captor.capture()));
            Map<String, Object> map = captor.getValue().toMap();
            assertThat(map).containsEntry("customer", "cus_abc");
            assertThat(map).containsEntry("usage", "off_session");
            assertThat(map.get("payment_method_types")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactly("card");
            assertThat(result.getClientSecret()).isEqualTo("seti_secret_xyz");
        }
    }

    @Test
    void createSubscription_includesTrial7days() throws Exception {
        StripeService svc = newService();

        try (MockedStatic<Subscription> mocked = mockStatic(Subscription.class)) {
            Subscription fake = new Subscription();
            fake.setId("sub_test");
            mocked.when(() -> Subscription.create(any(SubscriptionCreateParams.class))).thenReturn(fake);

            svc.createSubscription("cus_abc", "price_gestion_monthly", "pm_xyz");

            ArgumentCaptor<SubscriptionCreateParams> captor = ArgumentCaptor.forClass(SubscriptionCreateParams.class);
            mocked.verify(() -> Subscription.create(captor.capture()));
            Map<String, Object> map = captor.getValue().toMap();
            assertThat(map).containsEntry("trial_period_days", 7L);
            assertThat(map).containsEntry("customer", "cus_abc");
            assertThat(map).containsEntry("default_payment_method", "pm_xyz");
        }
    }

    @Test
    void createSubscription_disablesAutomaticTax_andSavesDefaultPaymentMethod() throws Exception {
        // automatic_tax is disabled until the business is VAT-registered in LU.
        // Flip this assertion to true (and the SDK call) when VAT immatriculation lands.
        StripeService svc = newService();

        try (MockedStatic<Subscription> mocked = mockStatic(Subscription.class)) {
            mocked.when(() -> Subscription.create(any(SubscriptionCreateParams.class)))
                .thenReturn(new Subscription());

            svc.createSubscription("cus_abc", "price_premium_yearly", "pm_xyz");

            ArgumentCaptor<SubscriptionCreateParams> captor = ArgumentCaptor.forClass(SubscriptionCreateParams.class);
            mocked.verify(() -> Subscription.create(captor.capture()));
            Map<String, Object> map = captor.getValue().toMap();
            @SuppressWarnings("unchecked")
            Map<String, Object> automaticTax = (Map<String, Object>) map.get("automatic_tax");
            assertThat(automaticTax).containsEntry("enabled", false);
            @SuppressWarnings("unchecked")
            Map<String, Object> paymentSettings = (Map<String, Object>) map.get("payment_settings");
            assertThat(paymentSettings).containsEntry("save_default_payment_method", "on_subscription");
        }
    }

    @Test
    void createPortalSession_passesCustomerAndReturnUrl() throws Exception {
        StripeService svc = newService();

        try (MockedStatic<Session> mocked = mockStatic(Session.class)) {
            Session fake = new Session();
            fake.setUrl("https://billing.stripe.com/p/session_abc");
            mocked.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(fake);

            Session result = svc.createPortalSession("cus_abc");

            ArgumentCaptor<SessionCreateParams> captor = ArgumentCaptor.forClass(SessionCreateParams.class);
            mocked.verify(() -> Session.create(captor.capture()));
            Map<String, Object> map = captor.getValue().toMap();
            assertThat(map).containsEntry("customer", "cus_abc");
            assertThat(map).containsEntry("return_url", "https://test.luxpretty.lu/pro/settings");
            assertThat(result.getUrl()).isEqualTo("https://billing.stripe.com/p/session_abc");
        }
    }
}
