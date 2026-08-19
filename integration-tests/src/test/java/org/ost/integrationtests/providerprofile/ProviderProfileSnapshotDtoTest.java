package org.ost.integrationtests.providerprofile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.junit.jupiter.api.Test;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.ChangeEntry.FieldChange;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.platform.providerprofile.model.ProviderKind;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain-unit-test exemplar (no Spring context, no DB) for {@link ProviderProfileSnapshotDto}'s
 * {@code diff()}/{@code allFields()} logic, plus a Jackson polymorphic round-trip (de)serialization
 * test — none of the 4 pre-existing {@link AuditableSnapshot} subtypes has one of these, so this
 * closes that gap for the new subtype rather than assuming {@code JacksonConfig
 * .registerAuditSnapshotSubtypes()} wiring is correct.
 */
class ProviderProfileSnapshotDtoTest {

    @Test
    void roundTrip_serializeAndDeserialize_preservesTypeAndFields() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.registerSubtypes(ProviderProfileSnapshotDto.class);
        ProviderProfileSnapshotDto original = new ProviderProfileSnapshotDto(
                ProviderKind.MASTER, "About me", List.of(2L, 1L), 5L);

        String json = mapper.writeValueAsString(original);
        AuditableSnapshot deserialized = mapper.readValue(json, AuditableSnapshot.class);

        assertThat(deserialized).isInstanceOf(ProviderProfileSnapshotDto.class);
        assertThat(deserialized).isEqualTo(original);
        assertThat(json).contains("\"@type\":\"provider_profile\"");
    }

    @Test
    void diff_noPrevious_returnsChangesForAllSetFields() {
        ProviderProfileSnapshotDto current = new ProviderProfileSnapshotDto(
                ProviderKind.MASTER, "About", List.of(1L, 2L), null);

        List<ChangeEntry> changes = current.diff(null);

        assertThat(changes).containsExactlyInAnyOrder(
                new FieldChange(ProviderProfileSnapshotDto.Fields.kind, "", "MASTER"),
                new FieldChange(ProviderProfileSnapshotDto.Fields.about, null, "About"),
                new FieldChange(ProviderProfileSnapshotDto.Fields.categoryIds, "", "1, 2"));
    }

    @Test
    void diff_identicalSnapshots_returnsNoChanges() {
        ProviderProfileSnapshotDto previous = new ProviderProfileSnapshotDto(
                ProviderKind.MASTER, "About", List.of(1L, 2L), 3L);
        ProviderProfileSnapshotDto current = new ProviderProfileSnapshotDto(
                ProviderKind.MASTER, "About", List.of(1L, 2L), 3L);

        assertThat(current.diff(previous)).isEmpty();
    }

    @Test
    void diff_kindChanged_returnsSingleFieldChange() {
        ProviderProfileSnapshotDto previous = new ProviderProfileSnapshotDto(
                ProviderKind.MASTER, "About", List.of(), null);
        ProviderProfileSnapshotDto current = new ProviderProfileSnapshotDto(
                ProviderKind.SHOP, "About", List.of(), null);

        assertThat(current.diff(previous)).containsExactly(
                new FieldChange(ProviderProfileSnapshotDto.Fields.kind, "MASTER", "SHOP"));
    }

    @Test
    void diff_cityTaxonIdChanged_returnsSingleFieldChange() {
        ProviderProfileSnapshotDto previous = new ProviderProfileSnapshotDto(
                ProviderKind.MASTER, "About", List.of(), 1L);
        ProviderProfileSnapshotDto current = new ProviderProfileSnapshotDto(
                ProviderKind.MASTER, "About", List.of(), 2L);

        assertThat(current.diff(previous)).containsExactly(
                new FieldChange(ProviderProfileSnapshotDto.Fields.cityTaxonId, "1", "2"));
    }

    @Test
    void constructor_categoryIdsAlwaysSorted_regardlessOfInputOrder() {
        ProviderProfileSnapshotDto dto = new ProviderProfileSnapshotDto(
                ProviderKind.MASTER, "About", List.of(5L, 1L, 3L), null);

        assertThat(dto.categoryIds()).containsExactly(1L, 3L, 5L);
    }

    @Test
    void constructor_nullCategoryIds_defaultsToEmptyList() {
        ProviderProfileSnapshotDto dto = new ProviderProfileSnapshotDto(
                ProviderKind.MASTER, "About", null, null);

        assertThat(dto.categoryIds()).isEmpty();
    }

    @Test
    void displayName_returnsKindName() {
        ProviderProfileSnapshotDto dto = new ProviderProfileSnapshotDto(
                ProviderKind.SUPPORT, "About", List.of(), null);

        assertThat(dto.displayName()).contains("SUPPORT");
    }
}
