package org.openidentityplatform.passwordless.auth.models;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthLoginRequest {

    @NotBlank
    private String identifier;

    private String clientId = "passwordless-web";

    private AuthMethod preferredMethod;
}
