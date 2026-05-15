package com.luxpretty.app.auth;

import com.luxpretty.app.auth.dto.AuthResponse;
import com.luxpretty.app.auth.dto.ForgotPasswordRequest;
import com.luxpretty.app.auth.dto.LoginRequest;
import com.luxpretty.app.auth.dto.ProRegisterRequest;
import com.luxpretty.app.auth.dto.RegisterRequest;
import com.luxpretty.app.auth.dto.ResetPasswordRequest;
import com.luxpretty.app.auth.dto.UserDto;
import com.luxpretty.app.mail.app.MailOutboxService;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.vars.ResetPasswordVars;
import com.luxpretty.app.mail.vars.WelcomeProVars;
import com.luxpretty.app.subscription.app.SubscriptionService;
import com.luxpretty.app.tenant.app.TenantProvisioningService;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.AuthProvider;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final TenantProvisioningService tenantProvisioningService;
    private final TenantRepository tenantRepository;
    private final MailOutboxService mailOutbox;
    private final SubscriptionService subscriptionService;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService,
                          TenantProvisioningService tenantProvisioningService, TenantRepository tenantRepository,
                          MailOutboxService mailOutbox, SubscriptionService subscriptionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.tenantProvisioningService = tenantProvisioningService;
        this.tenantRepository = tenantRepository;
        this.mailOutbox = mailOutbox;
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return registerWithRole(request, Role.PRO, true);
    }

    @PostMapping("/register/pro")
    @Transactional
    public ResponseEntity<AuthResponse> registerPro(@Valid @RequestBody ProRegisterRequest request) {
        return registerProWithSalonInfo(request);
    }

    @PostMapping("/register/client")
    @Transactional
    public ResponseEntity<AuthResponse> registerClient(@Valid @RequestBody RegisterRequest request) {
        return registerWithRole(request, Role.USER, false);
    }

    private ResponseEntity<AuthResponse> registerWithRole(RegisterRequest request, Role role, boolean provisionTenant) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .provider(AuthProvider.LOCAL)
                .role(role)
                .emailVerified(false)
                .consentGivenAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);

        if (provisionTenant) {
            tenantProvisioningService.provision(savedUser);
        }
        mailOutbox.queue(
                MailTemplate.WELCOME_PRO,
                new WelcomeProVars(
                        savedUser.getName(),
                        savedUser.getEmail(),
                        frontendBaseUrl + "/pro/dashboard"),
                savedUser.getEmail(),
                null);

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

    private ResponseEntity<AuthResponse> registerProWithSalonInfo(ProRegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.PRO)
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .consentGivenAt(LocalDateTime.now())
                .build();
        User savedUser = userRepository.save(user);

        // Provision tenant with salon info
        var tenant = tenantProvisioningService.provision(savedUser);
        tenant.setName(request.salonName());
        tenant.setPhone(request.phone());
        tenant.setAddressStreet(request.addressStreet());
        tenant.setAddressPostalCode(request.addressPostalCode());
        tenant.setAddressCity(request.addressCity());
        tenant.setSiret(request.siret());
        tenantRepository.save(tenant);

        // Initialize Stripe customer for the tenant (idempotent, non-blocking on failure)
        try {
            subscriptionService.initializeForTenant(savedUser, tenant);
        } catch (Exception e) {
            logger.warn("Failed to initialize Stripe customer for tenant {}: {}", tenant.getSlug(), e.getMessage());
        }

        try {
            mailOutbox.queue(
                    MailTemplate.WELCOME_PRO,
                    new WelcomeProVars(
                            savedUser.getName(),
                            savedUser.getEmail(),
                            frontendBaseUrl + "/pro/dashboard"),
                    savedUser.getEmail(),
                    null);
        } catch (Exception e) {
            logger.warn("Failed to queue welcome email for {}: {}", savedUser.getEmail(), e.getMessage());
        }

        String token = tokenService.generateToken(savedUser.getId(), savedUser.getEmail(), savedUser.getRole().name());

        UserDto userDto = UserDto.builder()
                .id(savedUser.getId())
                .name(savedUser.getName())
                .email(savedUser.getEmail())
                .imageUrl(savedUser.getImageUrl())
                .provider(savedUser.getProvider())
                .role(savedUser.getRole())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token, userDto));
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

            mailOutbox.queue(
                    MailTemplate.RESET_PASSWORD,
                    new ResetPasswordVars(
                            user.getName(),
                            frontendBaseUrl + "/reset-password?token=" + token),
                    user.getEmail(),
                    null);
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
