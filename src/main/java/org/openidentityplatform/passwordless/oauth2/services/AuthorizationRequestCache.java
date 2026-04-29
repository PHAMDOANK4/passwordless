package org.openidentityplatform.passwordless.oauth2.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed cache for OAuth2 authorization request parameters.
 * Used when an unauthenticated user hits {@code GET /oauth2/authorize}
 * and needs to be redirected to the login page first.
 * <p>
 * The full authorize request is cached under a random {@code request_id} with a 10-minute TTL.
 * After login, the IdP redirects back to {@code /oauth2/authorize/callback?oauth_request_id={id}}
 * which restores the cached parameters and continues the authorization flow.
 */
@Service
@AllArgsConstructor
@Slf4j
public class AuthorizationRequestCache {

    private static final String KEY_PREFIX = "oauth2:authz:req:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Stores the authorization request parameters and returns a unique request ID.
     */
    public String store(String responseType, String clientId, String redirectUri,
                        String scope, String state, String codeChallenge,
                        String codeChallengeMethod, String nonce) {
        String requestId = UUID.randomUUID().toString();
        CachedAuthorizationRequest cached = new CachedAuthorizationRequest(
                responseType, clientId, redirectUri, scope, state,
                codeChallenge, codeChallengeMethod, nonce
        );
        try {
            String json = objectMapper.writeValueAsString(cached);
            redisTemplate.opsForValue().set(KEY_PREFIX + requestId, json, TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to cache authorization request", e);
        }
        return requestId;
    }

    /**
     * Retrieves and returns the cached authorization request, or empty if expired/missing.
     */
    public Optional<CachedAuthorizationRequest> retrieve(String requestId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + requestId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, CachedAuthorizationRequest.class));
        } catch (JsonProcessingException e) {
            log.error("Unable to parse cached authorization request: {}", requestId, e);
            return Optional.empty();
        }
    }

    /**
     * Removes the cached request after it has been consumed.
     */
    public void remove(String requestId) {
        redisTemplate.delete(KEY_PREFIX + requestId);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CachedAuthorizationRequest {
        private String responseType;
        private String clientId;
        private String redirectUri;
        private String scope;
        private String state;
        private String codeChallenge;
        private String codeChallengeMethod;
        private String nonce;
    }
}
