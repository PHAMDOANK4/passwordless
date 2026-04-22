package org.openidentityplatform.passwordless.auth.models;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.openidentityplatform.passwordless.webauthn.models.AssertRequest;

@Data
public class AuthVerifyRequest {

    @NotBlank
    private String authTxId;

    private AuthMethod method;

    private String otp;

    private Integer totp;

    private AssertRequest webauthnAssertion;
}
