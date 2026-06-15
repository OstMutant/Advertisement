package org.ost.marketplace.security;

import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.model.Role;
import org.springframework.stereotype.Component;

@Component
public class RoleChecker {

    public boolean isAdmin(UserDto user) {
        return hasRole(user, Role.ADMIN);
    }

    public boolean isModerator(UserDto user) {
        return hasRole(user, Role.MODERATOR);
    }

    public boolean isUser(UserDto user) {
        return hasRole(user, Role.USER);
    }

    private boolean hasRole(UserDto user, Role role) {
        return user != null && user.role() == role;
    }
}
