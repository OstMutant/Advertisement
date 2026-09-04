package org.ost.integrationtests.taxon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.taxon.config.TaxonAutoConfiguration;
import org.ost.taxon.entities.Taxon;
import org.ost.taxon.repository.TaxonFilter;
import org.ost.taxon.repository.TaxonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TaxonRepository#findByIds} returns soft-deleted rows too (reversed 2026-07-21,
 * improvement-008/101 — see {@code .claude/nav/adr-index.md}): its only
 * caller, {@code DefaultTaxonPort.indexById()}, needs deleted taxons visible so the advertisement
 * view overlay can render them struck-through and audit diffs can resolve their real name instead
 * of a bare id.
 *
 * <p>{@code findByTypeAndCode} (formerly covered by the same class) was removed entirely during
 * improvement-058, along with {@code TaxonPort.findByCode}/{@code TaxonService.findByCode} —
 * confirmed zero callers anywhere in the codebase.
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
    void findByIds_includesSoftDeletedRows() {
        Taxon active = save("active-category");
        Taxon deleted = save("deleted-category");
        taxonRepository.softDelete(deleted.getId(), null, deleted.getVersion());

        var result = taxonRepository.findByIds(Set.of(active.getId(), deleted.getId()));

        assertThat(result).extracting(Taxon::getId).containsExactlyInAnyOrder(active.getId(), deleted.getId());
    }

    @Test
    void findByIds_returnsActiveRows() {
        Taxon a = save("category-a");
        Taxon b = save("category-b");

        var result = taxonRepository.findByIds(Set.of(a.getId(), b.getId()));

        assertThat(result).extracting(Taxon::getId).containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @Test
    void findAllByType_appliesPageSizeAndSort() {
        List<Long> ids = List.of(save("a").getId(), save("b").getId(), save("c").getId(), save("d").getId(), save("e").getId());

        var firstPage = taxonRepository.findAllByType(TaxonType.CATEGORY, TaxonFilter.active(),
                PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "id")));
        var secondPage = taxonRepository.findAllByType(TaxonType.CATEGORY, TaxonFilter.active(),
                PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "id")));

        assertThat(firstPage).extracting(Taxon::getId).containsExactly(ids.get(0), ids.get(1));
        assertThat(secondPage).extracting(Taxon::getId).containsExactly(ids.get(2), ids.get(3));
    }

    @Test
    void findAllByType_unpaged_returnsEveryRow() {
        List<Long> ids = List.of(save("x").getId(), save("y").getId(), save("z").getId());

        var result = taxonRepository.findAllByType(TaxonType.CATEGORY, TaxonFilter.active(), org.springframework.data.domain.Pageable.unpaged());

        assertThat(result).extracting(Taxon::getId).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void countByType_matchesRealRowCount() {
        save("count-a");
        save("count-b");
        Taxon deleted = save("count-deleted");
        taxonRepository.softDelete(deleted.getId(), null, deleted.getVersion());

        int activeCount = taxonRepository.countByType(TaxonType.CATEGORY, TaxonFilter.active());
        int allCount = taxonRepository.countByType(TaxonType.CATEGORY, TaxonFilter.all());

        assertThat(activeCount).isEqualTo(2);
        assertThat(allCount).isEqualTo(3);
    }
}
