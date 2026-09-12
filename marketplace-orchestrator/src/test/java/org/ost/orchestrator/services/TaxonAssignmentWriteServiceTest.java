package org.ost.orchestrator.services;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link TaxonAssignmentWriteService#unionAssignmentIds} -- the nullable-single-id union both {@code AdvertisementSaveService} and {@code ProviderProfileSaveService} use before one {@code replace()} call. */
class TaxonAssignmentWriteServiceTest {

    @Test
    void unionAssignmentIds_extraIdPresent_addsItToTheSet() {
        Set<Long> result = TaxonAssignmentWriteService.unionAssignmentIds(Set.of(1L, 2L), 5L);

        assertThat(result).containsExactlyInAnyOrder(1L, 2L, 5L);
    }

    @Test
    void unionAssignmentIds_extraIdNull_returnsOriginalSetUnchanged() {
        Set<Long> result = TaxonAssignmentWriteService.unionAssignmentIds(Set.of(1L, 2L), null);

        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void unionAssignmentIds_extraIdAlreadyInSet_noDuplicate() {
        Set<Long> result = TaxonAssignmentWriteService.unionAssignmentIds(Set.of(1L, 2L), 2L);

        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void unionAssignmentIds_emptySet_returnsSingletonOfExtraId() {
        Set<Long> result = TaxonAssignmentWriteService.unionAssignmentIds(Set.of(), 5L);

        assertThat(result).containsExactly(5L);
    }
}
