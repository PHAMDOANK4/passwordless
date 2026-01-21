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
import org.openidentityplatform.passwordless.apps.models.RegisteredApp;
import org.openidentityplatform.passwordless.apps.repositories.RegisteredAppRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class AppRegistrationService {
    
    private final RegisteredAppRepository appRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;
    
    @Transactional
    public RegisteredApp registerApp(String name, String description, Integer rateLimitPerMinute, Integer rateLimitPerHour) {
        if (appRepository.existsByName(name)) {
            throw new IllegalArgumentException("App with name '" + name + "' already exists");
        }
        
        String apiKey = generateApiKey();
        String apiKeyHash = passwordEncoder.encode(apiKey);
        
        RegisteredApp app = new RegisteredApp();
        app.setName(name);
        app.setDescription(description);
        app.setApiKey(apiKey); // Store temporarily to return to user
        app.setApiKeyHash(apiKeyHash);
        app.setActive(true);
        app.setRateLimitPerMinute(rateLimitPerMinute != null ? rateLimitPerMinute : 60);
        app.setRateLimitPerHour(rateLimitPerHour != null ? rateLimitPerHour : 1000);
        
        RegisteredApp savedApp = appRepository.save(app);
        log.info("Registered new app: {} with id: {}", name, savedApp.getId());
        
        return savedApp;
    }
    
    public Optional<RegisteredApp> findById(String id) {
        return appRepository.findById(id);
    }
    
    public Optional<RegisteredApp> findByName(String name) {
        return appRepository.findByName(name);
    }
    
    public List<RegisteredApp> listAllApps() {
        return appRepository.findAll();
    }
    
    @Transactional
    public boolean validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }
        
        // Hash the provided API key
        List<RegisteredApp> apps = appRepository.findAll();
        
        for (RegisteredApp app : apps) {
            if (app.isActive() && passwordEncoder.matches(apiKey, app.getApiKeyHash())) {
                // Update last used timestamp
                app.setLastUsedAt(Instant.now());
                appRepository.save(app);
                return true;
            }
        }
        
        return false;
    }
    
    @Transactional
    public Optional<RegisteredApp> getAppByApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return Optional.empty();
        }
        
        List<RegisteredApp> apps = appRepository.findAll();
        
        for (RegisteredApp app : apps) {
            if (app.isActive() && passwordEncoder.matches(apiKey, app.getApiKeyHash())) {
                app.setLastUsedAt(Instant.now());
                appRepository.save(app);
                return Optional.of(app);
            }
        }
        
        return Optional.empty();
    }
    
    @Transactional
    public void deactivateApp(String id) {
        RegisteredApp app = appRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("App not found with id: " + id));
        app.setActive(false);
        appRepository.save(app);
        log.info("Deactivated app: {}", app.getName());
    }
    
    @Transactional
    public void activateApp(String id) {
        RegisteredApp app = appRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("App not found with id: " + id));
        app.setActive(true);
        appRepository.save(app);
        log.info("Activated app: {}", app.getName());
    }
    
    @Transactional
    public void deleteApp(String id) {
        appRepository.deleteById(id);
        log.info("Deleted app with id: {}", id);
    }
    
    @Transactional
    public String regenerateApiKey(String id) {
        RegisteredApp app = appRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("App not found with id: " + id));
        
        String newApiKey = generateApiKey();
        String newApiKeyHash = passwordEncoder.encode(newApiKey);
        
        app.setApiKey(newApiKey);
        app.setApiKeyHash(newApiKeyHash);
        appRepository.save(app);
        
        log.info("Regenerated API key for app: {}", app.getName());
        return newApiKey;
    }
    
    private String generateApiKey() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return "pk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
