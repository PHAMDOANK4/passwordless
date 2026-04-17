package org.openidentityplatform.passwordless.token.services;

import org.openidentityplatform.passwordless.otp.services.BadRequestException;

public class InvalidRefreshTokenException extends BadRequestException {
    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token");
    }
}
