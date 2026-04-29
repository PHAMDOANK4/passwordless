package org.openidentityplatform.passwordless.admin;

import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.token.models.SigningKey;
import org.openidentityplatform.passwordless.token.services.KeyManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for JWT signing key management.
 */
@RestController
@RequestMapping("/admin/api/keys")
@AllArgsConstructor
public class AdminKeyController {

    private final KeyManagementService keyManagementService;

    /**
     * List all signing keys (public info only — private key material is never exposed).
     */
    @GetMapping
    public List<Map<String, Object>> listKeys() {
        return keyManagementService.getAllPublicKeys().stream()
                .map(this::toPublicInfo)
                .toList();
    }

    /**
     * Trigger key rotation: generates a new ACTIVE key and marks the current one as INACTIVE.
     */
    @PostMapping("/rotate")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> rotateKey() {
        SigningKey newKey = keyManagementService.rotateKey();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "rotated");
        response.put("newKid", newKey.getKid());
        response.put("createdAt", newKey.getCreatedAt());
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toPublicInfo(SigningKey key) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", key.getId());
        info.put("kid", key.getKid());
        info.put("algorithm", key.getAlgorithm());
        info.put("keySize", key.getKeySize());
        info.put("status", key.getStatus().name());
        info.put("createdAt", key.getCreatedAt());
        info.put("rotatedAt", key.getRotatedAt());
        return info;
    }
}
