package org.ost.platform.user.spi;

import lombok.NonNull;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.security.UserIdMarker;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface UserPort {

    List<UserDto> getFiltered(@NonNull UserFilterDto filter, int page, int size, @NonNull Sort sort);

    /**
     * Offset-based variant of {@link #getFiltered}, for callers with a raw row offset that isn't
     * necessarily a multiple of {@code limit} (e.g. Vaadin's {@code CallbackDataProvider}) — see
     * {@code UserPickerField} and improvement-056.
     */
    List<UserDto> getFilteredByOffset(@NonNull UserFilterDto filter, long offset, int limit, @NonNull Sort sort);

    int count(@NonNull UserFilterDto filter);

    void save(@NonNull UserProfileDto dto, @NonNull Long actingUserId);

    void refreshCurrentUserInContext(@NonNull Long userId);

    void updateLocale(@NonNull Long userId, @NonNull String locale);

    void delete(@NonNull Long userId, @NonNull Long actingUserId);

    void register(@NonNull SignUpDto dto, @NonNull String clientIp);

    Optional<UserDto> findById(@NonNull Long id);

    Optional<UserDto> restoreToSnapshot(@NonNull Long userId, @NonNull Long snapshotId, @NonNull Long actingUserId);

    Optional<UserDto> findByEmail(@NonNull String email);

    Set<Long> findExistingIds(@NonNull Set<Long> ids);

    Set<Long> findDeletedIds(@NonNull Set<Long> ids);

    Map<Long, String> findActorNames(@NonNull Collection<Long> ids);

    Map<Long, UserDto> findByIds(@NonNull Set<Long> ids);

    UserSettingsDto loadSettings(@NonNull Long userId);

    void saveSettings(@NonNull Long userId, @NonNull UserSettingsDto settings);

    boolean isAdmin(@NonNull UserDto user);

    boolean isModerator(@NonNull UserDto user);

    boolean isOwner(@NonNull UserDto user, @NonNull UserIdMarker target);

    boolean isOwner(@NonNull UserDto user, @NonNull Long ownerId);
}
