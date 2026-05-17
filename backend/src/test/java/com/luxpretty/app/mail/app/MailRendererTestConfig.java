package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Set;

@TestConfiguration
public class MailRendererTestConfig {

    /**
     * Mirrors the prod wiring contract: the TXT resolver is restricted to
     * {@code *.txt} so it does not hijack HTML lookups, and the renderer
     * passes explicit ".html"/".txt" suffixes in the template name (see
     * {@link ThymeleafMailRenderer}). The HTML resolver is left unrestricted
     * so fragment includes like {@code ~{mail/_layout :: html}} still resolve
     * (it appends {@code .html} when missing).
     */
    @Bean
    SpringTemplateEngine templateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver html = new ClassLoaderTemplateResolver();
        html.setPrefix("templates/");
        html.setSuffix(".html");
        html.setTemplateMode("HTML");
        html.setCharacterEncoding("UTF-8");
        html.setCheckExistence(true);
        html.setOrder(2); // fallback (after the txt resolver narrows out *.txt)

        ClassLoaderTemplateResolver text = new ClassLoaderTemplateResolver();
        text.setPrefix("templates/");
        text.setSuffix("");
        text.setResolvablePatterns(Set.of("*.txt"));
        text.setTemplateMode("TEXT");
        text.setCharacterEncoding("UTF-8");
        text.setCheckExistence(true);
        text.setOrder(1);

        engine.addTemplateResolver(html);
        engine.addTemplateResolver(text);
        return engine;
    }

    @Bean
    ObjectMapper objectMapper() { return new ObjectMapper(); }
}
