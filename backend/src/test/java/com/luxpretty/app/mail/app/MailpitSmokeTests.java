package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import com.luxpretty.app.mail.vars.ResetPasswordVars;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end smoke test for the mail outbox pipeline.
 *
 * Starts a Mailpit container (SMTP + HTTP API), boots the full Spring context
 * against H2 + Mailpit, queues one email, triggers the worker tick, and asserts
 * the message arrived in Mailpit and the DB row is marked SENT.
 */
@SpringBootTest(properties = {
        "app.mail.provider=smtp",
        "app.mail.from=test@luxpretty.local",
        "app.mail.worker.enabled=true",
        "spring.mail.properties.mail.smtp.auth=false"
})
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MailpitSmokeTests {

    private static final GenericContainer<?> MAILPIT =
            new GenericContainer<>("axllent/mailpit:latest")
                    .withExposedPorts(1025, 8025)
                    .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025).forStatusCode(200))
                    .withStartupTimeout(Duration.ofSeconds(60));

    @BeforeAll
    static void startContainer() {
        MAILPIT.start();
    }

    @AfterAll
    static void stopContainer() {
        MAILPIT.stop();
    }

    @DynamicPropertySource
    static void wireSmtp(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    @Autowired
    MailOutboxService service;

    @Autowired
    MailWorker worker;

    @Autowired
    MailOutboxRepository repo;

    @Test
    void queue_then_worker_tick_delivers_to_mailpit() throws Exception {
        // 1. Queue a RESET_PASSWORD mail
        service.queue(
                MailTemplate.RESET_PASSWORD,
                new ResetPasswordVars("Alice", "https://app/reset?token=abc"),
                "alice@example.com",
                null);

        // 2. Trigger the worker synchronously — no need to wait for the scheduler
        worker.pollAndSend();

        // 3. Assert Mailpit received the message via its HTTP API
        String mailpitApiUrl = "http://" + MAILPIT.getHost() + ":"
                + MAILPIT.getMappedPort(8025) + "/api/v1/messages";
        ObjectMapper om = new ObjectMapper();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            HttpResponse<String> resp = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(mailpitApiUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode body = om.readTree(resp.body());
            assertThat(body.get("messages_count").asInt())
                    .as("Mailpit should have received at least one message")
                    .isGreaterThanOrEqualTo(1);
            JsonNode first = body.get("messages").get(0);
            assertThat(first.get("To").get(0).get("Address").asText())
                    .as("Recipient address should match")
                    .isEqualTo("alice@example.com");
        });

        // 4. Assert the DB row is marked SENT
        var rows = repo.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus().name())
                .as("Outbox row should be SENT after successful delivery")
                .isEqualTo("SENT");
        assertThat(rows.get(0).getSentAt()).isNotNull();
    }
}
