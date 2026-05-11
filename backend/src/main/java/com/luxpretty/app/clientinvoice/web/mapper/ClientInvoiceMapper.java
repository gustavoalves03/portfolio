package com.luxpretty.app.clientinvoice.web.mapper;

import com.luxpretty.app.clientinvoice.domain.ClientInvoice;
import com.luxpretty.app.clientinvoice.domain.ClientInvoiceLine;
import com.luxpretty.app.clientinvoice.web.dto.ClientInvoiceLineResponse;
import com.luxpretty.app.clientinvoice.web.dto.ClientInvoiceResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClientInvoiceMapper {

    public ClientInvoiceResponse toResponse(ClientInvoice i) {
        List<ClientInvoiceLineResponse> lines = i.getLines().stream().map(this::toLine).toList();
        return new ClientInvoiceResponse(
                i.getId(),
                i.getNumberLabel(),
                i.getIssuedAt(),
                i.getKind(),
                i.getAmountSubtotal(),
                i.getAmountTax(),
                i.getAmountTotal(),
                i.getCurrency(),
                i.getTaxRate(),
                i.getStatus(),
                i.getBookingId(),
                i.getClientUserId(),
                lines
        );
    }

    private ClientInvoiceLineResponse toLine(ClientInvoiceLine l) {
        return new ClientInvoiceLineResponse(
                l.getId(), l.getDescription(), l.getQuantity(), l.getUnitPriceHt(), l.getTotalHt(), l.getPosition()
        );
    }
}
