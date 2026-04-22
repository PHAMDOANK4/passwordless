package org.openidentityplatform.passwordless.auth.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthTotpRegistrationResponse {

    private String username;
    private String uri;
    private String qr;
}