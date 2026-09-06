package org.ost.marketplace.ui.mappers;

import org.mapstruct.Mapper;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.marketplace.ui.dto.ProviderProfileEditDto;

/** MapStruct-generated mapper: read-only domain {@code ProviderProfileDto} to the mutable
 *  form-binding {@code ProviderProfileEditDto} the Provider Profile edit form binds against. */
@Mapper(componentModel = "spring")
public interface ProviderProfileMapper {

    ProviderProfileEditDto toProviderProfileEdit(ProviderProfileDto dto);
}
