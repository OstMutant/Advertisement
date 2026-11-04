package org.ost.platform.user.spi;

import lombok.NonNull;

import org.ost.platform.user.dto.UserSettingsDto;

import java.util.Optional;

/**
 * Read/write per-actor settings (pagination defaults, etc.) and locale.
 * Implementation lives in user-spring-boot-starter.
 */
public interface UserPreferencesPort {

    UserSettingsDto loadSettings(@NonNull Long userId);

    void saveSettings(@NonNull Long userId, @NonNull UserSettingsDto settings);

    void updateLocale(@NonNull Long userId, @NonNull String locale);

    /** The user's saved UI language tag (for example {@code uk}), empty when the user has never chosen one. */
    Optional<String> findLocale(@NonNull Long userId);
}
