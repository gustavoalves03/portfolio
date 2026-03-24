package com.prettyface.app.users.repo;

import com.prettyface.app.users.domain.AuthProvider;
import com.prettyface.app.users.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
    Boolean existsByEmail(String email);
    Optional<User> findByPasswordResetToken(String passwordResetToken);
}

