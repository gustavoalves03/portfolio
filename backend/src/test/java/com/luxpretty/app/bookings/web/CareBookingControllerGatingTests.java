package com.luxpretty.app.bookings.web;

import com.luxpretty.app.bookings.app.CareBookingService;
import com.luxpretty.app.feature.app.FeatureFlagService;
import com.luxpretty.app.feature.domain.FeatureKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T12 — verifies that @RequiresFeature(BOOKING) on CareBookingController
 * causes the FeatureGateAspect to return 403 when the feature is disabled,
 * and passes through to the service when enabled.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CareBookingControllerGatingTests {

    @Autowired MockMvc mvc;

    @MockBean FeatureFlagService flags;
    @MockBean CareBookingService careBookingService;

    @Test
    @WithMockUser(roles = "PRO")
    void disabledFeature_returns403() throws Exception {
        when(flags.isEnabled(eq(FeatureKey.BOOKING))).thenReturn(false);

        mvc.perform(get("/api/bookings"))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.error").value("FEATURE_DISABLED"))
           .andExpect(jsonPath("$.featureKey").value("BOOKING"));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void enabledFeature_passesThrough() throws Exception {
        when(flags.isEnabled(any(FeatureKey.class))).thenReturn(true);
        when(careBookingService.list(any())).thenReturn(Page.empty());

        mvc.perform(get("/api/bookings"))
           .andExpect(status().isOk());
    }
}
