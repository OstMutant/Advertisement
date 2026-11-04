package org.ost.orchestrator.services;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.platform.user.dto.SettingsSnapshotDto;
import org.ost.platform.user.dto.UserSettingsDto;

/** Maps a recorded snapshot onto its domain's save data; an unmapped target field fails the build, so a field added later cannot be silently skipped by a restore. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SnapshotMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "version", source = "version")
    AdvertisementSaveDto toSaveDto(AdvertisementSnapshotDto snapshot, Long id, Long version);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "version", source = "version")
    ProviderProfileSaveDto toSaveDto(ProviderProfileSnapshotDto snapshot, Long id, Long version);

    @Mapping(target = "version", source = "version")
    UserSettingsDto toSettings(SettingsSnapshotDto snapshot, Long version);
}
