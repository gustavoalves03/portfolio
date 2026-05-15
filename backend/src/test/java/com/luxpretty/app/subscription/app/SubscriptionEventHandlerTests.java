package com.luxpretty.app.subscription.app;

import com.luxpretty.app.mail.app.MailOutboxService;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.vars.InvoicePaidVars;
import com.luxpretty.app.mail.vars.InvoicePaymentFailedVars;
import com.luxpretty.app.mail.vars.MailVars;
import com.luxpretty.app.mail.vars.TrialEndingVars;
import com.luxpretty.app.subscription.domain.StripeEventProcessed;
import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionStatus;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import com.luxpretty.app.subscription.repo.StripeEventRepository;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.billingportal.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionEventHandlerTests {

    @Mock
    private StripeEventRepository eventRepo;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MailOutboxService mailOutbox;

    @InjectMocks
    private SubscriptionEventHandler handler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "frontendBaseUrl", "https://test.luxpretty.lu");
    }

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

    private Tenant stubTenant(Long id, String slug, String customerId, Long ownerId) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setSlug(slug);
        tenant.setStripeCustomerId(customerId);
        tenant.setOwnerId(ownerId);
        return tenant;
    }

    private User stubUser(Long id, String name, String email) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        return user;
    }

    // ── Existing tests (updated) ─────────────────────────────────────────────

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
    void handle_invoicePaid_persistsEvent_andQueuesMail() throws Exception {
        Invoice inv = stubInvoice("cus_4", 4999L);
        inv.setNumber("INV-001");
        inv.setHostedInvoiceUrl("https://stripe.com/invoice/INV-001");
        Event event = buildEvent("evt_4", "invoice.paid", inv);

        Tenant tenant = stubTenant(4L, "salon-test", "cus_4", 40L);
        User owner = stubUser(40L, "Marie Dupont", "marie@example.com");
        when(tenantRepository.findByStripeCustomerId("cus_4")).thenReturn(Optional.of(tenant));
        when(userRepository.findById(40L)).thenReturn(Optional.of(owner));

        handler.handle(event);

        verify(eventRepo).save(any(StripeEventProcessed.class));
        verify(tenantRepository).findByStripeCustomerId("cus_4");
        verify(userRepository).findById(40L);

        ArgumentCaptor<MailVars> varsCaptor = ArgumentCaptor.forClass(MailVars.class);
        verify(mailOutbox).queue(eq(MailTemplate.INVOICE_PAID), varsCaptor.capture(),
                eq("marie@example.com"), eq("salon-test"));
        assertThat(varsCaptor.getValue()).isInstanceOf(InvoicePaidVars.class);

        InvoicePaidVars paidVars = (InvoicePaidVars) varsCaptor.getValue();
        assertThat(paidVars.invoiceNumber()).isEqualTo("INV-001");
        assertThat(paidVars.amountFormatted()).isEqualTo("49,99 €");
    }

    @Test
    void handle_invoicePaymentFailed_setsStatusPastDue_andQueuesMail() throws Exception {
        Invoice inv = stubInvoice("cus_5", 4999L);
        Event event = buildEvent("evt_5", "invoice.payment_failed", inv);

        Tenant tenant = stubTenant(5L, "salon-cinq", "cus_5", 50L);
        User owner = stubUser(50L, "Paul Martin", "paul@example.com");
        when(tenantRepository.findByStripeCustomerId("cus_5")).thenReturn(Optional.of(tenant));
        when(userRepository.findById(50L)).thenReturn(Optional.of(owner));
        Session portalSession = mock(Session.class);
        when(portalSession.getUrl()).thenReturn("https://billing.stripe.com/portal/session_xxx");
        when(subscriptionService.createPortalSession(5L)).thenReturn(portalSession);

        handler.handle(event);

        assertThat(tenant.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        verify(tenantRepository).save(tenant);
        verify(tenantRepository).findByStripeCustomerId("cus_5");
        verify(userRepository).findById(50L);

        ArgumentCaptor<MailVars> varsCaptor = ArgumentCaptor.forClass(MailVars.class);
        verify(mailOutbox).queue(eq(MailTemplate.INVOICE_PAYMENT_FAILED), varsCaptor.capture(),
                eq("paul@example.com"), eq("salon-cinq"));
        assertThat(varsCaptor.getValue()).isInstanceOf(InvoicePaymentFailedVars.class);

        InvoicePaymentFailedVars failedVars = (InvoicePaymentFailedVars) varsCaptor.getValue();
        assertThat(failedVars.portalUrl()).isEqualTo("https://billing.stripe.com/portal/session_xxx");
    }

    @Test
    void handle_trialWillEnd_persistsEvent_andQueuesMail() throws Exception {
        Subscription sub = stubSubscription("sub_6", "cus_6", "trialing");
        sub.setTrialEnd(1780444800L); // 2026-06-03 00:00 UTC
        Event event = buildEvent("evt_6", "customer.subscription.trial_will_end", sub);

        Tenant tenant = stubTenant(6L, "salon-six", "cus_6", 60L);
        tenant.setSubscriptionTier(SubscriptionTier.GESTION);
        tenant.setSubscriptionBilling(SubscriptionBilling.MONTHLY);
        User owner = stubUser(60L, "Sophie Bernard", "sophie@example.com");
        when(tenantRepository.findByStripeCustomerId("cus_6")).thenReturn(Optional.of(tenant));
        when(userRepository.findById(60L)).thenReturn(Optional.of(owner));

        handler.handle(event);

        verify(eventRepo).save(any(StripeEventProcessed.class));
        verify(tenantRepository).findByStripeCustomerId("cus_6");
        verify(userRepository).findById(60L);

        ArgumentCaptor<MailVars> varsCaptor = ArgumentCaptor.forClass(MailVars.class);
        verify(mailOutbox).queue(eq(MailTemplate.TRIAL_ENDING), varsCaptor.capture(),
                eq("sophie@example.com"), eq("salon-six"));
        assertThat(varsCaptor.getValue()).isInstanceOf(TrialEndingVars.class);

        TrialEndingVars trialVars = (TrialEndingVars) varsCaptor.getValue();
        assertThat(trialVars.tierLabel()).isEqualTo("Gestion");
        assertThat(trialVars.priceLabel()).isEqualTo("49,99 €/mois");
    }

    @Test
    void handle_isIdempotent_skipsIfEventAlreadyProcessed() throws Exception {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_dup");
        when(eventRepo.save(any(StripeEventProcessed.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate event_id"));

        handler.handle(event);

        verifyNoInteractions(subscriptionService);
        verifyNoInteractions(tenantRepository);
    }

    // ── New tests ─────────────────────────────────────────────────────────────

    @Test
    void handle_invoicePaid_skipsMail_whenOwnerEmailMissing() throws Exception {
        Invoice inv = stubInvoice("cus_7", 4999L);
        Event event = buildEvent("evt_7", "invoice.paid", inv);

        Tenant tenant = stubTenant(7L, "salon-sept", "cus_7", 70L);
        when(tenantRepository.findByStripeCustomerId("cus_7")).thenReturn(Optional.of(tenant));
        when(userRepository.findById(70L)).thenReturn(Optional.empty());

        // Must not throw
        handler.handle(event);

        verify(userRepository).findById(70L);
        verify(mailOutbox, never()).queue(any(), any(), any(), any());
    }

    @Test
    void handle_invoicePaymentFailed_usesFallbackPortalUrl_whenStripeFails() throws Exception {
        Invoice inv = stubInvoice("cus_8", 6799L);
        Event event = buildEvent("evt_8", "invoice.payment_failed", inv);

        Tenant tenant = stubTenant(8L, "salon-huit", "cus_8", 80L);
        User owner = stubUser(80L, "Léa Moreau", "lea@example.com");
        when(tenantRepository.findByStripeCustomerId("cus_8")).thenReturn(Optional.of(tenant));
        when(userRepository.findById(80L)).thenReturn(Optional.of(owner));
        when(subscriptionService.createPortalSession(8L))
                .thenThrow(new RuntimeException("Stripe connection refused"));

        handler.handle(event);

        ArgumentCaptor<MailVars> varsCaptor = ArgumentCaptor.forClass(MailVars.class);
        verify(mailOutbox).queue(eq(MailTemplate.INVOICE_PAYMENT_FAILED), varsCaptor.capture(),
                eq("lea@example.com"), eq("salon-huit"));
        InvoicePaymentFailedVars vars = (InvoicePaymentFailedVars) varsCaptor.getValue();
        assertThat(vars.portalUrl()).isEqualTo("https://test.luxpretty.lu/pro/settings");
    }

    @Test
    void handle_trialWillEnd_formatsDate_inFrench() throws Exception {
        // 1780444800 = 2026-06-03 00:00:00 UTC = "3 juin 2026" in Europe/Paris (UTC+2 in summer)
        Subscription sub = stubSubscription("sub_9", "cus_9", "trialing");
        sub.setTrialEnd(1780444800L);
        Event event = buildEvent("evt_9", "customer.subscription.trial_will_end", sub);

        Tenant tenant = stubTenant(9L, "salon-neuf", "cus_9", 90L);
        tenant.setSubscriptionTier(SubscriptionTier.PREMIUM);
        tenant.setSubscriptionBilling(SubscriptionBilling.YEARLY);
        User owner = stubUser(90L, "Clara Petit", "clara@example.com");
        when(tenantRepository.findByStripeCustomerId("cus_9")).thenReturn(Optional.of(tenant));
        when(userRepository.findById(90L)).thenReturn(Optional.of(owner));

        handler.handle(event);

        ArgumentCaptor<MailVars> varsCaptor = ArgumentCaptor.forClass(MailVars.class);
        verify(mailOutbox).queue(eq(MailTemplate.TRIAL_ENDING), varsCaptor.capture(),
                eq("clara@example.com"), eq("salon-neuf"));
        TrialEndingVars vars = (TrialEndingVars) varsCaptor.getValue();
        // 1748908800 UTC = 2026-06-03 02:00 Europe/Paris → "3 juin 2026"
        assertThat(vars.trialEndDateFormatted()).isEqualTo("3 juin 2026");
        assertThat(vars.tierLabel()).isEqualTo("Premium");
        assertThat(vars.priceLabel()).isEqualTo("57,79 €/mois facturé à l'année");
    }
}
