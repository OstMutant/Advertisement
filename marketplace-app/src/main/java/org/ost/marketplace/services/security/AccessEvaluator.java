package org.ost.marketplace.services.security;

import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.security.UserIdMarker;
import org.ost.platform.user.spi.UserPort;
import org.ost.marketplace.services.auth.AuthContextService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccessEvaluator {

    private final UserPort           userPort;
    private final AuthContextService authContextService;

    public boolean isLoggedIn() {
        return authContextService.getCurrentUser().isPresent();
    }

    public boolean isPrivileged() {
        return currentUser().map(u -> userPort.isAdmin(u) || userPort.isModerator(u)).orElse(false);
    }

    public Long getCurrentUserId() {
        return currentUser().map(UserDto::id).orElse(null);
    }

    public boolean canView() {
        return isPrivileged();
    }

    public boolean canNotEdit(UserIdMarker target) {
        return !canOperate(target);
    }

    public boolean canNotDelete(UserIdMarker target) {
        return !canOperate(target);
    }

    public boolean canOperate(UserIdMarker target) {
        return currentUser()
                .map(u -> userPort.isAdmin(u) || userPort.isModerator(u) || userPort.isOwner(u, target))
                .orElse(false);
    }

    public boolean canNotEdit(Long ownerUserId) {
        return !canOperate(ownerUserId);
    }

    public boolean canNotDelete(Long ownerUserId) {
        return !canOperate(ownerUserId);
    }

    public boolean canOperate(Long ownerUserId) {
        return currentUser()
                .map(u -> userPort.isAdmin(u) || userPort.isModerator(u) || userPort.isOwner(u, ownerUserId))
                .orElse(false);
    }

    private Optional<UserDto> currentUser() {
        return authContextService.getCurrentUser();
    }
}
