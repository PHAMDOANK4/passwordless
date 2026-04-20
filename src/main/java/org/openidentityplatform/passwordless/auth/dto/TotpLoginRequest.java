package org.openidentityplatform.passwordless.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TotpLoginRequest {
    @NotBlank
    private String email;

    @NotNull
    private Integer totpCode;
}
