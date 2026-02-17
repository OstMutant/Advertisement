package org.ost.advertisement.ui.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.ui.dto.AdvertisementEditDto;

@Mapper(componentModel = "spring")
public interface AdvertisementMapper {

    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lastModifiedByUserId", ignore = true)
    Advertisement toAdvertisement(AdvertisementEditDto dto);

    AdvertisementEditDto toAdvertisementEdit(AdvertisementInfoDto dto);

}
