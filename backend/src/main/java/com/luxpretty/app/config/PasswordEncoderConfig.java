package com.luxpretty.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Standalone configuration for the {@link PasswordEncoder} bean.
 *
 * <p>Extracted out of {@link SecurityConfig} so services that need to encode
 * passwords (e.g. {@code EmployeeService}) can be wired transitively from
 * {@code CustomOAuth2UserService} without creating a circular dependency
 * (SecurityConfig -> CustomOAuth2UserService -> TenantProvisioningService
 *  -> EmployeeService -> PasswordEncoder defined in SecurityConfig).
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
