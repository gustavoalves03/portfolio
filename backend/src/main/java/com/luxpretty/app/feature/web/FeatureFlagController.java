package com.luxpretty.app.feature.web;

import com.luxpretty.app.feature.app.FeatureFlagService;
import com.luxpretty.app.feature.domain.FeatureKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class FeatureFlagController {

    private final FeatureFlagService service;

    public FeatureFlagController(FeatureFlagService service) {
        this.service = service;
    }

    @GetMapping("/features")
    public Map<FeatureKey, Boolean> myFeatures() {
        return service.snapshot();
    }
}
