package org.openidentityplatform.passwordless.auth.models;

import lombok.Data;

import java.time.Instant;

@Data
public class AuthTransactionState {

    private String id;
    private String identifier;
    private String clientId;
    private AuthMethod method;
    private String status;
    private Instant createdAt;
    private Instant expiresAt;
    private int attempts;
    private String ipAddress;
    private String userAgent;
}
