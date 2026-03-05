package com.fleurdecoquillage.app.config;

import com.fleurdecoquillage.app.users.domain.AuthProvider;
import com.fleurdecoquillage.app.users.domain.Role;
import com.fleurdecoquillage.app.users.domain.User;
import com.fleurdecoquillage.app.users.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Create default admin user if no users exist
            if (userRepository.count() == 0) {
                // Admin user
                User adminUser = User.builder()
                        .name("Administrator")
                        .email("admin@fleurdecoquillage.com")
                        .password(passwordEncoder.encode("admin123"))
                        .provider(AuthProvider.LOCAL)
                        .emailVerified(true)
                        .role(Role.ADMIN)
                        .build();
                userRepository.save(adminUser);
                System.out.println("✅ Created admin user: " + adminUser.getEmail() + " (password: admin123)");

                // Test client user
                User testUser = User.builder()
                        .name("Client Test")
                        .email("client@test.com")
                        .password(passwordEncoder.encode("password"))
                        .provider(AuthProvider.LOCAL)
                        .emailVerified(true)
                        .role(Role.USER)
                        .build();
                userRepository.save(testUser);
                System.out.println("✅ Created test user: " + testUser.getEmail() + " (password: password)");
            }
        };
    }
}
