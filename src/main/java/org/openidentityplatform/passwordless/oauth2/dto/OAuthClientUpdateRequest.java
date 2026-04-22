package org.openidentityplatform.passwordless.oauth2.dto;

import lombok.Data;

import java.util.List;

@Data
public class OAuthClientUpdateRequest {

    private String clientName;

    private List<String> redirectUris;

    private List<String> allowedScopes;

    private List<String> grantTypes;

    private Boolean requirePkce;

    private Boolean active;

    private String domainName;
}
