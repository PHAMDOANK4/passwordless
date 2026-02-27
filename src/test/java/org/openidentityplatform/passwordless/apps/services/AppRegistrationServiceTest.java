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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openidentityplatform.passwordless.apps.models.RegisteredApp;
import org.openidentityplatform.passwordless.apps.repositories.RegisteredAppRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AppRegistrationServiceTest {

    @Mock
    private RegisteredAppRepository appRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private SecureRandom secureRandom;

    @InjectMocks
    private AppRegistrationService appRegistrationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void registerApp_Success() {
        // Given
        String appName = "TestApp";
        String description = "Test Description";
        Integer rateLimitPerMinute = 100;
        Integer rateLimitPerHour = 5000;

        when(appRepository.existsByName(appName)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedApiKey");
        doAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(0);
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) i;
            }
            return null;
        }).when(secureRandom).nextBytes(any(byte[].class));

        RegisteredApp savedApp = new RegisteredApp();
        savedApp.setId("app-id-123");
        savedApp.setName(appName);
        savedApp.setDescription(description);
        savedApp.setActive(true);
        savedApp.setRateLimitPerMinute(rateLimitPerMinute);
        savedApp.setRateLimitPerHour(rateLimitPerHour);

        when(appRepository.save(any(RegisteredApp.class))).thenReturn(savedApp);

        // When
        RegisteredApp result = appRegistrationService.registerApp(appName, description, rateLimitPerMinute, rateLimitPerHour);

        // Then
        assertNotNull(result);
        assertEquals(appName, result.getName());
        assertEquals(description, result.getDescription());
        assertTrue(result.isActive());
        assertEquals(rateLimitPerMinute, result.getRateLimitPerMinute());
        assertEquals(rateLimitPerHour, result.getRateLimitPerHour());

        verify(appRepository).existsByName(appName);
        verify(appRepository).save(any(RegisteredApp.class));
    }

    @Test
    void registerApp_DuplicateName_ThrowsException() {
        // Given
        String appName = "ExistingApp";
        when(appRepository.existsByName(appName)).thenReturn(true);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            appRegistrationService.registerApp(appName, "Description", 60, 1000);
        });

        verify(appRepository).existsByName(appName);
        verify(appRepository, never()).save(any(RegisteredApp.class));
    }

    @Test
    void findById_Success() {
        // Given
        String appId = "app-id-123";
        RegisteredApp app = new RegisteredApp();
        app.setId(appId);
        app.setName("TestApp");

        when(appRepository.findById(appId)).thenReturn(Optional.of(app));

        // When
        Optional<RegisteredApp> result = appRegistrationService.findById(appId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(appId, result.get().getId());
        verify(appRepository).findById(appId);
    }

    @Test
    void listAllApps_Success() {
        // Given
        RegisteredApp app1 = new RegisteredApp();
        app1.setId("app1");
        app1.setName("App1");

        RegisteredApp app2 = new RegisteredApp();
        app2.setId("app2");
        app2.setName("App2");

        List<RegisteredApp> apps = Arrays.asList(app1, app2);
        when(appRepository.findAll()).thenReturn(apps);

        // When
        List<RegisteredApp> result = appRegistrationService.listAllApps();

        // Then
        assertEquals(2, result.size());
        verify(appRepository).findAll();
    }

    @Test
    void validateApiKey_ValidKey_ReturnsTrue() {
        // Given
        String apiKey = "valid-api-key";
        RegisteredApp app = new RegisteredApp();
        app.setId("app-id-123");
        app.setName("TestApp");
        app.setActive(true);
        app.setApiKeyHash("hashedKey");

        when(appRepository.findAll()).thenReturn(Arrays.asList(app));
        when(passwordEncoder.matches(apiKey, app.getApiKeyHash())).thenReturn(true);
        when(appRepository.save(any(RegisteredApp.class))).thenReturn(app);

        // When
        boolean result = appRegistrationService.validateApiKey(apiKey);

        // Then
        assertTrue(result);
        verify(appRepository).save(any(RegisteredApp.class));
    }

    @Test
    void validateApiKey_InvalidKey_ReturnsFalse() {
        // Given
        String apiKey = "invalid-api-key";
        RegisteredApp app = new RegisteredApp();
        app.setActive(true);
        app.setApiKeyHash("hashedKey");

        when(appRepository.findAll()).thenReturn(Arrays.asList(app));
        when(passwordEncoder.matches(apiKey, app.getApiKeyHash())).thenReturn(false);

        // When
        boolean result = appRegistrationService.validateApiKey(apiKey);

        // Then
        assertFalse(result);
        verify(appRepository, never()).save(any(RegisteredApp.class));
    }

    @Test
    void deactivateApp_Success() {
        // Given
        String appId = "app-id-123";
        RegisteredApp app = new RegisteredApp();
        app.setId(appId);
        app.setName("TestApp");
        app.setActive(true);

        when(appRepository.findById(appId)).thenReturn(Optional.of(app));
        when(appRepository.save(any(RegisteredApp.class))).thenReturn(app);

        // When
        appRegistrationService.deactivateApp(appId);

        // Then
        verify(appRepository).findById(appId);
        verify(appRepository).save(any(RegisteredApp.class));
    }

    @Test
    void deactivateApp_NotFound_ThrowsException() {
        // Given
        String appId = "non-existent-id";
        when(appRepository.findById(appId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            appRegistrationService.deactivateApp(appId);
        });

        verify(appRepository).findById(appId);
        verify(appRepository, never()).save(any(RegisteredApp.class));
    }

    @Test
    void deleteApp_Success() {
        // Given
        String appId = "app-id-123";

        // When
        appRegistrationService.deleteApp(appId);

        // Then
        verify(appRepository).deleteById(appId);
    }

    @Test
    void regenerateApiKey_Success() {
        // Given
        String appId = "app-id-123";
        RegisteredApp app = new RegisteredApp();
        app.setId(appId);
        app.setName("TestApp");
        app.setApiKeyHash("oldHashedKey");

        when(appRepository.findById(appId)).thenReturn(Optional.of(app));
        when(passwordEncoder.encode(anyString())).thenReturn("newHashedKey");
        when(appRepository.save(any(RegisteredApp.class))).thenReturn(app);

        // When
        String newApiKey = appRegistrationService.regenerateApiKey(appId);

        // Then
        assertNotNull(newApiKey);
        assertTrue(newApiKey.startsWith("pk_"));
        verify(appRepository).findById(appId);
        verify(appRepository).save(any(RegisteredApp.class));
    }
}
