package com.luxpretty.app.auth;

import com.luxpretty.app.users.domain.AuthProvider;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmailVerificationAuthControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepo;
    @Autowired private TokenService tokenService;

    @Test
    void verifyEmail_validToken_setsEmailVerifiedTrue() throws Exception {
        String token = UUID.randomUUID().toString();
        User u = userRepo.save(User.builder()
            .name("Test").email("test-verif@example.com")
            .password("x").provider(AuthProvider.LOCAL)
            .emailVerified(false)
            .emailVerificationToken(token)
            .emailVerificationTokenExpiresAt(Instant.now().plusSeconds(3600))
            .build());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
            .andExpect(status().isOk());

        User reloaded = userRepo.findById(u.getId()).orElseThrow();
        assertThat(reloaded.getEmailVerified()).isTrue();
        assertThat(reloaded.getEmailVerificationToken()).isNull();
    }

    @Test
    void verifyEmail_invalidToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"does-not-exist\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmail_expiredToken_returns400() throws Exception {
        String token = UUID.randomUUID().toString();
        userRepo.save(User.builder()
            .name("Expired").email("expired@example.com")
            .password("x").provider(AuthProvider.LOCAL)
            .emailVerified(false)
            .emailVerificationToken(token)
            .emailVerificationTokenExpiresAt(Instant.now().minusSeconds(60))
            .build());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmail_alreadyVerified_returns200_idempotent() throws Exception {
        String token = UUID.randomUUID().toString();
        userRepo.save(User.builder()
            .name("Done").email("done@example.com")
            .password("x").provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .emailVerificationToken(token)
            .emailVerificationTokenExpiresAt(Instant.now().plusSeconds(3600))
            .build());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
            .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // send-verification
    // ------------------------------------------------------------------

    @Test
    void sendVerification_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/send-verification"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sendVerification_alreadyVerified_returns409() throws Exception {
        User u = userRepo.save(User.builder()
            .name("Verified").email("send-verif-already@example.com")
            .password("x").provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build());
        String jwt = tokenService.generateToken(u.getId(), u.getEmail(), Collections.emptyList(), null);

        mockMvc.perform(post("/api/auth/send-verification")
                .header("Authorization", "Bearer " + jwt))
            .andExpect(status().isConflict());
    }

    @Test
    void sendVerification_cooldown_returns429() throws Exception {
        String existingToken = UUID.randomUUID().toString();
        User u = userRepo.save(User.builder()
            .name("Cooldown").email("send-verif-cooldown@example.com")
            .password("x").provider(AuthProvider.LOCAL)
            .emailVerified(false)
            .emailVerificationToken(existingToken)
            // fresh token (just created): expiresAt = now + 24h
            .emailVerificationTokenExpiresAt(Instant.now().plusSeconds(3600 * 24))
            .build());
        String jwt = tokenService.generateToken(u.getId(), u.getEmail(), Collections.emptyList(), null);

        mockMvc.perform(post("/api/auth/send-verification")
                .header("Authorization", "Bearer " + jwt))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void sendVerification_freshUser_returns200_andSetsToken() throws Exception {
        User u = userRepo.save(User.builder()
            .name("Fresh").email("send-verif-fresh@example.com")
            .password("x").provider(AuthProvider.LOCAL)
            .emailVerified(false)
            .build());
        String jwt = tokenService.generateToken(u.getId(), u.getEmail(), Collections.emptyList(), null);

        mockMvc.perform(post("/api/auth/send-verification")
                .header("Authorization", "Bearer " + jwt))
            .andExpect(status().isOk());

        User reloaded = userRepo.findById(u.getId()).orElseThrow();
        assertThat(reloaded.getEmailVerificationToken()).isNotNull();
        assertThat(reloaded.getEmailVerificationTokenExpiresAt()).isAfter(Instant.now());
    }
}
