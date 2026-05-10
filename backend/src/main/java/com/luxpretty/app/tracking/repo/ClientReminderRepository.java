package com.luxpretty.app.tracking.repo;

import com.luxpretty.app.tracking.domain.ClientReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientReminderRepository extends JpaRepository<ClientReminder, Long> {

    List<ClientReminder> findByUserIdAndSentFalseOrderByRecommendedDateAsc(Long userId);
}
