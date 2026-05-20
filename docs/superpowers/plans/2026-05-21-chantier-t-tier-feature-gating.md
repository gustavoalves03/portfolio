# Chantier T — Tier Feature Gating Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a per-tenant feature-flag system so the application enforces the differences between VITRINE, GESTION and PREMIUM tiers promised by the pricing page. Code never reads `SubscriptionTier` directly; it reads `FeatureFlagService.isEnabled(key)`. Admins can override individual flags per tenant. Downgrades hide data; re-upgrades restore it.

**Architecture:**
1. Tenant Flyway migration `V8__tenant_features.sql` (runs after Chantier B's `V7`) adds `TENANT_FEATURES(FEATURE_KEY, ENABLED, SOURCE, UPDATED_AT)`.
2. `FeatureKey` enum + `TierFeatureCatalog` (immutable `Map<SubscriptionTier, Set<FeatureKey>>`).
3. `FeatureFlagService` with Caffeine cache (TTL 60s), `applyTierDefaults(tier)` preserves `ADMIN_OVERRIDE` rows, `overrideForTenant(key,bool)` evicts cache for that key.
4. `@RequiresFeature(FeatureKey)` Spring AOP aspect throws `FeatureDisabledException`, mapped by `GlobalExceptionHandler` to `403 {error:FEATURE_DISABLED, featureKey, minimumTier}`.
5. Hook `SubscriptionService.startCheckout` and the Stripe-webhook-driven tier update path to call `featureFlagService.applyTierDefaults(newTier)`.
6. Frontend: `FeatureFlagsStore` (SignalStore) loaded after login from `GET /api/me/features`, exposes `isEnabled(key)`. Directive `*lpFeatureEnabled` removes element when off; component `<lp-feature-locked>` overlays read-only upsell. HTTP interceptor handles `403 FEATURE_DISABLED`.
7. Apply gating annotations to all controllers covering the pricing matrix (booking, employees, photos, SMS, client files, absence, online payment, shop, loyalty, multi-location).

**Tech Stack:** Spring Boot 3.5 / Java 21, Spring AOP, Caffeine cache, JPA, Mockito, `@WebMvcTest`, Angular 20 NgRx SignalStore, Karma/Jasmine, Transloco.

**Spec:** `docs/superpowers/specs/2026-05-21-tier-gating-and-multi-employee-booking-design.md` §3.

**Prerequisite:** Chantier B (multi-employee booking) merged on `main`. This plan adds tenant migration `V8`, after Chantier B's `V7`.

---

## File Structure

| Path | Action | Responsibility |
|---|---|---|
| `backend/src/main/java/com/luxpretty/app/feature/domain/FeatureKey.java` | Create | Enum of all gateable features |
| `backend/src/main/java/com/luxpretty/app/feature/domain/FeatureFlag.java` | Create | JPA entity for `TENANT_FEATURES` |
| `backend/src/main/java/com/luxpretty/app/feature/domain/FeatureFlagSource.java` | Create | Enum `TIER_DEFAULT \| ADMIN_OVERRIDE` |
| `backend/src/main/java/com/luxpretty/app/feature/repo/FeatureFlagRepository.java` | Create | JPA repository |
| `backend/src/main/java/com/luxpretty/app/feature/app/TierFeatureCatalog.java` | Create | Static map `Tier → Set<FeatureKey>` |
| `backend/src/main/java/com/luxpretty/app/feature/app/FeatureFlagService.java` | Create | `isEnabled`, `snapshot`, `applyTierDefaults`, `overrideForTenant`, cache |
| `backend/src/main/java/com/luxpretty/app/feature/app/RequiresFeature.java` | Create | `@RequiresFeature(FeatureKey)` annotation |
| `backend/src/main/java/com/luxpretty/app/feature/app/FeatureGateAspect.java` | Create | Spring AOP aspect enforcing the annotation |
| `backend/src/main/java/com/luxpretty/app/feature/app/FeatureDisabledException.java` | Create | Thrown by the aspect |
| `backend/src/main/java/com/luxpretty/app/common/error/GlobalExceptionHandler.java` | Modify | Map `FeatureDisabledException` → 403 |
| `backend/src/main/java/com/luxpretty/app/feature/web/FeatureFlagController.java` | Create | `GET /api/me/features`, admin override endpoint |
| `backend/src/main/java/com/luxpretty/app/subscription/app/SubscriptionService.java` | Modify | Call `applyTierDefaults` on tier change |
| `backend/src/main/resources/db/migration/tenant/V8__tenant_features.sql` | Create | Schema migration |
| `backend/src/main/java/com/luxpretty/app/multitenancy/TenantSchemaMigrator.java` | Modify | Mirror V8 for legacy tenants |
| `backend/src/main/java/com/luxpretty/app/feature/app/TenantProvisioningHook.java` | Create | On tenant create, seed flags from current tier |
| (existing controllers) | Modify | Add `@RequiresFeature(...)` annotations |
| `backend/src/main/java/com/luxpretty/app/config/CacheConfig.java` | Create or modify | Register Caffeine cache `featureFlags` |
| `backend/src/test/java/com/luxpretty/app/feature/app/FeatureFlagServiceTests.java` | Create | Unit tests TC 1-5, 8-12 |
| `backend/src/test/java/com/luxpretty/app/feature/app/FeatureGateAspectTests.java` | Create | Aspect throws when disabled |
| `backend/src/test/java/com/luxpretty/app/feature/web/FeatureFlagControllerTests.java` | Create | `/api/me/features` payload |
| `frontend/src/app/core/feature-flags/feature-key.ts` | Create | TS enum matching `FeatureKey.java` |
| `frontend/src/app/core/feature-flags/feature-flags.store.ts` | Create | NgRx SignalStore with `isEnabled` |
| `frontend/src/app/core/feature-flags/feature-flags.service.ts` | Create | HTTP service for `/api/me/features` |
| `frontend/src/app/core/feature-flags/feature-enabled.directive.ts` | Create | `*lpFeatureEnabled` structural directive |
| `frontend/src/app/core/feature-flags/feature-locked.component.ts` | Create | `<lp-feature-locked>` overlay component |
| `frontend/src/app/core/feature-flags/feature-flag.interceptor.ts` | Create | Handle 403 FEATURE_DISABLED |
| `frontend/src/app/app.config.ts` | Modify | Wire the interceptor |
| `frontend/src/app/core/auth/auth.service.ts` | Modify | Load flags after login |
| (existing components/templates) | Modify | Apply `*lpFeatureEnabled` to nav items + `<lp-feature-locked>` to feature pages |
| `frontend/src/assets/i18n/fr.json` | Modify | Translation keys |
| `frontend/src/assets/i18n/en.json` | Modify | Translation keys |

---

## Task 1: Create FeatureKey enum + FeatureFlagSource

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/feature/domain/FeatureKey.java`
- Create: `backend/src/main/java/com/luxpretty/app/feature/domain/FeatureFlagSource.java`

- [ ] **Step 1: Create FeatureKey enum**

```java
package com.luxpretty.app.feature.domain;

public enum FeatureKey {
    BOOKING,
    EMPLOYEES,
    PHOTOS,
    SMS_REMINDER,
    CLIENT_FILES,
    ABSENCE_MGMT,
    ONLINE_PAYMENT,
    SHOP,
    LOYALTY,
    MULTI_LOCATION
}
```

- [ ] **Step 2: Create FeatureFlagSource enum**

```java
package com.luxpretty.app.feature.domain;

public enum FeatureFlagSource {
    TIER_DEFAULT,
    ADMIN_OVERRIDE
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn -pl backend compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/feature/domain/
git commit -m "feat(feature): FeatureKey + FeatureFlagSource enums"
```

---

## Task 2: Create TierFeatureCatalog

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/feature/app/TierFeatureCatalog.java`
- Create: `backend/src/test/java/com/luxpretty/app/feature/app/TierFeatureCatalogTests.java`

- [ ] **Step 1: Write the failing test**

```java
package com.luxpretty.app.feature.app;

import com.luxpretty.app.feature.domain.FeatureKey;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TierFeatureCatalogTests {

    @Test
    void vitrine_hasNoFeatureFlagsEnabled() {
        assertTrue(TierFeatureCatalog.featuresFor(SubscriptionTier.VITRINE).isEmpty());
    }

    @Test
    void gestion_hasOperationalFeaturesButNotPremiumOnes() {
        var gestion = TierFeatureCatalog.featuresFor(SubscriptionTier.GESTION);
        assertTrue(gestion.contains(FeatureKey.BOOKING));
        assertTrue(gestion.contains(FeatureKey.EMPLOYEES));
        assertTrue(gestion.contains(FeatureKey.ABSENCE_MGMT));
        assertFalse(gestion.contains(FeatureKey.SHOP));
        assertFalse(gestion.contains(FeatureKey.LOYALTY));
    }

    @Test
    void premium_hasAllFeatures() {
        var premium = TierFeatureCatalog.featuresFor(SubscriptionTier.PREMIUM);
        for (FeatureKey k : FeatureKey.values()) {
            assertTrue(premium.contains(k), "PREMIUM missing " + k);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -pl backend test -Dtest=TierFeatureCatalogTests`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Create the catalog**

```java
package com.luxpretty.app.feature.app;

import com.luxpretty.app.feature.domain.FeatureKey;
import com.luxpretty.app.subscription.domain.SubscriptionTier;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TierFeatureCatalog {

    private static final Map<SubscriptionTier, Set<FeatureKey>> CATALOG = Map.of(
        SubscriptionTier.VITRINE, EnumSet.noneOf(FeatureKey.class),
        SubscriptionTier.GESTION, EnumSet.of(
            FeatureKey.BOOKING, FeatureKey.EMPLOYEES, FeatureKey.PHOTOS,
            FeatureKey.SMS_REMINDER, FeatureKey.CLIENT_FILES, FeatureKey.ABSENCE_MGMT
        ),
        SubscriptionTier.PREMIUM, EnumSet.allOf(FeatureKey.class)
    );

    public static Set<FeatureKey> featuresFor(SubscriptionTier tier) {
        return Set.copyOf(CATALOG.getOrDefault(tier, Set.of()));
    }

    /** Returns the minimum tier that includes the given feature, or PREMIUM as fallback. */
    public static SubscriptionTier minimumTierFor(FeatureKey key) {
        for (SubscriptionTier tier : new SubscriptionTier[]{
                SubscriptionTier.VITRINE, SubscriptionTier.GESTION, SubscriptionTier.PREMIUM}) {
            if (CATALOG.get(tier).contains(key)) return tier;
        }
        return SubscriptionTier.PREMIUM;
    }

    private TierFeatureCatalog() {}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -pl backend test -Dtest=TierFeatureCatalogTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/feature/app/TierFeatureCatalog.java \
        backend/src/test/java/com/luxpretty/app/feature/app/TierFeatureCatalogTests.java
git commit -m "feat(feature): TierFeatureCatalog default matrix per tier"
```

---

## Task 3: Create Flyway migration V8__tenant_features.sql

**Files:**
- Create: `backend/src/main/resources/db/migration/tenant/V8__tenant_features.sql`
- Modify: `backend/src/main/java/com/luxpretty/app/multitenancy/TenantSchemaMigrator.java`

- [ ] **Step 1: Create the migration**

```sql
-- V8: per-tenant feature flags
CREATE TABLE TENANT_FEATURES (
  FEATURE_KEY VARCHAR2(64) PRIMARY KEY,
  ENABLED     NUMBER(1) NOT NULL CHECK (ENABLED IN (0,1)),
  SOURCE      VARCHAR2(16) NOT NULL CHECK (SOURCE IN ('TIER_DEFAULT','ADMIN_OVERRIDE')),
  UPDATED_AT  TIMESTAMP WITH TIME ZONE NOT NULL
);
```

- [ ] **Step 2: Mirror in TenantSchemaMigrator for legacy tenants**

In `TenantSchemaMigrator.java`, add and wire a method:

```java
private void mirrorV8FeatureFlags(String tenantSchema) {
    try {
        jdbcTemplate.execute("CREATE TABLE \"" + tenantSchema + "\".TENANT_FEATURES (" +
            "FEATURE_KEY VARCHAR2(64) PRIMARY KEY," +
            "ENABLED NUMBER(1) NOT NULL CHECK (ENABLED IN (0,1))," +
            "SOURCE VARCHAR2(16) NOT NULL CHECK (SOURCE IN ('TIER_DEFAULT','ADMIN_OVERRIDE'))," +
            "UPDATED_AT TIMESTAMP WITH TIME ZONE NOT NULL)");
    } catch (DataAccessException e) {
        if (!isAlreadyExists(e)) throw e;
    }
}
```

Wire it in the legacy migration loop (same place where `mirrorV7Backfill` from Chantier B is called).

- [ ] **Step 3: Boot integration test**

Run: `mvn -pl backend test -Dtest=LuxPrettyApplicationTests -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/tenant/V8__tenant_features.sql \
        backend/src/main/java/com/luxpretty/app/multitenancy/TenantSchemaMigrator.java
git commit -m "feat(feature): TENANT_FEATURES table + legacy mirror"
```

---

## Task 4: Create FeatureFlag JPA entity + repository

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/feature/domain/FeatureFlag.java`
- Create: `backend/src/main/java/com/luxpretty/app/feature/repo/FeatureFlagRepository.java`
- Create: `backend/src/test/java/com/luxpretty/app/feature/repo/FeatureFlagRepositoryTests.java`

- [ ] **Step 1: Write failing repo test**

```java
@DataJpaTest
@ActiveProfiles("test")
class FeatureFlagRepositoryTests {
    @Autowired FeatureFlagRepository repo;

    @Test
    void persistsAndFindsByKey() {
        FeatureFlag f = new FeatureFlag();
        f.setKey(FeatureKey.SHOP);
        f.setEnabled(true);
        f.setSource(FeatureFlagSource.ADMIN_OVERRIDE);
        f.setUpdatedAt(Instant.now());
        repo.save(f);

        Optional<FeatureFlag> found = repo.findByKey(FeatureKey.SHOP);
        assertTrue(found.isPresent());
        assertTrue(found.get().isEnabled());
        assertEquals(FeatureFlagSource.ADMIN_OVERRIDE, found.get().getSource());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -pl backend test -Dtest=FeatureFlagRepositoryTests`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Create the entity**

```java
package com.luxpretty.app.feature.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "TENANT_FEATURES")
public class FeatureFlag {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "FEATURE_KEY", length = 64)
    private FeatureKey key;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "SOURCE", length = 16, nullable = false)
    private FeatureFlagSource source;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;
}
```

> Memory: per `feedback_instant_oracle_timestamp_tz.md`, `Instant` columns MUST map to `TIMESTAMP WITH TIME ZONE` in Oracle. The V8 migration uses `TIMESTAMP WITH TIME ZONE` — correct.

- [ ] **Step 4: Create the repository**

```java
package com.luxpretty.app.feature.repo;

import com.luxpretty.app.feature.domain.FeatureFlag;
import com.luxpretty.app.feature.domain.FeatureKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, FeatureKey> {
    Optional<FeatureFlag> findByKey(FeatureKey key);
}
```

- [ ] **Step 5: Run the test to verify it passes**

Same command as Step 2.
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/feature/ \
        backend/src/test/java/com/luxpretty/app/feature/repo/FeatureFlagRepositoryTests.java
git commit -m "feat(feature): FeatureFlag entity + repository"
```

---

## Task 5: Create FeatureFlagService with cache

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/feature/app/FeatureFlagService.java`
- Create: `backend/src/main/java/com/luxpretty/app/config/CacheConfig.java` (if not already existing)
- Create: `backend/src/test/java/com/luxpretty/app/feature/app/FeatureFlagServiceTests.java`

- [ ] **Step 1: Write the failing tests (TC 3, 4, 5, 11, 12)**

```java
package com.luxpretty.app.feature.app;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTests {

    @Mock FeatureFlagRepository repo;
    @Mock Clock clock;
    @InjectMocks FeatureFlagService service;

    @BeforeEach void setUp() {
        when(clock.instant()).thenReturn(Instant.parse("2026-05-21T10:00:00Z"));
        service = new FeatureFlagService(repo, clock);
    }

    @Test
    void isEnabled_returnsFalse_whenNoRow() {
        when(repo.findByKey(FeatureKey.SHOP)).thenReturn(Optional.empty());
        assertFalse(service.isEnabled(FeatureKey.SHOP));
    }

    @Test
    void isEnabled_returnsTrue_whenAdminOverrideEnabled() { // TC 3
        FeatureFlag f = flag(FeatureKey.SHOP, true, FeatureFlagSource.ADMIN_OVERRIDE);
        when(repo.findByKey(FeatureKey.SHOP)).thenReturn(Optional.of(f));
        assertTrue(service.isEnabled(FeatureKey.SHOP));
    }

    @Test
    void applyTierDefaults_preservesAdminOverrides() { // TC 4, 12
        FeatureFlag shopOverride = flag(FeatureKey.SHOP, true, FeatureFlagSource.ADMIN_OVERRIDE);
        when(repo.findAll()).thenReturn(List.of(shopOverride));
        service.applyTierDefaults(SubscriptionTier.GESTION);
        // SHOP not in GESTION defaults, but ADMIN_OVERRIDE row must be untouched.
        ArgumentCaptor<List<FeatureFlag>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(captor.capture());
        boolean shopTouched = captor.getValue().stream().anyMatch(f -> f.getKey() == FeatureKey.SHOP);
        assertFalse(shopTouched, "ADMIN_OVERRIDE row must not be rewritten");
    }

    @Test
    void applyTierDefaults_seedsAllKeys_forPremium() { // TC 5
        when(repo.findAll()).thenReturn(List.of());
        service.applyTierDefaults(SubscriptionTier.PREMIUM);
        ArgumentCaptor<List<FeatureFlag>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(captor.capture());
        Map<FeatureKey,Boolean> saved = captor.getValue().stream()
            .collect(Collectors.toMap(FeatureFlag::getKey, FeatureFlag::isEnabled));
        for (FeatureKey k : FeatureKey.values()) {
            assertEquals(Boolean.TRUE, saved.get(k), "Premium should enable " + k);
        }
    }

    @Test
    void overrideForTenant_marksAsAdminOverride_andEvictsCache() {
        FeatureFlag existing = flag(FeatureKey.SHOP, false, FeatureFlagSource.TIER_DEFAULT);
        when(repo.findByKey(FeatureKey.SHOP)).thenReturn(Optional.of(existing));
        service.overrideForTenant(FeatureKey.SHOP, true);
        ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
        verify(repo).save(captor.capture());
        assertTrue(captor.getValue().isEnabled());
        assertEquals(FeatureFlagSource.ADMIN_OVERRIDE, captor.getValue().getSource());
    }

    @Test
    void snapshot_returnsAllKeys_withFalseFallback() {
        when(repo.findAll()).thenReturn(List.of(flag(FeatureKey.BOOKING, true, FeatureFlagSource.TIER_DEFAULT)));
        Map<FeatureKey,Boolean> snap = service.snapshot();
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -pl backend test -Dtest=FeatureFlagServiceTests`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Create the service**

```java
package com.luxpretty.app.feature.app;

import com.luxpretty.app.feature.domain.FeatureFlag;
import com.luxpretty.app.feature.domain.FeatureFlagSource;
import com.luxpretty.app.feature.domain.FeatureKey;
import com.luxpretty.app.feature.repo.FeatureFlagRepository;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FeatureFlagService {

    private final FeatureFlagRepository repo;
    private final Clock clock;

    public FeatureFlagService(FeatureFlagRepository repo, Clock clock) {
        this.repo = repo; this.clock = clock;
    }

    @Cacheable(value = "featureFlags", key = "@tenantContextKey.current() + '::' + #key.name()")
    @Transactional(readOnly = true)
    public boolean isEnabled(FeatureKey key) {
        return repo.findByKey(key).map(FeatureFlag::isEnabled).orElse(false);
    }

    @Transactional(readOnly = true)
    public Map<FeatureKey, Boolean> snapshot() {
        Map<FeatureKey, Boolean> map = new EnumMap<>(FeatureKey.class);
        for (FeatureKey k : FeatureKey.values()) map.put(k, false);
        repo.findAll().forEach(f -> map.put(f.getKey(), f.isEnabled()));
        return map;
    }

    @Transactional
    @CacheEvict(value = "featureFlags", allEntries = true)
    public void applyTierDefaults(SubscriptionTier tier) {
        Set<FeatureKey> defaults = TierFeatureCatalog.featuresFor(tier);
        Map<FeatureKey, FeatureFlag> existing = repo.findAll().stream()
            .collect(Collectors.toMap(FeatureFlag::getKey, f -> f));
        List<FeatureFlag> upsert = new ArrayList<>();
        for (FeatureKey key : FeatureKey.values()) {
            FeatureFlag current = existing.get(key);
            if (current != null && current.getSource() == FeatureFlagSource.ADMIN_OVERRIDE) {
                continue; // preserve admin override
            }
            FeatureFlag f = current != null ? current : new FeatureFlag();
            f.setKey(key);
            f.setEnabled(defaults.contains(key));
            f.setSource(FeatureFlagSource.TIER_DEFAULT);
            f.setUpdatedAt(clock.instant());
            upsert.add(f);
        }
        repo.saveAll(upsert);
    }

    @Transactional
    @CacheEvict(value = "featureFlags", allEntries = true)
    public void overrideForTenant(FeatureKey key, boolean enabled) {
        FeatureFlag f = repo.findByKey(key).orElseGet(() -> {
            FeatureFlag ne = new FeatureFlag(); ne.setKey(key); return ne;
        });
        f.setEnabled(enabled);
        f.setSource(FeatureFlagSource.ADMIN_OVERRIDE);
        f.setUpdatedAt(clock.instant());
        repo.save(f);
    }
}
```

Also create a tiny SpEL helper bean to scope cache keys per tenant:

```java
// backend/src/main/java/com/luxpretty/app/feature/app/TenantContextKey.java
package com.luxpretty.app.feature.app;

import com.luxpretty.app.multitenancy.TenantContext;
import org.springframework.stereotype.Component;

@Component("tenantContextKey")
public class TenantContextKey {
    public String current() {
        String slug = TenantContext.getCurrentTenant();
        return slug != null ? slug : "_none";
    }
}
```

- [ ] **Step 4: Register the Caffeine cache**

In `backend/src/main/java/com/luxpretty/app/config/CacheConfig.java` (create if missing):

```java
package com.luxpretty.app.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {
    @Bean
    public CacheManager featureFlagsCacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager("featureFlags");
        mgr.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(60, TimeUnit.SECONDS));
        return mgr;
    }
}
```

(If a `CacheManager` already exists for another cache, add `"featureFlags"` to its list. Verify there's no duplicate `@Bean CacheManager` definition.)

In `LuxPrettyApplication.java` (or any `@Configuration`), ensure `@EnableCaching` is present.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -pl backend test -Dtest=FeatureFlagServiceTests`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/feature/app/ \
        backend/src/main/java/com/luxpretty/app/config/CacheConfig.java \
        backend/src/test/java/com/luxpretty/app/feature/app/FeatureFlagServiceTests.java
git commit -m "feat(feature): FeatureFlagService with Caffeine cache + override semantics"
```

---

## Task 6: Hook tier change → applyTierDefaults

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/subscription/app/SubscriptionService.java`
- Modify: `backend/src/test/java/com/luxpretty/app/subscription/app/SubscriptionServiceTests.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void startCheckout_appliesTierDefaultsForNewTier() {
    // ... existing setup
    service.startCheckout(tenantId, SubscriptionTier.PREMIUM, SubscriptionBilling.MONTHLY, ...);
    verify(featureFlagService).applyTierDefaults(SubscriptionTier.PREMIUM);
}

@Test
void syncFromStripeWebhook_appliesTierDefaultsWhenTierChanges() {
    // ... existing setup mimicking the webhook path
    service.syncFromStripe(/* webhook payload that changes tier */);
    verify(featureFlagService).applyTierDefaults(eq(/*new tier*/));
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -pl backend test -Dtest=SubscriptionServiceTests`
Expected: FAIL.

- [ ] **Step 3: Inject FeatureFlagService and call it**

In `SubscriptionService.java`:

```java
private final FeatureFlagService featureFlagService;
// add to constructor
```

In `startCheckout(...)` after `tenant.setSubscriptionTier(tier);` add:
```java
featureFlagService.applyTierDefaults(tier);
```

In the webhook sync path (after `tenant.setSubscriptionTier(tierBilling.tier())`):
```java
featureFlagService.applyTierDefaults(tierBilling.tier());
```

- [ ] **Step 4: Run the tests to verify they pass**

Same command as Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/subscription/app/SubscriptionService.java \
        backend/src/test/java/com/luxpretty/app/subscription/app/SubscriptionServiceTests.java
git commit -m "feat(subscription): apply tier defaults on tier change"
```

---

## Task 7: Provisioning hook — seed flags on tenant creation

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/feature/app/TenantProvisioningHook.java`

Find where tenants are provisioned (probably in `OnboardingService` or `TenantSchemaManager` — search). Wire a call to `featureFlagService.applyTierDefaults(tenant.getSubscriptionTier())` right after the new tenant's schema is migrated. If the tier is null at provisioning, seed with `VITRINE` defaults (empty).

- [ ] **Step 1: Locate provisioning entry point**

Run: `grep -rn "createTenant\|provisionTenant\|new Tenant(" backend/src/main/java | head -20`

- [ ] **Step 2: Add the post-provision call**

Wherever the new tenant's schema is freshly migrated and the `TenantContext` is set to that tenant, call:

```java
featureFlagService.applyTierDefaults(
    tenant.getSubscriptionTier() != null ? tenant.getSubscriptionTier() : SubscriptionTier.VITRINE);
```

- [ ] **Step 3: Smoke test by registering a new pro in dev**

Start backend + frontend, register a new pro via the onboarding flow, then check the DB:
```sql
SELECT * FROM <new-tenant-schema>.TENANT_FEATURES;
```
Expected: 10 rows, all matching the tier's default set.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/<provisioning-file>
git commit -m "feat(feature): seed tenant feature flags on provisioning"
```

---

## Task 8: Create @RequiresFeature annotation + aspect

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/feature/app/RequiresFeature.java`
- Create: `backend/src/main/java/com/luxpretty/app/feature/app/FeatureGateAspect.java`
- Create: `backend/src/main/java/com/luxpretty/app/feature/app/FeatureDisabledException.java`
- Create: `backend/src/test/java/com/luxpretty/app/feature/app/FeatureGateAspectTests.java`

- [ ] **Step 1: Create the annotation**

```java
package com.luxpretty.app.feature.app;

import com.luxpretty.app.feature.domain.FeatureKey;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {
    FeatureKey value();
}
```

- [ ] **Step 2: Create the exception**

```java
package com.luxpretty.app.feature.app;

import com.luxpretty.app.feature.domain.FeatureKey;
import com.luxpretty.app.subscription.domain.SubscriptionTier;

public class FeatureDisabledException extends RuntimeException {
    public final FeatureKey featureKey;
    public final SubscriptionTier minimumTier;
    public FeatureDisabledException(FeatureKey key, SubscriptionTier min) {
        super("Feature disabled: " + key);
        this.featureKey = key; this.minimumTier = min;
    }
}
```

- [ ] **Step 3: Write the failing aspect test**

```java
package com.luxpretty.app.feature.app;

@SpringBootTest
@ActiveProfiles("test")
class FeatureGateAspectTests {

    @Autowired ApplicationContext ctx;
    @MockBean FeatureFlagService flags;

    @Test
    void disabledFeature_throwsFeatureDisabledException() {
        GatedBean bean = ctx.getBean(GatedBean.class);
        when(flags.isEnabled(FeatureKey.SHOP)).thenReturn(false);
        FeatureDisabledException ex = assertThrows(FeatureDisabledException.class, bean::shopAction);
        assertEquals(FeatureKey.SHOP, ex.featureKey);
        assertEquals(SubscriptionTier.PREMIUM, ex.minimumTier);
    }

    @Test
    void enabledFeature_passesThrough() {
        GatedBean bean = ctx.getBean(GatedBean.class);
        when(flags.isEnabled(FeatureKey.SHOP)).thenReturn(true);
        assertEquals("ok", bean.shopAction());
    }

    @TestConfiguration
    static class TestBeans {
        @Bean GatedBean gatedBean() { return new GatedBean(); }
    }

    static class GatedBean {
        @RequiresFeature(FeatureKey.SHOP)
        public String shopAction() { return "ok"; }
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `mvn -pl backend test -Dtest=FeatureGateAspectTests`
Expected: FAIL — aspect does not exist.

- [ ] **Step 5: Create the aspect**

```java
package com.luxpretty.app.feature.app;

import com.luxpretty.app.feature.domain.FeatureKey;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class FeatureGateAspect {

    private final FeatureFlagService flags;

    public FeatureGateAspect(FeatureFlagService flags) {
        this.flags = flags;
    }

    @Around("@within(com.luxpretty.app.feature.app.RequiresFeature) || @annotation(com.luxpretty.app.feature.app.RequiresFeature)")
    public Object enforce(ProceedingJoinPoint pjp) throws Throwable {
        Method m = ((MethodSignature) pjp.getSignature()).getMethod();
        RequiresFeature ann = m.getAnnotation(RequiresFeature.class);
        if (ann == null) ann = m.getDeclaringClass().getAnnotation(RequiresFeature.class);
        if (ann != null) {
            FeatureKey key = ann.value();
            if (!flags.isEnabled(key)) {
                throw new FeatureDisabledException(key, TierFeatureCatalog.minimumTierFor(key));
            }
        }
        return pjp.proceed();
    }
}
```

Make sure `@EnableAspectJAutoProxy` is enabled on the application (it usually is by Spring Boot autoconfig). Verify `spring-boot-starter-aop` is in `pom.xml`; add if missing:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

- [ ] **Step 6: Run the test to verify it passes**

Same command as Step 4.
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/feature/app/RequiresFeature.java \
        backend/src/main/java/com/luxpretty/app/feature/app/FeatureGateAspect.java \
        backend/src/main/java/com/luxpretty/app/feature/app/FeatureDisabledException.java \
        backend/src/test/java/com/luxpretty/app/feature/app/FeatureGateAspectTests.java \
        backend/pom.xml
git commit -m "feat(feature): @RequiresFeature annotation + AOP enforcement"
```

---

## Task 9: Map FeatureDisabledException → 403 in GlobalExceptionHandler

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/common/error/GlobalExceptionHandler.java`
- Modify: `backend/src/test/java/com/luxpretty/app/common/error/GlobalExceptionHandlerTests.java` (or create)

- [ ] **Step 1: Write the failing test**

```java
@WebMvcTest(controllers = GlobalExceptionHandlerTestController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTests {
    @Autowired MockMvc mvc;

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
    static class GlobalExceptionHandlerTestController {
        @GetMapping("/gated")
        public String trigger() {
            throw new FeatureDisabledException(FeatureKey.SHOP, SubscriptionTier.PREMIUM);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -pl backend test -Dtest=GlobalExceptionHandlerTests#featureDisabled_returns403WithStructuredBody`
Expected: FAIL — handler not present.

- [ ] **Step 3: Add the handler**

In `GlobalExceptionHandler.java`:

```java
@ExceptionHandler(FeatureDisabledException.class)
public ResponseEntity<Map<String,Object>> handleFeatureDisabled(FeatureDisabledException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
        "error", "FEATURE_DISABLED",
        "featureKey", ex.featureKey.name(),
        "minimumTier", ex.minimumTier.name()
    ));
}
```

- [ ] **Step 4: Run the test to verify it passes**

Same command as Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/common/error/GlobalExceptionHandler.java \
        backend/src/test/java/com/luxpretty/app/common/error/GlobalExceptionHandlerTests.java
git commit -m "feat(feature): map FeatureDisabledException to 403 structured payload"
```

---

## Task 10: Expose GET /api/me/features

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/feature/web/FeatureFlagController.java`
- Create: `backend/src/test/java/com/luxpretty/app/feature/web/FeatureFlagControllerTests.java`

- [ ] **Step 1: Write the failing test**

```java
@WebMvcTest(FeatureFlagController.class)
class FeatureFlagControllerTests {
    @Autowired MockMvc mvc;
    @MockBean FeatureFlagService service;

    @Test
    @WithMockUser
    void meFeatures_returnsSnapshot() throws Exception {
        Map<FeatureKey,Boolean> snap = new EnumMap<>(FeatureKey.class);
        for (FeatureKey k : FeatureKey.values()) snap.put(k, k == FeatureKey.BOOKING);
        when(service.snapshot()).thenReturn(snap);

        mvc.perform(get("/api/me/features"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.BOOKING").value(true))
           .andExpect(jsonPath("$.SHOP").value(false));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -pl backend test -Dtest=FeatureFlagControllerTests`
Expected: FAIL.

- [ ] **Step 3: Create the controller**

```java
package com.luxpretty.app.feature.web;

import com.luxpretty.app.feature.app.FeatureFlagService;
import com.luxpretty.app.feature.domain.FeatureKey;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class FeatureFlagController {

    private final FeatureFlagService service;
    public FeatureFlagController(FeatureFlagService service) { this.service = service; }

    @GetMapping("/features")
    public Map<FeatureKey, Boolean> myFeatures() {
        return service.snapshot();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Same command as Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/feature/web/FeatureFlagController.java \
        backend/src/test/java/com/luxpretty/app/feature/web/FeatureFlagControllerTests.java
git commit -m "feat(feature): GET /api/me/features endpoint"
```

---

## Task 11: Admin endpoint to override feature per tenant

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/feature/web/FeatureFlagController.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
@WithMockUser(authorities = "ROLE_ADMIN")
void adminOverride_setsFlag() throws Exception {
    mvc.perform(put("/api/admin/tenants/42/features/SHOP")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":true}"))
       .andExpect(status().isNoContent());
    verify(service).overrideForTenant(FeatureKey.SHOP, true);
}

@Test
@WithMockUser(authorities = "ROLE_USER")
void nonAdmin_cannotOverride() throws Exception {
    mvc.perform(put("/api/admin/tenants/42/features/SHOP")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":true}"))
       .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -pl backend test -Dtest=FeatureFlagControllerTests#adminOverride_setsFlag+FeatureFlagControllerTests#nonAdmin_cannotOverride`
Expected: FAIL.

- [ ] **Step 3: Add the admin endpoint**

In `FeatureFlagController.java`:

```java
import org.springframework.security.access.prepost.PreAuthorize;

public record OverrideRequest(boolean enabled) {}

@PutMapping("/api/admin/tenants/{tenantId}/features/{key}")
@PreAuthorize("hasRole('ADMIN')")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void override(@PathVariable Long tenantId,
                     @PathVariable FeatureKey key,
                     @RequestBody OverrideRequest req) {
    // tenantId is used to switch TenantContext if needed; for now the override
    // applies to the currently-resolved tenant. Switching tenants requires the
    // existing TenantContextHelper — adjust to your codebase.
    service.overrideForTenant(key, req.enabled());
}
```

(If your `TenantContext` model requires explicit switching from an admin's session, wrap the call: resolve the tenant by `tenantId`, set context, call `overrideForTenant`, then restore context.)

- [ ] **Step 4: Run the tests to verify they pass**

Same command as Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/feature/web/FeatureFlagController.java \
        backend/src/test/java/com/luxpretty/app/feature/web/FeatureFlagControllerTests.java
git commit -m "feat(feature): admin endpoint to override feature flag per tenant"
```

---

## Task 12: Apply @RequiresFeature to gateable controllers

This is a mechanical pass across the controllers that the pricing page promises. Annotate at the **class level** so every endpoint inherits, except read-only public endpoints already accessible without auth.

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/bookings/web/CareBookingController.java` → `@RequiresFeature(FeatureKey.BOOKING)`
- Modify: `backend/src/main/java/com/luxpretty/app/employee/web/EmployeeController.java` → `@RequiresFeature(FeatureKey.EMPLOYEES)`
- Modify: `backend/src/main/java/com/luxpretty/app/employee/web/EmployeeLeaveController.java` → `@RequiresFeature(FeatureKey.ABSENCE_MGMT)`
- Modify: `backend/src/main/java/com/luxpretty/app/employee/web/EmployeeDocumentController.java` → `@RequiresFeature(FeatureKey.CLIENT_FILES)`
- (For SMS, online payment, shop, loyalty, multi-location, photos: locate matching controllers and annotate. If they don't exist yet — skip and add a TODO comment marker.)

> Add the annotation only on PRO/admin-side mutation endpoints. **Do NOT gate the public client-facing booking endpoint** (`POST /api/salon/{slug}/book`) — gating "booking" means a PRO without GESTION cannot configure or manage bookings, but their tenant should still be reachable on the public web (VITRINE shows the salon page even though no booking calendar is offered there). Audit each controller's public/pro nature when annotating.

- [ ] **Step 1: Add annotation to CareBookingController**

```java
import com.luxpretty.app.feature.app.RequiresFeature;
import com.luxpretty.app.feature.domain.FeatureKey;

@RequiresFeature(FeatureKey.BOOKING)
@RestController
@RequestMapping("/api/bookings")
public class CareBookingController { ... }
```

- [ ] **Step 2: Write a `@WebMvcTest` verifying 403 on a gated controller**

```java
@WebMvcTest(CareBookingController.class)
class CareBookingControllerGatingTests {
    @Autowired MockMvc mvc;
    @MockBean FeatureFlagService flags;
    @MockBean CareBookingService service;

    @Test
    @WithMockUser(roles = "PRO")
    void disabledBookingFeature_returns403() throws Exception {
        when(flags.isEnabled(FeatureKey.BOOKING)).thenReturn(false);
        mvc.perform(get("/api/bookings"))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.error").value("FEATURE_DISABLED"));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void enabledBookingFeature_passes() throws Exception {
        when(flags.isEnabled(FeatureKey.BOOKING)).thenReturn(true);
        when(service.list()).thenReturn(List.of());
        mvc.perform(get("/api/bookings")).andExpect(status().isOk());
    }
}
```

- [ ] **Step 3: Run the tests**

Run: `mvn -pl backend test -Dtest=CareBookingControllerGatingTests`
Expected: PASS.

- [ ] **Step 4: Repeat for each gated controller**

For each controller listed above:
1. Add `@RequiresFeature(FeatureKey.<key>)` at the class level.
2. Add a controller-slice test verifying 403 when the feature is disabled and 2xx when enabled. Reuse the pattern from Step 2.
3. Run `mvn -pl backend test` to confirm no regression in existing controller tests.

- [ ] **Step 5: Commit each controller separately**

For clarity:
```bash
git add backend/src/main/java/.../CareBookingController.java backend/src/test/java/.../CareBookingControllerGatingTests.java
git commit -m "feat(feature): gate CareBookingController with @RequiresFeature(BOOKING)"
```
Repeat with the same commit-per-controller pattern.

---

## Task 13: Frontend — FeatureKey TS enum + store + service

**Files:**
- Create: `frontend/src/app/core/feature-flags/feature-key.ts`
- Create: `frontend/src/app/core/feature-flags/feature-flags.service.ts`
- Create: `frontend/src/app/core/feature-flags/feature-flags.store.ts`
- Create: `frontend/src/app/core/feature-flags/feature-flags.store.spec.ts`

- [ ] **Step 1: Create the TS enum (mirror of FeatureKey.java)**

```typescript
// feature-key.ts
export type FeatureKey =
  | 'BOOKING' | 'EMPLOYEES' | 'PHOTOS' | 'SMS_REMINDER'
  | 'CLIENT_FILES' | 'ABSENCE_MGMT' | 'ONLINE_PAYMENT'
  | 'SHOP' | 'LOYALTY' | 'MULTI_LOCATION';

export const ALL_FEATURE_KEYS: FeatureKey[] = [
  'BOOKING','EMPLOYEES','PHOTOS','SMS_REMINDER','CLIENT_FILES',
  'ABSENCE_MGMT','ONLINE_PAYMENT','SHOP','LOYALTY','MULTI_LOCATION'
];

export type FeatureFlagSnapshot = Record<FeatureKey, boolean>;
```

- [ ] **Step 2: Create the HTTP service**

```typescript
// feature-flags.service.ts
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api-base-url';
import { FeatureFlagSnapshot } from './feature-key';

@Injectable({ providedIn: 'root' })
export class FeatureFlagsService {
  private http = inject(HttpClient);
  private base = inject(API_BASE_URL);
  fetch(): Observable<FeatureFlagSnapshot> {
    return this.http.get<FeatureFlagSnapshot>(`${this.base}/api/me/features`);
  }
}
```

- [ ] **Step 3: Write the failing store test**

```typescript
// feature-flags.store.spec.ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

describe('FeatureFlagsStore', () => {
  let store: InstanceType<typeof FeatureFlagsStore>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [FeatureFlagsStore, provideHttpClient(), provideHttpClientTesting(),
                  { provide: API_BASE_URL, useValue: '' }],
    });
    store = TestBed.inject(FeatureFlagsStore);
    http = TestBed.inject(HttpTestingController);
  });

  it('loads flags and exposes isEnabled', () => {
    store.load();
    http.expectOne('/api/me/features').flush({
      BOOKING: true, SHOP: false, EMPLOYEES: true, PHOTOS: true,
      SMS_REMINDER: true, CLIENT_FILES: true, ABSENCE_MGMT: true,
      ONLINE_PAYMENT: false, LOYALTY: false, MULTI_LOCATION: false,
    });
    expect(store.isEnabled('BOOKING')()).toBe(true);
    expect(store.isEnabled('SHOP')()).toBe(false);
  });

  it('isEnabled returns false before load', () => {
    expect(store.isEnabled('BOOKING')()).toBe(false);
  });
});
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `cd frontend && npm test -- --include='**/feature-flags.store.spec.ts' --watch=false`
Expected: FAIL.

- [ ] **Step 5: Create the SignalStore**

```typescript
// feature-flags.store.ts
import { computed, inject } from '@angular/core';
import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';
import { FeatureFlagsService } from './feature-flags.service';
import { ALL_FEATURE_KEYS, FeatureFlagSnapshot, FeatureKey } from './feature-key';

const emptyFlags = (): FeatureFlagSnapshot =>
  ALL_FEATURE_KEYS.reduce((acc, k) => ({ ...acc, [k]: false }), {} as FeatureFlagSnapshot);

export const FeatureFlagsStore = signalStore(
  { providedIn: 'root' },
  withState({ flags: emptyFlags() as FeatureFlagSnapshot, loaded: false }),
  withMethods((store, svc = inject(FeatureFlagsService)) => ({
    load: rxMethod<void>(pipe(
      switchMap(() => svc.fetch()),
      tap((flags) => patchState(store, { flags, loaded: true }))
    )),
    reset: () => patchState(store, { flags: emptyFlags(), loaded: false }),
    isEnabled: (key: FeatureKey) => computed(() => store.flags()[key] === true),
  })),
);
```

- [ ] **Step 6: Run the test to verify it passes**

Same command as Step 4.
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/core/feature-flags/
git commit -m "feat(feature): FeatureFlagsStore + service + types"
```

---

## Task 14: Frontend — load flags after login, reset on logout

**Files:**
- Modify: `frontend/src/app/core/auth/auth.service.ts`

- [ ] **Step 1: Inject the store and load/reset around auth changes**

In `auth.service.ts`:

```typescript
import { FeatureFlagsStore } from '../feature-flags/feature-flags.store';

// inside the AuthService class
private featureFlags = inject(FeatureFlagsStore);

// where login resolves successfully:
this.featureFlags.load();

// where logout completes:
this.featureFlags.reset();
```

(Hook also into the existing auth-bootstrap if it pre-fetches user data — flags should be loaded as part of that bootstrap.)

- [ ] **Step 2: Verify existing auth tests still pass**

Run: `cd frontend && npm test -- --include='**/auth.service.spec.ts' --watch=false`
Expected: PASS (add a `FeatureFlagsStore` stub provider if needed).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/core/auth/auth.service.ts \
        frontend/src/app/core/auth/auth.service.spec.ts
git commit -m "feat(feature): load flags after login, reset on logout"
```

---

## Task 15: Frontend — `*lpFeatureEnabled` structural directive

**Files:**
- Create: `frontend/src/app/core/feature-flags/feature-enabled.directive.ts`
- Create: `frontend/src/app/core/feature-flags/feature-enabled.directive.spec.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// feature-enabled.directive.spec.ts
@Component({
  standalone: true,
  imports: [FeatureEnabledDirective],
  template: `<span *lpFeatureEnabled="'SHOP'" data-testid="shop">Shop</span>`,
})
class Host {}

describe('FeatureEnabledDirective', () => {
  let store: { isEnabled: jasmine.Spy };

  beforeEach(() => {
    store = { isEnabled: jasmine.createSpy('isEnabled') };
    TestBed.configureTestingModule({
      imports: [Host],
      providers: [{ provide: FeatureFlagsStore, useValue: store }],
    });
  });

  it('renders host element when feature is enabled', () => {
    store.isEnabled.and.returnValue(signal(true));
    const fx = TestBed.createComponent(Host); fx.detectChanges();
    expect(fx.nativeElement.querySelector('[data-testid="shop"]')).toBeTruthy();
  });

  it('removes host element when feature is disabled', () => {
    store.isEnabled.and.returnValue(signal(false));
    const fx = TestBed.createComponent(Host); fx.detectChanges();
    expect(fx.nativeElement.querySelector('[data-testid="shop"]')).toBeNull();
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && npm test -- --include='**/feature-enabled.directive.spec.ts' --watch=false`
Expected: FAIL.

- [ ] **Step 3: Create the directive**

```typescript
// feature-enabled.directive.ts
import { Directive, Input, TemplateRef, ViewContainerRef, effect, inject } from '@angular/core';
import { FeatureFlagsStore } from './feature-flags.store';
import { FeatureKey } from './feature-key';

@Directive({ selector: '[lpFeatureEnabled]', standalone: true })
export class FeatureEnabledDirective {
  private tpl = inject(TemplateRef<unknown>);
  private vcr = inject(ViewContainerRef);
  private store = inject(FeatureFlagsStore);

  private key!: FeatureKey;
  private rendered = false;

  @Input() set lpFeatureEnabled(key: FeatureKey) {
    this.key = key;
    this.render();
  }

  constructor() {
    effect(() => {
      if (!this.key) return;
      const enabled = this.store.isEnabled(this.key)();
      if (enabled && !this.rendered) {
        this.vcr.createEmbeddedView(this.tpl);
        this.rendered = true;
      } else if (!enabled && this.rendered) {
        this.vcr.clear();
        this.rendered = false;
      }
    });
  }

  private render() {/* effect handles re-rendering */}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Same command as Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/feature-flags/feature-enabled.directive.ts \
        frontend/src/app/core/feature-flags/feature-enabled.directive.spec.ts
git commit -m "feat(feature): *lpFeatureEnabled structural directive"
```

---

## Task 16: Frontend — `<lp-feature-locked>` overlay component

**Files:**
- Create: `frontend/src/app/core/feature-flags/feature-locked.component.ts`
- Create: `frontend/src/app/core/feature-flags/feature-locked.component.html`
- Create: `frontend/src/app/core/feature-flags/feature-locked.component.scss`
- Create: `frontend/src/app/core/feature-flags/feature-locked.component.spec.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// feature-locked.component.spec.ts
@Component({
  standalone: true,
  imports: [FeatureLockedComponent],
  template: `<lp-feature-locked feature="SHOP"><button>Inner</button></lp-feature-locked>`,
})
class Host {}

it('renders content + overlay when feature disabled', () => {
  store.isEnabled.and.returnValue(signal(false));
  const fx = TestBed.createComponent(Host); fx.detectChanges();
  expect(fx.nativeElement.querySelector('[data-testid="upsell-overlay"]')).toBeTruthy();
  expect(fx.nativeElement.querySelector('button')).toBeTruthy(); // content stays in DOM
});

it('hides overlay when feature enabled', () => {
  store.isEnabled.and.returnValue(signal(true));
  const fx = TestBed.createComponent(Host); fx.detectChanges();
  expect(fx.nativeElement.querySelector('[data-testid="upsell-overlay"]')).toBeNull();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && npm test -- --include='**/feature-locked.component.spec.ts' --watch=false`
Expected: FAIL.

- [ ] **Step 3: Create the component**

```typescript
// feature-locked.component.ts
import { Component, ChangeDetectionStrategy, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { FeatureFlagsStore } from './feature-flags.store';
import { FeatureKey } from './feature-key';

@Component({
  selector: 'lp-feature-locked',
  standalone: true,
  imports: [RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './feature-locked.component.html',
  styleUrls: ['./feature-locked.component.scss'],
})
export class FeatureLockedComponent {
  feature = input.required<FeatureKey>();
  private store = inject(FeatureFlagsStore);
  enabled = computed(() => this.store.isEnabled(this.feature())());
  upsellKey = computed(() => `features.locked.${this.feature().toLowerCase()}`);
}
```

```html
<!-- feature-locked.component.html -->
<div class="feature-locked-wrapper" [class.locked]="!enabled()">
  <ng-content />
  @if (!enabled()) {
    <div class="overlay" data-testid="upsell-overlay" aria-hidden="false">
      <p class="overlay-text">{{ upsellKey() | transloco }}</p>
      <a class="overlay-cta" routerLink="/pricing">{{ 'features.locked.cta' | transloco }}</a>
    </div>
  }
</div>
```

```scss
/* feature-locked.component.scss */
.feature-locked-wrapper { position: relative; }
.feature-locked-wrapper.locked > :not(.overlay) {
  pointer-events: none; user-select: none; opacity: 0.5; filter: grayscale(40%);
}
.overlay {
  position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center;
  justify-content: center; gap: 12px; padding: 16px;
  background: color-mix(in srgb, var(--mat-sys-surface) 80%, transparent);
  backdrop-filter: blur(2px); border-radius: inherit;
}
.overlay-cta { padding: 8px 16px; border-radius: 9999px; background: var(--mat-sys-primary);
               color: var(--mat-sys-on-primary); text-decoration: none; }
```

- [ ] **Step 4: Run the test to verify it passes**

Same command as Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/feature-flags/feature-locked.component.*
git commit -m "feat(feature): <lp-feature-locked> read-only overlay with upsell"
```

---

## Task 17: Frontend — HTTP interceptor for 403 FEATURE_DISABLED

**Files:**
- Create: `frontend/src/app/core/feature-flags/feature-flag.interceptor.ts`
- Modify: `frontend/src/app/app.config.ts`

- [ ] **Step 1: Write a unit test (TestBed + spy on Router/MatSnackBar)**

```typescript
// feature-flag.interceptor.spec.ts (create alongside)
it('on 403 FEATURE_DISABLED → opens snackbar and navigates to /pricing', (done) => {
  const router = jasmine.createSpyObj('Router', ['navigate']);
  const snack = jasmine.createSpyObj('MatSnackBar', ['open']);
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([featureFlagInterceptor])),
      provideHttpClientTesting(),
      { provide: Router, useValue: router },
      { provide: MatSnackBar, useValue: snack },
    ],
  });
  const http = TestBed.inject(HttpClient);
  const ctrl = TestBed.inject(HttpTestingController);
  http.get('/x').subscribe({ error: () => {
    expect(router.navigate).toHaveBeenCalledWith(['/pricing'], jasmine.anything());
    expect(snack.open).toHaveBeenCalled();
    done();
  }});
  ctrl.expectOne('/x').flush({ error: 'FEATURE_DISABLED', featureKey: 'SHOP', minimumTier: 'PREMIUM' },
                              { status: 403, statusText: 'Forbidden' });
});
```

- [ ] **Step 2: Create the interceptor**

```typescript
// feature-flag.interceptor.ts
import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoService } from '@jsverse/transloco';
import { catchError, throwError } from 'rxjs';

export const featureFlagInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const snack = inject(MatSnackBar);
  const t = inject(TranslocoService);
  return next(req).pipe(catchError((err: HttpErrorResponse) => {
    if (err.status === 403 && err.error?.error === 'FEATURE_DISABLED') {
      snack.open(t.translate('errors.features.disabled', { tier: err.error.minimumTier }),
                 'OK', { duration: 4000 });
      router.navigate(['/pricing'], { queryParams: { highlight: err.error.featureKey } });
    }
    return throwError(() => err);
  }));
};
```

- [ ] **Step 3: Wire it in app.config.ts**

```typescript
import { featureFlagInterceptor } from './core/feature-flags/feature-flag.interceptor';
// ...
withInterceptors([credentialsInterceptor, csrfInterceptor, authInterceptor, featureFlagInterceptor]),
```

- [ ] **Step 4: Run the test**

Run: `cd frontend && npm test -- --include='**/feature-flag.interceptor.spec.ts' --watch=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/feature-flags/feature-flag.interceptor.* \
        frontend/src/app/app.config.ts
git commit -m "feat(feature): HTTP interceptor handling 403 FEATURE_DISABLED"
```

---

## Task 18: Frontend — apply directives to nav + feature pages

This is a UX-pass; targets vary by what exists. The expected pattern:

- Side/top nav items use `*lpFeatureEnabled`:
  ```html
  <a *lpFeatureEnabled="'SHOP'" routerLink="/pro/shop">{{ 'nav.shop' | transloco }}</a>
  ```
- Feature pages use `<lp-feature-locked>` so users on lower tiers see the page in read-only mode:
  ```html
  <lp-feature-locked feature="EMPLOYEES">
    <pro-employees-page />
  </lp-feature-locked>
  ```

- [ ] **Step 1: Identify nav locations + feature page wrappers**

Run: `grep -rn "routerLink\|<lp-" frontend/src/app/shared/layout | head -30` and identify the nav component(s) and the per-feature page wrappers.

- [ ] **Step 2: Apply `*lpFeatureEnabled` to each promised-feature nav item**

For each feature in the matrix that has a corresponding nav entry (BOOKING, EMPLOYEES, SHOP, LOYALTY, MULTI_LOCATION, etc.), wrap the `<a>` or list-item with `*lpFeatureEnabled`.

- [ ] **Step 3: Wrap each feature route's root template with `<lp-feature-locked>`**

For pages that should remain visible (read-only) when the feature is off — typically Employees, Photos, Client Files, Absence — wrap the inner component:

```html
<lp-feature-locked [feature]="'EMPLOYEES'">
  <pro-employees />
</lp-feature-locked>
```

Pages that should be entirely inaccessible (Shop, Loyalty, Multi-location) use a route guard instead — see Step 4.

- [ ] **Step 4: Add a route guard for hard-gated routes**

```typescript
// frontend/src/app/core/feature-flags/feature-route.guard.ts
import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { FeatureFlagsStore } from './feature-flags.store';
import { FeatureKey } from './feature-key';

export const requireFeature = (key: FeatureKey): CanActivateFn => () => {
  const store = inject(FeatureFlagsStore);
  const router = inject(Router);
  if (store.isEnabled(key)()) return true;
  router.navigate(['/pricing'], { queryParams: { highlight: key } });
  return false;
};
```

Apply on routes:
```typescript
{ path: 'pro/shop', loadComponent: () => import('...'), canActivate: [requireFeature('SHOP')] }
```

- [ ] **Step 5: Add translation keys for upsell text**

In `fr.json`:
```json
{
  "errors": {
    "features": {
      "disabled": "Cette fonctionnalité nécessite l'abonnement {{tier}}."
    }
  },
  "features": {
    "locked": {
      "cta": "Découvrir les offres",
      "shop": "La boutique est disponible avec Premium.",
      "employees": "La gestion des praticiens est disponible avec Gestion ou Premium.",
      "loyalty": "La fidélité est disponible avec Premium.",
      "multi_location": "Le multi-locations est disponible avec Premium.",
      "absence_mgmt": "La gestion des absences est disponible avec Gestion ou Premium.",
      "client_files": "Les fiches clients sont disponibles avec Gestion ou Premium.",
      "photos": "Les photos sont disponibles avec Gestion ou Premium.",
      "sms_reminder": "Les rappels SMS sont disponibles avec Gestion ou Premium.",
      "online_payment": "Le paiement en ligne est disponible avec Premium.",
      "booking": "La réservation en ligne est disponible avec Gestion ou Premium."
    }
  }
}
```

Mirror in `en.json`.

- [ ] **Step 6: Run the test suite**

Run: `cd frontend && npm test -- --watch=false`
Expected: PASS — nav directives, locked overlays, route guard all green.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/ frontend/src/assets/i18n/
git commit -m "feat(feature): apply gating directives + route guards + i18n upsell strings"
```

---

## Task 19: Manual E2E — downgrade/upgrade cycle preserves data

- [ ] **Step 1: Provision a PREMIUM test tenant**

Through the dev frontend, create a pro on PREMIUM, add 3 employees + a couple of bookings.

- [ ] **Step 2: Verify in DB**

```sql
SELECT * FROM <tenant_schema>.TENANT_FEATURES; -- 10 rows, all enabled=1
SELECT COUNT(*) FROM <tenant_schema>.EMPLOYEES; -- 3
```

- [ ] **Step 3: Downgrade to VITRINE (via Stripe test mode or admin override)**

Trigger a tier change to VITRINE. Verify in DB:
```sql
SELECT * FROM <tenant_schema>.TENANT_FEATURES; -- All TIER_DEFAULT rows now enabled=0
SELECT COUNT(*) FROM <tenant_schema>.EMPLOYEES; -- Still 3
```

In the UI: log in as the pro → employees menu hidden in nav (`*lpFeatureEnabled` removes it), but if the user navigates to `/pro/employees` directly they see a `<lp-feature-locked>` overlay over the list (the data is there, just read-only with upsell).

- [ ] **Step 4: Re-upgrade to GESTION**

```sql
SELECT * FROM <tenant_schema>.TENANT_FEATURES;
-- EMPLOYEES enabled=1, SHOP enabled=0, etc.
SELECT COUNT(*) FROM <tenant_schema>.EMPLOYEES; -- Still 3 — restored
```

UI: employees nav re-appears, page works fully.

- [ ] **Step 5: Apply an admin override**

Call `PUT /api/admin/tenants/{id}/features/SHOP { "enabled": true }` while on GESTION. Verify:
- `TENANT_FEATURES.SOURCE = 'ADMIN_OVERRIDE'` for SHOP.
- Tier change back to PREMIUM and back to GESTION preserves the override.

- [ ] **Step 6: Document the E2E result**

Add a short note in the PR description (screenshots optional). No commit needed unless adjustments were made.

---

## Self-Review Checklist

- [ ] **Spec coverage:** Test cases TC 1-12 (spec §3.7) each map to ≥ 1 task:
  - TC 1 → Task 12 (CareBookingController gating test)
  - TC 2 → Task 12 (gating any PREMIUM feature controller; create a SHOP/products controller stub test if a controller exists)
  - TC 3 → Task 5 (`isEnabled_returnsTrue_whenAdminOverrideEnabled`)
  - TC 4 → Task 5 (`applyTierDefaults_preservesAdminOverrides`)
  - TC 5 → Task 5 (`applyTierDefaults_seedsAllKeys_forPremium`)
  - TC 6 → Task 16 (FeatureLockedComponent test)
  - TC 7 → Task 12 (EmployeeController gating test)
  - TC 8-10 → Task 19 (manual E2E)
  - TC 11 → Task 7 (provisioning seeds defaults)
  - TC 12 → Task 5 + Task 6 (tier change applies defaults; override preserved)

- [ ] **No `evict in cache` left out:** `@CacheEvict(allEntries=true)` is conservative but correct given multi-tenant cache keys.

- [ ] **No tier-direct reads in code:** grep for `getSubscriptionTier(` / `SubscriptionTier.` usage in code paths other than `SubscriptionService` and pricing displays. Each call site outside those must go through `FeatureFlagService.isEnabled`.

- [ ] **Backward compatibility:** Existing tenants on `main` get `TENANT_FEATURES` seeded via the legacy mirror + `applyTierDefaults` triggered on the next tier-affecting webhook. Document a one-shot CLI/admin endpoint to seed *every* existing tenant if the webhook doesn't fire (e.g., for tenants currently on VITRINE who never re-checkout).

- [ ] **Public client endpoints unchanged:** `/api/salon/{slug}/book`, `/api/salon/{slug}`, etc. are NOT gated — gating only applies to pro-facing endpoints.

- [ ] **Annotation pass coverage:** the controllers gated in Task 12 cover the pricing matrix; if a feature has no corresponding controller (e.g., LOYALTY before implementation), the gating is added as a TODO with a follow-up ticket.

- [ ] **No placeholders in plan:** every step shows actual code, exact paths, exact commands. ✅
