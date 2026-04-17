package org.openidentityplatform.passwordless.auth.models;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class AuthSessionResponse {

    private String sessionId;
    private String ipAddress;
    private String deviceInfo;
    private String authMethod;
    private int authLevel;
    private Instant createdAt;
    private Instant lastActivityAt;
    private Instant expiresAt;
    private boolean current;
}
