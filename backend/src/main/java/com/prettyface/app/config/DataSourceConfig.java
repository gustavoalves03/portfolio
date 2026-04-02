package com.prettyface.app.config;

import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.multitenancy.TenantSchemaManager;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * Configures Hibernate schema-based multi-tenancy for Oracle and H2.
 * <p>
 * A single connection pool is shared across all tenants. Before each tenant query
 * Hibernate switches the session schema; on release it resets to the shared schema.
 */
@Configuration
public class DataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    private final String applicationSchema;

    public DataSourceConfig(
            @Value("${app.multitenancy.application-schema:${APP_USER:appuser}}") String applicationSchema
    ) {
        this.applicationSchema = normalizeIdentifier(applicationSchema);
    }

    @Bean
    HibernatePropertiesCustomizer hibernateMultiTenancyCustomizer(DataSource dataSource) {
        DatabaseKind databaseKind = detectDatabaseKind(dataSource);
        initializeDefaultSchema(dataSource, databaseKind, applicationSchema);

        return properties -> {
            properties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER,
                    new SchemaMultiTenantConnectionProvider(dataSource, applicationSchema, databaseKind));
            properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER,
                    new TenantIdentifierResolver(applicationSchema));
        };
    }

    // ─── CurrentTenantIdentifierResolver ────────────────────────────────────────

    static class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

        private final String defaultSchema;

        TenantIdentifierResolver(String defaultSchema) {
            this.defaultSchema = defaultSchema;
        }

        @Override
        public String resolveCurrentTenantIdentifier() {
            String tenant = TenantContext.getCurrentTenant();
            if (tenant != null) {
                return TenantSchemaManager.toSchemaName(tenant);
            }
            return defaultSchema;
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
        private final String defaultSchema;
        private final DatabaseKind databaseKind;

        SchemaMultiTenantConnectionProvider(DataSource dataSource, String defaultSchema, DatabaseKind databaseKind) {
            this.dataSource = dataSource;
            this.defaultSchema = defaultSchema;
            this.databaseKind = databaseKind;
        }

        @Override
        public Connection getAnyConnection() throws SQLException {
            Connection connection = dataSource.getConnection();
            initializeDefaultSchema(connection);
            applySchema(connection, defaultSchema);
            return connection;
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
            initializeDefaultSchema(connection);
            applySchema(connection, schemaName);
        }

        private void resetSchema(Connection connection) {
            try {
                initializeDefaultSchema(connection);
                applySchema(connection, defaultSchema);
            } catch (SQLException e) {
                log.warn("Failed to reset schema to {}: {}", defaultSchema, e.getMessage());
            }
        }

        private void initializeDefaultSchema(Connection connection) throws SQLException {
            if (databaseKind != DatabaseKind.H2) {
                return;
            }

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + defaultSchema + "\"");
            }
        }

        private void applySchema(Connection connection, String schemaName) throws SQLException {
            switch (databaseKind) {
                case ORACLE -> {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("ALTER SESSION SET CURRENT_SCHEMA = \"" + schemaName + "\"");
                    }
                }
                case H2 -> {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("SET SCHEMA \"" + schemaName + "\"");
                    }
                }
                case OTHER -> connection.setSchema(schemaName);
            }
        }
    }

    private static void initializeDefaultSchema(DataSource dataSource, DatabaseKind databaseKind, String schemaName) {
        if (databaseKind != DatabaseKind.H2) {
            return;
        }

        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");
        } catch (SQLException e) {
            logger.warn("Failed to initialize schema {}: {}", schemaName, e.getMessage());
        }
    }

    private static DatabaseKind detectDatabaseKind(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (productName == null) {
                return DatabaseKind.OTHER;
            }

            String normalized = productName.toLowerCase(Locale.ROOT);
            if (normalized.contains("oracle")) {
                return DatabaseKind.ORACLE;
            }
            if (normalized.contains("h2")) {
                return DatabaseKind.H2;
            }
        } catch (SQLException e) {
            logger.warn("Failed to detect database platform: {}", e.getMessage());
        }

        return DatabaseKind.OTHER;
    }

    private static String normalizeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.trim().toUpperCase(Locale.ROOT);
    }

    enum DatabaseKind {
        ORACLE,
        H2,
        OTHER
    }
}
