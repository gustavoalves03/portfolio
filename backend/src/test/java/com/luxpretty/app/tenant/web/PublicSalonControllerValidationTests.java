package com.luxpretty.app.tenant.web;

import com.luxpretty.app.auth.CustomOAuth2UserService;
import com.luxpretty.app.auth.CustomOidcUserService;
import com.luxpretty.app.auth.OAuth2AuthenticationFailureHandler;
import com.luxpretty.app.auth.OAuth2AuthenticationSuccessHandler;
import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.availability.app.AvailabilityService;
import com.luxpretty.app.availability.app.BlockedSlotService;
import com.luxpretty.app.availability.app.HolidayAvailabilityService;
import com.luxpretty.app.availability.app.SlotAvailabilityService;
import com.luxpretty.app.bookings.app.CareBookingService;
import com.luxpretty.app.bookings.app.ClientBookingHistoryService;
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
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot4 #23 — invalid date params on /api/salon/{slug}/available-slots
 * (second additional date-accepting endpoint required).
 *
 * The endpoint is public (permitAll on GET /api/salon/**) so no auth is required.
 */
@WebMvcTest(PublicSalonController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PublicSalonControllerValidationTests {

    @Autowired private MockMvc mvc;

    @MockBean private TenantService tenantService;
    @MockBean private CategoryRepository categoryRepository;
    @MockBean private AvailabilityService availabilityService;
    @MockBean private BlockedSlotService blockedSlotService;
    @MockBean private SlotAvailabilityService slotAvailabilityService;
    @MockBean private HolidayAvailabilityService holidayAvailabilityService;
    @MockBean private com.luxpretty.app.availability.app.ClosedDaysService closedDaysService;
    @MockBean private CareBookingService careBookingService;
    @MockBean private UserRepository userRepository;
    @MockBean private ClientBookingHistoryService clientBookingHistoryService;
    @MockBean private EmployeeService employeeService;
    @MockBean private PostService postService;
    @MockBean private SalonPreviewTokenService previewTokenService;

    @MockBean private TokenService tokenService;
    @MockBean private UserRoleService userRoleService;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private CustomOidcUserService customOidcUserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @SpyBean private RestAccessDeniedHandler restAccessDeniedHandler;
    @SpyBean private RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    @SpyBean private TenantFilter tenantFilter;
    @SpyBean private CsrfLoggingFilter csrfLoggingFilter;

    @Test
    @DisplayName("Lot4#23: publicAvailableSlots_withMalformedDate (date=abc) → 400")
    void publicAvailableSlots_withMalformedDate_returns400() throws Exception {
        mvc.perform(get("/api/salon/my-salon/available-slots")
                        .param("careId", "10")
                        .param("date", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Lot4#23: publicAvailableSlots_withImpossibleDate (2026-02-30) → 400")
    void publicAvailableSlots_withImpossibleDate_returns400() throws Exception {
        mvc.perform(get("/api/salon/my-salon/available-slots")
                        .param("careId", "10")
                        .param("date", "2026-02-30"))
                .andExpect(status().isBadRequest());
    }
}
