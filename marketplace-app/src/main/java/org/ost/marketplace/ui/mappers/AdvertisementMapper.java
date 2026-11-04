package org.ost.marketplace.ui.mappers;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.marketplace.ui.dto.AdvertisementEditDto;

@Mapper(componentModel = "spring")
public interface AdvertisementMapper {

    AdvertisementEditDto toAdvertisementEdit(AdvertisementInfoDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdByUserName", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    void restoreFromSnapshot(AdvertisementSnapshotDto snapshot, @MappingTarget AdvertisementEditDto dto);
}
