package com.luxpretty.app.proinvoice.web.mapper;

import com.luxpretty.app.proinvoice.domain.ProInvoice;
import com.luxpretty.app.proinvoice.web.dto.ProInvoiceResponse;
import org.springframework.stereotype.Component;

@Component
public class ProInvoiceMapper {
    public ProInvoiceResponse toResponse(ProInvoice i) {
        return new ProInvoiceResponse(
                i.getId(),
                i.getNumberLabel(),
                i.getIssuedAt(),
                i.getPeriodStart(),
                i.getPeriodEnd(),
                i.getAmountSubtotal(),
                i.getAmountTax(),
                i.getAmountTotal(),
                i.getCurrency(),
                i.getTaxRate(),
                i.getStatus(),
                i.getHostedInvoiceUrl()
        );
    }
}
