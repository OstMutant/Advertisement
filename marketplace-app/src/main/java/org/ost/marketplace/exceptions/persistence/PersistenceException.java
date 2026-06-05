package org.ost.marketplace.exceptions.persistence;

public abstract class PersistenceException extends RuntimeException {
    protected PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
