package com.prettyface.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies lightweight Oracle schema fixes for shared tables that are not
 * reliably updated by Hibernate's ddl-auto on existing databases.
 */
@Component
public class ApplicationSchemaMigrator implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationSchemaMigrator.class);
    private static final String USERS_ROLE_CHECK = "CK_USERS_ROLE";
    private static final String CLIENT_BOOKING_HISTORY_TABLE = "CLIENT_BOOKING_HISTORY";
    private static final String CLIENT_BOOKING_HISTORY_USER_DATE_INDEX = "IDX_CBH_USER_DATE";
    private static final String CLIENT_BOOKING_HISTORY_TENANT_BOOKING_INDEX = "UK_CBH_TENANT_BOOKING";
    private static final String NOTIFICATIONS_TABLE = "NOTIFICATIONS";
    private static final String NOTIFICATIONS_RECIPIENT_INDEX = "IDX_NOTIF_RECIPIENT";

    private final DataSource dataSource;

    public ApplicationSchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!isOracle()) {
            return;
        }

        migrateUsersRoleConstraint();
        ensureClientBookingHistoryTable();
        ensureNotificationsTable();
        backfillTenantDefaults();
    }

    /**
     * Tenants created before a given column existed have NULL values for that
     * column even though the JPA entity defines a default. Hibernate's
     * ddl-auto=update adds the column but never writes default values back to
     * existing rows, so we explicitly backfill the booking-related flags here.
     * Each statement is idempotent (only updates rows that are still NULL).
     */
    private void backfillTenantDefaults() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            executeBackfill(stmt, "UPDATE TENANTS SET CLOSED_ON_HOLIDAYS = 1 WHERE CLOSED_ON_HOLIDAYS IS NULL", "closed_on_holidays");
            executeBackfill(stmt, "UPDATE TENANTS SET BUFFER_MINUTES = 0 WHERE BUFFER_MINUTES IS NULL", "buffer_minutes");
            executeBackfill(stmt, "UPDATE TENANTS SET MIN_ADVANCE_MINUTES = 120 WHERE MIN_ADVANCE_MINUTES IS NULL", "min_advance_minutes");
            executeBackfill(stmt, "UPDATE TENANTS SET MAX_ADVANCE_DAYS = 90 WHERE MAX_ADVANCE_DAYS IS NULL", "max_advance_days");
            executeBackfill(stmt, "UPDATE TENANTS SET MAX_CLIENT_HOURS_PER_DAY = 8 WHERE MAX_CLIENT_HOURS_PER_DAY IS NULL", "max_client_hours_per_day");
            executeBackfill(stmt, "UPDATE TENANTS SET ANNUAL_LEAVE_DAYS = 25 WHERE ANNUAL_LEAVE_DAYS IS NULL", "annual_leave_days");
            executeBackfill(stmt, "UPDATE TENANTS SET EMPLOYEES_ENABLED = 0 WHERE EMPLOYEES_ENABLED IS NULL", "employees_enabled");
        } catch (SQLException e) {
            // Backfill is best-effort. If TENANTS doesn't exist yet (very first
            // boot before DataInitializer runs) or a column is missing on a
            // partial deploy, log and move on.
            logger.warn("Tenant defaults backfill skipped: {}", e.getMessage());
        }
    }

    private void executeBackfill(Statement stmt, String sql, String column) {
        try {
            int updated = stmt.executeUpdate(sql);
            if (updated > 0) {
                logger.info("Backfilled {} default on {} TENANTS row(s)", column, updated);
            }
        } catch (SQLException e) {
            logger.debug("Backfill skipped for {}: {}", column, e.getMessage());
        }
    }

    private void migrateUsersRoleConstraint() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            List<String> staleConstraints = new ArrayList<>();
            boolean employeeRoleAlreadyAllowed = false;

            try (PreparedStatement ps = connection.prepareStatement("""
                    select constraint_name, search_condition_vc
                    from user_constraints
                    where table_name = 'USERS'
                      and constraint_type = 'C'
                      and search_condition_vc is not null
                    """);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String constraintName = rs.getString("constraint_name");
                    String condition = rs.getString("search_condition_vc");
                    String normalized = normalizeCondition(condition);

                    if (!normalized.startsWith("ROLEIN(")) {
                        continue;
                    }

                    if (normalized.contains("'EMPLOYEE'")) {
                        employeeRoleAlreadyAllowed = true;
                        break;
                    }

                    staleConstraints.add(constraintName);
                }
            }

            if (employeeRoleAlreadyAllowed) {
                return;
            }

            try (Statement stmt = connection.createStatement()) {
                for (String constraintName : staleConstraints) {
                    stmt.execute("ALTER TABLE USERS DROP CONSTRAINT \"" + constraintName + "\"");
                    logger.info("Dropped outdated USERS role constraint {}", constraintName);
                }

                stmt.execute("""
                        ALTER TABLE USERS ADD CONSTRAINT CK_USERS_ROLE
                        CHECK (ROLE IN ('USER', 'ADMIN', 'PRO', 'EMPLOYEE'))
                        """);
                logger.info("Created USERS role constraint {} including EMPLOYEE", USERS_ROLE_CHECK);
            }
        }
    }

    private void ensureClientBookingHistoryTable() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            createOracleObjectIfMissing(stmt, """
                    CREATE TABLE CLIENT_BOOKING_HISTORY (
                        ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        USER_ID NUMBER(19) NOT NULL,
                        TENANT_SLUG VARCHAR2(100 CHAR) NOT NULL,
                        SALON_NAME VARCHAR2(255 CHAR) NOT NULL,
                        BOOKING_ID NUMBER(19) NOT NULL,
                        CARE_NAME VARCHAR2(255 CHAR) NOT NULL,
                        CARE_PRICE NUMBER(10) NOT NULL,
                        CARE_DURATION NUMBER(10) NOT NULL,
                        APPOINTMENT_DATE DATE NOT NULL,
                        APPOINTMENT_TIME TIMESTAMP NOT NULL,
                        STATUS VARCHAR2(20 CHAR) NOT NULL,
                        CREATED_AT TIMESTAMP NOT NULL
                    )
                    """, CLIENT_BOOKING_HISTORY_TABLE);
            createOracleObjectIfMissing(
                    stmt,
                    "CREATE INDEX " + CLIENT_BOOKING_HISTORY_USER_DATE_INDEX
                            + " ON CLIENT_BOOKING_HISTORY (USER_ID, APPOINTMENT_DATE)",
                    CLIENT_BOOKING_HISTORY_USER_DATE_INDEX
            );
            createOracleObjectIfMissing(
                    stmt,
                    "CREATE UNIQUE INDEX " + CLIENT_BOOKING_HISTORY_TENANT_BOOKING_INDEX
                            + " ON CLIENT_BOOKING_HISTORY (TENANT_SLUG, BOOKING_ID)",
                    CLIENT_BOOKING_HISTORY_TENANT_BOOKING_INDEX
            );
        }
    }

    private void ensureNotificationsTable() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            createOracleObjectIfMissing(stmt, """
                    CREATE TABLE NOTIFICATIONS (
                        ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        RECIPIENT_ID NUMBER(19) NOT NULL,
                        TENANT_SLUG VARCHAR2(100 CHAR) NOT NULL,
                        TYPE VARCHAR2(50 CHAR) NOT NULL,
                        CATEGORY VARCHAR2(30 CHAR) NOT NULL,
                        TITLE VARCHAR2(255 CHAR) NOT NULL,
                        MESSAGE VARCHAR2(500 CHAR) NOT NULL,
                        REFERENCE_ID NUMBER(19) NOT NULL,
                        REFERENCE_TYPE VARCHAR2(50 CHAR) NOT NULL,
                        IS_READ NUMBER(1) DEFAULT 0 NOT NULL,
                        CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                    )
                    """, NOTIFICATIONS_TABLE);
            createOracleObjectIfMissing(
                    stmt,
                    "CREATE INDEX " + NOTIFICATIONS_RECIPIENT_INDEX
                            + " ON NOTIFICATIONS (RECIPIENT_ID, IS_READ, CREATED_AT DESC)",
                    NOTIFICATIONS_RECIPIENT_INDEX
            );
        }
    }

    private boolean isOracle() {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase(Locale.ROOT).contains("oracle");
        } catch (SQLException e) {
            logger.warn("Failed to detect database platform for schema migration: {}", e.getMessage());
            return false;
        }
    }

    private static String normalizeCondition(String condition) {
        return condition == null
                ? ""
                : condition
                .replace("\"", "")
                .replace(" ", "")
                .toUpperCase(Locale.ROOT);
    }

    private void createOracleObjectIfMissing(Statement stmt, String ddl, String objectName) throws SQLException {
        try {
            stmt.execute(ddl);
            logger.info("Created shared Oracle object {}", objectName);
        } catch (SQLException e) {
            if (e.getErrorCode() == 955 || e.getErrorCode() == 1408) {
                logger.debug("Shared Oracle object {} already exists", objectName);
                return;
            }
            throw e;
        }
    }
}
