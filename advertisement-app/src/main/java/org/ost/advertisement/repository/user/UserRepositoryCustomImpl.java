package org.ost.advertisement.repository.user;

import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.dto.UserProfileDto;
import org.ost.sqlengine.RepositoryCustom;
import org.ost.sqlengine.writer.SqlEntityWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static org.ost.sqlengine.writer.SqlEntityWriter.col;

@Repository
public class UserRepositoryCustomImpl extends RepositoryCustom<User, UserFilterDto>
        implements UserRepositoryCustom {

    private static final UserProjection          USER_PROJECTION           = new UserProjection();
    private static final UserFilterBuilder        USER_FILTER_BUILDER       = new UserFilterBuilder();
    private static final UserEmailFilterBuilder   USER_EMAIL_FILTER_BUILDER = new UserEmailFilterBuilder();

    private static final SqlEntityWriter<UserProfileDto> PROFILE_WRITER = SqlEntityWriter.of(
            col("name", UserProfileDto::name),
            col("role", u -> u.role().name())
    );

    private static final SqlEntityWriter<String> LOCALE_WRITER = SqlEntityWriter.of(
            col("locale", s -> s)
    );

    public UserRepositoryCustomImpl(JdbcClient jdbcClient) {
        super(jdbcClient, USER_PROJECTION, USER_FILTER_BUILDER);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return find(USER_EMAIL_FILTER_BUILDER, email);
    }

    @Override
    public void updateLocale(Long userId, String locale) {
        executor.jdbcClient()
                .sql(LOCALE_WRITER.updateWhere("user_information", "id = :id"))
                .paramSource(LOCALE_WRITER.params(locale).addValue("id", userId))
                .update();
    }

    @Override
    public void updateProfile(UserProfileDto dto) {
        executor.jdbcClient()
                .sql(PROFILE_WRITER.updateWhere("user_information", "updated_at = NOW()", "id = :id"))
                .paramSource(PROFILE_WRITER.params(dto).addValue("id", dto.id()))
                .update();
    }
}
