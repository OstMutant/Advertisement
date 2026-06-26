package org.ost.platform.user.dto;

import lombok.experimental.FieldNameConstants;
import org.ost.platform.user.model.Role;

import java.time.Instant;

@FieldNameConstants
public record UserDto(
        Long id,
        String name,
        String email,
        Role role,
        Instant createdAt,
        Instant updatedAt,
        String locale
) {}
