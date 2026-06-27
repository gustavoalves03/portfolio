package com.luxpretty.app.feature.app;

import com.luxpretty.app.feature.domain.FeatureFlag;
import com.luxpretty.app.feature.domain.FeatureFlagSource;
import com.luxpretty.app.feature.domain.FeatureKey;
import com.luxpretty.app.feature.repo.FeatureFlagRepository;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTests {

    @Mock FeatureFlagRepository repo;
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-25T10:00:00Z"), ZoneId.of("UTC"));
    private FeatureFlagService service;

    @BeforeEach
    void setUp() {
        service = new FeatureFlagService(repo, clock);
    }

    @Test
    void isEnabled_returnsFalseWhenNoRow() {
        when(repo.findByKey(FeatureKey.SHOP)).thenReturn(Optional.empty());
        assertFalse(service.isEnabled(FeatureKey.SHOP));
    }

    @Test
    void isEnabled_returnsTrueWhenAdminOverrideEnabled() {
        FeatureFlag f = flag(FeatureKey.SHOP, true, FeatureFlagSource.ADMIN_OVERRIDE);
        when(repo.findByKey(FeatureKey.SHOP)).thenReturn(Optional.of(f));
        assertTrue(service.isEnabled(FeatureKey.SHOP));
    }

    @Test
    void applyTierDefaults_preservesAdminOverrides() {
        FeatureFlag shopOverride = flag(FeatureKey.SHOP, true, FeatureFlagSource.ADMIN_OVERRIDE);
        when(repo.findAll()).thenReturn(List.of(shopOverride));
        service.applyTierDefaults(SubscriptionTier.GESTION);
        ArgumentCaptor<List<FeatureFlag>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(captor.capture());
        // SHOP not in GESTION defaults, but ADMIN_OVERRIDE row must NOT appear in the saveAll
        boolean shopTouched = captor.getValue().stream().anyMatch(f -> f.getKey() == FeatureKey.SHOP);
        assertFalse(shopTouched, "ADMIN_OVERRIDE row must not be rewritten");
    }

    @Test
    void applyTierDefaults_seedsAllKeysForPremium() {
        when(repo.findAll()).thenReturn(List.of());
        service.applyTierDefaults(SubscriptionTier.PREMIUM);
        ArgumentCaptor<List<FeatureFlag>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(captor.capture());
        Map<FeatureKey, Boolean> saved = captor.getValue().stream()
            .collect(Collectors.toMap(FeatureFlag::getKey, FeatureFlag::isEnabled));
        assertEquals(FeatureKey.values().length, saved.size());
        for (FeatureKey k : FeatureKey.values()) {
            assertEquals(Boolean.TRUE, saved.get(k), "PREMIUM should enable " + k);
        }
    }

    @Test
    void overrideForTenant_marksAsAdminOverride() {
        FeatureFlag existing = flag(FeatureKey.SHOP, false, FeatureFlagSource.TIER_DEFAULT);
        when(repo.findByKey(FeatureKey.SHOP)).thenReturn(Optional.of(existing));
        service.overrideForTenant(FeatureKey.SHOP, true);
        ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
        verify(repo).save(captor.capture());
        assertTrue(captor.getValue().isEnabled());
        assertEquals(FeatureFlagSource.ADMIN_OVERRIDE, captor.getValue().getSource());
    }

    @Test
    void snapshot_returnsAllKeysWithFalseFallback() {
        when(repo.findAll()).thenReturn(List.of(
            flag(FeatureKey.BOOKING, true, FeatureFlagSource.TIER_DEFAULT)));
        Map<FeatureKey, Boolean> snap = service.snapshot();
        assertEquals(FeatureKey.values().length, snap.size());
        assertTrue(snap.get(FeatureKey.BOOKING));
        assertFalse(snap.get(FeatureKey.SHOP));
    }

    private FeatureFlag flag(FeatureKey k, boolean enabled, FeatureFlagSource s) {
        FeatureFlag f = new FeatureFlag();
        f.setKey(k); f.setEnabled(enabled); f.setSource(s); f.setUpdatedAt(Instant.now());
        return f;
    }
}
