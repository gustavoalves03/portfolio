package com.luxpretty.app.mail.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Postmark webhook payload. Single endpoint receives all event types,
 * discriminated by {@code RecordType}.
 */
public record PostmarkWebhookPayload(
        @JsonProperty("RecordType") String recordType,
        @JsonProperty("BounceType") String bounceType,
        @JsonProperty("Email") String email,
        @JsonProperty("MessageID") String messageId
) {}
