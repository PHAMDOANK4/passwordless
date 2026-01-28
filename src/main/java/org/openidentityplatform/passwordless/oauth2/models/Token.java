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
 * OAuth2 Token Entity
 * Stores access tokens, refresh tokens, and ID tokens
 */
@Entity
@Table(name = "oauth_tokens", indexes = {
    @Index(name = "idx_token_user", columnList = "user_id"),
    @Index(name = "idx_token_value", columnList = "token_value"),
    @Index(name = "idx_token_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Token {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 20)
    private TokenType tokenType;
    
    @Column(name = "token_value", nullable = false, length = 2048)
    private String tokenValue;  // JWT token or encrypted refresh token
    
    @Column(name = "scopes", length = 500)
    private String scopes;  // Space-separated scopes
    
    @Column(name = "client_id", length = 100)
    private String clientId;  // OAuth client that issued this token
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    @Column(name = "revoked")
    private boolean revoked = false;
    
    @Column(name = "revoked_at")
    private Instant revokedAt;
    
    @Column(name = "device_info", length = 500)
    private String deviceInfo;  // User agent, device type
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;  // IPv4 or IPv6
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    
    public enum TokenType {
        ACCESS,     // Short-lived access token (15 minutes - 1 hour)
        REFRESH,    // Long-lived refresh token (days to months)
        ID          // OpenID Connect ID token
    }
    
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
