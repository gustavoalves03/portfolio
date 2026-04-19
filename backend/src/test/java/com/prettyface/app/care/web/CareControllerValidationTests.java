package com.prettyface.app.care.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prettyface.app.auth.CustomOAuth2UserService;
import com.prettyface.app.auth.OAuth2AuthenticationFailureHandler;
import com.prettyface.app.auth.OAuth2AuthenticationSuccessHandler;
import com.prettyface.app.auth.TokenService;
import com.prettyface.app.care.app.CareService;
import com.prettyface.app.care.web.dto.CareResponse;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot4 — validation on /api/care:
 *  - #22: pagination clamp (third Page endpoint tested beyond Notification + bookings/detailed)
 *  - #30: negative / zero price rejected via @Positive on CareRequest
 */
@WebMvcTest(CareController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@org.springframework.test.context.TestPropertySource(properties = {
    "spring.data.web.pageable.max-page-size=100",
})
class CareControllerValidationTests {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CareService service;
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

    // ══════════════════════════════════════════════════════════════
    // ── Lot4 #22: pagination clamp ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Lot4#22: list_withExcessiveSize_clampedToAppMax — /api/care respects max-page-size=100")
    void list_withExcessiveSize_clampedToAppMax() throws Exception {
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        when(service.list(captor.capture()))
                .thenReturn(new PageImpl<>(List.<CareResponse>of(), PageRequest.of(0, 100), 0));

        mvc.perform(get("/api/care")
                        .param("size", "100000"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize())
                .isLessThanOrEqualTo(100);
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lot4 #30: POST /api/care with negative/zero price → 400 ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "PRO")
    @DisplayName("Lot4#30: create_withNegativePrice → 400 (via @Positive on CareRequest.price)")
    void create_withNegativePrice_returns400() throws Exception {
        // CareRequest: @NotNull @Positive Integer price
        String body = """
                {
                  "name": "Soin",
                  "price": -100,
                  "description": "desc",
                  "duration": 30,
                  "status": "ACTIVE",
                  "categoryId": 1
                }
                """;

        mvc.perform(post("/api/care")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PRO")
    @DisplayName("Lot4#30: create_withZeroPrice → 400 (@Positive rejects 0)")
    void create_withZeroPrice_returns400() throws Exception {
        String body = """
                {
                  "name": "Soin",
                  "price": 0,
                  "description": "desc",
                  "duration": 30,
                  "status": "ACTIVE",
                  "categoryId": 1
                }
                """;

        mvc.perform(post("/api/care")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
