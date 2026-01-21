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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openidentityplatform.passwordless.apps.models.RegisteredApp;
import org.openidentityplatform.passwordless.apps.services.AppRegistrationService;
import org.openidentityplatform.passwordless.apps.services.AuditLogService;
import org.openidentityplatform.passwordless.apps.services.RateLimitService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
@AllArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    
    private static final String API_KEY_HEADER = "X-API-Key";
    private final AppRegistrationService appRegistrationService;
    private final RateLimitService rateLimitService;
    private final AuditLogService auditLogService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String ipAddress = getClientIpAddress(request);
        
        // Skip authentication for app registration endpoints
        if (path.startsWith("/apps/v1")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Skip authentication for non-API endpoints
        if (!isApiEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String apiKey = request.getHeader(API_KEY_HEADER);
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Missing API key for path: {} from IP: {}", path, ipAddress);
            auditLogService.logAuthenticationAttempt(null, path, request.getMethod(), 
                ipAddress, false, "Missing API key");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Missing API key. Please provide X-API-Key header.\"}");
            response.setContentType("application/json");
            return;
        }
        
        Optional<RegisteredApp> appOpt = appRegistrationService.getAppByApiKey(apiKey);
        
        if (appOpt.isEmpty()) {
            log.warn("Invalid API key for path: {} from IP: {}", path, ipAddress);
            auditLogService.logAuthenticationAttempt(null, path, request.getMethod(), 
                ipAddress, false, "Invalid API key");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Invalid API key\"}");
            response.setContentType("application/json");
            return;
        }
        
        RegisteredApp app = appOpt.get();
        
        // Check rate limit
        if (!rateLimitService.allowRequest(app)) {
            log.warn("Rate limit exceeded for app: {} from IP: {}", app.getName(), ipAddress);
            auditLogService.logRateLimitExceeded(app, path, ipAddress);
            response.setStatus(429); // Too Many Requests
            response.getWriter().write("{\"error\": \"Rate limit exceeded. Please try again later.\"}");
            response.setContentType("application/json");
            return;
        }
        
        // Log successful authentication
        auditLogService.logAuthenticationAttempt(app, path, request.getMethod(), 
            ipAddress, true, null);
        
        // Log API request
        auditLogService.logApiRequest(app, path, request.getMethod(), ipAddress);
        
        // Set authentication in context
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            app.getName(),
            null,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_APP"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        // Store app info in request attribute for later use
        request.setAttribute("registeredApp", app);
        
        filterChain.doFilter(request, response);
    }
    
    private boolean isApiEndpoint(String path) {
        return path.startsWith("/otp/v1") || 
               path.startsWith("/totp/v1") || 
               path.startsWith("/webauthn/v1");
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
