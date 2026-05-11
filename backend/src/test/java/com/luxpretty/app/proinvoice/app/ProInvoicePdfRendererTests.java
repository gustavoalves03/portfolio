package com.luxpretty.app.proinvoice.app;

import com.luxpretty.app.invoice.pdf.HtmlToPdfRenderer;
import com.luxpretty.app.invoice.pdf.PdfTestConfig;
import com.luxpretty.app.proinvoice.domain.ProInvoice;
import com.luxpretty.app.proinvoice.domain.ProInvoiceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = { HtmlToPdfRenderer.class, ProInvoicePdfRenderer.class, PdfTestConfig.class })
@ActiveProfiles("test")
class ProInvoicePdfRendererTests {

    @Autowired ProInvoicePdfRenderer renderer;

    @Test
    void renders_pro_invoice_to_pdf() {
        ProInvoice inv = new ProInvoice();
        inv.setNumberLabel("PRO-2026-0042");
        inv.setIssuedAt(LocalDateTime.now());
        inv.setPeriodStart(LocalDate.of(2026, 5, 1));
        inv.setPeriodEnd(LocalDate.of(2026, 5, 31));
        inv.setStatus(ProInvoiceStatus.PAID);
        inv.setAmountSubtotal(new BigDecimal("59.00"));
        inv.setAmountTax(new BigDecimal("10.03"));
        inv.setAmountTotal(new BigDecimal("69.03"));
        inv.setTaxRate(new BigDecimal("17.00"));
        inv.setCurrency("EUR");

        byte[] pdf = renderer.render(inv);

        assertThat(pdf.length).isGreaterThan(500);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
