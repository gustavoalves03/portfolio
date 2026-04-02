package com.prettyface.app.availability.app;

import com.prettyface.app.availability.domain.BlockedSlot;
import com.prettyface.app.availability.repo.BlockedSlotRepository;
import com.prettyface.app.availability.web.dto.BlockedSlotRequest;
import com.prettyface.app.availability.web.dto.BlockedSlotResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockedSlotServiceTests {

    @Mock
    private BlockedSlotRepository repo;

    @InjectMocks
    private BlockedSlotService service;

    private BlockedSlot fullDaySlot;
    private BlockedSlot timeRangeSlot;

    @BeforeEach
    void setUp() {
        fullDaySlot = new BlockedSlot();
        fullDaySlot.setId(1L);
        fullDaySlot.setDate(LocalDate.now().plusDays(5));
        fullDaySlot.setFullDay(true);
        fullDaySlot.setStartTime(null);
        fullDaySlot.setEndTime(null);
        fullDaySlot.setReason("Vacances");

        timeRangeSlot = new BlockedSlot();
        timeRangeSlot.setId(2L);
        timeRangeSlot.setDate(LocalDate.now().plusDays(3));
        timeRangeSlot.setFullDay(false);
        timeRangeSlot.setStartTime(LocalTime.of(12, 0));
        timeRangeSlot.setEndTime(LocalTime.of(14, 0));
        timeRangeSlot.setReason("Pause déjeuner");
    }

    // ── listFuture ──

    @Test
    void listFuture_returnsFutureSlots() {
        when(repo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(timeRangeSlot, fullDaySlot));

        List<BlockedSlotResponse> result = service.listFuture();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(2L);
        assertThat(result.get(0).startTime()).isEqualTo("12:00");
        assertThat(result.get(0).endTime()).isEqualTo("14:00");
        assertThat(result.get(0).fullDay()).isFalse();
        assertThat(result.get(1).id()).isEqualTo(1L);
        assertThat(result.get(1).fullDay()).isTrue();
        assertThat(result.get(1).startTime()).isNull();
    }

    @Test
    void listFuture_empty_returnsEmptyList() {
        when(repo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        assertThat(service.listFuture()).isEmpty();
    }

    // ── create — full day ──

    @Test
    void create_fullDay_setsNullTimes() {
        BlockedSlotRequest req = new BlockedSlotRequest(
                LocalDate.now().plusDays(1), null, null, true, "Formation"
        );

        when(repo.save(any())).thenAnswer(inv -> {
            BlockedSlot saved = inv.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        BlockedSlotResponse response = service.create(req);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.fullDay()).isTrue();
        assertThat(response.startTime()).isNull();
        assertThat(response.endTime()).isNull();
        assertThat(response.reason()).isEqualTo("Formation");
    }

    @Test
    void create_fullDay_entityHasCorrectFields() {
        BlockedSlotRequest req = new BlockedSlotRequest(
                LocalDate.now().plusDays(2), null, null, true, "Congé"
        );

        ArgumentCaptor<BlockedSlot> captor = ArgumentCaptor.forClass(BlockedSlot.class);
        when(repo.save(captor.capture())).thenAnswer(inv -> {
            BlockedSlot s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        service.create(req);

        BlockedSlot saved = captor.getValue();
        assertThat(saved.isFullDay()).isTrue();
        assertThat(saved.getStartTime()).isNull();
        assertThat(saved.getEndTime()).isNull();
        assertThat(saved.getReason()).isEqualTo("Congé");
    }

    // ── create — time range ──

    @Test
    void create_timeRange_setsStartAndEndTime() {
        BlockedSlotRequest req = new BlockedSlotRequest(
                LocalDate.now().plusDays(1), "10:00", "12:00", false, "RDV perso"
        );

        when(repo.save(any())).thenAnswer(inv -> {
            BlockedSlot saved = inv.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        BlockedSlotResponse response = service.create(req);

        assertThat(response.fullDay()).isFalse();
        assertThat(response.startTime()).isEqualTo("10:00");
        assertThat(response.endTime()).isEqualTo("12:00");
    }

    // ── create — validation errors ──

    @Test
    void create_pastDate_throws() {
        BlockedSlotRequest req = new BlockedSlotRequest(
                LocalDate.now().minusDays(1), null, null, true, null
        );

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("past");

        verify(repo, never()).save(any());
    }

    @Test
    void create_timeRange_endBeforeStart_throws() {
        BlockedSlotRequest req = new BlockedSlotRequest(
                LocalDate.now().plusDays(1), "14:00", "10:00", false, null
        );

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after start");
    }

    @Test
    void create_timeRange_equalStartAndEnd_throws() {
        BlockedSlotRequest req = new BlockedSlotRequest(
                LocalDate.now().plusDays(1), "10:00", "10:00", false, null
        );

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after start");
    }

    @Test
    void create_notFullDay_missingStartTime_throws() {
        BlockedSlotRequest req = new BlockedSlotRequest(
                LocalDate.now().plusDays(1), null, "12:00", false, null
        );

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void create_notFullDay_missingEndTime_throws() {
        BlockedSlotRequest req = new BlockedSlotRequest(
                LocalDate.now().plusDays(1), "10:00", null, false, null
        );

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void create_reasonIsOptional() {
        BlockedSlotRequest req = new BlockedSlotRequest(
                LocalDate.now().plusDays(1), null, null, true, null
        );

        when(repo.save(any())).thenAnswer(inv -> {
            BlockedSlot saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        BlockedSlotResponse response = service.create(req);
        assertThat(response.reason()).isNull();
    }

    // ── delete ──

    @Test
    void delete_callsRepositoryDeleteById() {
        service.delete(42L);
        verify(repo).deleteById(42L);
    }
}
