package com.luxpretty.app.subscription.domain;

public enum SubscriptionTier {
    VITRINE,    // 0€, no Stripe subscription
    GESTION,    // 49.99€/mo or 42.49€/mo yearly
    PREMIUM;    // 67.99€/mo or 57.79€/mo yearly
}
