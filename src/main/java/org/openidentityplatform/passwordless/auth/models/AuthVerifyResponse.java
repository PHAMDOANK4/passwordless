package org.openidentityplatform.passwordless.auth.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthVerifyResponse {

    private boolean authenticated;
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private String sessionId;
    private String userId;
    private String email;
}
