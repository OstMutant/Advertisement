package org.ost.advertisement.services;

import org.ost.advertisement.entyties.Role;
import org.ost.advertisement.entyties.User;
import org.ost.advertisement.repository.RoleRepository;
import org.springframework.stereotype.Service;

@Service
public class SecurityService {

	private final RoleRepository roleRepository;

	public SecurityService(RoleRepository roleRepository) {
		this.roleRepository = roleRepository;
	}

	public boolean hasRole(User user, String roleCode) {
		if (user == null || user.getRoleId() == null) {
			return false;
		}

		Role role = roleRepository.findById(user.getRoleId()).orElse(null);
		return role != null && roleCode.equalsIgnoreCase(role.getCode());
	}

	public boolean isAdmin(User user) {
		return hasRole(user, "ADMIN");
	}

	public boolean isModerator(User user) {
		return hasRole(user, "MODERATOR");
	}

	public boolean isOwner(User user, Long ownerUserId) {
		return user != null && user.getId().equals(ownerUserId);
	}
}

