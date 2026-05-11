package com.luxpretty.app.invoice.pdf;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.util.Map;

@Component
public class HtmlToPdfRenderer {

    private final SpringTemplateEngine templateEngine;

    public HtmlToPdfRenderer(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public byte[] render(String templateName, Map<String, Object> variables) {
        Context ctx = new Context();
        variables.forEach(ctx::setVariable);

        String html = templateEngine.process(templateName, ctx);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF render failed for template " + templateName, e);
        }
    }
}
