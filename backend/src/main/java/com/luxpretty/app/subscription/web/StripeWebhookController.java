package com.luxpretty.app.subscription.web;

import com.luxpretty.app.subscription.app.SubscriptionEventHandler;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/stripe")
public class StripeWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

    @Value("${app.stripe.webhook-secret:}")
    private String webhookSecret;

    private final SubscriptionEventHandler handler;

    public StripeWebhookController(SubscriptionEventHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    public ResponseEntity<String> handle(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            logger.warn("Invalid Stripe webhook signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        try {
            handler.handle(event);
        } catch (Exception e) {
            logger.error("Webhook handling failed: type={} id={}", event.getType(), event.getId(), e);
            // Return 500 so Stripe retries
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing error");
        }

        return ResponseEntity.ok("ok");
    }
}
