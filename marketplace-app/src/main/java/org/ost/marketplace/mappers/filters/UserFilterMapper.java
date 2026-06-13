package org.ost.marketplace.mappers.filters;

import org.mapstruct.Mapper;
import org.ost.ui.query.filter.FilterMapper;
import org.mapstruct.MappingTarget;
import org.ost.marketplace.dto.filter.UserFilterDto;

@Mapper(componentModel = "spring")
public interface UserFilterMapper extends FilterMapper<UserFilterDto> {

    void update(@MappingTarget UserFilterDto target, UserFilterDto source);

    UserFilterDto copy(UserFilterDto source);
}
