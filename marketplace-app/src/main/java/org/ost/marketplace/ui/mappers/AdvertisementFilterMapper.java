package org.ost.marketplace.ui.mappers;

import org.mapstruct.Mapper;
import org.ost.ui.query.filter.FilterMapper;
import org.mapstruct.MappingTarget;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;

@Mapper(componentModel = "spring")
public interface AdvertisementFilterMapper extends FilterMapper<AdvertisementFilterDto> {

    void update(@MappingTarget AdvertisementFilterDto target, AdvertisementFilterDto source);

    AdvertisementFilterDto copy(AdvertisementFilterDto source);
}
