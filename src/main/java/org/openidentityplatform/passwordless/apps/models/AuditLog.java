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
import java.time.Instant;

@Data
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_app_id", columnList = "app_id"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_event_type", columnList = "event_type")
})
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "app_id")
    private String appId;
    
    @Column(name = "app_name")
    private String appName;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @Column(name = "endpoint")
    private String endpoint;
    
    @Column(name = "http_method")
    private String httpMethod;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "success")
    private boolean success;
    
    @Column(name = "error_message", length = 500)
    private String errorMessage;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
