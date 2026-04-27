package org.ost.advertisement.ui.mappers;

import org.mapstruct.Mapper;
import org.ost.advertisement.dto.UserProfileDto;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.ui.dto.UserEditDto;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserEditDto toUserEdit(User user);

    UserProfileDto copy(UserEditDto dto);

}
