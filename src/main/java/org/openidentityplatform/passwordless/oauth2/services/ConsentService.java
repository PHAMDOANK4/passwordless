package org.openidentityplatform.passwordless.oauth2.services;

import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.oauth2.models.UserConsent;
import org.openidentityplatform.passwordless.oauth2.repositories.UserConsentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages OAuth2 user consent records.
 * Consent is checked before issuing an authorization code — if the user
 * has not previously consented to the requested scopes for the client,
 * they must be redirected to a consent screen.
 */
@Service
@AllArgsConstructor
public class ConsentService {

    private final UserConsentRepository userConsentRepository;

    /**
     * Checks whether the user has already consented to all requested scopes for the client.
     *
     * @return true if full consent exists, false otherwise
     */
    public boolean hasConsent(String userId, String clientId, String requestedScopes) {
        Optional<UserConsent> consent = userConsentRepository.findByUserIdAndClientId(userId, clientId);
        if (consent.isEmpty()) {
            return false;
        }

        Set<String> approvedScopes = parseScopes(consent.get().getScopes());
        Set<String> requested = parseScopes(requestedScopes);

        return approvedScopes.containsAll(requested);
    }

    /**
     * Saves or updates the user's consent for a client with the given scopes.
     * If consent already exists, scopes are merged (union).
     */
    @Transactional
    public void saveConsent(User user, String clientId, String scopes) {
        Optional<UserConsent> existing = userConsentRepository.findByUserIdAndClientId(user.getId(), clientId);
        if (existing.isPresent()) {
            UserConsent consent = existing.get();
            Set<String> merged = parseScopes(consent.getScopes());
            merged.addAll(parseScopes(scopes));
            consent.setScopes(String.join(" ", merged));
            userConsentRepository.save(consent);
        } else {
            UserConsent consent = new UserConsent();
            consent.setUser(user);
            consent.setClientId(clientId);
            consent.setScopes(scopes);
            userConsentRepository.save(consent);
        }
    }

    /**
     * Revokes all consent for a user-client pair.
     */
    @Transactional
    public void revokeConsent(String userId, String clientId) {
        userConsentRepository.deleteByUserIdAndClientId(userId, clientId);
    }

    /**
     * Returns all consents for a user.
     */
    public List<UserConsent> getUserConsents(String userId) {
        return userConsentRepository.findByUserId(userId);
    }

    private Set<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return new java.util.LinkedHashSet<>();
        }
        return Arrays.stream(scopes.split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
