package org.ost.advertisement.repository;

import java.util.List;
import java.util.Optional;
import org.ost.advertisement.dto.UserFilter;
import org.ost.advertisement.entyties.User;
import org.springframework.data.domain.Pageable;

public interface UserRepositoryCustom {

	List<User> findByFilter(UserFilter filter, Pageable pageable);

	Long countByFilter(UserFilter filter);

	Optional<User> findByEmail(String email);
}
