package com.fleurdecoquillage.app.config;

import com.fleurdecoquillage.app.users.domain.User;
import com.fleurdecoquillage.app.users.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository) {
        return args -> {
            // Create default test user if no users exist
            if (userRepository.count() == 0) {
                User testUser = new User();
                testUser.setName("Client Test");
                testUser.setEmail("client@test.com");
                userRepository.save(testUser);
                System.out.println("✅ Created default test user: " + testUser.getEmail());
            }
        };
    }
}
