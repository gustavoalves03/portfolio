package com.prettyface.app.config;

import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.multitenancy.TenantSchemaManager;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Configures Hibernate multi-tenancy using the ALTER SESSION SET CURRENT_SCHEMA approach.
 * <p>
 * A single connection pool (APPUSER) is shared across all tenants. Before each tenant
 * query Hibernate switches the Oracle session schema; on release it resets to the default.
 */
@Configuration
public class DataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    /** Must match the Oracle username (upper-cased) used by the main datasource. */
    static final String DEFAULT_SCHEMA = "APPUSER";

    @Bean
    HibernatePropertiesCustomizer hibernateMultiTenancyCustomizer(DataSource dataSource) {
        return properties -> {
            properties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER,
                    new SchemaMultiTenantConnectionProvider(dataSource));
            properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER,
                    new TenantIdentifierResolver());
        };
    }

    // ─── CurrentTenantIdentifierResolver ────────────────────────────────────────

    static class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

        @Override
        public String resolveCurrentTenantIdentifier() {
            String tenant = TenantContext.getCurrentTenant();
            if (tenant != null) {
                return TenantSchemaManager.toSchemaName(tenant);
            }
            return DEFAULT_SCHEMA;
        }

        @Override
        public boolean validateExistingCurrentSessions() {
            return false;
        }
    }

    // ─── MultiTenantConnectionProvider (ALTER SESSION SET CURRENT_SCHEMA) ───────

    static class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

        private static final Logger log = LoggerFactory.getLogger(SchemaMultiTenantConnectionProvider.class);

        private final DataSource dataSource;

        SchemaMultiTenantConnectionProvider(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public Connection getAnyConnection() throws SQLException {
            return dataSource.getConnection();
        }

        @Override
        public void releaseAnyConnection(Connection connection) throws SQLException {
            resetSchema(connection);
            connection.close();
        }

        @Override
        public Connection getConnection(String tenantIdentifier) throws SQLException {
            Connection connection = getAnyConnection();
            setSchema(connection, tenantIdentifier);
            return connection;
        }

        @Override
        public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
            resetSchema(connection);
            connection.close();
        }

        @Override
        public boolean supportsAggressiveRelease() {
            return false;
        }

        @Override
        public boolean isUnwrappableAs(Class<?> unwrapType) {
            return false;
        }

        @Override
        public <T> T unwrap(Class<T> unwrapType) {
            return null;
        }

        // ── helpers ──

        private void setSchema(Connection connection, String schemaName) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER SESSION SET CURRENT_SCHEMA = \"" + schemaName + "\"");
            }
        }

        private void resetSchema(Connection connection) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER SESSION SET CURRENT_SCHEMA = \"" + DEFAULT_SCHEMA + "\"");
            } catch (SQLException e) {
                log.warn("Failed to reset schema to {}: {}", DEFAULT_SCHEMA, e.getMessage());
            }
        }
    }
}
