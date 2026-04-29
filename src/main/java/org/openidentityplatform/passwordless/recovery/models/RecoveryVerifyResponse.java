package org.openidentityplatform.passwordless.recovery.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecoveryVerifyResponse {
    private boolean verified;
    private String recoveryToken;
    private String message;
}
