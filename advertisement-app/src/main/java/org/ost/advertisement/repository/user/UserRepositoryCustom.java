package org.ost.advertisement.repository.user;

import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.User;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryCustom {

    List<User> findByFilter(UserFilterDto filter, Pageable pageable);

    Long countByFilter(UserFilterDto filter);

    Optional<User> findByEmail(String email);
}
