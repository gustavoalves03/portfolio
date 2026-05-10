package com.luxpretty.app.availability.repo;

import com.luxpretty.app.availability.domain.HolidayException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface HolidayExceptionRepository extends JpaRepository<HolidayException, Long> {
    Optional<HolidayException> findByHolidayDate(LocalDate date);
}
