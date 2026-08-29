package org.ost.platform.user.spi;

import lombok.NonNull;
import org.ost.platform.user.dto.UserDto;

/**
 * Role and ownership checks (isAdmin/isModerator/isOwner) used by UI access control.
 * Implementation lives in user-spring-boot-starter.
 */
public interface UserAuthorizationPort {

    boolean isAdmin(@NonNull UserDto user);

    boolean isModerator(@NonNull UserDto user);

    boolean isOwner(@NonNull UserDto user, @NonNull Long ownerId);
}
