package com.luxpretty.app.mail.vars;

public record InvoicePaidVars(
        String userName,
        String tenantSlug,
        String amountFormatted,
        String invoiceNumber,
        String invoicePdfUrl,
        String dashboardUrl
) implements MailVars {}
