package org.openidentityplatform.passwordless.magiclink.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Magic Link Entity
 * One-time tokens for passwordless login or account recovery
 */
@Entity
@Table(name = "magic_links", indexes = {
    @Index(name = "idx_magic_token", columnList = "token", unique = true),
    @Index(name = "idx_magic_email", columnList = "email"),
    @Index(name = "idx_magic_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MagicLink {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "token", nullable = false, unique = true, length = 100)
    private String token;  // Random token (UUID or secure random string)
    
    @Column(name = "email", nullable = false, length = 255)
    private String email;  // User email
    
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private Purpose purpose;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    @Column(name = "used")
    private boolean used = false;
    
    @Column(name = "used_at")
    private Instant usedAt;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;  // IP that requested the link
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "attempts")
    private int attempts = 0;  // Number of verification attempts
    
    @Column(name = "max_attempts")
    private int maxAttempts = 3;  // Maximum allowed attempts
    
    public enum Purpose {
        LOGIN,          // Regular login
        RECOVERY,       // Account recovery / password reset equivalent
        ENROLLMENT,     // Initial device enrollment
        VERIFICATION    // Email verification
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (expiresAt == null) {
            // Default 15 minutes expiry
            expiresAt = createdAt.plusSeconds(900);
        }
    }
    
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    public boolean isValid() {
        return !used && !isExpired() && attempts < maxAttempts;
    }
    
    public void incrementAttempts() {
        this.attempts++;
    }
    
    public void markAsUsed() {
        this.used = true;
        this.usedAt = Instant.now();
    }
}
