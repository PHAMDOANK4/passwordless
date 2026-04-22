package org.openidentityplatform.passwordless.auth.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthRegisterResponse {

    private String userId;
    private String email;
    private String displayName;
    private String domain;
    private String status;
    private boolean mfaEnabled;
    private String preferredMfaMethod;
}