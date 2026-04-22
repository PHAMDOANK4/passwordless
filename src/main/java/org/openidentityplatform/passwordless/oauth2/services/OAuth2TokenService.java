package org.openidentityplatform.passwordless.oauth2.services;

import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.oauth2.dto.OAuth2TokenResponse;
import org.openidentityplatform.passwordless.oauth2.models.AuthorizationCode;
import org.openidentityplatform.passwordless.oauth2.models.OAuthClient;
import org.openidentityplatform.passwordless.oauth2.repositories.AuthorizationCodeRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.OAuthClientRepository;
import org.openidentityplatform.passwordless.oauth2.services.SessionService;
import org.openidentityplatform.passwordless.token.models.TokenPair;
import org.openidentityplatform.passwordless.token.services.InvalidRefreshTokenException;
import org.openidentityplatform.passwordless.token.services.JwtTokenService;
import org.openidentityplatform.passwordless.token.services.RefreshTokenService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
@AllArgsConstructor
public class OAuth2TokenService {

    private final AuthorizationCodeRepository authorizationCodeRepository;
    private final OAuthClientRepository oAuthClientRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenService jwtTokenService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final SessionService sessionService;

    @Transactional
    public OAuth2TokenResponse token(Map<String, String> formParams, String ipAddress, String userAgent)
            throws OAuth2FlowException, InvalidRefreshTokenException {

        String grantType = formParams.get("grant_type");
        if (grantType == null || grantType.isBlank()) {
            throw new OAuth2FlowException("grant_type is required");
        }

        if ("authorization_code".equals(grantType)) {
            return handleAuthorizationCodeGrant(formParams, ipAddress, userAgent);
        }
        if ("refresh_token".equals(grantType)) {
            return handleRefreshTokenGrant(formParams);
        }

        throw new OAuth2FlowException("Unsupported grant_type");
    }

    private OAuth2TokenResponse handleAuthorizationCodeGrant(Map<String, String> formParams, String ipAddress, String userAgent)
            throws OAuth2FlowException {
        String codeValue = formParams.get("code");
        String clientId = formParams.get("client_id");
        String redirectUri = formParams.get("redirect_uri");
        String clientSecret = formParams.get("client_secret");
        String codeVerifier = formParams.get("code_verifier");

        if (codeValue == null || clientId == null || redirectUri == null) {
            throw new OAuth2FlowException("code, client_id and redirect_uri are required");
        }

        OAuthClient client = validateClient(clientId, clientSecret);

        AuthorizationCode code = authorizationCodeRepository
                .findByCodeAndUsedFalseAndExpiresAtAfter(codeValue, Instant.now())
                .orElseThrow(() -> new OAuth2FlowException("Invalid or expired authorization code"));

        if (!clientId.equals(code.getClientId())) {
            throw new OAuth2FlowException("Authorization code client mismatch");
        }
        if (!redirectUri.equals(code.getRedirectUri())) {
            throw new OAuth2FlowException("redirect_uri mismatch");
        }

        validatePkce(code, codeVerifier, client.isRequirePkce());

        code.markAsUsed();
        authorizationCodeRepository.save(code);

        org.openidentityplatform.passwordless.oauth2.models.Session session = sessionService.createSession(
            code.getUser(),
            ipAddress,
            userAgent,
            toAuthMethod(code.getAuthMethod())
        );

        TokenPair pair = refreshTokenService.issueInitialTokenPair(
            code.getUser(),
            clientId,
            ipAddress,
            userAgent,
            session.getSessionId()
        );
        String idToken = jwtTokenService.issueIdToken(code.getUser(), clientId, code.getNonce());

        return new OAuth2TokenResponse(
                pair.getAccessToken(),
                "Bearer",
                pair.getAccessTokenExpiresIn(),
                pair.getRefreshToken(),
                idToken,
                code.getScopes()
        );
    }

    private OAuth2TokenResponse handleRefreshTokenGrant(Map<String, String> formParams)
            throws OAuth2FlowException, InvalidRefreshTokenException {
        String refreshToken = formParams.get("refresh_token");
        String clientId = formParams.get("client_id");
        String clientSecret = formParams.get("client_secret");

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new OAuth2FlowException("refresh_token is required");
        }

        if (clientId != null && !clientId.isBlank()) {
            validateClient(clientId, clientSecret);
        }

        TokenPair pair = refreshTokenService.refresh(refreshToken, clientId);

        return new OAuth2TokenResponse(
                pair.getAccessToken(),
                "Bearer",
                pair.getAccessTokenExpiresIn(),
                pair.getRefreshToken(),
                null,
                "openid profile email"
        );
    }

    private OAuthClient validateClient(String clientId, String clientSecret) throws OAuth2FlowException {
        OAuthClient client = oAuthClientRepository.findByClientId(clientId)
                .filter(OAuthClient::isActive)
                .orElseThrow(() -> new OAuth2FlowException("Invalid client_id"));

        // Public clients may omit secret when PKCE is required.
        boolean allowPublicClient = client.isRequirePkce();
        if (allowPublicClient && (clientSecret == null || clientSecret.isBlank())) {
            return client;
        }

        if (clientSecret == null || !bCryptPasswordEncoder.matches(clientSecret, client.getClientSecret())) {
            throw new OAuth2FlowException("Invalid client credentials");
        }
        return client;
    }

    private void validatePkce(AuthorizationCode code, String codeVerifier, boolean requirePkce) throws OAuth2FlowException {
        if (!requirePkce && (code.getCodeChallenge() == null || code.getCodeChallenge().isBlank())) {
            return;
        }

        if (codeVerifier == null || codeVerifier.isBlank()) {
            throw new OAuth2FlowException("code_verifier is required");
        }

        String method = code.getCodeChallengeMethod() == null ? "S256" : code.getCodeChallengeMethod();
        String calculated;

        if ("plain".equalsIgnoreCase(method)) {
            calculated = codeVerifier;
        } else if ("S256".equalsIgnoreCase(method)) {
            calculated = s256(codeVerifier);
        } else {
            throw new OAuth2FlowException("Unsupported code_challenge_method");
        }

        if (!calculated.equals(code.getCodeChallenge())) {
            throw new OAuth2FlowException("Invalid code_verifier");
        }
    }

    private String s256(String verifier) throws OAuth2FlowException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new OAuth2FlowException("Unable to validate PKCE");
        }
    }

    private org.openidentityplatform.passwordless.auth.models.AuthMethod toAuthMethod(AuthorizationCode.AuthMethod method) {
        if (method == null) {
            return org.openidentityplatform.passwordless.auth.models.AuthMethod.OTP;
        }
        return switch (method) {
            case WEBAUTHN -> org.openidentityplatform.passwordless.auth.models.AuthMethod.WEBAUTHN;
            case TOTP -> org.openidentityplatform.passwordless.auth.models.AuthMethod.TOTP;
            default -> org.openidentityplatform.passwordless.auth.models.AuthMethod.OTP;
        };
    }
}
