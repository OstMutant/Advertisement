package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.model.PageSizeLimits;
import org.ost.platform.user.spi.UserAccountPort;
import org.ost.platform.user.spi.UserPort;
import org.ost.platform.user.spi.UserPreferencesPort;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Profile/settings read+write; {@link #save} enforces self-or-admin authorization. Mandatory
 * direct {@link UserPort}/{@link UserAccountPort}/{@link UserPreferencesPort} fields —
 * {@code user-spring-boot-starter} is a compile-scope, non-optional dependency of the final app,
 * matching {@link UserDeleteService}'s existing {@code UserAccountPort} precedent.
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserPort            userPort;
    private final UserAccountPort     accountPort;
    private final UserPreferencesPort preferencesPort;
    private final AuthorizationService authorizationService;

    public Optional<UserDto> findById(@NonNull Long id) {
        return userPort.findById(id);
    }

    public void save(@NonNull UserProfileDto dto, @NonNull Long actingUserId) {
        Optional<UserDto> actor = userPort.findById(actingUserId);
        if (actor.isEmpty()) {
            authorizationService.requireCanEditAccount(actingUserId, dto.id());
            return;
        }
        authorizationService.requireCanEditAccount(actor.get(), dto.id());

        // Self-edit reuses the actor row already fetched above instead of fetching it again.
        Optional<UserDto> target = actingUserId.equals(dto.id()) ? actor : userPort.findById(dto.id());
        boolean roleChanged = target.map(existing -> existing.role() != dto.role()).orElse(false);
        if (roleChanged) {
            authorizationService.requireCanEditRole(actor.get(), dto.id());
        }
        accountPort.save(dto, actingUserId);
    }

    public UserSettingsDto loadSettings(@NonNull Long userId) {
        return preferencesPort.loadSettings(userId);
    }

    public void saveSettings(@NonNull Long userId, @NonNull UserSettingsDto settings) {
        preferencesPort.saveSettings(userId, settings);
    }

    /** Effective page size for an advertisements list — the caller's own saved setting, or the shared default for an anonymous (actorId == null) caller. */
    public int resolveAdsPageSize(Long actorId) {
        return actorId == null ? PageSizeLimits.DEFAULT_PAGE_SIZE : loadSettings(actorId).getAdsPageSize();
    }

    /** Effective page size for a users list — same resolution as {@link #resolveAdsPageSize}. */
    public int resolveUsersPageSize(Long actorId) {
        return actorId == null ? PageSizeLimits.DEFAULT_PAGE_SIZE : loadSettings(actorId).getUsersPageSize();
    }

    public List<UserDto> getFiltered(@NonNull UserFilterDto filter, int page, int size, @NonNull Sort sort) {
        return userPort.getFiltered(filter, page, size, sort);
    }

    public List<UserDto> getFilteredByOffset(@NonNull UserFilterDto filter, long offset, int limit, @NonNull Sort sort) {
        return userPort.getFilteredByOffset(filter, offset, limit, sort);
    }

    public int count(@NonNull UserFilterDto filter) {
        return userPort.count(filter);
    }

    public Optional<UserDto> findByEmail(@NonNull String email) {
        return userPort.findByEmail(email);
    }

    public void register(@NonNull SignUpDto dto, @NonNull String clientIp) {
        accountPort.register(dto, clientIp);
    }

    public void updateLocale(@NonNull Long userId, @NonNull String locale) {
        preferencesPort.updateLocale(userId, locale);
    }

    public void refreshCurrentUserInContext(@NonNull Long userId) {
        accountPort.refreshCurrentUserInContext(userId);
    }
}
