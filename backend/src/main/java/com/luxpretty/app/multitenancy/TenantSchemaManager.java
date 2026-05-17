package com.luxpretty.app.multitenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
            "HOLIDAY_EXCEPTIONS",
            "SALON_CLIENTS",
            "CLIENT_PROFILES",
            "VISIT_RECORDS",
            "VISIT_PHOTOS",
            "CLIENT_REMINDERS",
            "EMPLOYEE_PERMISSIONS",
            "BOOKING_POLICY",
            "PRO_INVOICES",
            "CLIENT_INVOICES",
            "CLIENT_INVOICE_LINES"
    );

    private final DataSource dataSource;
    private final String applicationSchemaName;
    private final String provisioningJdbcUrl;
    private final String provisioningUsername;
    private final String provisioningPassword;
    private final DatabaseKind databaseKind;
    private final TenantFlywayService tenantFlywayService;

    public TenantSchemaManager(
            DataSource dataSource,
            @Value("${app.multitenancy.application-schema:${APP_USER:appuser}}") String applicationSchemaName,
            @Value("${app.multitenancy.provisioning.jdbc-url:${spring.datasource.url}}") String provisioningJdbcUrl,
            @Value("${app.multitenancy.provisioning.username:}") String provisioningUsername,
            @Value("${app.multitenancy.provisioning.password:}") String provisioningPassword,
            TenantFlywayService tenantFlywayService
    ) {
        this.dataSource = dataSource;
        this.applicationSchemaName = normalizeOracleIdentifier(applicationSchemaName);
        this.provisioningJdbcUrl = provisioningJdbcUrl;
        this.provisioningUsername = provisioningUsername;
        this.provisioningPassword = provisioningPassword;
        this.databaseKind = detectDatabaseKind(dataSource);
        this.tenantFlywayService = tenantFlywayService;
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

        // 1. Create the Oracle user/schema with the right privileges so Flyway can connect.
        // 2. Hand off all DDL (tables, synonyms, grants) to Flyway against db/migration/tenant.
        // Existing schemas (legacy tenants provisioned before this change) keep going through
        // TenantSchemaMigrator at startup until they can be baselined on a staging DB.
        createSchemaUser(schemaName);
        tenantFlywayService.migrate(schemaName, slug);

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

    public boolean tenantSchemaExists(String slug) {
        String schemaName = toSchemaName(slug);
        validateSchemaName(schemaName);

        try {
            if (databaseKind == DatabaseKind.H2) {
                try (Connection conn = dataSource.getConnection();
                     ResultSet schemas = conn.getMetaData().getSchemas()) {
                    while (schemas.next()) {
                        if (schemaName.equalsIgnoreCase(schemas.getString("TABLE_SCHEM"))) {
                            return true;
                        }
                    }
                    return false;
                }
            }

            try (Connection conn = getProvisioningConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT COUNT(*) FROM ALL_USERS WHERE USERNAME = ?"
                 )) {
                stmt.setString(1, schemaName);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to verify tenant schema existence for: " + schemaName, e);
        }
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
                )""",
                """
                CREATE TABLE CLIENT_PROFILES (
                    ID                   NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    USER_ID              NUMBER(19) NOT NULL,
                    NOTES                VARCHAR2(2000 CHAR),
                    SKIN_TYPE            VARCHAR2(100 CHAR),
                    HAIR_TYPE            VARCHAR2(100 CHAR),
                    ALLERGIES            VARCHAR2(500 CHAR),
                    PREFERENCES          VARCHAR2(500 CHAR),
                    CONSENT_PHOTOS       NUMBER(1) NOT NULL,
                    CONSENT_PUBLIC_SHARE NUMBER(1) NOT NULL,
                    CONSENT_GIVEN_AT     TIMESTAMP,
                    CREATED_AT           TIMESTAMP NOT NULL,
                    CONSTRAINT UK_CLIENT_PROFILE_USER UNIQUE (USER_ID)
                )""",
                """
                CREATE TABLE VISIT_RECORDS (
                    ID                   NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    CLIENT_PROFILE_ID    NUMBER(19) NOT NULL,
                    BOOKING_ID           NUMBER(19),
                    CARE_ID              NUMBER(19),
                    CARE_NAME            VARCHAR2(255 CHAR),
                    VISIT_DATE           DATE NOT NULL,
                    PRACTITIONER_NOTES   VARCHAR2(2000 CHAR),
                    PRODUCTS_USED        VARCHAR2(1000 CHAR),
                    SATISFACTION_SCORE   NUMBER(10),
                    SATISFACTION_COMMENT VARCHAR2(500 CHAR),
                    CREATED_AT           TIMESTAMP NOT NULL,
                    CONSTRAINT FK_VISIT_PROFILE FOREIGN KEY (CLIENT_PROFILE_ID) REFERENCES CLIENT_PROFILES(ID)
                )""",
                """
                CREATE TABLE VISIT_PHOTOS (
                    ID              NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    VISIT_RECORD_ID NUMBER(19) NOT NULL,
                    PHOTO_TYPE      VARCHAR2(20 CHAR) NOT NULL,
                    IMAGE_PATH      VARCHAR2(500 CHAR) NOT NULL,
                    IMAGE_ORDER     NUMBER(10) NOT NULL,
                    CREATED_AT      TIMESTAMP NOT NULL,
                    CONSTRAINT FK_PHOTO_VISIT FOREIGN KEY (VISIT_RECORD_ID) REFERENCES VISIT_RECORDS(ID)
                )""",
                """
                CREATE TABLE CLIENT_REMINDERS (
                    ID               NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    USER_ID          NUMBER(19) NOT NULL,
                    CARE_ID          NUMBER(19),
                    CARE_NAME        VARCHAR2(255 CHAR),
                    RECOMMENDED_DATE DATE,
                    MESSAGE          VARCHAR2(500 CHAR),
                    SENT             NUMBER(1) NOT NULL,
                    CREATED_AT       TIMESTAMP NOT NULL
                )""",
                """
                CREATE TABLE EMPLOYEE_PERMISSIONS (
                    ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    EMPLOYEE_ID NUMBER(19) NOT NULL,
                    DOMAIN VARCHAR2(30 CHAR) NOT NULL,
                    ACCESS_LEVEL VARCHAR2(10 CHAR) NOT NULL,
                    CONSTRAINT FK_EMP_PERM_EMP FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID),
                    CONSTRAINT UQ_EMP_PERM UNIQUE (EMPLOYEE_ID, DOMAIN)
                )""",
                """
                CREATE TABLE SALON_CLIENTS (
                    ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    NAME VARCHAR2(255 CHAR) NOT NULL,
                    PHONE VARCHAR2(20 CHAR),
                    EMAIL VARCHAR2(255 CHAR),
                    DATE_OF_BIRTH DATE,
                    NOTES VARCHAR2(500 CHAR),
                    USER_ID NUMBER(19),
                    IS_MANUAL NUMBER(1) DEFAULT 1 NOT NULL,
                    CREATED_AT TIMESTAMP NOT NULL,
                    CREATED_BY NUMBER(19)
                )""",
                """
                CREATE TABLE BOOKING_POLICY (
                    ID                                   NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    MAX_BOOKINGS_PER_DAY_PER_CLIENT      NUMBER(2) NOT NULL,
                    MAX_BOOKINGS_PER_WEEK_FOR_NEW_CLIENT NUMBER(2) NOT NULL,
                    UPDATED_AT                           TIMESTAMP NOT NULL
                )""",
                // Mirrors db/migration/tenant/V3__create_invoice_tables.sql for legacy tenants
                // (Flyway V3 only runs on Flyway-baselined tenants; legacy tenants come through here).
                """
                CREATE TABLE PRO_INVOICES (
                    ID                  NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    STRIPE_INVOICE_ID   VARCHAR2(255 CHAR),
                    NUMBER_LABEL        VARCHAR2(64 CHAR) NOT NULL,
                    ISSUED_AT           TIMESTAMP NOT NULL,
                    PERIOD_START        DATE,
                    PERIOD_END          DATE,
                    AMOUNT_SUBTOTAL     NUMBER(12,2) NOT NULL,
                    AMOUNT_TAX          NUMBER(12,2) NOT NULL,
                    AMOUNT_TOTAL        NUMBER(12,2) NOT NULL,
                    CURRENCY            CHAR(3 CHAR) DEFAULT 'EUR' NOT NULL,
                    TAX_RATE            NUMBER(5,2) NOT NULL,
                    STATUS              VARCHAR2(32 CHAR) NOT NULL,
                    HOSTED_INVOICE_URL  VARCHAR2(1024 CHAR),
                    PDF_URL             VARCHAR2(1024 CHAR),
                    CUSTOMER_SNAPSHOT   CLOB,
                    CREATED_AT          TIMESTAMP NOT NULL,
                    UPDATED_AT          TIMESTAMP NOT NULL,
                    CONSTRAINT UK_PRO_INVOICES_STRIPE_ID UNIQUE (STRIPE_INVOICE_ID),
                    CONSTRAINT CK_PRO_INVOICES_STATUS CHECK (STATUS IN ('DRAFT','OPEN','PAID','UNCOLLECTIBLE','VOID'))
                )""",
                """
                CREATE TABLE CLIENT_INVOICES (
                    ID                       NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    BOOKING_ID               NUMBER(19),
                    CLIENT_USER_ID           NUMBER(19),
                    STRIPE_PAYMENT_INTENT_ID VARCHAR2(255 CHAR),
                    STRIPE_INVOICE_ID        VARCHAR2(255 CHAR),
                    NUMBER_LABEL             VARCHAR2(64 CHAR) NOT NULL,
                    ISSUED_AT                TIMESTAMP NOT NULL,
                    KIND                     VARCHAR2(32 CHAR) NOT NULL,
                    AMOUNT_SUBTOTAL          NUMBER(12,2) NOT NULL,
                    AMOUNT_TAX               NUMBER(12,2) NOT NULL,
                    AMOUNT_TOTAL             NUMBER(12,2) NOT NULL,
                    CURRENCY                 CHAR(3 CHAR) DEFAULT 'EUR' NOT NULL,
                    TAX_RATE                 NUMBER(5,2) NOT NULL,
                    STATUS                   VARCHAR2(32 CHAR) NOT NULL,
                    EMITTER_SNAPSHOT         CLOB,
                    CLIENT_SNAPSHOT          CLOB,
                    CREATED_AT               TIMESTAMP NOT NULL,
                    UPDATED_AT               TIMESTAMP NOT NULL,
                    CONSTRAINT FK_CLIENT_INVOICE_BOOKING FOREIGN KEY (BOOKING_ID) REFERENCES CARE_BOOKINGS(ID),
                    CONSTRAINT CK_CLIENT_INVOICES_KIND CHECK (KIND IN ('NO_SHOW_FEE','CARE_PAYMENT')),
                    CONSTRAINT CK_CLIENT_INVOICES_STATUS CHECK (STATUS IN ('PAID','REFUNDED','FAILED','PENDING'))
                )""",
                """
                CREATE TABLE CLIENT_INVOICE_LINES (
                    ID            NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    INVOICE_ID    NUMBER(19) NOT NULL,
                    DESCRIPTION   VARCHAR2(1024 CHAR) NOT NULL,
                    QUANTITY      NUMBER(10,2) NOT NULL,
                    UNIT_PRICE_HT NUMBER(12,2) NOT NULL,
                    TOTAL_HT      NUMBER(12,2) NOT NULL,
                    POSITION      NUMBER(10) NOT NULL,
                    CONSTRAINT FK_CLIENT_INVOICE_LINE_INV FOREIGN KEY (INVOICE_ID) REFERENCES CLIENT_INVOICES(ID) ON DELETE CASCADE
                )""",
                // Mirrors db/migration/oracle/V8__create_mail_outbox.sql for legacy tenants.
                // Without this, MailOutboxService.queue() fails with ORA-04043 and Spring
                // surfaces a misleading "Malformed request body" via the retried filter chain.
                """
                CREATE TABLE MAIL_OUTBOX (
                    ID                    NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    TEMPLATE              VARCHAR2(64 CHAR) NOT NULL,
                    RECIPIENT_EMAIL       VARCHAR2(320 CHAR) NOT NULL,
                    TENANT_SLUG           VARCHAR2(64 CHAR),
                    VARS_JSON             CLOB NOT NULL,
                    STATUS                VARCHAR2(32 CHAR) DEFAULT 'PENDING' NOT NULL,
                    ATTEMPTS              NUMBER(5) DEFAULT 0 NOT NULL,
                    NEXT_ATTEMPT_AT       TIMESTAMP NOT NULL,
                    LAST_ERROR            VARCHAR2(2000 CHAR),
                    PROVIDER_MESSAGE_ID   VARCHAR2(255 CHAR),
                    CREATED_AT            TIMESTAMP NOT NULL,
                    SENT_AT               TIMESTAMP,
                    DELIVERED_AT          TIMESTAMP,
                    CONSTRAINT CK_MAIL_OUTBOX_STATUS CHECK (STATUS IN ('PENDING','IN_FLIGHT','SENT','PERMANENTLY_FAILED'))
                )"""
        };

        // New columns on existing tables (idempotent — ORA-01430 skipped)
        String[] alterStatements = {
                "ALTER TABLE OPENING_HOURS ADD (EMPLOYEE_ID NUMBER(19))",
                "ALTER TABLE BLOCKED_SLOTS ADD (EMPLOYEE_ID NUMBER(19))",
                "ALTER TABLE CARE_BOOKINGS ADD (EMPLOYEE_ID NUMBER(19))",
                // Audit columns for tracking
                "ALTER TABLE CLIENT_PROFILES ADD (UPDATED_AT TIMESTAMP)",
                "ALTER TABLE CLIENT_PROFILES ADD (UPDATED_BY NUMBER(19))",
                "ALTER TABLE VISIT_RECORDS ADD (UPDATED_AT TIMESTAMP)",
                "ALTER TABLE VISIT_RECORDS ADD (UPDATED_BY NUMBER(19))",
                "ALTER TABLE VISIT_PHOTOS ADD (UPLOADED_BY NUMBER(19))",
                "ALTER TABLE CLIENT_REMINDERS ADD (CREATED_BY NUMBER(19))",
                "ALTER TABLE CARE_BOOKINGS ADD (SALON_CLIENT_ID NUMBER(19))",
                // J-1 booking reminder scheduler — mirror of tenant Flyway V5__booking_reminder_sent_at.sql
                "ALTER TABLE CARE_BOOKINGS ADD (REMINDER_SENT_AT TIMESTAMP)",
                // Align legacy tenant schemas with the nullable PHONE column declared in CREATE TABLE.
                "ALTER TABLE SALON_CLIENTS MODIFY (PHONE VARCHAR2(20 CHAR) NULL)",
                // @Version on LeaveRequest — added after the LEAVE_REQUESTS table existed in
                // some tenants. Without this column Hibernate fails ORA-00904 on every read,
                // surfacing as a 500 on /api/pro/leaves/pending.
                "ALTER TABLE LEAVE_REQUESTS ADD (VERSION NUMBER(19))"
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
                    if (e.getErrorCode() == 1430 || e.getErrorCode() == 1451) {
                        logger.debug("Column already exists in {}, skipping", schemaName);
                    } else {
                        logger.warn("ALTER failed in {} (error {}): {}", schemaName, e.getErrorCode(), e.getMessage());
                    }
                }
            }

            // Seed the singleton BOOKING_POLICY row if the table is empty (idempotent).
            try {
                stmt.execute("""
                        INSERT INTO BOOKING_POLICY (
                            MAX_BOOKINGS_PER_DAY_PER_CLIENT,
                            MAX_BOOKINGS_PER_WEEK_FOR_NEW_CLIENT,
                            UPDATED_AT
                        ) SELECT 1, 1, CURRENT_TIMESTAMP FROM DUAL
                          WHERE NOT EXISTS (SELECT 1 FROM BOOKING_POLICY)
                        """);
                logger.info("Seeded BOOKING_POLICY default row in {}", schemaName);
            } catch (SQLException e) {
                logger.warn("Could not seed BOOKING_POLICY in {} (error {}): {}", schemaName, e.getErrorCode(), e.getMessage());
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
                    VERSION BIGINT,
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
                )""",
                """
                CREATE TABLE IF NOT EXISTS CLIENT_PROFILES (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    USER_ID BIGINT NOT NULL,
                    NOTES VARCHAR(2000),
                    SKIN_TYPE VARCHAR(100),
                    HAIR_TYPE VARCHAR(100),
                    ALLERGIES VARCHAR(500),
                    PREFERENCES VARCHAR(500),
                    CONSENT_PHOTOS BOOLEAN NOT NULL,
                    CONSENT_PUBLIC_SHARE BOOLEAN NOT NULL,
                    CONSENT_GIVEN_AT TIMESTAMP,
                    CREATED_AT TIMESTAMP NOT NULL,
                    UPDATED_AT TIMESTAMP,
                    UPDATED_BY BIGINT,
                    CONSTRAINT UK_CLIENT_PROFILE_USER UNIQUE (USER_ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS VISIT_RECORDS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    CLIENT_PROFILE_ID BIGINT NOT NULL,
                    BOOKING_ID BIGINT,
                    CARE_ID BIGINT,
                    CARE_NAME VARCHAR(255),
                    VISIT_DATE DATE NOT NULL,
                    PRACTITIONER_NOTES VARCHAR(2000),
                    PRODUCTS_USED VARCHAR(1000),
                    SATISFACTION_SCORE INTEGER,
                    SATISFACTION_COMMENT VARCHAR(500),
                    CREATED_AT TIMESTAMP NOT NULL,
                    UPDATED_AT TIMESTAMP,
                    UPDATED_BY BIGINT,
                    CONSTRAINT FK_VISIT_PROFILE FOREIGN KEY (CLIENT_PROFILE_ID) REFERENCES CLIENT_PROFILES(ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS VISIT_PHOTOS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    VISIT_RECORD_ID BIGINT NOT NULL,
                    PHOTO_TYPE VARCHAR(20) NOT NULL,
                    IMAGE_PATH VARCHAR(500) NOT NULL,
                    IMAGE_ORDER INTEGER NOT NULL,
                    CREATED_AT TIMESTAMP NOT NULL,
                    UPLOADED_BY BIGINT,
                    CONSTRAINT FK_PHOTO_VISIT FOREIGN KEY (VISIT_RECORD_ID) REFERENCES VISIT_RECORDS(ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS CLIENT_REMINDERS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    USER_ID BIGINT NOT NULL,
                    CARE_ID BIGINT,
                    CARE_NAME VARCHAR(255),
                    RECOMMENDED_DATE DATE,
                    MESSAGE VARCHAR(500),
                    SENT BOOLEAN NOT NULL,
                    CREATED_AT TIMESTAMP NOT NULL,
                    CREATED_BY BIGINT
                )""",
                """
                CREATE TABLE IF NOT EXISTS EMPLOYEE_PERMISSIONS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    EMPLOYEE_ID BIGINT NOT NULL,
                    DOMAIN VARCHAR(30) NOT NULL,
                    ACCESS_LEVEL VARCHAR(10) NOT NULL,
                    CONSTRAINT FK_EMP_PERM_EMP FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID),
                    CONSTRAINT UQ_EMP_PERM UNIQUE (EMPLOYEE_ID, DOMAIN)
                )""",
                """
                CREATE TABLE IF NOT EXISTS SALON_CLIENTS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    NAME VARCHAR(255) NOT NULL,
                    PHONE VARCHAR(20),
                    EMAIL VARCHAR(255),
                    DATE_OF_BIRTH DATE,
                    NOTES VARCHAR(500),
                    USER_ID BIGINT,
                    IS_MANUAL BOOLEAN DEFAULT TRUE NOT NULL,
                    CREATED_AT TIMESTAMP NOT NULL,
                    CREATED_BY BIGINT
                )""",
                """
                CREATE TABLE IF NOT EXISTS BOOKING_POLICY (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    MAX_BOOKINGS_PER_DAY_PER_CLIENT INTEGER NOT NULL,
                    MAX_BOOKINGS_PER_WEEK_FOR_NEW_CLIENT INTEGER NOT NULL,
                    UPDATED_AT TIMESTAMP NOT NULL
                )""",
                """
                CREATE TABLE IF NOT EXISTS PRO_INVOICES (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    STRIPE_INVOICE_ID VARCHAR(255),
                    NUMBER_LABEL VARCHAR(64) NOT NULL,
                    ISSUED_AT TIMESTAMP NOT NULL,
                    PERIOD_START DATE,
                    PERIOD_END DATE,
                    AMOUNT_SUBTOTAL DECIMAL(12,2) NOT NULL,
                    AMOUNT_TAX DECIMAL(12,2) NOT NULL,
                    AMOUNT_TOTAL DECIMAL(12,2) NOT NULL,
                    CURRENCY CHAR(3) DEFAULT 'EUR' NOT NULL,
                    TAX_RATE DECIMAL(5,2) NOT NULL,
                    STATUS VARCHAR(32) NOT NULL,
                    HOSTED_INVOICE_URL VARCHAR(1024),
                    PDF_URL VARCHAR(1024),
                    CUSTOMER_SNAPSHOT CLOB,
                    CREATED_AT TIMESTAMP NOT NULL,
                    UPDATED_AT TIMESTAMP NOT NULL,
                    CONSTRAINT UK_PRO_INVOICES_STRIPE_ID UNIQUE (STRIPE_INVOICE_ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS CLIENT_INVOICES (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    BOOKING_ID BIGINT,
                    CLIENT_USER_ID BIGINT,
                    STRIPE_PAYMENT_INTENT_ID VARCHAR(255),
                    STRIPE_INVOICE_ID VARCHAR(255),
                    NUMBER_LABEL VARCHAR(64) NOT NULL,
                    ISSUED_AT TIMESTAMP NOT NULL,
                    KIND VARCHAR(32) NOT NULL,
                    AMOUNT_SUBTOTAL DECIMAL(12,2) NOT NULL,
                    AMOUNT_TAX DECIMAL(12,2) NOT NULL,
                    AMOUNT_TOTAL DECIMAL(12,2) NOT NULL,
                    CURRENCY CHAR(3) DEFAULT 'EUR' NOT NULL,
                    TAX_RATE DECIMAL(5,2) NOT NULL,
                    STATUS VARCHAR(32) NOT NULL,
                    EMITTER_SNAPSHOT CLOB,
                    CLIENT_SNAPSHOT CLOB,
                    CREATED_AT TIMESTAMP NOT NULL,
                    UPDATED_AT TIMESTAMP NOT NULL,
                    CONSTRAINT FK_CLIENT_INVOICE_BOOKING FOREIGN KEY (BOOKING_ID) REFERENCES CARE_BOOKINGS(ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS CLIENT_INVOICE_LINES (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    INVOICE_ID BIGINT NOT NULL,
                    DESCRIPTION VARCHAR(1024) NOT NULL,
                    QUANTITY DECIMAL(10,2) NOT NULL,
                    UNIT_PRICE_HT DECIMAL(12,2) NOT NULL,
                    TOTAL_HT DECIMAL(12,2) NOT NULL,
                    POSITION INTEGER NOT NULL,
                    CONSTRAINT FK_CLIENT_INVOICE_LINE_INV FOREIGN KEY (INVOICE_ID) REFERENCES CLIENT_INVOICES(ID) ON DELETE CASCADE
                )"""
        };

        String[] alterStatements = {
                "ALTER TABLE OPENING_HOURS ADD COLUMN IF NOT EXISTS EMPLOYEE_ID BIGINT",
                "ALTER TABLE BLOCKED_SLOTS ADD COLUMN IF NOT EXISTS EMPLOYEE_ID BIGINT",
                "ALTER TABLE CARE_BOOKINGS ADD COLUMN IF NOT EXISTS EMPLOYEE_ID BIGINT",
                // Audit columns for tracking
                "ALTER TABLE CLIENT_PROFILES ADD COLUMN IF NOT EXISTS UPDATED_AT TIMESTAMP",
                "ALTER TABLE CLIENT_PROFILES ADD COLUMN IF NOT EXISTS UPDATED_BY BIGINT",
                "ALTER TABLE VISIT_RECORDS ADD COLUMN IF NOT EXISTS UPDATED_AT TIMESTAMP",
                "ALTER TABLE VISIT_RECORDS ADD COLUMN IF NOT EXISTS UPDATED_BY BIGINT",
                "ALTER TABLE VISIT_PHOTOS ADD COLUMN IF NOT EXISTS UPLOADED_BY BIGINT",
                "ALTER TABLE CLIENT_REMINDERS ADD COLUMN IF NOT EXISTS CREATED_BY BIGINT",
                "ALTER TABLE CARE_BOOKINGS ADD COLUMN IF NOT EXISTS SALON_CLIENT_ID BIGINT",
                // J-1 booking reminder scheduler — mirror of tenant Flyway V5__booking_reminder_sent_at.sql
                "ALTER TABLE CARE_BOOKINGS ADD COLUMN IF NOT EXISTS REMINDER_SENT_AT TIMESTAMP",
                // Align legacy H2 tenant schemas with the nullable PHONE column declared in CREATE TABLE.
                "ALTER TABLE SALON_CLIENTS ALTER COLUMN PHONE DROP NOT NULL",
                // @Version on LeaveRequest (legacy schemas miss it).
                "ALTER TABLE LEAVE_REQUESTS ADD COLUMN IF NOT EXISTS VERSION BIGINT"
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

            // Seed the singleton BOOKING_POLICY row if the table is empty (idempotent).
            stmt.execute("""
                    INSERT INTO BOOKING_POLICY (
                        MAX_BOOKINGS_PER_DAY_PER_CLIENT,
                        MAX_BOOKINGS_PER_WEEK_FOR_NEW_CLIENT,
                        UPDATED_AT
                    ) SELECT 1, 1, CURRENT_TIMESTAMP
                      WHERE NOT EXISTS (SELECT 1 FROM BOOKING_POLICY)
                    """);

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
    private void dropTenantTablesH2(Statement stmt) throws SQLException {
        // Drop child tables (FK holders) BEFORE parents.
        // CLIENT_INVOICE_LINES → CLIENT_INVOICES → CARE_BOOKINGS chain.
        // PRO_INVOICES also references CARE_BOOKINGS.
        List<String> reverseTables = List.of(
                "CLIENT_INVOICE_LINES", "CLIENT_INVOICES", "PRO_INVOICES",
                "BOOKING_POLICY",
                "EMPLOYEE_PERMISSIONS", "CLIENT_REMINDERS", "VISIT_PHOTOS", "VISIT_RECORDS",
                "SALON_CLIENTS", "CLIENT_PROFILES",
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
                    CLOSE_TIME TIME NOT NULL,
                    EMPLOYEE_ID BIGINT
                )""",
                """
                CREATE TABLE IF NOT EXISTS BLOCKED_SLOTS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    SLOT_DATE DATE NOT NULL,
                    START_TIME TIME,
                    END_TIME TIME,
                    FULL_DAY BOOLEAN NOT NULL,
                    REASON VARCHAR(500),
                    EMPLOYEE_ID BIGINT
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
                    EMPLOYEE_ID BIGINT,
                    SALON_CLIENT_ID BIGINT,
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
                    VERSION BIGINT,
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
                )""",
                """
                CREATE TABLE IF NOT EXISTS CLIENT_PROFILES (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    USER_ID BIGINT NOT NULL,
                    NOTES VARCHAR(2000),
                    SKIN_TYPE VARCHAR(100),
                    HAIR_TYPE VARCHAR(100),
                    ALLERGIES VARCHAR(500),
                    PREFERENCES VARCHAR(500),
                    CONSENT_PHOTOS BOOLEAN NOT NULL,
                    CONSENT_PUBLIC_SHARE BOOLEAN NOT NULL,
                    CONSENT_GIVEN_AT TIMESTAMP,
                    CREATED_AT TIMESTAMP NOT NULL,
                    UPDATED_AT TIMESTAMP,
                    UPDATED_BY BIGINT,
                    CONSTRAINT UK_CLIENT_PROFILE_USER UNIQUE (USER_ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS VISIT_RECORDS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    CLIENT_PROFILE_ID BIGINT NOT NULL,
                    BOOKING_ID BIGINT,
                    CARE_ID BIGINT,
                    CARE_NAME VARCHAR(255),
                    VISIT_DATE DATE NOT NULL,
                    PRACTITIONER_NOTES VARCHAR(2000),
                    PRODUCTS_USED VARCHAR(1000),
                    SATISFACTION_SCORE INTEGER,
                    SATISFACTION_COMMENT VARCHAR(500),
                    CREATED_AT TIMESTAMP NOT NULL,
                    UPDATED_AT TIMESTAMP,
                    UPDATED_BY BIGINT,
                    CONSTRAINT FK_VISIT_PROFILE FOREIGN KEY (CLIENT_PROFILE_ID) REFERENCES CLIENT_PROFILES(ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS VISIT_PHOTOS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    VISIT_RECORD_ID BIGINT NOT NULL,
                    PHOTO_TYPE VARCHAR(20) NOT NULL,
                    IMAGE_PATH VARCHAR(500) NOT NULL,
                    IMAGE_ORDER INTEGER NOT NULL,
                    CREATED_AT TIMESTAMP NOT NULL,
                    UPLOADED_BY BIGINT,
                    CONSTRAINT FK_PHOTO_VISIT FOREIGN KEY (VISIT_RECORD_ID) REFERENCES VISIT_RECORDS(ID)
                )""",
                """
                CREATE TABLE IF NOT EXISTS CLIENT_REMINDERS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    USER_ID BIGINT NOT NULL,
                    CARE_ID BIGINT,
                    CARE_NAME VARCHAR(255),
                    RECOMMENDED_DATE DATE,
                    MESSAGE VARCHAR(500),
                    SENT BOOLEAN NOT NULL,
                    CREATED_AT TIMESTAMP NOT NULL,
                    CREATED_BY BIGINT
                )""",
                """
                CREATE TABLE IF NOT EXISTS EMPLOYEE_PERMISSIONS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    EMPLOYEE_ID BIGINT NOT NULL,
                    DOMAIN VARCHAR(30) NOT NULL,
                    ACCESS_LEVEL VARCHAR(10) NOT NULL,
                    CONSTRAINT FK_EMP_PERM_EMP FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID),
                    CONSTRAINT UQ_EMP_PERM UNIQUE (EMPLOYEE_ID, DOMAIN)
                )""",
                """
                CREATE TABLE IF NOT EXISTS SALON_CLIENTS (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    NAME VARCHAR(255) NOT NULL,
                    PHONE VARCHAR(20),
                    EMAIL VARCHAR(255),
                    DATE_OF_BIRTH DATE,
                    NOTES VARCHAR(500),
                    USER_ID BIGINT,
                    IS_MANUAL BOOLEAN DEFAULT TRUE NOT NULL,
                    CREATED_AT TIMESTAMP NOT NULL,
                    CREATED_BY BIGINT
                )""",
                """
                CREATE TABLE IF NOT EXISTS BOOKING_POLICY (
                    ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    MAX_BOOKINGS_PER_DAY_PER_CLIENT INTEGER NOT NULL,
                    MAX_BOOKINGS_PER_WEEK_FOR_NEW_CLIENT INTEGER NOT NULL,
                    UPDATED_AT TIMESTAMP NOT NULL
                )"""
        };

        for (String ddl : ddlStatements) {
            stmt.execute(ddl);
        }

        // Seed the singleton policy row (table was just freshly created above).
        stmt.execute("""
                INSERT INTO BOOKING_POLICY (
                    MAX_BOOKINGS_PER_DAY_PER_CLIENT,
                    MAX_BOOKINGS_PER_WEEK_FOR_NEW_CLIENT,
                    UPDATED_AT
                ) VALUES (1, 1, CURRENT_TIMESTAMP)
                """);
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
