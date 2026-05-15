package com.luxpretty.app.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TokenService}.
 * Lot 6 — JWT generation / validation / expiration paths.
 */
class TokenServiceTests {

    private static final String TEST_SECRET =
            "test-secret-key-for-token-service-unit-tests-min-32-bytes-ok";
    private static final long ONE_DAY_MS = 86_400_000L;

    private TokenService tokenService;
    private com.luxpretty.app.users.app.UserRoleService userRoleService;
    private com.luxpretty.app.tenant.repo.TenantRepository tenantRepository;

    @BeforeEach
    void setUp() {
        userRoleService = org.mockito.Mockito.mock(com.luxpretty.app.users.app.UserRoleService.class);
        tenantRepository = org.mockito.Mockito.mock(com.luxpretty.app.tenant.repo.TenantRepository.class);
        // Default: empty tenants for the User-overload tests. Specific tests stub
        // findUserTenantIds + resolveRoles as needed.
        org.mockito.Mockito.lenient().when(userRoleService.findUserTenantIds(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(java.util.List.of());
        tokenService = new TokenService(userRoleService, tenantRepository);
        ReflectionTestUtils.setField(tokenService, "tokenSecret", TEST_SECRET);
        ReflectionTestUtils.setField(tokenService, "tokenExpirationMs", ONE_DAY_MS);
    }

    // Lot6: happy path — token generated, signed, and validates successfully
    @Test
    void generateAndValidate_validToken_returnsTrue() {
        String token = tokenService.generateToken(42L);

        assertThat(token).isNotBlank();
        assertThat(tokenService.validateToken(token)).isTrue();
        assertThat(tokenService.getUserIdFromToken(token)).isEqualTo(42L);
    }

    // Lot6: expired token → validateToken returns false (catches ExpiredJwtException)
    @Test
    void validateToken_expiredToken_returnsFalse() {
        // Build a JWT that expired 60s ago, signed with the same key
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Date issuedAt = new Date(System.currentTimeMillis() - 120_000L);
        Date expiresAt = new Date(System.currentTimeMillis() - 60_000L);

        String expiredToken = Jwts.builder()
                .subject("7")
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(key)
                .compact();

        assertThat(tokenService.validateToken(expiredToken)).isFalse();
    }

    // Lot6: malformed token → validateToken returns false (catches MalformedJwtException)
    @Test
    void validateToken_malformedToken_returnsFalse() {
        assertThat(tokenService.validateToken("not.a.valid.jwt")).isFalse();
        assertThat(tokenService.validateToken("garbage")).isFalse();
    }

    // Lot6: empty/null-ish token → validateToken returns false (catches IllegalArgumentException)
    @Test
    void validateToken_emptyToken_returnsFalse() {
        assertThat(tokenService.validateToken("")).isFalse();
    }

    // Scoped-RBAC: generateToken(userId, email, roles, activeTenantId) includes all claims
    @Test
    void generateToken_withRolesAndActiveTenant_includesAllClaims() {
        String token = tokenService.generateToken(
                99L, "sophie@salon.fr", java.util.List.of("PRO"), 42L);

        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("99");
        assertThat(claims.get("email", String.class)).isEqualTo("sophie@salon.fr");
        assertThat(claims.get("roles", java.util.List.class)).containsExactly("PRO");
        assertThat(claims.get("activeTenantId", Long.class)).isEqualTo(42L);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    // Lot6: wrong signature → validateToken returns false (catches io.jsonwebtoken.JwtException,
    // which covers io.jsonwebtoken.security.SignatureException and friends).
    @Test
    void validateToken_wrongSignature_returnsFalse() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "an-entirely-different-secret-key-32-bytes+padding-ok".getBytes(StandardCharsets.UTF_8));
        String tokenSignedElsewhere = Jwts.builder()
                .subject("1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ONE_DAY_MS))
                .signWith(wrongKey)
                .compact();

        assertThat(tokenService.validateToken(tokenSignedElsewhere)).isFalse();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Task 5 — Scoped RBAC: roles[] + activeTenantId + availableTenants
    // ──────────────────────────────────────────────────────────────────────

    private TokenService buildScopedTokenService(
            com.luxpretty.app.users.app.UserRoleService userRoleService,
            com.luxpretty.app.tenant.repo.TenantRepository tenantRepository) {
        TokenService svc = new TokenService(userRoleService, tenantRepository);
        ReflectionTestUtils.setField(svc, "tokenSecret", TEST_SECRET);
        ReflectionTestUtils.setField(svc, "tokenExpirationMs", ONE_DAY_MS);
        return svc;
    }

    @Test
    void generateToken_includesRolesAndActiveTenantClaims() {
        String token = tokenService.generateToken(1L, "a@a.com",
                java.util.List.of("PRO", "EMPLOYEE"), 42L);

        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        assertThat(claims.get("roles", java.util.List.class))
                .containsExactlyInAnyOrder("PRO", "EMPLOYEE");
        assertThat(claims.get("activeTenantId", Long.class)).isEqualTo(42L);
        assertThat(claims.get("email", String.class)).isEqualTo("a@a.com");
    }

    @Test
    void generateTokenForUser_picksFirstTenantAsActive() {
        var userRoleService = org.mockito.Mockito.mock(
                com.luxpretty.app.users.app.UserRoleService.class);
        var tenantRepository = org.mockito.Mockito.mock(
                com.luxpretty.app.tenant.repo.TenantRepository.class);
        org.mockito.Mockito.when(userRoleService.findUserTenantIds(1L))
                .thenReturn(java.util.List.of(99L));
        org.mockito.Mockito.when(userRoleService.resolveRoles(1L, 99L))
                .thenReturn(java.util.Set.of(com.luxpretty.app.users.domain.Role.PRO));
        var tenant = new com.luxpretty.app.tenant.domain.Tenant();
        tenant.setId(99L);
        tenant.setSlug("first");
        tenant.setName("First");
        org.mockito.Mockito.when(tenantRepository.findById(99L)).thenReturn(java.util.Optional.of(tenant));

        TokenService svc = buildScopedTokenService(userRoleService, tenantRepository);
        com.luxpretty.app.users.domain.User user = com.luxpretty.app.users.domain.User.builder()
                .id(1L).email("a@a.com").build();

        String token = svc.generateToken(user);
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        assertThat(claims.get("activeTenantId", Long.class)).isEqualTo(99L);
        assertThat(claims.get("roles", java.util.List.class)).containsExactly("PRO");
    }

    @Test
    void generateTokenForUser_includesAvailableTenants() {
        var userRoleService = org.mockito.Mockito.mock(
                com.luxpretty.app.users.app.UserRoleService.class);
        var tenantRepository = org.mockito.Mockito.mock(
                com.luxpretty.app.tenant.repo.TenantRepository.class);
        org.mockito.Mockito.when(userRoleService.findUserTenantIds(1L))
                .thenReturn(java.util.List.of(42L, 43L));
        org.mockito.Mockito.when(userRoleService.resolveRoles(1L, 42L))
                .thenReturn(java.util.Set.of(com.luxpretty.app.users.domain.Role.PRO));
        com.luxpretty.app.tenant.domain.Tenant t1 = new com.luxpretty.app.tenant.domain.Tenant();
        t1.setId(42L); t1.setSlug("salon-x"); t1.setName("Salon X");
        com.luxpretty.app.tenant.domain.Tenant t2 = new com.luxpretty.app.tenant.domain.Tenant();
        t2.setId(43L); t2.setSlug("salon-y"); t2.setName("Salon Y");
        org.mockito.Mockito.when(tenantRepository.findById(42L)).thenReturn(java.util.Optional.of(t1));
        org.mockito.Mockito.when(tenantRepository.findById(43L)).thenReturn(java.util.Optional.of(t2));

        TokenService svc = buildScopedTokenService(userRoleService, tenantRepository);
        com.luxpretty.app.users.domain.User user = com.luxpretty.app.users.domain.User.builder()
                .id(1L).email("a@a.com").build();

        String token = svc.generateToken(user);
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        java.util.List<?> tenants = claims.get("availableTenants", java.util.List.class);
        assertThat(tenants).hasSize(2);
    }

    @Test
    void generateTokenForUser_emptyRoles_whenNoAssignments() {
        var userRoleService = org.mockito.Mockito.mock(
                com.luxpretty.app.users.app.UserRoleService.class);
        var tenantRepository = org.mockito.Mockito.mock(
                com.luxpretty.app.tenant.repo.TenantRepository.class);
        org.mockito.Mockito.when(userRoleService.findUserTenantIds(2L))
                .thenReturn(java.util.List.of());
        org.mockito.Mockito.when(userRoleService.resolveRoles(2L, null))
                .thenReturn(java.util.Set.of());

        TokenService svc = buildScopedTokenService(userRoleService, tenantRepository);
        com.luxpretty.app.users.domain.User user = com.luxpretty.app.users.domain.User.builder()
                .id(2L).email("client@a.com").build();

        String token = svc.generateToken(user);
        assertThat(svc.getRolesFromToken(token)).isEmpty();
        assertThat(svc.getActiveTenantIdFromToken(token)).isNull();
    }

    @Test
    void generateTokenForUser_overrideActiveTenant() {
        var userRoleService = org.mockito.Mockito.mock(
                com.luxpretty.app.users.app.UserRoleService.class);
        var tenantRepository = org.mockito.Mockito.mock(
                com.luxpretty.app.tenant.repo.TenantRepository.class);
        org.mockito.Mockito.when(userRoleService.findUserTenantIds(1L))
                .thenReturn(java.util.List.of(42L, 43L));
        org.mockito.Mockito.when(userRoleService.resolveRoles(1L, 43L))
                .thenReturn(java.util.Set.of(com.luxpretty.app.users.domain.Role.PRO));
        com.luxpretty.app.tenant.domain.Tenant t = new com.luxpretty.app.tenant.domain.Tenant();
        t.setId(43L); t.setSlug("salon-y"); t.setName("Salon Y");
        org.mockito.Mockito.when(tenantRepository.findById(43L)).thenReturn(java.util.Optional.of(t));
        com.luxpretty.app.tenant.domain.Tenant t2 = new com.luxpretty.app.tenant.domain.Tenant();
        t2.setId(42L); t2.setSlug("salon-x"); t2.setName("Salon X");
        org.mockito.Mockito.when(tenantRepository.findById(42L)).thenReturn(java.util.Optional.of(t2));

        TokenService svc = buildScopedTokenService(userRoleService, tenantRepository);
        com.luxpretty.app.users.domain.User user = com.luxpretty.app.users.domain.User.builder()
                .id(1L).email("a@a.com").build();

        String token = svc.generateToken(user, 43L);

        assertThat(svc.getActiveTenantIdFromToken(token)).isEqualTo(43L);
        assertThat(svc.getRolesFromToken(token)).containsExactly("PRO");
    }

    @Test
    void getRolesAndActiveTenantFromToken_readsBackTheClaims() {
        String token = tokenService.generateToken(7L, "x@x.com",
                java.util.List.of("ADMIN"), 13L);

        assertThat(tokenService.getRolesFromToken(token)).containsExactly("ADMIN");
        assertThat(tokenService.getActiveTenantIdFromToken(token)).isEqualTo(13L);
    }
}
