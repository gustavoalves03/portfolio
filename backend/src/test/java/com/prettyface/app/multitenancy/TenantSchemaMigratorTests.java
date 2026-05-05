package com.prettyface.app.multitenancy;

import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSchemaMigratorTests {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantSchemaManager schemaManager;

    @Test
    void run_reprovisionsMissingTenantSchemaBeforeMigrating() throws Exception {
        Tenant tenant = Tenant.builder()
                .slug("sophie-martin")
                .name("Sophie Martin")
                .ownerId(1L)
                .build();

        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(schemaManager.tenantSchemaExists("sophie-martin")).thenReturn(false);

        new TenantSchemaMigrator(tenantRepository, schemaManager).run(null);

        verify(schemaManager).tenantSchemaExists("sophie-martin");
        verify(schemaManager).provisionSchema("sophie-martin");
        verify(schemaManager, never()).migrateSchema("sophie-martin");
    }

    @Test
    void run_migratesExistingTenantSchema() throws Exception {
        Tenant tenant = Tenant.builder()
                .slug("camille-dubois")
                .name("Camille Dubois")
                .ownerId(2L)
                .build();

        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(schemaManager.tenantSchemaExists("camille-dubois")).thenReturn(true);

        new TenantSchemaMigrator(tenantRepository, schemaManager).run(null);

        verify(schemaManager).tenantSchemaExists("camille-dubois");
        verify(schemaManager).migrateSchema("camille-dubois");
        verify(schemaManager, never()).provisionSchema("camille-dubois");
    }
}
