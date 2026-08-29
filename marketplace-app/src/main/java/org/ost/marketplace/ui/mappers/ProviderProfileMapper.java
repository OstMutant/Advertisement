package org.ost.marketplace.ui.mappers;

import org.mapstruct.Mapper;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.marketplace.ui.dto.ProviderProfileEditDto;

@Mapper(componentModel = "spring")
public interface ProviderProfileMapper {

    ProviderProfileEditDto toProviderProfileEdit(ProviderProfileDto dto);
}
