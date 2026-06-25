package org.ost.platform.user.spi;

import org.ost.platform.user.dto.UserDto;

public interface AuthenticatedPrincipal {
    UserDto toUserDto();
}
