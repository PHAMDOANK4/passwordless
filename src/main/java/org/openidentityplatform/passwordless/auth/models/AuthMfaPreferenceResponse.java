package org.openidentityplatform.passwordless.auth.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthMfaPreferenceResponse {

    private boolean mfaEnabled;
    private String preferredMfaMethod;
}