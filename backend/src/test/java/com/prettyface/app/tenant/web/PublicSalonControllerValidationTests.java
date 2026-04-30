package com.prettyface.app.tenant.web;

import com.prettyface.app.auth.CustomOAuth2UserService;
import com.prettyface.app.auth.OAuth2AuthenticationFailureHandler;
import com.prettyface.app.auth.OAuth2AuthenticationSuccessHandler;
import com.prettyface.app.auth.TokenService;
import com.prettyface.app.availability.app.AvailabilityService;
import com.prettyface.app.availability.app.BlockedSlotService;
import com.prettyface.app.availability.app.HolidayAvailabilityService;
import com.prettyface.app.availability.app.SlotAvailabilityService;
import com.prettyface.app.bookings.app.CareBookingService;
import com.prettyface.app.bookings.app.ClientBookingHistoryService;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.common.error.GlobalExceptionHandler;
import com.prettyface.app.common.error.RestAccessDeniedHandler;
import com.prettyface.app.common.error.RestAuthenticationEntryPoint;
import com.prettyface.app.config.CsrfLoggingFilter;
import com.prettyface.app.config.SecurityConfig;
import com.prettyface.app.employee.app.EmployeeService;
import com.prettyface.app.multitenancy.TenantFilter;
import com.prettyface.app.post.app.PostService;
import com.prettyface.app.tenant.app.TenantService;
import com.prettyface.app.users.repo.UserRepository;
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
    @MockBean private com.prettyface.app.availability.app.ClosedDaysService closedDaysService;
    @MockBean private CareBookingService careBookingService;
    @MockBean private UserRepository userRepository;
    @MockBean private ClientBookingHistoryService clientBookingHistoryService;
    @MockBean private EmployeeService employeeService;
    @MockBean private PostService postService;

    @MockBean private TokenService tokenService;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
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
