package org.openidentityplatform.passwordless.auth.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthLoginResponse {

    private String authTxId;
    private String nextStep;
    private AuthMethod selectedMethod;
    private long expiresInSeconds;
    private String message;
    private Object challenge;
}
