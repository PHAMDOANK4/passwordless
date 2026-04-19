package org.openidentityplatform.passwordless.webauthn.models;

import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WebAuthnLoginBeginResponse {

    private String transactionId;
    private PublicKeyCredentialRequestOptions publicKey;
}
