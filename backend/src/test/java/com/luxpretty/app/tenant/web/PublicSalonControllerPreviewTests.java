package com.luxpretty.app.tenant.web;

import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.availability.app.AvailabilityService;
import com.luxpretty.app.availability.app.BlockedSlotService;
import com.luxpretty.app.availability.app.ClosedDaysService;
import com.luxpretty.app.availability.app.HolidayAvailabilityService;
import com.luxpretty.app.availability.app.SlotAvailabilityService;
import com.luxpretty.app.bookings.app.CareBookingService;
import com.luxpretty.app.bookings.app.ClientBookingHistoryService;
import com.luxpretty.app.category.repo.CategoryRepository;
import com.luxpretty.app.employee.app.EmployeeService;
import com.luxpretty.app.post.app.PostService;
import com.luxpretty.app.tenant.app.SalonPreviewTokenService;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.domain.TenantStatus;
import com.luxpretty.app.tenant.web.dto.PublicSalonResponse;
import com.luxpretty.app.users.repo.UserRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @Mock private SalonPreviewTokenService previewTokenService;

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

        ResponseEntity<PublicSalonResponse> response = controller.getSalon(SLUG, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("ACTIVE");
    }

    @Test
    void anonymousGetsNotFoundForDraftSalon() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.DRAFT)));

        ResponseEntity<PublicSalonResponse> response = controller.getSalon(SLUG, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void ownerCanPreviewDraftSalon() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.DRAFT)));

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, principal(OWNER_ID), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("DRAFT");
    }

    @Test
    void nonOwnerGetsNotFoundForDraftSalon() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.DRAFT)));

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, principal(OTHER_USER_ID), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void suspendedSalonIsHiddenFromOwner() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.SUSPENDED)));

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, principal(OWNER_ID), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownSlugReturnsNotFound() {
        when(tenantService.findBySlug("unknown")).thenReturn(Optional.empty());

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon("unknown", principal(OWNER_ID), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anonymousWithValidPreviewTokenCanViewDraftSalon() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.DRAFT)));
        when(previewTokenService.isValidForTenant("good-token", 1L)).thenReturn(true);

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, null, "good-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("DRAFT");
    }

    @Test
    void anonymousWithInvalidPreviewTokenGetsNotFoundForDraft() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.DRAFT)));
        when(previewTokenService.isValidForTenant("bad-token", 1L)).thenReturn(false);

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, null, "bad-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void previewTokenIsIgnoredWhenSalonIsActive() {
        when(tenantService.findBySlug(SLUG))
                .thenReturn(Optional.of(tenantWithStatus(TenantStatus.ACTIVE)));

        ResponseEntity<PublicSalonResponse> response =
                controller.getSalon(SLUG, null, "any-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(previewTokenService, never())
                .isValidForTenant(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong());
    }
}
