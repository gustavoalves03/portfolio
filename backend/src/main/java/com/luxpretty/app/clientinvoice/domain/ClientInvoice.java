package com.luxpretty.app.clientinvoice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "CLIENT_INVOICES")
public class ClientInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "client_user_id")
    private Long clientUserId;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Column(name = "stripe_invoice_id", length = 255)
    private String stripeInvoiceId;

    @Column(name = "number_label", nullable = false, length = 64)
    private String numberLabel;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private ClientInvoiceKind kind;

    @Column(name = "amount_subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountSubtotal;

    @Column(name = "amount_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountTax;

    @Column(name = "amount_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountTotal;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "EUR";

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClientInvoiceStatus status;

    @Lob
    @Basic(fetch = FetchType.EAGER)
    @Column(name = "emitter_snapshot", columnDefinition = "CLOB")
    private String emitterSnapshotJson;

    @Lob
    @Basic(fetch = FetchType.EAGER)
    @Column(name = "client_snapshot", columnDefinition = "CLOB")
    private String clientSnapshotJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<ClientInvoiceLine> lines = new ArrayList<>();

    public void addLine(ClientInvoiceLine line) {
        line.setInvoice(this);
        line.setPosition(lines.size());
        lines.add(line);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
