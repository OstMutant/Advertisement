package org.ost.integrationtests.taxon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.taxon.config.TaxonAutoConfiguration;
import org.ost.taxon.entities.Taxon;
import org.ost.taxon.repository.TaxonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers improvement-045 item 4/5: {@link TaxonRepository#findByIds} was found missing a
 * {@code deleted_at IS NULL} filter, unlike every other query in the class ({@code findAllByType},
 * {@code countByType}). See {@code features/issues/improvement-045-critical-test-coverage-gaps.md}.
 *
 * <p>{@code findByTypeAndCode} (formerly covered by the same fix) was removed entirely during
 * improvement-058, along with {@code TaxonPort.findByCode}/{@code TaxonService.findByCode} —
 * confirmed zero callers anywhere in the codebase, including its own known-unreachable state at
 * the time of the original improvement-045 fix.
 */
@SpringBootTest(classes = {
        TaxonAutoConfiguration.class,
        RepositoryTestSupport.class
})
class TaxonRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TaxonRepository taxonRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private Taxon save(String code) {
        return taxonRepository.save(Taxon.builder().type(TaxonType.CATEGORY).code(code).build());
    }

    @Test
    void findByIds_excludesSoftDeletedRows() {
        Taxon active = save("active-category");
        Taxon deleted = save("deleted-category");
        taxonRepository.softDelete(deleted.getId(), null, deleted.getVersion());

        var result = taxonRepository.findByIds(Set.of(active.getId(), deleted.getId()));

        assertThat(result).extracting(Taxon::getId).containsExactly(active.getId());
    }

    @Test
    void findByIds_returnsActiveRows() {
        Taxon a = save("category-a");
        Taxon b = save("category-b");

        var result = taxonRepository.findByIds(Set.of(a.getId(), b.getId()));

        assertThat(result).extracting(Taxon::getId).containsExactlyInAnyOrder(a.getId(), b.getId());
    }
}
