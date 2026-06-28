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

    /**
     * True when a live Stripe subscription already exists, so a plan change must
     * go through the billing portal / a plan-change flow — NOT a fresh checkout.
     * Starting a new checkout in these states would create a second concurrent
     * subscription on the same Stripe customer (double billing).
     * Dead states (CANCELED, UNPAID, INCOMPLETE_EXPIRED, VITRINE_FREE) are
     * intentionally excluded so a lapsed tenant can re-subscribe.
     */
    public boolean hasLiveSubscription() {
        return this == TRIALING || this == ACTIVE || this == PAST_DUE || this == INCOMPLETE;
    }
}
