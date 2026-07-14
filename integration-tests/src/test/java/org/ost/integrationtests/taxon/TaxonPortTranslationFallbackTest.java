package org.ost.integrationtests.taxon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.taxon.config.TaxonAutoConfiguration;
import org.ost.taxon.entities.Taxon;
import org.ost.taxon.entities.TaxonTranslation;
import org.ost.taxon.repository.TaxonRepository;
import org.ost.taxon.repository.TaxonTranslationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers improvement-045 item 6: {@code DefaultTaxonPort.resolveTranslation()}'s three-tier
 * fallback (requested locale → configured default locale → first available → blank), which is
 * package-private and has no direct impact of its own — it only matters through the public
 * {@link TaxonPort#findById} contract that calls it. Tested through that public contract instead
 * of reaching into the package-private method directly, so this exercises real behavior (real SQL,
 * real service wiring) rather than an implementation detail in isolation — see
 * {@code integration-tests/DECISIONS.md} ADR-008.
 *
 * <p>Fixture setup goes through {@link TaxonRepository}/{@link TaxonTranslationRepository}
 * directly, not {@code TaxonPort.create()} — {@code TaxonService.create()}'s own validation
 * requires a translation for every configured {@code supportedLocales} entry, so the public
 * creation path can never actually produce the incomplete-translation state this fallback logic
 * exists to handle. That state is real nonetheless: e.g. a taxon created before a new locale was
 * added to {@code supportedLocales} would be missing a translation for it. Repository-level
 * fixture setup simulates exactly that, the same way {@code TaxonRepositoryTest} already builds
 * entities directly rather than through {@code TaxonService}.</p>
 */
@SpringBootTest(classes = {
        TaxonAutoConfiguration.class,
        RepositoryTestSupport.class
})
class TaxonPortTranslationFallbackTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TaxonPort taxonPort;

    @Autowired
    private TaxonRepository taxonRepository;

    @Autowired
    private TaxonTranslationRepository translationRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private Long createTaxonWithTranslations(TaxonTranslation... translations) {
        Taxon taxon = taxonRepository.save(Taxon.builder().type(TaxonType.CATEGORY).build());
        translationRepository.saveAll(taxon.getId(), List.of(translations));
        return taxon.getId();
    }

    private static TaxonTranslation translation(String locale, String name) {
        return TaxonTranslation.builder().locale(locale).name(name).description(name + " description").build();
    }

    @Test
    void findById_requestedLocaleAvailable_returnsRequestedTranslation() {
        Long id = createTaxonWithTranslations(
                translation("en", "Electronics"),
                translation("uk", "Електроніка"));

        Optional<TaxonDto> result = taxonPort.findById(id, Locale.forLanguageTag("uk"));

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Електроніка");
    }

    @Test
    void findById_requestedLocaleMissing_fallsBackToConfiguredDefaultLocale() {
        // TaxonProperties.defaultLocale() defaults to "en" when not overridden.
        Long id = createTaxonWithTranslations(translation("en", "Electronics"));

        Optional<TaxonDto> result = taxonPort.findById(id, Locale.forLanguageTag("uk"));

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Electronics");
    }

    @Test
    void findById_requestedAndDefaultLocaleMissing_fallsBackToFirstAvailableTranslation() {
        Long id = createTaxonWithTranslations(translation("fr", "Électronique"));

        Optional<TaxonDto> result = taxonPort.findById(id, Locale.forLanguageTag("uk"));

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Électronique");
    }

    @Test
    void findById_noTranslationsAtAll_returnsBlankNameNotError() {
        Long id = createTaxonWithTranslations();

        Optional<TaxonDto> result = taxonPort.findById(id, Locale.forLanguageTag("uk"));

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEmpty();
    }
}
