package com.luxpretty.app.mail.vars;

public record WelcomeProVars(
        String userName,
        String userEmail,
        String dashboardUrl
) implements MailVars {}
