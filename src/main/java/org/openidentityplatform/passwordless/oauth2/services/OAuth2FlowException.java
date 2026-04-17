package org.openidentityplatform.passwordless.oauth2.services;

import org.openidentityplatform.passwordless.otp.services.BadRequestException;

public class OAuth2FlowException extends BadRequestException {
    public OAuth2FlowException(String message) {
        super(message);
    }
}
