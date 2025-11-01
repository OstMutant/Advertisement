package org.ost.advertisement.security;

import static java.util.Optional.ofNullable;

import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.springframework.stereotype.Component;

@Component
public class RoleChecker {

	public boolean isAdmin(User user) {
		return hasRole(user, Role.ADMIN);
	}

	public boolean isModerator(User user) {
		return hasRole(user, Role.MODERATOR);
	}

	public boolean isUser(User user) {
		return hasRole(user, Role.USER);
	}

	private boolean hasRole(User user, Role role) {
		return ofNullable(user).map(User::role).filter(v -> v == role).isPresent();
	}
}

