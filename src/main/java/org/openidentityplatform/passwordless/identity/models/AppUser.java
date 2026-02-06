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
 * Central identity record that links all authentication modules together.
 * Every OTP session, TOTP registration, and WebAuthn credential
 * references back to a single AppUser row via its primary key.
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "handle", unique = true, nullable = false, length = 320)
    private String handle;

    @Column(name = "mail_addr", length = 320)
    private String mailAddr;

    @Column(name = "phone_num", length = 30)
    private String phoneNum;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt = Instant.now();

    @Column(name = "modified_at")
    private Instant modifiedAt = Instant.now();

    public AppUser(String handle, String mailAddr, String phoneNum) {
        this.handle = handle;
        this.mailAddr = mailAddr;
        this.phoneNum = phoneNum;
        this.registeredAt = Instant.now();
        this.modifiedAt = Instant.now();
    }
}
