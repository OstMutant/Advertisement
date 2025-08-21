package org.ost.advertisement.exceptions.authorization;

public abstract class AuthorizationException extends RuntimeException {
	public AuthorizationException(String message) {
		super(message);
	}
}
