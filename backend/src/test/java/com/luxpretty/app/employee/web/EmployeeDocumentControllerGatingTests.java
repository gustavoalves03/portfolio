package com.luxpretty.app.employee.web;

import com.luxpretty.app.employee.app.EmployeeDocumentService;
import com.luxpretty.app.feature.app.FeatureFlagService;
import com.luxpretty.app.feature.domain.FeatureKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T12 — verifies that @RequiresFeature(CLIENT_FILES) on EmployeeDocumentController
 * causes the FeatureGateAspect to return 403 when the feature is disabled,
 * and passes through to the service when enabled.
 *
 * NOTE: CLIENT_FILES is the closest current catalog key for employee documents.
 * See TODO comment in EmployeeDocumentController for full rationale.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeDocumentControllerGatingTests {

    @Autowired MockMvc mvc;

    @MockBean FeatureFlagService flags;
    @MockBean EmployeeDocumentService employeeDocumentService;

    @Test
    @WithMockUser(roles = "PRO")
    void disabledFeature_returns403() throws Exception {
        when(flags.isEnabled(eq(FeatureKey.CLIENT_FILES))).thenReturn(false);

        mvc.perform(get("/api/pro/employees/1/documents"))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.error").value("FEATURE_DISABLED"))
           .andExpect(jsonPath("$.featureKey").value("CLIENT_FILES"));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void enabledFeature_passesThrough() throws Exception {
        when(flags.isEnabled(any(FeatureKey.class))).thenReturn(true);
        when(employeeDocumentService.listByEmployee(1L)).thenReturn(List.of());

        mvc.perform(get("/api/pro/employees/1/documents"))
           .andExpect(status().isOk());
    }
}
