package com.luxpretty.app.tenant.web;

import com.luxpretty.app.auth.CustomOAuth2UserService;
import com.luxpretty.app.auth.CustomOidcUserService;
import com.luxpretty.app.auth.OAuth2AuthenticationFailureHandler;
import com.luxpretty.app.auth.OAuth2AuthenticationSuccessHandler;
import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.availability.app.AvailabilityService;
import com.luxpretty.app.availability.app.BlockedSlotService;
import com.luxpretty.app.availability.app.ClosedDaysService;
import com.luxpretty.app.availability.app.HolidayAvailabilityService;
import com.luxpretty.app.availability.app.SlotAvailabilityService;
import com.luxpretty.app.bookings.app.CareBookingService;
import com.luxpretty.app.bookings.app.ClientBookingHistoryService;
import com.luxpretty.app.bookings.web.dto.ClientBookingResponse;
import com.luxpretty.app.category.repo.CategoryRepository;
import com.luxpretty.app.common.error.GlobalExceptionHandler;
import com.luxpretty.app.common.error.RestAccessDeniedHandler;
import com.luxpretty.app.common.error.RestAuthenticationEntryPoint;
import com.luxpretty.app.config.CsrfLoggingFilter;
import com.luxpretty.app.config.SecurityConfig;
import com.luxpretty.app.employee.app.EmployeeService;
import com.luxpretty.app.multitenancy.TenantFilter;
import com.luxpretty.app.post.app.PostService;
import com.luxpretty.app.tenant.app.SalonPreviewTokenService;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.domain.TenantStatus;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression — POST /api/salon/{slug}/book must accept a well-formed JSON body
 * with the documented field names ({@code careId}, {@code appointmentDate} as
 * "yyyy-MM-dd", {@code appointmentTime} as "HH:mm", optional {@code employeeId}).
 *
 * The frontend currently sends this exact shape and the server returns
 * "Malformed request body" — this suite reproduces that scenario and pins the
 * contract.
 */
@WebMvcTest(PublicSalonController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PublicSalonControllerBookValidationTests {

    @Autowired private MockMvc mvc;

    @MockBean private TenantService tenantService;
    @MockBean private CategoryRepository categoryRepository;
    @MockBean private AvailabilityService availabilityService;
    @MockBean private BlockedSlotService blockedSlotService;
    @MockBean private SlotAvailabilityService slotAvailabilityService;
    @MockBean private HolidayAvailabilityService holidayAvailabilityService;
    @MockBean private ClosedDaysService closedDaysService;
    @MockBean private CareBookingService careBookingService;
    @MockBean private UserRepository userRepository;
    @MockBean private ClientBookingHistoryService clientBookingHistoryService;
    @MockBean private EmployeeService employeeService;
    @MockBean private PostService postService;
    @MockBean private SalonPreviewTokenService previewTokenService;

    @MockBean private TokenService tokenService;
    @MockBean private UserRoleService userRoleService;
    @MockBean private TenantRepository tenantRepository;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private CustomOidcUserService customOidcUserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @SpyBean private RestAccessDeniedHandler restAccessDeniedHandler;
    @SpyBean private RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    @SpyBean private TenantFilter tenantFilter;
    @SpyBean private CsrfLoggingFilter csrfLoggingFilter;

    private Tenant active() {
        return Tenant.builder()
                .id(1L).slug("salon-a").name("Salon A")
                .ownerId(100L).status(TenantStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("book — well-formed JSON body is NOT rejected as 'Malformed request body'")
    @WithMockUser(username = "marie@example.com")
    void book_wellFormedBody_isAccepted() throws Exception {
        when(tenantService.findBySlug("salon-a")).thenReturn(Optional.of(active()));
        when(userRepository.findById(any())).thenReturn(
                Optional.of(User.builder().id(200L).email("marie@example.com").name("Marie").build()));
        when(careBookingService.createClientBooking(any(), any(), anyString(), any()))
                .thenReturn(new ClientBookingResponse(
                        555L, "Soin", 5000, 60,
                        "2026-05-20", "10:00", "PENDING", "Salon A"));

        String body = """
                {
                  "careId": 10,
                  "appointmentDate": "2026-05-20",
                  "appointmentTime": "10:00",
                  "employeeId": null
                }
                """;

        mvc.perform(post("/api/salon/salon-a/book")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                // The bug under regression: the body must not be rejected as
                // malformed. A 400 with "Malformed request body" is exactly what
                // we are catching against — any other outcome (2xx or another
                // 4xx like 404/409) is acceptable here.
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    String content = result.getResponse().getContentAsString();
                    if (s == 400 && content.contains("Malformed request body")) {
                        throw new AssertionError(
                                "Regression: backend rejected a well-formed booking body as 'Malformed request body'. "
                                        + "Response: " + content);
                    }
                });
    }

    @Test
    @DisplayName("book — invalid date format → 400 (validation error, not 5xx)")
    @WithMockUser
    void book_invalidDate_returns400() throws Exception {
        when(tenantService.findBySlug("salon-a")).thenReturn(Optional.of(active()));

        String body = """
                {
                  "careId": 10,
                  "appointmentDate": "20-05-2026",
                  "appointmentTime": "10:00"
                }
                """;

        mvc.perform(post("/api/salon/salon-a/book")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("book — missing required field (careId) → 400 validation error")
    @WithMockUser
    void book_missingCareId_returns400() throws Exception {
        when(tenantService.findBySlug("salon-a")).thenReturn(Optional.of(active()));

        String body = """
                {
                  "appointmentDate": "2026-05-20",
                  "appointmentTime": "10:00"
                }
                """;

        mvc.perform(post("/api/salon/salon-a/book")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
