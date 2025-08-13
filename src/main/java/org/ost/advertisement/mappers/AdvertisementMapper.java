package org.ost.advertisement.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.entities.Advertisement;

@Mapper
public interface AdvertisementMapper {

	AdvertisementMapper INSTANCE = Mappers.getMapper(AdvertisementMapper.class);

	Advertisement dtoToEntity(AdvertisementView dto);

}
