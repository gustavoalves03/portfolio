package com.luxpretty.app.mail.app;

import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailWorkerTests {

    private MailOutboxRepository repo;
    private MailSender sender;
    private MailRenderer renderer;
    private MailRetryPolicy retryPolicy;
    private MailWorker worker;

    @BeforeEach
    void setUp() {
        repo = mock(MailOutboxRepository.class);
        sender = mock(MailSender.class);
        renderer = mock(MailRenderer.class);
        retryPolicy = new MailRetryPolicy();
        worker = new MailWorker(repo, sender, renderer, retryPolicy);

        when(renderer.render(any())).thenReturn(
                new MailRenderer.Rendered("subj", "<p>html</p>", "txt"));
    }

    @Test
    void success_marks_sent_with_provider_id() {
        MailOutbox row = pending();
        when(repo.lockBatchForSending(any(), anyInt())).thenReturn(List.of(row));
        when(sender.send("user@example.com", "subj", "<p>html</p>", "txt")).thenReturn("postmark-msg-id-123");

        worker.pollAndSend();

        assertThat(row.getStatus()).isEqualTo(MailStatus.SENT);
        assertThat(row.getProviderMessageId()).isEqualTo("postmark-msg-id-123");
        assertThat(row.getSentAt()).isNotNull();
    }

    @Test
    void retryable_failure_increments_attempts_and_schedules_next() {
        MailOutbox row = pending();
        LocalDateTime before = LocalDateTime.now();
        when(repo.lockBatchForSending(any(), anyInt())).thenReturn(List.of(row));
        when(sender.send(any(), any(), any(), any()))
                .thenThrow(new RetryableMailException("timeout"));

        worker.pollAndSend();

        assertThat(row.getStatus()).isEqualTo(MailStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isAfter(before);
        assertThat(row.getLastError()).contains("timeout");
    }

    @Test
    void fifth_attempt_marks_permanently_failed() {
        MailOutbox row = pending();
        row.setAttempts(4);
        when(repo.lockBatchForSending(any(), anyInt())).thenReturn(List.of(row));
        when(sender.send(any(), any(), any(), any()))
                .thenThrow(new RetryableMailException("still down"));

        worker.pollAndSend();

        assertThat(row.getStatus()).isEqualTo(MailStatus.PERMANENTLY_FAILED);
        assertThat(row.getAttempts()).isEqualTo(5);
    }

    @Test
    void hard_failure_marks_permanently_failed_immediately() {
        MailOutbox row = pending();
        when(repo.lockBatchForSending(any(), anyInt())).thenReturn(List.of(row));
        when(sender.send(any(), any(), any(), any()))
                .thenThrow(new HardMailException("invalid email"));

        worker.pollAndSend();

        assertThat(row.getStatus()).isEqualTo(MailStatus.PERMANENTLY_FAILED);
        assertThat(row.getAttempts()).isEqualTo(0);
        assertThat(row.getLastError()).contains("invalid email");
    }

    @Test
    void batch_size_is_10() {
        worker.pollAndSend();
        verify(repo, times(1)).lockBatchForSending(any(), eq(10));
    }

    private MailOutbox pending() {
        MailOutbox row = new MailOutbox();
        row.setTemplate(MailTemplate.RESET_PASSWORD);
        row.setRecipientEmail("user@example.com");
        row.setVarsJson("{\"userName\":\"u\",\"resetUrl\":\"http://x\"}");
        row.setStatus(MailStatus.PENDING);
        row.setAttempts(0);
        row.setNextAttemptAt(LocalDateTime.now());
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }
}
