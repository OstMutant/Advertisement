package org.ost.advertisement.mappers.filters;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.ost.advertisement.dto.filter.UserFilterDto;

@Mapper(componentModel = "spring")
public interface UserFilterMapper extends FilterMapper<UserFilterDto> {

	void update(@MappingTarget UserFilterDto target, UserFilterDto source);

	UserFilterDto copy(UserFilterDto source);
}
