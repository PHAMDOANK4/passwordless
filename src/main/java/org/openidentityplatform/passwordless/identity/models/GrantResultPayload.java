package org.openidentityplatform.passwordless.identity.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Payload returned to the calling application after AuthCentralA
 * successfully authenticates the user.
 */
@Getter
@AllArgsConstructor
public class GrantResultPayload {

    @JsonProperty("access_token")
    private final String accessToken;

    @JsonProperty("refresh_token")
    private final String refreshToken;

    @JsonProperty("expires_in_seconds")
    private final long expiresInSeconds;

    @JsonProperty("auth_method")
    private final String authMethod;

    @JsonProperty("user_handle")
    private final String userHandle;
}
