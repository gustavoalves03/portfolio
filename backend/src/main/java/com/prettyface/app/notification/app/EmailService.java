package com.prettyface.app.notification.app;

import com.prettyface.app.bookings.domain.CareBooking;
import com.prettyface.app.care.domain.Care;
import com.prettyface.app.users.domain.User;
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

import java.time.format.DateTimeFormatter;
import java.util.Locale;

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

    @Async
    public void sendPasswordResetEmail(User user, String resetToken) {
        try {
            Context ctx = new Context();
            ctx.setVariable("userName", user.getName());
            ctx.setVariable("resetUrl", frontendBaseUrl + "/reset-password?token=" + resetToken);

            String htmlContent = templateEngine.process("password-reset", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(user.getEmail());
            helper.setSubject("Reset your password / Réinitialiser votre mot de passe");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Password reset email sent to {}", user.getEmail());

        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            logger.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    @Async
    public void sendBookingConfirmationEmail(User client, CareBooking booking, Care care, String salonName) {
        try {
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

            Context ctx = new Context();
            ctx.setVariable("clientName", client.getName());
            ctx.setVariable("salonName", salonName);
            ctx.setVariable("careName", care.getName());
            ctx.setVariable("carePrice", care.getPrice());
            ctx.setVariable("careDuration", formatDuration(care.getDuration()));
            ctx.setVariable("appointmentDate", booking.getAppointmentDate().format(dateFmt));
            ctx.setVariable("appointmentTime", booking.getAppointmentTime().format(timeFmt));
            ctx.setVariable("bookingId", booking.getId());
            ctx.setVariable("dashboardUrl", frontendBaseUrl + "/bookings");

            String htmlContent = templateEngine.process("booking-confirmation", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(client.getEmail());
            helper.setSubject("Votre rendez-vous est confirmé / Your appointment is confirmed — " + salonName);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Booking confirmation email sent to {}", client.getEmail());

        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            logger.error("Failed to send booking confirmation email to {}: {}", client.getEmail(), e.getMessage());
        }
    }

    @Async
    public void sendNewBookingNotificationEmail(User pro, CareBooking booking, Care care, String clientName) {
        try {
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

            Context ctx = new Context();
            ctx.setVariable("proName", pro.getName());
            ctx.setVariable("clientName", clientName);
            ctx.setVariable("careName", care.getName());
            ctx.setVariable("carePrice", care.getPrice());
            ctx.setVariable("careDuration", formatDuration(care.getDuration()));
            ctx.setVariable("appointmentDate", booking.getAppointmentDate().format(dateFmt));
            ctx.setVariable("appointmentTime", booking.getAppointmentTime().format(timeFmt));
            ctx.setVariable("bookingId", booking.getId());
            ctx.setVariable("dashboardUrl", frontendBaseUrl + "/pro/bookings");

            String htmlContent = templateEngine.process("booking-notification-pro", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(pro.getEmail());
            helper.setSubject("Nouveau rendez-vous / New appointment — " + clientName);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Booking notification email sent to pro {}", pro.getEmail());

        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            logger.error("Failed to send booking notification email to {}: {}", pro.getEmail(), e.getMessage());
        }
    }

    private String formatDuration(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        int hours = minutes / 60;
        int remaining = minutes % 60;
        if (remaining == 0) {
            return hours + "h";
        }
        return hours + "h" + String.format("%02d", remaining);
    }
}
