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

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration class for OpenAPI/Swagger documentation
 * 
 * Provides comprehensive API documentation for the Passwordless Authentication Service.
 * Swagger UI is accessible at: /swagger-ui.html
 * OpenAPI JSON spec is at: /v3/api-docs
 */
@Configuration
public class OpenApiConfiguration {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        // Define API Key security scheme
        final String securitySchemeName = "X-API-Key";
        
        return new OpenAPI()
                .info(new Info()
                        .title("Passwordless Authentication API")
                        .version("1.0.0")
                        .description("""
                                # Passwordless Authentication Service
                                
                                Centralized authentication platform supporting multiple passwordless methods:
                                
                                ## Supported Authentication Methods
                                
                                * **WebAuthn/FIDO2** - Biometric authentication (Touch ID, Face ID, Windows Hello)
                                * **OTP** - One-Time Password via SMS or Email
                                * **TOTP** - Time-based OTP (Google Authenticator, Authy)
                                * **Magic Links** - Email-based passwordless login
                                
                                ## Key Features
                                
                                * Multi-tenant support with domain isolation
                                * API key authentication for server-to-server integration
                                * Rate limiting and abuse protection
                                * Comprehensive audit logging
                                * Production-ready security
                                
                                ## Getting Started
                                
                                1. **Register an Application**: Use `/apps/v1/register` to get an API key
                                2. **Choose Authentication Method**: WebAuthn (no API key), OTP/TOTP (requires API key)
                                3. **Integrate**: Follow API documentation below
                                
                                ## Authentication
                                
                                * **WebAuthn endpoints** (`/webauthn/v1/*`): No API key required (browser-based)
                                * **OTP/TOTP endpoints** (`/otp/v1/*`, `/totp/v1/*`): Requires `X-API-Key` header
                                * **App management** (`/apps/v1/*`): No API key for registration, required for management
                                
                                ## Documentation Links
                                
                                * [GitHub Repository](https://github.com/PHAMDOANK4/passwordless)
                                * [Quick Start Guide](https://github.com/PHAMDOANK4/passwordless/blob/main/QUICK_CURL_GUIDE.md)
                                * [WebAuthn Setup Guide](https://github.com/PHAMDOANK4/passwordless/blob/main/docs/WEBAUTHN_PRODUCTION_SETUP_VI.md)
                                """)
                        .contact(new Contact()
                                .name("Passwordless Project")
                                .url("https://github.com/PHAMDOANK4/passwordless")
                                .email("support@example.com"))
                        .license(new License()
                                .name("Apache License 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server"),
                        new Server()
                                .url("https://authentication.k4.vn")
                                .description("Production Server")))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("""
                                                API Key for authentication. Required for OTP and TOTP endpoints.
                                                
                                                **How to get an API Key:**
                                                1. Register your application: `POST /apps/v1/register`
                                                2. Copy the `apiKey` from the response
                                                3. Add it to the `X-API-Key` header in your requests
                                                
                                                **Example:**
                                                ```
                                                X-API-Key: pk_aGVsbG93b3JsZA...
                                                ```
                                                
                                                **Note:** WebAuthn endpoints do NOT require API key.
                                                """)))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName));
    }
}
