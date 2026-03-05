package com.fleurdecoquillage.app.notification.app;

import com.fleurdecoquillage.app.users.domain.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.frontend.base-url:http://localhost:4300}")
    private String frontendBaseUrl;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Async
    public void sendWelcomeEmail(User user) {
        try {
            Context ctx = new Context();
            ctx.setVariable("userName", user.getName());
            ctx.setVariable("userEmail", user.getEmail());
            ctx.setVariable("dashboardUrl", frontendBaseUrl + "/pro/dashboard");

            String htmlContent = templateEngine.process("welcome-pro", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(user.getEmail());
            helper.setSubject("Welcome to Pretty Face / Bienvenue sur Pretty Face !");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Welcome email sent to {}", user.getEmail());

        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            logger.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
        }
    }
}
