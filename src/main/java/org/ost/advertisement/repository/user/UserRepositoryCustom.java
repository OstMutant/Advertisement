package org.ost.advertisement.repository.user;

import java.util.List;
import java.util.Optional;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.User;
import org.springframework.data.domain.Pageable;

public interface UserRepositoryCustom {

	List<User> findByFilter(UserFilterDto filter, Pageable pageable);

	Long countByFilter(UserFilterDto filter);

	Optional<User> findByEmail(String email);
}
