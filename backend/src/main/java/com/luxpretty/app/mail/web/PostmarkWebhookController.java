package com.luxpretty.app.mail.web;

import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import com.luxpretty.app.mail.web.dto.PostmarkWebhookPayload;
import com.luxpretty.app.users.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/webhooks/postmark")
public class PostmarkWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PostmarkWebhookController.class);

    private final UserRepository userRepo;
    private final MailOutboxRepository mailRepo;
    private final String expectedSecret;

    public PostmarkWebhookController(
            UserRepository userRepo,
            MailOutboxRepository mailRepo,
            @Value("${app.mail.postmark.webhook-secret:}") String expectedSecret
    ) {
        this.userRepo = userRepo;
        this.mailRepo = mailRepo;
        this.expectedSecret = expectedSecret;
    }

    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestHeader(name = "X-Postmark-Webhook-Token", required = false) String token,
            @RequestBody PostmarkWebhookPayload payload
    ) {
        if (expectedSecret == null || expectedSecret.isBlank()) {
            log.warn("Postmark webhook secret not configured — rejecting all webhook calls");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (token == null || !expectedSecret.equals(token)) {
            log.warn("Postmark webhook rejected: invalid or missing token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (payload.recordType() == null) {
            return ResponseEntity.badRequest().build();
        }

        switch (payload.recordType()) {
            case "Bounce" -> {
                if ("HardBounce".equalsIgnoreCase(payload.bounceType())) {
                    blockUserAndMail(payload.email(), payload.messageId(), "hard bounce");
                }
            }
            case "SpamComplaint" -> blockUserAndMail(payload.email(), payload.messageId(), "spam complaint");
            case "Delivery" -> {
                if (payload.messageId() != null) {
                    mailRepo.findByProviderMessageId(payload.messageId())
                            .ifPresent(m -> { m.setDeliveredAt(LocalDateTime.now()); mailRepo.save(m); });
                }
            }
            default -> log.debug("Ignoring Postmark RecordType={}", payload.recordType());
        }

        return ResponseEntity.ok().build();
    }

    private void blockUserAndMail(String email, String messageId, String reason) {
        if (email != null) {
            int updated = userRepo.markEmailBlocked(email);
            log.info("Postmark {}: marked {} email(s) as blocked for {}", reason, updated, email);
        }
        if (messageId != null) {
            mailRepo.findByProviderMessageId(messageId).ifPresent(m -> {
                m.setStatus(MailStatus.PERMANENTLY_FAILED);
                m.setLastError(reason);
                mailRepo.save(m);
            });
        }
    }
}
