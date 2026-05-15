package com.luxpretty.app.subscription.app;

import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.users.domain.User;
import com.stripe.Stripe;
import com.stripe.model.Customer;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.model.billingportal.Session;
import com.stripe.param.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StripeService {

    @Value("${app.stripe.secret-key:}")
    private String secretKey;

    @Value("${app.stripe.customer-portal-return-url:https://luxpretty.lu/pro/settings}")
    private String portalReturnUrl;

    @PostConstruct
    void init() {
        if (secretKey != null && !secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
        }
    }

    public Customer createCustomer(User owner, Tenant tenant) throws Exception {
        CustomerCreateParams params = CustomerCreateParams.builder()
            .setEmail(owner.getEmail())
            .setName(owner.getName())
            .setDescription("LuxPretty tenant: " + tenant.getSlug())
            .putMetadata("tenant_id", String.valueOf(tenant.getId()))
            .putMetadata("tenant_slug", tenant.getSlug())
            .build();
        return Customer.create(params);
    }

    public SetupIntent createSetupIntent(String customerId) throws Exception {
        SetupIntentCreateParams params = SetupIntentCreateParams.builder()
            .setCustomer(customerId)
            .addPaymentMethodType("card")
            .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
            .build();
        return SetupIntent.create(params);
    }

    public Subscription createSubscription(String customerId, String priceId,
                                           String defaultPaymentMethodId) throws Exception {
        SubscriptionCreateParams params = SubscriptionCreateParams.builder()
            .setCustomer(customerId)
            .addItem(SubscriptionCreateParams.Item.builder().setPrice(priceId).build())
            .setTrialPeriodDays(7L)
            .setDefaultPaymentMethod(defaultPaymentMethodId)
            .setAutomaticTax(SubscriptionCreateParams.AutomaticTax.builder()
                .setEnabled(true).build())
            .setPaymentSettings(SubscriptionCreateParams.PaymentSettings.builder()
                .setSaveDefaultPaymentMethod(
                    SubscriptionCreateParams.PaymentSettings.SaveDefaultPaymentMethod.ON_SUBSCRIPTION)
                .build())
            .build();
        return Subscription.create(params);
    }

    public Session createPortalSession(String customerId) throws Exception {
        com.stripe.param.billingportal.SessionCreateParams params =
            com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(portalReturnUrl)
                .build();
        return Session.create(params);
    }
}
