package org.openidentityplatform.passwordless.admin;

import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.apps.security.JwtAuthenticationFilter;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin authentication endpoint.
 * Used by the admin frontend to verify the current user's identity and role.
 */
@RestController
@RequestMapping("/admin/api")
@AllArgsConstructor
public class AdminAuthController {

    private final UserRepository userRepository;

    /**
     * Returns the current admin user's profile and role.
     * Called by the admin UI on page load to determine what features to show.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtAuthenticationFilter.AdminPrincipal principal)) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", principal.userId());
        response.put("email", principal.email());
        response.put("role", principal.role());

        // Enrich with full user details if available
        userRepository.findById(principal.userId()).ifPresent(user -> {
            response.put("displayName", user.getDisplayName());
            response.put("firstName", user.getFirstName());
            response.put("lastName", user.getLastName());
            response.put("status", user.getStatus().name());
        });

        return ResponseEntity.ok(response);
    }
}
