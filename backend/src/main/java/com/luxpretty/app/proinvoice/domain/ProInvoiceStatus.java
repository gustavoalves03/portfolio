package com.luxpretty.app.proinvoice.domain;

/** Aligned with Stripe Invoice status. */
public enum ProInvoiceStatus {
    DRAFT, OPEN, PAID, UNCOLLECTIBLE, VOID
}
