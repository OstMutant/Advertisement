package org.ost.marketplace.ui.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.marketplace.ui.dto.ProviderProfileEditDto;

/** MapStruct-generated mapper: read-only domain {@code ProviderProfileDto} to the mutable
 *  form-binding {@code ProviderProfileEditDto} the Provider Profile edit form binds against. */
@Mapper(componentModel = "spring")
public interface ProviderProfileMapper {

    ProviderProfileEditDto toProviderProfileEdit(ProviderProfileDto dto);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "version", source = "version")
    ProviderProfileEditDto fromSnapshot(ProviderProfileSnapshotDto snapshot, Long id, Long version);
}
