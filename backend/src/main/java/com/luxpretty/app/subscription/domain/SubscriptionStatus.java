package com.luxpretty.app.subscription.domain;

public enum SubscriptionStatus {
    /** Free tier with no Stripe subscription. */
    VITRINE_FREE,
    /** Stripe trialing — card on file, will charge at trial_end. */
    TRIALING,
    /** Stripe active — paying customer. */
    ACTIVE,
    /** First invoice failed but in 7-day grace period. */
    PAST_DUE,
    /** Beyond grace window, app access blocked. */
    UNPAID,
    /** User canceled, access until current_period_end. */
    CANCELED,
    /** First payment intent failed (rare). */
    INCOMPLETE,
    INCOMPLETE_EXPIRED,
    PAUSED;

    public boolean grantsAccess() {
        return this == VITRINE_FREE || this == TRIALING || this == ACTIVE || this == PAST_DUE;
    }
}
