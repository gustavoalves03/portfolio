package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import com.luxpretty.app.mail.vars.ResetPasswordVars;
import com.luxpretty.app.mail.vars.WelcomeProVars;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailOutboxServiceTests {

    private MailOutboxRepository repo;
    private MailOutboxService service;

    @BeforeEach
    void setUp() {
        repo = mock(MailOutboxRepository.class);
        when(repo.save(org.mockito.ArgumentMatchers.any(MailOutbox.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service = new MailOutboxService(repo, new ObjectMapper());
    }

    @Test
    void queue_inserts_pending_row_with_serialized_vars() {
        ResetPasswordVars vars = new ResetPasswordVars("Alice", "https://app/reset?token=xyz");

        service.queue(MailTemplate.RESET_PASSWORD, vars, "alice@example.com", null);

        ArgumentCaptor<MailOutbox> captor = ArgumentCaptor.forClass(MailOutbox.class);
        verify(repo).save(captor.capture());
        MailOutbox saved = captor.getValue();
        assertThat(saved.getTemplate()).isEqualTo(MailTemplate.RESET_PASSWORD);
        assertThat(saved.getRecipientEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getStatus()).isEqualTo(MailStatus.PENDING);
        assertThat(saved.getAttempts()).isEqualTo(0);
        assertThat(saved.getTenantSlug()).isNull();
        assertThat(saved.getVarsJson()).contains("Alice");
        assertThat(saved.getVarsJson()).contains("https://app/reset?token=xyz");
    }

    @Test
    void queue_with_tenant_slug_persists_it() {
        WelcomeProVars vars = new WelcomeProVars("Bob", "bob@salon.fr", "https://app/pro/dashboard");

        service.queue(MailTemplate.WELCOME_PRO, vars, "bob@salon.fr", "salon-bob");

        ArgumentCaptor<MailOutbox> captor = ArgumentCaptor.forClass(MailOutbox.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getTenantSlug()).isEqualTo("salon-bob");
    }

    @Test
    void queue_rejects_mismatched_vars_type() {
        WelcomeProVars wrong = new WelcomeProVars("x", "y", "z");
        assertThatThrownBy(() ->
                service.queue(MailTemplate.RESET_PASSWORD, wrong, "alice@example.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RESET_PASSWORD")
                .hasMessageContaining("WelcomeProVars");
    }
}
