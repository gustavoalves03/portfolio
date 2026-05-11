package com.luxpretty.app.proinvoice.web;

import com.luxpretty.app.auth.CustomOAuth2UserService;
import com.luxpretty.app.auth.CustomOidcUserService;
import com.luxpretty.app.auth.OAuth2AuthenticationFailureHandler;
import com.luxpretty.app.auth.OAuth2AuthenticationSuccessHandler;
import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.common.error.RestAccessDeniedHandler;
import com.luxpretty.app.common.error.RestAuthenticationEntryPoint;
import com.luxpretty.app.config.CsrfLoggingFilter;
import com.luxpretty.app.config.SecurityConfig;
import com.luxpretty.app.multitenancy.TenantFilter;
import com.luxpretty.app.proinvoice.app.ProInvoicePdfRenderer;
import com.luxpretty.app.proinvoice.app.ProInvoiceService;
import com.luxpretty.app.proinvoice.domain.ProInvoice;
import com.luxpretty.app.proinvoice.domain.ProInvoiceStatus;
import com.luxpretty.app.proinvoice.web.mapper.ProInvoiceMapper;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProInvoiceController.class)
@Import({SecurityConfig.class, ProInvoiceMapper.class})
class ProInvoiceControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ProInvoiceService service;

    @MockBean
    private ProInvoicePdfRenderer pdfRenderer;

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

    // ── helpers ─────────────────────────────────────────────────────────────

    private ProInvoice sample() {
        ProInvoice inv = new ProInvoice();
        inv.setId(7L);
        inv.setNumberLabel("PRO-2026-0001");
        inv.setIssuedAt(LocalDateTime.now());
        inv.setStatus(ProInvoiceStatus.PAID);
        inv.setAmountSubtotal(new BigDecimal("100.00"));
        inv.setAmountTax(new BigDecimal("17.00"));
        inv.setAmountTotal(new BigDecimal("117.00"));
        inv.setTaxRate(new BigDecimal("17.00"));
        inv.setCurrency("EUR");
        return inv;
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "PRO")
    void list_returns_page() throws Exception {
        when(service.search(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sample())));

        mvc.perform(get("/api/pro/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].numberLabel").value("PRO-2026-0001"));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void get_returns_invoice() throws Exception {
        when(service.get(7L)).thenReturn(sample());

        mvc.perform(get("/api/pro/invoices/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numberLabel").value("PRO-2026-0001"));
    }

    @Test
    void list_requires_auth() throws Exception {
        mvc.perform(get("/api/pro/invoices"))
                .andExpect(status().isUnauthorized());
    }
}
