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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "App Management", description = "APIs for registering and managing applications. Register an app to get an API key for OTP/TOTP endpoints.")
public class AppRegistrationController {
    
    private final AppRegistrationService appRegistrationService;
    
    @PostMapping("/register")
    @Operation(
        summary = "Register a new application",
        description = """
            Register your application to get an API key. The API key is required for OTP and TOTP authentication endpoints.
            
            **Important:** Save the API key returned in the response - it cannot be retrieved later.
            
            **Rate Limits:** You can configure per-minute and per-hour rate limits for your app.
            """
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "App registered successfully", 
                    content = @Content(schema = @Schema(implementation = AppRegistrationResponse.class))),
        @ApiResponse(responseCode = "409", description = "App name already exists"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    public ResponseEntity<AppRegistrationResponse> registerApp(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Application registration details",
                required = true,
                content = @Content(schema = @Schema(implementation = AppRegistrationRequest.class))
            )
            @RequestBody @Valid AppRegistrationRequest request) {
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
    @Operation(
        summary = "List all registered applications",
        description = "Retrieve a list of all registered applications with their basic information."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AppInfoResponse.class)))
    })
    public ResponseEntity<List<AppInfoResponse>> listApps() {
        List<RegisteredApp> apps = appRegistrationService.listAllApps();
        List<AppInfoResponse> response = apps.stream()
            .map(AppInfoResponse::fromEntity)
            .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{id}")
    @Operation(
        summary = "Get application details",
        description = "Retrieve detailed information about a specific application by its ID."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "App found",
                    content = @Content(schema = @Schema(implementation = AppInfoResponse.class))),
        @ApiResponse(responseCode = "404", description = "App not found")
    })
    public ResponseEntity<AppInfoResponse> getApp(
            @Parameter(description = "Application ID", required = true)
            @PathVariable String id) {
        return appRegistrationService.findById(id)
            .map(app -> ResponseEntity.ok(AppInfoResponse.fromEntity(app)))
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/{id}/deactivate")
    @Operation(
        summary = "Deactivate an application",
        description = "Temporarily disable an application. The app's API key will stop working until reactivated."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "App deactivated successfully"),
        @ApiResponse(responseCode = "404", description = "App not found")
    })
    public ResponseEntity<Void> deactivateApp(
            @Parameter(description = "Application ID", required = true)
            @PathVariable String id) {
        try {
            appRegistrationService.deactivateApp(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PostMapping("/{id}/activate")
    @Operation(
        summary = "Activate an application",
        description = "Re-enable a previously deactivated application. The app's API key will start working again."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "App activated successfully"),
        @ApiResponse(responseCode = "404", description = "App not found")
    })
    public ResponseEntity<Void> activateApp(
            @Parameter(description = "Application ID", required = true)
            @PathVariable String id) {
        try {
            appRegistrationService.activateApp(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete an application",
        description = "Permanently delete an application. This action cannot be undone."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "App deleted successfully"),
        @ApiResponse(responseCode = "404", description = "App not found")
    })
    public ResponseEntity<Void> deleteApp(
            @Parameter(description = "Application ID", required = true)
            @PathVariable String id) {
        appRegistrationService.deleteApp(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/regenerate-key")
    @Operation(
        summary = "Regenerate API key",
        description = """
            Generate a new API key for the application. The old key will be immediately invalidated.
            
            **Important:** Save the new API key - it cannot be retrieved later.
            """
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "New API key generated",
                    content = @Content(schema = @Schema(implementation = String.class))),
        @ApiResponse(responseCode = "404", description = "App not found")
    })
    public ResponseEntity<String> regenerateApiKey(
            @Parameter(description = "Application ID", required = true)
            @PathVariable String id) {
        try {
            String newApiKey = appRegistrationService.regenerateApiKey(id);
            return ResponseEntity.ok(newApiKey);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
