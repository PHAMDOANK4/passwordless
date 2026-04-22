package org.openidentityplatform.passwordless.oauth2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OAuth2AuthorizeResponse {

    private String redirectUri;
    private String code;
    private String state;
}