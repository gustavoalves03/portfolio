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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    // --- Forgot Password ---

    @Test
    void forgotPassword_existingEmail_returns200() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
                .provider(AuthProvider.LOCAL).role(Role.PRO).build();
        when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sophie@salon.fr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(emailService).sendPasswordResetEmail(any(User.class), anyString());
    }

    @Test
    void forgotPassword_unknownEmail_returns200NoEmail() throws Exception {
        when(userRepository.findByEmail("unknown@salon.fr")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@salon.fr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(emailService, never()).sendPasswordResetEmail(any(), anyString());
    }

    @Test
    void forgotPassword_existingValidToken_skipsEmail() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
                .provider(AuthProvider.LOCAL).role(Role.PRO)
                .passwordResetToken("existing-token")
                .passwordResetTokenExpiresAt(Instant.now().plusSeconds(1800))
                .build();
        when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sophie@salon.fr\"}"))
                .andExpect(status().isOk());

        verify(emailService, never()).sendPasswordResetEmail(any(), anyString());
        verify(userRepository, never()).save(any());
    }

    // --- Reset Password ---

    @Test
    void resetPassword_validToken_updatesPassword() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
                .provider(AuthProvider.LOCAL).role(Role.PRO)
                .passwordResetToken("valid-token")
                .passwordResetTokenExpiresAt(Instant.now().plusSeconds(1800))
                .build();
        when(userRepository.findByPasswordResetToken("valid-token")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpassword123")).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenReturn(user);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"valid-token\",\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$encoded");
        assertThat(captor.getValue().getPasswordResetToken()).isNull();
        assertThat(captor.getValue().getPasswordResetTokenExpiresAt()).isNull();
    }

    @Test
    void resetPassword_expiredToken_returns400() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
                .provider(AuthProvider.LOCAL).role(Role.PRO)
                .passwordResetToken("expired-token")
                .passwordResetTokenExpiresAt(Instant.now().minusSeconds(60))
                .build();
        when(userRepository.findByPasswordResetToken("expired-token")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"expired-token\",\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_invalidToken_returns400() throws Exception {
        when(userRepository.findByPasswordResetToken("bogus")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"bogus\",\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- Brute Force Protection ---

    @Test
    void login_lockedAccount_returns423() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
                .password("$2a$encoded").provider(AuthProvider.LOCAL).role(Role.PRO)
                .failedLoginAttempts(5)
                .accountLockedUntil(Instant.now().plusSeconds(900))
                .build();
        when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sophie@salon.fr\",\"password\":\"password123\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_fifthFailedAttempt_locksAccount() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
                .password("$2a$encoded").provider(AuthProvider.LOCAL).role(Role.PRO)
                .failedLoginAttempts(4)
                .build();
        when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$encoded")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sophie@salon.fr\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(5);
        assertThat(captor.getValue().getAccountLockedUntil()).isNotNull();
    }

    @Test
    void login_successResetsFailedAttempts() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
                .password("$2a$encoded").provider(AuthProvider.LOCAL).role(Role.PRO)
                .failedLoginAttempts(3)
                .build();
        when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "$2a$encoded")).thenReturn(true);
        when(tokenService.generateToken(1L, "sophie@salon.fr", "PRO")).thenReturn("jwt-token");
        when(userRepository.save(any(User.class))).thenReturn(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sophie@salon.fr\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(0);
        assertThat(captor.getValue().getAccountLockedUntil()).isNull();
    }

    @Test
    void login_expiredLockout_allowsLogin() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
                .password("$2a$encoded").provider(AuthProvider.LOCAL).role(Role.PRO)
                .failedLoginAttempts(5)
                .accountLockedUntil(Instant.now().minusSeconds(60))
                .build();
        when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "$2a$encoded")).thenReturn(true);
        when(tokenService.generateToken(1L, "sophie@salon.fr", "PRO")).thenReturn("jwt-token");
        when(userRepository.save(any(User.class))).thenReturn(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sophie@salon.fr\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"));
    }
}
