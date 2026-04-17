package org.openidentityplatform.passwordless.token.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenPair {

    private String accessToken;
    private String refreshToken;
    private long accessTokenExpiresIn;

    public String getTokenType() {
        return "Bearer";
    }
}
