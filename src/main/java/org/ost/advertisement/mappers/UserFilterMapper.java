package org.ost.advertisement.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.ost.advertisement.dto.filter.UserFilter;

@Mapper(componentModel = "spring")
public interface UserFilterMapper {

	void update(@MappingTarget UserFilter target, UserFilter source);
}
