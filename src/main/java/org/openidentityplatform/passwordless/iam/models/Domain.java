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
 * Represents an organization/domain in the IAM system.
 * Similar to how Google manages @gmail.com, @google.com domains,
 * or Microsoft manages @outlook.com, @microsoft.com domains.
 */
@Data
@Entity
@Table(name = "domains", indexes = {
        @Index(name = "idx_domain_name", columnList = "domain_name", unique = true)
})
public class Domain {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    /**
     * Domain name (e.g., "company.com", "organization.vn")
     * This is unique across the system
     */
    @Column(name = "domain_name", nullable = false, unique = true, length = 255)
    private String domainName;
    
    /**
     * Display name for the organization
     */
    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;
    
    /**
     * Description of the organization/domain
     */
    @Column(length = 1000)
    private String description;
    
    /**
     * Domain owner/admin email
     */
    @Column(name = "owner_email", nullable = false, length = 255)
    private String ownerEmail;
    
    /**
     * Whether the domain is active
     */
    @Column(nullable = false)
    private boolean active = true;
    
    /**
     * Whether MFA is required for all users in this domain
     */
    @Column(name = "require_mfa", nullable = false)
    private boolean requireMfa = false;
    
    /**
     * Whether SSO is enabled for this domain
     */
    @Column(name = "sso_enabled", nullable = false)
    private boolean ssoEnabled = false;
    
    /**
     * SSO provider configuration (SAML/OAuth2 config in JSON)
     */
    @Column(name = "sso_config", columnDefinition = "TEXT")
    private String ssoConfig;
    
    /**
     * Maximum number of users allowed in this domain (for licensing/quota)
     */
    @Column(name = "max_users")
    private Integer maxUsers;
    
    /**
     * Custom login page URL for this domain
     */
    @Column(name = "custom_login_url", length = 500)
    private String customLoginUrl;
    
    /**
     * Domain logo URL
     */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;
    
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
