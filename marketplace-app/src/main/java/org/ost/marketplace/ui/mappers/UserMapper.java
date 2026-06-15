package org.ost.marketplace.ui.mappers;

import org.mapstruct.Mapper;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.marketplace.ui.dto.UserEditDto;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserEditDto toUserEdit(UserDto user);

    UserProfileDto copy(UserEditDto dto);

}
