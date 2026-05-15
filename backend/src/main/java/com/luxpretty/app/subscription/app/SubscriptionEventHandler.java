package com.luxpretty.app.subscription.app;

import com.luxpretty.app.subscription.domain.SubscriptionStatus;
import com.luxpretty.app.subscription.domain.StripeEventProcessed;
import com.luxpretty.app.subscription.repo.StripeEventRepository;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionEventHandler.class);

    private final StripeEventRepository eventRepo;
    private final SubscriptionService subscriptionService;
    private final TenantRepository tenantRepository;

    public SubscriptionEventHandler(StripeEventRepository eventRepo,
                                    SubscriptionService subscriptionService,
                                    TenantRepository tenantRepository) {
        this.eventRepo = eventRepo;
        this.subscriptionService = subscriptionService;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public void handle(Event event) throws Exception {
        // Idempotency: skip if already processed
        try {
            eventRepo.save(StripeEventProcessed.builder()
                    .eventId(event.getId())
                    .eventType(event.getType())
                    .build());
        } catch (DataIntegrityViolationException e) {
            logger.debug("Stripe event {} already processed, skipping", event.getId());
            return;
        }

        switch (event.getType()) {
            case "customer.subscription.created",
                    "customer.subscription.updated" -> onSubscriptionChange(event);
            case "customer.subscription.deleted" -> onCancellation(event);
            case "invoice.paid" -> onInvoicePaid(event);
            case "invoice.payment_failed" -> onPaymentFailed(event);
            case "customer.subscription.trial_will_end" -> onTrialWillEnd(event);
            default -> logger.debug("Ignoring Stripe event type: {}", event.getType());
        }
    }

    private void onSubscriptionChange(Event event) throws Exception {
        Subscription sub = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalArgumentException("Missing subscription object"));
        subscriptionService.applySubscriptionUpdate(sub);
    }

    private void onCancellation(Event event) throws Exception {
        Subscription sub = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalArgumentException("Missing subscription object"));
        subscriptionService.applySubscriptionUpdate(sub);
    }

    private void onInvoicePaid(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalArgumentException("Missing invoice object"));
        logger.info("TODO: send invoice-paid mail for customer={} amount={}",
                invoice.getCustomer(), invoice.getAmountPaid());
        // FIXME: pending mail template creation (see follow-up Task 7-bis)
    }

    private void onPaymentFailed(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalArgumentException("Missing invoice object"));
        // Set tenant status to PAST_DUE
        tenantRepository.findByStripeCustomerId(invoice.getCustomer()).ifPresent(tenant -> {
            tenant.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
            tenantRepository.save(tenant);
        });
        logger.warn("TODO: send payment-failed mail for customer={}", invoice.getCustomer());
        // FIXME: pending mail template creation
    }

    private void onTrialWillEnd(Event event) {
        Subscription sub = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalArgumentException("Missing subscription object"));
        logger.info("TODO: send trial-ending mail for customer={} trial_end={}",
                sub.getCustomer(), sub.getTrialEnd());
        // FIXME: pending mail template creation
    }
}
