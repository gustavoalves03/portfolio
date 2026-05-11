package com.luxpretty.app.mail.app;

import com.luxpretty.app.mail.domain.MailOutbox;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Stub renderer used in PR1 (before real Thymeleaf templates ship in PR2).
 * Disabled automatically when {@code ThymeleafMailRenderer} becomes available.
 */
@Component
@ConditionalOnMissingBean(name = "thymeleafMailRenderer")
public class StubMailRenderer implements MailRenderer {
    @Override
    public Rendered render(MailOutbox row) {
        String subject = "[" + row.getTemplate() + "]";
        String body = "Mail template not yet rendered (PR1 stub). Vars: " + row.getVarsJson();
        return new Rendered(subject, "<p>" + body + "</p>", body);
    }
}
