package org.ost.platform.apikey.model;

/**
 * Thrown when a caller revokes an API key that is already revoked, so HTTP mapping can tell it apart from an unexpected-state bug.
 */
public class ApiKeyAlreadyRevokedException extends RuntimeException {

    public ApiKeyAlreadyRevokedException(String message) {
        super(message);
    }
}
