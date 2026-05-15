package com.luxpretty.app.bookings.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.auth.CustomOAuth2UserService;
import com.luxpretty.app.auth.CustomOidcUserService;
import com.luxpretty.app.auth.OAuth2AuthenticationFailureHandler;
import com.luxpretty.app.auth.OAuth2AuthenticationSuccessHandler;
import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.bookings.app.BookingPolicyService;
import com.luxpretty.app.bookings.domain.BookingPolicy;
import com.luxpretty.app.bookings.web.dto.UpdateBookingPolicyRequest;
import com.luxpretty.app.common.error.GlobalExceptionHandler;
import com.luxpretty.app.common.error.RestAccessDeniedHandler;
import com.luxpretty.app.common.error.RestAuthenticationEntryPoint;
import com.luxpretty.app.config.CsrfLoggingFilter;
import com.luxpretty.app.config.SecurityConfig;
import com.luxpretty.app.multitenancy.TenantFilter;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingPolicyController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class BookingPolicyControllerTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean BookingPolicyService service;
    @MockBean TokenService tokenService;
    @MockBean UserRoleService userRoleService;
    @MockBean UserRepository userRepository;
    @MockBean CustomOAuth2UserService customOAuth2UserService;
    @MockBean CustomOidcUserService customOidcUserService;
    @MockBean OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean TenantService tenantService;

    @SpyBean RestAccessDeniedHandler restAccessDeniedHandler;
    @SpyBean RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    @SpyBean TenantFilter tenantFilter;
    @SpyBean CsrfLoggingFilter csrfLoggingFilter;

    private BookingPolicy fixture(int perDay, int perWeek) {
        BookingPolicy p = new BookingPolicy();
        p.setId(1L);
        p.setMaxBookingsPerDayPerClient(perDay);
        p.setMaxBookingsPerWeekForNewClient(perWeek);
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    @Test
    void getRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/pro/booking-policy"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void getForbiddenForNonPro() throws Exception {
        mvc.perform(get("/api/pro/booking-policy"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PRO")
    void getReturnsCurrentPolicy() throws Exception {
        when(service.getOrCreatePolicy()).thenReturn(fixture(1, 1));
        mvc.perform(get("/api/pro/booking-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxBookingsPerDayPerClient").value(1))
                .andExpect(jsonPath("$.maxBookingsPerWeekForNewClient").value(1));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void putAcceptsValidUpdate() throws Exception {
        when(service.update(anyInt(), anyInt())).thenReturn(fixture(3, 2));
        UpdateBookingPolicyRequest body = new UpdateBookingPolicyRequest(3, 2);
        mvc.perform(put("/api/pro/booking-policy")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxBookingsPerDayPerClient").value(3))
                .andExpect(jsonPath("$.maxBookingsPerWeekForNewClient").value(2));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void putRejectsZero() throws Exception {
        UpdateBookingPolicyRequest body = new UpdateBookingPolicyRequest(0, 1);
        mvc.perform(put("/api/pro/booking-policy")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PRO")
    void putRejectsAboveMax() throws Exception {
        UpdateBookingPolicyRequest body = new UpdateBookingPolicyRequest(1, 99);
        mvc.perform(put("/api/pro/booking-policy")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
