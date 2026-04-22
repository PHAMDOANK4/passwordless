package org.openidentityplatform.passwordless.token.services;

import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.token.models.TokenBlacklistEntry;
import org.openidentityplatform.passwordless.token.repositories.TokenBlacklistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@AllArgsConstructor
public class TokenBlacklistService {

    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Transactional
    public void blacklist(String jti, String subject, Instant expiresAt, String reason) {
        if (tokenBlacklistRepository.existsByJti(jti)) {
            return;
        }
        TokenBlacklistEntry entry = new TokenBlacklistEntry();
        entry.setJti(jti);
        entry.setSubject(subject);
        entry.setExpiresAt(expiresAt);
        entry.setReason(reason);
        tokenBlacklistRepository.save(entry);
    }

    public boolean isBlacklisted(String jti) {
        return tokenBlacklistRepository.existsByJti(jti);
    }

    @Transactional
    public void cleanupExpired() {
        tokenBlacklistRepository.deleteByExpiresAtBefore(Instant.now());
    }
}
