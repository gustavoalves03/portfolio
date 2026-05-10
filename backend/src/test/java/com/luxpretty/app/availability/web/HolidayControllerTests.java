package com.luxpretty.app.availability.web;

import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.availability.app.HolidayService;
import com.luxpretty.app.availability.app.HolidayService.HolidayInfo;
import com.luxpretty.app.availability.domain.HolidayException;
import com.luxpretty.app.availability.repo.HolidayExceptionRepository;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for HolidayController. The controller relies on:
 * - HolidayService for the upcoming-holidays read,
 * - HolidayExceptionRepository for the per-day open/closed override,
 * - TenantRepository to scope work to the caller's tenant,
 * - TenantContext to route writes to the right schema.
 */
@ExtendWith(MockitoExtension.class)
class HolidayControllerTests {

    @Mock private HolidayService holidayService;
    @Mock private HolidayExceptionRepository exceptionRepo;
    @Mock private TenantRepository tenantRepository;

    private HolidayController controller;

    private static final Long OWNER_ID = 42L;
    private static final String SLUG = "atelier-sophie";

    @BeforeEach
    void setUp() {
        controller = new HolidayController(holidayService, exceptionRepo, tenantRepository);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private UserPrincipal principal() {
        return new UserPrincipal(OWNER_ID, "sophie@test.fr", "Sophie", null);
    }

    private Tenant tenant() {
        Tenant t = new Tenant();
        t.setSlug(SLUG);
        t.setOwnerId(OWNER_ID);
        t.setName("Atelier Sophie");
        t.setAddressCountry("FR");
        return t;
    }

    // ── /upcoming ──

    @Test
    @DisplayName("getUpcomingHolidays delegates to HolidayService with the tenant country")
    void upcoming_delegatesToService() {
        when(tenantRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant()));
        List<HolidayInfo> stub = List.of(
                new HolidayInfo(LocalDate.of(2026, 5, 1), "Fête du Travail")
        );
        when(holidayService.getUpcomingHolidays("FR", 12)).thenReturn(stub);

        List<HolidayInfo> result = controller.getUpcomingHolidays(principal());

        assertThat(result).isSameAs(stub);
    }

    @Test
    @DisplayName("getUpcomingHolidays returns 404 when the user has no tenant")
    void upcoming_404_whenTenantMissing() {
        when(tenantRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getUpcomingHolidays(principal()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── PUT /exceptions/{date} ──

    @Test
    @DisplayName("toggleException creates a new exception when none exists, sets the open flag")
    void toggle_createsNew() {
        when(tenantRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant()));
        LocalDate date = LocalDate.of(2026, 5, 1);
        when(exceptionRepo.findByHolidayDate(date)).thenReturn(Optional.empty());
        when(exceptionRepo.save(any(HolidayException.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = controller.toggleException(
                principal(), date, Map.of("open", true));

        assertThat(result).containsEntry("date", date).containsEntry("open", true);
        ArgumentCaptor<HolidayException> captor = ArgumentCaptor.forClass(HolidayException.class);
        verify(exceptionRepo).save(captor.capture());
        assertThat(captor.getValue().getHolidayDate()).isEqualTo(date);
        assertThat(captor.getValue().isOpen()).isTrue();
    }

    @Test
    @DisplayName("toggleException updates an existing exception when one is found")
    void toggle_updatesExisting() {
        when(tenantRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant()));
        LocalDate date = LocalDate.of(2026, 5, 1);
        HolidayException existing = new HolidayException();
        existing.setHolidayDate(date);
        existing.setOpen(false);
        when(exceptionRepo.findByHolidayDate(date)).thenReturn(Optional.of(existing));
        when(exceptionRepo.save(any(HolidayException.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        controller.toggleException(principal(), date, Map.of("open", true));

        // Same instance, flag flipped — we don't create a duplicate.
        assertThat(existing.isOpen()).isTrue();
        verify(exceptionRepo).save(existing);
    }

    @Test
    @DisplayName("toggleException treats missing/false body value as open=false")
    void toggle_falsyBodyMapsToClosed() {
        when(tenantRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant()));
        LocalDate date = LocalDate.of(2026, 5, 1);
        when(exceptionRepo.findByHolidayDate(date)).thenReturn(Optional.empty());
        when(exceptionRepo.save(any(HolidayException.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = controller.toggleException(
                principal(), date, Map.of("open", false));

        assertThat(result).containsEntry("open", false);
    }

    @Test
    @DisplayName("toggleException sets and clears the TenantContext around the write")
    void toggle_managesTenantContext() {
        when(tenantRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant()));
        when(exceptionRepo.findByHolidayDate(any())).thenReturn(Optional.empty());
        when(exceptionRepo.save(any(HolidayException.class)))
                .thenAnswer(inv -> {
                    // Mid-call: tenant should be active.
                    assertThat(TenantContext.getCurrentTenant()).isEqualTo(SLUG);
                    return inv.getArgument(0);
                });

        controller.toggleException(principal(), LocalDate.of(2026, 5, 1), Map.of("open", true));

        // After: the controller must have cleared the context for the next request.
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("toggleException returns 404 when tenant is unknown — no save attempted")
    void toggle_404_whenTenantMissing() {
        when(tenantRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.toggleException(
                        principal(), LocalDate.of(2026, 5, 1), Map.of("open", true)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(exceptionRepo, never()).save(any());
    }

    // ── /exceptions ──

    @Test
    @DisplayName("listExceptions returns date+open pairs scoped to the tenant schema")
    void list_returnsPairs() {
        when(tenantRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant()));
        HolidayException ex1 = new HolidayException();
        ex1.setHolidayDate(LocalDate.of(2026, 5, 1));
        ex1.setOpen(true);
        HolidayException ex2 = new HolidayException();
        ex2.setHolidayDate(LocalDate.of(2026, 12, 25));
        ex2.setOpen(false);
        when(exceptionRepo.findAll()).thenReturn(List.of(ex1, ex2));

        List<Map<String, Object>> result = controller.listExceptions(principal());

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("date", LocalDate.of(2026, 5, 1)).containsEntry("open", true);
        assertThat(result.get(1)).containsEntry("date", LocalDate.of(2026, 12, 25)).containsEntry("open", false);
        // Context cleared once we're back from the controller.
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("listExceptions returns 404 when tenant missing")
    void list_404_whenTenantMissing() {
        when(tenantRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.listExceptions(principal()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
