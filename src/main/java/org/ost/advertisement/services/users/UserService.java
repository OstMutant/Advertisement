package org.ost.advertisement.services.users;

import java.time.Instant;
import java.util.List;
import org.ost.advertisement.dto.filter.UserFilter;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.exceptions.EntityNotFoundException;
import org.ost.advertisement.exceptions.authorization.AccessDeniedException;
import org.ost.advertisement.repository.user.UserRepository;
import org.ost.advertisement.security.AccessEvaluator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private final UserRepository repository;
	private final AccessEvaluator<User> access;

	public UserService(UserRepository repository, @Qualifier("userAccessEvaluator") AccessEvaluator<User> access) {
		this.repository = repository;
		this.access = access;
	}

	public List<User> getFilteredUsers(UserFilter filter, int page, int size, Sort sort) {
		return repository.findByFilter(filter, PageRequest.of(page, size, sort));
	}

	public int countFilteredUsers(UserFilter filter) {
		return repository.countByFilter(filter).intValue();
	}

	public void save(User currentUser, User targetUser) {
		if (!canEdit(currentUser, targetUser)) {
			throw new AccessDeniedException("You cannot edit this user");
		}
		targetUser.setUpdatedAt(Instant.now());
		repository.save(targetUser);
	}

	public void delete(User currentUser, User targetUser) {
		if (!canDelete(currentUser, targetUser)) {
			throw new AccessDeniedException("You cannot delete this user");
		}
		repository.delete(targetUser);
	}

	public User getById(Long id) {
		return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
	}

	public boolean canEdit(User currentUser, User targetUser) {
		return access.canEdit(currentUser, targetUser);
	}

	public boolean canDelete(User currentUser, User targetUser) {
		return access.canDelete(currentUser, targetUser);
	}
}
