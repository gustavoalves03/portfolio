package com.luxpretty.app.auth;

import com.luxpretty.app.auth.dto.AuthResponse;
import com.luxpretty.app.auth.dto.ForgotPasswordRequest;
import com.luxpretty.app.auth.dto.LoginRequest;
import com.luxpretty.app.auth.dto.ProRegisterRequest;
import com.luxpretty.app.auth.dto.ProUpgradeRequest;
import com.luxpretty.app.auth.dto.RegisterRequest;
import com.luxpretty.app.auth.dto.ResetPasswordRequest;
import com.luxpretty.app.auth.dto.UserDto;
import com.luxpretty.app.auth.dto.VerifyEmailRequest;
import com.luxpretty.app.mail.app.MailOutboxService;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.vars.ResetPasswordVars;
import com.luxpretty.app.mail.vars.VerifyEmailVars;
import com.luxpretty.app.mail.vars.WelcomeProVars;
import com.luxpretty.app.subscription.app.SubscriptionService;
import com.luxpretty.app.tenant.app.TenantProvisioningService;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.app.UserRoleService;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final UserRoleService userRoleService;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService,
                          TenantProvisioningService tenantProvisioningService, TenantRepository tenantRepository,
                          MailOutboxService mailOutbox, SubscriptionService subscriptionService,
                          UserRoleService userRoleService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.tenantProvisioningService = tenantProvisioningService;
        this.tenantRepository = tenantRepository;
        this.mailOutbox = mailOutbox;
        this.subscriptionService = subscriptionService;
        this.userRoleService = userRoleService;
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return registerWithRole(request, true);
    }

    @PostMapping("/register/pro")
    @Transactional
    public ResponseEntity<AuthResponse> registerPro(@Valid @RequestBody ProRegisterRequest request) {
        return registerProWithSalonInfo(request);
    }

    @PostMapping("/register/client")
    @Transactional
    public ResponseEntity<AuthResponse> registerClient(@Valid @RequestBody RegisterRequest request) {
        return registerWithRole(request, false);
    }

    @PostMapping("/upgrade-to-pro")
    @Transactional
    public ResponseEntity<AuthResponse> upgradeToPro(
            @Valid @RequestBody ProUpgradeRequest request
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        // Reject if user already has any tenant (conservative — covers any pro role)
        boolean alreadyPro = !userRoleService.findUserTenantIds(user.getId()).isEmpty();
        if (alreadyPro) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already has a tenant");
        }

        var tenant = tenantProvisioningService.provision(user);
        tenant.setName(user.getName());
        tenant.setSubscriptionTier(request.tier());
        tenant.setSubscriptionBilling(request.billing());
        tenantRepository.save(tenant);

        try {
            subscriptionService.initializeForTenant(user, tenant);
        } catch (Exception e) {
            logger.warn("Failed to initialize Stripe customer for upgraded user {}: {}", user.getEmail(), e.getMessage());
        }

        return ResponseEntity.ok(buildAuthResponse(user, tenant.getId()));
    }

    private ResponseEntity<AuthResponse> registerWithRole(RegisterRequest request, boolean provisionTenant) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .consentGivenAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);

        Long activeTenantId = null;
        if (provisionTenant) {
            var tenant = tenantProvisioningService.provision(savedUser);
            activeTenantId = tenant.getId();
        }
        mailOutbox.queue(
                MailTemplate.WELCOME_PRO,
                new WelcomeProVars(
                        savedUser.getName(),
                        savedUser.getEmail(),
                        frontendBaseUrl + "/pro/dashboard"),
                savedUser.getEmail(),
                null);

        // LOCAL register: queue email verification mail.
        // OAuth flows set emailVerified=true and do NOT reach this path.
        queueVerificationMail(savedUser);

        AuthResponse response = buildAuthResponse(savedUser, activeTenantId);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<AuthResponse> registerProWithSalonInfo(ProRegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .consentGivenAt(LocalDateTime.now())
                .build();
        User savedUser = userRepository.save(user);

        // Provision tenant with salon info
        var tenant = tenantProvisioningService.provision(savedUser);
        String salonName = (request.salonName() != null && !request.salonName().isBlank())
            ? request.salonName()
            : request.name();
        tenant.setName(salonName);
        tenant.setPhone(request.phone());
        tenant.setAddressStreet(request.addressStreet());
        tenant.setAddressPostalCode(request.addressPostalCode());
        tenant.setAddressCity(request.addressCity());
        tenant.setSiret(request.siret());
        tenant.setSubscriptionTier(request.tier());
        tenant.setSubscriptionBilling(request.billing());
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

        // LOCAL pro register: queue email verification mail.
        // OAuth flows set emailVerified=true and do NOT reach this path.
        try {
            queueVerificationMail(savedUser);
        } catch (Exception e) {
            logger.warn("Failed to queue verification email for {}: {}", savedUser.getEmail(), e.getMessage());
        }

        AuthResponse response = buildAuthResponse(savedUser, tenant.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

        Long activeTenantId = userRoleService.findUserTenantIds(user.getId())
                .stream().findFirst().orElse(null);
        return ResponseEntity.ok(buildAuthResponse(user, activeTenantId));
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

        Long activeTenantId = userRoleService.findUserTenantIds(user.getId())
                .stream().findFirst().orElse(null);
        return ResponseEntity.ok(buildUserDto(user, activeTenantId));
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

    @PostMapping("/verify-email")
    @Transactional
    public ResponseEntity<Map<String, String>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        User user = userRepository.findByEmailVerificationToken(request.token())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return ResponseEntity.ok(Map.of("message", "already verified"));
        }

        if (user.getEmailVerificationTokenExpiresAt() == null
                || user.getEmailVerificationTokenExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiresAt(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Email verified"));
    }

    @PostMapping("/send-verification")
    @Transactional
    public ResponseEntity<?> sendVerification(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "ALREADY_VERIFIED"));
        }

        // Cooldown: skip if the last token was created less than 1 minute ago.
        // A freshly issued token has expiresAt = now + 24h, so if it is still
        // greater than (now + 24h - 1min) then it was issued < 1 min ago.
        if (user.getEmailVerificationToken() != null
                && user.getEmailVerificationTokenExpiresAt() != null
                && user.getEmailVerificationTokenExpiresAt().isAfter(Instant.now().plusSeconds(3600 * 24 - 60))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "COOLDOWN", "retryAfter", 60));
        }

        queueVerificationMail(user);
        return ResponseEntity.ok(Map.of("message", "Verification email sent"));
    }

    private void queueVerificationMail(User user) {
        String token = UUID.randomUUID().toString();
        user.setEmailVerificationToken(token);
        user.setEmailVerificationTokenExpiresAt(Instant.now().plusSeconds(3600L * 24));
        userRepository.save(user);

        mailOutbox.queue(
                MailTemplate.VERIFY_EMAIL,
                new VerifyEmailVars(user.getName(), frontendBaseUrl + "/verify-email?token=" + token),
                user.getEmail(),
                null);
    }

    // -----------------------------------------------------------------------
    // Helpers: token + UserDto assembly from scoped role assignments
    // -----------------------------------------------------------------------

    private AuthResponse buildAuthResponse(User user, Long activeTenantId) {
        UserDto dto = buildUserDto(user, activeTenantId);
        String token = tokenService.generateToken(user.getId(), user.getEmail(),
                dto.getRoles(), activeTenantId);
        return new AuthResponse(token, dto);
    }

    private UserDto buildUserDto(User user, Long activeTenantId) {
        Set<Role> resolved = userRoleService.resolveRoles(user.getId(), activeTenantId);
        List<String> roleNames = resolved.stream().map(Enum::name).toList();
        List<com.luxpretty.app.me.web.dto.TenantSummary> tenants =
                userRoleService.findUserTenantIds(user.getId()).stream()
                        .map(tenantRepository::findById)
                        .flatMap(java.util.Optional::stream)
                        .map(t -> new com.luxpretty.app.me.web.dto.TenantSummary(
                                t.getId(), t.getSlug(), t.getName()))
                        .toList();
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .imageUrl(user.getImageUrl())
                .provider(user.getProvider())
                .roles(roleNames)
                .activeTenantId(activeTenantId)
                .availableTenants(tenants)
                .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
                .build();
    }
}
