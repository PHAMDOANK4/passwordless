package org.openidentityplatform.passwordless.configuration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtProviderTest {

    private JwtProvider jwtProvider;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setAccessTokenExpirationMinutes(15);
        jwtProperties.setRefreshTokenExpirationDays(7);
        jwtProperties.setIssuer("test-issuer");

        jwtProvider = new JwtProvider(jwtProperties);
        jwtProvider.init();
    }

    @Test
    void testGenerateAccessToken() {
        // When
        String token = jwtProvider.generateAccessToken("user-123", "user@test.com");

        // Then
        assertNotNull(token);
        Claims claims = jwtProvider.validateToken(token);
        assertEquals("user-123", claims.getSubject());
        assertEquals("user@test.com", claims.get("email", String.class));
        assertEquals("access", claims.get("type", String.class));
    }

    @Test
    void testGenerateRefreshToken() {
        // When
        String token = jwtProvider.generateRefreshToken("user-123");

        // Then
        assertNotNull(token);
        Claims claims = jwtProvider.validateToken(token);
        assertEquals("user-123", claims.getSubject());
        assertEquals("refresh", claims.get("type", String.class));
    }

    @Test
    void testValidateToken_expired() throws Exception {
        // Given - create a token signed with the same keys but already expired
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Build a separate provider with its own keys to create an expired token
        JwtProvider expiredProvider = new JwtProvider(jwtProperties);
        expiredProvider.init();

        // Use reflection to build an expired token using the provider's private key
        String expiredToken = Jwts.builder()
                .subject("user-123")
                .claim("type", "access")
                .issuer("test-issuer")
                .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .signWith((RSAPrivateKey) keyPair.getPrivate())
                .compact();

        // When & Then
        assertThrows(JwtException.class, () -> jwtProvider.validateToken(expiredToken));
    }

    @Test
    void testIsTokenValid_valid() {
        // Given
        String token = jwtProvider.generateAccessToken("user-123", "user@test.com");

        // When & Then
        assertTrue(jwtProvider.isTokenValid(token));
    }

    @Test
    void testIsTokenValid_invalid() {
        // When & Then
        assertFalse(jwtProvider.isTokenValid("not-a-valid-jwt-token"));
    }
}
