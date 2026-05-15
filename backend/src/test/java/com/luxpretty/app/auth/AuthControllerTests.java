package com.luxpretty.app.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.auth.dto.RegisterRequest;
import com.luxpretty.app.mail.app.MailOutboxService;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.subscription.app.SubscriptionService;
import com.luxpretty.app.tenant.app.TenantProvisioningService;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.AuthProvider;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private com.luxpretty.app.users.app.UserRoleService userRoleService;

    @MockBean
    private TenantProvisioningService tenantProvisioningService;

    @MockBean
    private MailOutboxService mailOutbox;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private TenantService tenantService;

    @MockBean
    private TenantRepository tenantRepository;

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
                .emailVerified(false)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        com.luxpretty.app.tenant.domain.Tenant tenant = new com.luxpretty.app.tenant.domain.Tenant();
        tenant.setId(42L);
        tenant.setSlug("sophie-martin");
        when(tenantProvisioningService.provision(any(User.class))).thenReturn(tenant);
        when(userRoleService.resolveRoles(1L, 42L))
                .thenReturn(java.util.Set.of(com.luxpretty.app.users.domain.Role.PRO));
        when(tokenService.generateToken(eq(1L), eq("sophie@salon.fr"), anyList(), eq(42L)))
                .thenReturn("jwt-token-abc");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token-abc"))
                .andExpect(jsonPath("$.user.email").value("sophie@salon.fr"))
                .andExpect(jsonPath("$.user.roles[0]").value("PRO"))
                .andExpect(jsonPath("$.user.activeTenantId").value(42))
                .andExpect(jsonPath("$.user.availableTenants").isArray());

        verify(tenantProvisioningService, times(1)).provision(any(User.class));
        verify(mailOutbox, times(1)).queue(eq(MailTemplate.WELCOME_PRO), any(), anyString(), isNull());
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
                .provider(AuthProvider.LOCAL).build();
        when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sophie@salon.fr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(mailOutbox).queue(eq(MailTemplate.RESET_PASSWORD), any(), anyString(), isNull());
    }

    @Test
    void forgotPassword_unknownEmail_returns200NoEmail() throws Exception {
        when(userRepository.findByEmail("unknown@salon.fr")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@salon.fr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(mailOutbox, never()).queue(any(), any(), any(), any());
    }

    @Test
    void forgotPassword_existingValidToken_skipsEmail() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
                .passwordResetToken("existing-token")
                .passwordResetTokenExpiresAt(Instant.now().plusSeconds(1800))
                .build();
        when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sophie@salon.fr\"}"))
                .andExpect(status().isOk());

        verify(mailOutbox, never()).queue(any(), any(), any(), any());
        verify(userRepository, never()).save(any());
    }

    // --- Reset Password ---

    @Test
    void resetPassword_validToken_updatesPassword() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
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

    // Lot6: forgot-password generates a UUID token with ~1h expiration and triggers email service
    @Test
    void forgotPassword_generatesUuidTokenWithOneHourExpiration() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
                .provider(AuthProvider.LOCAL).build();
        when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sophie@salon.fr\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();

        // Token must be a valid UUID — not predictable, not the email, not sequential
        assertThat(saved.getPasswordResetToken()).isNotNull();
        assertThat(java.util.UUID.fromString(saved.getPasswordResetToken())).isNotNull();

        // Expiration roughly 1 hour from now (3600s). Accept [3500s, 3700s] to absorb drift.
        Instant expires = saved.getPasswordResetTokenExpiresAt();
        assertThat(expires).isNotNull();
        long secondsAhead = java.time.Duration.between(Instant.now(), expires).getSeconds();
        assertThat(secondsAhead).isBetween(3500L, 3700L);

        // Mail outbox is called with RESET_PASSWORD template once
        verify(mailOutbox).queue(eq(MailTemplate.RESET_PASSWORD), any(), anyString(), isNull());
    }

    // Lot6: reset-password with a token whose expiration was never persisted → 400 (defensive path)
    @Test
    void resetPassword_nullExpiration_returns400() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
                .passwordResetToken("orphan-token")
                .passwordResetTokenExpiresAt(null)
                .build();
        when(userRepository.findByPasswordResetToken("orphan-token")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"orphan-token\",\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    // --- Brute Force Protection ---

    @Test
    void login_lockedAccount_returns423() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
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
                .password("$2a$encoded").provider(AuthProvider.LOCAL)
                .failedLoginAttempts(3)
                .build();
        when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "$2a$encoded")).thenReturn(true);
        when(tokenService.generateToken(eq(1L), eq("sophie@salon.fr"), anyList(), any()))
                .thenReturn("jwt-token");
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

    // Lot6: login happy path — returns 200 with JWT + user DTO (no prior failed attempts)
    @Test
    void login_happyPath_returnsTokenAndUser() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
                .password("$2a$encoded").provider(AuthProvider.LOCAL)
                .failedLoginAttempts(0)
                .build();
        when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "$2a$encoded")).thenReturn(true);
        when(userRoleService.findUserTenantIds(1L)).thenReturn(java.util.List.of(42L));
        when(userRoleService.resolveRoles(1L, 42L))
                .thenReturn(java.util.Set.of(com.luxpretty.app.users.domain.Role.PRO));
        when(tokenService.generateToken(eq(1L), eq("sophie@salon.fr"), anyList(), any()))
                .thenReturn("jwt-abc");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sophie@salon.fr\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-abc"))
                .andExpect(jsonPath("$.user.email").value("sophie@salon.fr"))
                .andExpect(jsonPath("$.user.roles[0]").value("PRO"))
                .andExpect(jsonPath("$.user.activeTenantId").value(42));

        // No state change when counters already at zero
        verify(userRepository, never()).save(any());
    }

    // Lot6: nonexistent user → 401 (NOT 404) to prevent account enumeration
    @Test
    void login_nonexistentUser_returns401NotEnumerable() throws Exception {
        when(userRepository.findByEmail("ghost@salon.fr")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@salon.fr\",\"password\":\"whatever123\"}"))
                .andExpect(status().isUnauthorized());

        // Must not leak whether the email exists by touching downstream services
        verify(passwordEncoder, never()).matches(any(), any());
        verify(tokenService, never()).generateToken(anyLong(), anyString(), anyList(), any());
        verify(userRepository, never()).save(any());
    }

    // Lot6: wrong password (first attempt, not yet locked) → 401 + attempts++
    @Test
    void login_wrongPasswordFirstAttempt_returns401AndIncrementsCounter() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
                .failedLoginAttempts(0)
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
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(1);
        // Not yet locked (threshold is 5)
        assertThat(captor.getValue().getAccountLockedUntil()).isNull();
        verify(tokenService, never()).generateToken(anyLong(), anyString(), anyList(), any());
    }

    @Test
    void login_expiredLockout_allowsLogin() throws Exception {
        User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
                .password("$2a$encoded").provider(AuthProvider.LOCAL)
                .failedLoginAttempts(5)
                .accountLockedUntil(Instant.now().minusSeconds(60))
                .build();
        when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "$2a$encoded")).thenReturn(true);
        when(tokenService.generateToken(eq(1L), eq("sophie@salon.fr"), anyList(), any()))
                .thenReturn("jwt-token");
        when(userRepository.save(any(User.class))).thenReturn(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sophie@salon.fr\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"));
    }
}
