package org.openidentityplatform.passwordless.webauthn.models;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WebAuthnLoginBeginRequest {

    @NotBlank
    private String username;
}
