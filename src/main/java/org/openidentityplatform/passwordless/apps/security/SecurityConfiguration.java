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

package org.openidentityplatform.passwordless.apps.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {
    
    private final ObjectProvider<ApiKeyAuthenticationFilter> apiKeyAuthenticationFilterProvider;

    @Value("${security.relaxed-mode:true}")
    private boolean relaxedMode;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // API layer remains stateless; IdP session is tracked by application-level session records.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/apps/v1/**").permitAll();
                auth.requestMatchers("/auth/**").permitAll();
                auth.requestMatchers("/token/**").permitAll();
                auth.requestMatchers("/oauth2/**").permitAll();
                auth.requestMatchers("/.well-known/**").permitAll();
                auth.requestMatchers("/idp/**").permitAll();
                auth.requestMatchers("/actuator/**").permitAll();
                auth.requestMatchers("/webauthn/test", "/webauthn/test/**", "/webauthn/v1/**", "/js/**").permitAll();

                if (relaxedMode) {
                    auth.requestMatchers("/admin/**", "/admin/api/**").permitAll();
                    auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll();
                } else {
                    auth.requestMatchers("/admin/**", "/admin/api/**").denyAll();
                    auth.requestMatchers("/auth/register", "/auth/mfa/**", "/auth/sessions/**").denyAll();
                    auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").denyAll();
                }

                auth.anyRequest().permitAll();
            });

        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter = apiKeyAuthenticationFilterProvider.getIfAvailable();
        if (apiKeyAuthenticationFilter != null) {
            http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }
        
        return http.build();
    }
}
