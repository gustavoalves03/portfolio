package com.luxpretty.app.mail.app;

/**
 * Abstraction over the underlying mail provider.
 * <p>Two implementations selected at runtime via {@code app.mail.provider}:
 * <ul>
 *   <li>{@code smtp}: {@link SmtpMailSender} (dev — Mailpit)</li>
 *   <li>{@code postmark}: {@link PostmarkMailSender} (prod)</li>
 * </ul>
 */
public interface MailSender {

    /**
     * Sends a mail synchronously.
     *
     * @param recipientEmail to address
     * @param subject mail subject
     * @param htmlBody HTML body (already inlined-CSS)
     * @param textBody plain text body
     * @return provider-specific message id (Postmark MessageID) or {@code null} for SMTP
     * @throws RetryableMailException for transient failures (5xx, timeout, IO)
     * @throws HardMailException for permanent failures (invalid email, bad credentials)
     */
    String send(String recipientEmail, String subject, String htmlBody, String textBody);
}
