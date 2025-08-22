package org.ost.advertisement.security;


import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.User;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessEvaluator {

	private final RoleChecker roleChecker;
	private final OwnershipChecker ownershipChecker;

	public boolean canView(User currentUser, UserIdMarker target) {
		return canView(currentUser) || ownershipChecker.isOwner(currentUser, target);
	}

	public boolean canView(User currentUser) {
		return roleChecker.isAdmin(currentUser) || roleChecker.isModerator(currentUser);
	}

	public boolean canEdit(User currentUser, UserIdMarker target) {
		return canEdit(currentUser) || ownershipChecker.isOwner(currentUser, target);
	}

	public boolean canEdit(User currentUser) {
		return roleChecker.isAdmin(currentUser);
	}

	public boolean canDelete(User currentUser, UserIdMarker target) {
		return canDelete(currentUser) || ownershipChecker.isOwner(currentUser, target);
	}

	public boolean canDelete(User currentUser) {
		return roleChecker.isAdmin(currentUser);
	}
}
