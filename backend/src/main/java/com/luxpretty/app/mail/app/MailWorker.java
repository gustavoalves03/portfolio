package com.luxpretty.app.mail.app;

import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import com.luxpretty.app.users.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Polls MAIL_OUTBOX for PENDING rows and sends them via the configured MailSender.
 * Runs every 30 seconds, batch size 10.
 */
@Component
@ConditionalOnProperty(name = "app.mail.worker.enabled", havingValue = "true", matchIfMissing = true)
public class MailWorker {

    private static final Logger log = LoggerFactory.getLogger(MailWorker.class);
    private static final int BATCH_SIZE = 10;
    private static final int LAST_ERROR_MAX_LEN = 2000;

    private final MailOutboxRepository repo;
    private final MailSender sender;
    private final MailRenderer renderer;
    private final MailRetryPolicy retryPolicy;
    private final UserRepository userRepo;

    public MailWorker(MailOutboxRepository repo,
                      MailSender sender,
                      MailRenderer renderer,
                      MailRetryPolicy retryPolicy,
                      UserRepository userRepo) {
        this.repo = repo;
        this.sender = sender;
        this.renderer = renderer;
        this.retryPolicy = retryPolicy;
        this.userRepo = userRepo;
    }

    @Scheduled(fixedDelayString = "${app.mail.worker.poll-interval-ms:30000}",
               initialDelayString = "${app.mail.worker.initial-delay-ms:10000}")
    @Transactional
    public void pollAndSend() {
        List<MailOutbox> batch = repo.lockBatchForSending(LocalDateTime.now(), BATCH_SIZE);
        if (batch.isEmpty()) return;
        log.debug("Mail worker: processing {} row(s)", batch.size());

        for (MailOutbox row : batch) {
            processOne(row);
        }
    }

    private void processOne(MailOutbox row) {
        boolean blocked = userRepo.findEmailBlockedByEmail(row.getRecipientEmail()).orElse(false);
        if (blocked) {
            row.setStatus(MailStatus.PERMANENTLY_FAILED);
            row.setLastError("recipient email blocked");
            log.info("Mail skipped (recipient blocked): id={} recipient={}",
                    row.getId(), row.getRecipientEmail());
            return;
        }
        try {
            MailRenderer.Rendered envelope = renderer.render(row);
            String providerMessageId = sender.send(
                    row.getRecipientEmail(),
                    envelope.subject(),
                    envelope.htmlBody(),
                    envelope.textBody());
            row.setStatus(MailStatus.SENT);
            row.setSentAt(LocalDateTime.now());
            row.setProviderMessageId(providerMessageId);
            log.info("Mail sent: id={} template={} recipient={} providerMsgId={}",
                    row.getId(), row.getTemplate(), row.getRecipientEmail(), providerMessageId);
        } catch (RetryableMailException e) {
            row.setAttempts(row.getAttempts() + 1);
            row.setLastError(truncate(e.getMessage()));
            if (row.getAttempts() >= MailRetryPolicy.MAX_ATTEMPTS) {
                row.setStatus(MailStatus.PERMANENTLY_FAILED);
                log.warn("Mail permanently failed (max attempts): id={} template={} error={}",
                        row.getId(), row.getTemplate(), e.getMessage());
            } else {
                row.setNextAttemptAt(retryPolicy.nextAttemptAfter(row.getAttempts(), LocalDateTime.now()));
                log.warn("Mail send failed, scheduled retry: id={} attempts={} next_attempt_at={} error={}",
                        row.getId(), row.getAttempts(), row.getNextAttemptAt(), e.getMessage());
            }
        } catch (HardMailException e) {
            row.setStatus(MailStatus.PERMANENTLY_FAILED);
            row.setLastError(truncate(e.getMessage()));
            log.warn("Mail permanently failed (hard error): id={} template={} error={}",
                    row.getId(), row.getTemplate(), e.getMessage());
        } catch (Exception e) {
            row.setStatus(MailStatus.PERMANENTLY_FAILED);
            row.setLastError(truncate("unexpected: " + e.getMessage()));
            log.error("Mail unexpected failure: id={} template={}",
                    row.getId(), row.getTemplate(), e);
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > LAST_ERROR_MAX_LEN ? s.substring(0, LAST_ERROR_MAX_LEN) : s;
    }
}
