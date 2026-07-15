package org.ost.integrationtests.taxon;

import org.junit.jupiter.api.Test;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.taxon.dto.TaxonSnapshotDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers improvement-027 Batch 2: plain unit test for {@link TaxonSnapshotDto#diff}, by direct
 * analogy with {@code AdvertisementSnapshotDtoTest}/{@code SettingsSnapshotDtoTest} — no Spring
 * context, no DB, pure field comparison with zero side effects.
 */
class TaxonSnapshotDtoTest {

    @Test
    void diff_noPrevious_returnsChangesForAllFields() {
        TaxonSnapshotDto current = new TaxonSnapshotDto("Electronics", "EN desc", "Електроніка", "UK desc");

        List<ChangeEntry> changes = current.diff(null);

        assertThat(changes).containsExactlyInAnyOrder(
                new ChangeEntry.FieldChange(TaxonSnapshotDto.Fields.nameEn, null, "Electronics"),
                new ChangeEntry.FieldChange(TaxonSnapshotDto.Fields.descriptionEn, null, "EN desc"),
                new ChangeEntry.FieldChange(TaxonSnapshotDto.Fields.nameUk, null, "Електроніка"),
                new ChangeEntry.FieldChange(TaxonSnapshotDto.Fields.descriptionUk, null, "UK desc"));
    }

    @Test
    void diff_identicalSnapshots_returnsNoChanges() {
        TaxonSnapshotDto previous = new TaxonSnapshotDto("Electronics", "EN desc", "Електроніка", "UK desc");
        TaxonSnapshotDto current = new TaxonSnapshotDto("Electronics", "EN desc", "Електроніка", "UK desc");

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).isEmpty();
    }

    @Test
    void diff_nameEnChanged_returnsSingleFieldChange() {
        TaxonSnapshotDto previous = new TaxonSnapshotDto("Electronics", "EN desc", "Електроніка", "UK desc");
        TaxonSnapshotDto current = new TaxonSnapshotDto("Gadgets", "EN desc", "Електроніка", "UK desc");

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new ChangeEntry.FieldChange(TaxonSnapshotDto.Fields.nameEn, "Electronics", "Gadgets"));
    }

    @Test
    void diff_descriptionEnChanged_returnsSingleFieldChange() {
        TaxonSnapshotDto previous = new TaxonSnapshotDto("Electronics", "Old desc", "Електроніка", "UK desc");
        TaxonSnapshotDto current = new TaxonSnapshotDto("Electronics", "New desc", "Електроніка", "UK desc");

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new ChangeEntry.FieldChange(TaxonSnapshotDto.Fields.descriptionEn, "Old desc", "New desc"));
    }

    @Test
    void diff_nameUkChanged_returnsSingleFieldChange() {
        TaxonSnapshotDto previous = new TaxonSnapshotDto("Electronics", "EN desc", "Електроніка", "UK desc");
        TaxonSnapshotDto current = new TaxonSnapshotDto("Electronics", "EN desc", "Гаджети", "UK desc");

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new ChangeEntry.FieldChange(TaxonSnapshotDto.Fields.nameUk, "Електроніка", "Гаджети"));
    }

    @Test
    void diff_descriptionUkChanged_returnsSingleFieldChange() {
        TaxonSnapshotDto previous = new TaxonSnapshotDto("Electronics", "EN desc", "Електроніка", "Old UK desc");
        TaxonSnapshotDto current = new TaxonSnapshotDto("Electronics", "EN desc", "Електроніка", "New UK desc");

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new ChangeEntry.FieldChange(TaxonSnapshotDto.Fields.descriptionUk, "Old UK desc", "New UK desc"));
    }

    @Test
    void diff_allFieldsChanged_returnsAllChangedFields() {
        TaxonSnapshotDto previous = new TaxonSnapshotDto("Electronics", "EN desc", "Електроніка", "UK desc");
        TaxonSnapshotDto current = new TaxonSnapshotDto("Gadgets", "New EN desc", "Гаджети", "New UK desc");

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactlyInAnyOrder(
                new ChangeEntry.FieldChange(TaxonSnapshotDto.Fields.nameEn, "Electronics", "Gadgets"),
                new ChangeEntry.FieldChange(TaxonSnapshotDto.Fields.descriptionEn, "EN desc", "New EN desc"),
                new ChangeEntry.FieldChange(TaxonSnapshotDto.Fields.nameUk, "Електроніка", "Гаджети"),
                new ChangeEntry.FieldChange(TaxonSnapshotDto.Fields.descriptionUk, "UK desc", "New UK desc"));
    }
}
