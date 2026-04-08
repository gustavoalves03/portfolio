package com.prettyface.app.notification.app;

import com.prettyface.app.notification.domain.Notification;
import com.prettyface.app.notification.domain.NotificationCategory;
import com.prettyface.app.notification.domain.NotificationType;
import com.prettyface.app.notification.domain.ReferenceType;
import com.prettyface.app.notification.repo.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> notificationService.markAsRead(10L, 1L)
        );
    }

    @Test
    @DisplayName("listForRecipient with read filter delegates to filtered query")
    void listForRecipient_withReadFilter_returnsFilteredPage() {
        Page<Notification> page = new PageImpl<>(List.of());
        when(notificationRepository.findByRecipientIdAndReadOrderByCreatedAtDesc(1L, false, Pageable.unpaged()))
                .thenReturn(page);

        Page<Notification> result = notificationService.listForRecipient(1L, false, Pageable.unpaged());

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

        Page<Notification> result = notificationService.listForRecipient(1L, null, Pageable.unpaged());

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
}
