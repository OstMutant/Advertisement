package org.ost.advertisement.services;

import static java.util.Optional.ofNullable;

import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.springframework.stereotype.Service;

@Service
public class SecurityService {

	public SecurityService() {
	}

	public boolean hasRole(User user, Role role) {
		return ofNullable(user).map(User::getRole).filter(v -> v == role).isPresent();
	}

	public boolean isAdmin(User user) {
		return hasRole(user, Role.ADMIN);
	}

	public boolean isModerator(User user) {
		return hasRole(user, Role.MODERATOR);
	}

	public boolean isUser(User user) {
		return hasRole(user, Role.USER);
	}

	public boolean isOwner(User user, Long ownerUserId) {
		return user != null && user.getId().equals(ownerUserId);
	}
}

