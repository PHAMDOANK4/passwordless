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

package org.openidentityplatform.passwordless.apps.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openidentityplatform.passwordless.apps.models.AuditLog;
import org.openidentityplatform.passwordless.apps.models.RegisteredApp;
import org.openidentityplatform.passwordless.apps.repositories.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class AuditLogService {
    
    private final AuditLogRepository auditLogRepository;
    
    @Async
    public void logAuthenticationAttempt(RegisteredApp app, String endpoint, String httpMethod, 
                                        String ipAddress, boolean success, String errorMessage) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setAppId(app != null ? app.getId() : null);
            auditLog.setAppName(app != null ? app.getName() : null);
            auditLog.setEventType("AUTHENTICATION");
            auditLog.setEndpoint(endpoint);
            auditLog.setHttpMethod(httpMethod);
            auditLog.setIpAddress(ipAddress);
            auditLog.setSuccess(success);
            auditLog.setErrorMessage(errorMessage);
            
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }
    
    @Async
    public void logApiRequest(RegisteredApp app, String endpoint, String httpMethod, String ipAddress) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setAppId(app.getId());
            auditLog.setAppName(app.getName());
            auditLog.setEventType("API_REQUEST");
            auditLog.setEndpoint(endpoint);
            auditLog.setHttpMethod(httpMethod);
            auditLog.setIpAddress(ipAddress);
            auditLog.setSuccess(true);
            
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }
    
    @Async
    public void logRateLimitExceeded(RegisteredApp app, String endpoint, String ipAddress) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setAppId(app.getId());
            auditLog.setAppName(app.getName());
            auditLog.setEventType("RATE_LIMIT_EXCEEDED");
            auditLog.setEndpoint(endpoint);
            auditLog.setIpAddress(ipAddress);
            auditLog.setSuccess(false);
            auditLog.setErrorMessage("Rate limit exceeded");
            
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }
    
    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }
    
    public Page<AuditLog> getAuditLogsByApp(String appId, Pageable pageable) {
        return auditLogRepository.findByAppId(appId, pageable);
    }
    
    public Page<AuditLog> getAuditLogsByEventType(String eventType, Pageable pageable) {
        return auditLogRepository.findByEventType(eventType, pageable);
    }
    
    public List<AuditLog> getAuditLogsInRange(Instant start, Instant end) {
        return auditLogRepository.findByCreatedAtBetween(start, end);
    }
    
    public long countRecentRequests(String appId, Instant after) {
        return auditLogRepository.countByAppIdAndCreatedAtAfter(appId, after);
    }
}
