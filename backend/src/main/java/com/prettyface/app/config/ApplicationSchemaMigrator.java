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
}
