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

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.openidentityplatform.passwordless.apps.models.RegisteredApp;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {
    
    private final Map<String, Bucket> minuteBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> hourBuckets = new ConcurrentHashMap<>();
    
    public boolean allowRequest(RegisteredApp app) {
        String appId = app.getId();
        
        // Check minute rate limit
        Bucket minuteBucket = minuteBuckets.computeIfAbsent(appId, k -> createMinuteBucket(app));
        if (!minuteBucket.tryConsume(1)) {
            return false;
        }
        
        // Check hour rate limit
        Bucket hourBucket = hourBuckets.computeIfAbsent(appId, k -> createHourBucket(app));
        if (!hourBucket.tryConsume(1)) {
            return false;
        }
        
        return true;
    }
    
    private Bucket createMinuteBucket(RegisteredApp app) {
        Bandwidth limit = Bandwidth.classic(
            app.getRateLimitPerMinute(),
            Refill.intervally(app.getRateLimitPerMinute(), Duration.ofMinutes(1))
        );
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }
    
    private Bucket createHourBucket(RegisteredApp app) {
        Bandwidth limit = Bandwidth.classic(
            app.getRateLimitPerHour(),
            Refill.intervally(app.getRateLimitPerHour(), Duration.ofHours(1))
        );
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }
    
    public void resetBuckets(String appId) {
        minuteBuckets.remove(appId);
        hourBuckets.remove(appId);
    }
}
