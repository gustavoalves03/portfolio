package com.luxpretty.app.clientinvoice.web.dto;

import com.luxpretty.app.clientinvoice.domain.ClientInvoiceKind;
import com.luxpretty.app.clientinvoice.domain.ClientInvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ClientInvoiceResponse(
        Long id,
        String numberLabel,
        LocalDateTime issuedAt,
        ClientInvoiceKind kind,
        BigDecimal amountSubtotal,
        BigDecimal amountTax,
        BigDecimal amountTotal,
        String currency,
        BigDecimal taxRate,
        ClientInvoiceStatus status,
        Long bookingId,
        Long clientUserId,
        List<ClientInvoiceLineResponse> lines
) {}
