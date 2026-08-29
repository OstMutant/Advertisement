package org.ost.marketplace.services.security;

import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.AuthorizationService;
import org.ost.orchestrator.services.CurrentUserService;
import org.ost.platform.user.dto.UserDto;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Predicate;

/** Delegates every authorization/ownership check to {@link AuthorizationService} rather than
 *  holding a direct {@code UserAuthorizationPort}, so a future non-Vaadin caller can reuse it. */
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

    /** Self, admin, or moderator may open the account (view its Name/Settings/Provider Profile tabs). */
    public boolean canViewUserAccount(Long targetUserId) {
        return canOperate(targetUserId);
    }

    /** Only self or admin may edit any account tab -- a moderator viewing another user's account
     *  gets read-only across all tabs, never partial edit rights. */
    public boolean canEditUserAccount(Long targetUserId) {
        return currentUser()
                .map(u -> authorizationService.isAdmin(u) || authorizationService.isOwner(u, targetUserId))
                .orElse(false);
    }

    /** Role is a more privileged sub-permission than the rest of the Name tab: only an admin
     *  editing someone *else's* account may change it -- never their own role (even an admin's),
     *  and never a moderator's at all. */
    public boolean canEditRole(Long targetUserId) {
        return currentUser()
                .map(u -> authorizationService.isAdmin(u) && !authorizationService.isOwner(u, targetUserId))
                .orElse(false);
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
