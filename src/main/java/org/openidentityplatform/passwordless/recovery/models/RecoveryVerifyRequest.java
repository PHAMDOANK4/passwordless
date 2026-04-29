package org.openidentityplatform.passwordless.recovery.models;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecoveryVerifyRequest {
    @NotBlank
    private String email;

    @NotBlank
    private String code;

    /**
     * Type of recovery code: "backup" for backup codes, "otp" for email OTP.
     */
    @NotBlank
    private String type;
}
