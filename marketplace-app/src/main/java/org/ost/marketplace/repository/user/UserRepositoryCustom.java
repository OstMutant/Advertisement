package org.ost.marketplace.repository.user;

import org.ost.marketplace.dto.UserProfileDto;
import org.ost.marketplace.dto.filter.UserFilterDto;
import org.ost.marketplace.entities.User;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryCustom {

    List<User> findByFilter(UserFilterDto filter, Pageable pageable);

    Long countByFilter(UserFilterDto filter);

    Optional<User> findByEmail(String email);

    void updateProfile(UserProfileDto dto);

    void updateLocale(Long userId, String locale);
}
