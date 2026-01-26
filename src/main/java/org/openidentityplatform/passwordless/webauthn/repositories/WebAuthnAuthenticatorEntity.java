package org.openidentityplatform.passwordless.webauthn.repositories;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "webauthn_authenticators", indexes = {
        @Index(name = "idx_username", columnList = "username"),
        @Index(name = "idx_credential_id", columnList = "credential_id")
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

}
