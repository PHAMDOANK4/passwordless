package org.openidentityplatform.passwordless.oauth2.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.openidentityplatform.passwordless.iam.models.User;

import java.time.Instant;
import java.util.UUID;

/**
 * User Session Entity
 * Tracks active user sessions for SSO and session management
 */
@Entity
@Table(name = "user_sessions", indexes = {
    @Index(name = "idx_session_user", columnList = "user_id"),
    @Index(name = "idx_session_id", columnList = "session_id", unique = true),
    @Index(name = "idx_session_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Session {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;  // External session identifier
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "device_info", length = 500)
    private String deviceInfo;  // User agent, OS, browser
    
    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;  // Hash of device characteristics
    
    @Column(name = "ip_address", length = 45, nullable = false)
    private String ipAddress;
    
    @Column(name = "location", length = 200)
    private String location;  // City, Country from IP
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;
    
    @Column(name = "revoked")
    private boolean revoked = false;
    
    @Column(name = "revoked_at")
    private Instant revokedAt;
    
    @Column(name = "revoked_reason", length = 255)
    private String revokedReason;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_method", length = 20)
    private AuthMethod authMethod;
    
    @Column(name = "auth_level")
    private int authLevel = 1;  // 1 = single factor, 2 = MFA, etc.
    
    public enum AuthMethod {
        WEBAUTHN,
        MAGIC_LINK,
        OTP,
        TOTP,
        PUSH,
        DELEGATED  // From another IdP
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (lastActivityAt == null) {
            lastActivityAt = createdAt;
        }
    }
    
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    public boolean isActive() {
        return !revoked && !isExpired();
    }
    
    public void updateActivity() {
        this.lastActivityAt = Instant.now();
    }
}
