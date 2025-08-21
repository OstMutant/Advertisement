package org.ost.advertisement.exceptions.authorization;

public class OwnershipViolationException extends AuthorizationException {

	public OwnershipViolationException(String message) {
		super(message);
	}
}
