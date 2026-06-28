package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.vars.BookingConfirmedVars;
import com.luxpretty.app.mail.vars.BookingReceivedProVars;
import com.luxpretty.app.mail.vars.BookingReminderVars;
import com.luxpretty.app.mail.vars.BookingRescheduledVars;
import com.luxpretty.app.mail.vars.InvoicePaidVars;
import com.luxpretty.app.mail.vars.MailVars;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component("thymeleafMailRenderer")
@Primary
public class ThymeleafMailRenderer implements MailRenderer {

    private final SpringTemplateEngine templateEngine;
    private final ObjectMapper objectMapper;
    private final String stylesCss;

    public ThymeleafMailRenderer(SpringTemplateEngine templateEngine, ObjectMapper objectMapper) {
        this.templateEngine = templateEngine;
        this.objectMapper = objectMapper;
        this.stylesCss = loadStyles();
    }

    @Override
    public Rendered render(MailOutbox row) {
        MailVars vars = deserialize(row);
        Map<String, Object> ctxVars = toContextMap(vars);

        // Explicit suffixes so each call hits the right resolver:
        //  - "<name>.html" → Spring Boot's default HTML resolver
        //  - "<name>.txt"  → mailTextTemplateResolver (TEXT mode, restricted to *.txt)
        // Without explicit suffixes the .txt resolver hijacks both calls
        // (lower order wins) and the HTML body comes back as stripped text.
        String base = "mail/" + row.getTemplate().templatePath();

        Context htmlCtx = new Context();
        ctxVars.forEach(htmlCtx::setVariable);
        String html = templateEngine.process(base + ".html", htmlCtx);
        String inlined = inlineCss(html);

        Context txtCtx = new Context();
        ctxVars.forEach(txtCtx::setVariable);
        String txt = templateEngine.process(base + ".txt", txtCtx);

        String subject = subjectFor(row.getTemplate(), vars);
        return new Rendered(subject, inlined, txt);
    }

    private String inlineCss(String html) {
        Document doc = Jsoup.parse(html);
        Element head = doc.head();
        if (head.selectFirst("style") == null && stylesCss != null && !stylesCss.isBlank()) {
            // Use html() to set content as a data node so data() can read it back
            head.appendElement("style").html(stylesCss);
        }
        applyInlineStyles(doc);
        return doc.outerHtml();
    }

    private void applyInlineStyles(Document doc) {
        Elements styleBlocks = doc.select("style");
        for (Element styleBlock : styleBlocks) {
            // data() returns data nodes (set via html()); fall back to html() for compatibility
            String css = styleBlock.data().isEmpty() ? styleBlock.html() : styleBlock.data();
            for (String rule : css.split("\\}")) {
                int braceIdx = rule.indexOf('{');
                if (braceIdx < 0) continue;
                String selector = rule.substring(0, braceIdx).trim();
                String declarations = rule.substring(braceIdx + 1).trim();
                if (selector.isEmpty() || declarations.isEmpty()) continue;
                if (selector.startsWith("@") || selector.contains(":")) continue;
                try {
                    Elements targets = doc.select(selector);
                    for (Element el : targets) {
                        String existing = el.attr("style");
                        el.attr("style", existing.isEmpty()
                                ? declarations
                                : existing + ";" + declarations);
                    }
                } catch (Exception ignored) {
                    // invalid selector, skip
                }
            }
        }
    }

    private MailVars deserialize(MailOutbox row) {
        try {
            return (MailVars) objectMapper.readValue(row.getVarsJson(), row.getTemplate().varsClass());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot deserialize MailVars for row id=" + row.getId(), e);
        }
    }

    private Map<String, Object> toContextMap(MailVars vars) {
        return objectMapper.convertValue(vars, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    private String subjectFor(MailTemplate template, MailVars vars) {
        return switch (template) {
            case RESET_PASSWORD -> "Réinitialiser votre mot de passe";
            case BOOKING_CONFIRMED -> {
                BookingConfirmedVars v = (BookingConfirmedVars) vars;
                yield "Votre rendez-vous chez " + v.salonName() + " est confirmé";
            }
            case BOOKING_RECEIVED_PRO -> {
                BookingReceivedProVars v = (BookingReceivedProVars) vars;
                yield "Nouveau rendez-vous — " + v.clientName();
            }
            case WELCOME_PRO -> "Bienvenue sur LuxPretty";
            case INVOICE_PAID -> {
                InvoicePaidVars v = (InvoicePaidVars) vars;
                yield "Paiement reçu — " + v.amountFormatted();
            }
            case INVOICE_PAYMENT_FAILED -> "Un souci avec votre paiement — LuxPretty";
            case TRIAL_ENDING -> "Votre essai gratuit se termine bientôt";
            case VERIFY_EMAIL -> "Vérifie ton email LuxPretty";
            case BOOKING_REMINDER_J1 -> {
                BookingReminderVars v = (BookingReminderVars) vars;
                yield "Rappel : ton RDV demain à " + v.timeStr();
            }
            case BOOKING_RESCHEDULED -> {
                BookingRescheduledVars v = (BookingRescheduledVars) vars;
                yield "Votre rendez-vous chez " + v.salonName() + " a été reprogrammé";
            }
        };
    }

    private String loadStyles() {
        try {
            return new String(
                    new ClassPathResource("templates/mail/_styles.css").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
