package org.ost.marketplace.exceptions.authorization;

abstract class AuthorizationException extends RuntimeException {
    protected AuthorizationException(String message) {
        super(message);
    }
}
