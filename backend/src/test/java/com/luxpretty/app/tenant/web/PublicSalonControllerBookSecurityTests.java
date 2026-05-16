package com.luxpretty.app.tenant.web;

import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.availability.app.AvailabilityService;
import com.luxpretty.app.availability.app.BlockedSlotService;
import com.luxpretty.app.availability.app.ClosedDaysService;
import com.luxpretty.app.availability.app.HolidayAvailabilityService;
import com.luxpretty.app.availability.app.SlotAvailabilityService;
import com.luxpretty.app.bookings.app.CareBookingService;
import com.luxpretty.app.bookings.app.ClientBookingHistoryService;
import com.luxpretty.app.bookings.web.dto.ClientBookingRequest;
import com.luxpretty.app.bookings.web.dto.ClientBookingResponse;
import com.luxpretty.app.category.repo.CategoryRepository;
import com.luxpretty.app.employee.app.EmployeeService;
import com.luxpretty.app.post.app.PostService;
import com.luxpretty.app.tenant.app.SalonPreviewTokenService;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.domain.TenantStatus;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression — a PRO/EMPLOYEE/ADMIN currently in a tenant context
 * ({@code activeTenantId != null}) must NOT be able to create a client booking
 * via {@code POST /api/salon/{slug}/book}. The booking flow is for "client mode"
 * only (no active tenant).
 *
 * The frontend hides the CTA when not in client mode, but defence in depth lives
 * here: the controller must refuse the request even if the UI is bypassed.
 *
 * Mirrors the direct-controller test pattern used by
 * {@link PublicSalonControllerCancelBookingTests}.
 */
@ExtendWith(MockitoExtension.class)
class PublicSalonControllerBookSecurityTests {

    @Mock private TenantService tenantService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AvailabilityService availabilityService;
    @Mock private BlockedSlotService blockedSlotService;
    @Mock private SlotAvailabilityService slotAvailabilityService;
    @Mock private HolidayAvailabilityService holidayAvailabilityService;
    @Mock private ClosedDaysService closedDaysService;
    @Mock private CareBookingService careBookingService;
    @Mock private UserRepository userRepository;
    @Mock private ClientBookingHistoryService clientBookingHistoryService;
    @Mock private EmployeeService employeeService;
    @Mock private PostService postService;
    @Mock private SalonPreviewTokenService previewTokenService;

    @InjectMocks
    private PublicSalonController controller;

    private Tenant activeTenant;
    private User client;
    private User owner;

    @BeforeEach
    void setUp() {
        activeTenant = Tenant.builder()
                .id(1L)
                .slug("salon-a")
                .name("Salon A")
                .ownerId(100L)
                .status(TenantStatus.ACTIVE)
                .build();
        client = User.builder().id(200L).name("Marie").email("marie@example.com").build();
        owner = User.builder().id(100L).name("Owner").email("owner@example.com").build();
    }

    @Test
    @DisplayName("book — user in client mode (activeTenantId=null) succeeds")
    void book_clientMode_succeeds() {
        when(tenantService.findBySlug("salon-a")).thenReturn(Optional.of(activeTenant));
        when(userRepository.findById(200L)).thenReturn(Optional.of(client));
        when(userRepository.findById(100L)).thenReturn(Optional.of(owner));
        when(careBookingService.createClientBooking(any(), any(), anyString(), any()))
                .thenReturn(new ClientBookingResponse(
                        555L, "Soin", 5000, 60,
                        "2026-05-20", "10:00", "PENDING", "Salon A"));

        // activeTenantId == null -> client mode. We model this by NOT putting an
        // activeTenantId attribute on the principal. The controller must accept.
        UserPrincipal principal = new UserPrincipal(200L, "marie@example.com", "Marie", null);
        ClientBookingRequest req = new ClientBookingRequest(
                10L, LocalDate.now().plusDays(2), "10:00", null);

        ClientBookingResponse result = controller.book("salon-a", req, principal);

        assertThat(result).isNotNull();
        verify(careBookingService).createClientBooking(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("book — user in PRO mode (activeTenantId != null) is rejected with 403")
    void book_inProMode_rejectedWith403() {
        // The pro-mode guard runs before any tenant or user lookup, so no
        // stubbing is needed beyond the principal carrying activeTenantId.

        // PRO/EMPLOYEE/ADMIN in tenant context: principal carries activeTenantId.
        // The booking endpoint must refuse to create a "client booking" in this case.
        UserPrincipal proPrincipal = new UserPrincipal(
                200L, "pro@example.com", "Pro User",
                Map.of("activeTenantId", 42L));
        ClientBookingRequest req = new ClientBookingRequest(
                10L, LocalDate.now().plusDays(2), "10:00", null);

        assertThatThrownBy(() -> controller.book("salon-a", req, proPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        // Defence in depth: the booking service must never be reached.
        verify(careBookingService, never())
                .createClientBooking(any(), any(), anyString(), any());
        verify(clientBookingHistoryService, never())
                .createMirror(any(), any(), anyString(), anyString());
    }
}
