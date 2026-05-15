package com.luxpretty.app.employee.web;

import com.luxpretty.app.auth.CustomOAuth2UserService;
import com.luxpretty.app.auth.CustomOidcUserService;
import com.luxpretty.app.auth.OAuth2AuthenticationFailureHandler;
import com.luxpretty.app.auth.OAuth2AuthenticationSuccessHandler;
import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.common.error.GlobalExceptionHandler;
import com.luxpretty.app.common.error.RestAccessDeniedHandler;
import com.luxpretty.app.common.error.RestAuthenticationEntryPoint;
import com.luxpretty.app.config.CsrfLoggingFilter;
import com.luxpretty.app.config.SecurityConfig;
import com.luxpretty.app.employee.app.EmployeeService;
import com.luxpretty.app.employee.web.dto.EmployeeSlimResponse;
import com.luxpretty.app.multitenancy.TenantFilter;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class EmployeeControllerTests {

    @Autowired MockMvc mvc;

    @MockBean EmployeeService service;
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

    @Test
    @WithMockUser(roles = "PRO")
    void list_withoutCareId_returnsAllEmployees() throws Exception {
        when(service.listAll()).thenReturn(List.of());
        mvc.perform(get("/api/pro/employees"))
           .andExpect(status().isOk());
        verify(service).listAll();
    }

    @Test
    @WithMockUser(roles = "PRO")
    void list_withCareId_filtersByCare() throws Exception {
        when(service.listForCare(42L))
            .thenReturn(List.of(new EmployeeSlimResponse(7L, "Alice", null)));
        mvc.perform(get("/api/pro/employees").param("careId", "42"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(7))
           .andExpect(jsonPath("$[0].name").value("Alice"));
        verify(service).listForCare(eq(42L));
    }
}
