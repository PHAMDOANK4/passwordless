package org.openidentityplatform.passwordless.identity.models;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body sent by the calling application when it asks AuthCentralA
 * to produce a token after the user has been verified.
 */
@Data
@NoArgsConstructor
public class GrantRequestPayload {

    @NotEmpty
    private String appKey;

    @NotEmpty
    private String userHandle;

    @NotEmpty
    private String authMethod;
}
