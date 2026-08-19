package org.ost.orchestrator.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.spi.UserSettingsChangedHook;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserSettingsChangedHookImpl implements UserSettingsChangedHook {

    private final SettingsChangeHook forwardHook;

    @Override
    public void onSettingsChanged(@NonNull Long userId, @NonNull UserSettingsDto settings) {
        forwardHook.onSettingsChanged(userId, settings);
    }
}
