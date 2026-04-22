package org.openidentityplatform.passwordless.webauthn.models;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WebAuthnLoginFinishRequest {

    @NotBlank
    private String transactionId;

    @NotNull
    @Valid
    private AssertRequest assertion;
}
