package com.luxpretty.app.mail.vars;

/**
 * Sealed marker interface for typed mail template variables.
 * <p>Each {@code MailTemplate} entry binds to one concrete subtype.
 * Records are serialized to JSON and stored in {@code MAIL_OUTBOX.VARS_JSON}.
 */
public sealed interface MailVars
    permits ResetPasswordVars, BookingConfirmedVars, BookingReceivedProVars, WelcomeProVars {}
