package org.ost.advertisement.security;

import org.ost.advertisement.entities.User;
import org.springframework.stereotype.Component;

@Component
public class OwnershipChecker {

    public boolean isOwner(User user, Long ownerId) {
        return user != null && user.getId() != null && user.getId().equals(ownerId);
    }

    public boolean isOwner(User user, UserIdMarker target) {
        return target != null && isOwner(user, target.getOwnerUserId());
    }
}
