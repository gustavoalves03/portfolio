package com.luxpretty.app.feature.web;

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

import java.util.EnumMap;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeatureFlagControllerTests {

    @Autowired MockMvc mvc;
    @MockBean FeatureFlagService service;

    @Test
    @WithMockUser
    void meFeatures_returnsSnapshot() throws Exception {
        Map<FeatureKey, Boolean> snap = new EnumMap<>(FeatureKey.class);
        for (FeatureKey k : FeatureKey.values()) snap.put(k, k == FeatureKey.BOOKING);
        when(service.snapshot()).thenReturn(snap);

        mvc.perform(get("/api/me/features"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.BOOKING").value(true))
           .andExpect(jsonPath("$.SHOP").value(false));
    }

    @Test
    void meFeatures_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/me/features"))
           .andExpect(status().isUnauthorized());
    }
}
