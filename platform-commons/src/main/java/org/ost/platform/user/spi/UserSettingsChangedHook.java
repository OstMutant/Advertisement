package org.ost.platform.user.spi;

import lombok.NonNull;
import org.ost.platform.user.dto.UserSettingsDto;

public interface UserSettingsChangedHook {
    void onSettingsChanged(@NonNull Long userId, @NonNull UserSettingsDto settings);
}
