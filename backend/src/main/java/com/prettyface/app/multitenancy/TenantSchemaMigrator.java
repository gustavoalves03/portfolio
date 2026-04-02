package com.prettyface.app.multitenancy;

import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs at application startup to ensure all existing tenant schemas
 * have the latest table definitions (e.g., new employee tables and columns).
 */
@Component
public class TenantSchemaMigrator implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(TenantSchemaMigrator.class);

    private final TenantRepository tenantRepository;
    private final TenantSchemaManager schemaManager;

    public TenantSchemaMigrator(TenantRepository tenantRepository, TenantSchemaManager schemaManager) {
        this.tenantRepository = tenantRepository;
        this.schemaManager = schemaManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Tenant> allTenants = tenantRepository.findAll();
        if (allTenants.isEmpty()) {
            logger.info("No tenants to migrate.");
            return;
        }

        logger.info("Migrating {} tenant schema(s) to add employee tables and columns...", allTenants.size());

        for (Tenant tenant : allTenants) {
            try {
                schemaManager.migrateSchema(tenant.getSlug());
                logger.info("Migrated tenant schema: {}", tenant.getSlug());
            } catch (Exception e) {
                logger.error("Failed to migrate tenant {}: {}", tenant.getSlug(), e.getMessage());
            }
        }

        logger.info("Tenant schema migration complete.");
    }
}
