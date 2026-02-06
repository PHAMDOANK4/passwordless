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
 * OAuth2 Authorization Code Entity
 * Represents a temporary code issued during the OAuth2 authorization code flow
 * This implements the centralized authentication flow:
 * 1. Ecommerce App redirects to AuthCentralA
 * 2. AuthCentralA authenticates user (OTP/WebAuthn/TOTP)
 * 3. AuthCentralA issues authorization code
 * 4. Ecommerce App exchanges code for tokens
 */
@Entity
@Table(name = "authorization_codes", indexes = {
    @Index(name = "idx_authcode_code", columnList = "code", unique = true),
    @Index(name = "idx_authcode_user", columnList = "user_id"),
    @Index(name = "idx_authcode_client", columnList = "client_id"),
    @Index(name = "idx_authcode_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationCode {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;  // The authorization code itself
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;  // User who was authenticated
    
    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;  // OAuth client requesting authentication
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oauth_client_id")
    private OAuthClient oauthClient;  // Link to full OAuth client entity
    
    @Column(name = "redirect_uri", nullable = false, length = 500)
    private String redirectUri;  // Where to redirect back after authentication
    
    @Column(name = "scopes", length = 500)
    private String scopes;  // Requested scopes (space-separated)
    
    @Column(name = "state", length = 255)
    private String state;  // Client state for CSRF protection
    
    @Column(name = "code_challenge", length = 255)
    private String codeChallenge;  // PKCE code challenge
    
    @Column(name = "code_challenge_method", length = 10)
    private String codeChallengeMethod;  // S256 or plain
    
    @Column(name = "nonce", length = 255)
    private String nonce;  // OpenID Connect nonce for ID token
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;  // Typically 10 minutes from creation
    
    @Column(name = "used")
    private boolean used = false;
    
    @Column(name = "used_at")
    private Instant usedAt;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;  // IP that initiated the auth request
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_method", length = 20)
    private AuthMethod authMethod;  // How user was authenticated
    
    public enum AuthMethod {
        WEBAUTHN,
        MAGIC_LINK,
        OTP,
        TOTP,
        SMS,
        EMAIL
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (expiresAt == null) {
            // Default 10 minutes expiry for authorization codes
            expiresAt = createdAt.plusSeconds(600);
        }
    }
    
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    public boolean isValid() {
        return !used && !isExpired();
    }
    
    public void markAsUsed() {
        this.used = true;
        this.usedAt = Instant.now();
    }
}
