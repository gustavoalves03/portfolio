package com.luxpretty.app.availability.repo;

import com.luxpretty.app.availability.domain.OpeningHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpeningHourRepository extends JpaRepository<OpeningHour, Long> {
    List<OpeningHour> findAllByOrderByDayOfWeekAscOpenTimeAsc();

    List<OpeningHour> findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(Long employeeId);

    List<OpeningHour> findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc();
}
