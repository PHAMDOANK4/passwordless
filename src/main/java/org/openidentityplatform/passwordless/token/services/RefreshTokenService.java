package org.openidentityplatform.passwordless.token.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.oauth2.models.Token;
import org.openidentityplatform.passwordless.oauth2.repositories.TokenRepository;
import org.openidentityplatform.passwordless.oauth2.services.SessionService;
import org.openidentityplatform.passwordless.token.configuration.TokenProperties;
import org.openidentityplatform.passwordless.token.models.TokenPair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
@AllArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final TokenRepository tokenRepository;
    private final JwtTokenService jwtTokenService;
    private final TokenProperties tokenProperties;
    private final SessionService sessionService;

    @Transactional
    public TokenPair refresh(String rawRefreshToken) throws InvalidRefreshTokenException {
        return refresh(rawRefreshToken, null);
    }

    @Transactional
    public TokenPair refresh(String rawRefreshToken, String expectedClientId) throws InvalidRefreshTokenException {
        String refreshTokenHash = hash(rawRefreshToken);

        Token refreshToken = tokenRepository.findByTokenValueAndNotRevoked(refreshTokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (refreshToken.getTokenType() != Token.TokenType.REFRESH || refreshToken.isExpired()) {
            throw new InvalidRefreshTokenException();
        }
        if (expectedClientId != null && !expectedClientId.isBlank() && !expectedClientId.equals(refreshToken.getClientId())) {
            throw new InvalidRefreshTokenException();
        }
        if (refreshToken.getSessionId() != null && !refreshToken.getSessionId().isBlank()
                && !sessionService.isSessionActive(refreshToken.getSessionId())) {
            throw new InvalidRefreshTokenException();
        }

        // Revoke old refresh token (rotation)
        refreshToken.setRevoked(true);
        refreshToken.setRevokedAt(Instant.now());
        tokenRepository.save(refreshToken);

        String newAccessToken = jwtTokenService.issueAccessToken(
            refreshToken.getUser(),
            refreshToken.getClientId(),
            refreshToken.getSessionId()
        );
        String newRawRefreshToken = jwtTokenService.generateOpaqueRefreshToken();
        String newRefreshTokenHash = hash(newRawRefreshToken);

        Token accessToken = new Token();
        accessToken.setUser(refreshToken.getUser());
        accessToken.setClientId(refreshToken.getClientId());
        accessToken.setTokenType(Token.TokenType.ACCESS);
        accessToken.setTokenValue(newAccessToken);
        accessToken.setScopes(refreshToken.getScopes());
        accessToken.setSessionId(refreshToken.getSessionId());
        accessToken.setDeviceInfo(refreshToken.getDeviceInfo());
        accessToken.setIpAddress(refreshToken.getIpAddress());
        accessToken.setExpiresAt(Instant.now().plusSeconds(jwtTokenService.getAccessTokenLifetimeSeconds()));
        accessToken.setRevoked(false);
        tokenRepository.save(accessToken);

        Token rotatedRefreshToken = new Token();
        rotatedRefreshToken.setUser(refreshToken.getUser());
        rotatedRefreshToken.setClientId(refreshToken.getClientId());
        rotatedRefreshToken.setTokenType(Token.TokenType.REFRESH);
        rotatedRefreshToken.setTokenValue(newRefreshTokenHash);
        rotatedRefreshToken.setScopes(refreshToken.getScopes());
        rotatedRefreshToken.setSessionId(refreshToken.getSessionId());
        rotatedRefreshToken.setDeviceInfo(refreshToken.getDeviceInfo());
        rotatedRefreshToken.setIpAddress(refreshToken.getIpAddress());
        rotatedRefreshToken.setExpiresAt(Instant.now().plusSeconds(tokenProperties.getRefreshTokenLifetimeSeconds()));
        rotatedRefreshToken.setRevoked(false);
        tokenRepository.save(rotatedRefreshToken);

        return new TokenPair(
                newAccessToken,
                newRawRefreshToken,
                jwtTokenService.getAccessTokenLifetimeSeconds()
        );
    }

    @Transactional
    public TokenPair issueInitialTokenPair(User user, String clientId, String ipAddress, String userAgent) {
        return issueInitialTokenPair(user, clientId, ipAddress, userAgent, null);
    }

    @Transactional
    public TokenPair issueInitialTokenPair(User user, String clientId, String ipAddress, String userAgent, String sessionId) {
        String normalizedClientId = (clientId == null || clientId.isBlank()) ? "passwordless-web" : clientId;

        String accessToken = jwtTokenService.issueAccessToken(user, normalizedClientId, sessionId);
        String rawRefreshToken = jwtTokenService.generateOpaqueRefreshToken();
        String refreshTokenHash = hash(rawRefreshToken);

        Token accessTokenRecord = new Token();
        accessTokenRecord.setUser(user);
        accessTokenRecord.setClientId(normalizedClientId);
        accessTokenRecord.setTokenType(Token.TokenType.ACCESS);
        accessTokenRecord.setTokenValue(accessToken);
        accessTokenRecord.setScopes("openid profile email");
        accessTokenRecord.setSessionId(sessionId);
        accessTokenRecord.setDeviceInfo(userAgent);
        accessTokenRecord.setIpAddress(ipAddress);
        accessTokenRecord.setExpiresAt(Instant.now().plusSeconds(jwtTokenService.getAccessTokenLifetimeSeconds()));
        accessTokenRecord.setRevoked(false);
        tokenRepository.save(accessTokenRecord);

        Token refreshToken = new Token();
        refreshToken.setUser(user);
        refreshToken.setClientId(normalizedClientId);
        refreshToken.setTokenType(Token.TokenType.REFRESH);
        refreshToken.setTokenValue(refreshTokenHash);
        refreshToken.setScopes("openid profile email");
        refreshToken.setSessionId(sessionId);
        refreshToken.setDeviceInfo(userAgent);
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setExpiresAt(Instant.now().plusSeconds(tokenProperties.getRefreshTokenLifetimeSeconds()));
        refreshToken.setRevoked(false);
        tokenRepository.save(refreshToken);

        return new TokenPair(accessToken, rawRefreshToken, jwtTokenService.getAccessTokenLifetimeSeconds());
    }

    public long getRefreshTokenLifetimeSeconds() {
        return tokenProperties.getRefreshTokenLifetimeSeconds();
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
