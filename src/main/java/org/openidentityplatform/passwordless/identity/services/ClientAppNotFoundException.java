package org.openidentityplatform.passwordless.identity.services;

import org.openidentityplatform.passwordless.exceptions.NotFoundException;

/**
 * Thrown when the appKey supplied in a grant request does not
 * correspond to any active {@link org.openidentityplatform.passwordless.identity.models.RegisteredClient}.
 */
public class ClientAppNotFoundException extends NotFoundException {
    public ClientAppNotFoundException(String msg) {
        super(msg);
    }
}
