package org.ost.advertisement.ui.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.ui.dto.UserEditDto;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "updatedAt", ignore = true)
    User toUser(UserEditDto dto);

    UserEditDto toUserEdit(User dto);

}
