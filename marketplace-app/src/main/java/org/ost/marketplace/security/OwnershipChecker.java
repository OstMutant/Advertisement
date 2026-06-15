package org.ost.marketplace.security;

import org.ost.platform.user.dto.UserDto;
import org.springframework.stereotype.Component;

@Component
public class OwnershipChecker {

    public boolean isOwner(UserDto user, Long ownerId) {
        return user != null && user.id() != null && user.id().equals(ownerId);
    }

    public boolean isOwner(UserDto user, UserIdMarker target) {
        return target != null && isOwner(user, target.getOwnerUserId());
    }
}
