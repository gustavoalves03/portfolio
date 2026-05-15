package com.luxpretty.app.tenant.repo;

import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionStatus;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.domain.TenantStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase
class TenantRepositoryStripeTests {

    @Autowired
    TenantRepository repo;

    private Tenant baseTenant(long ownerId) {
        return Tenant.builder()
                .slug("test-tenant-" + ownerId)
                .name("Test " + ownerId)
                .ownerId(ownerId)
                .status(TenantStatus.DRAFT)
                .build();
    }

    @Test
    void tenant_persists_with_default_vitrine_status() {
        // GIVEN a fresh tenant (no Stripe customer yet)
        Tenant saved = repo.save(baseTenant(1L));
        repo.flush();

        // WHEN reloaded
        Tenant reloaded = repo.findById(saved.getId()).orElseThrow();

        // THEN defaults match: VITRINE_FREE / VITRINE / FREE
        assertThat(reloaded.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.VITRINE_FREE);
        assertThat(reloaded.getSubscriptionTier()).isEqualTo(SubscriptionTier.VITRINE);
        assertThat(reloaded.getSubscriptionBilling()).isEqualTo(SubscriptionBilling.FREE);
        assertThat(reloaded.getStripeCustomerId()).isNull();
        assertThat(reloaded.getStripeSubscriptionId()).isNull();
        assertThat(reloaded.getCurrentPeriodEnd()).isNull();
        assertThat(reloaded.getTrialEnd()).isNull();
    }

    @Test
    void findByStripeCustomerId_returns_tenant() {
        Tenant t = baseTenant(2L);
        t.setStripeCustomerId("cus_TEST_123");
        repo.saveAndFlush(t);

        Optional<Tenant> found = repo.findByStripeCustomerId("cus_TEST_123");

        assertThat(found).isPresent();
        assertThat(found.get().getOwnerId()).isEqualTo(2L);
    }

    @Test
    void findByStripeSubscriptionId_returns_tenant() {
        Tenant t = baseTenant(3L);
        t.setStripeSubscriptionId("sub_TEST_456");
        repo.saveAndFlush(t);

        Optional<Tenant> found = repo.findByStripeSubscriptionId("sub_TEST_456");

        assertThat(found).isPresent();
        assertThat(found.get().getOwnerId()).isEqualTo(3L);
    }

    @Test
    void findByOwnerId_returns_tenant() {
        Tenant t = baseTenant(4L);
        repo.saveAndFlush(t);

        Optional<Tenant> found = repo.findByOwnerId(4L);

        assertThat(found).isPresent();
        assertThat(found.get().getSlug()).isEqualTo("test-tenant-4");
    }
}
