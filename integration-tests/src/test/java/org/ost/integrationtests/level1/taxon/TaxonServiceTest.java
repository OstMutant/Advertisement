package org.ost.integrationtests.level1.taxon;

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

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

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

    // Confirms real behavior rather than assuming it: Spring Data JDBC's @LastModifiedDate auditing
    // does refresh updatedAt on update, including for this Lombok @Value (immutable) entity --
    // confirmed directly via a repository-level save() with no manual updatedAt set at all. The
    // real, separate bug this test caught: TaxonService.update() was discarding save()'s own
    // return value (which carries the auditing-refreshed updatedAt/version) and returning the
    // stale pre-save object instead.
    @Test
    void update_returnsAuditingRefreshedUpdatedAt_notTheStalePreSaveValue() {
        Taxon taxon = taxonRepository.save(Taxon.builder()
                .type(TaxonType.CATEGORY).code("auditing-refreshed-updated-at").build());
        Instant insertedAt = taxon.getUpdatedAt();
        assertThat(insertedAt).isNotNull();

        Taxon updated = taxonService.update(taxon.getId(), validTranslations(), 99L, taxon.getVersion());

        assertThat(updated.getUpdatedAt()).isNotNull().isAfter(insertedAt).isBeforeOrEqualTo(Instant.now());
        Taxon afterUpdate = taxonRepository.findById(taxon.getId()).orElseThrow();
        // Two independent reads of the same persisted timestamptz column can round-trip through
        // the driver with sub-microsecond jitter (observed directly: a 1-microsecond mismatch on
        // otherwise-identical values) -- assert closeness, not exact equality, for the same
        // reason the earlier version of this test (exact equality) proved flaky under real load.
        assertThat(afterUpdate.getUpdatedAt())
                .isCloseTo(updated.getUpdatedAt(), within(1, java.time.temporal.ChronoUnit.MILLIS));
    }

    // ── validateTranslations (via create) ──────────────────────────────────────────

    @Test
    void create_missingSupportedLocale_throws() {
        Map<Locale, TaxonTranslationData> onlyEnglish = Map.of(Locale.ENGLISH, new TaxonTranslationData("Name", "Description"));

        assertThatThrownBy(() -> taxonService.create(TaxonType.CATEGORY, "missing-locale", onlyEnglish, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uk");
    }

    @Test
    void create_blankName_throws() {
        Map<Locale, TaxonTranslationData> blankName = Map.of(
                Locale.forLanguageTag("uk"), new TaxonTranslationData("", "Опис"),
                Locale.ENGLISH, new TaxonTranslationData("Name", "Description"));

        assertThatThrownBy(() -> taxonService.create(TaxonType.CATEGORY, "blank-name", blankName, 99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_blankDescription_throws() {
        Map<Locale, TaxonTranslationData> blankDescription = Map.of(
                Locale.forLanguageTag("uk"), new TaxonTranslationData("Назва", ""),
                Locale.ENGLISH, new TaxonTranslationData("Name", "Description"));

        assertThatThrownBy(() -> taxonService.create(TaxonType.CATEGORY, "blank-description", blankDescription, 99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_allSupportedLocalesPresent_succeeds() {
        Taxon created = taxonService.create(TaxonType.CATEGORY, "all-locales-present", validTranslations(), 99L);

        assertThat(created.getId()).isNotNull();
    }
}
