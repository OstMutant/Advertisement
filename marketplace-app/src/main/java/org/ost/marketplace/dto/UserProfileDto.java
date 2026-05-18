package org.ost.marketplace.dto;

import lombok.experimental.FieldNameConstants;
import org.ost.platform.core.model.Role;
import org.ost.marketplace.security.UserIdMarker;

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
