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

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.apps.models.*;
import org.openidentityplatform.passwordless.apps.services.AppRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/apps/v1")
@AllArgsConstructor
public class AppRegistrationController {
    
    private final AppRegistrationService appRegistrationService;
    
    @PostMapping("/register")
    public ResponseEntity<AppRegistrationResponse> registerApp(@RequestBody @Valid AppRegistrationRequest request) {
        try {
            RegisteredApp app = appRegistrationService.registerApp(
                request.getName(),
                request.getDescription(),
                request.getRateLimitPerMinute(),
                request.getRateLimitPerHour()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(AppRegistrationResponse.fromEntity(app));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
    
    @GetMapping("/list")
    public ResponseEntity<List<AppInfoResponse>> listApps() {
        List<RegisteredApp> apps = appRegistrationService.listAllApps();
        List<AppInfoResponse> response = apps.stream()
            .map(AppInfoResponse::fromEntity)
            .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<AppInfoResponse> getApp(@PathVariable String id) {
        return appRegistrationService.findById(id)
            .map(app -> ResponseEntity.ok(AppInfoResponse.fromEntity(app)))
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateApp(@PathVariable String id) {
        try {
            appRegistrationService.deactivateApp(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activateApp(@PathVariable String id) {
        try {
            appRegistrationService.activateApp(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApp(@PathVariable String id) {
        appRegistrationService.deleteApp(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/regenerate-key")
    public ResponseEntity<String> regenerateApiKey(@PathVariable String id) {
        try {
            String newApiKey = appRegistrationService.regenerateApiKey(id);
            return ResponseEntity.ok(newApiKey);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
