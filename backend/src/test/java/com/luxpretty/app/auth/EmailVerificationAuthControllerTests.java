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
}
