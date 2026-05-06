package com.prettyface.app.tenant.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.availability.app.AvailabilityService;
import com.prettyface.app.availability.app.BlockedSlotService;
import com.prettyface.app.availability.app.ClosedDaysService;
import com.prettyface.app.availability.app.HolidayAvailabilityService;
import com.prettyface.app.availability.app.SlotAvailabilityService;
import com.prettyface.app.bookings.app.CareBookingService;
import com.prettyface.app.bookings.app.ClientBookingHistoryService;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.employee.app.EmployeeService;
import com.prettyface.app.post.app.PostService;
import com.prettyface.app.tenant.app.TenantService;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.domain.TenantStatus;
import com.prettyface.app.tenant.web.dto.PublicSalonResponse;
import com.prettyface.app.users.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Storefront access policy tests.
 *
 * - ACTIVE salon → visible to anyone (anonymous + any logged-in user).
 * - DRAFT salon → visible only to the tenant owner (preview mode).
 * - SUSPENDED / DELETED salon → 404 for everyone.
 *
 * Direct controller invocation (no Spring context) — pins behavior at the
 * controller layer where the policy lives.
 */
@ExtendWith(MockitoExtension.class)
class PublicSalonControllerPreviewTests {

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

    @InjectMocks
    private PublicSalonController controller;

    private static final long OWNER_ID = 42L;
    private static final long OTHER_USER_ID = 99L;
    private static final String SLUG = "demo";

    @BeforeEach
    void setUp() {
        // Categories repo is invoked when we successfully serve the storefront.
        // Empty list is enough — we only check the response status and the
        // status field on the body.
        lenient().when(categoryRepository.findAllWithCaresFull()).thenReturn(List.of());
    }

    private Tenant tenantWithStatus(TenantStatus status) {
        return Tenant.builder()
                .id(1L)
                .slug(SLUG)
                .name("Demo Salon")
                .ownerId(OWNER_ID)
                .status(status)
                .build();
    }

    private UserPrincipal principal(long id) {
        // UserPrincipal has a 4-arg @AllArgsConstructor: (id, email, name, attributes).
        return new UserPrincipal(id, "user@example.com", "User", null);
    }

    @Test
    void anonymousCanViewActiveSalon() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.ACTIVE)));

        ResponseEntity<PublicSalonResponse> response = controller.getSalon(SLUG, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("ACTIVE");
    }

    @Test
    void anonymousGetsNotFoundForDraftSalon() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.DRAFT)));

        ResponseEntity<PublicSalonResponse> response = controller.getSalon(SLUG, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void ownerCanPreviewDraftSalon() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.DRAFT)));

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, principal(OWNER_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("DRAFT");
    }

    @Test
    void nonOwnerGetsNotFoundForDraftSalon() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.DRAFT)));

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, principal(OTHER_USER_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void suspendedSalonIsHiddenFromOwner() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.SUSPENDED)));

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, principal(OWNER_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownSlugReturnsNotFound() {
        when(tenantService.findBySlug("unknown")).thenReturn(Optional.empty());

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon("unknown", principal(OWNER_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
