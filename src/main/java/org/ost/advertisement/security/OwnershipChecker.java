package org.ost.advertisement.security;

import static java.util.Optional.ofNullable;

import org.ost.advertisement.entities.User;
import org.springframework.stereotype.Component;

@Component
public class OwnershipChecker {

	public boolean isOwner(User user, Long ownerId) {
		return ofNullable(user).map(User::getId).filter(v -> v.equals(ownerId)).isPresent();
	}

	public boolean isOwner(User user, UserIdMarker target) {
		return ofNullable(target).map(UserIdMarker::getUserId).filter(v -> isOwner(user, v)).isPresent();
	}
}
