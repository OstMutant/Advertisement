package org.ost.user.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.security.UserIdMarker;
import org.ost.platform.user.spi.UserPort;
import org.ost.user.security.OwnershipChecker;
import org.ost.user.security.RoleChecker;
import org.ost.user.services.UserService;
import org.ost.user.services.UserSettingsService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class UserPortImpl implements UserPort {

    private final UserService         userService;
    private final UserSettingsService settingsService;
    private final RoleChecker         roleChecker;
    private final OwnershipChecker    ownershipChecker;

    @Override
    public List<UserDto> getFiltered(@NonNull UserFilterDto filter, int page, int size, @NonNull Sort sort) {
        return userService.getFiltered(filter, page, size, sort);
    }

    @Override
    public int count(@NonNull UserFilterDto filter) {
        return userService.count(filter);
    }

    @Override
    public void save(@NonNull UserProfileDto dto, @NonNull Long actingUserId) {
        userService.save(dto, actingUserId);
    }

    @Override
    public void refreshCurrentUserInContext(@NonNull Long userId) {
        userService.refreshSecurityContext(userId);
    }

    @Override
    public void updateLocale(@NonNull Long userId, @NonNull String locale) {
        userService.updateLocale(userId, locale);
    }

    @Override
    public void delete(@NonNull Long userId) {
        userService.delete(userId);
    }

    @Override
    public void register(@NonNull SignUpDto dto) {
        userService.register(dto);
    }

    @Override
    public Optional<UserDto> findById(@NonNull Long id) {
        return userService.findById(id);
    }

    @Override
    public Optional<UserDto> restoreToSnapshot(@NonNull Long userId, @NonNull Long snapshotId, @NonNull Long actingUserId) {
        return userService.restoreToSnapshot(userId, snapshotId, actingUserId);
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
    public Map<Long, String> findActorNames(@NonNull Collection<Long> ids) {
        return userService.findActorNames(ids);
    }

    @Override
    public List<ChangeEntry> expandActivityFields(@NonNull AuditTimelineItemDto<AuditableSnapshot> item) {
        return userService.expandActivityFields(item);
    }

    @Override
    public UserSettingsDto loadSettings(@NonNull Long userId) {
        return settingsService.load(userId);
    }

    @Override
    public void saveSettings(@NonNull Long userId, @NonNull UserSettingsDto settings) {
        settingsService.save(userId, settings);
    }

    @Override
    public boolean isAdmin(@NonNull UserDto user) {
        return roleChecker.isAdmin(user);
    }

    @Override
    public boolean isModerator(@NonNull UserDto user) {
        return roleChecker.isModerator(user);
    }

    @Override
    public boolean isOwner(@NonNull UserDto user, @NonNull UserIdMarker target) {
        return ownershipChecker.isOwner(user, target);
    }

    @Override
    public boolean isOwner(@NonNull UserDto user, @NonNull Long ownerId) {
        return ownershipChecker.isOwner(user, ownerId);
    }

}
