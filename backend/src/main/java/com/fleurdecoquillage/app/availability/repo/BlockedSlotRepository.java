package com.fleurdecoquillage.app.availability.repo;

import com.fleurdecoquillage.app.availability.domain.BlockedSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BlockedSlotRepository extends JpaRepository<BlockedSlot, Long> {
    List<BlockedSlot> findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(LocalDate date);
}
