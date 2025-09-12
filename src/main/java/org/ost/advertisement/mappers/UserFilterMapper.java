package org.ost.advertisement.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.ost.advertisement.dto.filter.UserFilter;

@Mapper(componentModel = "spring")
public interface UserFilterMapper extends FilterMapper<UserFilter> {

	void update(@MappingTarget UserFilter target, UserFilter source);

	UserFilter copy(UserFilter source);
}
