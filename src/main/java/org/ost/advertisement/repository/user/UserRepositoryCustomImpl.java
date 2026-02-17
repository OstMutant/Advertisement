package org.ost.advertisement.repository.user;

import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.repository.RepositoryCustom;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryCustomImpl extends RepositoryCustom<User, UserFilterDto>
        implements UserRepositoryCustom {

    private static final UserProjection USER_PROJECTION = new UserProjection();
    private static final UserFilterBuilder USER_FILTER_BUILDER = new UserFilterBuilder();
    private static final UserEmailFilterBuilder USER_EMAIL_FILTER_BUILDER = new UserEmailFilterBuilder();

    public UserRepositoryCustomImpl(NamedParameterJdbcTemplate jdbc) {
        super(jdbc, USER_PROJECTION, USER_FILTER_BUILDER);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return find(USER_EMAIL_FILTER_BUILDER, email);
    }
}
