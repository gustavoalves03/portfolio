package com.prettyface.app.notification.app;

import com.prettyface.app.notification.domain.Notification;
import com.prettyface.app.notification.domain.NotificationCategory;
import com.prettyface.app.notification.domain.NotificationType;
import com.prettyface.app.notification.domain.ReferenceType;
import com.prettyface.app.notification.repo.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("countUnread returns count of unread notifications for recipient")
    void countUnread_returnsCount() {
        when(notificationRepository.countByRecipientIdAndReadFalse(1L)).thenReturn(5L);

        long count = notificationService.countUnread(1L);

        assertThat(count).isEqualTo(5L);
        verify(notificationRepository).countByRecipientIdAndReadFalse(1L);
    }

    @Test
    @DisplayName("markAsRead sets read=true and saves")
    void markAsRead_setsReadTrue() {
        Notification notification = Notification.builder()
                .id(10L)
                .recipientId(1L)
                .tenantSlug("salon-a")
                .type(NotificationType.NEW_BOOKING)
                .category(NotificationCategory.BOOKING)
                .title("New booking")
                .message("Test message")
                .referenceId(100L)
                .referenceType(ReferenceType.BOOKING)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(10L, 1L);

        assertThat(notification.isRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    @DisplayName("markAsRead throws when notification belongs to another user")
    void markAsRead_throwsWhenWrongRecipient() {
        Notification notification = Notification.builder()
                .id(10L)
                .recipientId(2L)
                .read(false)
                .build();

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        // Sec5: strengthened — assert the exact HTTP status.
        // NOTE-SEC: Current implementation returns 403 FORBIDDEN when the recipient mismatches
        // (see NotificationService.markAsRead). 403 is acceptable; a case could also be made
        // for 404 (pretending the notification doesn't exist is safer because it hides the ID
        // existence from a scanner). This assertion pins the current contract so a future
        // change is a conscious decision.
        org.springframework.web.server.ResponseStatusException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        () -> notificationService.markAsRead(10L, 1L)
                );
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Side-effect guard: save() must NOT be called for a rejected caller.
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    // ══════════════════════════════════════════════════════════════
    // ── Sec5: mark-as-read cross-user + idempotency ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Sec5: markAsRead_isIdempotent_whenCalledMultipleTimes — documents current behaviour")
    void markAsRead_isIdempotent_whenCalledMultipleTimes() {
        // TODO-SEC: markAsRead always sets read=true and calls save() — even if the notification
        // is already read. It's idempotent at the data-level (end state is identical) but save()
        // is called BOTH times. This writes an extra UPDATE, generates an audit trail entry in
        // any auditing listener, and could decrement an unread-count cache TWICE if one exists.
        // This test pins current behaviour; a future optimisation should skip save() when already
        // read AND the unread-count is NOT decremented twice.
        Notification notification = Notification.builder()
                .id(10L)
                .recipientId(1L)
                .tenantSlug("salon-a")
                .type(NotificationType.NEW_BOOKING)
                .category(NotificationCategory.BOOKING)
                .title("New booking")
                .message("Test message")
                .referenceId(100L)
                .referenceType(ReferenceType.BOOKING)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        // First call: read false → true, save called.
        notificationService.markAsRead(10L, 1L);
        assertThat(notification.isRead()).isTrue();

        // Second call: notification already read. Service still writes.
        notificationService.markAsRead(10L, 1L);

        // Documents: save is called TWICE. If later optimised, flip to times(1).
        verify(notificationRepository, times(2)).save(notification);
        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("Sec5: markAsRead_crossUser_statusIs403NotBecauseOfMissingResource")
    void markAsRead_crossUser_statusIs403() {
        // NOTE-SEC: explicitly asserts the 403 vs 404 distinction. 403 leaks existence of
        // the notification ID to a scanner. Pinning current contract.
        Notification notification = Notification.builder()
                .id(10L)
                .recipientId(99L) // belongs to someone else
                .read(false)
                .build();
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        org.springframework.web.server.ResponseStatusException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        () -> notificationService.markAsRead(10L, 1L)
                );
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getReason()).isEqualTo("Access denied");
    }

    @Test
    @DisplayName("listForRecipient with read filter delegates to filtered query")
    void listForRecipient_withReadFilter_returnsFilteredPage() {
        Page<Notification> page = new PageImpl<>(List.of());
        when(notificationRepository.findByRecipientIdAndReadOrderByCreatedAtDesc(1L, false, Pageable.unpaged()))
                .thenReturn(page);

        Page<Notification> result = notificationService.listForRecipient(1L, false, null, Pageable.unpaged());

        assertThat(result).isEqualTo(page);
        verify(notificationRepository).findByRecipientIdAndReadOrderByCreatedAtDesc(1L, false, Pageable.unpaged());
        verify(notificationRepository, never()).findByRecipientIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("listForRecipient without filter delegates to unfiltered query")
    void listForRecipient_withoutFilter_returnsAllPage() {
        Page<Notification> page = new PageImpl<>(List.of());
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(1L, Pageable.unpaged()))
                .thenReturn(page);

        Page<Notification> result = notificationService.listForRecipient(1L, null, null, Pageable.unpaged());

        assertThat(result).isEqualTo(page);
        verify(notificationRepository).findByRecipientIdOrderByCreatedAtDesc(1L, Pageable.unpaged());
        verify(notificationRepository, never()).findByRecipientIdAndReadOrderByCreatedAtDesc(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("markAsRead throws 404 when notification not found")
    void markAsRead_notificationNotFound_throws404() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        org.springframework.web.server.ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> notificationService.markAsRead(99L, 1L)
        );
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("save delegates to repository")
    void save_delegatesToRepository() {
        Notification notification = Notification.builder().recipientId(1L).build();
        when(notificationRepository.save(notification)).thenReturn(notification);

        Notification result = notificationService.save(notification);

        assertThat(result).isEqualTo(notification);
        verify(notificationRepository).save(notification);
    }

    @Test
    void listForRecipient_withSinceOnly_callsSinceQuery() {
        Instant since = Instant.parse("2026-04-16T00:00:00Z");
        Pageable pageable = PageRequest.of(0, 20);
        when(notificationRepository.findByRecipientIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(Page.empty());

        notificationService.listForRecipient(1L, null, since, pageable);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        Mockito.verify(notificationRepository)
                .findByRecipientIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(1L), captor.capture(), eq(pageable));
        assertThat(captor.getValue()).isEqualTo(LocalDateTime.of(2026, 4, 16, 0, 0, 0));
    }

    @Test
    void listForRecipient_withReadAndSince_callsCombinedQuery() {
        Instant since = Instant.parse("2026-04-16T00:00:00Z");
        Pageable pageable = PageRequest.of(0, 20);
        when(notificationRepository.findByRecipientIdAndReadAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), anyBoolean(), any(), any()))
                .thenReturn(Page.empty());

        notificationService.listForRecipient(1L, false, since, pageable);

        Mockito.verify(notificationRepository)
                .findByRecipientIdAndReadAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(1L), eq(false), any(), eq(pageable));
    }

    @Test
    void listForRecipient_withoutSince_callsExistingQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Page.empty());

        notificationService.listForRecipient(1L, null, null, pageable);

        Mockito.verify(notificationRepository).findByRecipientIdOrderByCreatedAtDesc(eq(1L), eq(pageable));
    }
}
