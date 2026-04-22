package org.openidentityplatform.passwordless.auth.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthMeResponse {

    private String userId;
    private String email;
    private String displayName;
    private String status;
    private String role;
    private boolean mfaEnabled;
    private String preferredMfaMethod;
}
