package com.fleurdecoquillage.app.availability.app;

import com.fleurdecoquillage.app.availability.domain.OpeningHour;
import com.fleurdecoquillage.app.availability.repo.OpeningHourRepository;
import com.fleurdecoquillage.app.availability.web.dto.OpeningHourRequest;
import com.fleurdecoquillage.app.availability.web.dto.OpeningHourResponse;
import com.fleurdecoquillage.app.availability.web.mapper.OpeningHourMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {

    private final OpeningHourRepository repo;

    public AvailabilityService(OpeningHourRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<OpeningHourResponse> list() {
        return repo.findAllByOrderByDayOfWeekAscOpenTimeAsc()
                .stream()
                .map(OpeningHourMapper::toResponse)
                .toList();
    }

    @Transactional
    public List<OpeningHourResponse> replaceAll(List<OpeningHourRequest> requests) {
        for (OpeningHourRequest req : requests) {
            LocalTime open = LocalTime.parse(req.openTime());
            LocalTime close = LocalTime.parse(req.closeTime());
            if (!close.isAfter(open)) {
                throw new IllegalArgumentException(
                        "Close time must be after open time for day " + req.dayOfWeek());
            }
        }

        Map<Integer, List<OpeningHourRequest>> byDay = requests.stream()
                .collect(Collectors.groupingBy(OpeningHourRequest::dayOfWeek));

        for (var entry : byDay.entrySet()) {
            List<OpeningHourRequest> daySlots = entry.getValue().stream()
                    .sorted(Comparator.comparing(r -> LocalTime.parse(r.openTime())))
                    .toList();

            for (int i = 1; i < daySlots.size(); i++) {
                LocalTime prevClose = LocalTime.parse(daySlots.get(i - 1).closeTime());
                LocalTime currOpen = LocalTime.parse(daySlots.get(i).openTime());
                if (currOpen.isBefore(prevClose)) {
                    throw new IllegalArgumentException(
                            "Overlapping time slots on day " + entry.getKey());
                }
            }
        }

        repo.deleteAllInBatch();
        repo.flush();

        List<OpeningHour> entities = requests.stream()
                .map(OpeningHourMapper::toEntity)
                .toList();

        List<OpeningHour> saved = repo.saveAll(entities);

        return saved.stream()
                .sorted(Comparator.comparingInt(OpeningHour::getDayOfWeek)
                        .thenComparing(OpeningHour::getOpenTime))
                .map(OpeningHourMapper::toResponse)
                .toList();
    }
}
