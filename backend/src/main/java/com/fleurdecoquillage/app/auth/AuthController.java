package com.fleurdecoquillage.app.auth;

import com.fleurdecoquillage.app.auth.dto.AuthResponse;
import com.fleurdecoquillage.app.auth.dto.ForgotPasswordRequest;
import com.fleurdecoquillage.app.auth.dto.LoginRequest;
import com.fleurdecoquillage.app.auth.dto.RegisterRequest;
import com.fleurdecoquillage.app.auth.dto.ResetPasswordRequest;
import com.fleurdecoquillage.app.auth.dto.UserDto;
import com.fleurdecoquillage.app.notification.app.EmailService;
import com.fleurdecoquillage.app.tenant.app.TenantProvisioningService;
import com.fleurdecoquillage.app.users.domain.AuthProvider;
import com.fleurdecoquillage.app.users.domain.Role;
import com.fleurdecoquillage.app.users.domain.User;
import com.fleurdecoquillage.app.users.repo.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final TenantProvisioningService tenantProvisioningService;
    private final EmailService emailService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService,
                          TenantProvisioningService tenantProvisioningService, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.tenantProvisioningService = tenantProvisioningService;
        this.emailService = emailService;
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .provider(AuthProvider.LOCAL)
                .role(Role.PRO)
                .emailVerified(false)
                .consentGivenAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);

        tenantProvisioningService.provision(savedUser);
        emailService.sendWelcomeEmail(savedUser);

        String token = tokenService.generateToken(savedUser.getId(), savedUser.getEmail(), savedUser.getRole().name());

        UserDto userDto = UserDto.builder()
                .id(savedUser.getId())
                .name(savedUser.getName())
                .email(savedUser.getEmail())
                .imageUrl(savedUser.getImageUrl())
                .provider(savedUser.getProvider())
                .role(savedUser.getRole())
                .build();

        return ResponseEntity.ok(new AuthResponse(token, userDto));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Check if account is locked
        if (user.getAccountLockedUntil() != null && user.getAccountLockedUntil().isAfter(Instant.now())) {
            long retryAfterSeconds = Duration.between(Instant.now(), user.getAccountLockedUntil()).getSeconds();
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(Map.of(
                            "error", "ACCOUNT_LOCKED",
                            "message", "Account temporarily locked. Try again later.",
                            "retryAfterSeconds", retryAfterSeconds
                    ));
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= 5) {
                user.setAccountLockedUntil(Instant.now().plusSeconds(900)); // 15 minutes
            }
            userRepository.save(user);
            throw new BadCredentialsException("Invalid email or password");
        }

        // Reset failed attempts on successful login
        if (user.getFailedLoginAttempts() > 0 || user.getAccountLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setAccountLockedUntil(null);
            userRepository.save(user);
        }

        String token = tokenService.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .imageUrl(user.getImageUrl())
                .provider(user.getProvider())
                .role(user.getRole())
                .build();

        return ResponseEntity.ok(new AuthResponse(token, userDto));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId())
            .orElseThrow(() -> new RuntimeException("User not found"));

        UserDto userDto = UserDto.builder()
            .id(user.getId())
            .name(user.getName())
            .email(user.getEmail())
            .imageUrl(user.getImageUrl())
            .provider(user.getProvider())
            .role(user.getRole())
            .build();

        return ResponseEntity.ok(userDto);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String message = "If an account exists with this email, you will receive a reset link.";

        userRepository.findByEmail(request.email()).ifPresent(user -> {
            // Cooldown: skip if a valid (non-expired) token already exists
            if (user.getPasswordResetToken() != null
                    && user.getPasswordResetTokenExpiresAt() != null
                    && user.getPasswordResetTokenExpiresAt().isAfter(Instant.now())) {
                return;
            }

            String token = UUID.randomUUID().toString();
            user.setPasswordResetToken(token);
            user.setPasswordResetTokenExpiresAt(Instant.now().plusSeconds(3600));
            userRepository.save(user);

            emailService.sendPasswordResetEmail(user, token);
        });

        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(request.token())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        if (user.getPasswordResetTokenExpiresAt() == null
                || user.getPasswordResetTokenExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiresAt(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }
}
