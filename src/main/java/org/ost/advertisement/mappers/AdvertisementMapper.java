package org.ost.advertisement.mappers;

import org.mapstruct.Mapper;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.entities.Advertisement;

@Mapper(componentModel = "spring")
public interface AdvertisementMapper {

	Advertisement toAdvertisement(AdvertisementView dto);

}
