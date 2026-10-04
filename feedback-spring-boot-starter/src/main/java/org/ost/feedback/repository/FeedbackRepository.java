package org.ost.feedback.repository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.feedback.entity.Feedback;
import org.ost.feedback.entity.FeedbackAggregate;
import org.ost.feedback.entity.FeedbackComment;
import org.ost.feedback.entity.FeedbackContent;
import org.ost.platform.core.StaleWriteException;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.query.sort.PaginationSqlBuilder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Bespoke {@code JdbcClient} queries across all 5 feedback tables; trivial CRUD delegates to the {@code *CrudRepository}s. */
@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class FeedbackRepository {

    /** Read record joining {@code feedback}+{@code feedback_content}+{@code feedback_rating} -- same fields as {@code FeedbackDto}. */
    public record FeedbackView(
            Long id,
            EntityType entityType,
            Long entityId,
            Long authorId,
            Long contentId,
            int rating,
            String feedbackText,
            FeedbackModerationStatus moderationStatus,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    /** Read record joining {@code feedback_comment}+{@code feedback_content} only -- reactions are folded in separately by the service layer. */
    public record FeedbackCommentView(
            Long id,
            Long feedbackId,
            Long parentCommentId,
            Long authorId,
            Long contentId,
            String commentText,
            FeedbackModerationStatus moderationStatus,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    public record FeedbackCommentReactionView(Long feedbackCommentId, Long userId, String reactionType) {
    }

    private static final RowMapper<FeedbackView> VIEW_ROW_MAPPER = (rs, _) -> new FeedbackView(
            rs.getObject("id", Long.class),
            EntityType.valueOf(rs.getString("entity_type")),
            rs.getObject("entity_id", Long.class),
            rs.getObject("author_id", Long.class),
            rs.getObject("content_id", Long.class),
            rs.getInt("rating"),
            rs.getString("content_text"),
            FeedbackModerationStatus.valueOf(rs.getString("moderation_status")),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null,
            rs.getObject("version", Long.class));

    private static final RowMapper<FeedbackCommentView> COMMENT_VIEW_ROW_MAPPER = (rs, _) -> new FeedbackCommentView(
            rs.getObject("id", Long.class),
            rs.getObject("feedback_id", Long.class),
            rs.getObject("parent_comment_id", Long.class),
            rs.getObject("author_id", Long.class),
            rs.getObject("content_id", Long.class),
            rs.getString("content_text"),
            FeedbackModerationStatus.valueOf(rs.getString("moderation_status")),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null,
            rs.getObject("version", Long.class));

    private static final RowMapper<FeedbackCommentReactionView> REACTION_VIEW_ROW_MAPPER = (rs, _) -> new FeedbackCommentReactionView(
            rs.getObject("feedback_comment_id", Long.class),
            rs.getObject("user_id", Long.class),
            rs.getString("reaction_type"));

    private static final RowMapper<FeedbackAggregate> AGGREGATE_ROW_MAPPER = (rs, _) -> FeedbackAggregate.builder()
            .id(rs.getObject("id", Long.class))
            .entityType(EntityType.valueOf(rs.getString("entity_type")))
            .entityId(rs.getObject("entity_id", Long.class))
            .avgRating(rs.getDouble("avg_rating"))
            .reviewCount(rs.getInt("review_count"))
            .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null)
            .build();

    private static final String VIEW_SELECT = """
            SELECT f.id, f.entity_type, f.entity_id, f.author_id, f.content_id, r.rating,
                   c.content_text, c.moderation_status, c.created_at, c.updated_at, c.version
            FROM feedback f
            JOIN feedback_content c ON c.id = f.content_id
            JOIN feedback_rating r ON r.feedback_id = f.id
            """;

    private final JdbcClient jdbcClient;
    private final FeedbackCrudRepository feedbackCrud;
    private final FeedbackContentCrudRepository feedbackContentCrud;
    private final FeedbackCommentCrudRepository feedbackCommentCrud;

    public Optional<FeedbackView> findByAuthorAndEntity(@NonNull Long authorId, @NonNull EntityType entityType, @NonNull Long entityId) {
        return jdbcClient.sql(VIEW_SELECT + " WHERE f.author_id = :authorId AND f.entity_type = :entityType AND f.entity_id = :entityId")
                .paramSource(new MapSqlParameterSource()
                        .addValue("authorId", authorId)
                        .addValue("entityType", entityType.name())
                        .addValue("entityId", entityId))
                .query(VIEW_ROW_MAPPER)
                .optional();
    }

    public List<FeedbackView> findByEntity(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("entityType", entityType.name())
                .addValue("entityId", entityId);
        String pageClause = PaginationSqlBuilder.pageLimit(params, pageable);
        return jdbcClient.sql(VIEW_SELECT + " WHERE f.entity_type = :entityType AND f.entity_id = :entityId ORDER BY c.created_at DESC" + pageClause)
                .paramSource(params)
                .query(VIEW_ROW_MAPPER)
                .list();
    }

    public Optional<FeedbackView> findViewById(@NonNull Long id) {
        return jdbcClient.sql(VIEW_SELECT + " WHERE f.id = :id")
                .paramSource(new MapSqlParameterSource().addValue("id", id))
                .query(VIEW_ROW_MAPPER)
                .optional();
    }

    public int count(@NonNull EntityType entityType, @NonNull Long entityId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM feedback WHERE entity_type = :entityType AND entity_id = :entityId")
                .paramSource(new MapSqlParameterSource()
                        .addValue("entityType", entityType.name())
                        .addValue("entityId", entityId))
                .query(Integer.class)
                .single();
    }

    public FeedbackContent saveContent(@NonNull FeedbackContent content) {
        try {
            return feedbackContentCrud.save(content);
        } catch (OptimisticLockingFailureException e) {
            throw new StaleWriteException("FeedbackContent " + content.getId() + " was modified by another session", e);
        }
    }

    public Feedback saveLink(@NonNull Feedback feedback) {
        return feedbackCrud.save(feedback);
    }

    /** Upserts the 1:1 {@code feedback_rating} row for one feedback entry. */
    public void upsertRating(@NonNull Long feedbackId, int rating) {
        jdbcClient.sql("""
                        INSERT INTO feedback_rating (feedback_id, rating, created_at, updated_at)
                        VALUES (:feedbackId, :rating, NOW(), NOW())
                        ON CONFLICT (feedback_id) DO UPDATE
                        SET rating = EXCLUDED.rating, updated_at = EXCLUDED.updated_at
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("feedbackId", feedbackId)
                        .addValue("rating", rating))
                .update();
    }

    /** Recomputes {@code avg_rating}/{@code review_count} from {@code feedback}/{@code feedback_rating} and upserts the one {@code feedback_aggregate} row -- caller runs this in the same transaction as the triggering feedback write. */
    public void upsertAggregate(@NonNull EntityType entityType, @NonNull Long entityId) {
        jdbcClient.sql("""
                        INSERT INTO feedback_aggregate (entity_type, entity_id, avg_rating, review_count, updated_at)
                        SELECT :entityType, :entityId, COALESCE(AVG(r.rating), 0), COUNT(*), NOW()
                        FROM feedback f
                        JOIN feedback_rating r ON r.feedback_id = f.id
                        WHERE f.entity_type = :entityType AND f.entity_id = :entityId
                        ON CONFLICT (entity_type, entity_id) DO UPDATE
                        SET avg_rating = EXCLUDED.avg_rating, review_count = EXCLUDED.review_count, updated_at = EXCLUDED.updated_at
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("entityType", entityType.name())
                        .addValue("entityId", entityId))
                .update();
    }

    public Optional<FeedbackAggregate> getAggregate(@NonNull EntityType entityType, @NonNull Long entityId) {
        return jdbcClient.sql("""
                        SELECT id, entity_type, entity_id, avg_rating, review_count, updated_at
                        FROM feedback_aggregate
                        WHERE entity_type = :entityType AND entity_id = :entityId
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("entityType", entityType.name())
                        .addValue("entityId", entityId))
                .query(AGGREGATE_ROW_MAPPER)
                .optional();
    }

    /** Full comment tree for one feedback entry, depth-first sibling-ordered via a recursive path array. */
    public List<FeedbackCommentView> findCommentsByFeedback(@NonNull Long feedbackId) {
        return jdbcClient.sql("""
                        WITH RECURSIVE tree AS (
                            SELECT id, feedback_id, parent_comment_id, author_id, content_id, ARRAY[id] AS path
                            FROM feedback_comment
                            WHERE feedback_id = :feedbackId AND parent_comment_id IS NULL
                            UNION ALL
                            SELECT fc.id, fc.feedback_id, fc.parent_comment_id, fc.author_id, fc.content_id, tree.path || fc.id
                            FROM feedback_comment fc
                            JOIN tree ON fc.parent_comment_id = tree.id
                        )
                        SELECT tree.id, tree.feedback_id, tree.parent_comment_id, tree.author_id, tree.content_id,
                               c.content_text, c.moderation_status, c.created_at, c.updated_at, c.version
                        FROM tree
                        JOIN feedback_content c ON c.id = tree.content_id
                        ORDER BY tree.path
                        """)
                .paramSource(new MapSqlParameterSource().addValue("feedbackId", feedbackId))
                .query(COMMENT_VIEW_ROW_MAPPER)
                .list();
    }

    public List<FeedbackCommentReactionView> findReactionsByComments(@NonNull List<Long> commentIds) {
        return jdbcClient.sql("""
                        SELECT feedback_comment_id, user_id, reaction_type
                        FROM feedback_comment_reaction
                        WHERE feedback_comment_id = ANY(:commentIds)
                        """)
                .paramSource(new MapSqlParameterSource().addValue("commentIds", commentIds.toArray(new Long[0])))
                .query(REACTION_VIEW_ROW_MAPPER)
                .list();
    }

    public Optional<FeedbackCommentView> findCommentViewById(@NonNull Long id) {
        return jdbcClient.sql("""
                        SELECT fc.id, fc.feedback_id, fc.parent_comment_id, fc.author_id, fc.content_id,
                               c.content_text, c.moderation_status, c.created_at, c.updated_at, c.version
                        FROM feedback_comment fc
                        JOIN feedback_content c ON c.id = fc.content_id
                        WHERE fc.id = :id
                        """)
                .paramSource(new MapSqlParameterSource().addValue("id", id))
                .query(COMMENT_VIEW_ROW_MAPPER)
                .optional();
    }

    public FeedbackComment saveCommentLink(@NonNull FeedbackComment comment) {
        return feedbackCommentCrud.save(comment);
    }

    public Optional<String> findReactionType(@NonNull Long feedbackCommentId, @NonNull Long userId) {
        return jdbcClient.sql("""
                        SELECT reaction_type
                        FROM feedback_comment_reaction
                        WHERE feedback_comment_id = :feedbackCommentId AND user_id = :userId
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("feedbackCommentId", feedbackCommentId)
                        .addValue("userId", userId))
                .query(String.class)
                .optional();
    }

    public void upsertReaction(@NonNull Long feedbackCommentId, @NonNull Long userId, @NonNull String reactionType) {
        jdbcClient.sql("""
                        INSERT INTO feedback_comment_reaction (feedback_comment_id, user_id, reaction_type, created_at)
                        VALUES (:feedbackCommentId, :userId, :reactionType, NOW())
                        ON CONFLICT (feedback_comment_id, user_id) DO UPDATE
                        SET reaction_type = EXCLUDED.reaction_type, created_at = EXCLUDED.created_at
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("feedbackCommentId", feedbackCommentId)
                        .addValue("userId", userId)
                        .addValue("reactionType", reactionType))
                .update();
    }

    public void deleteReaction(@NonNull Long feedbackCommentId, @NonNull Long userId) {
        jdbcClient.sql("DELETE FROM feedback_comment_reaction WHERE feedback_comment_id = :feedbackCommentId AND user_id = :userId")
                .paramSource(new MapSqlParameterSource()
                        .addValue("feedbackCommentId", feedbackCommentId)
                        .addValue("userId", userId))
                .update();
    }

    public int countChildren(@NonNull Long commentId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM feedback_comment WHERE parent_comment_id = :commentId")
                .paramSource(new MapSqlParameterSource().addValue("commentId", commentId))
                .query(Integer.class)
                .single();
    }

    /** Deletes the comment, its own content row, and any reactions on it -- no FK cascade configured, so all three run explicitly. */
    public void deleteCommentHard(@NonNull Long commentId, @NonNull Long contentId) {
        jdbcClient.sql("DELETE FROM feedback_comment_reaction WHERE feedback_comment_id = :commentId")
                .paramSource(new MapSqlParameterSource().addValue("commentId", commentId))
                .update();
        jdbcClient.sql("DELETE FROM feedback_comment WHERE id = :commentId")
                .paramSource(new MapSqlParameterSource().addValue("commentId", commentId))
                .update();
        jdbcClient.sql("DELETE FROM feedback_content WHERE id = :contentId")
                .paramSource(new MapSqlParameterSource().addValue("contentId", contentId))
                .update();
    }

    /** Sets {@code content_text} to NULL -- the tombstone signal for a deleted comment that still has replies. */
    public void tombstoneComment(@NonNull Long contentId) {
        jdbcClient.sql("UPDATE feedback_content SET content_text = NULL WHERE id = :contentId")
                .paramSource(new MapSqlParameterSource().addValue("contentId", contentId))
                .update();
    }
}
