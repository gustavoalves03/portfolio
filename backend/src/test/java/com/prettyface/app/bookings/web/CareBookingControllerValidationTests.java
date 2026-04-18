package com.prettyface.app.bookings.web;

import com.prettyface.app.auth.CustomOAuth2UserService;
import com.prettyface.app.auth.OAuth2AuthenticationFailureHandler;
import com.prettyface.app.auth.OAuth2AuthenticationSuccessHandler;
import com.prettyface.app.auth.TokenService;
import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.bookings.app.CareBookingService;
import com.prettyface.app.common.error.RestAccessDeniedHandler;
import com.prettyface.app.common.error.RestAuthenticationEntryPoint;
import com.prettyface.app.config.CsrfLoggingFilter;
import com.prettyface.app.config.SecurityConfig;
import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.multitenancy.TenantFilter;
import com.prettyface.app.tenant.app.TenantService;
import com.prettyface.app.users.repo.UserRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sec4 — validation of abusive inputs on /api/bookings/detailed.
 */
@WebMvcTest(CareBookingController.class)
@Import(SecurityConfig.class)
class CareBookingControllerValidationTests {

    @Autowired private MockMvc mvc;

    @MockBean private CareBookingService service;
    @MockBean private ApplicationSchemaExecutor applicationSchemaExecutor;
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
        when(service.listDetailed(any(), any(), any(), any(), any()))
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
}
