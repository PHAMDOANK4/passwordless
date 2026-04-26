package org.openidentityplatform.passwordless.oauth2.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.auth.configuration.AuthSessionCookie;
import org.openidentityplatform.passwordless.oauth2.dto.OAuth2AuthorizeRequest;
import org.openidentityplatform.passwordless.oauth2.dto.OAuth2AuthorizeResponse;
import org.openidentityplatform.passwordless.oauth2.dto.OAuth2TokenResponse;
import org.openidentityplatform.passwordless.oauth2.services.OAuth2AuthorizationService;
import org.openidentityplatform.passwordless.oauth2.services.OAuth2FlowException;
import org.openidentityplatform.passwordless.oauth2.services.OAuth2TokenManagementService;
import org.openidentityplatform.passwordless.oauth2.services.OAuth2TokenService;
import org.openidentityplatform.passwordless.oauth2.services.OAuth2UserInfoService;
import org.openidentityplatform.passwordless.token.services.InvalidRefreshTokenException;
import org.openidentityplatform.passwordless.token.services.JwtTokenService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CookieValue;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor
@RequestMapping("/oauth2")
public class OAuth2Controller {

    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenService tokenService;
    private final OAuth2TokenManagementService tokenManagementService;
    private final OAuth2UserInfoService userInfoService;
    private final JwtTokenService jwtTokenService;

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @RequestParam("response_type") String responseType,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
            @RequestParam(value = "nonce", required = false) String nonce,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @CookieValue(value = AuthSessionCookie.NAME, required = false) String sessionCookie,
            HttpServletRequest request
    ) throws OAuth2FlowException {
        String redirect = authorizationService.authorize(
                authorization,
                sessionCookie,
                responseType,
                clientId,
                redirectUri,
                scope,
                state,
                codeChallenge,
                codeChallengeMethod,
                nonce,
                request
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(redirect));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @PostMapping(value = "/authorize", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public OAuth2AuthorizeResponse authorizeJson(
            @RequestBody @Valid OAuth2AuthorizeRequest authorizeRequest,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @CookieValue(value = AuthSessionCookie.NAME, required = false) String sessionCookie,
            HttpServletRequest request
    ) throws OAuth2FlowException {
        String redirect = authorizationService.authorize(
                authorization,
            sessionCookie,
                authorizeRequest.getResponseType(),
                authorizeRequest.getClientId(),
                authorizeRequest.getRedirectUri(),
                authorizeRequest.getScope(),
                authorizeRequest.getState(),
                authorizeRequest.getCodeChallenge(),
                authorizeRequest.getCodeChallengeMethod(),
                authorizeRequest.getNonce(),
                request
        );

        URI redirectUri = URI.create(redirect);
        Map<String, String> queryValues = parseQuery(redirectUri.getRawQuery());
        return new OAuth2AuthorizeResponse(
                redirect,
                queryValues.get("code"),
                queryValues.get("state")
        );
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public OAuth2TokenResponse token(@RequestParam Map<String, String> formParams, HttpServletRequest request)
            throws OAuth2FlowException, InvalidRefreshTokenException {
        return tokenService.token(formParams, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PostMapping(value = "/introspect", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> introspect(@RequestParam Map<String, String> formParams) throws OAuth2FlowException {
        return tokenManagementService.introspect(formParams);
    }

    @PostMapping(value = "/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> revoke(@RequestParam Map<String, String> formParams) throws OAuth2FlowException {
        tokenManagementService.revoke(formParams);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/userinfo")
    public Map<String, Object> userInfo(@RequestHeader(value = "Authorization", required = false) String authorization)
            throws OAuth2FlowException {
        return userInfoService.userInfo(authorization);
    }

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

    @GetMapping("/jwks")
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

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }

        Arrays.stream(rawQuery.split("&"))
                .filter(part -> !part.isBlank())
                .forEach(part -> {
                    String[] pieces = part.split("=", 2);
                    String key = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8);
                    String value = pieces.length > 1
                            ? URLDecoder.decode(pieces[1], StandardCharsets.UTF_8)
                            : "";
                    values.put(key, value);
                });
        return values;
    }
}
