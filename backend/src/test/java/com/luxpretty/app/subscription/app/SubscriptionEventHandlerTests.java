package com.luxpretty.app.subscription.app;

import com.luxpretty.app.subscription.domain.StripeEventProcessed;
import com.luxpretty.app.subscription.domain.SubscriptionStatus;
import com.luxpretty.app.subscription.repo.StripeEventRepository;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionEventHandlerTests {

    @Mock
    private StripeEventRepository eventRepo;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private SubscriptionEventHandler handler;

    private Event buildEvent(String id, String type, Object dataObject) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn(type);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.ofNullable((com.stripe.model.StripeObject) dataObject));
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }

    private Subscription stubSubscription(String id, String customer, String status) {
        Subscription sub = new Subscription();
        sub.setId(id);
        sub.setCustomer(customer);
        sub.setStatus(status);
        return sub;
    }

    private Invoice stubInvoice(String customer, long amount) {
        Invoice inv = new Invoice();
        inv.setCustomer(customer);
        inv.setAmountPaid(amount);
        return inv;
    }

    @BeforeEach
    void allowDefaultEventSave() {
        // No-op; per-test stubs override as needed.
    }

    @Test
    void handle_subscriptionCreated_updatesTenant() throws Exception {
        Subscription sub = stubSubscription("sub_1", "cus_1", "active");
        Event event = buildEvent("evt_1", "customer.subscription.created", sub);

        handler.handle(event);

        verify(eventRepo).save(any(StripeEventProcessed.class));
        verify(subscriptionService).applySubscriptionUpdate(sub);
    }

    @Test
    void handle_subscriptionUpdated_updatesTenant() throws Exception {
        Subscription sub = stubSubscription("sub_2", "cus_2", "trialing");
        Event event = buildEvent("evt_2", "customer.subscription.updated", sub);

        handler.handle(event);

        verify(subscriptionService).applySubscriptionUpdate(sub);
    }

    @Test
    void handle_subscriptionDeleted_setsStatusCanceled() throws Exception {
        Subscription sub = stubSubscription("sub_3", "cus_3", "canceled");
        Event event = buildEvent("evt_3", "customer.subscription.deleted", sub);

        handler.handle(event);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionService).applySubscriptionUpdate(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("canceled");
    }

    @Test
    void handle_invoicePaid_persistsEvent_butDoesNotChangeTenant() throws Exception {
        Invoice inv = stubInvoice("cus_4", 4999L);
        Event event = buildEvent("evt_4", "invoice.paid", inv);

        handler.handle(event);

        verify(eventRepo).save(any(StripeEventProcessed.class));
        verifyNoInteractions(subscriptionService);
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void handle_invoicePaymentFailed_setsStatusPastDue() throws Exception {
        Invoice inv = stubInvoice("cus_5", 4999L);
        Event event = buildEvent("evt_5", "invoice.payment_failed", inv);

        Tenant tenant = new Tenant();
        tenant.setId(5L);
        tenant.setStripeCustomerId("cus_5");
        when(tenantRepository.findByStripeCustomerId("cus_5")).thenReturn(Optional.of(tenant));

        handler.handle(event);

        assertThat(tenant.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        verify(tenantRepository).save(tenant);
    }

    @Test
    void handle_trialWillEnd_persistsEvent_andLogs() throws Exception {
        Subscription sub = stubSubscription("sub_6", "cus_6", "trialing");
        sub.setTrialEnd(1234567890L);
        Event event = buildEvent("evt_6", "customer.subscription.trial_will_end", sub);

        handler.handle(event);

        verify(eventRepo).save(any(StripeEventProcessed.class));
        verifyNoInteractions(subscriptionService);
    }

    @Test
    void handle_isIdempotent_skipsIfEventAlreadyProcessed() throws Exception {
        // Minimal event stub: only id+type are read before idempotency bail-out.
        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_dup");
        when(eventRepo.save(any(StripeEventProcessed.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate event_id"));

        handler.handle(event);

        verifyNoInteractions(subscriptionService);
        verifyNoInteractions(tenantRepository);
    }
}
