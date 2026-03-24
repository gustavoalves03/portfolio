package com.prettyface.app.users.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTests {

    @Test
    void getFailedLoginAttempts_returnsZeroWhenBackingValueIsNull() {
        User user = User.builder()
                .failedLoginAttempts(null)
                .build();

        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void setFailedLoginAttempts_replacesNullWithZero() {
        User user = new User();

        user.setFailedLoginAttempts(null);

        assertThat(user.getFailedLoginAttempts()).isZero();
    }
}
