package org.ost.integrationtests.advertisement;

import org.junit.jupiter.api.Test;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.ChangeEntry.FieldChange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch 1 plain-unit-test exemplar (no Spring context, no DB) — {@link AdvertisementSnapshotDto
 * #diff(org.ost.platform.audit.api.AuditableSnapshot)} is pure field comparison with zero side
 * effects, exactly the kind of logic {@code integration-tests} hosts even though it never touches
 * a database — see {@code integration-tests/CLAUDE.md}.
 */
class AdvertisementSnapshotDtoTest {

    @Test
    void diff_noPrevious_returnsChangesForAllSetFields() {
        AdvertisementSnapshotDto current = new AdvertisementSnapshotDto(
                "Title", "Description", List.of(1L, 2L), null);

        List<ChangeEntry> changes = current.diff(null);

        assertThat(changes).containsExactlyInAnyOrder(
                new FieldChange(AdvertisementSnapshotDto.Fields.title, null, "Title"),
                new FieldChange(AdvertisementSnapshotDto.Fields.description, null, "Description"),
                new FieldChange(AdvertisementSnapshotDto.Fields.categoryIds, "", "1, 2"));
    }

    @Test
    void diff_identicalSnapshots_returnsNoChanges() {
        AdvertisementSnapshotDto previous = new AdvertisementSnapshotDto(
                "Title", "Description", List.of(1L, 2L), null);
        AdvertisementSnapshotDto current = new AdvertisementSnapshotDto(
                "Title", "Description", List.of(1L, 2L), null);

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).isEmpty();
    }

    @Test
    void diff_titleChanged_returnsSingleFieldChange() {
        AdvertisementSnapshotDto previous = new AdvertisementSnapshotDto(
                "Old title", "Description", List.of(), null);
        AdvertisementSnapshotDto current = new AdvertisementSnapshotDto(
                "New title", "Description", List.of(), null);

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new FieldChange(AdvertisementSnapshotDto.Fields.title, "Old title", "New title"));
    }

    @Test
    void diff_descriptionChanged_returnsSingleFieldChange() {
        AdvertisementSnapshotDto previous = new AdvertisementSnapshotDto(
                "Title", "Old description", List.of(), null);
        AdvertisementSnapshotDto current = new AdvertisementSnapshotDto(
                "Title", "New description", List.of(), null);

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new FieldChange(AdvertisementSnapshotDto.Fields.description, "Old description", "New description"));
    }

    @Test
    void diff_categoryIdsChanged_returnsSortedJoinedStrings() {
        AdvertisementSnapshotDto previous = new AdvertisementSnapshotDto(
                "Title", "Description", List.of(3L, 1L), null);
        AdvertisementSnapshotDto current = new AdvertisementSnapshotDto(
                "Title", "Description", List.of(2L, 5L), null);

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new FieldChange(AdvertisementSnapshotDto.Fields.categoryIds, "1, 3", "2, 5"));
    }

    @Test
    void diff_multipleFieldsChanged_returnsAllChangedFields() {
        AdvertisementSnapshotDto previous = new AdvertisementSnapshotDto(
                "Old title", "Old description", List.of(1L), null);
        AdvertisementSnapshotDto current = new AdvertisementSnapshotDto(
                "New title", "New description", List.of(2L), null);

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactlyInAnyOrder(
                new FieldChange(AdvertisementSnapshotDto.Fields.title, "Old title", "New title"),
                new FieldChange(AdvertisementSnapshotDto.Fields.description, "Old description", "New description"),
                new FieldChange(AdvertisementSnapshotDto.Fields.categoryIds, "1", "2"));
    }

    @Test
    void diff_categoryIdsAddedFromEmpty_fromIsEmptyString() {
        AdvertisementSnapshotDto previous = new AdvertisementSnapshotDto(
                "Title", "Description", List.of(), null);
        AdvertisementSnapshotDto current = new AdvertisementSnapshotDto(
                "Title", "Description", List.of(1L), null);

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new FieldChange(AdvertisementSnapshotDto.Fields.categoryIds, "", "1"));
    }

    @Test
    void constructor_categoryIdsAlwaysSorted_regardlessOfInputOrder() {
        AdvertisementSnapshotDto dto = new AdvertisementSnapshotDto(
                "Title", "Description", List.of(5L, 1L, 3L), null);

        assertThat(dto.categoryIds()).containsExactly(1L, 3L, 5L);
    }

    @Test
    void constructor_nullCategoryIds_defaultsToEmptyList() {
        AdvertisementSnapshotDto dto = new AdvertisementSnapshotDto(
                "Title", "Description", null, null);

        assertThat(dto.categoryIds()).isEmpty();
    }
}
