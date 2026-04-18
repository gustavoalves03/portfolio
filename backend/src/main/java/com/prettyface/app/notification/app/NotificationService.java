package com.prettyface.app.notification.app;

import com.prettyface.app.notification.domain.Notification;
import com.prettyface.app.notification.repo.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public Page<Notification> listForRecipient(Long recipientId, Boolean read, Instant since, Pageable pageable) {
        LocalDateTime sinceLdt = since != null
                ? LocalDateTime.ofInstant(since, ZoneOffset.UTC)
                : null;

        if (read != null && sinceLdt != null) {
            return notificationRepository.findByRecipientIdAndReadAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                    recipientId, read, sinceLdt, pageable);
        }
        if (read != null) {
            return notificationRepository.findByRecipientIdAndReadOrderByCreatedAtDesc(recipientId, read, pageable);
        }
        if (sinceLdt != null) {
            return notificationRepository.findByRecipientIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                    recipientId, sinceLdt, pageable);
        }
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long recipientId) {
        return notificationRepository.countByRecipientIdAndReadFalse(recipientId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long recipientId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.getRecipientId().equals(recipientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public Notification save(Notification notification) {
        return notificationRepository.save(notification);
    }
}
