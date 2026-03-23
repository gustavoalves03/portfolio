package com.fleurdecoquillage.app.availability.app;

import com.fleurdecoquillage.app.availability.domain.BlockedSlot;
import com.fleurdecoquillage.app.availability.repo.BlockedSlotRepository;
import com.fleurdecoquillage.app.availability.web.dto.BlockedSlotRequest;
import com.fleurdecoquillage.app.availability.web.dto.BlockedSlotResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class BlockedSlotService {

    private final BlockedSlotRepository repo;

    public BlockedSlotService(BlockedSlotRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<BlockedSlotResponse> listFuture() {
        return repo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(LocalDate.now())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BlockedSlotResponse create(BlockedSlotRequest req) {
        if (req.date().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot block a date in the past");
        }

        BlockedSlot slot = new BlockedSlot();
        slot.setDate(req.date());
        slot.setFullDay(req.fullDay());

        if (req.fullDay()) {
            slot.setStartTime(null);
            slot.setEndTime(null);
        } else {
            if (req.startTime() == null || req.endTime() == null) {
                throw new IllegalArgumentException("Start time and end time are required when not full day");
            }
            LocalTime start = LocalTime.parse(req.startTime());
            LocalTime end = LocalTime.parse(req.endTime());
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("End time must be after start time");
            }
            slot.setStartTime(start);
            slot.setEndTime(end);
        }

        slot.setReason(req.reason());
        return toResponse(repo.save(slot));
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }

    private BlockedSlotResponse toResponse(BlockedSlot s) {
        return new BlockedSlotResponse(
                s.getId(),
                s.getDate(),
                s.getStartTime() != null ? s.getStartTime().toString() : null,
                s.getEndTime() != null ? s.getEndTime().toString() : null,
                s.isFullDay(),
                s.getReason()
        );
    }
}
