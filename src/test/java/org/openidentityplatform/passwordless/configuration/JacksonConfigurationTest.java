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

package org.openidentityplatform.passwordless.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JacksonConfigurationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testInstantSerialization() throws JsonProcessingException {
        // Given
        Instant now = Instant.now();
        Map<String, Object> data = new HashMap<>();
        data.put("createdAt", now);
        data.put("name", "test");

        // When
        String json = objectMapper.writeValueAsString(data);

        // Then
        assertNotNull(json);
        assertTrue(json.contains("createdAt"));
        assertTrue(json.contains("name"));
        assertFalse(json.contains("\"createdAt\":{")); // Should not be serialized as object
    }

    @Test
    void testInstantDeserialization() throws JsonProcessingException {
        // Given
        String json = "{\"createdAt\":\"2026-01-21T18:00:00Z\",\"name\":\"test\"}";

        // When
        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.readValue(json, Map.class);

        // Then
        assertNotNull(data);
        assertEquals("test", data.get("name"));
        assertNotNull(data.get("createdAt"));
    }
}
