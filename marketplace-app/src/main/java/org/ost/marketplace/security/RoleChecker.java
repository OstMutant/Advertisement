package org.ost.marketplace.security;

import org.ost.platform.core.model.Role;
import org.ost.marketplace.entities.User;
import org.springframework.stereotype.Component;

@Component
public class RoleChecker {

    public boolean isAdmin(User user) {
        return hasRole(user, Role.ADMIN);
    }

    public boolean isModerator(User user) {
        return hasRole(user, Role.MODERATOR);
    }

    public boolean isUser(User user) {
        return hasRole(user, Role.USER);
    }

    private boolean hasRole(User user, Role role) {
        return user != null && user.getRole() == role;
    }
}

