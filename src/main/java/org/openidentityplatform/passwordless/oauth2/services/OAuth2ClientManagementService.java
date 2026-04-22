package org.openidentityplatform.passwordless.oauth2.services;

import lombok.RequiredArgsConstructor;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.oauth2.dto.OAuthClientAdminResponse;
import org.openidentityplatform.passwordless.oauth2.dto.OAuthClientCreateRequest;
import org.openidentityplatform.passwordless.oauth2.dto.OAuthClientSecretResponse;
import org.openidentityplatform.passwordless.oauth2.dto.OAuthClientUpdateRequest;
import org.openidentityplatform.passwordless.oauth2.models.OAuthClient;
import org.openidentityplatform.passwordless.oauth2.repositories.OAuthClientRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuth2ClientManagementService {

    private static final String DEFAULT_DOMAIN = "default.com";
    private static final String ALPHANUM = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final OAuthClientRepository oAuthClientRepository;
    private final DomainRepository domainRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;

    @Transactional(readOnly = true)
    public List<OAuthClientAdminResponse> listClients() {
        return oAuthClientRepository.findAll().stream()
                .map(OAuthClientAdminResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public OAuthClientAdminResponse getClient(String id) {
        OAuthClient client = findClient(id);
        return OAuthClientAdminResponse.fromEntity(client);
    }

    @Transactional
    public OAuthClientSecretResponse createClient(OAuthClientCreateRequest request) {
        List<String> redirectUris = sanitizeUris(request.getRedirectUris());
        if (redirectUris.isEmpty()) {
            throw new IllegalArgumentException("At least one redirect URI is required");
        }

        OAuthClient client = new OAuthClient();

        String clientId = normalizeClientId(request.getClientId(), request.getClientName());
        if (oAuthClientRepository.existsByClientId(clientId)) {
            throw new IllegalArgumentException("client_id already exists");
        }

        String clientSecret = generateClientSecret();

        client.setClientId(clientId);
        client.setClientSecret(passwordEncoder.encode(clientSecret));
        client.setClientName(request.getClientName().trim());
        client.setDomain(resolveDomain(request.getDomainName()));
        client.setRedirectUris(String.join(",", redirectUris));
        client.setAllowedScopes(String.join(" ", sanitizeScopes(request.getAllowedScopes())));
        client.setGrantTypes(String.join(",", sanitizeGrantTypes(request.getGrantTypes())));
        client.setRequirePkce(request.getRequirePkce() == null || request.getRequirePkce());
        client.setActive(request.getActive() == null || request.getActive());
        client.setCreatedBy(normalizeCreatedBy(request.getCreatedBy()));

        OAuthClient saved = oAuthClientRepository.save(client);
        return new OAuthClientSecretResponse(
                saved.getId().toString(),
                saved.getClientId(),
                clientSecret,
                Instant.now()
        );
    }

    @Transactional
    public OAuthClientAdminResponse updateClient(String id, OAuthClientUpdateRequest request) {
        OAuthClient client = findClient(id);

        if (request.getClientName() != null && !request.getClientName().isBlank()) {
            client.setClientName(request.getClientName().trim());
        }
        if (request.getRedirectUris() != null) {
            List<String> redirectUris = sanitizeUris(request.getRedirectUris());
            if (redirectUris.isEmpty()) {
                throw new IllegalArgumentException("At least one redirect URI is required");
            }
            client.setRedirectUris(String.join(",", redirectUris));
        }
        if (request.getAllowedScopes() != null) {
            client.setAllowedScopes(String.join(" ", sanitizeScopes(request.getAllowedScopes())));
        }
        if (request.getGrantTypes() != null) {
            client.setGrantTypes(String.join(",", sanitizeGrantTypes(request.getGrantTypes())));
        }
        if (request.getRequirePkce() != null) {
            client.setRequirePkce(request.getRequirePkce());
        }
        if (request.getActive() != null) {
            client.setActive(request.getActive());
        }
        if (request.getDomainName() != null && !request.getDomainName().isBlank()) {
            client.setDomain(resolveDomain(request.getDomainName()));
        }

        OAuthClient saved = oAuthClientRepository.save(client);
        return OAuthClientAdminResponse.fromEntity(saved);
    }

    @Transactional
    public OAuthClientSecretResponse rotateSecret(String id) {
        OAuthClient client = findClient(id);
        String newSecret = generateClientSecret();
        client.setClientSecret(passwordEncoder.encode(newSecret));
        OAuthClient saved = oAuthClientRepository.save(client);

        return new OAuthClientSecretResponse(
                saved.getId().toString(),
                saved.getClientId(),
                newSecret,
                Instant.now()
        );
    }

    @Transactional
    public void activateClient(String id) {
        OAuthClient client = findClient(id);
        client.setActive(true);
        oAuthClientRepository.save(client);
    }

    @Transactional
    public void deactivateClient(String id) {
        OAuthClient client = findClient(id);
        client.setActive(false);
        oAuthClientRepository.save(client);
    }

    @Transactional
    public void deleteClient(String id) {
        OAuthClient client = findClient(id);
        oAuthClientRepository.delete(client);
    }

    private OAuthClient findClient(String id) {
        UUID clientId;
        try {
            clientId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid OAuth client id");
        }

        return oAuthClientRepository.findById(clientId)
                .orElseThrow(() -> new NoSuchElementException("OAuth client not found"));
    }

    private Domain resolveDomain(String domainName) {
        String normalized = (domainName == null || domainName.isBlank())
                ? DEFAULT_DOMAIN
                : domainName.trim().toLowerCase();

        return domainRepository.findByDomainName(normalized)
                .orElseGet(() -> {
                    Domain domain = new Domain();
                    domain.setDomainName(normalized);
                    domain.setDisplayName(normalized.equals(DEFAULT_DOMAIN) ? "Default Domain" : normalized);
                    domain.setOwnerEmail("admin@" + normalized);
                    domain.setActive(true);
                    return domainRepository.save(domain);
                });
    }

    private String normalizeClientId(String rawClientId, String clientName) {
        if (rawClientId != null && !rawClientId.isBlank()) {
            return rawClientId.trim();
        }

        String seed = clientName == null ? "client" : clientName.toLowerCase();
        seed = seed.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (seed.isBlank()) {
            seed = "client";
        }

        for (int i = 0; i < 10; i++) {
            String candidate = seed + "-" + randomSuffix(8);
            if (!oAuthClientRepository.existsByClientId(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Unable to generate unique client_id");
    }

    private String normalizeCreatedBy(String createdBy) {
        if (createdBy == null || createdBy.isBlank()) {
            return "admin-ui";
        }
        return createdBy.trim();
    }

    private String generateClientSecret() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return "pcs_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String randomSuffix(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALPHANUM.charAt(secureRandom.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }

    private List<String> sanitizeUris(List<String> uris) {
        List<String> values = new ArrayList<>();
        if (uris == null) {
            return values;
        }

        for (String value : uris) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String uri = value.trim();
            validateUri(uri);
            if (!values.contains(uri)) {
                values.add(uri);
            }
        }
        return values;
    }

    private void validateUri(String uriValue) {
        URI uri;
        try {
            uri = URI.create(uriValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid redirect URI: " + uriValue);
        }

        if (!uri.isAbsolute() || uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Redirect URI must be absolute: " + uriValue);
        }

        String scheme = uri.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Redirect URI scheme must be http/https: " + uriValue);
        }
    }

    private List<String> sanitizeScopes(List<String> scopes) {
        List<String> normalized = new ArrayList<>();
        if (scopes != null) {
            for (String scope : scopes) {
                if (scope == null) {
                    continue;
                }
                String s = scope.trim();
                if (!s.isBlank() && !normalized.contains(s)) {
                    normalized.add(s);
                }
            }
        }

        if (normalized.isEmpty()) {
            normalized.add("openid");
            normalized.add("profile");
            normalized.add("email");
        }

        return normalized;
    }

    private List<String> sanitizeGrantTypes(List<String> grantTypes) {
        List<String> normalized = new ArrayList<>();
        if (grantTypes != null) {
            for (String grantType : grantTypes) {
                if (grantType == null) {
                    continue;
                }
                String g = grantType.trim();
                if (!g.isBlank() && !normalized.contains(g)) {
                    normalized.add(g);
                }
            }
        }

        if (normalized.isEmpty()) {
            normalized.add("authorization_code");
            normalized.add("refresh_token");
        }

        return normalized;
    }
}
