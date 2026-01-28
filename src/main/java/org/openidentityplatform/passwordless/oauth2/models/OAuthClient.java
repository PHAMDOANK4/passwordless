package org.openidentityplatform.passwordless.oauth2.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.openidentityplatform.passwordless.iam.models.Domain;

import java.time.Instant;
import java.util.UUID;

/**
 * OAuth2 Client Entity
 * Represents an application that uses OAuth2 to authenticate users
 */
@Entity
@Table(name = "oauth_clients", indexes = {
    @Index(name = "idx_client_id", columnList = "client_id", unique = true),
    @Index(name = "idx_client_domain", columnList = "domain_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OAuthClient {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "client_id", nullable = false, unique = true, length = 100)
    private String clientId;
    
    @Column(name = "client_secret", nullable = false, length = 255)
    private String clientSecret;  // BCrypt hashed
    
    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id", nullable = false)
    private Domain domain;
    
    @Column(name = "redirect_uris", nullable = false, columnDefinition = "TEXT")
    private String redirectUris;  // Comma-separated list
    
    @Column(name = "allowed_scopes", length = 500)
    private String allowedScopes;  // Space-separated scopes
    
    @Column(name = "grant_types", length = 200)
    private String grantTypes;  // authorization_code, refresh_token, etc.
    
    @Column(name = "active", nullable = false)
    private boolean active = true;
    
    @Column(name = "require_pkce")
    private boolean requirePkce = true;  // Proof Key for Code Exchange
    
    @Column(name = "access_token_lifetime_seconds")
    private int accessTokenLifetimeSeconds = 3600;  // 1 hour default
    
    @Column(name = "refresh_token_lifetime_seconds")
    private int refreshTokenLifetimeSeconds = 2592000;  // 30 days default
    
    @Column(name = "id_token_lifetime_seconds")
    private int idTokenLifetimeSeconds = 3600;  // 1 hour default
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "created_by", length = 255)
    private String createdBy;  // User email who created this client
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
