package com.luxpretty.app.mail.app;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailSender implements MailSender {

    private final JavaMailSender javaMailSender;
    private final String fromAddress;
    private final String fromName;

    public SmtpMailSender(
            JavaMailSender javaMailSender,
            @Value("${app.mail.from}") String fromAddress,
            @Value("${app.mail.from-name:LuxPretty}") String fromName
    ) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public String send(String recipientEmail, String subject, String htmlBody, String textBody) {
        try {
            MimeMessage msg = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);  // multipart alternative
            javaMailSender.send(msg);
            return null;  // SMTP has no provider message id
        } catch (MailException e) {
            // Spring wraps IO/timeout in MailSendException
            throw new RetryableMailException("SMTP send failed", e);
        } catch (MessagingException | UnsupportedEncodingException e) {
            // Bad subject encoding, invalid From — caller config error
            throw new HardMailException("SMTP message construction failed", e);
        }
    }
}
