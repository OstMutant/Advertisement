package org.ost.advertisement.security;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.auth.AuthContextService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessEvaluator {

    private final RoleChecker roleChecker;
    private final OwnershipChecker ownershipChecker;
    private final AuthContextService authContextService;

    public boolean isLoggedIn() {
        return authContextService.getCurrentUser().isPresent();
    }

    public boolean canView() {
        User currentUser = getCurrentUser();
        return roleChecker.isAdmin(currentUser) || roleChecker.isModerator(currentUser);
    }

    public boolean canNotEdit(UserIdMarker target) {
        return !canOperate(target);
    }

    public boolean canNotDelete(UserIdMarker target) {
        return !canOperate(target);
    }

    public boolean canOperate(UserIdMarker target) {
        User currentUser = getCurrentUser();
        return roleChecker.isAdmin(currentUser)
                || roleChecker.isModerator(currentUser)
                || ownershipChecker.isOwner(currentUser, target);
    }

    protected User getCurrentUser() {
        return authContextService.getCurrentUser().orElse(null);
    }
}