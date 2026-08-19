package org.ost.platform.user.spi;

import lombok.NonNull;
import org.ost.platform.user.dto.UserSettingsDto;

/**
 * Hook: user-starter → marketplace.
 * User-starter calls this after a settings save; marketplace resets any cached UI state derived
 * from settings (e.g. pagination defaults) for the affected user's active session.
 */
public interface UserSettingsChangedHook {
    void onSettingsChanged(@NonNull Long userId, @NonNull UserSettingsDto settings);
}
