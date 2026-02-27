package org.openidentityplatform.passwordless.totp.models;

import jakarta.persistence.*;
import lombok.Data;
import org.openidentityplatform.passwordless.iam.models.User;

/**
 * Registered TOTP Entity
 * Stores TOTP secret for Google Authenticator-style authentication
 * Linked to User for centralized authentication tracking
 */
@Entity(name = "registered_totps")
@Table(name = "registered_totps", indexes = {
    @Index(name = "idx_totp_username", columnList = "username", unique = true),
    @Index(name = "idx_totp_user", columnList = "user_id")
})
@Data
public class RegisteredTotp {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(unique = true, nullable = false)
    private String username;

    @Column
    private String secret; //TODO add encryption and decryption
    
    /**
     * Link to User entity for centralized authentication
     * Optional because TOTP can be registered before full user setup
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column
    private Long lastUsedStep;

}
