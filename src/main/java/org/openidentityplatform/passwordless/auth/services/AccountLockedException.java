package org.openidentityplatform.passwordless.auth.services;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}
