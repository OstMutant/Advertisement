package org.ost.marketplace.repository.user;

import org.ost.marketplace.dto.UserProfileDto;
import org.ost.marketplace.dto.filter.UserFilterDto;
import org.ost.marketplace.entities.User;
import org.ost.sqlengine.FilterableRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final FilterableRepository<User, UserFilterDto> query;
    private final UserCrudRepository crud;

    public UserRepository(JdbcClient jdbcClient, UserCrudRepository crud) {
        this.query = new FilterableRepository<>(jdbcClient,
                UserDescriptor.Read.PROJECTION,
                UserDescriptor.Read.FILTER);
        this.crud  = crud;
    }

    public User save(User user) {
        return crud.save(user);
    }

    public Optional<User> findById(Long id) {
        return crud.findById(id);
    }

    public void deleteById(Long id) {
        crud.deleteById(id);
    }

    public List<User> findByFilter(UserFilterDto filter, Pageable pageable) {
        return query.findByFilter(filter, pageable);
    }

    public Long countByFilter(UserFilterDto filter) {
        return query.countByFilter(filter);
    }

    public Optional<User> findByEmail(String email) {
        return query.find(UserDescriptor.Read.EMAIL_FILTER, email);
    }

    public void updateProfile(UserProfileDto dto) {
        query.execute(UserDescriptor.Write.UPDATE_PROFILE,
                UserDescriptor.Write.updateProfileParams(dto));
    }

    public void updateLocale(Long userId, String locale) {
        query.execute(UserDescriptor.Write.UPDATE_LOCALE,
                UserDescriptor.Write.updateLocaleParams(userId, locale));
    }
}
