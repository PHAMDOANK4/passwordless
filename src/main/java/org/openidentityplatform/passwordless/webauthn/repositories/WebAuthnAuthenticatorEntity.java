package org.openidentityplatform.passwordless.webauthn.repositories;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.openidentityplatform.passwordless.iam.models.User;

import java.io.Serializable;
import java.time.Instant;

/**
 * WebAuthn Authenticator Entity
 * Stores WebAuthn/FIDO2 credentials for passwordless authentication
 * Linked to User for centralized authentication tracking
 */
@Entity
@Table(name = "webauthn_authenticators", indexes = {
        @Index(name = "idx_username", columnList = "username"),
        @Index(name = "idx_credential_id", columnList = "credential_id"),
        @Index(name = "idx_webauthn_user", columnList = "user_id")
})
@Getter
@Setter
public class WebAuthnAuthenticatorEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    @Column(name = "credential_id", nullable = false, length = 512)
    private String credentialId;

    @Column(name = "authenticator", nullable = false, columnDefinition = "TEXT")
    private String authenticator;

    @Column(name = "attestation_type", length = 50)
    private String attestationType;

    @Column(name = "transports", length = 255)
    private String transports;

    @Column(name = "counter")
    private Long counter;

    @Column(name = "backup_eligible")
    private Boolean backupEligible;

    @Column(name = "backed_up")
    private Boolean backedUp;

    @Column(name = "uv_initialized")
    private Boolean uvInitialized;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "device_name", length = 255)
    private String deviceName;
    
    /**
     * Link to User entity for centralized authentication
     * Optional because WebAuthn can be registered before full user setup
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

}
