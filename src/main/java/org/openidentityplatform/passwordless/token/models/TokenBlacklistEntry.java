package org.openidentityplatform.passwordless.token.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "token_blacklist", indexes = {
        @Index(name = "idx_blacklist_jti", columnList = "jti", unique = true),
        @Index(name = "idx_blacklist_expires", columnList = "expires_at")
})
@Data
public class TokenBlacklistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "jti", nullable = false, unique = true, length = 100)
    private String jti;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    @Column(name = "reason", length = 255)
    private String reason;

    @PrePersist
    protected void onCreate() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }
}
