package org.ost.user.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.spi.UserAccountPort;
import org.ost.user.services.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAccountPortImpl implements UserAccountPort {

    private final UserService userService;

    @Override
    @Transactional
    public void save(@NonNull UserProfileDto dto, @NonNull Long actingUserId) {
        userService.save(dto, actingUserId);
    }

    @Override
    @Transactional
    public void delete(@NonNull Long userId, @NonNull Long actingUserId) {
        userService.delete(userId, actingUserId);
    }

    @Override
    @Transactional
    public void register(@NonNull SignUpDto dto, @NonNull String clientIp) {
        userService.register(dto, clientIp);
    }

    @Override
    public void refreshCurrentUserInContext(@NonNull Long userId) {
        userService.refreshSecurityContext(userId);
    }
}
