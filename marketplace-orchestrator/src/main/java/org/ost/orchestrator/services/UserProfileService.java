package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.spi.UserAccountPort;
import org.ost.platform.user.spi.UserPort;
import org.ost.platform.user.spi.UserPreferencesPort;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Mandatory direct {@link UserPort}/{@link UserAccountPort}/{@link UserPreferencesPort} fields —
 * {@code user-spring-boot-starter} is a compile-scope, non-optional dependency of the final app,
 * matching {@link UserDeleteService}'s existing {@code UserAccountPort} precedent.
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserPort            userPort;
    private final UserAccountPort     accountPort;
    private final UserPreferencesPort preferencesPort;

    public Optional<UserDto> findById(@NonNull Long id) {
        return userPort.findById(id);
    }

    public void save(@NonNull UserProfileDto dto, @NonNull Long actingUserId) {
        accountPort.save(dto, actingUserId);
    }

    public UserSettingsDto loadSettings(@NonNull Long userId) {
        return preferencesPort.loadSettings(userId);
    }

    public void saveSettings(@NonNull Long userId, @NonNull UserSettingsDto settings) {
        preferencesPort.saveSettings(userId, settings);
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
