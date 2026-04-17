package org.openidentityplatform.passwordless.oauth2.services;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.oauth2.models.AuthorizationCode;
import org.openidentityplatform.passwordless.oauth2.models.OAuthClient;
import org.openidentityplatform.passwordless.oauth2.repositories.AuthorizationCodeRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.OAuthClientRepository;
import org.openidentityplatform.passwordless.token.services.JwtTokenService;
import org.openidentityplatform.passwordless.token.services.TokenBlacklistService;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class OAuth2AuthorizationService {

    private final OAuthClientRepository oAuthClientRepository;
    private final AuthorizationCodeRepository authorizationCodeRepository;
    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final SessionService sessionService;
    private final UserRepository userRepository;

    public String authorize(String authorizationHeader,
                            String responseType,
                            String clientId,
                            String redirectUri,
                            String scope,
                            String state,
                            String codeChallenge,
                            String codeChallengeMethod,
                            String nonce,
                            HttpServletRequest request) throws OAuth2FlowException {

        if (!"code".equals(responseType)) {
            throw new OAuth2FlowException("Unsupported response_type");
        }
        if (clientId == null || clientId.isBlank() || redirectUri == null || redirectUri.isBlank()) {
            throw new OAuth2FlowException("client_id and redirect_uri are required");
        }

        OAuthClient client = oAuthClientRepository.findByClientId(clientId)
                .filter(OAuthClient::isActive)
                .orElseThrow(() -> new OAuth2FlowException("Invalid client"));

        validateRedirectUri(client, redirectUri);
        String effectiveScope = validateScope(client, scope);

        if (client.isRequirePkce() && (codeChallenge == null || codeChallenge.isBlank())) {
            throw new OAuth2FlowException("code_challenge is required for this client");
        }

        String bearer = extractBearerToken(authorizationHeader);
        JWTClaimsSet claims;
        try {
            claims = jwtTokenService.validateAccessToken(bearer);
        } catch (IllegalArgumentException e) {
            throw new OAuth2FlowException("Invalid access token");
        }

        if (claims.getJWTID() != null && tokenBlacklistService.isBlacklisted(claims.getJWTID())) {
            throw new OAuth2FlowException("Access token revoked");
        }

        Object sid = claims.getClaim("sid");
        if (sid instanceof String sessionId && !sessionId.isBlank() && !sessionService.isSessionActive(sessionId)) {
            throw new OAuth2FlowException("Session is no longer active");
        }

        User user = userRepository.findById(claims.getSubject())
                .orElseThrow(() -> new OAuth2FlowException("Authenticated user not found"));

        AuthorizationCode authCode = new AuthorizationCode();
        authCode.setCode(generateCode());
        authCode.setUser(user);
        authCode.setClientId(clientId);
        authCode.setOauthClient(client);
        authCode.setRedirectUri(redirectUri);
        authCode.setScopes(effectiveScope);
        authCode.setState(state);
        authCode.setCodeChallenge(codeChallenge);
        authCode.setCodeChallengeMethod(codeChallengeMethod == null || codeChallengeMethod.isBlank() ? "S256" : codeChallengeMethod);
        authCode.setNonce(nonce);
        authCode.setIpAddress(request.getRemoteAddr());
        authCode.setUserAgent(request.getHeader("User-Agent"));
        authCode.setAuthMethod(resolveAuthMethodFromClaims(claims));
        authCode.setCreatedAt(Instant.now());
        authCode.setExpiresAt(Instant.now().plusSeconds(600));
        authCode.setUsed(false);
        authorizationCodeRepository.save(authCode);

        StringBuilder redirect = new StringBuilder(redirectUri)
                .append(redirectUri.contains("?") ? "&" : "?")
                .append("code=").append(urlEncode(authCode.getCode()));

        if (state != null && !state.isBlank()) {
            redirect.append("&state=").append(urlEncode(state));
        }

        return redirect.toString();
    }

    private void validateRedirectUri(OAuthClient client, String redirectUri) throws OAuth2FlowException {
        List<String> allowedRedirects = Arrays.stream(client.getRedirectUris().split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toList());

        if (!allowedRedirects.contains(redirectUri)) {
            throw new OAuth2FlowException("redirect_uri is not allowed for this client");
        }
    }

    private String validateScope(OAuthClient client, String requestedScope) throws OAuth2FlowException {
        String allowedScopeStr = client.getAllowedScopes() == null ? "openid profile email" : client.getAllowedScopes();
        List<String> allowed = Arrays.stream(allowedScopeStr.split("\\s+"))
                .filter(v -> !v.isBlank())
                .collect(Collectors.toList());

        if (requestedScope == null || requestedScope.isBlank()) {
            return String.join(" ", allowed);
        }

        List<String> requested = Arrays.stream(requestedScope.split("\\s+"))
                .filter(v -> !v.isBlank())
                .collect(Collectors.toList());

        for (String s : requested) {
            if (!allowed.contains(s)) {
                throw new OAuth2FlowException("Invalid scope requested: " + s);
            }
        }
        return String.join(" ", requested);
    }

    private String extractBearerToken(String authorizationHeader) throws OAuth2FlowException {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new OAuth2FlowException("Missing Bearer access token");
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }

    private String generateCode() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private AuthorizationCode.AuthMethod resolveAuthMethodFromClaims(JWTClaimsSet claims) {
        Object sid = claims.getClaim("sid");
        if (sid instanceof String sessionId && !sessionId.isBlank()) {
            return sessionService.findActiveSession(sessionId)
                    .map(session -> switch (session.getAuthMethod()) {
                        case WEBAUTHN -> AuthorizationCode.AuthMethod.WEBAUTHN;
                        case TOTP -> AuthorizationCode.AuthMethod.TOTP;
                        default -> AuthorizationCode.AuthMethod.OTP;
                    })
                    .orElse(AuthorizationCode.AuthMethod.OTP);
        }
        return AuthorizationCode.AuthMethod.OTP;
    }
}
