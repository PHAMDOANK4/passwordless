package org.openidentityplatform.passwordless.oauth2.services;

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.oauth2.models.OAuthClient;
import org.openidentityplatform.passwordless.oauth2.models.Token;
import org.openidentityplatform.passwordless.oauth2.repositories.OAuthClientRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.TokenRepository;
import org.openidentityplatform.passwordless.token.services.JwtTokenService;
import org.openidentityplatform.passwordless.token.services.RefreshTokenService;
import org.openidentityplatform.passwordless.token.services.TokenBlacklistService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@AllArgsConstructor
public class OAuth2TokenManagementService {

    private final OAuthClientRepository oAuthClientRepository;
    private final TokenRepository tokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final SessionService sessionService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public Map<String, Object> introspect(Map<String, String> formParams) throws OAuth2FlowException {
        String token = formParams.get("token");
        String tokenTypeHint = formParams.get("token_type_hint");

        if (token == null || token.isBlank()) {
            throw new OAuth2FlowException("token is required");
        }

        validateClient(formParams.get("client_id"), formParams.get("client_secret"), false);

        if ("access_token".equals(tokenTypeHint)) {
            return introspectAccessToken(token);
        }
        if ("refresh_token".equals(tokenTypeHint)) {
            return introspectRefreshToken(token);
        }

        Map<String, Object> accessResult = introspectAccessToken(token);
        if (Boolean.TRUE.equals(accessResult.get("active"))) {
            return accessResult;
        }
        return introspectRefreshToken(token);
    }

    @Transactional
    public void revoke(Map<String, String> formParams) throws OAuth2FlowException {
        String token = formParams.get("token");
        String tokenTypeHint = formParams.get("token_type_hint");

        if (token == null || token.isBlank()) {
            throw new OAuth2FlowException("token is required");
        }

        validateClient(formParams.get("client_id"), formParams.get("client_secret"), false);

        if ("access_token".equals(tokenTypeHint)) {
            revokeAccessToken(token);
            return;
        }
        if ("refresh_token".equals(tokenTypeHint)) {
            revokeRefreshToken(token);
            return;
        }

        // RFC 7009-compatible behavior: try both and always return success for unknown tokens.
        revokeAccessToken(token);
        revokeRefreshToken(token);
    }

    private Map<String, Object> introspectAccessToken(String rawToken) {
        JWTClaimsSet claims;
        try {
            claims = jwtTokenService.validateAccessToken(rawToken);
        } catch (IllegalArgumentException e) {
            return inactiveResponse();
        }

        Optional<Token> tokenRecordOpt = tokenRepository.findByTokenValueAndNotRevoked(rawToken);
        if (tokenRecordOpt.isEmpty()) {
            return introspectStatelessClientCredentialsToken(claims);
        }

        Token tokenRecord = tokenRecordOpt.get();
        if (tokenRecord.getTokenType() != Token.TokenType.ACCESS || tokenRecord.isExpired()) {
            return inactiveResponse();
        }

        if (claims.getJWTID() != null && tokenBlacklistService.isBlacklisted(claims.getJWTID())) {
            return inactiveResponse();
        }

        Object sid = claims.getClaim("sid");
        if (sid instanceof String sessionId && !sessionId.isBlank() && !sessionService.isSessionActive(sessionId)) {
            return inactiveResponse();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("active", true);
        response.put("token_type", "access_token");
        response.put("scope", firstNonBlank(claimAsString(claims.getClaim("scope")), tokenRecord.getScopes()));
        response.put("client_id", firstNonBlank(claimAsString(claims.getClaim("client_id")), tokenRecord.getClientId()));
        response.put("sub", claims.getSubject());

        if (claims.getExpirationTime() != null) {
            response.put("exp", claims.getExpirationTime().toInstant().getEpochSecond());
        }
        if (claims.getIssueTime() != null) {
            response.put("iat", claims.getIssueTime().toInstant().getEpochSecond());
        }
        if (claims.getIssuer() != null) {
            response.put("iss", claims.getIssuer());
        }
        if (claims.getAudience() != null && !claims.getAudience().isEmpty()) {
            response.put("aud", claims.getAudience());
        }
        if (sid instanceof String sessionId && !sessionId.isBlank()) {
            response.put("sid", sessionId);
        }

        return response;
    }

    private Map<String, Object> introspectStatelessClientCredentialsToken(JWTClaimsSet claims) {
        String grantType = claimAsString(claims.getClaim("grant_type"));
        if (!"client_credentials".equals(grantType)) {
            return inactiveResponse();
        }

        String clientId = claimAsString(claims.getClaim("client_id"));
        if (clientId == null || clientId.isBlank()) {
            return inactiveResponse();
        }

        boolean activeClient = oAuthClientRepository.findByClientId(clientId)
                .map(OAuthClient::isActive)
                .orElse(false);
        if (!activeClient) {
            return inactiveResponse();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("active", true);
        response.put("token_type", "access_token");
        response.put("scope", claimAsString(claims.getClaim("scope")));
        response.put("client_id", clientId);
        response.put("sub", claims.getSubject());
        if (claims.getExpirationTime() != null) {
            response.put("exp", claims.getExpirationTime().toInstant().getEpochSecond());
        }
        if (claims.getIssueTime() != null) {
            response.put("iat", claims.getIssueTime().toInstant().getEpochSecond());
        }
        if (claims.getIssuer() != null) {
            response.put("iss", claims.getIssuer());
        }
        if (claims.getAudience() != null && !claims.getAudience().isEmpty()) {
            response.put("aud", claims.getAudience());
        }
        return response;
    }

    private Map<String, Object> introspectRefreshToken(String rawToken) {
        String tokenHash = refreshTokenService.hash(rawToken);
        Optional<Token> tokenRecordOpt = tokenRepository.findByTokenValueAndNotRevoked(tokenHash);
        if (tokenRecordOpt.isEmpty()) {
            return inactiveResponse();
        }

        Token tokenRecord = tokenRecordOpt.get();
        if (tokenRecord.getTokenType() != Token.TokenType.REFRESH || tokenRecord.isExpired()) {
            return inactiveResponse();
        }

        if (tokenRecord.getSessionId() != null && !tokenRecord.getSessionId().isBlank()
                && !sessionService.isSessionActive(tokenRecord.getSessionId())) {
            return inactiveResponse();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("active", true);
        response.put("token_type", "refresh_token");
        response.put("scope", tokenRecord.getScopes());
        response.put("client_id", tokenRecord.getClientId());
        response.put("sub", tokenRecord.getUser() != null ? tokenRecord.getUser().getId() : null);
        response.put("exp", tokenRecord.getExpiresAt().getEpochSecond());
        response.put("iat", tokenRecord.getCreatedAt().getEpochSecond());
        if (tokenRecord.getSessionId() != null && !tokenRecord.getSessionId().isBlank()) {
            response.put("sid", tokenRecord.getSessionId());
        }

        return response;
    }

    private void revokeAccessToken(String rawToken) {
        Instant now = Instant.now();

        try {
            JWTClaimsSet claims = jwtTokenService.validateAccessToken(rawToken);
            if (claims.getJWTID() != null && claims.getExpirationTime() != null) {
                tokenBlacklistService.blacklist(
                        claims.getJWTID(),
                        claims.getSubject(),
                        claims.getExpirationTime().toInstant(),
                        "oauth2_token_revocation"
                );
            }
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed tokens to keep revocation endpoint idempotent.
        }

        tokenRepository.findByTokenValueAndNotRevoked(rawToken).ifPresent(token -> {
            token.setRevoked(true);
            token.setRevokedAt(now);
            tokenRepository.save(token);
        });
    }

    private void revokeRefreshToken(String rawToken) {
        String tokenHash = refreshTokenService.hash(rawToken);
        Instant now = Instant.now();

        tokenRepository.findByTokenValueAndNotRevoked(tokenHash).ifPresent(token -> {
            if (token.getTokenType() == Token.TokenType.REFRESH) {
                token.setRevoked(true);
                token.setRevokedAt(now);
                tokenRepository.save(token);
            }
        });
    }

    private OAuthClient validateClient(String clientId, String clientSecret, boolean allowPublicClientWithoutSecret)
            throws OAuth2FlowException {
        if (clientId == null || clientId.isBlank()) {
            throw new OAuth2FlowException("client_id is required");
        }

        OAuthClient client = oAuthClientRepository.findByClientId(clientId)
                .filter(OAuthClient::isActive)
                .orElseThrow(() -> new OAuth2FlowException("Invalid client_id"));

        boolean allowPublicClient = allowPublicClientWithoutSecret && client.isRequirePkce();
        if (allowPublicClient && (clientSecret == null || clientSecret.isBlank())) {
            return client;
        }

        if (clientSecret == null || !bCryptPasswordEncoder.matches(clientSecret, client.getClientSecret())) {
            throw new OAuth2FlowException("Invalid client credentials");
        }
        return client;
    }

    private Map<String, Object> inactiveResponse() {
        return Map.of("active", false);
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private String claimAsString(Object value) {
        return value instanceof String ? (String) value : null;
    }
}
