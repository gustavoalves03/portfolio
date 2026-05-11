package com.luxpretty.app.users.repo;

import com.luxpretty.app.users.domain.AuthProvider;
import com.luxpretty.app.users.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
    Boolean existsByEmail(String email);
    Optional<User> findByPasswordResetToken(String passwordResetToken);

    @Query("SELECT u.emailBlocked FROM User u WHERE u.email = :email")
    Optional<Boolean> findEmailBlockedByEmail(@Param("email") String email);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.emailBlocked = true WHERE u.email = :email")
    int markEmailBlocked(@Param("email") String email);
}

