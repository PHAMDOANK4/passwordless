package org.openidentityplatform.passwordless.oauth2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class OAuthClientSecretResponse {

    private String id;
    private String clientId;
    private String clientSecret;
    private Instant issuedAt;
}
