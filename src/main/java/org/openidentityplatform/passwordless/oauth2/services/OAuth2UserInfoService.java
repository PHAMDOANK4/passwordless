package org.openidentityplatform.passwordless.oauth2.services;

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.token.services.JwtTokenService;
import org.openidentityplatform.passwordless.token.services.TokenBlacklistService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@AllArgsConstructor
public class OAuth2UserInfoService {

    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final SessionService sessionService;
    private final UserRepository userRepository;

    public Map<String, Object> userInfo(String authorizationHeader) throws OAuth2FlowException {
        String token = extractBearerToken(authorizationHeader);
        JWTClaimsSet claims;
        try {
            claims = jwtTokenService.validateAccessToken(token);
        } catch (IllegalArgumentException e) {
            throw new OAuth2FlowException("Invalid access token");
        }

        if (claims.getJWTID() != null && tokenBlacklistService.isBlacklisted(claims.getJWTID())) {
            throw new OAuth2FlowException("Token revoked");
        }

        Object sid = claims.getClaim("sid");
        if (sid instanceof String sessionId && !sessionId.isBlank() && !sessionService.isSessionActive(sessionId)) {
            throw new OAuth2FlowException("Session is no longer active");
        }

        User user = userRepository.findById(claims.getSubject())
                .orElseThrow(() -> new OAuth2FlowException("User not found"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sub", user.getId());
        response.put("email", user.getEmail());
        response.put("email_verified", true);
        response.put("name", user.getDisplayName());
        response.put("preferred_username", user.getEmail());
        response.put("updated_at", user.getUpdatedAt() != null ? user.getUpdatedAt().getEpochSecond() : null);
        return response;
    }

    private String extractBearerToken(String authorizationHeader) throws OAuth2FlowException {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new OAuth2FlowException("Missing Bearer token");
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }
}
