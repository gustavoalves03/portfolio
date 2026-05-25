package com.luxpretty.app.feature.app;

import com.luxpretty.app.feature.domain.FeatureKey;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class FeatureGateAspectTests {

    @MockBean
    FeatureFlagService flags;

    @Autowired
    GatedBean bean;

    @Test
    void disabledFeature_throwsFeatureDisabledException() {
        when(flags.isEnabled(FeatureKey.SHOP)).thenReturn(false);
        FeatureDisabledException ex = assertThrows(FeatureDisabledException.class, bean::shopAction);
        assertEquals(FeatureKey.SHOP, ex.featureKey);
        assertEquals(SubscriptionTier.PREMIUM, ex.minimumTier);
    }

    @Test
    void enabledFeature_passesThrough() {
        when(flags.isEnabled(FeatureKey.SHOP)).thenReturn(true);
        assertEquals("ok", bean.shopAction());
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        GatedBean gatedBean() {
            return new GatedBean();
        }
    }

    static class GatedBean {
        @RequiresFeature(FeatureKey.SHOP)
        public String shopAction() {
            return "ok";
        }
    }
}
