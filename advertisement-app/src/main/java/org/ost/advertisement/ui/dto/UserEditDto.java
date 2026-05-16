package org.ost.advertisement.ui.dto;

import lombok.*;
import org.ost.advertisement.core.model.Role;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class UserEditDto implements EditDto {

    private Long id;
    private String name;
    private Role role;

}
