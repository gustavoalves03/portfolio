package com.prettyface.app.multitenancy.integration;

import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.domain.CareStatus;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.domain.Category;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.multitenancy.TenantSchemaManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves schema-per-tenant isolation end-to-end: data written under one
 * TenantContext is invisible to reads performed under a different context.
 *
 * <p>Unit tests mock the repository; this test exercises the real Hibernate
 * multi-tenant connection provider + H2 schema switching.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TenantIsolationIntegrationTests {

    @Autowired
    private CareRepository careRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TenantSchemaManager tenantSchemaManager;

    private static final String TENANT_A = "isolation-salon-a";
    private static final String TENANT_B = "isolation-salon-b";

    @BeforeEach
    void provisionTenants() {
        // provisionSchema is idempotent — both tenants get their own schema
        // (TENANT_ISOLATION_SALON_A, TENANT_ISOLATION_SALON_B) with the usual
        // tenant-scoped tables.
        tenantSchemaManager.provisionSchema(TENANT_A);
        tenantSchemaManager.migrateSchema(TENANT_A);
        tenantSchemaManager.provisionSchema(TENANT_B);
        tenantSchemaManager.migrateSchema(TENANT_B);
        TenantContext.clear();
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void dataInTenantAIsInvisibleToTenantB() {
        // Seed tenant A with one Care
        TenantContext.setCurrentTenant(TENANT_A);
        Long careInA = seedCare("A-only facial");

        // Seed tenant B with a different Care
        TenantContext.setCurrentTenant(TENANT_B);
        Long careInB = seedCare("B-only massage");

        // Note: both schemas use IDENTITY columns starting at 1, so careInA
        // and careInB may collide on id — isolation is asserted by row content
        // (name), which is what actually matters for cross-tenant data leakage.

        // When scoped to tenant A, we see A's data but not B's
        TenantContext.setCurrentTenant(TENANT_A);
        List<Care> caresAsA = careRepository.findAll();
        assertThat(caresAsA)
                .as("tenant A should see exactly one Care (its own)")
                .extracting(Care::getName)
                .containsExactly("A-only facial");
        assertThat(caresAsA)
                .as("tenant A must never see tenant B's Care")
                .extracting(Care::getName)
                .doesNotContain("B-only massage");
        assertThat(careRepository.findById(careInA))
                .hasValueSatisfying(c -> assertThat(c.getName()).isEqualTo("A-only facial"));

        // When scoped to tenant B, we see B's data but not A's
        TenantContext.setCurrentTenant(TENANT_B);
        List<Care> caresAsB = careRepository.findAll();
        assertThat(caresAsB)
                .as("tenant B should see exactly one Care (its own)")
                .extracting(Care::getName)
                .containsExactly("B-only massage");
        assertThat(caresAsB)
                .as("tenant B must never see tenant A's Care")
                .extracting(Care::getName)
                .doesNotContain("A-only facial");
        assertThat(careRepository.findById(careInB))
                .hasValueSatisfying(c -> assertThat(c.getName()).isEqualTo("B-only massage"));
    }

    @Test
    void requireActiveRejectsCallersWithNoTenantContext() {
        TenantContext.clear();
        // Representative defense-in-depth guard used by mutating service methods —
        // it must refuse to operate when no tenant is active.
        assertThatThrownBy(TenantContext::requireActive)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No tenant context");
    }

    // --- helpers --------------------------------------------------------------

    private Long seedCare(String name) {
        Category category = new Category();
        category.setName("Category " + name);
        category.setDescription("cat for " + name);
        category = categoryRepository.save(category);

        Care care = new Care();
        care.setName(name);
        care.setPrice(5000);
        care.setDescription("Description for " + name);
        care.setStatus(CareStatus.ACTIVE);
        care.setDuration(30);
        care.setCategory(category);
        return careRepository.save(care).getId();
    }
}
