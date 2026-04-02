package com.prettyface.app.availability.app;

import com.prettyface.app.availability.domain.OpeningHour;
import com.prettyface.app.availability.repo.OpeningHourRepository;
import com.prettyface.app.availability.web.dto.OpeningHourRequest;
import com.prettyface.app.availability.web.dto.OpeningHourResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTests {

    @Mock
    private OpeningHourRepository repo;

    @InjectMocks
    private AvailabilityService service;

    // ── list ──

    @Test
    void list_returnsMappedResponses() {
        OpeningHour monday = buildOpeningHour(1L, 1, "09:00", "12:00");
        OpeningHour mondayAfternoon = buildOpeningHour(2L, 1, "14:00", "18:00");
        OpeningHour saturday = buildOpeningHour(3L, 6, "10:00", "16:00");

        when(repo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(monday, mondayAfternoon, saturday));

        List<OpeningHourResponse> result = service.list();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).dayOfWeek()).isEqualTo(1);
        assertThat(result.get(0).openTime()).isEqualTo("09:00");
        assertThat(result.get(0).closeTime()).isEqualTo("12:00");
        assertThat(result.get(2).dayOfWeek()).isEqualTo(6);
    }

    @Test
    void list_empty_returnsEmptyList() {
        when(repo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of());
        assertThat(service.list()).isEmpty();
    }

    // ── replaceAll — happy path ──

    @Test
    void replaceAll_deletesOldAndSavesNew() {
        List<OpeningHourRequest> requests = List.of(
                new OpeningHourRequest(1, "09:00", "12:00"),
                new OpeningHourRequest(1, "14:00", "18:00")
        );

        when(repo.saveAll(anyList())).thenAnswer(inv -> {
            List<OpeningHour> entities = inv.getArgument(0);
            long id = 1;
            for (OpeningHour e : entities) {
                e.setId(id++);
            }
            return entities;
        });

        List<OpeningHourResponse> result = service.replaceAll(requests);

        verify(repo).deleteAllInBatch();
        verify(repo).flush();
        verify(repo).saveAll(anyList());
        assertThat(result).hasSize(2);
    }

    @Test
    void replaceAll_returnsSortedByDayAndTime() {
        List<OpeningHourRequest> requests = List.of(
                new OpeningHourRequest(5, "10:00", "17:00"),
                new OpeningHourRequest(1, "14:00", "18:00"),
                new OpeningHourRequest(1, "09:00", "12:00")
        );

        when(repo.saveAll(anyList())).thenAnswer(inv -> {
            List<OpeningHour> entities = inv.getArgument(0);
            long id = 1;
            for (OpeningHour e : entities) {
                e.setId(id++);
            }
            return entities;
        });

        List<OpeningHourResponse> result = service.replaceAll(requests);

        assertThat(result.get(0).dayOfWeek()).isEqualTo(1);
        assertThat(result.get(0).openTime()).isEqualTo("09:00");
        assertThat(result.get(1).dayOfWeek()).isEqualTo(1);
        assertThat(result.get(1).openTime()).isEqualTo("14:00");
        assertThat(result.get(2).dayOfWeek()).isEqualTo(5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceAll_mapsRequestsToEntitiesCorrectly() {
        List<OpeningHourRequest> requests = List.of(
                new OpeningHourRequest(3, "08:00", "12:00")
        );

        ArgumentCaptor<List<OpeningHour>> captor = ArgumentCaptor.forClass(List.class);
        when(repo.saveAll(captor.capture())).thenAnswer(inv -> {
            List<OpeningHour> entities = inv.getArgument(0);
            entities.get(0).setId(1L);
            return entities;
        });

        service.replaceAll(requests);

        List<OpeningHour> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getDayOfWeek()).isEqualTo(3);
        assertThat(saved.get(0).getOpenTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(saved.get(0).getCloseTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void replaceAll_emptyList_deletesAllAndReturnsEmpty() {
        when(repo.saveAll(anyList())).thenReturn(List.of());

        List<OpeningHourResponse> result = service.replaceAll(List.of());

        verify(repo).deleteAllInBatch();
        assertThat(result).isEmpty();
    }

    // ── replaceAll — validation: close before open ──

    @Test
    void replaceAll_closeBeforeOpen_throws() {
        List<OpeningHourRequest> requests = List.of(
                new OpeningHourRequest(1, "18:00", "09:00")
        );

        assertThatThrownBy(() -> service.replaceAll(requests))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after open");

        verify(repo, never()).deleteAllInBatch();
    }

    @Test
    void replaceAll_closeEqualsOpen_throws() {
        List<OpeningHourRequest> requests = List.of(
                new OpeningHourRequest(1, "09:00", "09:00")
        );

        assertThatThrownBy(() -> service.replaceAll(requests))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after open");
    }

    // ── replaceAll — validation: overlapping slots ──

    @Test
    void replaceAll_overlappingSlotsOnSameDay_throws() {
        List<OpeningHourRequest> requests = List.of(
                new OpeningHourRequest(1, "09:00", "14:00"),
                new OpeningHourRequest(1, "13:00", "18:00")
        );

        assertThatThrownBy(() -> service.replaceAll(requests))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Overlapping");
    }

    @Test
    void replaceAll_adjacentSlotsOnSameDay_succeeds() {
        List<OpeningHourRequest> requests = List.of(
                new OpeningHourRequest(1, "09:00", "12:00"),
                new OpeningHourRequest(1, "12:00", "18:00")
        );

        when(repo.saveAll(anyList())).thenAnswer(inv -> {
            List<OpeningHour> entities = inv.getArgument(0);
            long id = 1;
            for (OpeningHour e : entities) {
                e.setId(id++);
            }
            return entities;
        });

        List<OpeningHourResponse> result = service.replaceAll(requests);
        assertThat(result).hasSize(2);
    }

    @Test
    void replaceAll_slotsOnDifferentDays_noOverlapCheck() {
        List<OpeningHourRequest> requests = List.of(
                new OpeningHourRequest(1, "09:00", "18:00"),
                new OpeningHourRequest(2, "09:00", "18:00")
        );

        when(repo.saveAll(anyList())).thenAnswer(inv -> {
            List<OpeningHour> entities = inv.getArgument(0);
            long id = 1;
            for (OpeningHour e : entities) {
                e.setId(id++);
            }
            return entities;
        });

        List<OpeningHourResponse> result = service.replaceAll(requests);
        assertThat(result).hasSize(2);
    }

    // ── helper ──

    private OpeningHour buildOpeningHour(Long id, int day, String open, String close) {
        OpeningHour h = new OpeningHour();
        h.setId(id);
        h.setDayOfWeek(day);
        h.setOpenTime(LocalTime.parse(open));
        h.setCloseTime(LocalTime.parse(close));
        return h;
    }
}
