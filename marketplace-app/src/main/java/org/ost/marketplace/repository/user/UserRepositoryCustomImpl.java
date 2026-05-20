package org.ost.marketplace.repository.user;

import org.ost.marketplace.dto.filter.UserFilterDto;
import org.ost.marketplace.entities.User;
import org.ost.marketplace.dto.UserProfileDto;
import org.ost.sqlengine.RepositoryCustom;
import org.ost.sqlengine.writer.SqlWriteCommand;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryCustomImpl extends RepositoryCustom<User, UserFilterDto>
        implements UserRepositoryCustom {

    private static final UserDescriptor        PROJECTION           = new UserDescriptor();
    private static final UserFilterBuilder      FILTER_BUILDER       = new UserFilterBuilder();
    private static final UserEmailFilterBuilder EMAIL_FILTER_BUILDER = new UserEmailFilterBuilder();

    private static final SqlWriteCommand UPDATE_PROFILE = SqlWriteCommand.of(UserDescriptor.Write.PROFILE_WRITER.updateWhere("id = :id"));
    private static final SqlWriteCommand UPDATE_LOCALE  = SqlWriteCommand.of(UserDescriptor.Write.LOCALE_WRITER.updateWhere("id = :id"));

    public UserRepositoryCustomImpl(JdbcClient jdbcClient) {
        super(jdbcClient, PROJECTION, FILTER_BUILDER);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return find(EMAIL_FILTER_BUILDER, email);
    }

    @Override
    public void updateLocale(Long userId, String locale) {
        execute(UPDATE_LOCALE, UserDescriptor.Write.LOCALE_WRITER.params(locale).addValue("id", userId));
    }

    @Override
    public void updateProfile(UserProfileDto dto) {
        execute(UPDATE_PROFILE, UserDescriptor.Write.PROFILE_WRITER.params(dto).addValue("id", dto.id()));
    }
}
