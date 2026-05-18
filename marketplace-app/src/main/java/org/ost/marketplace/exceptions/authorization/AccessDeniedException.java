package org.ost.marketplace.exceptions.authorization;

public class AccessDeniedException extends AuthorizationException {

    public AccessDeniedException(String message) {
        super(message);
    }
}

