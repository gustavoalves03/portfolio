package com.fleurdecoquillage.app.category.web;

import com.fleurdecoquillage.app.category.app.CategoryService;
import com.fleurdecoquillage.app.category.web.dto.CategoryResponse;
import com.fleurdecoquillage.app.category.web.dto.DeleteCategoryResponse;
import com.fleurdecoquillage.app.config.SecurityConfig;
import com.fleurdecoquillage.app.config.CsrfLoggingFilter;
import com.fleurdecoquillage.app.common.error.RestAccessDeniedHandler;
import com.fleurdecoquillage.app.common.error.RestAuthenticationEntryPoint;
import com.fleurdecoquillage.app.auth.TokenService;
import com.fleurdecoquillage.app.users.repo.UserRepository;
import com.fleurdecoquillage.app.auth.CustomOAuth2UserService;
import com.fleurdecoquillage.app.auth.OAuth2AuthenticationSuccessHandler;
import com.fleurdecoquillage.app.auth.OAuth2AuthenticationFailureHandler;
import com.fleurdecoquillage.app.multitenancy.TenantFilter;
import com.fleurdecoquillage.app.tenant.app.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProCategoryController.class)
@Import(SecurityConfig.class)
class ProCategoryControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryService categoryService;

    @MockBean private TokenService tokenService;
    @MockBean private UserRepository userRepository;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private TenantService tenantService;

    // Use @SpyBean for components that must write responses or forward the request chain
    @SpyBean private RestAccessDeniedHandler restAccessDeniedHandler;
    @SpyBean private RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    @SpyBean private TenantFilter tenantFilter;
    @SpyBean private CsrfLoggingFilter csrfLoggingFilter;

    @Test
    @WithMockUser(roles = "PRO")
    void list_returnsCategoriesForPro() throws Exception {
        when(categoryService.listAll()).thenReturn(List.of(
                new CategoryResponse(1L, "Visage", "desc")));
        mvc.perform(get("/api/pro/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Visage"));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void create_returnsCreatedCategory() throws Exception {
        when(categoryService.create(any())).thenReturn(new CategoryResponse(1L, "Visage", null));
        mvc.perform(post("/api/pro/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Visage\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Visage"));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void create_invalidName_returns400() throws Exception {
        mvc.perform(post("/api/pro/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PRO")
    void update_returnsUpdatedCategory() throws Exception {
        when(categoryService.update(eq(1L), any())).thenReturn(new CategoryResponse(1L, "Updated", null));
        mvc.perform(put("/api/pro/categories/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void delete_noCares_returns200() throws Exception {
        when(categoryService.deleteWithReassignment(1L, null))
                .thenReturn(new DeleteCategoryResponse(0));
        mvc.perform(delete("/api/pro/categories/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reassignedCaresCount").value(0));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void delete_withReassignment_returns200() throws Exception {
        when(categoryService.deleteWithReassignment(1L, 2L))
                .thenReturn(new DeleteCategoryResponse(3));
        mvc.perform(delete("/api/pro/categories/1?reassignTo=2").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reassignedCaresCount").value(3));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/pro/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void list_nonProRole_returns403() throws Exception {
        mvc.perform(get("/api/pro/categories"))
                .andExpect(status().isForbidden());
    }
}
