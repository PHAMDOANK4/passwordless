package org.openidentityplatform.passwordless.webauthn.models;

import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WebAuthnRegisterBeginResponse {

    private String transactionId;
    private PublicKeyCredentialCreationOptions publicKey;
}
