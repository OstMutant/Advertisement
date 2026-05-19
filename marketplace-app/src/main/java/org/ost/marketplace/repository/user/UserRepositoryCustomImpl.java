package org.ost.marketplace.repository.user;

import org.ost.marketplace.dto.filter.UserFilterDto;
import org.ost.marketplace.entities.User;
import org.ost.marketplace.dto.UserProfileDto;
import org.ost.sqlengine.RepositoryCustom;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryCustomImpl extends RepositoryCustom<User, UserFilterDto>
        implements UserRepositoryCustom {

    public UserRepositoryCustomImpl(JdbcClient jdbcClient) {
        super(jdbcClient, UserDescriptor.Read.PROJECTION, UserDescriptor.Read.FILTER);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return find(UserDescriptor.Read.EMAIL_FILTER, email);
    }

    @Override
    public void updateLocale(Long userId, String locale) {
        execute(UserDescriptor.Write.UPDATE_LOCALE,
                UserDescriptor.Write.updateLocaleParams(userId, locale));
    }

    @Override
    public void updateProfile(UserProfileDto dto) {
        execute(UserDescriptor.Write.UPDATE_PROFILE,
                UserDescriptor.Write.updateProfileParams(dto));
    }
}
