package org.ost.advertisement.dto;

import org.ost.advertisement.entities.Role;
import org.ost.advertisement.security.UserIdMarker;

public record UserProfileDto(
        Long id,
        String name,
        Role role
) implements UserIdMarker {

    @Override
    public Long getOwnerUserId() {
        return id;
    }

}
