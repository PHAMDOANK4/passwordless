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

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openidentityplatform.passwordless.apps.models.RegisteredApp;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }

    @Test
    void allowRequest_WithinLimit_ReturnsTrue() {
        // Given
        RegisteredApp app = new RegisteredApp();
        app.setId("app-id-123");
        app.setName("TestApp");
        app.setRateLimitPerMinute(5);
        app.setRateLimitPerHour(100);

        // When & Then
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimitService.allowRequest(app), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void allowRequest_ExceedsMinuteLimit_ReturnsFalse() {
        // Given
        RegisteredApp app = new RegisteredApp();
        app.setId("app-id-123");
        app.setName("TestApp");
        app.setRateLimitPerMinute(3);
        app.setRateLimitPerHour(100);

        // When
        assertTrue(rateLimitService.allowRequest(app)); // Request 1
        assertTrue(rateLimitService.allowRequest(app)); // Request 2
        assertTrue(rateLimitService.allowRequest(app)); // Request 3
        boolean fourthRequest = rateLimitService.allowRequest(app); // Request 4

        // Then
        assertFalse(fourthRequest, "Fourth request should be denied due to minute rate limit");
    }

    @Test
    void resetBuckets_ClearsBuckets() {
        // Given
        RegisteredApp app = new RegisteredApp();
        app.setId("app-id-123");
        app.setName("TestApp");
        app.setRateLimitPerMinute(2);
        app.setRateLimitPerHour(100);

        // Consume all requests
        assertTrue(rateLimitService.allowRequest(app));
        assertTrue(rateLimitService.allowRequest(app));
        assertFalse(rateLimitService.allowRequest(app));

        // When
        rateLimitService.resetBuckets(app.getId());

        // Then
        assertTrue(rateLimitService.allowRequest(app), "After reset, request should be allowed again");
    }

    @Test
    void allowRequest_DifferentApps_IndependentLimits() {
        // Given
        RegisteredApp app1 = new RegisteredApp();
        app1.setId("app1");
        app1.setRateLimitPerMinute(2);
        app1.setRateLimitPerHour(100);

        RegisteredApp app2 = new RegisteredApp();
        app2.setId("app2");
        app2.setRateLimitPerMinute(2);
        app2.setRateLimitPerHour(100);

        // When & Then
        assertTrue(rateLimitService.allowRequest(app1));
        assertTrue(rateLimitService.allowRequest(app1));
        assertFalse(rateLimitService.allowRequest(app1));

        // App2 should still have its own limit
        assertTrue(rateLimitService.allowRequest(app2));
        assertTrue(rateLimitService.allowRequest(app2));
        assertFalse(rateLimitService.allowRequest(app2));
    }
}
