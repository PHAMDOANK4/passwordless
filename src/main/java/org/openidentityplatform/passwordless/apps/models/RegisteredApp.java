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

package org.openidentityplatform.passwordless.apps.models;

import jakarta.persistence.*;
import lombok.Data;
import org.openidentityplatform.passwordless.iam.models.Domain;

import java.time.Instant;

/**
 * Registered App Entity
 * Represents an application registered to use the authentication service
 * Linked to Domain for multi-tenant support
 */
@Data
@Entity
@Table(name = "registered_apps", indexes = {
    @Index(name = "idx_app_name", columnList = "name", unique = true),
    @Index(name = "idx_app_domain", columnList = "domain_id")
})
public class RegisteredApp {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(length = 1000)
    private String description;
    
    /**
     * Link to Domain entity for multi-tenant support
     * Optional for backward compatibility (can be null for global apps)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id")
    private Domain domain;
    
    @Transient
    private String apiKey;
    
    @Column(nullable = false)
    private String apiKeyHash;
    
    @Column(nullable = false)
    private boolean active = true;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    
    @Column(name = "rate_limit_per_minute")
    private Integer rateLimitPerMinute = 60;
    
    @Column(name = "rate_limit_per_hour")
    private Integer rateLimitPerHour = 1000;
    
    @Column(name = "allowed_origins", columnDefinition = "TEXT")
    private String allowedOrigins;  // Comma-separated list of allowed origins for CORS
    
    @Column(name = "created_by", length = 255)
    private String createdBy;  // User email who created this app
    
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
