package com.prettyface.app.availability.repo;

import com.prettyface.app.availability.domain.BlockedSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BlockedSlotRepository extends JpaRepository<BlockedSlot, Long> {
    List<BlockedSlot> findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(LocalDate date);
}
