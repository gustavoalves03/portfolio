package com.luxpretty.app.invoice.pdf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = { HtmlToPdfRenderer.class, PdfTestConfig.class })
@ActiveProfiles("test")
class HtmlToPdfRendererTests {

    @Autowired HtmlToPdfRenderer renderer;

    @Test
    void renders_pdf_from_template() {
        byte[] pdf = renderer.render("invoice/_test/minimal",
                Map.of("title", "Hello", "body", "World"));
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
