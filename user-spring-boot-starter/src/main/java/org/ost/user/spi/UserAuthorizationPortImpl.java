package org.ost.user.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.spi.UserAuthorizationPort;
import org.ost.platform.user.spi.UserIdMarker;
import org.ost.user.security.OwnershipChecker;
import org.ost.user.security.RoleChecker;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAuthorizationPortImpl implements UserAuthorizationPort {

    private final RoleChecker      roleChecker;
    private final OwnershipChecker ownershipChecker;

    @Override
    public boolean isAdmin(@NonNull UserDto user) {
        return roleChecker.isAdmin(user);
    }

    @Override
    public boolean isModerator(@NonNull UserDto user) {
        return roleChecker.isModerator(user);
    }

    @Override
    public boolean isOwner(@NonNull UserDto user, @NonNull UserIdMarker target) {
        return ownershipChecker.isOwner(user, target);
    }

    @Override
    public boolean isOwner(@NonNull UserDto user, @NonNull Long ownerId) {
        return ownershipChecker.isOwner(user, ownerId);
    }
}
