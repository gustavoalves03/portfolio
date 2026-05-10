package com.luxpretty.app.auth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.auth.dto.LoginRequest;
import com.luxpretty.app.auth.dto.RegisterRequest;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the /api/auth flow: register, login, and
 * the JWT-protected /api/auth/me endpoint. Exercises the real Security
 * filter chain (JwtAuthenticationFilter + TenantFilter) against H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private static final String EMAIL = "flow-user@test.com";
    private static final String PASSWORD = "integration-pwd-123";

    @BeforeEach
    @Transactional
    void cleanSlate() {
        // /register provisions a tenant for PRO role. We use the /register/client
        // path below (no tenant) to keep the test side-effect footprint small —
        // just wipe the user between runs.
        userRepository.findByEmail(EMAIL).ifPresent(u -> userRepository.deleteById(u.getId()));
    }

    @Test
    @DisplayName("register → login → /me roundtrip returns the same user")
    void fullAuthFlow_returnsSameUser() throws Exception {
        // 1. Register a client (client registration path does not provision a tenant,
        //    which keeps the test surface minimal and avoids cross-schema concerns).
        RegisterRequest registerPayload = new RegisterRequest(
                "Flow User",
                EMAIL,
                PASSWORD,
                true
        );

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andReturn();

        JsonNode registerBody = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String registerToken = registerBody.get("accessToken").asText();
        assertThat(registerToken).isNotBlank();

        // 2. Login with the same credentials
        LoginRequest loginPayload = new LoginRequest(EMAIL, PASSWORD);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String loginToken = loginBody.get("accessToken").asText();
        assertThat(loginToken).isNotBlank();

        // 3. /me with a valid Bearer token returns the user DTO
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.name").value("Flow User"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("/me without any Authorization header returns 401")
    void meEndpoint_noToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/me with a malformed Bearer token returns 401")
    void meEndpoint_invalidToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login with wrong password is rejected")
    void login_wrongPassword_returns401() throws Exception {
        // Seed a user first via register
        RegisterRequest registerPayload = new RegisterRequest(
                "Flow User",
                EMAIL,
                PASSWORD,
                true
        );
        mockMvc.perform(post("/api/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerPayload)))
                .andExpect(status().isOk());

        LoginRequest wrongPayload = new LoginRequest(EMAIL, "wrong-password-xyz");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPayload)))
                .andExpect(status().isUnauthorized());
    }
}
