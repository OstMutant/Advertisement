package org.ost.marketplace.ui.mappers;

import org.mapstruct.Mapper;
import org.ost.marketplace.dto.UserProfileDto;
import org.ost.marketplace.entities.User;
import org.ost.marketplace.ui.dto.UserEditDto;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserEditDto toUserEdit(User user);

    UserProfileDto copy(UserEditDto dto);

}
