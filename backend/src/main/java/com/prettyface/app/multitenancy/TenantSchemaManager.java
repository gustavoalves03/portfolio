package com.prettyface.app.multitenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Creates and provisions tenant schemas for each salon.
 * <p>
 * Each tenant gets its own schema containing the tenant-scoped tables
 * (CATEGORIES, SERVICES, CARE_IMAGES, OPENING_HOURS, BLOCKED_SLOTS, CARE_BOOKINGS).
 * The application schema remains the default and holds shared tables (USERS, TENANTS).
 */
@Component
public class TenantSchemaManager {

    private static final Logger logger = LoggerFactory.getLogger(TenantSchemaManager.class);
    private static final Pattern SAFE_SCHEMA_NAME = Pattern.compile("^[A-Z][A-Z0-9_]{0,29}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final List<String> TENANT_TABLES = List.of(
            "CATEGORIES",
            "SERVICES",
            "CARE_IMAGES",
            "OPENING_HOURS",
            "BLOCKED_SLOTS",
            "CARE_BOOKINGS",
            "EMPLOYEES",
            "EMPLOYEE_CARES",
            "LEAVE_REQUESTS",
            "EMPLOYEE_DOCUMENTS",
            "POSTS",
            "POST_IMAGES",
            "HOLIDAY_EXCEPTIONS"
    );

    private final DataSource dataSource;
    private final String applicationSchemaName;
    private final String provisioningJdbcUrl;
    private final String provisioningUsername;
    private final String provisioningPassword;
    private final DatabaseKind databaseKind;

    public TenantSchemaManager(
            DataSource dataSource,
            @Value("${app.multitenancy.application-schema:${APP_USER:appuser}}") String applicationSchemaName,
            @Value("${app.multitenancy.provisioning.jdbc-url:${spring.datasource.url}}") String provisioningJdbcUrl,
            @Value("${app.multitenancy.provisioning.username:}") String provisioningUsername,
            @Value("${app.multitenancy.provisioning.password:}") String provisioningPassword
    ) {
        this.dataSource = dataSource;
        this.applicationSchemaName = normalizeOracleIdentifier(applicationSchemaName);
        this.provisioningJdbcUrl = provisioningJdbcUrl;
        this.provisioningUsername = provisioningUsername;
        this.provisioningPassword = provisioningPassword;
        this.databaseKind = detectDatabaseKind(dataSource);
    }

    /**
     * Full provisioning: creates the tenant schema/user when needed and creates all
     * tenant-scoped tables inside the new schema.
     */
    public void provisionSchema(String slug) {
        String schemaName = toSchemaName(slug);
        validateSchemaName(schemaName);

        if (databaseKind == DatabaseKind.H2) {
            logger.info("Provisioning H2 schema: {}", schemaName);
            provisionH2Schema(schemaName);
            logger.info("Schema {} fully provisioned with tables", schemaName);
            return;
        }

        logger.info("Provisioning Oracle schema: {}", schemaName);

        createSchemaUser(schemaName);
        dropTenantTables(schemaName);
        createTenantTables(schemaName);

        logger.info("Schema {} fully provisioned with tables", schemaName);
    }

    /**
     * Migrate an existing tenant schema to add new tables and columns
     * without dropping existing data. Idempotent — safe to run multiple times.
     */
    public void migrateSchema(String slug) {
        String schemaName = toSchemaName(slug);
        validateSchemaName(schemaName);

        if (databaseKind == DatabaseKind.H2) {
            migrateH2Schema(schemaName);
            return;
        }

        migrateOracleSchema(schemaName);
    }

    private void migrateOracleSchema(String schemaName) {
        // New tables to create (idempotent — ORA-00955 skipped)
        String[] newTables = {
                """
                CREATE TABLE EMPLOYEES (
                    ID         NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    USER_ID    NUMBER(19) NOT NULL,
                    NAME       VARCHAR2(255 CHAR) NOT NULL,
                    EMAIL      VARCHAR2(255 CHAR) NOT NULL,
                    PHONE      VARCHAR2(255 CHAR),
                    ACTIVE     NUMBER(1) NOT NULL,
                    CREATED_AT TIMESTAMP NOT NULL,
                    CONSTRAINT UK_EMPLOYEE_USER UNIQUE (USER_ID)
                )""",
                """
                CREATE TABLE EMPLOYEE_CARES (
                    EMPLOYEE_ID NUMBER(19) NOT NULL,
                    CARE_ID     NUMBER(19) NOT NULL,
                    CONSTRAINT FK_EC_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID),
                    CONSTRAINT FK_EC_CARE FOREIGN KEY (CARE_ID) REFERENCES SERVICES(ID),
                    CONSTRAINT PK_EMPLOYEE_CARES PRIMARY KEY (EMPLOYEE_ID, CARE_ID)
                )""",
                """
                CREATE TABLE LEAVE_REQUESTS (
                    ID            NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    EMPLOYEE_ID   NUMBER(19) NOT NULL,
                    LEAVE_TYPE    VARCHAR2(255 CHAR) NOT NULL,
                    STATUS        VARCHAR2(255 CHAR) NOT NULL,
                    START_DATE    DATE NOT NULL,
                    END_DATE      DATE NOT NULL,
                    REASON        VARCHAR2(500 CHAR),
                    DOCUMENT_PATH VARCHAR2(500 CHAR),
                    REVIEWER_NOTE VARCHAR2(500 CHAR),
                    CREATED_AT    TIMESTAMP NOT NULL,
                    REVIEWED_AT   TIMESTAMP,
                    CONSTRAINT FK_LEAVE_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID)
                )""",
                """
                CREATE TABLE EMPLOYEE_DOCUMENTS (
                    ID                  NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    EMPLOYEE_ID         NUMBER(19) NOT NULL,
                    DOC_TYPE            VARCHAR2(255 CHAR) NOT NULL,
                    TITLE               VARCHAR2(255 CHAR) NOT NULL,
                    FILENAME            VARCHAR2(255 CHAR) NOT NULL,
                    FILE_PATH           VARCHAR2(500 CHAR) NOT NULL,
                    UPLOADED_BY_USER_ID NUMBER(19) NOT NULL,
                    CREATED_AT          TIMESTAMP NOT NULL,
                    CONSTRAINT FK_DOC_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID)
                )""",
                """
                CREATE TABLE POSTS (
                    ID               NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    POST_TYPE        VARCHAR2(255 CHAR) NOT NULL,
                    CAPTION          VARCHAR2(1000 CHAR),
                    BEFORE_IMAGE_PATH VARCHAR2(500 CHAR),
                    AFTER_IMAGE_PATH  VARCHAR2(500 CHAR),
                    CARE_ID          NUMBER(19),
                    CARE_NAME        VARCHAR2(255 CHAR),
                    CREATED_AT       TIMESTAMP NOT NULL
                )""",
                """
                CREATE TABLE POST_IMAGES (
                    ID          NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    POST_ID     NUMBER(19) NOT NULL,
                    IMAGE_PATH  VARCHAR2(500 CHAR) NOT NULL,
                    IMAGE_ORDER NUMBER(10) NOT NULL,
                    CONSTRAINT FK_POST_IMAGE_POST FOREIGN KEY (POST_ID) REFERENCES POSTS(ID)
                )""",
                """
                CREATE TABLE HOLIDAY_EXCEPTIONS (
                    ID           NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    HOLIDAY_DATE DATE NOT NULL,
                    OPEN         NUMBER(1) NOT NULL
                )"""
        };

        // New columns on existing tables (idempotent — ORA-01430 skipped)
        String[] alterStatements = {
                "ALTER TABLE OPENING_HOURS ADD (EMPLOYEE_ID NUMBER(19))",
                "ALTER TABLE BLOCKED_SLOTS ADD (EMPLOYEE_ID NUMBER(19))",
                "ALTER TABLE CARE_BOOKINGS ADD (EMPLOYEE_ID NUMBER(19))"
        };

        // Use provisioning connection (Oracle admin) — app user lacks CREATE TABLE privilege
        try (Connection conn = getProvisioningConnection();
             Statement stmt = conn.createStatement()) {

            setCurrentSchema(stmt, schemaName);

            for (String ddl : newTables) {
                try {
                    stmt.execute(ddl);
                    logger.info("Created table in {}", schemaName);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 955) {
                        logger.debug("Table already exists in {}, skipping", schemaName);
                    } else {
                        logger.warn("DDL failed in {} (error {}): {}", schemaName, e.getErrorCode(), e.getMessage());
                    }
                }
            }

            for (String alter : alterStatements) {
                try {
                    stmt.execute(alter);
                    logger.info("Added column in {}", schemaName);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1430) {
                        logger.debug("Column already exists in {}, skipping", schemaName);
                    } else {
                        logger.warn("ALTER failed in {} (error {}): {}", schemaName, e.getErrorCode(), e.getMessage());
                    }
                }
            }

            setCurrentSchema(stmt, applicationSchemaName);
            grantTenantTablePrivileges(stmt, schemaName);
            logger.info("Oracle schema {} migrated successfully", schemaName);

        } catch (SQLException e) {
            logger.error("Failed to migrate Oracle schema {}: {}", schemaName, e.getMessage());
            throw new RuntimeException("Schema migration failed for: " + schemaName, e);
        }
    }

    private void migrateH2Schema(String schemaName) {
        String[] newTables = {
                """
                CREATE TABLE IF NOT EXISTS EMPLOYEES (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    USER_ID BIGINT NOT NULL,
                    NAME VARCHAR(255) NOT NULL,
                    EMAIL VARCHAR(255) NOT NULL,
                    PHONE VARCHAR(255),
                    ACTIVE BOOLEAN NOT NULL,
                    CREATED_AT TIMESTAMP NOT NULL,
                    CONSTRAINT UK_EMPLOYEE_USER UNIQUE (USER_ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS EMPLOYEE_CARES (
                    EMPLOYEE_ID BIGINT NOT NULL,
                    CARE_ID BIGINT NOT NULL,
                    CONSTRAINT FK_EC_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID),
                    CONSTRAINT FK_EC_CARE FOREIGN KEY (CARE_ID) REFERENCES SERVICES(ID),
                    CONSTRAINT PK_EMPLOYEE_CARES PRIMARY KEY (EMPLOYEE_ID, CARE_ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS LEAVE_REQUESTS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    EMPLOYEE_ID BIGINT NOT NULL,
                    LEAVE_TYPE VARCHAR(255) NOT NULL,
                    STATUS VARCHAR(255) NOT NULL,
                    START_DATE DATE NOT NULL,
                    END_DATE DATE NOT NULL,
                    REASON VARCHAR(500),
                    DOCUMENT_PATH VARCHAR(500),
                    REVIEWER_NOTE VARCHAR(500),
                    CREATED_AT TIMESTAMP NOT NULL,
                    REVIEWED_AT TIMESTAMP,
                    CONSTRAINT FK_LEAVE_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS EMPLOYEE_DOCUMENTS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    EMPLOYEE_ID BIGINT NOT NULL,
                    DOC_TYPE VARCHAR(255) NOT NULL,
                    TITLE VARCHAR(255) NOT NULL,
                    FILENAME VARCHAR(255) NOT NULL,
                    FILE_PATH VARCHAR(500) NOT NULL,
                    UPLOADED_BY_USER_ID BIGINT NOT NULL,
                    CREATED_AT TIMESTAMP NOT NULL,
                    CONSTRAINT FK_DOC_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS POSTS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    POST_TYPE VARCHAR(255) NOT NULL,
                    CAPTION VARCHAR(1000),
                    BEFORE_IMAGE_PATH VARCHAR(500),
                    AFTER_IMAGE_PATH VARCHAR(500),
                    CARE_ID BIGINT,
                    CARE_NAME VARCHAR(255),
                    CREATED_AT TIMESTAMP NOT NULL
                )""",
                """
                CREATE TABLE IF NOT EXISTS POST_IMAGES (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    POST_ID BIGINT NOT NULL,
                    IMAGE_PATH VARCHAR(500) NOT NULL,
                    IMAGE_ORDER INTEGER NOT NULL,
                    CONSTRAINT FK_POST_IMAGE_POST FOREIGN KEY (POST_ID) REFERENCES POSTS(ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS HOLIDAY_EXCEPTIONS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    HOLIDAY_DATE DATE NOT NULL,
                    OPEN BOOLEAN NOT NULL
                )"""
        };

        String[] alterStatements = {
                "ALTER TABLE OPENING_HOURS ADD COLUMN IF NOT EXISTS EMPLOYEE_ID BIGINT",
                "ALTER TABLE BLOCKED_SLOTS ADD COLUMN IF NOT EXISTS EMPLOYEE_ID BIGINT",
                "ALTER TABLE CARE_BOOKINGS ADD COLUMN IF NOT EXISTS EMPLOYEE_ID BIGINT"
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            setCurrentSchema(stmt, schemaName);

            for (String ddl : newTables) {
                stmt.execute(ddl);
            }

            for (String alter : alterStatements) {
                stmt.execute(alter);
            }

            setCurrentSchema(stmt, applicationSchemaName);
            logger.info("H2 schema {} migrated successfully", schemaName);

        } catch (SQLException e) {
            logger.error("Failed to migrate H2 schema {}: {}", schemaName, e.getMessage());
            throw new RuntimeException("Schema migration failed for: " + schemaName, e);
        }
    }

    /**
     * Creates the Oracle user (schema) if it does not already exist.
     */
    private void createSchemaUser(String schemaName) {
        String schemaPassword = generateSecurePassword();

        try (Connection conn = getProvisioningConnection();
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
            throw new RuntimeException(
                    "Schema user creation failed for: " + schemaName +
                            ". Configure app.multitenancy.provisioning.* with an Oracle admin account " +
                            "that can CREATE USER, ALTER USER, and GRANT privileges.",
                    e
            );
        }
    }

    private void provisionH2Schema(String schemaName) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + applicationSchemaName + "\"");
            stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");

            setCurrentSchema(stmt, schemaName);
            dropTenantTablesH2(stmt);
            createTenantTablesH2(stmt);
            setCurrentSchema(stmt, applicationSchemaName);

        } catch (SQLException e) {
            logger.error("Failed to create tables in schema {}: {}", schemaName, e.getMessage());
            throw new RuntimeException(
                    "Table creation failed in schema: " + schemaName +
                            ". H2 local provisioning could not initialize the tenant schema.",
                    e
            );
        }
    }

    /**
     * Drops all tenant-scoped tables in the given schema (reverse dependency order).
     * Silently skips tables that don't exist (ORA-00942).
     */
    private void dropTenantTables(String schemaName) {
        // Reverse order to respect FK dependencies
        List<String> reverseTables = List.of(
                "HOLIDAY_EXCEPTIONS",
                "POST_IMAGES", "POSTS",
                "EMPLOYEE_DOCUMENTS", "LEAVE_REQUESTS", "EMPLOYEE_CARES",
                "EMPLOYEES", "CARE_BOOKINGS", "BLOCKED_SLOTS", "OPENING_HOURS",
                "CARE_IMAGES", "SERVICES", "CATEGORIES"
        );

        try (Connection conn = getProvisioningConnection();
             Statement stmt = conn.createStatement()) {

            setCurrentSchema(stmt, schemaName);

            for (String table : reverseTables) {
                try {
                    stmt.execute("DROP TABLE \"" + table + "\" CASCADE CONSTRAINTS PURGE");
                } catch (SQLException e) {
                    if (e.getErrorCode() != 942) { // ORA-00942: table or view does not exist
                        logger.warn("Could not drop {}.{}: {}", schemaName, table, e.getMessage());
                    }
                }
            }

            setCurrentSchema(stmt, applicationSchemaName);
            logger.debug("Dropped existing tables in schema {}", schemaName);

        } catch (SQLException e) {
            logger.warn("Could not drop tables in schema {}: {}", schemaName, e.getMessage());
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
                // NOTE: user_id references the shared USERS table so we do NOT
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
                )""",

                // ── EMPLOYEES ──
                """
                CREATE TABLE EMPLOYEES (
                    ID         NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    USER_ID    NUMBER(19) NOT NULL,
                    NAME       VARCHAR2(255 CHAR) NOT NULL,
                    EMAIL      VARCHAR2(255 CHAR) NOT NULL,
                    PHONE      VARCHAR2(255 CHAR),
                    ACTIVE     NUMBER(1) NOT NULL,
                    CREATED_AT TIMESTAMP NOT NULL,
                    CONSTRAINT UK_EMPLOYEE_USER UNIQUE (USER_ID)
                )""",

                // ── EMPLOYEE_CARES (join table) ──
                """
                CREATE TABLE EMPLOYEE_CARES (
                    EMPLOYEE_ID NUMBER(19) NOT NULL,
                    CARE_ID     NUMBER(19) NOT NULL,
                    CONSTRAINT FK_EC_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID),
                    CONSTRAINT FK_EC_CARE FOREIGN KEY (CARE_ID) REFERENCES SERVICES(ID),
                    CONSTRAINT PK_EMPLOYEE_CARES PRIMARY KEY (EMPLOYEE_ID, CARE_ID)
                )""",

                // ── LEAVE_REQUESTS ──
                """
                CREATE TABLE LEAVE_REQUESTS (
                    ID            NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    EMPLOYEE_ID   NUMBER(19) NOT NULL,
                    LEAVE_TYPE    VARCHAR2(255 CHAR) NOT NULL,
                    STATUS        VARCHAR2(255 CHAR) NOT NULL,
                    START_DATE    DATE NOT NULL,
                    END_DATE      DATE NOT NULL,
                    REASON        VARCHAR2(500 CHAR),
                    DOCUMENT_PATH VARCHAR2(500 CHAR),
                    REVIEWER_NOTE VARCHAR2(500 CHAR),
                    CREATED_AT    TIMESTAMP NOT NULL,
                    REVIEWED_AT   TIMESTAMP,
                    CONSTRAINT FK_LEAVE_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID)
                )""",

                // ── EMPLOYEE_DOCUMENTS ──
                """
                CREATE TABLE EMPLOYEE_DOCUMENTS (
                    ID                  NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    EMPLOYEE_ID         NUMBER(19) NOT NULL,
                    DOC_TYPE            VARCHAR2(255 CHAR) NOT NULL,
                    TITLE               VARCHAR2(255 CHAR) NOT NULL,
                    FILENAME            VARCHAR2(255 CHAR) NOT NULL,
                    FILE_PATH           VARCHAR2(500 CHAR) NOT NULL,
                    UPLOADED_BY_USER_ID NUMBER(19) NOT NULL,
                    CREATED_AT          TIMESTAMP NOT NULL,
                    CONSTRAINT FK_DOC_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID)
                )""",

                // ── POSTS ──
                """
                CREATE TABLE POSTS (
                    ID               NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    POST_TYPE        VARCHAR2(255 CHAR) NOT NULL,
                    CAPTION          VARCHAR2(1000 CHAR),
                    BEFORE_IMAGE_PATH VARCHAR2(500 CHAR),
                    AFTER_IMAGE_PATH  VARCHAR2(500 CHAR),
                    CARE_ID          NUMBER(19),
                    CARE_NAME        VARCHAR2(255 CHAR),
                    CREATED_AT       TIMESTAMP NOT NULL
                )""",

                // ── POST_IMAGES ──
                """
                CREATE TABLE POST_IMAGES (
                    ID          NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    POST_ID     NUMBER(19) NOT NULL,
                    IMAGE_PATH  VARCHAR2(500 CHAR) NOT NULL,
                    IMAGE_ORDER NUMBER(10) NOT NULL,
                    CONSTRAINT FK_POST_IMAGE_POST FOREIGN KEY (POST_ID) REFERENCES POSTS(ID)
                )""",

                // ── HOLIDAY_EXCEPTIONS ──
                """
                CREATE TABLE HOLIDAY_EXCEPTIONS (
                    ID           NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    HOLIDAY_DATE DATE NOT NULL,
                    OPEN         NUMBER(1) NOT NULL
                )"""
        };

        // Synonyms that point tenant schema references to shared public tables.
        // This allows Hibernate JPA joins (e.g. CareBooking -> User) to resolve
        // cross-schema without changing entity mappings.
        String[] synonyms = {
                "CREATE OR REPLACE SYNONYM \"" + schemaName + "\".\"USERS\" FOR \"" + applicationSchemaName + "\".\"USERS\"",
                "CREATE OR REPLACE SYNONYM \"" + schemaName + "\".\"TENANTS\" FOR \"" + applicationSchemaName + "\".\"TENANTS\""
        };

        try (Connection conn = getProvisioningConnection();
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
            setCurrentSchema(stmt, schemaName);

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
            setCurrentSchema(stmt, applicationSchemaName);
            grantTenantTablePrivileges(stmt, schemaName);

            logger.info("Tenant tables created in schema {}", schemaName);

        } catch (SQLException e) {
            logger.error("Failed to create tables in schema {}: {}", schemaName, e.getMessage());
            throw new RuntimeException(
                    "Table creation failed in schema: " + schemaName +
                            ". The provisioning account must be able to create tenant objects and grant " +
                            "the application user access to them.",
                    e
            );
        }
    }

    private void dropTenantTablesH2(Statement stmt) throws SQLException {
        List<String> reverseTables = List.of(
                "HOLIDAY_EXCEPTIONS",
                "POST_IMAGES", "POSTS",
                "EMPLOYEE_DOCUMENTS", "LEAVE_REQUESTS", "EMPLOYEE_CARES",
                "EMPLOYEES", "CARE_BOOKINGS", "BLOCKED_SLOTS", "OPENING_HOURS",
                "CARE_IMAGES", "SERVICES", "CATEGORIES"
        );

        for (String table : reverseTables) {
            stmt.execute("DROP TABLE IF EXISTS \"" + table + "\"");
        }
    }

    private void createTenantTablesH2(Statement stmt) throws SQLException {
        String[] ddlStatements = {
                """
                CREATE TABLE IF NOT EXISTS CATEGORIES (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    NAME VARCHAR(255) NOT NULL,
                    DESCRIPTION VARCHAR(255),
                    CONSTRAINT UK_CATEGORY_NAME UNIQUE (NAME)
                )""",
                """
                CREATE TABLE IF NOT EXISTS SERVICES (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    NAME VARCHAR(255) NOT NULL,
                    PRICE INTEGER NOT NULL,
                    DESCRIPTION VARCHAR(255) NOT NULL,
                    STATUS VARCHAR(255) NOT NULL,
                    DURATION INTEGER NOT NULL,
                    DISPLAY_ORDER INTEGER,
                    CATEGORY_ID BIGINT NOT NULL,
                    CONSTRAINT FK_CARE_CATEGORY FOREIGN KEY (CATEGORY_ID) REFERENCES CATEGORIES(ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS CARE_IMAGES (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    NAME VARCHAR(255) NOT NULL,
                    IMAGE_ORDER INTEGER NOT NULL,
                    FILENAME VARCHAR(100) NOT NULL,
                    FILE_PATH VARCHAR(500) NOT NULL,
                    CARE_ID BIGINT NOT NULL,
                    CONSTRAINT FK_CARE_IMAGE_CARE FOREIGN KEY (CARE_ID) REFERENCES SERVICES(ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS OPENING_HOURS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    DAY_OF_WEEK INTEGER NOT NULL,
                    OPEN_TIME TIME NOT NULL,
                    CLOSE_TIME TIME NOT NULL
                )""",
                """
                CREATE TABLE IF NOT EXISTS BLOCKED_SLOTS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    SLOT_DATE DATE NOT NULL,
                    START_TIME TIME,
                    END_TIME TIME,
                    FULL_DAY BOOLEAN NOT NULL,
                    REASON VARCHAR(500)
                )""",
                """
                CREATE TABLE IF NOT EXISTS CARE_BOOKINGS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    USER_ID BIGINT NOT NULL,
                    CARE_ID BIGINT NOT NULL,
                    QUANTITY INTEGER NOT NULL,
                    APPOINTMENT_DATE DATE NOT NULL,
                    APPOINTMENT_TIME TIME NOT NULL,
                    STATUS VARCHAR(255) NOT NULL,
                    CREATED_AT TIMESTAMP NOT NULL,
                    CONSTRAINT FK_BOOKING_CARE FOREIGN KEY (CARE_ID) REFERENCES SERVICES(ID),
                    CONSTRAINT UK_BOOKING_SLOT UNIQUE (APPOINTMENT_DATE, APPOINTMENT_TIME, CARE_ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS EMPLOYEES (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    USER_ID BIGINT NOT NULL,
                    NAME VARCHAR(255) NOT NULL,
                    EMAIL VARCHAR(255) NOT NULL,
                    PHONE VARCHAR(255),
                    ACTIVE BOOLEAN NOT NULL,
                    CREATED_AT TIMESTAMP NOT NULL,
                    CONSTRAINT UK_EMPLOYEE_USER UNIQUE (USER_ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS EMPLOYEE_CARES (
                    EMPLOYEE_ID BIGINT NOT NULL,
                    CARE_ID BIGINT NOT NULL,
                    CONSTRAINT FK_EC_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID),
                    CONSTRAINT FK_EC_CARE FOREIGN KEY (CARE_ID) REFERENCES SERVICES(ID),
                    CONSTRAINT PK_EMPLOYEE_CARES PRIMARY KEY (EMPLOYEE_ID, CARE_ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS LEAVE_REQUESTS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    EMPLOYEE_ID BIGINT NOT NULL,
                    LEAVE_TYPE VARCHAR(255) NOT NULL,
                    STATUS VARCHAR(255) NOT NULL,
                    START_DATE DATE NOT NULL,
                    END_DATE DATE NOT NULL,
                    REASON VARCHAR(500),
                    DOCUMENT_PATH VARCHAR(500),
                    REVIEWER_NOTE VARCHAR(500),
                    CREATED_AT TIMESTAMP NOT NULL,
                    REVIEWED_AT TIMESTAMP,
                    CONSTRAINT FK_LEAVE_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS EMPLOYEE_DOCUMENTS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    EMPLOYEE_ID BIGINT NOT NULL,
                    DOC_TYPE VARCHAR(255) NOT NULL,
                    TITLE VARCHAR(255) NOT NULL,
                    FILENAME VARCHAR(255) NOT NULL,
                    FILE_PATH VARCHAR(500) NOT NULL,
                    UPLOADED_BY_USER_ID BIGINT NOT NULL,
                    CREATED_AT TIMESTAMP NOT NULL,
                    CONSTRAINT FK_DOC_EMPLOYEE FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS POSTS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    POST_TYPE VARCHAR(255) NOT NULL,
                    CAPTION VARCHAR(1000),
                    BEFORE_IMAGE_PATH VARCHAR(500),
                    AFTER_IMAGE_PATH VARCHAR(500),
                    CARE_ID BIGINT,
                    CARE_NAME VARCHAR(255),
                    CREATED_AT TIMESTAMP NOT NULL
                )""",
                """
                CREATE TABLE IF NOT EXISTS POST_IMAGES (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    POST_ID BIGINT NOT NULL,
                    IMAGE_PATH VARCHAR(500) NOT NULL,
                    IMAGE_ORDER INTEGER NOT NULL,
                    CONSTRAINT FK_POST_IMAGE_POST FOREIGN KEY (POST_ID) REFERENCES POSTS(ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS HOLIDAY_EXCEPTIONS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    HOLIDAY_DATE DATE NOT NULL,
                    OPEN BOOLEAN NOT NULL
                )"""
        };

        for (String ddl : ddlStatements) {
            stmt.execute(ddl);
        }
    }

    private void setCurrentSchema(Statement stmt, String schemaName) throws SQLException {
        if (databaseKind == DatabaseKind.H2) {
            stmt.execute("SET SCHEMA \"" + schemaName + "\"");
            return;
        }

        stmt.execute("ALTER SESSION SET CURRENT_SCHEMA = \"" + schemaName + "\"");
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

    private void grantTenantTablePrivileges(Statement stmt, String schemaName) throws SQLException {
        for (String tableName : TENANT_TABLES) {
            stmt.execute(
                    "GRANT SELECT, INSERT, UPDATE, DELETE ON \"" + schemaName + "\".\"" + tableName +
                            "\" TO \"" + applicationSchemaName + "\""
            );
        }
    }

    private Connection getProvisioningConnection() throws SQLException {
        if (StringUtils.hasText(provisioningUsername) && StringUtils.hasText(provisioningPassword)) {
            return DriverManager.getConnection(provisioningJdbcUrl, provisioningUsername, provisioningPassword);
        }

        logger.warn(
                "No dedicated Oracle provisioning account configured; falling back to the main datasource user {}",
                applicationSchemaName
        );
        return dataSource.getConnection();
    }

    private static String generateSecurePassword() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return "T_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static DatabaseKind detectDatabaseKind(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (productName == null) {
                return DatabaseKind.ORACLE;
            }

            String normalized = productName.toLowerCase(Locale.ROOT);
            if (normalized.contains("h2")) {
                return DatabaseKind.H2;
            }
        } catch (SQLException e) {
            logger.warn("Failed to detect database platform for tenant provisioning: {}", e.getMessage());
        }

        return DatabaseKind.ORACLE;
    }

    private static String normalizeOracleIdentifier(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim().toUpperCase(Locale.ROOT);
        validateSchemaName(normalized);
        return normalized;
    }

    private enum DatabaseKind {
        ORACLE,
        H2
    }
}
