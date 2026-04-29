package org.openidentityplatform.passwordless.apps.security;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openidentityplatform.passwordless.token.services.JwtTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT authentication filter for admin API endpoints.
 * <p>
 * Activates only for {@code /admin/api/**} paths. Extracts the {@code Authorization: Bearer <token>}
 * header, validates the JWT, and populates the Spring {@code SecurityContext} with the user's
 * identity and role-based granted authorities.
 * <p>
 * If the token is missing, invalid, or the user's role is not ADMIN/SUPER_ADMIN, the filter
 * returns 401/403 and blocks the request before it reaches the controller.
 */
@Component
@ConditionalOnBean(JwtTokenService.class)
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only apply to /admin/api/** endpoints
        return !path.startsWith("/admin/api");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing or invalid Authorization header. Expected: Bearer <token>");
            return;
        }

        String token = authHeader.substring("Bearer ".length()).trim();

        JWTClaimsSet claims;
        try {
            claims = jwtTokenService.validateAccessToken(token);
        } catch (IllegalArgumentException e) {
            log.warn("Admin JWT validation failed: {}", e.getMessage());
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or expired access token");
            return;
        }

        String userId = claims.getSubject();
        String email = claimAsString(claims.getClaim("email"));
        String role = claimAsString(claims.getClaim("role"));

        if (role == null || role.isBlank()) {
            log.warn("JWT for user {} is missing role claim", userId);
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Access denied. Token does not contain a role claim.");
            return;
        }

        // Only ADMIN and SUPER_ADMIN roles are allowed to access admin endpoints
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            log.warn("User {} with role {} attempted to access admin endpoint: {}",
                    userId, role, request.getRequestURI());
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Access denied. Insufficient privileges.");
            return;
        }

        // Build Spring Security authorities from the role
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

        // SUPER_ADMIN also gets ROLE_ADMIN (hierarchical)
        if ("SUPER_ADMIN".equals(role)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        // Create authentication token with user details
        AdminPrincipal principal = new AdminPrincipal(userId, email, role);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Store useful info in request attributes for downstream use
        request.setAttribute("adminUserId", userId);
        request.setAttribute("adminUserEmail", email);
        request.setAttribute("adminUserRole", role);

        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }

    private String claimAsString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    /**
     * Simple principal object carrying admin user info through the SecurityContext.
     */
    public record AdminPrincipal(String userId, String email, String role) {
    }
}
