package org.ost.platform.user.spi;

import lombok.NonNull;
import org.ost.platform.user.dto.UserSettingsDto;

/**
 * Port: marketplace → user-starter.
 * Read/write per-actor settings (pagination defaults, etc.) and locale.
 * Implementation lives in user-spring-boot-starter.
 */
public interface UserPreferencesPort {

    UserSettingsDto loadSettings(@NonNull Long userId);

    void saveSettings(@NonNull Long userId, @NonNull UserSettingsDto settings);

    void updateLocale(@NonNull Long userId, @NonNull String locale);

    String findLocale(@NonNull Long userId);
}
