package org.ost.platform.user.spi;

import org.ost.platform.user.dto.UserDto;

/**
 * Spring Security principal contract implemented by user-spring-boot-starter's user entity —
 * holds the authenticated user's identity, role, and locale for the current request.
 */
public interface AuthenticatedPrincipal {
    UserDto toUserDto();

    String locale();
}
