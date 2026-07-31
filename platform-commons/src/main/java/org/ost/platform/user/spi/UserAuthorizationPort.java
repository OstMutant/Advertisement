package org.ost.platform.user.spi;

import lombok.NonNull;
import org.ost.platform.user.dto.UserDto;

public interface UserAuthorizationPort {

    boolean isAdmin(@NonNull UserDto user);

    boolean isModerator(@NonNull UserDto user);

    boolean isOwner(@NonNull UserDto user, @NonNull UserIdMarker target);

    boolean isOwner(@NonNull UserDto user, @NonNull Long ownerId);
}
