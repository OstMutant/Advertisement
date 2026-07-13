package org.ost.marketplace.ui.dto;

import lombok.*;
import org.ost.platform.user.model.Role;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class UserEditDto implements EditDto {

    private Long id;
    private String name;
    private Role role;
    private Long version;

}
