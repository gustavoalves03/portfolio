package com.luxpretty.app.proinvoice.app;

import com.luxpretty.app.invoice.pdf.HtmlToPdfRenderer;
import com.luxpretty.app.proinvoice.domain.ProInvoice;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ProInvoicePdfRenderer {

    private final HtmlToPdfRenderer html;

    public ProInvoicePdfRenderer(HtmlToPdfRenderer html) {
        this.html = html;
    }

    public byte[] render(ProInvoice invoice) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("invoice", invoice);
        ctx.put("customer", Map.of(
                "name", "Salon (snapshot à brancher)",
                "addressLine1", "",
                "addressLine2", ""
        ));
        return html.render("invoice/pro-invoice", ctx);
    }
}
