package org.ost.marketplace.mappers.filters;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.ost.marketplace.dto.filter.AdvertisementFilterDto;

@Mapper(componentModel = "spring")
public interface AdvertisementFilterMapper extends FilterMapper<AdvertisementFilterDto> {

    void update(@MappingTarget AdvertisementFilterDto target, AdvertisementFilterDto source);

    AdvertisementFilterDto copy(AdvertisementFilterDto source);
}
