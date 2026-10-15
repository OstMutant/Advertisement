package org.ost.integrationtests.level1.feedback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.feedback.config.FeedbackAutoConfiguration;
import org.ost.feedback.entity.Feedback;
import org.ost.feedback.entity.FeedbackComment;
import org.ost.feedback.entity.FeedbackContent;
import org.ost.feedback.repository.FeedbackRepository;
import org.ost.feedback.repository.FeedbackRepository.FeedbackCommentView;
import org.ost.feedback.repository.FeedbackRepository.FeedbackView;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.ModerationQueueFilterDto;
import org.ost.platform.feedback.dto.ModerationQueueItemDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.platform.feedback.model.ModerationQueueItemType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

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

    /** Creates a full feedback entry (content + link + rating) the same way {@code FeedbackService.save} does on create. */
    private Feedback createFeedback(Long authorId, EntityType entityType, Long entityId, int rating, String text) {
        FeedbackContent content = feedbackRepository.saveContent(FeedbackContent.builder()
                .contentText(text)
                .moderationStatus(FeedbackModerationStatus.NEW)
                .build());
        Feedback saved = feedbackRepository.saveLink(Feedback.builder()
                .entityType(entityType)
                .entityId(entityId)
                .authorId(authorId)
                .contentId(content.getId())
                .build());
        feedbackRepository.upsertRating(saved.getId(), rating);
        return saved;
    }

    /** Creates the single reply to a feedback entry, mirroring {@code FeedbackService.saveComment} on create. */
    private FeedbackComment createComment(Long feedbackId, Long authorId, String text) {
        FeedbackContent content = feedbackRepository.saveContent(FeedbackContent.builder()
                .contentText(text)
                .moderationStatus(FeedbackModerationStatus.NEW)
                .build());
        return feedbackRepository.saveCommentLink(FeedbackComment.builder()
                .contentId(content.getId())
                .authorId(authorId)
                .feedbackId(feedbackId)
                .build());
    }

    private void backdateContentCreatedAt(Long contentId, Instant createdAt) {
        jdbcClient.sql("UPDATE feedback_content SET created_at = :createdAt WHERE id = :id")
                .paramSource(new MapSqlParameterSource().addValue("createdAt", Timestamp.from(createdAt)).addValue("id", contentId))
                .update();
    }

    @Test
    void save_and_findByAuthorAndEntity_returnsPersistedRow() {
        Long entityId = newEntityId();

        Feedback saved = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 5, "Great service, highly recommend.");

        Optional<FeedbackView> found = feedbackRepository.findByAuthorAndEntity(1L, EntityType.PROVIDER_PROFILE, entityId);

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(saved.getId());
        assertThat(found.get().rating()).isEqualTo(5);
        assertThat(found.get().feedbackText()).isEqualTo("Great service, highly recommend.");
        assertThat(found.get().moderationStatus()).isEqualTo(FeedbackModerationStatus.NEW);
        assertThat(found.get().version()).isZero();
    }

    @Test
    void findByAuthorAndEntity_noRow_returnsEmpty() {
        assertThat(feedbackRepository.findByAuthorAndEntity(1L, EntityType.PROVIDER_PROFILE, newEntityId())).isEmpty();
    }

    @Test
    void findByEntity_ordersByCreatedAtDescending() {
        Long entityId = newEntityId();
        createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 4, "First.");
        createFeedback(2L, EntityType.PROVIDER_PROFILE, entityId, 5, "Second.");

        List<FeedbackView> page = feedbackRepository.findByEntity(EntityType.PROVIDER_PROFILE, entityId, PageRequest.of(0, 10));

        assertThat(page).hasSize(2);
        assertThat(page.get(0).authorId()).isEqualTo(2L);
        assertThat(page.get(1).authorId()).isEqualTo(1L);
    }

    @Test
    void count_returnsNumberOfEntriesForEntity() {
        Long entityId = newEntityId();
        createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 4, "First.");
        createFeedback(2L, EntityType.PROVIDER_PROFILE, entityId, 5, "Second.");

        assertThat(feedbackRepository.count(EntityType.PROVIDER_PROFILE, entityId)).isEqualTo(2);
        assertThat(feedbackRepository.count(EntityType.PROVIDER_PROFILE, newEntityId())).isZero();
    }

    @Test
    void upsertAggregate_recomputesAvgRatingAndReviewCount() {
        Long entityId = newEntityId();
        createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 4, "First.");
        createFeedback(2L, EntityType.PROVIDER_PROFILE, entityId, 5, "Second.");

        feedbackRepository.upsertAggregate(EntityType.PROVIDER_PROFILE, entityId);

        assertThat(feedbackRepository.getAggregate(EntityType.PROVIDER_PROFILE, entityId)).hasValueSatisfying(agg -> {
            assertThat(agg.getReviewCount()).isEqualTo(2);
            assertThat(agg.getAvgRating()).isEqualTo(4.5);
        });
    }

    @Test
    void upsertAggregate_calledTwice_updatesSameRowInPlace() {
        Long entityId = newEntityId();
        createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 3, "First.");
        feedbackRepository.upsertAggregate(EntityType.PROVIDER_PROFILE, entityId);
        Long firstAggregateId = feedbackRepository.getAggregate(EntityType.PROVIDER_PROFILE, entityId).orElseThrow().getId();

        createFeedback(2L, EntityType.PROVIDER_PROFILE, entityId, 5, "Second.");
        feedbackRepository.upsertAggregate(EntityType.PROVIDER_PROFILE, entityId);

        assertThat(feedbackRepository.getAggregate(EntityType.PROVIDER_PROFILE, entityId)).hasValueSatisfying(agg -> {
            assertThat(agg.getId()).isEqualTo(firstAggregateId);
            assertThat(agg.getReviewCount()).isEqualTo(2);
        });
    }

    @Test
    void upsertAggregate_excludesHiddenFeedback() {
        Long entityId = newEntityId();
        Feedback visible = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 4, "Visible.");
        Feedback hidden = createFeedback(2L, EntityType.PROVIDER_PROFILE, entityId, 1, "Hidden.");
        feedbackRepository.flagContent(hidden.getContentId(), "Spam.");

        feedbackRepository.upsertAggregate(EntityType.PROVIDER_PROFILE, entityId);

        assertThat(feedbackRepository.getAggregate(EntityType.PROVIDER_PROFILE, entityId)).hasValueSatisfying(agg -> {
            assertThat(agg.getReviewCount()).isEqualTo(1);
            assertThat(agg.getAvgRating()).isEqualTo(4.0);
        });
    }

    @Test
    void upsertAggregate_allHidden_producesZeroReviewCountRow() {
        Long entityId = newEntityId();
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 5, "Only entry.");
        feedbackRepository.flagContent(feedback.getContentId(), "Spam.");

        feedbackRepository.upsertAggregate(EntityType.PROVIDER_PROFILE, entityId);

        assertThat(feedbackRepository.getAggregate(EntityType.PROVIDER_PROFILE, entityId)).hasValueSatisfying(agg -> {
            assertThat(agg.getReviewCount()).isZero();
            assertThat(agg.getAvgRating()).isZero();
        });
    }

    @Test
    void getAggregate_noFeedback_returnsEmpty() {
        assertThat(feedbackRepository.getAggregate(EntityType.PROVIDER_PROFILE, newEntityId())).isEmpty();
    }

    @Test
    void findCommentByFeedback_returnsTheSingleReply() {
        Long entityId = newEntityId();
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 5, "Root feedback.");

        createComment(feedback.getId(), 2L, "The one reply.");

        Optional<FeedbackCommentView> found = feedbackRepository.findCommentByFeedback(feedback.getId());

        assertThat(found).isPresent();
        assertThat(found.get().commentText()).isEqualTo("The one reply.");
        assertThat(found.get().authorId()).isEqualTo(2L);
    }

    @Test
    void findCommentByFeedback_noReply_returnsEmpty() {
        Long entityId = newEntityId();
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 5, "Root feedback.");

        assertThat(feedbackRepository.findCommentByFeedback(feedback.getId())).isEmpty();
    }

    @Test
    void deleteCommentHard_removesRowAndItsContent() {
        Long entityId = newEntityId();
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 5, "Root feedback.");
        FeedbackComment reply = createComment(feedback.getId(), 2L, "A reply.");
        Long contentId = reply.getContentId();

        feedbackRepository.deleteCommentHard(reply.getId(), contentId);

        assertThat(feedbackRepository.findCommentViewById(reply.getId())).isEmpty();
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM feedback_content WHERE id = :id")
                .paramSource(new MapSqlParameterSource().addValue("id", contentId))
                .query(Integer.class)
                .single()).isZero();
    }

    @Test
    void flagContent_setsHiddenStatusAndRecordsReason() {
        Long entityId = newEntityId();
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 5, "Root feedback.");

        feedbackRepository.flagContent(feedback.getContentId(), "Spam content.");

        Optional<FeedbackView> found = feedbackRepository.findViewById(feedback.getId());
        assertThat(found).isPresent();
        assertThat(found.get().moderationStatus()).isEqualTo(FeedbackModerationStatus.HIDDEN);
        assertThat(found.get().moderationReason()).isEqualTo("Spam content.");
    }

    @Test
    void findCommentViewById_pastEditWindow_contentStillReadable_callerEnforcesWindow() {
        Long entityId = newEntityId();
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 5, "Root feedback.");
        FeedbackComment comment = createComment(feedback.getId(), 2L, "A comment.");
        backdateContentCreatedAt(comment.getContentId(), Instant.now().minus(49, ChronoUnit.HOURS));

        Optional<FeedbackCommentView> view = feedbackRepository.findCommentViewById(comment.getId());

        assertThat(view).isPresent();
        assertThat(view.get().createdAt()).isBefore(Instant.now().minus(48, ChronoUnit.HOURS));
    }

    @Test
    void deleteFeedbackHard_cascadesThroughItsReply() {
        Long entityId = newEntityId();
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 5, "Root feedback.");
        createComment(feedback.getId(), 2L, "A reply.");

        feedbackRepository.deleteFeedbackHard(feedback.getId(), feedback.getContentId());

        Integer feedbackCommentCount = jdbcClient.sql("SELECT COUNT(*) FROM feedback_comment WHERE feedback_id = :feedbackId")
                .paramSource(new MapSqlParameterSource().addValue("feedbackId", feedback.getId()))
                .query(Integer.class)
                .single();
        assertThat(feedbackCommentCount).isZero();
    }

    @Test
    void findModerationQueuePage_commentsAcrossDifferentFeedbackEntries_resolveDistinctEntityIds() {
        Long entityId1 = newEntityId();
        Long entityId2 = newEntityId();
        Feedback feedback1 = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId1, 1, "Bad one.");
        Feedback feedback2 = createFeedback(2L, EntityType.PROVIDER_PROFILE, entityId2, 1, "Bad two.");
        FeedbackComment comment1 = createComment(feedback1.getId(), 3L, "Reply one.");
        FeedbackComment comment2 = createComment(feedback2.getId(), 4L, "Reply two.");
        feedbackRepository.flagContent(comment1.getContentId(), "Spam.");
        feedbackRepository.flagContent(comment2.getContentId(), "Spam.");

        List<FeedbackRepository.ModerationQueueRow> rows = feedbackRepository.findModerationQueuePage(
                ModerationQueueFilterDto.builder().types(Set.of(ModerationQueueItemType.COMMENT)).build(),
                Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), PageRequest.of(0, 20), null);

        assertThat(rows).hasSize(2);
        FeedbackRepository.ModerationQueueRow row1 = rows.stream().filter(r -> r.id().equals(comment1.getId())).findFirst().orElseThrow();
        FeedbackRepository.ModerationQueueRow row2 = rows.stream().filter(r -> r.id().equals(comment2.getId())).findFirst().orElseThrow();
        assertThat(row1.entityId()).isEqualTo(entityId1);
        assertThat(row2.entityId()).isEqualTo(entityId2);
        assertThat(row1.entityId()).isNotEqualTo(row2.entityId());
    }

    @Test
    void findModerationQueuePage_defaultStatus_onlyHiddenShown() {
        Feedback hidden = createFeedback(1L, EntityType.PROVIDER_PROFILE, newEntityId(), 1, "Hidden one.");
        feedbackRepository.flagContent(hidden.getContentId(), "Spam.");
        Feedback confirmed = createFeedback(2L, EntityType.PROVIDER_PROFILE, newEntityId(), 1, "Confirmed one.");
        feedbackRepository.flagContent(confirmed.getContentId(), "Spam.");
        feedbackRepository.updateModerationStatus(confirmed.getContentId(), FeedbackModerationStatus.CONFIRMED_HIDDEN);

        List<FeedbackRepository.ModerationQueueRow> rows = feedbackRepository.findModerationQueuePage(
                ModerationQueueFilterDto.empty(), Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), PageRequest.of(0, 20), null);

        assertThat(rows).extracting(FeedbackRepository.ModerationQueueRow::id).containsExactly(hidden.getId());
        assertThat(feedbackRepository.countModerationQueue(ModerationQueueFilterDto.empty(), null)).isEqualTo(1);
    }

    @Test
    void findModerationQueuePage_reasonFilter_matchesIgnoringCase() {
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, newEntityId(), 1, "Bad.");
        feedbackRepository.flagContent(feedback.getContentId(), "VerySpecificReasonText");

        List<FeedbackRepository.ModerationQueueRow> matching = feedbackRepository.findModerationQueuePage(
                ModerationQueueFilterDto.builder().reasonText("veryspecificreason").build(),
                Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), PageRequest.of(0, 20), null);
        assertThat(matching).hasSize(1);

        List<FeedbackRepository.ModerationQueueRow> nonMatching = feedbackRepository.findModerationQueuePage(
                ModerationQueueFilterDto.builder().reasonText("totally different").build(),
                Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), PageRequest.of(0, 20), null);
        assertThat(nonMatching).isEmpty();
    }

    @Test
    void findModerationQueuePage_dateRangeFilter_excludesOutOfRange() {
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, newEntityId(), 1, "Bad.");
        feedbackRepository.flagContent(feedback.getContentId(), "Spam.");
        backdateContentCreatedAt(feedback.getContentId(), Instant.now().minus(10, ChronoUnit.DAYS));

        List<FeedbackRepository.ModerationQueueRow> excluded = feedbackRepository.findModerationQueuePage(
                ModerationQueueFilterDto.builder().createdAtStart(Instant.now().minus(1, ChronoUnit.DAYS)).build(),
                Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), PageRequest.of(0, 20), null);
        assertThat(excluded).isEmpty();

        List<FeedbackRepository.ModerationQueueRow> included = feedbackRepository.findModerationQueuePage(
                ModerationQueueFilterDto.builder().createdAtEnd(Instant.now().minus(1, ChronoUnit.DAYS)).build(),
                Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), PageRequest.of(0, 20), null);
        assertThat(included).hasSize(1);
    }

    @Test
    void findModerationQueuePage_authorIdsFilter_restrictsToMatchingAuthors() {
        Feedback feedback1 = createFeedback(101L, EntityType.PROVIDER_PROFILE, newEntityId(), 1, "By author 101.");
        Feedback feedback2 = createFeedback(202L, EntityType.PROVIDER_PROFILE, newEntityId(), 1, "By author 202.");
        feedbackRepository.flagContent(feedback1.getContentId(), "Spam.");
        feedbackRepository.flagContent(feedback2.getContentId(), "Spam.");

        List<FeedbackRepository.ModerationQueueRow> rows = feedbackRepository.findModerationQueuePage(
                ModerationQueueFilterDto.empty(), Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)),
                PageRequest.of(0, 20), Set.of(101L));

        assertThat(rows).extracting(FeedbackRepository.ModerationQueueRow::authorId).containsExactly(101L);
    }

    @Test
    void findModerationQueuePage_typeSort_ordersFeedbackAndCommentsByDiscriminator() {
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, newEntityId(), 1, "Bad.");
        FeedbackComment comment = createComment(feedback.getId(), 2L, "Reply.");
        feedbackRepository.flagContent(feedback.getContentId(), "Spam.");
        feedbackRepository.flagContent(comment.getContentId(), "Spam.");

        List<FeedbackRepository.ModerationQueueRow> ascByType = feedbackRepository.findModerationQueuePage(
                ModerationQueueFilterDto.empty(), Sort.by(Sort.Order.asc(ModerationQueueItemDto.Fields.type)),
                PageRequest.of(0, 20), null);

        assertThat(ascByType).extracting(FeedbackRepository.ModerationQueueRow::itemType)
                .containsExactly("COMMENT", "FEEDBACK");
    }

    @Test
    void countModerationQueue_matchesTotal_independentOfPageLimit() {
        Feedback f1 = createFeedback(1L, EntityType.PROVIDER_PROFILE, newEntityId(), 1, "One.");
        Feedback f2 = createFeedback(2L, EntityType.PROVIDER_PROFILE, newEntityId(), 1, "Two.");
        feedbackRepository.flagContent(f1.getContentId(), "Spam.");
        feedbackRepository.flagContent(f2.getContentId(), "Spam.");

        long total = feedbackRepository.countModerationQueue(ModerationQueueFilterDto.empty(), null);
        assertThat(total).isEqualTo(2);

        List<FeedbackRepository.ModerationQueueRow> firstPage = feedbackRepository.findModerationQueuePage(
                ModerationQueueFilterDto.empty(), Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)),
                PageRequest.of(0, 1), null);
        assertThat(firstPage).hasSize(1);
    }
}
