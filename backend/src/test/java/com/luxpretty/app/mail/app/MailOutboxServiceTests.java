package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.mail.vars.ResetPasswordVars;
import com.luxpretty.app.mail.vars.WelcomeProVars;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailOutboxServiceTests {

    private MailOutboxRepository repo;
    private NamedParameterJdbcOperations jdbc;
    private MailOutboxService service;

    @BeforeEach
    void setUp() {
        repo = mock(MailOutboxRepository.class);
        jdbc = mock(NamedParameterJdbcOperations.class);
        when(repo.save(any(MailOutbox.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service = new MailOutboxService(repo, new ObjectMapper(), jdbc, "appuser");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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

    @Test
    void queue_with_active_tenant_context_inserts_into_shared_schema_via_jdbc() {
        TenantContext.setCurrentTenant("salon-bob");
        ResetPasswordVars vars = new ResetPasswordVars("Alice", "https://app/reset?token=xyz");

        service.queue(MailTemplate.RESET_PASSWORD, vars, "alice@example.com", "salon-bob");

        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("\"APPUSER\".MAIL_OUTBOX"), captor.capture());
        verify(repo, never()).save(any(MailOutbox.class));
        SqlParameterSource params = captor.getValue();
        assertThat(params.getValue("template")).isEqualTo("RESET_PASSWORD");
        assertThat(params.getValue("recipientEmail")).isEqualTo("alice@example.com");
        assertThat(params.getValue("tenantSlug")).isEqualTo("salon-bob");
        assertThat(params.getValue("status")).isEqualTo("PENDING");
        assertThat(params.getValue("attempts")).isEqualTo(0);
        assertThat(params.getValue("createdAt")).isNotNull();
        assertThat(params.getValue("nextAttemptAt")).isNotNull();
    }
}
