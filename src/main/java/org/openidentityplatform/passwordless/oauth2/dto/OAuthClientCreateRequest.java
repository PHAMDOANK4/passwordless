package org.openidentityplatform.passwordless.oauth2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class OAuthClientCreateRequest {

    private String clientId;

    @NotBlank
    private String clientName;

    @NotEmpty
    private List<String> redirectUris;

    private List<String> allowedScopes;

    private List<String> grantTypes;

    private Boolean requirePkce;

    private Boolean active;

    private String domainName;

    private String createdBy;
}
