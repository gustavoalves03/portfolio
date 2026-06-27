package com.luxpretty.app.feature.web;

import com.luxpretty.app.feature.app.FeatureFlagService;
import com.luxpretty.app.feature.domain.FeatureKey;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminFeatureFlagControllerTests {

    @Autowired MockMvc mvc;
    @MockBean FeatureFlagService featureFlagService;
    @MockBean TenantRepository tenantRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminOverride_setsFlag() throws Exception {
        Tenant t = new Tenant();
        t.setId(42L);
        t.setSlug("test-salon");
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(t));

        mvc.perform(put("/api/admin/tenants/42/features/SHOP")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}")
                .with(csrf()))
           .andExpect(status().isNoContent());

        verify(featureFlagService).overrideForTenant(FeatureKey.SHOP, true);
    }

    @Test
    @WithMockUser(roles = "PRO")
    void nonAdmin_cannotOverride_returns403() throws Exception {
        mvc.perform(put("/api/admin/tenants/42/features/SHOP")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}")
                .with(csrf()))
           .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminOverride_unknownTenant_returns404() throws Exception {
        when(tenantRepository.findById(999L)).thenReturn(Optional.empty());

        mvc.perform(put("/api/admin/tenants/999/features/SHOP")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}")
                .with(csrf()))
           .andExpect(status().isNotFound());
    }
}
