package org.ost.marketplace.ui.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.ost.marketplace.ui.query.filter.FilterMapper;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;

/** MapStruct copy/update mapper for {@link ProviderProfileFilterDto}, backing {@link org.ost.marketplace.ui.query.filter.FilterProcessor}'s dirty-tracking. */
@Mapper(componentModel = "spring")
public interface ProviderProfileFilterMapper extends FilterMapper<ProviderProfileFilterDto> {

    void update(@MappingTarget ProviderProfileFilterDto target, ProviderProfileFilterDto source);

    ProviderProfileFilterDto copy(ProviderProfileFilterDto source);
}
