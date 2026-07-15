package org.ost.integrationtests.user;

import org.junit.jupiter.api.Test;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.ChangeEntry.FieldChange;
import org.ost.platform.user.dto.SettingsSnapshotDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers improvement-045 item 8: plain unit test for {@link SettingsSnapshotDto#diff}, by direct
 * analogy with {@code AdvertisementSnapshotDtoTest} — no Spring context, no DB, pure field
 * comparison with zero side effects.
 */
class SettingsSnapshotDtoTest {

    @Test
    void diff_noPrevious_returnsChangesForAllFields() {
        SettingsSnapshotDto current = new SettingsSnapshotDto(10, 20, 30);

        List<ChangeEntry> changes = current.diff(null);

        assertThat(changes).containsExactlyInAnyOrder(
                new FieldChange(SettingsSnapshotDto.Fields.adsPageSize, null, "10"),
                new FieldChange(SettingsSnapshotDto.Fields.usersPageSize, null, "20"),
                new FieldChange(SettingsSnapshotDto.Fields.timelinePageSize, null, "30"));
    }

    @Test
    void diff_identicalSnapshots_returnsNoChanges() {
        SettingsSnapshotDto previous = new SettingsSnapshotDto(10, 20, 30);
        SettingsSnapshotDto current = new SettingsSnapshotDto(10, 20, 30);

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).isEmpty();
    }

    @Test
    void diff_adsPageSizeChanged_returnsSingleFieldChange() {
        SettingsSnapshotDto previous = new SettingsSnapshotDto(10, 20, 30);
        SettingsSnapshotDto current = new SettingsSnapshotDto(15, 20, 30);

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new FieldChange(SettingsSnapshotDto.Fields.adsPageSize, "10", "15"));
    }

    @Test
    void diff_usersPageSizeChanged_returnsSingleFieldChange() {
        SettingsSnapshotDto previous = new SettingsSnapshotDto(10, 20, 30);
        SettingsSnapshotDto current = new SettingsSnapshotDto(10, 25, 30);

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new FieldChange(SettingsSnapshotDto.Fields.usersPageSize, "20", "25"));
    }

    @Test
    void diff_timelinePageSizeChanged_returnsSingleFieldChange() {
        SettingsSnapshotDto previous = new SettingsSnapshotDto(10, 20, 30);
        SettingsSnapshotDto current = new SettingsSnapshotDto(10, 20, 35);

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new FieldChange(SettingsSnapshotDto.Fields.timelinePageSize, "30", "35"));
    }

    @Test
    void diff_allFieldsChanged_returnsAllChangedFields() {
        SettingsSnapshotDto previous = new SettingsSnapshotDto(10, 20, 30);
        SettingsSnapshotDto current = new SettingsSnapshotDto(11, 21, 31);

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactlyInAnyOrder(
                new FieldChange(SettingsSnapshotDto.Fields.adsPageSize, "10", "11"),
                new FieldChange(SettingsSnapshotDto.Fields.usersPageSize, "20", "21"),
                new FieldChange(SettingsSnapshotDto.Fields.timelinePageSize, "30", "31"));
    }
}
