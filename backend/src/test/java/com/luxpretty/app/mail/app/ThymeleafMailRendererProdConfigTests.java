package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.vars.BookingConfirmedVars;
import com.luxpretty.app.mail.vars.BookingReminderVars;
import com.luxpretty.app.mail.vars.ResetPasswordVars;
import com.luxpretty.app.mail.vars.VerifyEmailVars;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test: render every transactional mail template using the REAL
 * production Thymeleaf wiring ({@link MailTemplateConfig} + Spring Boot's
 * {@link ThymeleafAutoConfiguration}). This catches resolver-ordering bugs
 * where a {@code .txt} resolver hijacks an HTML render.
 */
@SpringBootTest(classes = {
        ThymeleafAutoConfiguration.class,
        MailTemplateConfig.class,
        ThymeleafMailRenderer.class,
        ThymeleafMailRendererProdConfigTests.JacksonConfig.class
})
@ActiveProfiles("test")
@Import(MailTemplateConfig.class)
class ThymeleafMailRendererProdConfigTests {

    @Autowired ThymeleafMailRenderer renderer;

    @org.springframework.boot.test.context.TestConfiguration
    static class JacksonConfig {
        @org.springframework.context.annotation.Bean
        ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    @Test
    void reset_password_renders_full_html_layout() throws Exception {
        ObjectMapper om = new ObjectMapper();
        ResetPasswordVars vars = new ResetPasswordVars("Alice", "https://app/reset?token=xyz");
        MailOutbox row = new MailOutbox();
        row.setTemplate(MailTemplate.RESET_PASSWORD);
        row.setVarsJson(om.writeValueAsString(vars));

        MailRenderer.Rendered out = renderer.render(row);

        assertHasFullHtmlLayout(out.htmlBody(), "Alice");
        assertThat(out.textBody()).contains("Alice").doesNotContain("<html");
    }

    @Test
    void verify_email_renders_full_html_layout() throws Exception {
        ObjectMapper om = new ObjectMapper();
        VerifyEmailVars vars = new VerifyEmailVars("Bob", "https://app/verify-email?token=xyz");
        MailOutbox row = new MailOutbox();
        row.setTemplate(MailTemplate.VERIFY_EMAIL);
        row.setVarsJson(om.writeValueAsString(vars));

        MailRenderer.Rendered out = renderer.render(row);

        assertHasFullHtmlLayout(out.htmlBody(), "Bob");
        assertThat(out.textBody()).contains("Bob").doesNotContain("<html");
    }

    @Test
    void booking_confirmed_renders_full_html_layout() throws Exception {
        ObjectMapper om = new ObjectMapper();
        BookingConfirmedVars vars = new BookingConfirmedVars(
                "Carol", "Salon Test", "Soin Visage",
                new java.math.BigDecimal("50.00"), "60 min",
                "2026-06-01", "14:00", 1L, "https://app/dashboard");
        MailOutbox row = new MailOutbox();
        row.setTemplate(MailTemplate.BOOKING_CONFIRMED);
        row.setVarsJson(om.writeValueAsString(vars));

        MailRenderer.Rendered out = renderer.render(row);

        assertHasFullHtmlLayout(out.htmlBody(), "Carol");
        assertThat(out.textBody()).contains("Carol").doesNotContain("<html");
    }

    @Test
    void booking_reminder_j1_renders_full_html_layout() throws Exception {
        ObjectMapper om = new ObjectMapper();
        BookingReminderVars vars = new BookingReminderVars(
                "Dave", "Salon Test", "Soin", "2026-06-01", "14:00",
                "12 rue Test", 1L, "https://app/bookings/1");
        MailOutbox row = new MailOutbox();
        row.setTemplate(MailTemplate.BOOKING_REMINDER_J1);
        row.setVarsJson(om.writeValueAsString(vars));

        MailRenderer.Rendered out = renderer.render(row);

        assertHasFullHtmlLayout(out.htmlBody(), "Dave");
        assertThat(out.textBody()).contains("Dave").doesNotContain("<html");
    }

    private static void assertHasFullHtmlLayout(String html, String name) {
        assertThat(html)
                .as("HTML body must contain the shared _layout.html scaffolding")
                .contains("class=\"container\"")
                .contains("class=\"header\"")
                .contains("class=\"foot\"")
                .contains("class=\"cta\"")
                .contains("LuxPretty")
                .contains(name);
    }
}
