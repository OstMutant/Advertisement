package org.ost.integrationtests.level1.feedback;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.feedback.config.FeedbackAutoConfiguration;
import org.ost.feedback.services.FeedbackService;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.core.TooManyAttemptsException;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentReactionSaveDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.platform.feedback.model.FeedbackReactionType;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link FeedbackService#save}/{@code saveComment}/{@code saveReaction}/{@code deleteComment} sanitization, edit-window enforcement, and aggregate recompute. */
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
    void findCommentsByFeedback_returnsTreeWithReactionsFoldedIn() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));

        FeedbackCommentDto reply = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), "A reply.", null));
        feedbackService.saveComment(new FeedbackCommentSaveDto(null, feedback.id(), reply.id(), newAuthorId(), "A nested reply.", null));

        Long reactorId = newAuthorId();
        feedbackService.saveReaction(new FeedbackCommentReactionSaveDto(reply.id(), reactorId, FeedbackReactionType.UP));

        List<FeedbackCommentDto> tree = feedbackService.findCommentsByFeedback(feedback.id(), reactorId);

        assertThat(tree).hasSize(2);
        FeedbackCommentDto topLevel = tree.stream().filter(c -> c.parentCommentId() == null).findFirst().orElseThrow();
        assertThat(topLevel.commentText()).isEqualTo("A reply.");
        assertThat(topLevel.reactorIdsByType().get("UP")).containsExactly(reactorId);
        assertThat(topLevel.myReaction()).isEqualTo("UP");

        FeedbackCommentDto nested = tree.stream().filter(c -> c.parentCommentId() != null).findFirst().orElseThrow();
        assertThat(nested.commentText()).isEqualTo("A nested reply.");
        assertThat(nested.myReaction()).isNull();
    }

    @Test
    void saveComment_editWithinWindow_updatesText() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto comment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), "Original text.", null));

        FeedbackCommentDto updated = feedbackService.saveComment(
                new FeedbackCommentSaveDto(comment.id(), feedback.id(), null, newAuthorId(), "Updated text.", comment.version()));

        assertThat(updated.id()).isEqualTo(comment.id());
        assertThat(updated.commentText()).isEqualTo("Updated text.");
    }

    @Test
    void saveComment_pastEditWindow_throwsIllegalStateException() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto comment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), "Original text.", null));
        backdateCommentContentCreatedAt(comment.id(), Instant.now().minus(49, ChronoUnit.HOURS));

        assertThatThrownBy(() -> feedbackService.saveComment(
                new FeedbackCommentSaveDto(comment.id(), feedback.id(), null, newAuthorId(), "Edit attempt.", comment.version())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void saveReaction_voteThenSameType_unvotes() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto comment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), "A comment.", null));

        Long reactorId = newAuthorId();
        feedbackService.saveReaction(new FeedbackCommentReactionSaveDto(comment.id(), reactorId, FeedbackReactionType.UP));
        List<FeedbackCommentDto> afterVote = feedbackService.findCommentsByFeedback(feedback.id(), reactorId);
        assertThat(afterVote.get(0).reactorIdsByType().get("UP")).containsExactly(reactorId);

        feedbackService.saveReaction(new FeedbackCommentReactionSaveDto(comment.id(), reactorId, FeedbackReactionType.DOWN));
        List<FeedbackCommentDto> afterChange = feedbackService.findCommentsByFeedback(feedback.id(), reactorId);
        assertThat(afterChange.get(0).reactorIdsByType().getOrDefault("UP", List.of())).isEmpty();
        assertThat(afterChange.get(0).reactorIdsByType().get("DOWN")).containsExactly(reactorId);

        feedbackService.saveReaction(new FeedbackCommentReactionSaveDto(comment.id(), reactorId, FeedbackReactionType.DOWN));
        List<FeedbackCommentDto> afterUnvote = feedbackService.findCommentsByFeedback(feedback.id(), reactorId);
        assertThat(afterUnvote.get(0).reactorIdsByType().getOrDefault("DOWN", List.of())).isEmpty();
        assertThat(afterUnvote.get(0).myReaction()).isNull();
    }

    @Test
    void deleteComment_leafComment_removesItEntirely() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto leaf = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), "A leaf comment.", null));

        feedbackService.deleteComment(leaf.id());

        assertThat(feedbackService.findCommentsByFeedback(feedback.id(), null)).isEmpty();
    }

    @Test
    void deleteComment_commentWithReplies_tombstonesButKeepsChildren() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto parent = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), "Parent comment.", null));
        FeedbackCommentDto child = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), parent.id(), newAuthorId(), "Child reply.", null));

        feedbackService.deleteComment(parent.id());

        List<FeedbackCommentDto> tree = feedbackService.findCommentsByFeedback(feedback.id(), null);
        assertThat(tree).hasSize(2);
        FeedbackCommentDto tombstoned = tree.stream().filter(c -> c.id().equals(parent.id())).findFirst().orElseThrow();
        assertThat(tombstoned.commentText()).isNull();
        FeedbackCommentDto stillThere = tree.stream().filter(c -> c.id().equals(child.id())).findFirst().orElseThrow();
        assertThat(stillThere.commentText()).isEqualTo("Child reply.");
    }

    @Test
    void deleteComment_lastChildOfTombstonedParent_prunesParentToo() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto parent = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), "Parent comment.", null));
        FeedbackCommentDto child = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), parent.id(), newAuthorId(), "Child reply.", null));

        feedbackService.deleteComment(parent.id());
        List<FeedbackCommentDto> afterParentDelete = feedbackService.findCommentsByFeedback(feedback.id(), null);
        assertThat(afterParentDelete).hasSize(2);
        assertThat(afterParentDelete.stream().filter(c -> c.id().equals(parent.id())).findFirst().orElseThrow().commentText()).isNull();

        feedbackService.deleteComment(child.id());

        assertThat(feedbackService.findCommentsByFeedback(feedback.id(), null)).isEmpty();
    }

    @Test
    void deleteComment_pastEditWindow_throwsIllegalStateException() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto comment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), "A comment.", null));
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

        feedbackService.flagFeedback(feedback.id(), 2L);

        List<FeedbackDto> listed = feedbackService.findForEntity(EntityType.PROVIDER_PROFILE, entityId, 0, 10);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).moderationStatus()).isEqualTo(FeedbackModerationStatus.HIDDEN);
        List<FeedbackDto> hidden = feedbackService.findHiddenFeedback(PageRequest.of(0, 20));
        assertThat(hidden).hasSize(1);
        assertThat(hidden.get(0).id()).isEqualTo(feedback.id());
    }

    @Test
    void flagFeedback_byOwnAuthor_throwsIllegalStateException() {
        Long entityId = newEntityId();
        Long authorId = newAuthorId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, authorId, entityId, 5, "Root feedback.", null));

        assertThatThrownBy(() -> feedbackService.flagFeedback(feedback.id(), authorId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approveFeedback_reversesHiddenStatus_reappearsInPublicListing() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        feedbackService.flagFeedback(feedback.id(), 2L);

        feedbackService.approveFeedback(feedback.id());

        List<FeedbackDto> visible = feedbackService.findForEntity(EntityType.PROVIDER_PROFILE, entityId, 0, 10);
        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).id()).isEqualTo(feedback.id());
        assertThat(feedbackService.findHiddenFeedback(PageRequest.of(0, 20))).isEmpty();
    }

    @Test
    void rejectFeedback_hardDeletesEntireCommentTree() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto topLevelComment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), "Top-level comment.", null));
        feedbackService.saveComment(new FeedbackCommentSaveDto(null, feedback.id(), topLevelComment.id(), newAuthorId(), "Nested reply.", null));

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
    void rejectFeedback_cascadesEvenWhenTreeContainsHiddenComment() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto topLevelComment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), "Top-level comment.", null));
        feedbackService.flagComment(topLevelComment.id(), 3L);

        feedbackService.rejectFeedback(feedback.id());

        assertThat(feedbackService.findForEntity(EntityType.PROVIDER_PROFILE, entityId, 0, 10)).isEmpty();
        Integer commentCount = jdbcClient.sql("SELECT COUNT(*) FROM feedback_comment WHERE feedback_id = :feedbackId")
                .paramSource(new MapSqlParameterSource().addValue("feedbackId", feedback.id()))
                .query(Integer.class)
                .single();
        assertThat(commentCount).isZero();
    }

    @Test
    void flagComment_staysInTreeWithHiddenStatus_alsoVisibleInHiddenQueue() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto comment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), "A comment.", null));

        feedbackService.flagComment(comment.id(), 3L);

        List<FeedbackCommentDto> tree = feedbackService.findCommentsByFeedback(feedback.id(), null);
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).moderationStatus()).isEqualTo(FeedbackModerationStatus.HIDDEN);
        List<FeedbackCommentDto> hidden = feedbackService.findHiddenComments(PageRequest.of(0, 20));
        assertThat(hidden).hasSize(1);
        assertThat(hidden.get(0).id()).isEqualTo(comment.id());
    }

    @Test
    void flagComment_byOwnAuthor_throwsIllegalStateException() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        Long commentAuthorId = newAuthorId();
        FeedbackCommentDto comment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, commentAuthorId, "A comment.", null));

        assertThatThrownBy(() -> feedbackService.flagComment(comment.id(), commentAuthorId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectComment_hardDeletesRegardlessOfChildren() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto topLevelComment = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), "Parent comment.", null));
        feedbackService.saveComment(new FeedbackCommentSaveDto(null, feedback.id(), topLevelComment.id(), newAuthorId(), "Child reply.", null));

        feedbackService.rejectComment(topLevelComment.id());

        List<FeedbackCommentDto> tree = feedbackService.findCommentsByFeedback(feedback.id(), null);
        assertThat(tree).isEmpty();
        Integer commentCount = jdbcClient.sql("SELECT COUNT(*) FROM feedback_comment WHERE id = :id")
                .paramSource(new MapSqlParameterSource().addValue("id", topLevelComment.id()))
                .query(Integer.class)
                .single();
        assertThat(commentCount).isZero();
    }

    @Test
    void saveComment_exceedsMaxNestingDepth_throwsIllegalStateException() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto levelOne = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), "Level 1.", null));
        FeedbackCommentDto levelTwo = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), levelOne.id(), newAuthorId(), "Level 2.", null));
        FeedbackCommentDto levelThree = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), levelTwo.id(), newAuthorId(), "Level 3.", null));

        assertThatThrownBy(() -> feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), levelThree.id(), newAuthorId(), "Level 4 attempt.", null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void saveComment_replyAtMaxDepthMinusOne_succeeds() {
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        FeedbackCommentDto levelOne = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), "Level 1.", null));
        FeedbackCommentDto levelTwo = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), levelOne.id(), newAuthorId(), "Level 2.", null));

        FeedbackCommentDto levelThree = feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), levelTwo.id(), newAuthorId(), "Level 3.", null));

        assertThat(levelThree.parentCommentId()).isEqualTo(levelTwo.id());
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
        Long entityId = newEntityId();
        FeedbackDto feedback = feedbackService.save(newSaveDto(null, newAuthorId(), entityId, 5, "Root feedback.", null));
        Long authorId = newAuthorId();
        for (int i = 0; i < 30; i++) {
            feedbackService.saveComment(new FeedbackCommentSaveDto(null, feedback.id(), null, authorId, "Comment " + i + ".", null));
        }

        assertThatThrownBy(() -> feedbackService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), null, authorId, "One too many.", null)))
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
                new FeedbackCommentSaveDto(null, feedback.id(), null, newAuthorId(), tooLong, null)))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
