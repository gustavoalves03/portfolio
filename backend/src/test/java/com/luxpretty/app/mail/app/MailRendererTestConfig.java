package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@TestConfiguration
public class MailRendererTestConfig {

    @Bean
    SpringTemplateEngine templateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver html = new ClassLoaderTemplateResolver();
        html.setPrefix("templates/");
        html.setSuffix(".html");
        html.setTemplateMode("HTML");
        html.setCharacterEncoding("UTF-8");
        html.setCheckExistence(true);
        html.setOrder(1);

        ClassLoaderTemplateResolver text = new ClassLoaderTemplateResolver();
        text.setPrefix("templates/");
        text.setSuffix(".txt");
        text.setTemplateMode("TEXT");
        text.setCharacterEncoding("UTF-8");
        text.setCheckExistence(true);
        text.setOrder(2);

        engine.addTemplateResolver(html);
        engine.addTemplateResolver(text);
        return engine;
    }

    @Bean
    ObjectMapper objectMapper() { return new ObjectMapper(); }
}
