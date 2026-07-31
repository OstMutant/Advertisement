package org.ost.marketplace.services.security;

import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.spi.UserAuthorizationPort;
import org.ost.platform.user.spi.UserIdMarker;
import org.ost.marketplace.services.auth.AuthContextService;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public class AccessEvaluator {

    private final UserAuthorizationPort authorizationPort;
    private final AuthContextService    authContextService;

    public boolean isLoggedIn() {
        return authContextService.getCurrentUser().isPresent();
    }

    public boolean isPrivileged() {
        return currentUser().map(u -> authorizationPort.isAdmin(u) || authorizationPort.isModerator(u)).orElse(false);
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
        return canOperate(u -> authorizationPort.isOwner(u, target));
    }

    public boolean canNotEdit(Long ownerUserId) {
        return !canOperate(ownerUserId);
    }

    public boolean canNotDelete(Long ownerUserId) {
        return !canOperate(ownerUserId);
    }

    public boolean canOperate(Long ownerUserId) {
        return canOperate(u -> authorizationPort.isOwner(u, ownerUserId));
    }

    private boolean canOperate(Predicate<UserDto> isOwner) {
        return currentUser()
                .map(u -> authorizationPort.isAdmin(u) || authorizationPort.isModerator(u) || isOwner.test(u))
                .orElse(false);
    }

    private Optional<UserDto> currentUser() {
        return authContextService.getCurrentUser();
    }
}
