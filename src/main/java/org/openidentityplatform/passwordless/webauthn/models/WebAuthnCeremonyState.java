package org.openidentityplatform.passwordless.webauthn.models;

import lombok.Data;

import java.time.Instant;

@Data
public class WebAuthnCeremonyState {

    public enum CeremonyType {
        REGISTRATION,
        AUTHENTICATION
    }

    private String transactionId;
    private CeremonyType ceremonyType;
    private String username;
    private String challenge;
    private Instant createdAt;
    private Instant expiresAt;
}
