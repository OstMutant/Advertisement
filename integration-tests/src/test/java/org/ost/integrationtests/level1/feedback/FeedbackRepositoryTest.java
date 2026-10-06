package org.ost.integrationtests.level1.feedback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.feedback.config.FeedbackAutoConfiguration;
import org.ost.feedback.entity.Feedback;
import org.ost.feedback.entity.FeedbackComment;
import org.ost.feedback.entity.FeedbackContent;
import org.ost.feedback.repository.FeedbackRepository;
import org.ost.feedback.repository.FeedbackRepository.FeedbackCommentReactionView;
import org.ost.feedback.repository.FeedbackRepository.FeedbackCommentView;
import org.ost.feedback.repository.FeedbackRepository.FeedbackView;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
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

    /** Creates a comment (content + comment link), mirroring {@code FeedbackService.saveComment} on create. */
    private FeedbackComment createComment(Long feedbackId, Long parentCommentId, Long authorId, String text) {
        FeedbackContent content = feedbackRepository.saveContent(FeedbackContent.builder()
                .contentText(text)
                .moderationStatus(FeedbackModerationStatus.NEW)
                .build());
        return feedbackRepository.saveCommentLink(FeedbackComment.builder()
                .contentId(content.getId())
                .authorId(authorId)
                .feedbackId(feedbackId)
                .parentCommentId(parentCommentId)
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
    void getAggregate_noFeedback_returnsEmpty() {
        assertThat(feedbackRepository.getAggregate(EntityType.PROVIDER_PROFILE, newEntityId())).isEmpty();
    }

    @Test
    void findCommentsByFeedback_ordersDepthFirstBySiblingAcrossThreeLevels() {
        Long entityId = newEntityId();
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 5, "Root feedback.");

        FeedbackComment replyA = createComment(feedback.getId(), null, 2L, "Reply A (top-level).");
        FeedbackComment replyB = createComment(feedback.getId(), null, 3L, "Reply B (top-level).");
        FeedbackComment replyA1 = createComment(feedback.getId(), replyA.getId(), 4L, "Reply A.1 (nested under A).");
        createComment(feedback.getId(), replyA1.getId(), 5L, "Reply A.1.1 (nested under A.1).");
        createComment(feedback.getId(), replyB.getId(), 6L, "Reply B.1 (nested under B).");

        List<FeedbackCommentView> tree = feedbackRepository.findCommentsByFeedback(feedback.getId());

        assertThat(tree).hasSize(5);
        assertThat(tree.stream().map(FeedbackCommentView::commentText)).containsExactly(
                "Reply A (top-level).",
                "Reply A.1 (nested under A).",
                "Reply A.1.1 (nested under A.1).",
                "Reply B (top-level).",
                "Reply B.1 (nested under B).");
    }

    @Test
    void reaction_vote_changeType_thenUnvote_roundTrips() {
        Long entityId = newEntityId();
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 5, "Root feedback.");
        FeedbackComment comment = createComment(feedback.getId(), null, 2L, "A comment.");

        feedbackRepository.upsertReaction(comment.getId(), 3L, "UP");
        assertThat(feedbackRepository.findReactionType(comment.getId(), 3L)).contains("UP");

        feedbackRepository.upsertReaction(comment.getId(), 3L, "DOWN");
        assertThat(feedbackRepository.findReactionType(comment.getId(), 3L)).contains("DOWN");
        List<FeedbackCommentReactionView> afterChange = feedbackRepository.findReactionsByComments(List.of(comment.getId()));
        assertThat(afterChange).hasSize(1);
        assertThat(afterChange.get(0).reactionType()).isEqualTo("DOWN");

        feedbackRepository.deleteReaction(comment.getId(), 3L);
        assertThat(feedbackRepository.findReactionType(comment.getId(), 3L)).isEmpty();
        assertThat(feedbackRepository.findReactionsByComments(List.of(comment.getId()))).isEmpty();
    }

    @Test
    void deleteCommentHard_leafComment_removesRowAndItsContent() {
        Long entityId = newEntityId();
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 5, "Root feedback.");
        FeedbackComment leaf = createComment(feedback.getId(), null, 2L, "A leaf comment.");
        Long contentId = leaf.getContentId();

        assertThat(feedbackRepository.countChildren(leaf.getId())).isZero();
        feedbackRepository.deleteCommentHard(leaf.getId(), contentId);

        assertThat(feedbackRepository.findCommentViewById(leaf.getId())).isEmpty();
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM feedback_content WHERE id = :id")
                .paramSource(new MapSqlParameterSource().addValue("id", contentId))
                .query(Integer.class)
                .single()).isZero();
    }

    @Test
    void tombstoneComment_commentWithReplies_setsTextNullButKeepsRowAndChildren() {
        Long entityId = newEntityId();
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 5, "Root feedback.");
        FeedbackComment parent = createComment(feedback.getId(), null, 2L, "Parent comment.");
        FeedbackComment child = createComment(feedback.getId(), parent.getId(), 3L, "Child reply.");

        assertThat(feedbackRepository.countChildren(parent.getId())).isEqualTo(1);
        feedbackRepository.tombstoneComment(parent.getContentId());

        Optional<FeedbackCommentView> parentView = feedbackRepository.findCommentViewById(parent.getId());
        assertThat(parentView).isPresent();
        assertThat(parentView.get().commentText()).isNull();
        assertThat(feedbackRepository.findCommentViewById(child.getId())).isPresent();
    }

    @Test
    void findCommentViewById_pastEditWindow_contentStillReadable_callerEnforcesWindow() {
        Long entityId = newEntityId();
        Feedback feedback = createFeedback(1L, EntityType.PROVIDER_PROFILE, entityId, 5, "Root feedback.");
        FeedbackComment comment = createComment(feedback.getId(), null, 2L, "A comment.");
        backdateContentCreatedAt(comment.getContentId(), Instant.now().minus(49, ChronoUnit.HOURS));

        Optional<FeedbackCommentView> view = feedbackRepository.findCommentViewById(comment.getId());

        assertThat(view).isPresent();
        assertThat(view.get().createdAt()).isBefore(Instant.now().minus(48, ChronoUnit.HOURS));
    }
}
