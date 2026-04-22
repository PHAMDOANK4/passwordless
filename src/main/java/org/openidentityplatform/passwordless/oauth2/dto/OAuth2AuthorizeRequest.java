package org.openidentityplatform.passwordless.oauth2.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OAuth2AuthorizeRequest {

    @JsonProperty("response_type")
    @JsonAlias("responseType")
    @NotBlank
    private String responseType;

    @JsonProperty("client_id")
    @JsonAlias("clientId")
    @NotBlank
    private String clientId;

    @JsonProperty("redirect_uri")
    @JsonAlias("redirectUri")
    @NotBlank
    private String redirectUri;

    private String scope;

    private String state;

    @JsonProperty("code_challenge")
    @JsonAlias("codeChallenge")
    private String codeChallenge;

    @JsonProperty("code_challenge_method")
    @JsonAlias("codeChallengeMethod")
    private String codeChallengeMethod;

    private String nonce;
}