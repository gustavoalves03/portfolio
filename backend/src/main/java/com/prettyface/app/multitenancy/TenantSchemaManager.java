package com.prettyface.app.multitenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.regex.Pattern;

@Component
public class TenantSchemaManager {

    private static final Logger logger = LoggerFactory.getLogger(TenantSchemaManager.class);
    private static final Pattern SAFE_SCHEMA_NAME = Pattern.compile("^[A-Z][A-Z0-9_]{0,29}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DataSource dataSource;

    public TenantSchemaManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void provisionSchema(String slug) {
        String schemaName = toSchemaName(slug);
        validateSchemaName(schemaName);

        logger.info("Provisioning Oracle schema: {}", schemaName);

        String schemaPassword = generateSecurePassword();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("BEGIN " +
                    "EXECUTE IMMEDIATE 'CREATE USER \"" + schemaName + "\" IDENTIFIED BY \"" + schemaPassword + "\"'; " +
                    "EXECUTE IMMEDIATE 'GRANT CONNECT, RESOURCE, CREATE SESSION TO \"" + schemaName + "\"'; " +
                    "EXECUTE IMMEDIATE 'ALTER USER \"" + schemaName + "\" QUOTA UNLIMITED ON USERS'; " +
                    "EXCEPTION WHEN OTHERS THEN " +
                    "IF SQLCODE = -1920 THEN NULL; " +
                    "ELSE RAISE; " +
                    "END IF; " +
                    "END;");

            logger.info("Schema {} provisioned successfully", schemaName);

        } catch (SQLException e) {
            logger.error("Failed to provision schema {}: {}", schemaName, e.getMessage());
            throw new RuntimeException("Schema provisioning failed for tenant: " + slug, e);
        }
    }

    public static String toSchemaName(String slug) {
        return "TENANT_" + slug.toUpperCase().replace("-", "_");
    }

    private static void validateSchemaName(String schemaName) {
        if (!SAFE_SCHEMA_NAME.matcher(schemaName).matches()) {
            throw new IllegalArgumentException("Invalid schema name derived from slug: " + schemaName);
        }
    }

    private static String generateSecurePassword() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return "T_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
