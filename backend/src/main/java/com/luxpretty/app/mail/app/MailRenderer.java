package com.luxpretty.app.mail.app;

import com.luxpretty.app.mail.domain.MailOutbox;

/**
 * Renders a MailOutbox row into an envelope (subject, html, text).
 * PR1 ships with a stub that returns minimal placeholders;
 * PR2 replaces it with Thymeleaf + Jsoup inlining.
 */
public interface MailRenderer {
    record Rendered(String subject, String htmlBody, String textBody) {}
    Rendered render(MailOutbox row);
}
