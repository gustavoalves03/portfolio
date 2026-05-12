package com.luxpretty.app.mail.app;

import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import com.luxpretty.app.mail.vars.ResetPasswordVars;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real-SMTP smoke test against Hostinger.
 *
 * <p>This test sends an ACTUAL email through the configured Hostinger SMTP
 * account. It is gated behind the {@code HOSTINGER_SMOKE_RECIPIENT} env var
 * so it never runs in CI or by accident — only when you set the var and
 * activate the {@code prodtest} profile.
 *
 * <p>Run from the {@code backend/} directory:
 * <pre>
 *   HOSTINGER_SMOKE_RECIPIENT=you@example.com \
 *   SPRING_PROFILES_ACTIVE=prodtest \
 *   mvn test -Dtest=HostingerSmtpSmokeTests
 * </pre>
 *
 * <p>Prerequisite: copy {@code application-prodtest.properties.template} to
 * {@code application-prodtest.properties} and fill in the Hostinger password.
 * The {@code .properties} file is gitignored automatically.
 *
 * <p>Expected outcome:
 * <ul>
 *   <li>Mail visible in your inbox within ~10 seconds (check spam too)</li>
 *   <li>From: {@code noreply@luxpretty.lu}</li>
 *   <li>Test logs end with {@code BUILD SUCCESS}</li>
 * </ul>
 *
 * <p>If the test fails with {@code AuthenticationFailedException}, the
 * password in {@code application-prodtest.properties} is wrong or the alias
 * cannot authenticate (Hostinger limitation). Use the parent mailbox
 * credentials in that case.
 */
@SpringBootTest
@ActiveProfiles("prodtest")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "HOSTINGER_SMOKE_RECIPIENT", matches = ".+")
class HostingerSmtpSmokeTests {

    @Autowired MailOutboxService service;
    @Autowired MailWorker worker;
    @Autowired MailOutboxRepository repo;

    @Test
    void send_real_mail_through_hostinger_smtp() {
        String recipient = System.getenv("HOSTINGER_SMOKE_RECIPIENT");
        assertThat(recipient).as("HOSTINGER_SMOKE_RECIPIENT must be set").isNotBlank();

        // 1. Queue a RESET_PASSWORD mail (uses an existing template with safe
        //    LuxPretty branding — no PII other than the recipient address).
        service.queue(
                MailTemplate.RESET_PASSWORD,
                new ResetPasswordVars(
                        "Smoke Test",
                        "https://luxpretty.lu/reset?token=smoke-test-token"),
                recipient,
                null);

        // 2. Trigger the worker once (don't wait for the @Scheduled tick).
        worker.pollAndSend();

        // 3. Poll the outbox until the row is SENT (or FAILED on auth error).
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<MailOutbox> rows = repo.findAll();
            assertThat(rows).hasSize(1);
            MailStatus status = rows.get(0).getStatus();
            assertThat(status)
                    .as("Outbox row should be SENT after delivery. Status=%s, lastError=%s",
                            status, rows.get(0).getLastError())
                    .isEqualTo(MailStatus.SENT);
            assertThat(rows.get(0).getSentAt()).isNotNull();
        });

        System.out.println("✓ Mail sent to " + recipient + " via Hostinger SMTP.");
        System.out.println("  Check your inbox (and spam folder) for the LuxPretty reset-password mail.");
    }
}
