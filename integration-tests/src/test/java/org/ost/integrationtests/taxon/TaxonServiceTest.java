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
import org.ost.taxon.services.TaxonService;
import org.ost.taxon.services.TaxonTranslationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers improvement-049 item 1: {@link TaxonService#update} forwarded {@code deletedAt} but not
 * {@code deletedBy} when rebuilding the entity, silently overwriting {@code deleted_by} to
 * {@code NULL} on every {@code update()} call against an already soft-deleted taxon (Spring Data
 * JDBC's {@code save()} is a full-row {@code UPDATE}, not a partial patch, so any field the
 * builder doesn't forward reverts to its default). {@code TaxonRepository.restore()} deliberately
 * leaves {@code deleted_by} untouched after a restore (a permanent "who last deleted this" trail)
 * — a different, intentional case, not touched here.
 */
@SpringBootTest(classes = {
        TaxonAutoConfiguration.class,
        RepositoryTestSupport.class
})
class TaxonServiceTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TaxonService taxonService;

    @Autowired
    private TaxonRepository taxonRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private static Map<Locale, TaxonTranslationData> validTranslations() {
        return Map.of(
                Locale.forLanguageTag("uk"), new TaxonTranslationData("Назва", "Опис"),
                Locale.ENGLISH, new TaxonTranslationData("Name", "Description"));
    }

    @Test
    void update_onSoftDeletedTaxon_preservesDeletedBy() {
        Taxon taxon = taxonRepository.save(Taxon.builder()
                .type(TaxonType.CATEGORY).code("preserve-deleted-by").build());
        Long deleterId = 42L;
        taxonRepository.softDelete(taxon.getId(), deleterId, taxon.getVersion());
        Taxon deleted = taxonRepository.findById(taxon.getId()).orElseThrow();
        assertThat(deleted.getDeletedBy()).isEqualTo(deleterId);

        Taxon updated = taxonService.update(taxon.getId(), validTranslations(), 99L, deleted.getVersion());

        assertThat(updated.getDeletedBy()).isEqualTo(deleterId);
        Taxon afterUpdate = taxonRepository.findById(taxon.getId()).orElseThrow();
        assertThat(afterUpdate.getDeletedBy()).isEqualTo(deleterId);
        assertThat(afterUpdate.getDeletedAt()).isEqualTo(deleted.getDeletedAt());
    }

    @Test
    void update_onActiveTaxon_deletedByStaysNull() {
        Taxon taxon = taxonRepository.save(Taxon.builder()
                .type(TaxonType.CATEGORY).code("active-taxon").build());

        Taxon updated = taxonService.update(taxon.getId(), validTranslations(), 99L, taxon.getVersion());

        assertThat(updated.getDeletedBy()).isNull();
        assertThat(updated.getDeletedAt()).isNull();
    }
}
