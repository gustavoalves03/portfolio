package com.prettyface.app.care.app;

import com.prettyface.app.bookings.domain.CareBookingStatus;
import com.prettyface.app.bookings.repo.CareBookingRepository;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.common.storage.FileStorageService;
import com.prettyface.app.multitenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CareServiceDeletionTests {

    @Mock private CareRepository repo;
    @Mock private CategoryRepository categoryRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private CareBookingRepository careBookingRepository;

    @InjectMocks
    private CareService service;

    @BeforeEach
    void setTenant() {
        TenantContext.setCurrentTenant("test-tenant");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Delete care with future bookings → CONFLICT with clear message")
    void deleteCareWithFutureBookings_throwsConflict() {
        when(careBookingRepository.countByCareIdAndAppointmentDateGreaterThanEqualAndStatusNot(
                eq(1L), any(LocalDate.class), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(3L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot delete")
                .hasMessageContaining("3 future booking(s) exist")
                .hasMessageContaining("Cancel them first");

        // Verify deletion was NOT attempted
        verify(repo, never()).deleteById(any());
        verify(fileStorageService, never()).deleteCareImages(any());
    }

    @Test
    @DisplayName("Delete care with NO future bookings → succeeds")
    void deleteCareWithNoFutureBookings_succeeds() {
        when(careBookingRepository.countByCareIdAndAppointmentDateGreaterThanEqualAndStatusNot(
                eq(1L), any(LocalDate.class), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(0L);
        doNothing().when(fileStorageService).deleteCareImages(1L);

        service.delete(1L);

        verify(fileStorageService).deleteCareImages(1L);
        verify(repo).deleteById(1L);
    }

    @Test
    @DisplayName("Delete care where all future bookings are cancelled → succeeds")
    void deleteCareWithOnlyCancelledBookings_succeeds() {
        // countBy...StatusNot(CANCELLED) returns 0 because all bookings are cancelled
        when(careBookingRepository.countByCareIdAndAppointmentDateGreaterThanEqualAndStatusNot(
                eq(1L), any(LocalDate.class), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(0L);
        doNothing().when(fileStorageService).deleteCareImages(1L);

        service.delete(1L);

        verify(fileStorageService).deleteCareImages(1L);
        verify(repo).deleteById(1L);
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lot2 Sec1: Cross-tenant IDOR on care delete ──
    // ══════════════════════════════════════════════════════════════
    // NOTE-SEC: Care has no tenantSlug/tenantId column. Multi-tenancy is enforced
    // purely by Hibernate per-tenant schemas (schema router). CareService.delete(id)
    // performs NO explicit tenant check — it only checks for future bookings then
    // forwards to repo.deleteById(). If the schema router is bypassed, a pro from
    // salon A could delete a care from salon B.

    @Test
    @DisplayName("Lot2#27: delete_requiresActiveTenant_throwsWhenUnset — defense-in-depth guard")
    void delete_requiresActiveTenant_throwsWhenUnset() {
        // Fix2: CareService.delete now calls TenantContext.requireActive() so a
        // missing tenant context (schema router bug, background job) refuses to
        // delete anything. Previously this test documented the absence of the
        // guard; now it asserts the guard works.
        TenantContext.clear();

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
                .hasMessageContaining("No tenant context");

        verify(repo, never()).deleteById(any());
        verify(fileStorageService, never()).deleteCareImages(any());
        verify(careBookingRepository, never())
                .countByCareIdAndAppointmentDateGreaterThanEqualAndStatusNot(any(), any(), any());
    }
}
