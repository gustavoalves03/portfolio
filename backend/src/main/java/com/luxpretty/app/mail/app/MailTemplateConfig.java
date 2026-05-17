package com.luxpretty.app.mail.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.Set;

@Configuration
public class MailTemplateConfig {

    /**
     * Additional Thymeleaf resolver in TEXT mode so the engine can process
     * both `.html` (default HTML resolver, provided by Spring Boot) and
     * `.txt` templates from the same `mail/` folder.
     *
     * <p>Resolver wiring contract (must stay in sync with
     * {@link ThymeleafMailRenderer} which calls
     * {@code templateEngine.process("mail/foo.html"|".txt", ctx)}):
     *
     * <ul>
     *   <li>{@code resolvablePatterns = "*.txt"} → this resolver only kicks in
     *       when the template name ends with {@code .txt}; otherwise it
     *       defers to the default HTML resolver. Without this filter, the
     *       {@code .txt} resolver hijacks suffix-less lookups (lower order
     *       wins) and the HTML render comes back in TEXT mode (no layout,
     *       just stripped text).</li>
     *   <li>{@code suffix = ""} because the renderer already passes the
     *       explicit {@code .txt} suffix.</li>
     * </ul>
     */
    @Bean
    SpringResourceTemplateResolver mailTextTemplateResolver() {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setPrefix("classpath:/templates/");
        resolver.setSuffix("");
        resolver.setResolvablePatterns(Set.of("*.txt"));
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setOrder(100);
        resolver.setCheckExistence(true);
        return resolver;
    }
}
