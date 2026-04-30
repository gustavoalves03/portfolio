package com.prettyface.app.availability.web;

import com.prettyface.app.availability.app.ClosedDaysService;
import com.prettyface.app.availability.app.ClosedDaysService.ClosedDayReason;
import com.prettyface.app.availability.web.dto.ClosedDayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the pro-side /api/pro/availability/closed-days controller.
 * Validates the date-sorted DTO mapping; the service-level rules
 * (HOLIDAY priority, weekly-closed, etc.) are covered separately by
 * {@link com.prettyface.app.availability.app.ClosedDaysServiceTests}.
 */
@ExtendWith(MockitoExtension.class)
class ClosedDaysControllerTests {

    @Mock private ClosedDaysService service;

    private ClosedDaysController controller;

    @BeforeEach
    void setUp() {
        controller = new ClosedDaysController(service);
    }

    @Test
    @DisplayName("returns sorted list of {date, reason} for the requested range")
    void returnsSortedList() {
        // Use a LinkedHashMap with intentionally-unsorted keys to make sure
        // the controller, not the JVM, is the one ordering by date.
        Map<LocalDate, ClosedDayReason> raw = new LinkedHashMap<>();
        raw.put(LocalDate.of(2026, 5, 17), ClosedDayReason.WEEKLY_CLOSED);
        raw.put(LocalDate.of(2026, 5, 1), ClosedDayReason.HOLIDAY);
        raw.put(LocalDate.of(2026, 5, 8), ClosedDayReason.HOLIDAY);
        when(service.getClosedDays(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                .thenReturn(raw);

        List<ClosedDayResponse> result = controller.getClosedDays(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(result.get(0).reason()).isEqualTo(ClosedDayReason.HOLIDAY);
        assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2026, 5, 8));
        assertThat(result.get(2).date()).isEqualTo(LocalDate.of(2026, 5, 17));
    }

    @Test
    @DisplayName("returns empty list when service returns no closed days")
    void emptyService() {
        when(service.getClosedDays(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(Map.of());

        List<ClosedDayResponse> result = controller.getClosedDays(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("preserves the reason verbatim — no mapping/transformation")
    void preservesReason() {
        when(service.getClosedDays(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 7)))
                .thenReturn(Map.of(
                        LocalDate.of(2026, 5, 6), ClosedDayReason.FULL_DAY_BLOCK
                ));

        List<ClosedDayResponse> result = controller.getClosedDays(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 7));

        assertThat(result).singleElement()
                .satisfies(r -> {
                    assertThat(r.date()).isEqualTo(LocalDate.of(2026, 5, 6));
                    assertThat(r.reason()).isEqualTo(ClosedDayReason.FULL_DAY_BLOCK);
                });
    }
}
