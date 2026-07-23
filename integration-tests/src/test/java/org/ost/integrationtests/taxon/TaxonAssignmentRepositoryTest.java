package org.ost.integrationtests.taxon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.taxon.config.TaxonAutoConfiguration;
import org.ost.taxon.entities.Taxon;
import org.ost.taxon.entities.TaxonAssignment;
import org.ost.taxon.repository.TaxonAssignmentRepository;
import org.ost.taxon.repository.TaxonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers improvement-027 Batch 3: {@link TaxonAssignmentRepository}'s dynamic SQL — the
 * many-to-many (entity_type, entity_id) &lt;-&gt; taxon_id join table backing category assignment.
 * Not exhaustive; the highest-risk paths: idempotent {@code assign()} (`ON CONFLICT DO NOTHING`),
 * both directions of bulk lookup ({@code findAllByEntities}, {@code findEntityIdsByTaxonIds}), and
 * the two count variants.
 */
@SpringBootTest(classes = {
        TaxonAutoConfiguration.class,
        RepositoryTestSupport.class
})
class TaxonAssignmentRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TaxonAssignmentRepository assignmentRepository;

    @Autowired
    private TaxonRepository taxonRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private Long saveTaxon(String code) {
        return taxonRepository.save(Taxon.builder().type(TaxonType.CATEGORY).code(code).build()).getId();
    }

    @Test
    void assign_thenFindAllByEntity_returnsTheAssignment() {
        Long taxonId = saveTaxon("electronics");

        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, taxonId, 42L);

        var assignments = assignmentRepository.findAllByEntity(EntityType.ADVERTISEMENT.name(), 1L);
        assertThat(assignments).extracting(TaxonAssignment::getTaxonId).containsExactly(taxonId);
        assertThat(assignments.get(0).getAssignedBy()).isEqualTo(42L);
    }

    @Test
    void assign_calledTwiceForSamePair_isIdempotent() {
        Long taxonId = saveTaxon("electronics");

        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, taxonId, 42L);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, taxonId, 42L);

        assertThat(assignmentRepository.findAllByEntity(EntityType.ADVERTISEMENT.name(), 1L)).hasSize(1);
    }

    @Test
    void unassign_removesOnlyTheSpecifiedTaxon() {
        Long electronics = saveTaxon("electronics");
        Long vehicles = saveTaxon("vehicles");
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, electronics, 42L);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, vehicles, 42L);

        assignmentRepository.unassign(EntityType.ADVERTISEMENT.name(), 1L, electronics);

        var remaining = assignmentRepository.findAllByEntity(EntityType.ADVERTISEMENT.name(), 1L);
        assertThat(remaining).extracting(TaxonAssignment::getTaxonId).containsExactly(vehicles);
    }

    @Test
    void deleteAllByEntity_removesEveryAssignmentForThatEntity() {
        Long electronics = saveTaxon("electronics");
        Long vehicles = saveTaxon("vehicles");
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, electronics, 42L);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, vehicles, 42L);

        assignmentRepository.deleteAllByEntity(EntityType.ADVERTISEMENT.name(), 1L);

        assertThat(assignmentRepository.findAllByEntity(EntityType.ADVERTISEMENT.name(), 1L)).isEmpty();
    }

    @Test
    void findAllByEntities_returnsAssignmentsAcrossMultipleEntities() {
        Long electronics = saveTaxon("electronics");
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, electronics, 42L);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 2L, electronics, 42L);

        var assignments = assignmentRepository.findAllByEntities(EntityType.ADVERTISEMENT.name(), Set.of(1L, 2L, 3L));

        assertThat(assignments).extracting(TaxonAssignment::getEntityId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void findEntityIdsByTaxonIds_returnsEntitiesTaggedWithAnyOfTheGivenTaxons() {
        Long electronics = saveTaxon("electronics");
        Long vehicles = saveTaxon("vehicles");
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, electronics, 42L);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 2L, vehicles, 42L);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 3L, electronics, 42L);

        Set<Long> entityIds = assignmentRepository.findEntityIdsByTaxonIds(EntityType.ADVERTISEMENT.name(), Set.of(electronics));

        assertThat(entityIds).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void countByTaxonId_countsOnlyThatTaxonsAssignments() {
        Long electronics = saveTaxon("electronics");
        Long vehicles = saveTaxon("vehicles");
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, electronics, 42L);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 2L, electronics, 42L);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 3L, vehicles, 42L);

        assertThat(assignmentRepository.countByTaxonId(electronics)).isEqualTo(2L);
        assertThat(assignmentRepository.countByTaxonId(vehicles)).isEqualTo(1L);
    }

    @Test
    void countByTaxonIds_returnsPerTaxonCounts() {
        Long electronics = saveTaxon("electronics");
        Long vehicles = saveTaxon("vehicles");
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, electronics, 42L);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 2L, electronics, 42L);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 3L, vehicles, 42L);

        Map<Long, Long> counts = assignmentRepository.countByTaxonIds(Set.of(electronics, vehicles));

        assertThat(counts).containsEntry(electronics, 2L).containsEntry(vehicles, 1L);
    }
}
