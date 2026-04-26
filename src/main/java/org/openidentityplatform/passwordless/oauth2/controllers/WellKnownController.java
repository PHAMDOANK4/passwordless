package org.openidentityplatform.passwordless.oauth2.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.token.services.JwtTokenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor
public class WellKnownController {

    private final JwtTokenService jwtTokenService;

    @GetMapping("/.well-known/openid-configuration")
    public Map<String, Object> openIdConfiguration(HttpServletRequest request) {
        String issuer = resolveIssuer(request);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("issuer", issuer);
        config.put("authorization_endpoint", issuer + "/oauth2/authorize");
        config.put("token_endpoint", issuer + "/oauth2/token");
        config.put("userinfo_endpoint", issuer + "/oauth2/userinfo");
        config.put("jwks_uri", issuer + "/.well-known/jwks.json");
        config.put("introspection_endpoint", issuer + "/oauth2/introspect");
        config.put("revocation_endpoint", issuer + "/oauth2/revoke");
        config.put("end_session_endpoint", issuer + "/auth/logout");
        config.put("response_types_supported", List.of("code"));
        config.put("grant_types_supported", List.of("authorization_code", "refresh_token", "client_credentials"));
        config.put("subject_types_supported", List.of("public"));
        config.put("id_token_signing_alg_values_supported", List.of("RS256"));
        config.put("scopes_supported", List.of("openid", "profile", "email", "api.read", "api.write"));
        config.put("token_endpoint_auth_methods_supported", List.of("client_secret_post", "none"));
        config.put("introspection_endpoint_auth_methods_supported", List.of("client_secret_post"));
        config.put("revocation_endpoint_auth_methods_supported", List.of("client_secret_post"));
        config.put("code_challenge_methods_supported", List.of("S256"));
        config.put("claims_supported", List.of("sub", "email", "name", "preferred_username", "nonce"));
        return config;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwtTokenService.getJwks();
    }

    private String resolveIssuer(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        return scheme + "://" + host;
    }
}
