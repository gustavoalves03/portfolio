package com.prettyface.app.notification.repo;

import com.prettyface.app.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadOrderByCreatedAtDesc(Long recipientId, boolean read, Pageable pageable);

    long countByRecipientIdAndReadFalse(Long recipientId);

    List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(Long recipientId);
}
