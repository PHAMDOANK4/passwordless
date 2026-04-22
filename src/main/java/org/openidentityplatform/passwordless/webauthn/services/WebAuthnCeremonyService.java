package org.openidentityplatform.passwordless.webauthn.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openidentityplatform.passwordless.webauthn.configuration.WebAuthnConfiguration;
import org.openidentityplatform.passwordless.webauthn.models.WebAuthnCeremonyState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
@Slf4j
public class WebAuthnCeremonyService {

    private static final String KEY_PREFIX = "webauthn:ceremony:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;
    private final WebAuthnConfiguration webAuthnConfiguration;

    public byte[] generateChallenge() {
        byte[] challenge = new byte[32];
        secureRandom.nextBytes(challenge);
        return challenge;
    }

    public String create(String username, WebAuthnCeremonyState.CeremonyType type, byte[] challenge) {
        WebAuthnCeremonyState state = new WebAuthnCeremonyState();
        state.setTransactionId(UUID.randomUUID().toString());
        state.setCeremonyType(type);
        state.setUsername(username);
        state.setChallenge(Base64.getUrlEncoder().withoutPadding().encodeToString(challenge));
        state.setCreatedAt(Instant.now());
        state.setExpiresAt(Instant.now().plusSeconds(webAuthnConfiguration.getCeremonyTtlSeconds()));

        save(state);
        return state.getTransactionId();
    }

    public Optional<WebAuthnCeremonyState> findByType(String transactionId, WebAuthnCeremonyState.CeremonyType type) {
        Optional<WebAuthnCeremonyState> stateOpt = find(transactionId);
        if (stateOpt.isEmpty()) {
            return Optional.empty();
        }

        WebAuthnCeremonyState state = stateOpt.get();
        if (state.getCeremonyType() != type) {
            return Optional.empty();
        }

        return Optional.of(state);
    }

    public void delete(String transactionId) {
        redisTemplate.delete(redisKey(transactionId));
    }

    public byte[] decodeChallenge(WebAuthnCeremonyState state) {
        return Base64.getUrlDecoder().decode(state.getChallenge().getBytes(StandardCharsets.UTF_8));
    }

    private void save(WebAuthnCeremonyState state) {
        try {
            String raw = objectMapper.writeValueAsString(state);
            long ttlSeconds = Duration.between(Instant.now(), state.getExpiresAt()).toSeconds();
            if (ttlSeconds <= 0) {
                ttlSeconds = 1;
            }
            redisTemplate.opsForValue().set(redisKey(state.getTransactionId()), raw, Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to persist WebAuthn ceremony state", e);
        }
    }

    private Optional<WebAuthnCeremonyState> find(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return Optional.empty();
        }

        String raw = redisTemplate.opsForValue().get(redisKey(transactionId));
        if (raw == null) {
            return Optional.empty();
        }

        try {
            WebAuthnCeremonyState state = objectMapper.readValue(raw, WebAuthnCeremonyState.class);
            if (state.getExpiresAt() != null && state.getExpiresAt().isBefore(Instant.now())) {
                delete(transactionId);
                return Optional.empty();
            }
            return Optional.of(state);
        } catch (JsonProcessingException e) {
            log.warn("Unable to parse stored WebAuthn ceremony state for {}", transactionId, e);
            delete(transactionId);
            return Optional.empty();
        }
    }

    private String redisKey(String txId) {
        return KEY_PREFIX + txId;
    }
}
