package com.prettyface.app.auth;

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

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
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

    // Lot6: generateToken(userId, email, role) includes all three claims in payload
    @Test
    void generateToken_withEmailAndRole_includesAllClaims() {
        String token = tokenService.generateToken(99L, "sophie@salon.fr", "PRO");

        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("99");
        assertThat(claims.get("email", String.class)).isEqualTo("sophie@salon.fr");
        assertThat(claims.get("role", String.class)).isEqualTo("PRO");
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
}
