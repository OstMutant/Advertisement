package org.ost.advertisement.security;


import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.utils.AuthUtil;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessEvaluator {

    private final RoleChecker roleChecker;
    private final OwnershipChecker ownershipChecker;


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
        return roleChecker.isAdmin(currentUser) || ownershipChecker.isOwner(currentUser, target);
    }

    protected User getCurrentUser() {
        return AuthUtil.getCurrentUser();
    }
}
