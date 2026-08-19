package org.ost.platform.user.spi;

import lombok.NonNull;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserProfileDto;

/**
 * Port: marketplace → user-starter.
 * Mutate side: save/delete a profile, register a new account, refresh the current user's security
 * context after an in-place edit. Implementation lives in user-spring-boot-starter.
 */
public interface UserAccountPort {

    void save(@NonNull UserProfileDto dto, @NonNull Long actingUserId);

    void delete(@NonNull Long userId, @NonNull Long actingUserId);

    void register(@NonNull SignUpDto dto, @NonNull String clientIp);

    void refreshCurrentUserInContext(@NonNull Long userId);
}
