package com.luxpretty.app.clientinvoice.app;

import com.luxpretty.app.clientinvoice.domain.ClientInvoice;
import com.luxpretty.app.clientinvoice.domain.ClientInvoiceKind;
import com.luxpretty.app.clientinvoice.domain.ClientInvoiceLine;
import com.luxpretty.app.clientinvoice.domain.ClientInvoiceStatus;
import com.luxpretty.app.invoice.pdf.HtmlToPdfRenderer;
import com.luxpretty.app.invoice.pdf.PdfTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = { HtmlToPdfRenderer.class, ClientInvoicePdfRenderer.class, PdfTestConfig.class })
@ActiveProfiles("test")
class ClientInvoicePdfRendererTests {

    @Autowired ClientInvoicePdfRenderer renderer;

    @Test
    void renders_real_client_invoice_to_pdf() {
        ClientInvoice inv = new ClientInvoice();
        inv.setNumberLabel("DEMO-2026-0042");
        inv.setIssuedAt(LocalDateTime.now());
        inv.setKind(ClientInvoiceKind.NO_SHOW_FEE);
        inv.setStatus(ClientInvoiceStatus.PAID);
        inv.setAmountSubtotal(new BigDecimal("25.00"));
        inv.setAmountTax(new BigDecimal("4.25"));
        inv.setAmountTotal(new BigDecimal("29.25"));
        inv.setTaxRate(new BigDecimal("17.00"));
        inv.setCurrency("EUR");

        ClientInvoiceLine line = new ClientInvoiceLine();
        line.setDescription("Frais de non-présentation");
        line.setQuantity(new BigDecimal("1.00"));
        line.setUnitPriceHt(new BigDecimal("25.00"));
        line.setTotalHt(new BigDecimal("25.00"));
        inv.addLine(line);

        byte[] pdf = renderer.render(inv);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(500);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
