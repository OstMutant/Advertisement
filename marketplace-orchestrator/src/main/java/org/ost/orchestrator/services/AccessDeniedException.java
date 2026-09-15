package org.ost.orchestrator.services;

/** Thrown by a save/delete service when the acting user is neither the resource's owner nor privileged. */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
