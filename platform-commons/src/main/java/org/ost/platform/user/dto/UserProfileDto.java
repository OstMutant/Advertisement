package org.ost.platform.user.dto;

import lombok.experimental.FieldNameConstants;
import org.ost.platform.user.model.Role;

@FieldNameConstants
public record UserProfileDto(
        Long id,
        String name,
        Role role
) {}
