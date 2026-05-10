package com.luxpretty.app.tracking.repo;

import com.luxpretty.app.tracking.domain.SalonClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SalonClientRepository extends JpaRepository<SalonClient, Long> {

    Optional<SalonClient> findByUserId(Long userId);

    List<SalonClient> findByPhoneAndManualTrueAndUserIdIsNull(String phone);

    @Query("SELECT c FROM SalonClient c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR c.phone LIKE CONCAT('%', :query, '%')")
    List<SalonClient> search(String query);

    List<SalonClient> findTop10ByOrderByCreatedAtDesc();

    @Query("SELECT c FROM SalonClient c WHERE FUNCTION('TO_CHAR', c.dateOfBirth, 'MM-DD') = :monthDay")
    List<SalonClient> findByBirthdayMonthDay(String monthDay);
}
