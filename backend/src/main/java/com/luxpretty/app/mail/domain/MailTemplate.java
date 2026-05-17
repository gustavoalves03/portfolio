package com.luxpretty.app.mail.domain;

import com.luxpretty.app.mail.vars.BookingConfirmedVars;
import com.luxpretty.app.mail.vars.BookingReceivedProVars;
import com.luxpretty.app.mail.vars.BookingReminderVars;
import com.luxpretty.app.mail.vars.InvoicePaidVars;
import com.luxpretty.app.mail.vars.InvoicePaymentFailedVars;
import com.luxpretty.app.mail.vars.MailVars;
import com.luxpretty.app.mail.vars.ResetPasswordVars;
import com.luxpretty.app.mail.vars.TrialEndingVars;
import com.luxpretty.app.mail.vars.VerifyEmailVars;
import com.luxpretty.app.mail.vars.WelcomeProVars;

/**
 * Catalogue of available transactional mail templates.
 *
 * <p>Each entry binds a logical template name to:
 * <ul>
 *   <li>the Thymeleaf template path (under {@code templates/mail/})</li>
 *   <li>the concrete {@link MailVars} class expected by the template</li>
 * </ul>
 *
 * <p>Run-time check: {@code MailOutboxService.queue()} asserts
 * {@code template.varsClass().isInstance(vars)} to fail fast if a caller
 * passes mismatched vars.
 */
public enum MailTemplate {
    RESET_PASSWORD("reset-password", ResetPasswordVars.class),
    BOOKING_CONFIRMED("booking-confirmed", BookingConfirmedVars.class),
    BOOKING_RECEIVED_PRO("booking-received-pro", BookingReceivedProVars.class),
    WELCOME_PRO("welcome-pro", WelcomeProVars.class),
    INVOICE_PAID("invoice-paid", InvoicePaidVars.class),
    INVOICE_PAYMENT_FAILED("invoice-payment-failed", InvoicePaymentFailedVars.class),
    TRIAL_ENDING("trial-ending", TrialEndingVars.class),
    VERIFY_EMAIL("verify-email", VerifyEmailVars.class),
    BOOKING_REMINDER_J1("booking-reminder-j1", BookingReminderVars.class);

    private final String templatePath;
    private final Class<? extends MailVars> varsClass;

    MailTemplate(String templatePath, Class<? extends MailVars> varsClass) {
        this.templatePath = templatePath;
        this.varsClass = varsClass;
    }

    public String templatePath() { return templatePath; }
    public Class<? extends MailVars> varsClass() { return varsClass; }
}
