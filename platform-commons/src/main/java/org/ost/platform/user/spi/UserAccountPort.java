package org.ost.platform.user.spi;

import lombok.NonNull;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserProfileDto;

public interface UserAccountPort {

    void save(@NonNull UserProfileDto dto, @NonNull Long actingUserId);

    void delete(@NonNull Long userId, @NonNull Long actingUserId);

    void register(@NonNull SignUpDto dto, @NonNull String clientIp);

    void refreshCurrentUserInContext(@NonNull Long userId);
}
