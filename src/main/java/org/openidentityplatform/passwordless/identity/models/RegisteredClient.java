package org.openidentityplatform.passwordless.identity.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an external application (e.g. "Ecommerce A") that delegates
 * authentication to this AuthCentralA service.  Each registered client
 * receives a pair of credentials (appKey / appSecret) and must supply a
 * callback URI so that tokens can be delivered back after verification.
 */
@Entity
@Table(name = "registered_clients")
@Getter
@Setter
@NoArgsConstructor
public class RegisteredClient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rc_id", updatable = false, nullable = false)
    private UUID rcId;

    @Column(name = "app_key", unique = true, nullable = false, length = 128)
    private String appKey;

    @Column(name = "app_secret", nullable = false, length = 256)
    private String appSecret;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "callback_uri", nullable = false, length = 2048)
    private String callbackUri;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RegisteredClient(String appKey, String appSecret,
                            String displayName, String callbackUri) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.displayName = displayName;
        this.callbackUri = callbackUri;
    }
}
