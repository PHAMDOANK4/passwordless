package org.openidentityplatform.passwordless.token.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Persisted RSA signing key for JWT issuance.
 * Supports multiple keys via {@code kid} for seamless key rotation.
 * Private key is stored PEM-encoded (PKCS#8); public key is X.509 PEM.
 */
@Entity
@Table(name = "signing_keys", indexes = {
        @Index(name = "idx_signing_kid", columnList = "kid", unique = true),
        @Index(name = "idx_signing_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class SigningKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "kid", nullable = false, unique = true, length = 50)
    private String kid;

    @Column(name = "private_key", nullable = false, columnDefinition = "TEXT")
    private String privateKeyPem;

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKeyPem;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private KeyStatus status = KeyStatus.ACTIVE;

    @Column(name = "algorithm", nullable = false, length = 10)
    private String algorithm = "RS256";

    @Column(name = "key_size", nullable = false)
    private int keySize = 2048;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    public enum KeyStatus {
        ACTIVE,
        INACTIVE
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
