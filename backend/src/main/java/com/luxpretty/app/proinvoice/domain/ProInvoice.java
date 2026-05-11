package com.luxpretty.app.proinvoice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "PRO_INVOICES", uniqueConstraints = {
        @UniqueConstraint(name = "UK_PRO_INVOICES_STRIPE_ID", columnNames = "stripe_invoice_id")
})
public class ProInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stripe_invoice_id")
    private String stripeInvoiceId;

    @Column(name = "number_label", nullable = false, length = 64)
    private String numberLabel;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

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
    private ProInvoiceStatus status;

    @Column(name = "hosted_invoice_url", length = 1024)
    private String hostedInvoiceUrl;

    @Column(name = "pdf_url", length = 1024)
    private String pdfUrl;

    @Lob
    @Basic(fetch = FetchType.EAGER)
    @Column(name = "customer_snapshot", columnDefinition = "CLOB")
    private String customerSnapshotJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
