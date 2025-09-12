package org.ost.advertisement.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.ost.advertisement.dto.filter.AdvertisementFilter;

@Mapper(componentModel = "spring")
public interface AdvertisementFilterMapper extends FilterMapper<AdvertisementFilter> {

	void update(@MappingTarget AdvertisementFilter target, AdvertisementFilter source);

	AdvertisementFilter copy(AdvertisementFilter source);
}
