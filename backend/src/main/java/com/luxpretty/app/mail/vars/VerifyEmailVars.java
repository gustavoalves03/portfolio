package com.luxpretty.app.mail.vars;

public record VerifyEmailVars(
        String name,
        String verifyUrl
) implements MailVars {}
