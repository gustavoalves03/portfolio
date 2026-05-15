package com.luxpretty.app.auth;

import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TokenService {
    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    @Value("${app.auth.token.secret}")
    private String tokenSecret;

    @Value("${app.auth.token.expiration-ms}")
    private long tokenExpirationMs;

    private final UserRoleService userRoleService;
    private final TenantRepository tenantRepository;

    /** Default constructor preserved for tests that build TokenService with no deps. */
    public TokenService() {
        this.userRoleService = null;
        this.tenantRepository = null;
    }

    public TokenService(UserRoleService userRoleService, TenantRepository tenantRepository) {
        this.userRoleService = userRoleService;
        this.tenantRepository = tenantRepository;
    }

    private SecretKey getSigningKey() {
        // Ensure the secret is at least 256 bits (32 bytes) for HS256
        byte[] keyBytes = tokenSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + tokenExpirationMs);

        return Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(getSigningKey())
            .compact();
    }

    public String generateToken(Long userId, String email, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + tokenExpirationMs);

        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("email", email)
            .claim("role", role)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(getSigningKey())
            .compact();
    }

    /**
     * Scoped-RBAC overload (used by AuthController + OAuth2 handler). Emits
     * a roles[] claim + activeTenantId + availableTenants[] for the frontend.
     */
    public String generateToken(Long userId, String email, List<String> roles, Long activeTenantId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + tokenExpirationMs);

        JwtBuilder builder = Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("email", email)
            .claim("roles", roles)
            .claim("activeTenantId", activeTenantId);

        // availableTenants only added when the user has TENANT-scoped assignments;
        // requires UserRoleService + TenantRepository to be wired (production path).
        if (userRoleService != null && tenantRepository != null) {
            List<Map<String, Object>> tenants = userRoleService.findUserTenantIds(userId).stream()
                    .map(tenantRepository::findById)
                    .flatMap(java.util.Optional::stream)
                    .map(this::toTenantSummaryMap)
                    .toList();
            builder.claim("availableTenants", tenants);
        }

        return builder
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(getSigningKey())
            .compact();
    }

    /**
     * Resolve roles + active tenant + available tenants from the user, then
     * generate the JWT. Picks the user's first TENANT-scoped tenant as active
     * (caller can override via the 2-arg overload).
     */
    public String generateToken(User user) {
        Long activeTenantId = userRoleService == null
                ? null
                : userRoleService.findUserTenantIds(user.getId()).stream().findFirst().orElse(null);
        return generateToken(user, activeTenantId);
    }

    /** Generate a token with an explicit activeTenantId (used by /switch-tenant). */
    public String generateToken(User user, Long activeTenantId) {
        if (userRoleService == null) {
            // Defensive fallback for tests that built TokenService with no deps —
            // emit an empty roles[] and the requested activeTenantId.
            return generateToken(user.getId(), user.getEmail(), List.<String>of(), activeTenantId);
        }
        Set<Role> resolved = userRoleService.resolveRoles(user.getId(), activeTenantId);
        List<String> roleNames = resolved.stream().map(Enum::name).toList();
        return generateToken(user.getId(), user.getEmail(), roleNames, activeTenantId);
    }

    private Map<String, Object> toTenantSummaryMap(Tenant t) {
        return Map.of(
                "id", t.getId(),
                "slug", t.getSlug() == null ? "" : t.getSlug(),
                "name", t.getName() == null ? "" : t.getName()
        );
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(parse(token).getSubject());
    }

    public Long getActiveTenantIdFromToken(String token) {
        Object value = parse(token).get("activeTenantId");
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Object value = parse(token).get("roles");
        if (value == null) return List.of();
        return (List<String>) value;
    }

    public boolean validateToken(String authToken) {
        try {
            parse(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            logger.warn("Invalid JWT: {}", ex.getMessage());
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
