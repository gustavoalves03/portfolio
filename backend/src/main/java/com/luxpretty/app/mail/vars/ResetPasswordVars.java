package com.luxpretty.app.mail.vars;

public record ResetPasswordVars(
        String userName,
        String resetUrl
) implements MailVars {}
