package com.prettyface.app.multitenancy;

import com.prettyface.app.tenant.app.SlugUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TenantSchemaManager utilities.
 *
 * NOTE: Integration tests requiring Oracle schema provisioning (T2.1 NFR10 timing,
 * T2.2 schema isolation) cannot run in CI with H2 in-memory DB.
 * Those tests require a live Oracle DB and should be run manually or in an
 * Oracle-backed integration test profile.
 */
class TenantSchemaManagerTests {

    // T2.1: Schema name conversion
    @Test
    void toSchemaName_convertsSlugToOracleSchemaName() {
        String schemaName = TenantSchemaManager.toSchemaName("salon-sophie");
        assertThat(schemaName).isEqualTo("TENANT_SALON_SOPHIE");
    }

    // Slug generation tests (used by TenantProvisioningService)
    @Test
    void slugUtils_convertsNameToKebabCase() {
        assertThat(SlugUtils.toSlug("Salon Sophie")).isEqualTo("salon-sophie");
    }

    @Test
    void slugUtils_removesAccents() {
        assertThat(SlugUtils.toSlug("Beauté & Élégance")).isEqualTo("beaute-elegance");
    }

    @Test
    void slugUtils_handlesSpecialCharacters() {
        assertThat(SlugUtils.toSlug("L'Atelier du Maquillage")).isEqualTo("l-atelier-du-maquillage");
    }

    @Test
    void slugUtils_handlesAllLowercase() {
        assertThat(SlugUtils.toSlug("myfirstsalon")).isEqualTo("myfirstsalon");
    }
}
