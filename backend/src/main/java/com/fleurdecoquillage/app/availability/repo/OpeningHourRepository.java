package com.fleurdecoquillage.app.availability.repo;

import com.fleurdecoquillage.app.availability.domain.OpeningHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpeningHourRepository extends JpaRepository<OpeningHour, Long> {
    List<OpeningHour> findAllByOrderByDayOfWeekAscOpenTimeAsc();
}
