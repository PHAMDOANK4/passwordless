package org.openidentityplatform.passwordless.oauth2.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.auth.configuration.AuthSessionCookie;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.oauth2.models.OAuthClient;
import org.openidentityplatform.passwordless.oauth2.models.Session;
import org.openidentityplatform.passwordless.oauth2.repositories.OAuthClientRepository;
import org.openidentityplatform.passwordless.oauth2.services.AuthorizationRequestCache;
import org.openidentityplatform.passwordless.oauth2.services.ConsentService;
import org.openidentityplatform.passwordless.oauth2.services.OAuth2FlowException;
import org.openidentityplatform.passwordless.oauth2.services.SessionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles the OAuth2 consent screen flow.
 * <ul>
 *   <li>GET /oauth2/consent?request_id={id} — Renders the consent page</li>
 *   <li>POST /oauth2/consent/approve — Saves consent and redirects to authorize callback</li>
 *   <li>POST /oauth2/consent/deny — Redirects to client with error=access_denied</li>
 * </ul>
 */
@Controller
@RequestMapping("/oauth2/consent")
@AllArgsConstructor
public class ConsentController {

    private final ConsentService consentService;
    private final SessionService sessionService;
    private final AuthorizationRequestCache authorizationRequestCache;
    private final OAuthClientRepository oAuthClientRepository;
    private final UserRepository userRepository;

    private static final Map<String, String> SCOPE_DESCRIPTIONS = Map.of(
            "openid", "Verify your identity",
            "profile", "Access your profile information (name, picture)",
            "email", "Access your email address",
            "api.read", "Read data on your behalf",
            "api.write", "Write data on your behalf"
    );

    /**
     * Renders the consent page showing client name and requested scopes.
     */
    @GetMapping
    public String consentPage(
            @RequestParam("request_id") String requestId,
            @CookieValue(value = AuthSessionCookie.NAME, required = false) String sessionCookie,
            Model model
    ) {
        Optional<Session> session = resolveSession(sessionCookie);
        if (session.isEmpty()) {
            model.addAttribute("error", "Not authenticated. Please log in first.");
            return "consent";
        }

        Optional<AuthorizationRequestCache.CachedAuthorizationRequest> cachedOpt =
                authorizationRequestCache.retrieve(requestId);
        if (cachedOpt.isEmpty()) {
            model.addAttribute("error", "Authorization request expired. Please try again.");
            return "consent";
        }

        AuthorizationRequestCache.CachedAuthorizationRequest cached = cachedOpt.get();
        Optional<OAuthClient> clientOpt = oAuthClientRepository.findByClientId(cached.getClientId());

        String clientName = clientOpt.map(OAuthClient::getClientName).orElse(cached.getClientId());
        List<ScopeInfo> scopes = parseScopeInfoList(cached.getScope());

        // Load User explicitly to avoid LazyInitializationException on Hibernate proxy
        String userEmail = userRepository.findById(session.get().getUser().getId())
                .map(User::getEmail)
                .orElse(null);

        model.addAttribute("requestId", requestId);
        model.addAttribute("clientName", clientName);
        model.addAttribute("scopes", scopes);
        model.addAttribute("userEmail", userEmail);
        return "consent";
    }

    /**
     * User approves the consent. Saves consent record and redirects to authorize callback.
     */
    @PostMapping("/approve")
    public ResponseEntity<Void> approve(
            @RequestParam("request_id") String requestId,
            @CookieValue(value = AuthSessionCookie.NAME, required = false) String sessionCookie
    ) throws OAuth2FlowException {
        Session session = resolveSession(sessionCookie)
                .orElseThrow(() -> new OAuth2FlowException("Not authenticated"));

        AuthorizationRequestCache.CachedAuthorizationRequest cached =
                authorizationRequestCache.retrieve(requestId)
                        .orElseThrow(() -> new OAuth2FlowException("Authorization request expired"));

        User user = userRepository.findById(session.getUser().getId())
                .orElseThrow(() -> new OAuth2FlowException("User not found"));

        consentService.saveConsent(user, cached.getClientId(), cached.getScope());

        String callbackUrl = "/oauth2/authorize/callback?oauth_request_id=" + urlEncode(requestId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, callbackUrl).build();
    }

    /**
     * User denies the consent. Redirects to client redirect_uri with error=access_denied.
     */
    @PostMapping("/deny")
    public ResponseEntity<Void> deny(
            @RequestParam("request_id") String requestId
    ) throws OAuth2FlowException {
        AuthorizationRequestCache.CachedAuthorizationRequest cached =
                authorizationRequestCache.retrieve(requestId)
                        .orElseThrow(() -> new OAuth2FlowException("Authorization request expired"));

        authorizationRequestCache.remove(requestId);

        StringBuilder redirect = new StringBuilder(cached.getRedirectUri())
                .append(cached.getRedirectUri().contains("?") ? "&" : "?")
                .append("error=access_denied")
                .append("&error_description=").append(urlEncode("User denied consent"));

        if (cached.getState() != null && !cached.getState().isBlank()) {
            redirect.append("&state=").append(urlEncode(cached.getState()));
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirect.toString()).build();
    }

    // ---------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------

    private Optional<Session> resolveSession(String sessionCookie) {
        if (sessionCookie == null || sessionCookie.isBlank()) {
            return Optional.empty();
        }
        return sessionService.findActiveSession(sessionCookie);
    }

    private List<ScopeInfo> parseScopeInfoList(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scopes.split("\\s+"))
                .filter(s -> !s.isBlank())
                .map(s -> new ScopeInfo(s, SCOPE_DESCRIPTIONS.getOrDefault(s, s)))
                .toList();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record ScopeInfo(String name, String description) {
    }
}
