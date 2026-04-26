package org.openidentityplatform.passwordless.auth.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthLoginRequestJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesLegacyEmailFieldIntoIdentifier() throws Exception {
        String json = """
                {
                  "email": "legacy.user@example.com",
                  "preferredMethod": "OTP",
                  "clientId": "passwordless-web"
                }
                """;

        AuthLoginRequest request = objectMapper.readValue(json, AuthLoginRequest.class);

        assertEquals("legacy.user@example.com", request.getIdentifier());
        assertEquals(AuthMethod.OTP, request.getPreferredMethod());
        assertEquals("passwordless-web", request.getClientId());
    }

    @Test
    void keepsIdentifierFieldWhenProvided() throws Exception {
        String json = """
                {
                  "identifier": "modern.user@example.com",
                  "preferredMethod": "TOTP"
                }
                """;

        AuthLoginRequest request = objectMapper.readValue(json, AuthLoginRequest.class);

        assertEquals("modern.user@example.com", request.getIdentifier());
        assertEquals(AuthMethod.TOTP, request.getPreferredMethod());
    }
}
