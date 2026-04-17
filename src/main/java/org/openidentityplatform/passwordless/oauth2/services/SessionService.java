package org.openidentityplatform.passwordless.oauth2.services;

import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.auth.configuration.AuthProperties;
import org.openidentityplatform.passwordless.auth.models.AuthMethod;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.oauth2.models.Session;
import org.openidentityplatform.passwordless.oauth2.repositories.SessionRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.TokenRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class SessionService {

    private static final String REDIS_SESSION_PREFIX = "session:active:";

    private final SessionRepository sessionRepository;
    private final TokenRepository tokenRepository;
    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;

    @Transactional
    public Session createSession(User user, String ipAddress, String userAgent, AuthMethod method) {
        Session session = new Session();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUser(user);
        session.setIpAddress(ipAddress != null ? ipAddress : "unknown");
        session.setDeviceInfo(userAgent);
        session.setDeviceFingerprint(fingerprint(ipAddress, userAgent));
        session.setCreatedAt(Instant.now());
        session.setLastActivityAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(authProperties.getSessionTtlSeconds()));
        session.setRevoked(false);
        session.setAuthMethod(toSessionAuthMethod(method));
        session.setAuthLevel(method == AuthMethod.WEBAUTHN ? 2 : 1);

        Session saved = sessionRepository.save(session);
        cacheSession(saved.getSessionId(), saved.getExpiresAt());
        return saved;
    }

    public boolean isSessionActive(String sessionId) {
        Boolean cached = redisTemplate.hasKey(redisKey(sessionId));
        if (Boolean.TRUE.equals(cached)) {
            return true;
        }

        Optional<Session> session = sessionRepository.findActiveSession(sessionId, Instant.now());
        session.ifPresent(value -> cacheSession(value.getSessionId(), value.getExpiresAt()));
        return session.isPresent();
    }

    public Optional<Session> findActiveSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        Optional<Session> session = sessionRepository.findActiveSession(sessionId, Instant.now());
        session.ifPresent(value -> cacheSession(value.getSessionId(), value.getExpiresAt()));
        return session;
    }

    public List<Session> findActiveSessionsByUser(User user) {
        return sessionRepository.findActiveSessions(user, Instant.now());
    }

    @Transactional
    public void revokeSession(String sessionId, String reason) {
        sessionRepository.revokeSession(sessionId, Instant.now(), reason == null ? "logout" : reason);
        tokenRepository.revokeAllBySessionId(sessionId, Instant.now());
        redisTemplate.delete(redisKey(sessionId));
    }

    @Transactional
    public int revokeAllUserSessions(User user, String reason) {
        Instant now = Instant.now();
        List<Session> activeSessions = sessionRepository.findActiveSessions(user, now);
        if (activeSessions.isEmpty()) {
            return 0;
        }

        sessionRepository.revokeAllUserSessions(user, now, reason == null ? "logout_all" : reason);
        tokenRepository.revokeAllUserTokens(user, now);
        activeSessions.forEach(session -> redisTemplate.delete(redisKey(session.getSessionId())));
        return activeSessions.size();
    }

    private void cacheSession(String sessionId, Instant expiresAt) {
        long ttlSeconds = Duration.between(Instant.now(), expiresAt).toSeconds();
        if (ttlSeconds <= 0) {
            ttlSeconds = 1;
        }
        redisTemplate.opsForValue().set(redisKey(sessionId), "1", Duration.ofSeconds(ttlSeconds));
    }

    private String redisKey(String sessionId) {
        return REDIS_SESSION_PREFIX + sessionId;
    }

    private Session.AuthMethod toSessionAuthMethod(AuthMethod method) {
        if (method == null) {
            return Session.AuthMethod.OTP;
        }
        return switch (method) {
            case OTP -> Session.AuthMethod.OTP;
            case TOTP -> Session.AuthMethod.TOTP;
            case WEBAUTHN -> Session.AuthMethod.WEBAUTHN;
        };
    }

    private String fingerprint(String ipAddress, String userAgent) {
        String raw = (ipAddress == null ? "" : ipAddress) + "|" + (userAgent == null ? "" : userAgent);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString();
        }
    }
}
