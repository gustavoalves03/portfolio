package com.luxpretty.app.mail.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

@Configuration
public class MailTemplateConfig {

    /**
     * Additional Thymeleaf resolver in TEXT mode so the engine can process
     * both `.html` (default HTML resolver) and `.txt` templates from the same
     * `mail/` folder. Order is high so the default HTML resolver wins for
     * `.html` lookups; this resolver kicks in when the suffix is `.txt`.
     */
    @Bean
    SpringResourceTemplateResolver mailTextTemplateResolver() {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setPrefix("classpath:/templates/");
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setOrder(100);  // after default HTML resolver
        resolver.setCheckExistence(true);
        return resolver;
    }
}
