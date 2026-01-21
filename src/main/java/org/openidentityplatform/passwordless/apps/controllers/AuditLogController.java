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

package org.openidentityplatform.passwordless.apps.controllers;

import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.apps.models.AuditLog;
import org.openidentityplatform.passwordless.apps.services.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/apps/v1/audit")
@AllArgsConstructor
public class AuditLogController {
    
    private final AuditLogService auditLogService;
    
    @GetMapping("/logs")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        
        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<AuditLog> logs = auditLogService.getAuditLogs(pageable);
        return ResponseEntity.ok(logs);
    }
    
    @GetMapping("/logs/app/{appId}")
    public ResponseEntity<Page<AuditLog>> getAuditLogsByApp(
            @PathVariable String appId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> logs = auditLogService.getAuditLogsByApp(appId, pageable);
        return ResponseEntity.ok(logs);
    }
    
    @GetMapping("/logs/event/{eventType}")
    public ResponseEntity<Page<AuditLog>> getAuditLogsByEventType(
            @PathVariable String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> logs = auditLogService.getAuditLogsByEventType(eventType, pageable);
        return ResponseEntity.ok(logs);
    }
    
    @GetMapping("/logs/range")
    public ResponseEntity<List<AuditLog>> getAuditLogsInRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        
        List<AuditLog> logs = auditLogService.getAuditLogsInRange(start, end);
        return ResponseEntity.ok(logs);
    }
    
    @GetMapping("/stats/{appId}")
    public ResponseEntity<Long> getRecentRequestCount(
            @PathVariable String appId,
            @RequestParam(defaultValue = "1") int hours) {
        
        Instant after = Instant.now().minusSeconds(hours * 3600L);
        long count = auditLogService.countRecentRequests(appId, after);
        return ResponseEntity.ok(count);
    }
}
