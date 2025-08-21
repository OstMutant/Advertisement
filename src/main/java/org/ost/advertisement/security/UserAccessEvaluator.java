package org.ost.advertisement.security;


import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.SecurityService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Qualifier("userAccessEvaluator")
public class UserAccessEvaluator implements AccessEvaluator<User> {

	private final SecurityService security;

	public UserAccessEvaluator(SecurityService security) {
		this.security = security;
	}

	@Override
	public boolean canView(User currentUser, User target) {
		return canView(currentUser) || security.isOwner(currentUser, target.getId());
	}

	@Override
	public boolean canView(User currentUser) {
		return security.isAdmin(currentUser) || security.isModerator(currentUser);
	}

	@Override
	public boolean canEdit(User currentUser, User target) {
		return canEdit(currentUser) || security.isOwner(currentUser, target.getId());
	}

	@Override
	public boolean canEdit(User currentUser) {
		return security.isAdmin(currentUser);
	}

	@Override
	public boolean canDelete(User currentUser, User target) {
		return canDelete(currentUser) || security.isOwner(currentUser, target.getId());
	}

	@Override
	public boolean canDelete(User currentUser) {
		return security.isAdmin(currentUser);
	}
}
