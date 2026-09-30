package org.ost.integrationtests.level1.feedback;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.feedback.config.FeedbackAutoConfiguration;
import org.ost.feedback.services.FeedbackService;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link FeedbackService#save} sanitization, edit-window enforcement, and aggregate recompute. */
@SpringBootTest(classes = {
        FeedbackAutoConfiguration.class,
        RepositoryTestSupport.class,
        ValidationAutoConfiguration.class
})
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=2")
class FeedbackServiceTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong ENTITY_ID_SEQ = new AtomicLong(1);

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private static Long newEntityId() {
        return ENTITY_ID_SEQ.incrementAndGet();
    }

    private static FeedbackSaveDto newSaveDto(Long id, Long authorId, Long entityId, int rating, String text, Long version) {
        return new FeedbackSaveDto(id, EntityType.PROVIDER_PROFILE, entityId, authorId, rating, text, version);
    }

    @Test
    void save_sanitizesHtmlAndRecomputesAggregate() {
        Long entityId = newEntityId();

        FeedbackDto saved = feedbackService.save(newSaveDto(null, 1L, entityId, 5,
                "<script>alert(1)</script>Great <b>master</b>!", null));

        assertThat(saved.feedbackText()).doesNotContain("<script>").contains("<b>master</b>");
        FeedbackAggregateDto aggregate = feedbackService.getAggregate(EntityType.PROVIDER_PROFILE, entityId);
        assertThat(aggregate.reviewCount()).isEqualTo(1);
        assertThat(aggregate.avgRating()).isEqualTo(5.0);
    }

    @Test
    void save_secondCallSameAuthorEntity_updatesExistingRowInPlace() {
        Long entityId = newEntityId();
        FeedbackDto first = feedbackService.save(newSaveDto(null, 1L, entityId, 3, "Decent.", null));

        FeedbackDto second = feedbackService.save(newSaveDto(null, 1L, entityId, 4, "Actually pretty good.", first.version()));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.rating()).isEqualTo(4);
        assertThat(feedbackService.count(EntityType.PROVIDER_PROFILE, entityId)).isEqualTo(1);
    }

    @Test
    void save_pastEditWindow_throwsIllegalStateException() {
        Long entityId = newEntityId();
        FeedbackDto first = feedbackService.save(newSaveDto(null, 1L, entityId, 3, "Decent.", null));
        backdateCreatedAt(first.id(), Instant.now().minus(49, ChronoUnit.HOURS));

        assertThatThrownBy(() -> feedbackService.save(newSaveDto(null, 1L, entityId, 5, "Edit attempt.", first.version())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void save_ratingOutOfRange_throwsConstraintViolationException() {
        assertThatThrownBy(() -> feedbackService.save(newSaveDto(null, 1L, newEntityId(), 6, "Too high.", null)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void save_blankText_throwsConstraintViolationException() {
        assertThatThrownBy(() -> feedbackService.save(newSaveDto(null, 1L, newEntityId(), 4, "   ", null)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void findForEntity_returnsEntriesForThatEntityOnly() {
        Long entityId = newEntityId();
        Long otherEntityId = newEntityId();
        feedbackService.save(newSaveDto(null, 1L, entityId, 5, "For this profile.", null));
        feedbackService.save(newSaveDto(null, 1L, otherEntityId, 2, "For a different profile.", null));

        List<FeedbackDto> found = feedbackService.findForEntity(EntityType.PROVIDER_PROFILE, entityId, 0, 10);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).entityId()).isEqualTo(entityId);
    }

    @Test
    void getAggregate_noFeedback_returnsZeroValueDefault() {
        FeedbackAggregateDto aggregate = feedbackService.getAggregate(EntityType.PROVIDER_PROFILE, newEntityId());

        assertThat(aggregate.avgRating()).isZero();
        assertThat(aggregate.reviewCount()).isZero();
    }

    private void backdateCreatedAt(Long feedbackId, Instant createdAt) {
        jdbcClient.sql("UPDATE feedback SET created_at = :createdAt WHERE id = :id")
                .paramSource(new MapSqlParameterSource().addValue("createdAt", Timestamp.from(createdAt)).addValue("id", feedbackId))
                .update();
    }
}
