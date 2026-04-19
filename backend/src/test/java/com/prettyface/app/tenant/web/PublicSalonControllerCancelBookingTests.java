package com.prettyface.app.tenant.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.availability.app.AvailabilityService;
import com.prettyface.app.availability.app.BlockedSlotService;
import com.prettyface.app.availability.app.HolidayAvailabilityService;
import com.prettyface.app.availability.app.SlotAvailabilityService;
import com.prettyface.app.bookings.app.CareBookingService;
import com.prettyface.app.bookings.app.ClientBookingHistoryService;
import com.prettyface.app.bookings.domain.CareBooking;
import com.prettyface.app.bookings.domain.CareBookingStatus;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.employee.app.EmployeeService;
import com.prettyface.app.post.app.PostService;
import com.prettyface.app.tenant.app.TenantService;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.domain.TenantStatus;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Lot2 Sec1 — Scenario #100: Client cannot cancel another client's booking.
 *
 * Pattern B: The check EXISTS at the controller layer in
 * {@link PublicSalonController#cancelBooking}. The service layer
 * (CareBookingService) does NOT perform this principal-ownership check, so
 * the safety lives purely in the controller's
 * {@code !booking.getUser().getId().equals(principal.getId())} guard. The
 * service-level tenant guard (TenantContext.requireActive) is a separate
 * defense-in-depth layer, not a caller-ownership check.
 *
 * These tests invoke the controller method directly (no Spring context) to
 * pin that guard against regression.
 */
@ExtendWith(MockitoExtension.class)
class PublicSalonControllerCancelBookingTests {

    @Mock private TenantService tenantService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AvailabilityService availabilityService;
    @Mock private BlockedSlotService blockedSlotService;
    @Mock private SlotAvailabilityService slotAvailabilityService;
    @Mock private HolidayAvailabilityService holidayAvailabilityService;
    @Mock private CareBookingService careBookingService;
    @Mock private UserRepository userRepository;
    @Mock private ClientBookingHistoryService clientBookingHistoryService;
    @Mock private EmployeeService employeeService;
    @Mock private PostService postService;

    @InjectMocks
    private PublicSalonController controller;

    private Tenant activeTenant;

    @BeforeEach
    void setUp() {
        activeTenant = Tenant.builder()
                .id(1L)
                .slug("salon-a")
                .name("Salon A")
                .ownerId(100L)
                .status(TenantStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Lot2#100: cancel — booking owner cancels their own booking → 200 OK")
    void cancelBooking_byOwner_succeeds() {
        User owner = User.builder().id(200L).name("Marie").email("marie@example.com").build();
        CareBooking booking = new CareBooking();
        booking.setId(555L);
        booking.setUser(owner);
        booking.setAppointmentDate(LocalDate.now().plusDays(3));
        booking.setAppointmentTime(LocalTime.of(10, 0));
        booking.setStatus(CareBookingStatus.CONFIRMED);

        when(tenantService.findBySlug("salon-a")).thenReturn(Optional.of(activeTenant));
        when(careBookingService.findById(555L)).thenReturn(Optional.of(booking));

        UserPrincipal principal = new UserPrincipal(200L, "marie@example.com", "Marie", null);

        ResponseEntity<Void> result = controller.cancelBooking("salon-a", 555L, principal);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        verify(careBookingService).cancelBooking(555L);
        verify(clientBookingHistoryService).updateMirrorStatus("salon-a", 555L, "CANCELLED");
    }

    @Test
    @DisplayName("Lot2#100: cancel — non-owner cannot cancel another client's booking → 403 FORBIDDEN")
    void cancelBooking_byNonOwner_rejectedWith403() {
        // Booking belongs to user 200 (Marie). Caller is user 201 (Mallory).
        User owner = User.builder().id(200L).name("Marie").email("marie@example.com").build();
        CareBooking booking = new CareBooking();
        booking.setId(555L);
        booking.setUser(owner);
        booking.setAppointmentDate(LocalDate.now().plusDays(3));
        booking.setAppointmentTime(LocalTime.of(10, 0));
        booking.setStatus(CareBookingStatus.CONFIRMED);

        when(tenantService.findBySlug("salon-a")).thenReturn(Optional.of(activeTenant));
        when(careBookingService.findById(555L)).thenReturn(Optional.of(booking));

        UserPrincipal attacker = new UserPrincipal(201L, "mallory@evil.example", "Mallory", null);

        assertThatThrownBy(() -> controller.cancelBooking("salon-a", 555L, attacker))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("Not your booking");
                });

        // Critically: cancelBooking was NOT forwarded to the service.
        verify(careBookingService, never()).cancelBooking(anyLong());
        verify(clientBookingHistoryService, never()).updateMirrorStatus(anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("Lot2#100: cancel — salon not active → 404 NOT FOUND (defence in depth)")
    void cancelBooking_salonInactive_rejectedWith404() {
        Tenant draftTenant = Tenant.builder()
                .id(1L)
                .slug("salon-a")
                .ownerId(100L)
                .status(TenantStatus.DRAFT)
                .build();
        when(tenantService.findBySlug("salon-a")).thenReturn(Optional.of(draftTenant));

        UserPrincipal principal = new UserPrincipal(200L, "marie@example.com", "Marie", null);

        assertThatThrownBy(() -> controller.cancelBooking("salon-a", 555L, principal))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(careBookingService, never()).cancelBooking(anyLong());
    }
}
