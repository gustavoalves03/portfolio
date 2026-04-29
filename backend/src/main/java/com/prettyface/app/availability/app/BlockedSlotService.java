package com.prettyface.app.availability.app;

import com.prettyface.app.availability.domain.BlockedSlot;
import com.prettyface.app.availability.repo.BlockedSlotRepository;
import com.prettyface.app.availability.web.dto.BlockedSlotRequest;
import com.prettyface.app.availability.web.dto.BlockedSlotResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot block a date in the past");
        }

        BlockedSlot slot = new BlockedSlot();
        slot.setDate(req.date());
        slot.setFullDay(req.fullDay());

        if (req.fullDay()) {
            slot.setStartTime(null);
            slot.setEndTime(null);
        } else {
            if (req.startTime() == null || req.endTime() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Start time and end time are required when not full day");
            }
            LocalTime start;
            LocalTime end;
            try {
                start = LocalTime.parse(req.startTime());
                end = LocalTime.parse(req.endTime());
            } catch (java.time.format.DateTimeParseException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid time format");
            }
            if (!end.isAfter(start)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "End time must be after start time");
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
