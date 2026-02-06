package org.openidentityplatform.passwordless.identity.services;

import org.openidentityplatform.passwordless.exceptions.NotFoundException;

/**
 * Thrown when the userHandle supplied in a grant request does not
 * correspond to any active {@link org.openidentityplatform.passwordless.identity.models.AppUser}.
 */
public class AppUserNotFoundException extends NotFoundException {
    public AppUserNotFoundException(String msg) {
        super(msg);
    }
}
