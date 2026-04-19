package org.openidentityplatform.passwordless.webauthn.models;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WebAuthnRegisterBeginRequest {

    @NotBlank
    private String username;

    private String authenticatorAttachment;

    private Boolean residentKeyRequired;

    private String userVerification;
}
