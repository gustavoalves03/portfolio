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
}
