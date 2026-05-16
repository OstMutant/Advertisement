package org.ost.advertisement.dto;

import lombok.experimental.FieldNameConstants;
import org.ost.advertisement.core.model.Role;
import org.ost.advertisement.security.UserIdMarker;

@FieldNameConstants
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
