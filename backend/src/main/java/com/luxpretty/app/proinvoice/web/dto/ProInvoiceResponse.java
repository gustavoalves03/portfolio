package com.luxpretty.app.proinvoice.web.dto;

import com.luxpretty.app.proinvoice.domain.ProInvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProInvoiceResponse(
        Long id,
        String numberLabel,
        LocalDateTime issuedAt,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal amountSubtotal,
        BigDecimal amountTax,
        BigDecimal amountTotal,
        String currency,
        BigDecimal taxRate,
        ProInvoiceStatus status,
        String hostedInvoiceUrl
) {}
