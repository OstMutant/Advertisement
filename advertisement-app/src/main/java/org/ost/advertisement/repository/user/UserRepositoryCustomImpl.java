package org.ost.advertisement.repository.user;

import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.dto.UserProfileDto;
import org.ost.sqlengine.RepositoryCustom;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
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

    @Override
    public void updateLocale(Long userId, String locale) {
        executor.jdbc().update(
                "UPDATE user_information SET locale = :locale WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("locale", locale)
                        .addValue("id",     userId)
        );
    }

    @Override
    public void updateProfile(UserProfileDto dto) {
        executor.jdbc().update(
                "UPDATE user_information SET name = :name, role = :role WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("name", dto.name())
                        .addValue("role", dto.role().name())
                        .addValue("id",   dto.id())
        );
    }
}
