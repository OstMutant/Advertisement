package org.ost.user.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.spi.UserPreferencesPort;
import org.ost.user.services.UserPreferencesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPreferencesPortImpl implements UserPreferencesPort {

    private final UserPreferencesService preferencesService;

    @Override
    public UserSettingsDto loadSettings(@NonNull Long userId) {
        return preferencesService.load(userId);
    }

    @Override
    @Transactional
    public void saveSettings(@NonNull Long userId, @NonNull UserSettingsDto settings) {
        preferencesService.save(userId, settings);
    }

    @Override
    @Transactional
    public void updateLocale(@NonNull Long userId, @NonNull String locale) {
        preferencesService.updateLocale(userId, locale);
    }
}
