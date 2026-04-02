package com.prettyface.app.multitenancy;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenantSchemaManagerH2Tests {

    @Test
    void provisionSchemaCreatesTenantTablesInH2() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:tenant_schema_manager;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        TenantSchemaManager manager = new TenantSchemaManager(
                dataSource,
                "appuser",
                dataSource.getURL(),
                "",
                ""
        );

        manager.provisionSchema("salon-sophie");
        manager.provisionSchema("salon-sophie");

        assertThat(schemaExists(dataSource, "APPUSER")).isTrue();
        assertThat(schemaExists(dataSource, "TENANT_SALON_SOPHIE")).isTrue();

        for (String tableName : List.of(
                "CATEGORIES",
                "SERVICES",
                "CARE_IMAGES",
                "OPENING_HOURS",
                "BLOCKED_SLOTS",
                "CARE_BOOKINGS"
        )) {
            assertThat(tableExists(dataSource, "TENANT_SALON_SOPHIE", tableName))
                    .as("expected table %s in TENANT_SALON_SOPHIE", tableName)
                    .isTrue();
        }
    }

    private boolean schemaExists(JdbcDataSource dataSource, String schemaName) throws SQLException {
        try (var connection = dataSource.getConnection();
             ResultSet schemas = connection.getMetaData().getSchemas()) {
            while (schemas.next()) {
                if (schemaName.equals(schemas.getString("TABLE_SCHEM"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean tableExists(JdbcDataSource dataSource, String schemaName, String tableName) throws SQLException {
        try (var connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, schemaName, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}
