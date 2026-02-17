package org.ost.advertisement.ui.dto;

import lombok.*;
import org.ost.advertisement.entities.Role;

import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class UserEditDto {

    private Long id;

    private String name;
    private String email;
    private String passwordHash;

    private Role role;

    private Instant createdAt;
    private Instant updatedAt;

    private String locale;

}
