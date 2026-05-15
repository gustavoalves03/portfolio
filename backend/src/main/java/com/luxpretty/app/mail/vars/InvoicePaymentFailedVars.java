package com.luxpretty.app.mail.vars;

public record InvoicePaymentFailedVars(
        String userName,
        String tenantSlug,
        String amountFormatted,
        String portalUrl,
        String dashboardUrl
) implements MailVars {}
