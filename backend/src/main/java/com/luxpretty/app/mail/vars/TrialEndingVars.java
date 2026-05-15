package com.luxpretty.app.mail.vars;

public record TrialEndingVars(
        String userName,
        String tenantSlug,
        String trialEndDateFormatted,
        String tierLabel,
        String priceLabel,
        String dashboardUrl
) implements MailVars {}
