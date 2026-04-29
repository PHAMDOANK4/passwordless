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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enables @PreAuthorize / @Secured on controller methods
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final ObjectProvider<ApiKeyAuthenticationFilter> apiKeyAuthenticationFilterProvider;
    private final ObjectProvider<JwtAuthenticationFilter> jwtAuthenticationFilterProvider;

    @Value("${security.admin.enabled:true}")
    private boolean adminSecurityEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // API layer remains stateless; IdP session is tracked by application-level
                // session records.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // Public endpoints (no auth required)
                    auth.requestMatchers("/apps/v1/**").permitAll();
                    auth.requestMatchers("/auth/**").permitAll();
                    auth.requestMatchers("/token/**").permitAll();
                    auth.requestMatchers("/oauth2/**").permitAll();
                    auth.requestMatchers("/recovery/**").permitAll();
                    auth.requestMatchers("/.well-known/**").permitAll();
                    auth.requestMatchers("/idp/**").permitAll();
                    auth.requestMatchers("/actuator/**").permitAll();
                    auth.requestMatchers("/webauthn/test", "/webauthn/test/**", "/webauthn/v1/**", "/js/**")
                            .permitAll();

                    // Admin endpoints — RBAC enforcement
                    if (adminSecurityEnabled) {
                        // Admin API: requires ADMIN or SUPER_ADMIN role
                        // (the JwtAuthenticationFilter populates SecurityContext with ROLE_ADMIN /
                        // ROLE_SUPER_ADMIN)
                        auth.requestMatchers("/admin/api/**").hasAnyRole("ADMIN", "SUPER_ADMIN");

                        // Admin static UI pages: permit access (frontend checks role via /admin/api/me)
                        auth.requestMatchers("/admin/**").permitAll();
                    } else {
                        // Development mode: permit all admin access (no token required)
                        auth.requestMatchers("/admin/**", "/admin/api/**").permitAll();
                    }

                    auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll();

                    auth.anyRequest().permitAll();
                });

        // Register JWT auth filter BEFORE UsernamePasswordAuthenticationFilter
        // This ensures admin API requests are authenticated via JWT before reaching
        // controllers
        if (adminSecurityEnabled) {
            JwtAuthenticationFilter jwtFilter = jwtAuthenticationFilterProvider.getIfAvailable();
            if (jwtFilter != null) {
                http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
            }
        }

        // Register existing API key filter for OTP/TOTP endpoints
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter = apiKeyAuthenticationFilterProvider.getIfAvailable();
        if (apiKeyAuthenticationFilter != null) {
            http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }
}
