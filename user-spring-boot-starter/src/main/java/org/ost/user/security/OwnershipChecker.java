package org.ost.user.security;

import lombok.NonNull;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.security.UserIdMarker;
import org.springframework.stereotype.Component;

@Component
public class OwnershipChecker {

    public boolean isOwner(@NonNull UserDto user, @NonNull Long ownerId) {
        return ownerId.equals(user.id());
    }

    public boolean isOwner(@NonNull UserDto user, @NonNull UserIdMarker target) {
        return isOwner(user, target.getOwnerUserId());
    }
}
