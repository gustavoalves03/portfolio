package com.luxpretty.app.bookings.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.auth.CustomOAuth2UserService;
import com.luxpretty.app.auth.CustomOidcUserService;
import com.luxpretty.app.auth.OAuth2AuthenticationFailureHandler;
import com.luxpretty.app.auth.OAuth2AuthenticationSuccessHandler;
import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.bookings.app.CareBookingService;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.bookings.web.dto.CareBookingRequest;
import com.luxpretty.app.common.error.GlobalExceptionHandler;
import com.luxpretty.app.common.error.ResourceNotFoundException;
import com.luxpretty.app.common.error.RestAccessDeniedHandler;
import com.luxpretty.app.common.error.RestAuthenticationEntryPoint;
import com.luxpretty.app.config.CsrfLoggingFilter;
import com.luxpretty.app.config.SecurityConfig;
import com.luxpretty.app.multitenancy.ApplicationSchemaExecutor;
import com.luxpretty.app.multitenancy.TenantFilter;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sec4 — validation of abusive inputs on /api/bookings/detailed.
 */
@WebMvcTest(CareBookingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@org.springframework.test.context.TestPropertySource(properties = {
    "spring.data.web.pageable.max-page-size=100",
})
class CareBookingControllerValidationTests {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CareBookingService service;
    @MockBean private ApplicationSchemaExecutor applicationSchemaExecutor;
    @MockBean private TokenService tokenService;
    @MockBean private UserRepository userRepository;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private CustomOidcUserService customOidcUserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private TenantService tenantService;

    @SpyBean private RestAccessDeniedHandler restAccessDeniedHandler;
    @SpyBean private RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    @SpyBean private TenantFilter tenantFilter;
    @SpyBean private CsrfLoggingFilter csrfLoggingFilter;

    private UsernamePasswordAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        UserPrincipal principal = new UserPrincipal(1L, "user@example.com", "Test User", null);
        authToken = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("Sec4: listDetailed_withInvertedDateRange_returnsEmptyOr400 — documents current behavior")
    void listDetailed_withInvertedDateRange_returnsEmptyOr400() throws Exception {
        // NOTE-SEC: No server-side validation for from > to. The service/repo is called
        // with the inverted range; the query naturally returns an empty page (from < to
        // is impossible), so no crash and no data leak. Documented: if business rules
        // require a 400 here, add a @RequestParam-level guard in the controller.
        when(service.listDetailed(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/bookings/detailed")
                        .param("from", "2099-01-01")
                        .param("to", "1900-01-01")
                        .with(authentication(authToken)))
                .andExpect(status().isOk()); // empty page, not 400
    }

    @Test
    @DisplayName("Sec4: listDetailed_withInvalidDateFormat returns 400")
    void listDetailed_withInvalidDateFormat_returns400() throws Exception {
        mvc.perform(get("/api/bookings/detailed")
                        .param("from", "not-a-date")
                        .with(authentication(authToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Sec4: listDetailed_withInvalidStatusEnum returns 400")
    void listDetailed_withInvalidStatusEnum_returns400() throws Exception {
        mvc.perform(get("/api/bookings/detailed")
                        .param("status", "NOT_A_REAL_STATUS")
                        .with(authentication(authToken)))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lot4 #15/#17/#18: POST /api/bookings admin-create payload ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Lot4#15: create_withInexistentCareId — service throws ResourceNotFoundException → 404")
    void create_withInexistentCareId_returns404() throws Exception {
        // NOTE-SEC: CareBookingRequest declares @NotNull on careId but has no existence check in the DTO.
        // The service layer is responsible: CareBookingService.create() throws
        // ResourceNotFoundException("Care not found: ...") which GlobalExceptionHandler maps to 404.
        // (Plain IllegalArgumentException now maps to 400 — reserved for validation failures.)
        when(service.create(any(CareBookingRequest.class)))
                .thenThrow(new ResourceNotFoundException("Care not found: 999999"));

        CareBookingRequest req = new CareBookingRequest(
                1L, 999999L, 1,
                LocalDate.now().plusDays(7),
                LocalTime.of(10, 0),
                CareBookingStatus.CONFIRMED, null);

        mvc.perform(post("/api/bookings")
                        .with(authentication(authToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Lot4#17: create_withBookingDateInPast_returns400 — @FutureOrPresent rejects past dates")
    void create_withBookingDateInPast_returns400() throws Exception {
        // @FutureOrPresent on CareBookingRequest.appointmentDate causes @Valid to reject
        // past dates at the controller layer with 400 Bad Request.
        CareBookingRequest req = new CareBookingRequest(
                1L, 10L, 1,
                LocalDate.now().minusDays(5),
                LocalTime.of(10, 0),
                CareBookingStatus.CONFIRMED, null);

        mvc.perform(post("/api/bookings")
                        .with(authentication(authToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest()); // 400
    }

    @Test
    @DisplayName("Lot4#18: create_withTimeOutsideOpeningHours_rejected — service enforces slot availability")
    void create_withTimeOutsideOpeningHours_rejected() throws Exception {
        // Admin create() now invokes SlotAvailabilityService; 03:00 is outside opening hours
        // so the service throws CONFLICT, which GlobalExceptionHandler maps to 409.
        when(service.create(any(CareBookingRequest.class)))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "The requested time slot is not available."));

        CareBookingRequest req = new CareBookingRequest(
                1L, 10L, 1,
                LocalDate.now().plusDays(7),
                LocalTime.of(3, 0), // 03:00 — well outside any realistic opening hours
                CareBookingStatus.CONFIRMED, null);

        mvc.perform(post("/api/bookings")
                        .with(authentication(authToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict()); // 409
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lot4 #22: pagination clamp on /api/bookings/detailed ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Lot4#22: listDetailed_withExcessiveSize_clampedToAppMax — max-page-size=100")
    void listDetailed_withExcessiveSize_clampedToAppMax() throws Exception {
        // NOTE-SEC: spring.data.web.pageable.max-page-size=100 caps any ?size=. Verifies
        // that /api/bookings/detailed also respects the clamp (already tested on
        // NotificationController) — coverage on a second Page-returning endpoint.
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        when(service.listDetailed(any(), any(), any(), any(), captor.capture(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mvc.perform(get("/api/bookings/detailed")
                        .param("size", "100000")
                        .with(authentication(authToken)))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize())
                .isLessThanOrEqualTo(100);
    }
}
