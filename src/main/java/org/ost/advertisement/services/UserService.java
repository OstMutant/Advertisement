package org.ost.advertisement.services;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.filter.UserFilter;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.exceptions.EntityNotFoundException;
import org.ost.advertisement.exceptions.authorization.AccessDeniedException;
import org.ost.advertisement.repository.user.UserRepository;
import org.ost.advertisement.security.AccessEvaluator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository repository;
	private final AccessEvaluator access;

	public List<User> getFiltered(UserFilter filter, int page, int size, Sort sort) {
		return repository.findByFilter(filter, PageRequest.of(page, size, sort));
	}

	public int count(UserFilter filter) {
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
