package org.ost.integrationtests.level1.taxon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.model.SqlColumn;
import org.ost.platform.core.model.SqlPredicate;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.taxon.config.TaxonAutoConfiguration;
import org.ost.taxon.entities.Taxon;
import org.ost.taxon.repository.TaxonAssignmentRepository;
import org.ost.taxon.repository.TaxonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The predicate Taxon builds for a consumer's statement, run on a real PostgreSQL against a table the test owns
 * (a {@code VALUES} list of entity ids 1 to 5), the way a consumer appends it to its own page and count statements.
 */
@SpringBootTest(classes = {
        TaxonAutoConfiguration.class,
        RepositoryTestSupport.class
})
class TaxonCategoryAndCityPredicateTest extends AbstractPostgresIntegrationTest {

    private static final SqlColumn KEY = new SqlColumn("c", "id");

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

    private Long saveTaxon(TaxonType type, String code) {
        return taxonRepository.save(Taxon.builder().type(type).code(code).build()).getId();
    }

    private void assign(EntityType type, Long taxonId, long... entityIds) {
        for (long entityId : entityIds) {
            assignmentRepository.assign(type.name(), entityId, taxonId);
        }
    }

    private Optional<SqlPredicate> predicate(Set<Long> categoryIds, Long cityTaxonId) {
        return assignmentRepository.categoryAndCityPredicate(EntityType.ADVERTISEMENT.name(), KEY, categoryIds, cityTaxonId);
    }

    private List<Long> rows(Optional<SqlPredicate> predicate) {
        String sql = "SELECT c.id FROM (VALUES (1), (2), (3), (4), (5)) AS c(id) WHERE TRUE"
                + predicate.map(SqlPredicate::asAndClause).orElse("") + " ORDER BY c.id";
        var params = new MapSqlParameterSource();
        predicate.ifPresent(p -> params.addValues(p.params()));
        return jdbcClient.sql(sql).paramSource(params).query(Long.class).list();
    }

    @Test
    void neitherCategoryNorCityRequested_returnsEmpty() {
        assertThat(predicate(null, null)).isEmpty();
    }

    @Test
    void onlyCategoryRequested_keepsTheEntitiesOfAnyGivenCategory() {
        Long first = saveTaxon(TaxonType.CATEGORY, "first");
        Long second = saveTaxon(TaxonType.CATEGORY, "second");
        assign(EntityType.ADVERTISEMENT, first, 1, 2);
        assign(EntityType.ADVERTISEMENT, second, 3);

        assertThat(rows(predicate(Set.of(first), null))).containsExactly(1L, 2L);
        assertThat(rows(predicate(Set.of(first, second), null))).containsExactly(1L, 2L, 3L);
    }

    @Test
    void onlyCityRequested_keepsTheEntitiesOfThatCity() {
        Long city = saveTaxon(TaxonType.CITY, "city");
        assign(EntityType.ADVERTISEMENT, city, 4, 5);

        assertThat(rows(predicate(null, city))).containsExactly(4L, 5L);
    }

    @Test
    void bothRequestedWithOverlap_keepsTheIntersection() {
        Long category = saveTaxon(TaxonType.CATEGORY, "category");
        Long city = saveTaxon(TaxonType.CITY, "city");
        assign(EntityType.ADVERTISEMENT, category, 1, 2, 3);
        assign(EntityType.ADVERTISEMENT, city, 2, 3, 4);

        assertThat(rows(predicate(Set.of(category), city))).containsExactly(2L, 3L);
    }

    @Test
    void bothRequestedWithoutOverlap_matchesNothing() {
        Long category = saveTaxon(TaxonType.CATEGORY, "category");
        Long city = saveTaxon(TaxonType.CITY, "city");
        assign(EntityType.ADVERTISEMENT, category, 1);
        assign(EntityType.ADVERTISEMENT, city, 2);

        assertThat(rows(predicate(Set.of(category), city))).isEmpty();
    }

    @Test
    void categoryWithoutAnyAssignment_matchesNothing() {
        Long category = saveTaxon(TaxonType.CATEGORY, "category");

        assertThat(rows(predicate(Set.of(category), null))).isEmpty();
    }

    @Test
    void presentButEmptyCategorySet_matchesNothingInsteadOfEverything() {
        Long category = saveTaxon(TaxonType.CATEGORY, "category");
        assign(EntityType.ADVERTISEMENT, category, 1, 2);

        assertThat(rows(predicate(Set.of(), null))).isEmpty();
    }

    @Test
    void assignmentsOfAnotherEntityType_areNotMatched() {
        Long category = saveTaxon(TaxonType.CATEGORY, "category");
        assign(EntityType.PROVIDER_PROFILE, category, 1, 2);
        assign(EntityType.ADVERTISEMENT, category, 3);

        assertThat(rows(predicate(Set.of(category), null))).containsExactly(3L);
    }

    @Test
    void textDoesNotDependOnTheValues_andNoValueTravelsInIt() {
        Long first = saveTaxon(TaxonType.CATEGORY, "first");
        Long second = saveTaxon(TaxonType.CATEGORY, "second");
        Long city = saveTaxon(TaxonType.CITY, "city");

        SqlPredicate one = predicate(Set.of(first), city).orElseThrow();
        SqlPredicate other = predicate(Set.of(first, second), city).orElseThrow();

        assertThat(one.sql()).isEqualTo(other.sql());
        assertThat(one.sql()).contains(":_taxonCategoryIds", ":_taxonCityId", ":_taxonEntityType");
    }

    @Test
    void worksOverAnotherAliasAndKeyColumn() {
        Long category = saveTaxon(TaxonType.CATEGORY, "category");
        assign(EntityType.ADVERTISEMENT, category, 2, 4);
        SqlPredicate predicate = assignmentRepository
                .categoryAndCityPredicate(EntityType.ADVERTISEMENT.name(), new SqlColumn("x", "advertisement_id"), Set.of(category), null)
                .orElseThrow();
        var params = new MapSqlParameterSource().addValues(predicate.params());

        List<Long> ids = jdbcClient
                .sql("SELECT x.advertisement_id FROM (VALUES (1), (2), (3), (4)) AS x(advertisement_id) WHERE x.advertisement_id > 1"
                        + predicate.asAndClause() + " ORDER BY 1")
                .paramSource(params).query(Long.class).list();

        assertThat(ids).containsExactly(2L, 4L);
    }
}
