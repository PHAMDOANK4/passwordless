package org.openidentityplatform.passwordless.oauth2.services;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.oauth2.models.OAuthClient;
import org.openidentityplatform.passwordless.oauth2.models.Token;
import org.openidentityplatform.passwordless.oauth2.repositories.OAuthClientRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.TokenRepository;
import org.openidentityplatform.passwordless.token.services.JwtTokenService;
import org.openidentityplatform.passwordless.token.services.RefreshTokenService;
import org.openidentityplatform.passwordless.token.services.TokenBlacklistService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2TokenManagementServiceTest {

    @Mock
    private OAuthClientRepository oAuthClientRepository;
    @Mock
    private TokenRepository tokenRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private SessionService sessionService;

    private OAuth2TokenManagementService service;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        service = new OAuth2TokenManagementService(
                oAuthClientRepository,
                tokenRepository,
                refreshTokenService,
                jwtTokenService,
                tokenBlacklistService,
                sessionService,
                passwordEncoder
        );
    }

    @Test
    void introspect_requiresToken() {
        OAuth2FlowException exception = assertThrows(
                OAuth2FlowException.class,
                () -> service.introspect(Map.of("client_id", "client", "client_secret", "secret"))
        );

        assertEquals("token is required", exception.getMessage());
    }

    @Test
    void introspect_returnsActiveAccessToken() throws Exception {
        OAuthClient client = buildActiveClient();
        when(oAuthClientRepository.findByClientId("client")).thenReturn(Optional.of(client));

        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer("issuer")
                .claim("scope", "openid profile")
                .claim("client_id", "client")
                .claim("sid", "sid-1")
                .jwtID("jti-1")
                .issueTime(Date.from(now.minusSeconds(10)))
                .expirationTime(Date.from(now.plusSeconds(600)))
                .build();

        Token accessToken = new Token();
        accessToken.setTokenType(Token.TokenType.ACCESS);
        accessToken.setTokenValue("access-token");
        accessToken.setScopes("openid profile");
        accessToken.setClientId("client");
        accessToken.setSessionId("sid-1");
        accessToken.setExpiresAt(now.plusSeconds(600));

        when(jwtTokenService.validateAccessToken("access-token")).thenReturn(claims);
        when(tokenRepository.findByTokenValueAndNotRevoked("access-token")).thenReturn(Optional.of(accessToken));
        when(tokenBlacklistService.isBlacklisted("jti-1")).thenReturn(false);
        when(sessionService.isSessionActive("sid-1")).thenReturn(true);

        Map<String, Object> result = service.introspect(Map.of(
                "token", "access-token",
                "client_id", "client",
                "client_secret", "secret"
        ));

        assertTrue((Boolean) result.get("active"));
        assertEquals("access_token", result.get("token_type"));
        assertEquals("user-1", result.get("sub"));
        assertEquals("client", result.get("client_id"));
    }

    @Test
        void introspect_returnsActiveRefreshTokenWithHint() throws Exception {
        OAuthClient client = buildActiveClient();
        when(oAuthClientRepository.findByClientId("client")).thenReturn(Optional.of(client));

        User user = new User();
        user.setId("user-2");

        Token refreshToken = new Token();
        refreshToken.setTokenType(Token.TokenType.REFRESH);
        refreshToken.setTokenValue("hash-1");
        refreshToken.setScopes("openid email");
        refreshToken.setClientId("client");
        refreshToken.setSessionId("sid-2");
        refreshToken.setUser(user);
        refreshToken.setCreatedAt(Instant.now().minusSeconds(120));
        refreshToken.setExpiresAt(Instant.now().plusSeconds(3600));

        when(refreshTokenService.hash("refresh-token")).thenReturn("hash-1");
        when(tokenRepository.findByTokenValueAndNotRevoked("hash-1")).thenReturn(Optional.of(refreshToken));
        when(sessionService.isSessionActive("sid-2")).thenReturn(true);

        Map<String, Object> result = service.introspect(Map.of(
                "token", "refresh-token",
                "token_type_hint", "refresh_token",
                "client_id", "client",
                "client_secret", "secret"
        ));

        assertTrue((Boolean) result.get("active"));
        assertEquals("refresh_token", result.get("token_type"));
        assertEquals("user-2", result.get("sub"));
    }

    @Test
        void revoke_accessToken_blacklistsAndRevokesRecord() throws Exception {
        OAuthClient client = buildActiveClient();
        when(oAuthClientRepository.findByClientId("client")).thenReturn(Optional.of(client));

        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-3")
                .jwtID("jti-3")
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();

        Token accessToken = new Token();
        accessToken.setTokenType(Token.TokenType.ACCESS);
        accessToken.setTokenValue("access-token");
        accessToken.setExpiresAt(now.plusSeconds(300));

        when(jwtTokenService.validateAccessToken("access-token")).thenReturn(claims);
        when(tokenRepository.findByTokenValueAndNotRevoked("access-token")).thenReturn(Optional.of(accessToken));

        service.revoke(Map.of(
                "token", "access-token",
                "token_type_hint", "access_token",
                "client_id", "client",
                "client_secret", "secret"
        ));

        verify(tokenBlacklistService).blacklist(eq("jti-3"), eq("user-3"), any(Instant.class), eq("oauth2_token_revocation"));
        verify(tokenRepository).save(accessToken);
        assertTrue(accessToken.isRevoked());
    }

    @Test
        void revoke_refreshToken_revokesOnlyRefreshRecord() throws Exception {
        OAuthClient client = buildActiveClient();
        when(oAuthClientRepository.findByClientId("client")).thenReturn(Optional.of(client));

        Token refreshToken = new Token();
        refreshToken.setTokenType(Token.TokenType.REFRESH);
        refreshToken.setTokenValue("hash-refresh");
        refreshToken.setExpiresAt(Instant.now().plusSeconds(600));

        when(refreshTokenService.hash("refresh-token")).thenReturn("hash-refresh");
        when(tokenRepository.findByTokenValueAndNotRevoked("hash-refresh")).thenReturn(Optional.of(refreshToken));

        service.revoke(Map.of(
                "token", "refresh-token",
                "token_type_hint", "refresh_token",
                "client_id", "client",
                "client_secret", "secret"
        ));

        verify(tokenRepository).save(refreshToken);
        verify(tokenBlacklistService, never()).blacklist(any(), any(), any(), any());
        assertTrue(refreshToken.isRevoked());
    }

    @Test
        void introspect_returnsInactiveWhenAccessTokenIsBlacklisted() throws Exception {
        OAuthClient client = buildActiveClient();
        when(oAuthClientRepository.findByClientId("client")).thenReturn(Optional.of(client));

        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-4")
                .jwtID("jti-4")
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();

        Token accessToken = new Token();
        accessToken.setTokenType(Token.TokenType.ACCESS);
        accessToken.setTokenValue("access-token");
        accessToken.setExpiresAt(now.plusSeconds(300));

        when(jwtTokenService.validateAccessToken("access-token")).thenReturn(claims);
        when(tokenRepository.findByTokenValueAndNotRevoked("access-token")).thenReturn(Optional.of(accessToken));
        when(tokenBlacklistService.isBlacklisted("jti-4")).thenReturn(true);

        Map<String, Object> result = service.introspect(Map.of(
                "token", "access-token",
                "client_id", "client",
                "client_secret", "secret"
        ));

        assertFalse((Boolean) result.get("active"));
    }

    private OAuthClient buildActiveClient() {
        OAuthClient client = new OAuthClient();
        client.setClientId("client");
        client.setClientSecret(passwordEncoder.encode("secret"));
        client.setActive(true);
        client.setRequirePkce(false);
        return client;
    }
}
