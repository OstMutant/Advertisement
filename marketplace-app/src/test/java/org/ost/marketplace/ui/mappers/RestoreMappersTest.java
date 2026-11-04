package org.ost.marketplace.ui.mappers;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.ost.marketplace.ui.dto.AdvertisementEditDto;
import org.ost.marketplace.ui.dto.ProviderProfileEditDto;
import org.ost.marketplace.ui.dto.UserEditDto;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.advertisement.model.AdKind;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.ost.platform.user.model.Role;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The mappers the restore handlers use to stage a snapshot's values into an edit form: every snapshot field arrives, and what the snapshot does not carry stays as it was. */
class RestoreMappersTest {

    private final AdvertisementMapper advertisementMapper = Mappers.getMapper(AdvertisementMapper.class);
    private final ProviderProfileMapper providerProfileMapper = Mappers.getMapper(ProviderProfileMapper.class);
    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @Test
    void advertisement_snapshotValuesOverlayTheForm_andIdentityAndAuditFieldsStay() {
        Instant created = Instant.parse("2026-01-01T10:00:00Z");
        AdvertisementEditDto form = AdvertisementEditDto.builder()
                .id(7L).version(3L).createdAt(created).createdBy(40L).createdByUserName("Owner")
                .title("Current title").description("Current description").adKind(AdKind.REQUEST)
                .categoryIds(new HashSet<>(Set.of(9L))).cityTaxonId(8L).build();
        AdvertisementSnapshotDto snapshot = new AdvertisementSnapshotDto("Original", "First description", AdKind.OFFER,
                List.of(2L, 1L), 5L, 11L);

        advertisementMapper.restoreFromSnapshot(snapshot, form);

        assertThat(form.getTitle()).isEqualTo("Original");
        assertThat(form.getDescription()).isEqualTo("First description");
        assertThat(form.getAdKind()).isEqualTo(AdKind.OFFER);
        assertThat(form.getCategoryIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(form.getCityTaxonId()).isEqualTo(5L);
        assertThat(form.getId()).isEqualTo(7L);
        assertThat(form.getVersion()).isEqualTo(3L);
        assertThat(form.getCreatedAt()).isEqualTo(created);
        assertThat(form.getCreatedBy()).isEqualTo(40L);
        assertThat(form.getCreatedByUserName()).isEqualTo("Owner");
    }

    @Test
    void providerProfile_snapshotBecomesAFormWithTheCurrentIdAndVersion() {
        ProviderProfileSnapshotDto snapshot = new ProviderProfileSnapshotDto(ProviderKind.SHOP, "About text",
                List.of(3L, 4L), 6L, "+380501234567", "plumber_master", "+380509876543");

        ProviderProfileEditDto form = providerProfileMapper.fromSnapshot(snapshot, 12L, 2L);

        assertThat(form.getId()).isEqualTo(12L);
        assertThat(form.getVersion()).isEqualTo(2L);
        assertThat(form.getKind()).isEqualTo(ProviderKind.SHOP);
        assertThat(form.getAbout()).isEqualTo("About text");
        assertThat(form.getCategoryIds()).containsExactlyInAnyOrder(3L, 4L);
        assertThat(form.getCityTaxonId()).isEqualTo(6L);
        assertThat(form.getPhone()).isEqualTo("+380501234567");
        assertThat(form.getTelegram()).isEqualTo("plumber_master");
        assertThat(form.getViber()).isEqualTo("+380509876543");
    }

    @Test
    void user_snapshotBecomesAFormWithTheCurrentIdAndVersion_roleConvertedFromText() {
        UserSnapshotDto snapshot = new UserSnapshotDto("Old Name", "old@example.com", "MODERATOR");

        UserEditDto form = userMapper.fromSnapshot(snapshot, 31L, 4L);

        assertThat(form.getId()).isEqualTo(31L);
        assertThat(form.getVersion()).isEqualTo(4L);
        assertThat(form.getName()).isEqualTo("Old Name");
        assertThat(form.getRole()).isEqualTo(Role.MODERATOR);
    }
}
