package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import com.luxpretty.app.mail.vars.MailVars;
import com.luxpretty.app.multitenancy.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class MailOutboxService {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Z][A-Z0-9_]*");

    private final MailOutboxRepository repo;
    private final ObjectMapper objectMapper;
    private final NamedParameterJdbcOperations jdbc;
    private final String insertIntoSharedOutboxSql;

    public MailOutboxService(MailOutboxRepository repo,
                             ObjectMapper objectMapper,
                             NamedParameterJdbcOperations jdbc,
                             @Value("${app.multitenancy.application-schema:${APP_USER:appuser}}")
                             String applicationSchema) {
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.insertIntoSharedOutboxSql = """
                INSERT INTO %s.MAIL_OUTBOX (
                    TEMPLATE,
                    RECIPIENT_EMAIL,
                    TENANT_SLUG,
                    VARS_JSON,
                    STATUS,
                    ATTEMPTS,
                    NEXT_ATTEMPT_AT,
                    LAST_ERROR,
                    PROVIDER_MESSAGE_ID,
                    CREATED_AT,
                    SENT_AT,
                    DELIVERED_AT
                ) VALUES (
                    :template,
                    :recipientEmail,
                    :tenantSlug,
                    :varsJson,
                    :status,
                    :attempts,
                    :nextAttemptAt,
                    :lastError,
                    :providerMessageId,
                    :createdAt,
                    :sentAt,
                    :deliveredAt
                )
                """.formatted(qualifySchema(applicationSchema));
    }

    /**
     * Inserts a mail in the outbox. Joins the caller's transaction (REQUIRED):
     * if the caller rolls back, the mail is not queued — guarantees atomicity
     * between business action and mail send.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void queue(MailTemplate template, MailVars vars, String recipientEmail, String tenantSlug) {
        if (!template.varsClass().isInstance(vars)) {
            throw new IllegalArgumentException(
                "Template " + template + " expects " + template.varsClass().getSimpleName()
                + " but got " + vars.getClass().getSimpleName());
        }

        MailOutbox row = new MailOutbox();
        row.setTemplate(template);
        row.setRecipientEmail(recipientEmail);
        row.setTenantSlug(tenantSlug);
        row.setVarsJson(serialize(vars));
        row.setStatus(MailStatus.PENDING);
        row.setAttempts(0);
        row.setNextAttemptAt(LocalDateTime.now());

        // In tenant-scoped flows (e.g. public booking), the current Hibernate
        // session points at the tenant schema. MAIL_OUTBOX lives in the shared
        // application schema, so we use a schema-qualified JDBC insert that
        // still participates in the caller's transaction.
        if (TenantContext.getCurrentTenant() != null) {
            insertIntoSharedOutbox(row);
            return;
        }

        repo.save(row);
    }

    private String serialize(MailVars vars) {
        try {
            return objectMapper.writeValueAsString(vars);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize MailVars: " + vars, e);
        }
    }

    private void insertIntoSharedOutbox(MailOutbox row) {
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(LocalDateTime.now());
        }
        if (row.getNextAttemptAt() == null) {
            row.setNextAttemptAt(row.getCreatedAt());
        }

        jdbc.update(insertIntoSharedOutboxSql, new MapSqlParameterSource()
                .addValue("template", row.getTemplate().name())
                .addValue("recipientEmail", row.getRecipientEmail())
                .addValue("tenantSlug", row.getTenantSlug())
                .addValue("varsJson", row.getVarsJson())
                .addValue("status", row.getStatus().name())
                .addValue("attempts", row.getAttempts())
                .addValue("nextAttemptAt", row.getNextAttemptAt())
                .addValue("lastError", row.getLastError())
                .addValue("providerMessageId", row.getProviderMessageId())
                .addValue("createdAt", row.getCreatedAt())
                .addValue("sentAt", row.getSentAt())
                .addValue("deliveredAt", row.getDeliveredAt()));
    }

    private static String qualifySchema(String schema) {
        String normalized = schema == null ? "" : schema.trim().toUpperCase(Locale.ROOT);
        if (!SQL_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid application schema name: " + schema);
        }
        return "\"" + normalized + "\"";
    }
}
