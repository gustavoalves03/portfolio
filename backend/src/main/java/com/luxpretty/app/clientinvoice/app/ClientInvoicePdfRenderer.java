package com.luxpretty.app.clientinvoice.app;

import com.luxpretty.app.clientinvoice.domain.ClientInvoice;
import com.luxpretty.app.invoice.pdf.HtmlToPdfRenderer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ClientInvoicePdfRenderer {

    private final HtmlToPdfRenderer html;

    public ClientInvoicePdfRenderer(HtmlToPdfRenderer html) {
        this.html = html;
    }

    public byte[] render(ClientInvoice invoice) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("invoice", invoice);

        // Snapshots seront désérialisés plus tard depuis emitterSnapshotJson / clientSnapshotJson.
        // En attendant Stripe, on injecte des placeholders neutres.
        ctx.put("emitter", Map.of(
                "name", "Salon (à compléter)",
                "addressLine1", "",
                "addressLine2", "",
                "vatNumber", "—",
                "rcs", "—"
        ));
        ctx.put("client", Map.of(
                "name", "Client",
                "addressLine1", "",
                "addressLine2", ""
        ));

        return html.render("invoice/client-invoice", ctx);
    }
}
