package com.fleurdecoquillage.app.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleurdecoquillage.app.auth.dto.RegisterRequest;
import com.fleurdecoquillage.app.notification.app.EmailService;
import com.fleurdecoquillage.app.tenant.app.TenantProvisioningService;
import com.fleurdecoquillage.app.tenant.app.TenantService;
import com.fleurdecoquillage.app.users.domain.AuthProvider;
import com.fleurdecoquillage.app.users.domain.Role;
import com.fleurdecoquillage.app.users.domain.User;
import com.fleurdecoquillage.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private TenantProvisioningService tenantProvisioningService;

    @MockBean
    private EmailService emailService;

    @MockBean
    private TenantService tenantService;

    // T1.1: Happy path — returns 200 with JWT token
    @Test
    void register_happyPath_returns200WithToken() throws Exception {
        RegisterRequest request = new RegisterRequest("Sophie Martin", "sophie@salon.fr", "password123", true);

        when(userRepository.existsByEmail("sophie@salon.fr")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$encoded");

        User savedUser = User.builder()
                .id(1L)
                .name("Sophie Martin")
                .email("sophie@salon.fr")
                .password("$2a$encoded")
                .provider(AuthProvider.LOCAL)
                .role(Role.PRO)
                .emailVerified(false)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(tokenService.generateToken(1L, "sophie@salon.fr", "PRO")).thenReturn("jwt-token-abc");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token-abc"))
                .andExpect(jsonPath("$.user.email").value("sophie@salon.fr"))
                .andExpect(jsonPath("$.user.role").value("PRO"));

        verify(tenantProvisioningService, times(1)).provision(any(User.class));
        verify(emailService, times(1)).sendWelcomeEmail(any(User.class));
    }

    // T1.2: Duplicate email → 409 Conflict
    @Test
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("Sophie Martin", "existing@salon.fr", "password123", true);

        when(userRepository.existsByEmail("existing@salon.fr")).thenReturn(true);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        verify(userRepository, never()).save(any());
        verify(tenantProvisioningService, never()).provision(any());
    }

    // T1.3a: Blank name → 400 Bad Request
    @Test
    void register_blankName_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("", "sophie@salon.fr", "password123", true);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    // T1.3b: Invalid email format → 400 Bad Request
    @Test
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("Sophie", "not-an-email", "password123", true);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    // T1.3c: Password too short → 400 Bad Request
    @Test
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("Sophie", "sophie@salon.fr", "short", true);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    // T1.3d: Consent not accepted → 400 Bad Request
    @Test
    void register_consentFalse_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("Sophie", "sophie@salon.fr", "password123", false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }
}
