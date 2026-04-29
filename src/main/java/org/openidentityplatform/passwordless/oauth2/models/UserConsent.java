package org.openidentityplatform.passwordless.oauth2.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.openidentityplatform.passwordless.iam.models.User;

import java.time.Instant;

/**
 * Records a user's consent to allow an OAuth2 client access to specific scopes.
 * Once consent is granted, subsequent authorization requests for the same client
 * and scopes are auto-approved (no consent screen shown).
 */
@Entity
@Table(name = "user_consents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_client", columnNames = {"user_id", "client_id"})
}, indexes = {
        @Index(name = "idx_consent_user", columnList = "user_id"),
        @Index(name = "idx_consent_client", columnList = "client_id")
})
@Getter
@Setter
@NoArgsConstructor
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "scopes", nullable = false, length = 500)
    private String scopes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

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
