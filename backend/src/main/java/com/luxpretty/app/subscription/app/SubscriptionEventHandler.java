package com.luxpretty.app.subscription.app;

import com.luxpretty.app.mail.app.MailOutboxService;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.vars.InvoicePaidVars;
import com.luxpretty.app.mail.vars.InvoicePaymentFailedVars;
import com.luxpretty.app.mail.vars.TrialEndingVars;
import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionStatus;
import com.luxpretty.app.subscription.domain.StripeEventProcessed;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import com.luxpretty.app.subscription.repo.StripeEventRepository;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.repo.UserRepository;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.billingportal.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class SubscriptionEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionEventHandler.class);

    private final StripeEventRepository eventRepo;
    private final SubscriptionService subscriptionService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final MailOutboxService mailOutbox;

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    public SubscriptionEventHandler(StripeEventRepository eventRepo,
                                    SubscriptionService subscriptionService,
                                    TenantRepository tenantRepository,
                                    UserRepository userRepository,
                                    MailOutboxService mailOutbox) {
        this.eventRepo = eventRepo;
        this.subscriptionService = subscriptionService;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.mailOutbox = mailOutbox;
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

        tenantRepository.findByStripeCustomerId(invoice.getCustomer()).ifPresent(tenant -> {
            var owner = userRepository.findById(tenant.getOwnerId());
            if (owner.isEmpty()) {
                logger.warn("Owner not found for tenant {} (ownerId={}), skipping mail",
                        tenant.getSlug(), tenant.getOwnerId());
                return;
            }

            String recipientEmail = owner.get().getEmail();
            String userName = owner.get().getName();
            String dashboardUrl = frontendBaseUrl + "/pro/dashboard";

            InvoicePaidVars vars = new InvoicePaidVars(
                    userName,
                    tenant.getSlug(),
                    formatAmountEuros(invoice.getAmountPaid()),
                    invoice.getNumber() != null ? invoice.getNumber() : "",
                    invoice.getHostedInvoiceUrl() != null ? invoice.getHostedInvoiceUrl() : dashboardUrl,
                    dashboardUrl
            );
            mailOutbox.queue(MailTemplate.INVOICE_PAID, vars, recipientEmail, tenant.getSlug());
        });
    }

    private void onPaymentFailed(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalArgumentException("Missing invoice object"));

        tenantRepository.findByStripeCustomerId(invoice.getCustomer()).ifPresent(tenant -> {
            // Set tenant status to PAST_DUE
            tenant.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
            tenantRepository.save(tenant);

            var owner = userRepository.findById(tenant.getOwnerId());
            if (owner.isEmpty()) {
                logger.warn("Owner not found for tenant {} (ownerId={}), skipping mail",
                        tenant.getSlug(), tenant.getOwnerId());
                return;
            }

            String recipientEmail = owner.get().getEmail();
            String userName = owner.get().getName();
            String dashboardUrl = frontendBaseUrl + "/pro/dashboard";
            String portalUrl = resolvePortalUrl(tenant);

            InvoicePaymentFailedVars vars = new InvoicePaymentFailedVars(
                    userName,
                    tenant.getSlug(),
                    formatAmountEuros(invoice.getAmountPaid()),
                    portalUrl,
                    dashboardUrl
            );
            mailOutbox.queue(MailTemplate.INVOICE_PAYMENT_FAILED, vars, recipientEmail, tenant.getSlug());
        });
    }

    private void onTrialWillEnd(Event event) {
        Subscription sub = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalArgumentException("Missing subscription object"));

        tenantRepository.findByStripeCustomerId(sub.getCustomer()).ifPresent(tenant -> {
            var owner = userRepository.findById(tenant.getOwnerId());
            if (owner.isEmpty()) {
                logger.warn("Owner not found for tenant {} (ownerId={}), skipping mail",
                        tenant.getSlug(), tenant.getOwnerId());
                return;
            }

            String recipientEmail = owner.get().getEmail();
            String userName = owner.get().getName();
            String dashboardUrl = frontendBaseUrl + "/pro/dashboard";
            String trialEndFormatted = formatTrialEndDate(sub.getTrialEnd());
            String[] tierPrice = resolveTierPriceLabels(tenant);

            TrialEndingVars vars = new TrialEndingVars(
                    userName,
                    tenant.getSlug(),
                    trialEndFormatted,
                    tierPrice[0],
                    tierPrice[1],
                    dashboardUrl
            );
            mailOutbox.queue(MailTemplate.TRIAL_ENDING, vars, recipientEmail, tenant.getSlug());
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String resolvePortalUrl(Tenant tenant) {
        try {
            Session session = subscriptionService.createPortalSession(tenant.getId());
            return session.getUrl();
        } catch (Exception e) {
            logger.warn("Failed to create Stripe portal session for tenant {}, falling back to settings URL: {}",
                    tenant.getSlug(), e.getMessage());
            return frontendBaseUrl + "/pro/settings";
        }
    }

    private String formatAmountEuros(long cents) {
        // Use explicit integer arithmetic with comma separator to avoid
        // JDK CLDR non-breaking space for Locale.FRANCE in formatted numbers
        long euros = cents / 100;
        long centsPart = Math.abs(cents % 100);
        return euros + "," + String.format("%02d", centsPart) + " €";
    }

    private String formatTrialEndDate(Long epochSecond) {
        if (epochSecond == null) return "";
        return Instant.ofEpochSecond(epochSecond)
                .atZone(ZoneId.of("Europe/Paris"))
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.FRANCE));
    }

    private String[] resolveTierPriceLabels(Tenant tenant) {
        SubscriptionTier tier = tenant.getSubscriptionTier();
        SubscriptionBilling billing = tenant.getSubscriptionBilling();

        if (tier == null || billing == null) {
            return new String[]{"votre abonnement", ""};
        }

        return switch (tier) {
            case GESTION -> switch (billing) {
                case MONTHLY -> new String[]{"Gestion", "49,99 €/mois"};
                case YEARLY  -> new String[]{"Gestion", "42,49 €/mois facturé à l'année"};
                default      -> new String[]{"Gestion", ""};
            };
            case PREMIUM -> switch (billing) {
                case MONTHLY -> new String[]{"Premium", "67,99 €/mois"};
                case YEARLY  -> new String[]{"Premium", "57,79 €/mois facturé à l'année"};
                default      -> new String[]{"Premium", ""};
            };
            default -> new String[]{"votre abonnement", ""};
        };
    }
}
