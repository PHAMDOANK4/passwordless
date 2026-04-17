package org.openidentityplatform.passwordless.auth.services;

import org.openidentityplatform.passwordless.otp.services.BadRequestException;

public class InvalidAuthTransactionException extends BadRequestException {

    public InvalidAuthTransactionException(String message) {
        super(message);
    }
}
