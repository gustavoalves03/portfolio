package com.prettyface.app.users.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RoleTests {
    @Test
    void employee_role_exists() {
        assertThat(Role.valueOf("EMPLOYEE")).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void all_roles_present() {
        assertThat(Role.values()).containsExactly(Role.USER, Role.ADMIN, Role.PRO, Role.EMPLOYEE);
    }
}
