package org.ost.platform.user.spi;

import lombok.NonNull;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UserPort {

    List<UserDto> getFiltered(@NonNull UserFilterDto filter, int page, int size, @NonNull Sort sort);

    int count(@NonNull UserFilterDto filter);

    void save(@NonNull UserProfileDto dto);

    void updateLocale(@NonNull Long userId, @NonNull String locale);

    void delete(@NonNull Long userId);

    void register(@NonNull SignUpDto dto);

    Optional<UserDto> findById(@NonNull Long id);

    Optional<UserDto> restoreToSnapshot(@NonNull Long userId, @NonNull Long snapshotId, @NonNull Long actingUserId);

    Optional<UserDto> findByEmail(@NonNull String email);

    List<Long> findExistingIds(@NonNull Long[] ids);

    Map<Long, String> findActorNames(@NonNull Collection<Long> ids);

    List<ChangeEntry> expandActivityFields(@NonNull AuditTimelineItemDto<AuditableSnapshot> item);

    UserSettingsDto loadSettings(@NonNull Long userId);

    void saveSettings(@NonNull Long userId, @NonNull UserSettingsDto settings);
}
