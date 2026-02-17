package org.ost.advertisement.security;

import org.ost.advertisement.entities.User;
import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

@Component
public class OwnershipChecker {

    public boolean isOwner(User user, Long ownerId) {
        return ofNullable(user).map(User::getId).filter(v -> v.equals(ownerId)).isPresent();
    }

    public boolean isOwner(User user, UserIdMarker target) {
        return ofNullable(target).map(UserIdMarker::getOwnerUserId).filter(v -> isOwner(user, v)).isPresent();
    }
}
