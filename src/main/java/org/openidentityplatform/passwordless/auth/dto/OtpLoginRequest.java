package org.openidentityplatform.passwordless.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpLoginRequest {
    @NotBlank
    private String email;

    @NotBlank
    private String otp;
}
