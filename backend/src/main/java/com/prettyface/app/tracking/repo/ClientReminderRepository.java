package com.prettyface.app.tracking.repo;

import com.prettyface.app.tracking.domain.ClientReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientReminderRepository extends JpaRepository<ClientReminder, Long> {

    List<ClientReminder> findByUserIdAndSentFalseOrderByRecommendedDateAsc(Long userId);
}
