package com.luxpretty.app.subscription.web.dto;

/**
 * Public response DTO for GET /api/stripe/config.
 * Returns the Stripe publishable key so the frontend can initialise Stripe.js
 * without hardcoding any key in TypeScript code.
 */
public record StripeConfigResponse(String publishableKey) {
}
