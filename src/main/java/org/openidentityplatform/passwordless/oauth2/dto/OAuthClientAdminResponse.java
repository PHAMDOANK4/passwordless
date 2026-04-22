package org.openidentityplatform.passwordless.oauth2.dto;

import lombok.Data;
import org.openidentityplatform.passwordless.oauth2.models.OAuthClient;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Data
public class OAuthClientAdminResponse {

    private String id;
    private String clientId;
    private String clientName;
    private String domainName;
    private List<String> redirectUris;
    private List<String> allowedScopes;
    private List<String> grantTypes;
    private boolean requirePkce;
    private boolean active;
    private int accessTokenLifetimeSeconds;
    private int refreshTokenLifetimeSeconds;
    private int idTokenLifetimeSeconds;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static OAuthClientAdminResponse fromEntity(OAuthClient client) {
        OAuthClientAdminResponse response = new OAuthClientAdminResponse();
        response.setId(client.getId() != null ? client.getId().toString() : null);
        response.setClientId(client.getClientId());
        response.setClientName(client.getClientName());
        response.setDomainName(client.getDomain() != null ? client.getDomain().getDomainName() : null);
        response.setRedirectUris(split(client.getRedirectUris(), ","));
        response.setAllowedScopes(split(client.getAllowedScopes(), "\\s+"));
        response.setGrantTypes(split(client.getGrantTypes(), ","));
        response.setRequirePkce(client.isRequirePkce());
        response.setActive(client.isActive());
        response.setAccessTokenLifetimeSeconds(client.getAccessTokenLifetimeSeconds());
        response.setRefreshTokenLifetimeSeconds(client.getRefreshTokenLifetimeSeconds());
        response.setIdTokenLifetimeSeconds(client.getIdTokenLifetimeSeconds());
        response.setCreatedBy(client.getCreatedBy());
        response.setCreatedAt(client.getCreatedAt());
        response.setUpdatedAt(client.getUpdatedAt());
        return response;
    }

    private static List<String> split(String value, String regex) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(regex))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .toList();
    }
}
