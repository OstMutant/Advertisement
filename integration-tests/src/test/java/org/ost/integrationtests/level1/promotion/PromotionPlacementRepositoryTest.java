package org.ost.integrationtests.level1.promotion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.promotion.dto.PromotionPlacementDto;
import org.ost.platform.promotion.model.PromotionReason;
import org.ost.promotion.config.PromotionAutoConfiguration;
import org.ost.promotion.repository.PromotionPlacementRepository;
import org.ost.promotion.services.PromotionPlacementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Testcontainers test for {@link PromotionPlacementService} and {@link PromotionPlacementRepository} -- entity ids are synthetic longs, since {@code promotion_placement} carries no FK to any other table. */
@SpringBootTest(classes = {
        PromotionAutoConfiguration.class,
        RepositoryTestSupport.class
})
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=2")
class PromotionPlacementRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PromotionPlacementService service;

    @Autowired
    private PromotionPlacementRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private List<Long> promotedIds(EntityType entityType) {
        return repository.findPromoted(entityType).stream()
                .map(PromotionPlacementDto::entityId)
                .toList();
    }

    private void insertRow(String entityType, long entityId, String reason) {
        jdbcClient.sql("INSERT INTO promotion_placement (entity_type, entity_id, reason) VALUES (:type, :id, :reason)")
                .param("type", entityType)
                .param("id", entityId)
                .param("reason", reason)
                .update();
    }

    private int rowCount(String reason) {
        return jdbcClient.sql("SELECT COUNT(*) FROM promotion_placement WHERE reason = :reason")
                .param("reason", reason)
                .query(Integer.class)
                .single();
    }

    @Test
    void replaceForReason_insertsEveryGivenId() {
        service.replaceForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, Set.of(1L, 2L, 3L));

        assertThat(promotedIds(EntityType.PROVIDER_PROFILE)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void replaceForReason_calledTwiceWithTheSameSet_changesNothing() {
        service.replaceForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, Set.of(1L, 2L));
        service.replaceForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, Set.of(1L, 2L));

        assertThat(promotedIds(EntityType.PROVIDER_PROFILE)).containsExactly(1L, 2L);
        assertThat(rowCount("TOP_RATED")).isEqualTo(2);
    }

    @Test
    void replaceForReason_removesRowsMissingFromTheSetAndAddsNewOnes() {
        service.replaceForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, Set.of(1L, 2L, 3L));

        service.replaceForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, Set.of(2L, 3L, 4L));

        assertThat(promotedIds(EntityType.PROVIDER_PROFILE)).containsExactly(2L, 3L, 4L);
    }

    @Test
    void replaceForReason_emptySet_removesEveryRowOfThatReason() {
        service.replaceForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, Set.of(1L, 2L));

        service.replaceForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, Set.of());

        assertThat(promotedIds(EntityType.PROVIDER_PROFILE)).isEmpty();
    }

    @Test
    void replaceForReason_leavesOtherReasonsAndOtherEntityTypesUntouched() {
        insertRow("PROVIDER_PROFILE", 9L, "SPONSORED");
        service.replaceForReason(EntityType.ADVERTISEMENT, PromotionReason.TOP_RATED, Set.of(5L));

        service.replaceForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, Set.of(1L));
        service.replaceForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, Set.of());

        assertThat(rowCount("SPONSORED")).isEqualTo(1);
        assertThat(promotedIds(EntityType.ADVERTISEMENT)).containsExactly(5L);
    }

    @Test
    void addForReason_addsOneEntityAndIsIdempotent() {
        service.addForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, 4L);
        service.addForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, 4L);

        assertThat(promotedIds(EntityType.PROVIDER_PROFILE)).containsExactly(4L);
        assertThat(rowCount("TOP_RATED")).isEqualTo(1);
    }

    @Test
    void removeForReason_removesOnlyThatEntityForThatReason() {
        service.replaceForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, Set.of(1L, 2L));
        insertRow("PROVIDER_PROFILE", 1L, "SPONSORED");

        service.removeForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, 1L);
        service.removeForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, 99L);

        assertThat(promotedIds(EntityType.PROVIDER_PROFILE)).containsExactly(2L);
        assertThat(rowCount("SPONSORED")).isEqualTo(1);
    }

    @Test
    void findPromotedOfEveryReason_returnsTheRowsOfTheEntityType() {
        service.replaceForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, Set.of(1L, 2L));
        service.replaceForReason(EntityType.ADVERTISEMENT, PromotionReason.TOP_RATED, Set.of(5L));

        assertThat(repository.findPromoted(EntityType.PROVIDER_PROFILE)).extracting(PromotionPlacementDto::entityId)
                .containsExactly(1L, 2L);
    }

    @Test
    void findPromotedOfEveryReason_skipsARowWithAnUnknownReasonInsteadOfFailing() {
        insertRow("PROVIDER_PROFILE", 1L, "TOP_RATED");
        insertRow("PROVIDER_PROFILE", 2L, "FROM_A_FUTURE_VERSION");

        assertThat(repository.findPromoted(EntityType.PROVIDER_PROFILE)).extracting(PromotionPlacementDto::entityId)
                .containsExactly(1L);
    }

    @Test
    void findPromoted_mapsTheRowToTheDto() {
        service.replaceForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, Set.of(7L));

        assertThat(repository.findPromoted(EntityType.PROVIDER_PROFILE)).singleElement().satisfies(dto -> {
            assertThat(dto.entityType()).isEqualTo(EntityType.PROVIDER_PROFILE);
            assertThat(dto.entityId()).isEqualTo(7L);
            assertThat(dto.reason()).isEqualTo(PromotionReason.TOP_RATED);
        });
    }
}
