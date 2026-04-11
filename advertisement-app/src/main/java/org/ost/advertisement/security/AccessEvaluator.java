package org.ost.advertisement.security;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.auth.AuthContextService;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
        return currentUser()
                .map(u -> roleChecker.isAdmin(u) || roleChecker.isModerator(u))
                .orElse(false);
    }

    public boolean canNotEdit(UserIdMarker target) {
        return !canOperate(target);
    }

    public boolean canNotDelete(UserIdMarker target) {
        return !canOperate(target);
    }

    public boolean canOperate(UserIdMarker target) {
        return currentUser()
                .map(u -> roleChecker.isAdmin(u)
                        || roleChecker.isModerator(u)
                        || ownershipChecker.isOwner(u, target))
                .orElse(false);
    }

    private Optional<User> currentUser() {
        return authContextService.getCurrentUser();
    }
}
