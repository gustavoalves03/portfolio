package com.fleurdecoquillage.app.config;

import com.fleurdecoquillage.app.users.domain.AuthProvider;
import com.fleurdecoquillage.app.users.domain.Role;
import com.fleurdecoquillage.app.users.domain.User;
import com.fleurdecoquillage.app.users.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class AdminSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminSeeder.class);

    private static final String ADMIN_EMAIL = "admin@prettyface.fr";
    private static final String ADMIN_PASSWORD = "Admin2026!";
    private static final String ADMIN_NAME = "Pretty Face Admin";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            logger.info("Admin account already exists: {}", ADMIN_EMAIL);
            return;
        }

        User admin = User.builder()
                .name(ADMIN_NAME)
                .email(ADMIN_EMAIL)
                .password(passwordEncoder.encode(ADMIN_PASSWORD))
                .provider(AuthProvider.LOCAL)
                .role(Role.ADMIN)
                .emailVerified(true)
                .build();

        userRepository.save(admin);
        logger.info("Admin account created: {} / {}", ADMIN_EMAIL, ADMIN_PASSWORD);
    }
}
