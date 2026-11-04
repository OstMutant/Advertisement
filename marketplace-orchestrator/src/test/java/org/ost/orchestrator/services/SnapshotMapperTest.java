package org.ost.orchestrator.services;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.advertisement.model.AdKind;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.platform.user.dto.SettingsSnapshotDto;
import org.ost.platform.user.dto.UserSettingsDto;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Field-by-field checks of {@link SnapshotMapper}: every snapshot value lands on its save DTO, and id/version come from the parameters. */
class SnapshotMapperTest {

    private final SnapshotMapper mapper = Mappers.getMapper(SnapshotMapper.class);

    @Test
    void toSaveDto_advertisement_mapsEveryFieldAndTakesIdAndVersionFromParameters() {
        AdvertisementSnapshotDto snapshot = new AdvertisementSnapshotDto("Title", "Description", AdKind.REQUEST,
                List.of(3L, 1L), 9L, 4L);

        AdvertisementSaveDto dto = mapper.toSaveDto(snapshot, 7L, 2L);

        assertThat(dto.id()).isEqualTo(7L);
        assertThat(dto.title()).isEqualTo("Title");
        assertThat(dto.description()).isEqualTo("Description");
        assertThat(dto.adKind()).isEqualTo(AdKind.REQUEST);
        assertThat(dto.categoryIds()).isEqualTo(Set.of(1L, 3L));
        assertThat(dto.cityTaxonId()).isEqualTo(9L);
        assertThat(dto.version()).isEqualTo(2L);
    }

    @Test
    void toSaveDto_providerProfile_mapsEveryFieldAndTakesIdAndVersionFromParameters() {
        ProviderProfileSnapshotDto snapshot = new ProviderProfileSnapshotDto(ProviderKind.SHOP, "About", List.of(5L, 2L), 8L,
                "+380501234567", "shop_handle", "+380501234568");

        ProviderProfileSaveDto dto = mapper.toSaveDto(snapshot, 11L, 6L);

        assertThat(dto.id()).isEqualTo(11L);
        assertThat(dto.kind()).isEqualTo(ProviderKind.SHOP);
        assertThat(dto.about()).isEqualTo("About");
        assertThat(dto.categoryIds()).isEqualTo(Set.of(2L, 5L));
        assertThat(dto.cityTaxonId()).isEqualTo(8L);
        assertThat(dto.phone()).isEqualTo("+380501234567");
        assertThat(dto.telegram()).isEqualTo("shop_handle");
        assertThat(dto.viber()).isEqualTo("+380501234568");
        assertThat(dto.version()).isEqualTo(6L);
    }

    @Test
    void toSettings_mapsEverySizeAndTakesVersionFromParameter() {
        SettingsSnapshotDto snapshot = new SettingsSnapshotDto(30, 31, 32, 33, 34, 35);

        UserSettingsDto settings = mapper.toSettings(snapshot, 4L);

        assertThat(settings.getAdsPageSize()).isEqualTo(30);
        assertThat(settings.getUsersPageSize()).isEqualTo(31);
        assertThat(settings.getTimelinePageSize()).isEqualTo(32);
        assertThat(settings.getProviderProfilesPageSize()).isEqualTo(33);
        assertThat(settings.getFeedbackPageSize()).isEqualTo(34);
        assertThat(settings.getModerationPageSize()).isEqualTo(35);
        assertThat(settings.getVersion()).isEqualTo(4L);
    }
}
