package com.luxpretty.app.mail.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import com.luxpretty.app.mail.vars.MailVars;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MailOutboxService {

    private final MailOutboxRepository repo;
    private final ObjectMapper objectMapper;

    public MailOutboxService(MailOutboxRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
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
        repo.save(row);
    }

    private String serialize(MailVars vars) {
        try {
            return objectMapper.writeValueAsString(vars);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize MailVars: " + vars, e);
        }
    }
}
