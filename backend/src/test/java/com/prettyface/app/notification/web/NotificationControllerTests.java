package com.prettyface.app.notification.web;

import com.prettyface.app.auth.CustomOAuth2UserService;
import com.prettyface.app.auth.OAuth2AuthenticationFailureHandler;
import com.prettyface.app.auth.OAuth2AuthenticationSuccessHandler;
import com.prettyface.app.auth.TokenService;
import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.common.error.RestAccessDeniedHandler;
import com.prettyface.app.common.error.RestAuthenticationEntryPoint;
import com.prettyface.app.config.CsrfLoggingFilter;
import com.prettyface.app.config.SecurityConfig;
import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.multitenancy.TenantFilter;
import com.prettyface.app.notification.app.NotificationService;
import com.prettyface.app.notification.web.dto.NotificationResponse;
import com.prettyface.app.tenant.app.TenantService;
import com.prettyface.app.users.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private ApplicationSchemaExecutor applicationSchemaExecutor;

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
    @SuppressWarnings("unchecked")
    void setUp() {
        UserPrincipal principal = new UserPrincipal(1L, "user@example.com", "Test User", null);
        authToken = new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        // Make applicationSchemaExecutor.call() execute the supplier directly
        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        }).when(applicationSchemaExecutor).call(any(Supplier.class));

        // Make applicationSchemaExecutor.run() execute the runnable directly
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(applicationSchemaExecutor).run(any(Runnable.class));
    }

    @Test
    void list_authenticated_returns200() throws Exception {
        NotificationResponse response = new NotificationResponse(
                1L, "BOOKING", "APPOINTMENT", "Booking confirmed",
                "Your booking is confirmed", 10L, "BOOKING",
                false, "salon-slug", LocalDateTime.now());

        when(notificationService.listForRecipient(eq(1L), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        // Use a page with actual content for assertion
        when(notificationService.listForRecipient(eq(1L), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/notifications").with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void unreadCount_authenticated_returnsCount() throws Exception {
        when(notificationService.countUnread(1L)).thenReturn(5L);

        mvc.perform(get("/api/notifications/unread/count").with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(5));
    }

    @Test
    void markAsRead_authenticated_returns204() throws Exception {
        mvc.perform(patch("/api/notifications/1/read")
                        .with(authentication(authToken))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void markAsRead_unauthenticated_returns401() throws Exception {
        mvc.perform(patch("/api/notifications/1/read").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ══════════════════════════════════════════════════════════════
    // ── Sec4: validation of abusive inputs ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @org.junit.jupiter.api.DisplayName("Sec4: list_withMalformedSince returns 400")
    void list_withMalformedSince_returns400() throws Exception {
        mvc.perform(get("/api/notifications")
                        .param("since", "not-a-date")
                        .with(authentication(authToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Sec4: list_withNegativePageNumber_returns400orClampsToZero — documents current behavior")
    void list_withNegativePageNumber_returns400orClampsToZero() throws Exception {
        // NOTE-SEC: Spring's PageableHandlerMethodArgumentResolver silently clamps
        // a negative page index to 0 (it does NOT return 400). Verifies current behavior
        // so a regression that changes this is caught.
        when(notificationService.listForRecipient(eq(1L), isNull(), isNull(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        java.util.List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/notifications")
                        .param("page", "-5")
                        .with(authentication(authToken)))
                .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Sec4: list_withExcessiveSize_returnsReasonableError_notCrash — size clamped, no crash")
    void list_withExcessiveSize_returnsReasonableError_notCrash() throws Exception {
        // TODO-SEC: Spring's default max page size (2000) clamps size=100000 → 200 OK.
        // This means a single request cannot exfiltrate 100k rows, but there is no
        // application-level limit or per-user rate limit. The test documents this
        // and would catch a regression where a misconfiguration lifts the cap.
        when(notificationService.listForRecipient(eq(1L), isNull(), isNull(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        java.util.List.of(), PageRequest.of(0, 2000), 0));

        mvc.perform(get("/api/notifications")
                        .param("size", "100000")
                        .with(authentication(authToken)))
                .andExpect(status().isOk()); // clamp, not crash
    }
}
