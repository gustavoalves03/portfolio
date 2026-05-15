package com.luxpretty.app.multitenancy;

import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.users.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Backfill EMPLOYEE role assignments for users that have an Employee row in
 * any tenant schema but no matching assignment in USER_ROLE_ASSIGNMENTS yet.
 * Idempotent: re-running has no effect (UserRoleService.assignOnTenant is
 * idempotent at the DB level via UK_USER_ROLE_SCOPE).
 *
 * <p>Disabled during tests (the "test" profile uses H2 with a different
 * tenant-routing setup).
 */
@Configuration
@Profile("!test")
public class EmployeeRoleBackfillRunner {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeRoleBackfillRunner.class);

    @Bean
    ApplicationRunner backfillEmployeeRoles(
            TenantRepository tenantRepository,
            EmployeeRepository employeeRepository,
            UserRoleService userRoleService) {

        return args -> {
            int total = 0;
            for (Tenant tenant : tenantRepository.findAll()) {
                TenantContext.setCurrentTenant(tenant.getSlug());
                try {
                    for (Employee e : employeeRepository.findAll()) {
                        userRoleService.assignOnTenant(e.getUserId(), Role.EMPLOYEE, tenant.getId());
                        total++;
                    }
                } catch (Exception ex) {
                    logger.warn("Backfill failed for tenant {} ({}): {}",
                            tenant.getId(), tenant.getSlug(), ex.getMessage());
                } finally {
                    TenantContext.clear();
                }
            }
            logger.info("EmployeeRoleBackfillRunner: ensured {} EMPLOYEE assignments", total);
        };
    }
}
