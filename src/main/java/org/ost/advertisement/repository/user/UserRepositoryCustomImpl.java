package org.ost.advertisement.repository.user;

import java.util.Optional;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.repository.RepositoryCustom;
import org.ost.advertisement.repository.user.filter.UserEmailFilterApplier;
import org.ost.advertisement.repository.user.filter.UserFilterApplier;
import org.ost.advertisement.repository.user.mapping.UserMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryCustomImpl extends RepositoryCustom<User, UserFilterDto>
	implements UserRepositoryCustom {

	private static final UserMapper USER_MAPPER = new UserMapper();
	private static final UserFilterApplier USER_FILTER_APPLIER = new UserFilterApplier();
	private static final UserEmailFilterApplier USER_EMAIL_FILTER_APPLIER = new UserEmailFilterApplier();

	public UserRepositoryCustomImpl(NamedParameterJdbcTemplate jdbc) {
		super(jdbc, USER_MAPPER, USER_FILTER_APPLIER);
	}

	@Override
	public Optional<User> findByEmail(String email) {
		return find(USER_EMAIL_FILTER_APPLIER, email);
	}
}
