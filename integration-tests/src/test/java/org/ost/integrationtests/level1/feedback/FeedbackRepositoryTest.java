package org.ost.integrationtests.level1.feedback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.feedback.config.FeedbackAutoConfiguration;
import org.ost.feedback.entity.Feedback;
import org.ost.feedback.repository.FeedbackRepository;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.core.StaleWriteException;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testcontainers repository test for {@link FeedbackRepository} -- entity ids are synthetic longs, since {@code feedback}/{@code feedback_aggregate} carry no FK to any other table. */
@SpringBootTest(classes = {
        FeedbackAutoConfiguration.class,
        RepositoryTestSupport.class,
        ValidationAutoConfiguration.class
})
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=2")
class FeedbackRepositoryTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong ENTITY_ID_SEQ = new AtomicLong(1);

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private static Long newEntityId() {
        return ENTITY_ID_SEQ.incrementAndGet();
    }

    private static Feedback newFeedback(Long authorId, EntityType entityType, Long entityId, int rating) {
        return Feedback.builder()
                .authorId(authorId)
                .entityType(entityType)
                .entityId(entityId)
                .rating(rating)
                .feedbackText("Great service, highly recommend.")
                .moderationStatus(FeedbackModerationStatus.NEW)
                .build();
    }

    @Test
    void save_and_findByAuthorAndEntity_returnsPersistedRow() {
        Long entityId = newEntityId();

        Feedback saved = feedbackRepository.save(newFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 5));

        Optional<Feedback> found = feedbackRepository.findByAuthorAndEntity(1L, EntityType.PROVIDER_PROFILE, entityId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getRating()).isEqualTo(5);
        assertThat(found.get().getModerationStatus()).isEqualTo(FeedbackModerationStatus.NEW);
        assertThat(found.get().getVersion()).isZero();
    }

    @Test
    void findByAuthorAndEntity_noRow_returnsEmpty() {
        assertThat(feedbackRepository.findByAuthorAndEntity(1L, EntityType.PROVIDER_PROFILE, newEntityId())).isEmpty();
    }

    @Test
    void findByEntity_ordersByCreatedAtDescending() {
        Long entityId = newEntityId();
        feedbackRepository.save(newFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 4));
        feedbackRepository.save(newFeedback(2L, EntityType.PROVIDER_PROFILE, entityId, 5));

        List<Feedback> page = feedbackRepository.findByEntity(EntityType.PROVIDER_PROFILE, entityId, PageRequest.of(0, 10));

        assertThat(page).hasSize(2);
        assertThat(page.get(0).getAuthorId()).isEqualTo(2L);
        assertThat(page.get(1).getAuthorId()).isEqualTo(1L);
    }

    @Test
    void count_returnsNumberOfEntriesForEntity() {
        Long entityId = newEntityId();
        feedbackRepository.save(newFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 4));
        feedbackRepository.save(newFeedback(2L, EntityType.PROVIDER_PROFILE, entityId, 5));

        assertThat(feedbackRepository.count(EntityType.PROVIDER_PROFILE, entityId)).isEqualTo(2);
        assertThat(feedbackRepository.count(EntityType.PROVIDER_PROFILE, newEntityId())).isZero();
    }

    @Test
    void save_staleVersion_throwsStaleWriteException() {
        Long entityId = newEntityId();
        Feedback saved = feedbackRepository.save(newFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 3));
        feedbackRepository.save(Feedback.builder()
                .id(saved.getId())
                .authorId(1L)
                .entityType(EntityType.PROVIDER_PROFILE)
                .entityId(entityId)
                .rating(4)
                .feedbackText("Updated text.")
                .moderationStatus(FeedbackModerationStatus.NEW)
                .createdAt(saved.getCreatedAt())
                .version(saved.getVersion())
                .build());

        Feedback staleUpdate = Feedback.builder()
                .id(saved.getId())
                .authorId(1L)
                .entityType(EntityType.PROVIDER_PROFILE)
                .entityId(entityId)
                .rating(2)
                .feedbackText("Stale text.")
                .moderationStatus(FeedbackModerationStatus.NEW)
                .createdAt(saved.getCreatedAt())
                .version(saved.getVersion())
                .build();

        assertThatThrownBy(() -> feedbackRepository.save(staleUpdate))
                .isInstanceOf(StaleWriteException.class);
    }

    @Test
    void upsertAggregate_recomputesAvgRatingAndReviewCount() {
        Long entityId = newEntityId();
        feedbackRepository.save(newFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 4));
        feedbackRepository.save(newFeedback(2L, EntityType.PROVIDER_PROFILE, entityId, 5));

        feedbackRepository.upsertAggregate(EntityType.PROVIDER_PROFILE, entityId);

        assertThat(feedbackRepository.getAggregate(EntityType.PROVIDER_PROFILE, entityId)).hasValueSatisfying(agg -> {
            assertThat(agg.getReviewCount()).isEqualTo(2);
            assertThat(agg.getAvgRating()).isEqualTo(4.5);
        });
    }

    @Test
    void upsertAggregate_calledTwice_updatesSameRowInPlace() {
        Long entityId = newEntityId();
        feedbackRepository.save(newFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 3));
        feedbackRepository.upsertAggregate(EntityType.PROVIDER_PROFILE, entityId);
        Long firstAggregateId = feedbackRepository.getAggregate(EntityType.PROVIDER_PROFILE, entityId).orElseThrow().getId();

        feedbackRepository.save(newFeedback(2L, EntityType.PROVIDER_PROFILE, entityId, 5));
        feedbackRepository.upsertAggregate(EntityType.PROVIDER_PROFILE, entityId);

        assertThat(feedbackRepository.getAggregate(EntityType.PROVIDER_PROFILE, entityId)).hasValueSatisfying(agg -> {
            assertThat(agg.getId()).isEqualTo(firstAggregateId);
            assertThat(agg.getReviewCount()).isEqualTo(2);
        });
    }

    @Test
    void getAggregate_noFeedback_returnsEmpty() {
        assertThat(feedbackRepository.getAggregate(EntityType.PROVIDER_PROFILE, newEntityId())).isEmpty();
    }
}
