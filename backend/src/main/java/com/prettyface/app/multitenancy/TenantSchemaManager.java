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

/**
 * Creates and provisions Oracle schemas (users) for each tenant.
 * <p>
 * Each tenant gets its own Oracle schema containing the tenant-scoped tables
 * (CATEGORIES, SERVICES, CARE_IMAGES, OPENING_HOURS, BLOCKED_SLOTS, CARE_BOOKINGS).
 * The APPUSER schema remains the default and holds shared tables (USERS, TENANTS).
 */
@Component
public class TenantSchemaManager {

    private static final Logger logger = LoggerFactory.getLogger(TenantSchemaManager.class);
    private static final Pattern SAFE_SCHEMA_NAME = Pattern.compile("^[A-Z][A-Z0-9_]{0,29}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DataSource dataSource;

    public TenantSchemaManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Full provisioning: creates the Oracle user/schema, grants privileges to APPUSER,
     * and creates all tenant-scoped tables inside the new schema.
     */
    public void provisionSchema(String slug) {
        String schemaName = toSchemaName(slug);
        validateSchemaName(schemaName);

        logger.info("Provisioning Oracle schema: {}", schemaName);

        createSchemaUser(schemaName);
        grantCrossSchemaPrivileges();
        createTenantTables(schemaName);

        logger.info("Schema {} fully provisioned with tables", schemaName);
    }

    /**
     * Creates the Oracle user (schema) if it does not already exist.
     */
    private void createSchemaUser(String schemaName) {
        String schemaPassword = generateSecurePassword();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("BEGIN " +
                    "EXECUTE IMMEDIATE 'CREATE USER \"" + schemaName + "\" IDENTIFIED BY \"" + schemaPassword + "\"'; " +
                    "EXECUTE IMMEDIATE 'GRANT CONNECT, RESOURCE, CREATE SESSION TO \"" + schemaName + "\"'; " +
                    "EXECUTE IMMEDIATE 'ALTER USER \"" + schemaName + "\" QUOTA UNLIMITED ON USERS'; " +
                    "EXCEPTION WHEN OTHERS THEN " +
                    "IF SQLCODE = -1920 THEN NULL; " +  // ORA-01920: user already exists
                    "ELSE RAISE; " +
                    "END IF; " +
                    "END;");

            logger.info("Oracle user {} created (or already existed)", schemaName);

        } catch (SQLException e) {
            logger.error("Failed to create schema user {}: {}", schemaName, e.getMessage());
            throw new RuntimeException("Schema user creation failed for: " + schemaName, e);
        }
    }

    /**
     * Grants the APPUSER account the system privileges needed to create and
     * manipulate objects in any tenant schema. These GRANTs are idempotent.
     */
    private void grantCrossSchemaPrivileges() {
        String[] privileges = {
                "CREATE ANY TABLE",
                "ALTER ANY TABLE",
                "DROP ANY TABLE",
                "SELECT ANY TABLE",
                "INSERT ANY TABLE",
                "UPDATE ANY TABLE",
                "DELETE ANY TABLE",
                "CREATE ANY SEQUENCE",
                "SELECT ANY SEQUENCE",
                "CREATE ANY INDEX",
                "CREATE ANY SYNONYM",
                "DROP ANY SYNONYM"
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            for (String priv : privileges) {
                try {
                    stmt.execute("GRANT " + priv + " TO \"APPUSER\"");
                } catch (SQLException e) {
                    // ORA-01031: insufficient privileges (may already be granted or DBA manages it)
                    logger.debug("Grant '{}' to APPUSER: {}", priv, e.getMessage());
                }
            }

        } catch (SQLException e) {
            logger.warn("Could not grant cross-schema privileges to APPUSER: {}. " +
                    "Ensure APPUSER has the required privileges (CREATE ANY TABLE, etc.) " +
                    "or run the grants manually as SYSDBA.", e.getMessage());
        }
    }

    /**
     * Creates all tenant-scoped tables inside the given schema by using
     * {@code ALTER SESSION SET CURRENT_SCHEMA}.
     * <p>
     * The DDL matches the Hibernate entity mappings for:
     * Category, Care (SERVICES), CareImage, OpeningHour, BlockedSlot, CareBooking.
     * <p>
     * Each CREATE TABLE is wrapped to silently skip ORA-00955 (name already used).
     */
    private void createTenantTables(String schemaName) {
        // DDL statements aligned with the JPA entity definitions and Oracle dialect.
        // NUMBER(19) = Long, NUMBER(10) = Integer, NUMBER(1) = boolean
        // VARCHAR2(255 CHAR) = default String, TIMESTAMP = LocalTime/Instant, DATE = LocalDate
        String[] ddlStatements = {

                // ── CATEGORIES ──
                """
                CREATE TABLE CATEGORIES (
                    ID           NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    NAME         VARCHAR2(255 CHAR) NOT NULL,
                    DESCRIPTION  VARCHAR2(255 CHAR),
                    CONSTRAINT UK_CATEGORY_NAME UNIQUE (NAME)
                )""",

                // ── SERVICES (Care entity) ──
                """
                CREATE TABLE SERVICES (
                    ID            NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    NAME          VARCHAR2(255 CHAR) NOT NULL,
                    PRICE         NUMBER(10) NOT NULL,
                    DESCRIPTION   VARCHAR2(255 CHAR) NOT NULL,
                    STATUS        VARCHAR2(255 CHAR) NOT NULL,
                    DURATION      NUMBER(10) NOT NULL,
                    DISPLAY_ORDER NUMBER(10),
                    CATEGORY_ID   NUMBER(19) NOT NULL,
                    CONSTRAINT FK_CARE_CATEGORY FOREIGN KEY (CATEGORY_ID) REFERENCES CATEGORIES(ID)
                )""",

                // ── CARE_IMAGES ──
                """
                CREATE TABLE CARE_IMAGES (
                    ID          NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    NAME        VARCHAR2(255 CHAR) NOT NULL,
                    IMAGE_ORDER NUMBER(10) NOT NULL,
                    FILENAME    VARCHAR2(100 CHAR) NOT NULL,
                    FILE_PATH   VARCHAR2(500 CHAR) NOT NULL,
                    CARE_ID     NUMBER(19) NOT NULL,
                    CONSTRAINT FK_CARE_IMAGE_CARE FOREIGN KEY (CARE_ID) REFERENCES SERVICES(ID)
                )""",

                // ── OPENING_HOURS ──
                """
                CREATE TABLE OPENING_HOURS (
                    ID          NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    DAY_OF_WEEK NUMBER(10) NOT NULL,
                    OPEN_TIME   TIMESTAMP NOT NULL,
                    CLOSE_TIME  TIMESTAMP NOT NULL
                )""",

                // ── BLOCKED_SLOTS ──
                """
                CREATE TABLE BLOCKED_SLOTS (
                    ID         NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    SLOT_DATE  DATE NOT NULL,
                    START_TIME TIMESTAMP,
                    END_TIME   TIMESTAMP,
                    FULL_DAY   NUMBER(1) NOT NULL,
                    REASON     VARCHAR2(500 CHAR)
                )""",

                // ── CARE_BOOKINGS ──
                // NOTE: user_id references APPUSER.USERS (cross-schema) so we do NOT
                // add a FK constraint here — just store the user id as a plain number.
                """
                CREATE TABLE CARE_BOOKINGS (
                    ID               NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    USER_ID          NUMBER(19) NOT NULL,
                    CARE_ID          NUMBER(19) NOT NULL,
                    QUANTITY         NUMBER(10) NOT NULL,
                    APPOINTMENT_DATE DATE NOT NULL,
                    APPOINTMENT_TIME TIMESTAMP NOT NULL,
                    STATUS           VARCHAR2(255 CHAR) NOT NULL,
                    CREATED_AT       TIMESTAMP NOT NULL,
                    CONSTRAINT FK_BOOKING_CARE FOREIGN KEY (CARE_ID) REFERENCES SERVICES(ID),
                    CONSTRAINT UK_BOOKING_SLOT UNIQUE (APPOINTMENT_DATE, APPOINTMENT_TIME, CARE_ID)
                )"""
        };

        // Synonyms that point tenant schema references to APPUSER public tables.
        // This allows Hibernate JPA joins (e.g. CareBooking -> User) to resolve
        // cross-schema without changing entity mappings.
        String[] synonyms = {
                "CREATE OR REPLACE SYNONYM \"" + schemaName + "\".\"USERS\" FOR \"APPUSER\".\"USERS\"",
                "CREATE OR REPLACE SYNONYM \"" + schemaName + "\".\"TENANTS\" FOR \"APPUSER\".\"TENANTS\""
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Create synonyms for public tables (must be done before schema switch,
            // using fully-qualified names)
            for (String syn : synonyms) {
                try {
                    stmt.execute(syn);
                } catch (SQLException e) {
                    logger.warn("Synonym creation skipped in {}: {}", schemaName, e.getMessage());
                }
            }

            // Switch to the tenant schema
            stmt.execute("ALTER SESSION SET CURRENT_SCHEMA = \"" + schemaName + "\"");

            for (String ddl : ddlStatements) {
                try {
                    stmt.execute(ddl);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 955) {
                        // ORA-00955: name is already used by an existing object — table exists, skip
                        logger.debug("Table already exists in {}, skipping: {}", schemaName, e.getMessage());
                    } else {
                        throw e;
                    }
                }
            }

            // Reset to default schema
            stmt.execute("ALTER SESSION SET CURRENT_SCHEMA = \"APPUSER\"");

            logger.info("Tenant tables created in schema {}", schemaName);

        } catch (SQLException e) {
            logger.error("Failed to create tables in schema {}: {}", schemaName, e.getMessage());
            throw new RuntimeException("Table creation failed in schema: " + schemaName, e);
        }
    }

    /**
     * Converts a tenant slug (e.g. "sophie-martin") to an Oracle schema name
     * (e.g. "TENANT_SOPHIE_MARTIN").
     */
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
