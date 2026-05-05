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
import static org.ost.sqlengine.writer.SqlEntityWriter.colExpr;

@Repository
public class UserRepositoryCustomImpl extends RepositoryCustom<User, UserFilterDto>
        implements UserRepositoryCustom {

    private static final UserDescriptor         USER_PROJECTION           = new UserDescriptor();
    private static final UserFilterBuilder       USER_FILTER_BUILDER       = new UserFilterBuilder();
    private static final UserEmailFilterBuilder  USER_EMAIL_FILTER_BUILDER = new UserEmailFilterBuilder();

    private static final SqlEntityWriter<UserProfileDto> PROFILE_WRITER = SqlEntityWriter.of(
            "user_information",
            col("name", UserProfileDto::name),
            col("role", u -> u.role().name()),
            colExpr("updated_at", "NOW()")
    );

    private static final SqlEntityWriter<String> LOCALE_WRITER = SqlEntityWriter.of(
            "user_information",
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
        execute(LOCALE_WRITER.updateWhere("id = :id"),
                LOCALE_WRITER.params(locale).addValue("id", userId));
    }

    @Override
    public void updateProfile(UserProfileDto dto) {
        execute(PROFILE_WRITER.updateWhere("id = :id"),
                PROFILE_WRITER.params(dto).addValue("id", dto.id()));
    }
}
