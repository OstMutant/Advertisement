package org.ost.advertisement.services;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.EntityMarker;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.exceptions.authorization.AccessDeniedException;
import org.ost.advertisement.repository.user.UserRepository;
import org.ost.advertisement.security.AccessEvaluator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Validated
public class UserService {

    private final UserRepository repository;
    private final AccessEvaluator access;

    public List<User> getFiltered(@Valid UserFilterDto filter, int page, int size, Sort sort) {
        return repository.findByFilter(filter, PageRequest.of(page, size, sort));
    }

    public int count(@Valid UserFilterDto filter) {
        return repository.countByFilter(filter).intValue();
    }

    public void save(User targetUser) {
        if (access.canNotEdit(targetUser)) {
            throw new AccessDeniedException("You cannot edit this user");
        }
        repository.save(targetUser);
    }

    public void delete(EntityMarker targetUser) {
        if (access.canNotDelete(targetUser)) {
            throw new AccessDeniedException("You cannot delete this user");
        }
        repository.deleteById(targetUser.getId());
    }

    public void register(User newUser) {
        repository.save(newUser);
    }

    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }
}