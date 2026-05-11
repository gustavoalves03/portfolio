package com.luxpretty.app.multitenancy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Runs Flyway migrations against a single tenant Oracle schema.
 *
 * <p>Used at tenant creation only. Legacy tenants (provisioned before this
 * service existed) are still migrated by {@link TenantSchemaMigrator} via
 * idempotent ALTERs at app startup; once we have a staging environment we
 * can baseline them here and switch Hibernate to {@code ddl-auto=validate}.
 *
 * <p>Connects with the dedicated provisioning account (DBA-grade) configured
 * via {@code app.multitenancy.provisioning.*}, since creating tables and
 * granting privileges in the tenant schema requires elevated rights.
 */
@Component
public class TenantFlywayService {

    private static final Logger logger = LoggerFactory.getLogger(TenantFlywayService.class);
    private static final String TENANT_LOCATIONS = "classpath:db/migration/tenant";

    private final String applicationSchemaName;
    private final String provisioningJdbcUrl;
    private final String provisioningUsername;
    private final String provisioningPassword;

    public TenantFlywayService(
            @Value("${app.multitenancy.application-schema:${APP_USER:appuser}}") String applicationSchemaName,
            @Value("${app.multitenancy.provisioning.jdbc-url:${spring.datasource.url}}") String provisioningJdbcUrl,
            @Value("${app.multitenancy.provisioning.username:}") String provisioningUsername,
            @Value("${app.multitenancy.provisioning.password:}") String provisioningPassword
    ) {
        this.applicationSchemaName = normalizeOracleIdentifier(applicationSchemaName);
        this.provisioningJdbcUrl = provisioningJdbcUrl;
        this.provisioningUsername = provisioningUsername;
        this.provisioningPassword = provisioningPassword;
    }

    /**
     * Run all pending tenant-scoped Flyway migrations against the schema for {@code slug}.
     * The schema (and its Oracle user) must already exist — call
     * {@link TenantSchemaManager#provisionSchema(String)} first.
     */
    public MigrateResult migrate(String tenantSchema, String tenantSlug) {
        if (!StringUtils.hasText(tenantSchema)) {
            throw new IllegalArgumentException("Tenant schema must not be blank");
        }
        if (!StringUtils.hasText(tenantSlug)) {
            throw new IllegalArgumentException("Tenant slug must not be blank");
        }
        if (!StringUtils.hasText(provisioningUsername) || !StringUtils.hasText(provisioningPassword)) {
            throw new IllegalStateException(
                    "Tenant Flyway requires app.multitenancy.provisioning.{username,password} to be set"
            );
        }

        logger.info("Running tenant Flyway migrations for schema {} (slug {})", tenantSchema, tenantSlug);

        Flyway flyway = Flyway.configure()
                .dataSource(provisioningJdbcUrl, provisioningUsername, provisioningPassword)
                .schemas(tenantSchema)
                .defaultSchema(tenantSchema)
                .locations(TENANT_LOCATIONS)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .placeholders(Map.of(
                        "tenantSchema", tenantSchema,
                        "tenantSlug", tenantSlug,
                        "appSchema", applicationSchemaName
                ))
                .load();

        MigrateResult result = flyway.migrate();
        logger.info("Tenant Flyway migration done for {} — {} migrations applied",
                tenantSchema, result.migrationsExecuted);
        return result;
    }

    private static String normalizeOracleIdentifier(String identifier) {
        return identifier == null ? null : identifier.toUpperCase(java.util.Locale.ROOT);
    }
}
