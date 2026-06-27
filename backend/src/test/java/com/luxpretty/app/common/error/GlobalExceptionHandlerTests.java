package com.luxpretty.app.common.error;

import com.luxpretty.app.feature.app.FeatureDisabledException;
import com.luxpretty.app.feature.domain.FeatureKey;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that GlobalExceptionHandler maps FeatureDisabledException → 403
 * with a structured JSON body {error, featureKey, minimumTier}.
 *
 * Uses @SpringBootTest (full context with H2) to avoid the SecurityConfig
 * dependency chain that @WebMvcTest cannot satisfy without additional mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerTests.GatedTestController.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GlobalExceptionHandlerTests {

    @Autowired
    MockMvc mvc;

    @Test
    void featureDisabled_returns403WithStructuredBody() throws Exception {
        mvc.perform(get("/test/gated"))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.error").value("FEATURE_DISABLED"))
           .andExpect(jsonPath("$.featureKey").value("SHOP"))
           .andExpect(jsonPath("$.minimumTier").value("PREMIUM"));
    }

    @RestController
    @RequestMapping("/test")
    static class GatedTestController {
        @GetMapping("/gated")
        public String trigger() {
            throw new FeatureDisabledException(FeatureKey.SHOP, SubscriptionTier.PREMIUM);
        }
    }
}
