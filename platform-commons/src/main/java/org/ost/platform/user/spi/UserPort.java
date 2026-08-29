package org.ost.platform.user.spi;

import lombok.NonNull;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Query side: find/filter users, resolve profile data by id/email, batched name/existence lookups.
 * Implementation lives in user-spring-boot-starter.
 */
public interface UserPort {

    List<UserDto> getFiltered(@NonNull UserFilterDto filter, int page, int size, @NonNull Sort sort);

    /**
     * Offset-based variant of {@link #getFiltered}, for callers with a raw row offset that isn't
     * necessarily a multiple of {@code limit} (e.g. Vaadin's {@code CallbackDataProvider} in
     * {@code UserPickerField}).
     */
    List<UserDto> getFilteredByOffset(@NonNull UserFilterDto filter, long offset, int limit, @NonNull Sort sort);

    int count(@NonNull UserFilterDto filter);

    Optional<UserDto> findById(@NonNull Long id);

    Optional<UserDto> findByEmail(@NonNull String email);

    Set<Long> findExistingIds(@NonNull Set<Long> ids);

    Set<Long> findDeletedIds(@NonNull Set<Long> ids);

    Map<Long, String> findActorNames(@NonNull Collection<Long> ids);

    Map<Long, UserDto> findByIds(@NonNull Set<Long> ids);
}
