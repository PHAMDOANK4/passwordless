/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openidentityplatform.passwordless.iam.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

/**
 * Represents a user account in the IAM system.
 * Each user belongs to a domain (organization).
 */
@Data
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_email", columnList = "email", unique = true),
        @Index(name = "idx_user_domain", columnList = "domain_id"),
        @Index(name = "idx_user_external_id", columnList = "external_id")
})
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    /**
     * User's email address (serves as username)
     * Format: user@domain.com
     */
    @Column(nullable = false, unique = true, length = 255)
    private String email;
    
    /**
     * Domain this user belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "domain_id", nullable = false)
    private Domain domain;
    
    /**
     * User's first name
     */
    @Column(name = "first_name", length = 100)
    private String firstName;
    
    /**
     * User's last name
     */
    @Column(name = "last_name", length = 100)
    private String lastName;
    
    /**
     * User's display name
     */
    @Column(name = "display_name", length = 255)
    private String displayName;
    
    /**
     * User's phone number
     */
    @Column(name = "phone_number", length = 50)
    private String phoneNumber;
    
    /**
     * External ID from external identity provider (for SSO)
     */
    @Column(name = "external_id", length = 255)
    private String externalId;
    
    /**
     * User status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;
    
    /**
     * Whether MFA is enabled for this user
     */
    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;
    
    /**
     * User's preferred MFA method
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_mfa_method", length = 20)
    private MfaMethod preferredMfaMethod;
    
    /**
     * User's role in the domain
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;
    
    /**
     * User's profile picture URL
     */
    @Column(name = "profile_picture_url", length = 500)
    private String profilePictureUrl;
    
    /**
     * User's locale/language preference
     */
    @Column(length = 10)
    private String locale = "en-US";
    
    /**
     * User's timezone
     */
    @Column(length = 50)
    private String timezone = "UTC";
    
    /**
     * Last login timestamp
     */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    
    /**
     * Last login IP address
     */
    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;
    
    /**
     * Failed login attempts counter
     */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;
    
    /**
     * Account locked until timestamp (for security)
     */
    @Column(name = "locked_until")
    private Instant lockedUntil;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (displayName == null && firstName != null && lastName != null) {
            displayName = firstName + " " + lastName;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    /**
     * Check if user account is locked
     */
    @Transient
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
    
    /**
     * Check if user is active and not locked
     */
    @Transient
    public boolean isActive() {
        return status == UserStatus.ACTIVE && !isLocked();
    }
    
    public enum UserStatus {
        ACTIVE,
        SUSPENDED,
        DELETED,
        PENDING_VERIFICATION
    }
    
    public enum UserRole {
        SUPER_ADMIN,      // System-wide admin
        DOMAIN_ADMIN,     // Domain administrator
        USER,             // Regular user
        GUEST             // Limited access user
    }
    
    public enum MfaMethod {
        TOTP,             // Time-based OTP (Google Authenticator)
        SMS,              // SMS OTP
        EMAIL,            // Email OTP
        WEBAUTHN          // WebAuthn/FIDO2
    }
}
