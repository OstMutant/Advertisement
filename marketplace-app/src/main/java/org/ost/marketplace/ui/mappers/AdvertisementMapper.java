package org.ost.marketplace.ui.mappers;

import org.mapstruct.Mapper;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.marketplace.ui.dto.AdvertisementEditDto;

@Mapper(componentModel = "spring")
public interface AdvertisementMapper {

    AdvertisementEditDto toAdvertisementEdit(AdvertisementInfoDto dto);
}
