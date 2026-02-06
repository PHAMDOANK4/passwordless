package org.openidentityplatform.passwordless.identity.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Captures the access grant (bearer token) that AuthCentralA hands back
 * to the calling application after the user passes OTP / TOTP / WebAuthn
 * verification.  The grant references both the authenticated {@link AppUser}
 * and the {@link RegisteredClient} that requested the authentication,
 * creating a logical three-way link among all tables.
 */
@Entity
@Table(name = "issued_grants")
@Getter
@Setter
@NoArgsConstructor
public class IssuedAccessGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "grant_id", updatable = false, nullable = false)
    private UUID grantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
                referencedColumnName = "user_id")
    private AppUser appUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rc_id", nullable = false,
                referencedColumnName = "rc_id")
    private RegisteredClient registeredClient;

    @Column(name = "bearer_token", unique = true, nullable = false, length = 512)
    private String bearerToken;

    @Column(name = "refresh_token", unique = true, length = 512)
    private String refreshToken;

    @Column(name = "auth_method", nullable = false, length = 30)
    private String authMethod;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt = Instant.now();

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    public IssuedAccessGrant(AppUser appUser,
                             RegisteredClient registeredClient,
                             String bearerToken,
                             String refreshToken,
                             String authMethod,
                             Instant validUntil) {
        this.appUser = appUser;
        this.registeredClient = registeredClient;
        this.bearerToken = bearerToken;
        this.refreshToken = refreshToken;
        this.authMethod = authMethod;
        this.validUntil = validUntil;
    }
}
