package org.ost.platform.user.spi;

/**
 * Marker interface for "something with an owning user id" — implemented by DTOs that need an
 * ownership check via {@link UserAuthorizationPort#isOwner(org.ost.platform.user.dto.UserDto, UserIdMarker)}.
 */
public interface UserIdMarker {

    Long getOwnerUserId();

}
