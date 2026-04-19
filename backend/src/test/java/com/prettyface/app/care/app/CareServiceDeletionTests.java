package com.prettyface.app.care.app;

import com.prettyface.app.bookings.domain.CareBookingStatus;
import com.prettyface.app.bookings.repo.CareBookingRepository;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.common.storage.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

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
    @DisplayName("Lot2#27: delete_WARN_careFromAnotherSalonCanBeDeleted — NO tenant check (FINDING)")
    void delete_WARN_careFromAnotherSalonCanBeDeleted() {
        // TODO-SEC: CareService.delete performs no tenant ownership verification.
        // If TenantContext were spoofed or schema routing bypassed, any careId from
        // another salon could be deleted. Documents the gap.
        when(careBookingRepository.countByCareIdAndAppointmentDateGreaterThanEqualAndStatusNot(
                eq(999L), any(LocalDate.class), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(0L);
        doNothing().when(fileStorageService).deleteCareImages(999L);

        // No ResponseStatusException thrown, no tenant/owner lookup — service trusts
        // schema router to have already isolated the caller to the correct schema.
        service.delete(999L);

        verify(fileStorageService).deleteCareImages(999L);
        verify(repo).deleteById(999L);
        // No call to any tenant/owner-verification collaborator because there is none.
    }
}
