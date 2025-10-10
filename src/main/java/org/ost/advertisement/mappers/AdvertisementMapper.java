package org.ost.advertisement.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.ost.advertisement.dto.AdvertisementEdit;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.entities.Advertisement;

@Mapper(componentModel = "spring")
public interface AdvertisementMapper {

	@Mapping(target = "updatedAt", ignore = true)
	@Mapping(source = "userId", target = "createdByUserId")
	Advertisement toAdvertisement(AdvertisementEdit dto);

	AdvertisementEdit toAdvertisementEdit(AdvertisementView dto);

}
