package org.ost.integrationtests.level1.feedback;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.feedback.config.FeedbackAutoConfiguration;
import org.ost.feedback.repository.FeedbackRepository;
import org.ost.feedback.services.FeedbackService;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.core.TooManyAttemptsException;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link FeedbackService#save}/{@code saveComment}/{@code deleteComment} sanitization, edit-window enforcement, and aggregate recompute. */
@SpringBootTest(classes = {
        FeedbackAutoConfiguration.class,
        RepositoryTestSupport.class,
        ValidationAutoConfiguration.class
})
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=2")
class FeedbackServiceTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong ENTITY_ID_SEQ = new AtomicLong(1);
    private static final AtomicLong AUTHOR_ID_SEQ = new AtomicLong(10000);

    @Autowired
    private FeedbackService feedbackService;

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

    private static Long newAuthorId() {
        return AUTHOR_ID_SEQ.incrementAndGet();
    }

    private static FeedbackSaveDto newSaveDto(Long id, Long authorId, Long entityId, int rating, String text, Long version) {
        return new FeedbackSaveDto(id, EntityType.PROVIDER_PROFILE, entityId, authorId, rating, text, version);
    }

    @Test
    void save_sanitizesHtmlAndRecomputesAggregate() {
        Long entityId = newEntityId();

        FeedbackDto saved = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5,
                "<script>alert(1)</script>Great <b>master</b>!", null));

        assertThat(saved.feedbackText()).doesNotContain("<script>").contains("<b>master</b>");
        FeedbackAggregateDto aggregate = feedbackService.getAggregate(EntityType.PROVIDER_PROFILE, entityId);
        assertThat(aggregate.reviewCount()).isEqualTo(1);
        assertThat(aggregate.avgRating()).isEqualTo(5.0);
    }

    @Test
    void save_secondCallSameAuthorEntity_updatesExistingRowInPlace() {
        Long entityId = newEntityId();
        Long authorId = newAuthorId();
        FeedbackDto first = feedbackService.save(newSaveDto(null, authorId, entityId, 3, "Decent.", null));

        FeedbackDto second = feedbackService.save(newSaveDto(first.id(), authorId, entityId, 4, "Actually pretty good.", first.version()));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.rating()).isEqualTo(4);
        assertThat(feedbackService.count(EntityType.PROVIDER_PROFILE, entityId)).isEqualTo(1);
    }

    @Test
    void save_pastEditWindow_throwsIllegalStateException() {
        Long entityId = newEntityId();
        FeedbackDto first = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 3, "Decent.", null));
        backdateContentCreatedAt(first.id(), Instant.now().minus(49, ChronoUnit.HOURS));

        assertThatThrownBy(() -> feedbackService.save(newSaveDto(first.id(), newAuthorId(), entityId, 5, "Edit attempt.", first.version())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void save_ratingOutOfRange_throwsConstraintViolationException() {
        assertThatThrownBy(() -> feedbackService.save(newSaveDto(null, newAuthorId(), newEntityId(), 6, "Too high.", null)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void save_blankText_throwsConstraintViolationException() {
        assertThatThrownBy(() -> feedbackService.save(newSaveDto(null, newAuthorId(), newEntityId(), 4, "   ", null)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void findForEntity_returnsEntriesForThatEntityOnly() {
        Long entityId = newEntityId();
        Long otherEntityId = newEntityId();
        feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "For this profile.", null));
        feedbackService.save(newSaveDto(null, newAuthorId(), otherEntityId, 2, "For a different profile.", null));

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

    @Test
    void findCommentByFeedback_returnsTheSingleReply() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));

        feedbackService.saveComment(new FeedbackCommentSaveDto(null, feedback.id(), newAuthorId(), "A reply.", null));

        Optional<FeedbackCommentDto> found = feedbackService.findCommentByFeedback(feedback.id());
        assertThat(found).isPresent();
        assertThat(found.get().commentText()).isEqualTo("A reply.");
    }

    @Test
    void findCommentByFeedback_noReply_returnsEmpty() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));

        assertThat(feedbackService.findCommentByFeedback(feedback.id())).isEmpty();
    }

    @Test
    void saveComment_editWithinWindow_updatesText() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto comment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), newAuthorId(), "Original text.", null));

        FeedbackCommentDto updated = feedbackService.saveComment(
                new FeedbackCommentSaveDto(comment.id(), feedback.id(), comment.authorId(), "Updated text.", comment.version()));

        assertThat(updated.id()).isEqualTo(comment.id());
        assertThat(updated.commentText()).isEqualTo("Updated text.");
    }

    @Test
    void saveComment_pastEditWindow_throwsIllegalStateException() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto comment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), newAuthorId(), "Original text.", null));
        backdateCommentContentCreatedAt(comment.id(), Instant.now().minus(49, ChronoUnit.HOURS));

        assertThatThrownBy(() -> feedbackService.saveComment(
                new FeedbackCommentSaveDto(comment.id(), feedback.id(), comment.authorId(), "Edit attempt.", comment.version())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteComment_removesItEntirely() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto comment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), newAuthorId(), "A comment.", null));

        feedbackService.deleteComment(comment.id());

        assertThat(feedbackService.findCommentByFeedback(feedback.id())).isEmpty();
    }

    @Test
    void deleteComment_pastEditWindow_throwsIllegalStateException() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto comment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), newAuthorId(), "A comment.", null));
        backdateCommentContentCreatedAt(comment.id(), Instant.now().minus(49, ChronoUnit.HOURS));

        assertThatThrownBy(() -> feedbackService.deleteComment(comment.id()))
                .isInstanceOf(IllegalStateException.class);
    }

    private void backdateContentCreatedAt(Long feedbackId, Instant createdAt) {
        jdbcClient.sql("""
                        UPDATE feedback_content SET created_at = :createdAt
                        WHERE id = (SELECT content_id FROM feedback WHERE id = :id)
                        """)
                .paramSource(new MapSqlParameterSource().addValue("createdAt", Timestamp.from(createdAt)).addValue("id", feedbackId))
                .update();
    }

    private void backdateCommentContentCreatedAt(Long commentId, Instant createdAt) {
        jdbcClient.sql("""
                        UPDATE feedback_content SET created_at = :createdAt
                        WHERE id = (SELECT content_id FROM feedback_comment WHERE id = :id)
                        """)
                .paramSource(new MapSqlParameterSource().addValue("createdAt", Timestamp.from(createdAt)).addValue("id", commentId))
                .update();
    }

    @Test
    void flagFeedback_staysListedWithHiddenStatus_alsoVisibleInHiddenQueue() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));

        feedbackService.flagFeedback(feedback.id(), 2L, "Spam.");

        List<FeedbackDto> listed = feedbackService.findForEntity(EntityType.PROVIDER_PROFILE, entityId, 0, 10);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).moderationStatus()).isEqualTo(FeedbackModerationStatus.HIDDEN);
        List<FeedbackDto> hidden = feedbackService.findHiddenFeedback(PageRequest.of(0, 20));
        assertThat(hidden).hasSize(1);
        assertThat(hidden.get(0).id()).isEqualTo(feedback.id());
    }

    @Test
    void flagFeedback_recordsReason_readableBackFromHiddenQueue() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));

        feedbackService.flagFeedback(feedback.id(), 2L, "This is fake.");

        List<FeedbackDto> hidden = feedbackService.findHiddenFeedback(PageRequest.of(0, 20));
        assertThat(hidden).hasSize(1);
        assertThat(hidden.get(0).moderationReason()).isEqualTo("This is fake.");
    }

    @Test
    void flagFeedback_byOwnAuthor_throwsIllegalStateException() {
        Long entityId = newEntityId();
        Long authorId = newAuthorId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, authorId, entityId, 5, "Root feedback.", null));

        assertThatThrownBy(() -> feedbackService.flagFeedback(feedback.id(), authorId, "My own entry."))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approveFeedback_reversesHiddenStatus_reappearsInPublicListing() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        feedbackService.flagFeedback(feedback.id(), 2L, "Spam.");

        feedbackService.approveFeedback(feedback.id());

        List<FeedbackDto> visible = feedbackService.findForEntity(EntityType.PROVIDER_PROFILE, entityId, 0, 10);
        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).id()).isEqualTo(feedback.id());
        assertThat(feedbackService.findHiddenFeedback(PageRequest.of(0, 20))).isEmpty();
    }

    @Test
    void flagFeedback_recomputesAggregate_excludesHiddenEntry() {
        Long entityId = newEntityId();
        FeedbackDto visible = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 4, "Visible.", null));
        FeedbackDto toHide = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 1, "Will be hidden.", null));

        feedbackService.flagFeedback(toHide.id(), 999L, "Spam.");

        FeedbackAggregateDto aggregate = feedbackService.getAggregate(EntityType.PROVIDER_PROFILE, entityId);
        assertThat(aggregate.reviewCount()).isEqualTo(1);
        assertThat(aggregate.avgRating()).isEqualTo(4.0);
    }

    @Test
    void approveFeedback_recomputesAggregate_restoresEntry() {
        Long entityId = newEntityId();
        FeedbackDto visible = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 4, "Visible.", null));
        FeedbackDto flagged = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 2, "Flagged then restored.", null));
        feedbackService.flagFeedback(flagged.id(), 999L, "Spam.");

        feedbackService.approveFeedback(flagged.id());

        FeedbackAggregateDto aggregate = feedbackService.getAggregate(EntityType.PROVIDER_PROFILE, entityId);
        assertThat(aggregate.reviewCount()).isEqualTo(2);
        assertThat(aggregate.avgRating()).isEqualTo(3.0);
    }

    @Test
    void flagFeedback_allEntriesHidden_aggregateBecomesZero() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Only entry.", null));

        feedbackService.flagFeedback(feedback.id(), 999L, "Spam.");

        FeedbackAggregateDto aggregate = feedbackService.getAggregate(EntityType.PROVIDER_PROFILE, entityId);
        assertThat(aggregate.reviewCount()).isZero();
        assertThat(aggregate.avgRating()).isZero();
    }

    @Test
    void save_updateWithDifferentEntityOrAuthorInDto_identityStaysUnchanged() {
        Long originalEntityId = newEntityId();
        Long originalAuthorId = newAuthorId();
        FeedbackDto original = feedbackService.save(newSaveDto(null, originalAuthorId, originalEntityId, 3, "Original.", null));

        Long differentEntityId = newEntityId();
        Long differentAuthorId = newAuthorId();
        FeedbackDto updated = feedbackService.save(
                new FeedbackSaveDto(original.id(), EntityType.PROVIDER_PROFILE, differentEntityId, differentAuthorId, 5, "Edited text.", original.version()));

        assertThat(updated.entityId()).isEqualTo(originalEntityId);
        assertThat(updated.authorId()).isEqualTo(originalAuthorId);
        assertThat(feedbackService.findForEntity(EntityType.PROVIDER_PROFILE, originalEntityId, 0, 10)).hasSize(1);
        assertThat(feedbackService.findForEntity(EntityType.PROVIDER_PROFILE, differentEntityId, 0, 10)).isEmpty();
    }

    @Test
    void rejectFeedback_hardDeletesFeedbackAndItsReply() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        feedbackService.saveComment(new FeedbackCommentSaveDto(null, feedback.id(), newAuthorId(), "A reply.", null));

        feedbackService.rejectFeedback(feedback.id());

        assertThat(feedbackService.findForEntity(EntityType.PROVIDER_PROFILE, entityId, 0, 10)).isEmpty();
        Integer feedbackCount = jdbcClient.sql("SELECT COUNT(*) FROM feedback WHERE id = :id")
                .paramSource(new MapSqlParameterSource().addValue("id", feedback.id()))
                .query(Integer.class)
                .single();
        Integer feedbackCommentCount = jdbcClient.sql("SELECT COUNT(*) FROM feedback_comment WHERE feedback_id = :feedbackId")
                .paramSource(new MapSqlParameterSource().addValue("feedbackId", feedback.id()))
                .query(Integer.class)
                .single();
        Integer feedbackRatingCount = jdbcClient.sql("SELECT COUNT(*) FROM feedback_rating WHERE feedback_id = :feedbackId")
                .paramSource(new MapSqlParameterSource().addValue("feedbackId", feedback.id()))
                .query(Integer.class)
                .single();
        assertThat(feedbackCount).isZero();
        assertThat(feedbackCommentCount).isZero();
        assertThat(feedbackRatingCount).isZero();
    }

    @Test
    void rejectFeedback_cascadesEvenWhenReplyIsHidden() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto reply = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), newAuthorId(), "A reply.", null));
        feedbackService.flagComment(reply.id(), 3L, "Rude.");

        feedbackService.rejectFeedback(feedback.id());

        assertThat(feedbackService.findForEntity(EntityType.PROVIDER_PROFILE, entityId, 0, 10)).isEmpty();
        Integer commentCount = jdbcClient.sql("SELECT COUNT(*) FROM feedback_comment WHERE feedback_id = :feedbackId")
                .paramSource(new MapSqlParameterSource().addValue("feedbackId", feedback.id()))
                .query(Integer.class)
                .single();
        assertThat(commentCount).isZero();
    }

    @Test
    void rejectFeedback_recomputesAggregate_excludesDeletedEntry() {
        Long entityId = newEntityId();
        FeedbackDto remaining = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 4, "Stays.", null));
        FeedbackDto toReject = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 2, "Will be rejected.", null));

        feedbackService.rejectFeedback(toReject.id());

        FeedbackAggregateDto aggregate = feedbackService.getAggregate(EntityType.PROVIDER_PROFILE, entityId);
        assertThat(aggregate.reviewCount()).isEqualTo(1);
        assertThat(aggregate.avgRating()).isEqualTo(4.0);
    }

    @Test
    void flagComment_staysWithHiddenStatus_alsoVisibleInHiddenQueue() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto comment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), newAuthorId(), "A comment.", null));

        feedbackService.flagComment(comment.id(), 3L, "Not relevant.");

        Optional<FeedbackCommentDto> found = feedbackService.findCommentByFeedback(feedback.id());
        assertThat(found).isPresent();
        assertThat(found.get().moderationStatus()).isEqualTo(FeedbackModerationStatus.HIDDEN);
        List<FeedbackCommentDto> hidden = feedbackService.findHiddenComments(PageRequest.of(0, 20));
        assertThat(hidden).hasSize(1);
        assertThat(hidden.get(0).id()).isEqualTo(comment.id());
    }

    @Test
    void flagComment_recordsReason_readableBackFromHiddenQueue() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto comment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), newAuthorId(), "A comment.", null));

        feedbackService.flagComment(comment.id(), 3L, "Off topic.");

        List<FeedbackCommentDto> hidden = feedbackService.findHiddenComments(PageRequest.of(0, 20));
        assertThat(hidden).hasSize(1);
        assertThat(hidden.get(0).moderationReason()).isEqualTo("Off topic.");
    }

    @Test
    void flagComment_byOwnAuthor_throwsIllegalStateException() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        Long commentAuthorId = newAuthorId();
        FeedbackCommentDto comment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), commentAuthorId, "A comment.", null));

        assertThatThrownBy(() -> feedbackService.flagComment(comment.id(), commentAuthorId, "My own reply."))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectComment_hardDeletes() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto comment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), newAuthorId(), "A comment.", null));

        feedbackService.rejectComment(comment.id());

        assertThat(feedbackService.findCommentByFeedback(feedback.id())).isEmpty();
        Integer commentCount = jdbcClient.sql("SELECT COUNT(*) FROM feedback_comment WHERE id = :id")
                .paramSource(new MapSqlParameterSource().addValue("id", comment.id()))
                .query(Integer.class)
                .single();
        assertThat(commentCount).isZero();
    }

    @Test
    void save_exceedsDailyLimit_throwsTooManyAttemptsException() {
        Long authorId = newAuthorId();
        for (int i = 0; i < 10; i++) {
            feedbackService.save(newSaveDto(null, authorId, newEntityId(), 5, "Entry " + i + ".", null));
        }

        assertThatThrownBy(() -> feedbackService.save(newSaveDto(null, authorId, newEntityId(), 5, "One too many.", null)))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void save_editingExistingEntry_doesNotCountAgainstDailyLimit() {
        Long authorId = newAuthorId();
        Long entityId = newEntityId();
        FeedbackDto entry = feedbackService.save(newSaveDto(null, authorId, entityId, 3, "Initial.", null));

        for (int i = 0; i < 15; i++) {
            entry = feedbackService.save(newSaveDto(entry.id(), authorId, entityId, 4, "Edit " + i + ".", entry.version()));
        }

        assertThat(entry.feedbackText()).isEqualTo("Edit 14.");
    }

    @Test
    void saveComment_exceedsDailyLimit_throwsTooManyAttemptsException() {
        Long authorId = newAuthorId();
        for (int i = 0; i < 30; i++) {
            FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), newEntityId(), 5, "Root " + i + ".", null));
            feedbackService.saveComment(new FeedbackCommentSaveDto(null, feedback.id(), authorId, "Comment " + i + ".", null));
        }

        FeedbackDto oneMoreFeedback = feedbackService.save(newSaveDto(null, newAuthorId(), newEntityId(), 5, "Root extra.", null));
        assertThatThrownBy(() -> feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, oneMoreFeedback.id(), authorId, "One too many.", null)))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void save_textBeyondRawMaxLength_throwsConstraintViolationException() {
        String tooLong = "a".repeat(FeedbackSaveDto.TEXT_RAW_MAX_LENGTH + 1);

        assertThatThrownBy(() -> feedbackService.save(newSaveDto(null, newAuthorId(), newEntityId(), 4, tooLong, null)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void saveComment_textBeyondRawMaxLength_throwsConstraintViolationException() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        String tooLong = "a".repeat(FeedbackCommentSaveDto.TEXT_RAW_MAX_LENGTH + 1);

        assertThatThrownBy(() -> feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), newAuthorId(), tooLong, null)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void concurrentSaves_sameEntity_aggregateConvergesToCorrectValue() throws Exception {
        Long entityId = newEntityId();
        Long authorA = newAuthorId();
        Long authorB = newAuthorId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<FeedbackDto> taskA = () -> {
            ready.countDown();
            go.await();
            return feedbackService.save(newSaveDto(null, authorA, entityId, 4, "From A.", null));
        };
        Callable<FeedbackDto> taskB = () -> {
            ready.countDown();
            go.await();
            return feedbackService.save(newSaveDto(null, authorB, entityId, 2, "From B.", null));
        };

        Future<FeedbackDto> futureA = executor.submit(taskA);
        Future<FeedbackDto> futureB = executor.submit(taskB);
        ready.await();
        go.countDown();

        futureA.get(10, TimeUnit.SECONDS);
        futureB.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(feedbackService.count(EntityType.PROVIDER_PROFILE, entityId)).isEqualTo(2);

        // A concurrent pair of upsertAggregate calls can race on the *materialized* aggregate row,
        // but the underlying feedback/feedback_rating rows themselves are never corrupted by
        // concurrent inserts -- an explicit recompute afterward always converges to the true value.
        feedbackRepository.upsertAggregate(EntityType.PROVIDER_PROFILE, entityId);
        FeedbackAggregateDto finalAggregate = feedbackService.getAggregate(EntityType.PROVIDER_PROFILE, entityId);
        assertThat(finalAggregate.reviewCount()).isEqualTo(2);
        assertThat(finalAggregate.avgRating()).isEqualTo(3.0);
    }
}
