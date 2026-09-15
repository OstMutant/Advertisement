package org.ost.user.security;

import lombok.NonNull;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.model.Role;
import org.springframework.stereotype.Component;

/** Checks whether a {@link UserDto} holds the ADMIN or MODERATOR role. */
@Component
public class RoleChecker {

    public boolean isAdmin(@NonNull UserDto user) {
        return hasRole(user, Role.ADMIN);
    }

    public boolean isModerator(@NonNull UserDto user) {
        return hasRole(user, Role.MODERATOR);
    }

    private boolean hasRole(UserDto user, Role role) {
        return user.role() == role;
    }
}
