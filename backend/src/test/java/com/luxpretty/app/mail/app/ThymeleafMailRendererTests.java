package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.vars.ResetPasswordVars;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = { ThymeleafMailRenderer.class, MailRendererTestConfig.class })
@ActiveProfiles("test")
class ThymeleafMailRendererTests {

    @Autowired ThymeleafMailRenderer renderer;

    @Test
    void renders_reset_password_with_vars_inlined() throws Exception {
        ObjectMapper om = new ObjectMapper();
        ResetPasswordVars vars = new ResetPasswordVars("Alice", "https://app/reset?token=xyz");

        MailOutbox row = new MailOutbox();
        row.setTemplate(MailTemplate.RESET_PASSWORD);
        row.setVarsJson(om.writeValueAsString(vars));

        MailRenderer.Rendered out = renderer.render(row);

        assertThat(out.subject()).contains("Réinitialiser");
        assertThat(out.htmlBody()).contains("Alice");
        assertThat(out.htmlBody()).contains("https://app/reset?token=xyz");
        // CSS should be inlined (style attribute present on at least one element)
        assertThat(out.htmlBody()).containsPattern("style=\"[^\"]*color");
        assertThat(out.textBody()).contains("Alice");
        assertThat(out.textBody()).contains("https://app/reset?token=xyz");
    }
}
