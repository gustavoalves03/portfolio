package com.prettyface.app.availability.web;

import com.prettyface.app.auth.CustomOAuth2UserService;
import com.prettyface.app.auth.OAuth2AuthenticationFailureHandler;
import com.prettyface.app.auth.OAuth2AuthenticationSuccessHandler;
import com.prettyface.app.auth.TokenService;
import com.prettyface.app.availability.app.AvailabilityService;
import com.prettyface.app.availability.app.SlotAvailabilityService;
import com.prettyface.app.common.error.GlobalExceptionHandler;
import com.prettyface.app.common.error.RestAccessDeniedHandler;
import com.prettyface.app.common.error.RestAuthenticationEntryPoint;
import com.prettyface.app.config.CsrfLoggingFilter;
import com.prettyface.app.config.SecurityConfig;
import com.prettyface.app.multitenancy.TenantFilter;
import com.prettyface.app.tenant.app.TenantService;
import com.prettyface.app.users.repo.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot4 #23 — invalid date params on /api/pro/opening-hours/available-slots
 * (one of the two additional date-accepting endpoints required beyond
 * /api/bookings/detailed and /api/notifications).
 */
@WebMvcTest(AvailabilityController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AvailabilityControllerValidationTests {

    @Autowired private MockMvc mvc;

    @MockBean private AvailabilityService service;
    @MockBean private SlotAvailabilityService slotAvailabilityService;
    @MockBean private TokenService tokenService;
    @MockBean private UserRepository userRepository;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private TenantService tenantService;

    @SpyBean private RestAccessDeniedHandler restAccessDeniedHandler;
    @SpyBean private RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    @SpyBean private TenantFilter tenantFilter;
    @SpyBean private CsrfLoggingFilter csrfLoggingFilter;

    @Test
    @WithMockUser(roles = "PRO")
    @DisplayName("Lot4#23: availableSlots_withMalformedDate (from=abc) → 400")
    void availableSlots_withMalformedDate_returns400() throws Exception {
        // @DateTimeFormat(iso = DATE) + MethodArgumentTypeMismatchException
        // is now mapped to 400 by GlobalExceptionHandler (was 404 before the fix).
        mvc.perform(get("/api/pro/opening-hours/available-slots")
                        .param("careId", "10")
                        .param("date", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PRO")
    @DisplayName("Lot4#23: availableSlots_withImpossibleCalendarDate (2026-02-30) → 400")
    void availableSlots_withImpossibleDate_returns400() throws Exception {
        mvc.perform(get("/api/pro/opening-hours/available-slots")
                        .param("careId", "10")
                        .param("date", "2026-02-30"))
                .andExpect(status().isBadRequest());
    }
}
