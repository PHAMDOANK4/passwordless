package org.openidentityplatform.passwordless.auth.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openidentityplatform.passwordless.auth.configuration.AuthProperties;
import org.openidentityplatform.passwordless.auth.models.AuthMethod;
import org.openidentityplatform.passwordless.auth.models.AuthTransactionState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
@Slf4j
public class AuthTransactionService {

    private static final String KEY_PREFIX = "auth:tx:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthProperties authProperties;

    public AuthTransactionState create(String identifier, String clientId, AuthMethod method, String ipAddress, String userAgent) {
        AuthTransactionState tx = new AuthTransactionState();
        tx.setId(UUID.randomUUID().toString());
        tx.setIdentifier(identifier);
        tx.setClientId(clientId == null || clientId.isBlank() ? "passwordless-web" : clientId);
        tx.setMethod(method);
        tx.setStatus("PENDING");
        tx.setCreatedAt(Instant.now());
        tx.setExpiresAt(Instant.now().plus(Duration.ofSeconds(authProperties.getTransactionTtlSeconds())));
        tx.setAttempts(0);
        tx.setIpAddress(ipAddress);
        tx.setUserAgent(userAgent);
        save(tx);
        return tx;
    }

    public Optional<AuthTransactionState> find(String txId) {
        String raw = redisTemplate.opsForValue().get(KEY_PREFIX + txId);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            AuthTransactionState tx = objectMapper.readValue(raw, AuthTransactionState.class);
            return Optional.of(tx);
        } catch (JsonProcessingException e) {
            log.error("Unable to parse auth transaction {}", txId, e);
            return Optional.empty();
        }
    }

    public void save(AuthTransactionState tx) {
        try {
            String raw = objectMapper.writeValueAsString(tx);
            long ttl = Duration.between(Instant.now(), tx.getExpiresAt()).toSeconds();
            if (ttl <= 0) {
                ttl = 1;
            }
            redisTemplate.opsForValue().set(KEY_PREFIX + tx.getId(), raw, Duration.ofSeconds(ttl));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to persist auth transaction", e);
        }
    }

    public void delete(String txId) {
        redisTemplate.delete(KEY_PREFIX + txId);
    }
}
