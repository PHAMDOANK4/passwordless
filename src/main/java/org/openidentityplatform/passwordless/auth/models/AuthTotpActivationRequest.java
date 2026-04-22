package org.openidentityplatform.passwordless.auth.models;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AuthTotpActivationRequest {

    @NotNull
    private Integer totp;
}