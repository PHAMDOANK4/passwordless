package org.openidentityplatform.passwordless.recovery.models;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecoveryRequest {
    @NotBlank
    private String email;
}
