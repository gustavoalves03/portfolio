package com.luxpretty.app.me.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.auth.CustomOAuth2UserService;
import com.luxpretty.app.auth.CustomOidcUserService;
import com.luxpretty.app.auth.OAuth2AuthenticationFailureHandler;
import com.luxpretty.app.auth.OAuth2AuthenticationSuccessHandler;
import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.common.error.GlobalExceptionHandler;
import com.luxpretty.app.config.SecurityConfig;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MeControllerTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockBean UserRoleService userRoleService;
    @MockBean TenantRepository tenantRepository;
    @MockBean UserRepository userRepository;
    @MockBean TokenService tokenService;
    // Mocks required by SecurityConfig
    @MockBean CustomOAuth2UserService customOAuth2UserService;
    @MockBean CustomOidcUserService customOidcUserService;
    @MockBean OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean TenantService tenantService;

    @org.springframework.boot.test.mock.mockito.SpyBean
    com.luxpretty.app.common.error.RestAccessDeniedHandler restAccessDeniedHandler;
    @org.springframework.boot.test.mock.mockito.SpyBean
    com.luxpretty.app.common.error.RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    @org.springframework.boot.test.mock.mockito.SpyBean
    com.luxpretty.app.multitenancy.TenantFilter tenantFilter;
    @org.springframework.boot.test.mock.mockito.SpyBean
    com.luxpretty.app.config.CsrfLoggingFilter csrfLoggingFilter;

    private static org.springframework.security.core.Authentication authFor(Long id) {
        User u = User.builder().id(id).email("a@a.com").name("A").build();
        UserPrincipal p = UserPrincipal.create(u);
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                p, null, List.of());
    }

    @Test
    void myTenants_returnsTenantSummaries_forUserWithAssignments() throws Exception {
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of(42L, 43L));
        Tenant t1 = new Tenant();
        t1.setId(42L); t1.setSlug("salon-x"); t1.setName("Salon X");
        Tenant t2 = new Tenant();
        t2.setId(43L); t2.setSlug("salon-y"); t2.setName("Salon Y");
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(t1));
        when(tenantRepository.findById(43L)).thenReturn(Optional.of(t2));

        mvc.perform(MockMvcRequestBuilders.get("/api/me/tenants").with(authentication(authFor(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].slug").value("salon-x"))
                .andExpect(jsonPath("$[1].id").value(43));
    }

    @Test
    void myTenants_returnsEmpty_forClientWithoutAssignments() throws Exception {
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of());

        mvc.perform(MockMvcRequestBuilders.get("/api/me/tenants").with(authentication(authFor(1L))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void switchTenant_returnsNewToken_whenUserHasAssignment() throws Exception {
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of(42L));
        User u = User.builder().id(1L).email("a@a.com").name("A").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(tokenService.generateToken(any(User.class), eq(42L))).thenReturn("new.jwt.token");
        when(userRoleService.resolveRoles(1L, 42L)).thenReturn(Set.of(Role.PRO));

        mvc.perform(MockMvcRequestBuilders.post("/api/me/switch-tenant")
                        .with(authentication(authFor(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\": 42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.jwt.token"))
                .andExpect(jsonPath("$.user.roles[0]").value("PRO"))
                .andExpect(jsonPath("$.user.activeTenantId").value(42));
    }

    @Test
    void switchTenant_acceptsNullTenantId_emitsClientMode() throws Exception {
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of(42L));
        User u = User.builder().id(1L).email("a@a.com").name("A").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(tokenService.generateToken(any(User.class), eq((Long) null))).thenReturn("client.mode.token");
        when(userRoleService.resolveRoles(1L, null)).thenReturn(Set.of());

        mvc.perform(MockMvcRequestBuilders.post("/api/me/switch-tenant")
                        .with(authentication(authFor(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("client.mode.token"))
                .andExpect(jsonPath("$.user.activeTenantId").doesNotExist())
                .andExpect(jsonPath("$.user.roles").isEmpty());
    }

    @Test
    void switchTenant_rejectsWith403_whenUserHasNoAssignment() throws Exception {
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of(42L));

        mvc.perform(MockMvcRequestBuilders.post("/api/me/switch-tenant")
                        .with(authentication(authFor(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\": 999}"))
                .andExpect(status().isForbidden());
    }
}
