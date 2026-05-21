package org.ost.marketplace.repository.user;

import org.ost.marketplace.dto.UserProfileDto;
import org.ost.marketplace.dto.filter.UserFilterDto;
import org.ost.marketplace.entities.User;
import org.ost.sqlengine.FilterableRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.ost.marketplace.repository.user.UserDescriptor.*;

@Repository
public class UserRepository extends FilterableRepository<User, UserFilterDto> {

    private final UserCrudRepository crud;

    UserRepository(JdbcClient jdbcClient, UserCrudRepository crud) {
        super(jdbcClient, Read.PROJECTION, Read.FILTER);
        this.crud = crud;
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

    public Optional<User> findByEmail(String email) {
        return find(Read.EMAIL_FILTER, email);
    }

    public void updateProfile(UserProfileDto dto) {
        executeUpdate(Write.UPDATE_PROFILE, Write.updateProfileParams(dto));
    }

    public void updateLocale(Long userId, String locale) {
        executeUpdate(Write.UPDATE_LOCALE, Write.updateLocaleParams(userId, locale));
    }

    public List<Long> findExistingIds(Long[] ids) {
        return findAll(Read.SELECT_EXISTING_IDS, Read.idsParams(ids),
                (rs, _) -> ID.extract(rs));
    }

    public Map<Long, String> findActorNames(Long[] ids) {
        return findAll(Read.SELECT_ACTOR_NAMES, Read.idsParams(ids),
                        (rs, _) -> Map.entry(ID.extract(rs), NAME.extract(rs)))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
