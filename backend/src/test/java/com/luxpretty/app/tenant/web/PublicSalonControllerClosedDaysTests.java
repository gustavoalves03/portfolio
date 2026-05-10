package com.luxpretty.app.tenant.web;

import com.luxpretty.app.availability.app.ClosedDaysService;
import com.luxpretty.app.availability.app.ClosedDaysService.ClosedDayReason;
import com.luxpretty.app.availability.web.dto.ClosedDayResponse;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.domain.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Targeted unit tests for the public-side closed-days endpoint
 * GET /api/salon/{slug}/closed-days. The endpoint is special because:
 * - it must reject non-ACTIVE salons with 404,
 * - it must set/clear TenantContext around the service call,
 * - it must serialize the result in date order.
 */
@ExtendWith(MockitoExtension.class)
class PublicSalonControllerClosedDaysTests {

    @Mock private TenantService tenantService;
    @Mock private ClosedDaysService closedDaysService;

    private PublicSalonController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicSalonController(
                tenantService,
                /* categoryRepository */ null,
                /* availabilityService */ null,
                /* blockedSlotService */ null,
                /* slotAvailabilityService */ null,
                /* holidayAvailabilityService */ null,
                closedDaysService,
                /* careBookingService */ null,
                /* userRepository */ null,
                /* clientBookingHistoryService */ null,
                /* employeeService */ null,
                /* postService */ null,
                /* previewTokenService */ null);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private Tenant tenant(String slug, TenantStatus status) {
        Tenant t = new Tenant();
        t.setSlug(slug);
        t.setStatus(status);
        t.setName("Atelier " + slug);
        return t;
    }

    @Test
    @DisplayName("returns 200 + sorted ClosedDayResponse list for an ACTIVE salon")
    void activeSalon_returnsSortedList() {
        when(tenantService.findBySlug("atelier-sophie"))
                .thenReturn(Optional.of(tenant("atelier-sophie", TenantStatus.ACTIVE)));

        Map<LocalDate, ClosedDayReason> raw = new LinkedHashMap<>();
        raw.put(LocalDate.of(2026, 5, 17), ClosedDayReason.WEEKLY_CLOSED);
        raw.put(LocalDate.of(2026, 5, 1), ClosedDayReason.HOLIDAY);
        when(closedDaysService.getClosedDays(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                .thenReturn(raw);

        ResponseEntity<List<ClosedDayResponse>> response = controller.getClosedDays(
                "atelier-sophie", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ClosedDayResponse> body = response.getBody();
        assertThat(body).hasSize(2);
        assertThat(body.get(0).date()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(body.get(0).reason()).isEqualTo(ClosedDayReason.HOLIDAY);
        assertThat(body.get(1).date()).isEqualTo(LocalDate.of(2026, 5, 17));
    }

    @Test
    @DisplayName("returns 404 when the slug does not match any tenant")
    void unknownSlug_returns404() {
        when(tenantService.findBySlug("missing-salon")).thenReturn(Optional.empty());

        ResponseEntity<List<ClosedDayResponse>> response = controller.getClosedDays(
                "missing-salon", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("returns 404 when the tenant is not ACTIVE (still hidden from the public)")
    void inactiveSalon_returns404() {
        when(tenantService.findBySlug("draft-salon"))
                .thenReturn(Optional.of(tenant("draft-salon", TenantStatus.DRAFT)));

        ResponseEntity<List<ClosedDayResponse>> response = controller.getClosedDays(
                "draft-salon", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("sets the TenantContext during the call and clears it afterwards")
    void managesTenantContext() {
        when(tenantService.findBySlug("atelier-sophie"))
                .thenReturn(Optional.of(tenant("atelier-sophie", TenantStatus.ACTIVE)));
        when(closedDaysService.getClosedDays(any(), any())).thenAnswer(inv -> {
            // While the service runs, the context must be active.
            assertThat(TenantContext.getCurrentTenant()).isEqualTo("atelier-sophie");
            return Map.of();
        });

        controller.getClosedDays("atelier-sophie",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        // After the call, the context must be cleared so the next request
        // doesn't accidentally inherit a stale schema.
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("clears TenantContext even when the service throws")
    void clearsTenantContextOnException() {
        when(tenantService.findBySlug("atelier-sophie"))
                .thenReturn(Optional.of(tenant("atelier-sophie", TenantStatus.ACTIVE)));
        when(closedDaysService.getClosedDays(any(), any()))
                .thenThrow(new RuntimeException("boom"));

        try {
            controller.getClosedDays("atelier-sophie",
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        } catch (RuntimeException expected) {
            // Expected — we just want the finally block to have run.
        }

        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
