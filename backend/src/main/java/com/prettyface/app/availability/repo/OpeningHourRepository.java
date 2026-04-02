package com.prettyface.app.availability.repo;

import com.prettyface.app.availability.domain.OpeningHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpeningHourRepository extends JpaRepository<OpeningHour, Long> {
    List<OpeningHour> findAllByOrderByDayOfWeekAscOpenTimeAsc();

    List<OpeningHour> findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(Long employeeId);

    List<OpeningHour> findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc();
}
