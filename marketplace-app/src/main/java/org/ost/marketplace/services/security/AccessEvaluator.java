package org.ost.marketplace.services.security;

import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.AuthorizationService;
import org.ost.orchestrator.services.CurrentUserService;
import org.ost.platform.user.dto.UserDto;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public class AccessEvaluator {

    private final AuthorizationService authorizationService;
    private final CurrentUserService   currentUserService;

    public boolean isLoggedIn() {
        return currentUserService.getCurrentUser().isPresent();
    }

    public boolean isPrivileged() {
        return currentUser().map(u -> authorizationService.isAdmin(u) || authorizationService.isModerator(u)).orElse(false);
    }

    public Long getCurrentUserId() {
        return currentUser().map(UserDto::id).orElse(null);
    }

    public boolean canView() {
        return isPrivileged();
    }

    public boolean canNotEdit(Long ownerUserId) {
        return !canOperate(ownerUserId);
    }

    public boolean canNotDelete(Long ownerUserId) {
        return !canOperate(ownerUserId);
    }

    public boolean canOperate(Long ownerUserId) {
        return canOperate(u -> authorizationService.isOwner(u, ownerUserId));
    }

    private boolean canOperate(Predicate<UserDto> isOwner) {
        return currentUser()
                .map(u -> authorizationService.isAdmin(u) || authorizationService.isModerator(u) || isOwner.test(u))
                .orElse(false);
    }

    private Optional<UserDto> currentUser() {
        return currentUserService.getCurrentUser();
    }
}
