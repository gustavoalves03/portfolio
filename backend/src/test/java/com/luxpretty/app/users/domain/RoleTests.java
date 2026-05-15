package com.luxpretty.app.users.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTests {

    @Test
    void pro_isTenantScoped() {
        assertThat(Role.PRO.expectedScopeType()).isEqualTo(ScopeType.TENANT);
    }

    @Test
    void employee_isTenantScoped() {
        assertThat(Role.EMPLOYEE.expectedScopeType()).isEqualTo(ScopeType.TENANT);
    }

    @Test
    void commercial_isGlobalScoped() {
        assertThat(Role.COMMERCIAL.expectedScopeType()).isEqualTo(ScopeType.GLOBAL);
    }

    @Test
    void admin_isGlobalScoped() {
        assertThat(Role.ADMIN.expectedScopeType()).isEqualTo(ScopeType.GLOBAL);
    }

    @Test
    void values_containsExactlyTheFourRoles() {
        assertThat(Role.values())
                .containsExactlyInAnyOrder(Role.PRO, Role.EMPLOYEE, Role.COMMERCIAL, Role.ADMIN);
    }
}
