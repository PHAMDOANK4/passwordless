package org.openidentityplatform.passwordless.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.openidentityplatform.passwordless.oauth2.dto.OAuthClientAdminResponse;
import org.openidentityplatform.passwordless.oauth2.dto.OAuthClientCreateRequest;
import org.openidentityplatform.passwordless.oauth2.dto.OAuthClientSecretResponse;
import org.openidentityplatform.passwordless.oauth2.dto.OAuthClientUpdateRequest;
import org.openidentityplatform.passwordless.oauth2.services.OAuth2ClientManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/admin/api/oauth2/clients")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminOAuthClientController {

    private final OAuth2ClientManagementService clientManagementService;

    @GetMapping
    public ResponseEntity<List<OAuthClientAdminResponse>> listClients() {
        return ResponseEntity.ok(clientManagementService.listClients());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getClient(@PathVariable String id) {
        try {
            return ResponseEntity.ok(clientManagementService.getClient(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "OAuth client not found"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createClient(@RequestBody @Valid OAuthClientCreateRequest request) {
        try {
            OAuthClientSecretResponse response = clientManagementService.createClient(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().contains("already exists")
                    ? HttpStatus.CONFLICT
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateClient(@PathVariable String id, @RequestBody OAuthClientUpdateRequest request) {
        try {
            OAuthClientAdminResponse response = clientManagementService.updateClient(id, request);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "OAuth client not found"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/rotate-secret")
    public ResponseEntity<?> rotateSecret(@PathVariable String id) {
        try {
            OAuthClientSecretResponse response = clientManagementService.rotateSecret(id);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "OAuth client not found"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<?> activateClient(@PathVariable String id) {
        try {
            clientManagementService.activateClient(id);
            return ResponseEntity.ok(Map.of("status", "activated"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "OAuth client not found"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateClient(@PathVariable String id) {
        try {
            clientManagementService.deactivateClient(id);
            return ResponseEntity.ok(Map.of("status", "deactivated"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "OAuth client not found"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> deleteClient(@PathVariable String id) {
        try {
            clientManagementService.deleteClient(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "OAuth client not found"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
