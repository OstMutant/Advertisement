package org.ost.advertisement.security;

import org.ost.advertisement.entities.User;

public interface AccessEvaluator<T> {
	boolean canView(User currentUser, T target);
	boolean canView(User currentUser);
	boolean canEdit(User currentUser, T target);
	boolean canEdit(User currentUser);
	boolean canDelete(User currentUser, T target);
	boolean canDelete(User currentUser);
}
