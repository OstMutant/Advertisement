package org.ost.marketplace.ui.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.ost.marketplace.ui.dto.UserEditDto;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserEditDto toUserEdit(UserDto user);

    UserProfileDto copy(UserEditDto dto);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "version", source = "version")
    UserEditDto fromSnapshot(UserSnapshotDto snapshot, Long id, Long version);

}
