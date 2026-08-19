package org.ost.user.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.spi.UserPort;
import org.ost.user.services.UserService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPortImpl implements UserPort {

    private final UserService userService;

    @Override
    public List<UserDto> getFiltered(@NonNull UserFilterDto filter, int page, int size, @NonNull Sort sort) {
        return userService.getFiltered(filter, page, size, sort);
    }

    @Override
    public List<UserDto> getFilteredByOffset(@NonNull UserFilterDto filter, long offset, int limit, @NonNull Sort sort) {
        return userService.getFilteredByOffset(filter, offset, limit, sort);
    }

    @Override
    public int count(@NonNull UserFilterDto filter) {
        return userService.count(filter);
    }

    @Override
    public Optional<UserDto> findById(@NonNull Long id) {
        return userService.findById(id);
    }

    @Override
    public Optional<UserDto> findByEmail(@NonNull String email) {
        return userService.findDtoByEmail(email);
    }

    @Override
    public Set<Long> findExistingIds(@NonNull Set<Long> ids) {
        return userService.findExistingIds(ids);
    }

    @Override
    public Set<Long> findDeletedIds(@NonNull Set<Long> ids) {
        return userService.findDeletedIds(ids);
    }

    @Override
    public Map<Long, String> findActorNames(@NonNull Collection<Long> ids) {
        return userService.findActorNames(ids);
    }

    @Override
    public Map<Long, UserDto> findByIds(@NonNull Set<Long> ids) {
        return userService.findByIds(ids);
    }
}
