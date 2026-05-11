package com.luxpretty.app.clientinvoice.web.dto;

import java.math.BigDecimal;

public record ClientInvoiceLineResponse(
        Long id,
        String description,
        BigDecimal quantity,
        BigDecimal unitPriceHt,
        BigDecimal totalHt,
        Integer position
) {}
