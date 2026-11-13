package org.ost.integrationtests.level1.taxon;

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

/** Covers {@link TaxonAssignmentRepository} on a real PostgreSQL: idempotent {@code assign()}, both bulk-lookup directions and {@code countByTaxonIds}. */
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

        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, taxonId);

        var assignments = assignmentRepository.findAllByEntity(EntityType.ADVERTISEMENT.name(), 1L);
        assertThat(assignments).extracting(TaxonAssignment::getTaxonId).containsExactly(taxonId);
    }

    @Test
    void assign_calledTwiceForSamePair_isIdempotent() {
        Long taxonId = saveTaxon("electronics");

        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, taxonId);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, taxonId);

        assertThat(assignmentRepository.findAllByEntity(EntityType.ADVERTISEMENT.name(), 1L)).hasSize(1);
    }

    @Test
    void unassign_removesOnlyTheSpecifiedTaxon() {
        Long electronics = saveTaxon("electronics");
        Long vehicles = saveTaxon("vehicles");
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, electronics);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, vehicles);

        assignmentRepository.unassign(EntityType.ADVERTISEMENT.name(), 1L, electronics);

        var remaining = assignmentRepository.findAllByEntity(EntityType.ADVERTISEMENT.name(), 1L);
        assertThat(remaining).extracting(TaxonAssignment::getTaxonId).containsExactly(vehicles);
    }

    @Test
    void findAllByEntities_returnsAssignmentsAcrossMultipleEntities() {
        Long electronics = saveTaxon("electronics");
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, electronics);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 2L, electronics);

        var assignments = assignmentRepository.findAllByEntities(EntityType.ADVERTISEMENT.name(), Set.of(1L, 2L, 3L));

        assertThat(assignments).extracting(TaxonAssignment::getEntityId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void findEntityIdsByTaxonIds_returnsEntitiesTaggedWithAnyOfTheGivenTaxons() {
        Long electronics = saveTaxon("electronics");
        Long vehicles = saveTaxon("vehicles");
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, electronics);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 2L, vehicles);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 3L, electronics);

        Set<Long> entityIds = assignmentRepository.findEntityIdsByTaxonIds(EntityType.ADVERTISEMENT.name(), Set.of(electronics));

        assertThat(entityIds).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void countByTaxonIds_returnsPerTaxonCounts() {
        Long electronics = saveTaxon("electronics");
        Long vehicles = saveTaxon("vehicles");
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 1L, electronics);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 2L, electronics);
        assignmentRepository.assign(EntityType.ADVERTISEMENT.name(), 3L, vehicles);

        Map<Long, Long> counts = assignmentRepository.countByTaxonIds(Set.of(electronics, vehicles));

        assertThat(counts).containsEntry(electronics, 2L).containsEntry(vehicles, 1L);
    }
}
