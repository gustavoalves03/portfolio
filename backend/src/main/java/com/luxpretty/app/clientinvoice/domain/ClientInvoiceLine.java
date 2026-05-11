package com.luxpretty.app.clientinvoice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "CLIENT_INVOICE_LINES")
public class ClientInvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false,
                foreignKey = @ForeignKey(name = "FK_CLIENT_INVOICE_LINE_INV"))
    private ClientInvoice invoice;

    @Column(name = "description", nullable = false, length = 1024)
    private String description;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price_ht", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceHt;

    @Column(name = "total_ht", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalHt;

    @Column(name = "position", nullable = false)
    private Integer position;
}
