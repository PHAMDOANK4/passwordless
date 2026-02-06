package org.openidentityplatform.passwordless.identity.controllers;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.identity.models.GrantRequestPayload;
import org.openidentityplatform.passwordless.identity.models.GrantResultPayload;
import org.openidentityplatform.passwordless.identity.services.AccessGrantService;
import org.openidentityplatform.passwordless.identity.services.AppUserNotFoundException;
import org.openidentityplatform.passwordless.identity.services.ClientAppNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * REST API that external applications (Ecommerce A, etc.) call after
 * a user completes passwordless authentication.
 *
 * Typical flow:
 *   POST /auth/v1/grant   – obtain bearer + refresh tokens
 *   GET  /auth/v1/check    – verify a previously issued bearer token
 */
@RestController
@AllArgsConstructor
@RequestMapping("/auth/v1")
public class AccessGrantController {

    private final AccessGrantService grantService;

    @PostMapping("/grant")
    public GrantResultPayload issueGrant(
            @RequestBody @Valid GrantRequestPayload body)
            throws ClientAppNotFoundException, AppUserNotFoundException {

        return grantService.produceGrant(
                body.getAppKey(),
                body.getUserHandle(),
                body.getAuthMethod()
        );
    }

    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkToken(
            @RequestParam("token") String token) {

        boolean stillValid = grantService.validateBearerToken(token);
        Map<String, Object> result = Collections.singletonMap("valid", stillValid);

        if (stillValid) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(401).body(result);
    }
}
