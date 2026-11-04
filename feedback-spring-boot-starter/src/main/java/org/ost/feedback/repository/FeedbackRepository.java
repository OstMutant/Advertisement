package org.ost.feedback.repository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.feedback.entity.Feedback;
import org.ost.feedback.entity.FeedbackAggregate;
import org.ost.feedback.entity.FeedbackComment;
import org.ost.feedback.entity.FeedbackContent;
import org.ost.platform.core.StaleWriteException;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.ModerationQueueFilterDto;
import org.ost.platform.feedback.dto.ModerationQueueItemDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.query.filter.SqlBoundFilter;
import org.ost.query.filter.SqlCondition;
import org.ost.query.filter.SqlFilterBuilder;
import org.ost.query.filter.SqlFilterMapping;
import org.ost.query.sort.OrderByBuilder;
import org.ost.query.sort.PaginationSqlBuilder;
import org.ost.query.sort.SortField;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
            String moderationReason,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    /** Read record joining {@code feedback_comment}+{@code feedback_content} -- the single reply, if any, to a feedback entry. */
    public record FeedbackCommentView(
            Long id,
            Long feedbackId,
            Long authorId,
            Long contentId,
            String commentText,
            FeedbackModerationStatus moderationStatus,
            String moderationReason,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    /** One row of the merged moderation queue's own SQL projection -- 'FEEDBACK'/'COMMENT' discriminated, author name NOT resolved here (that's display composition, done by the caller). */
    public record ModerationQueueRow(
            String itemType,
            Long id,
            Long entityId,
            String contentText,
            Integer rating,
            String moderationReason,
            Long authorId,
            Instant createdAt,
            FeedbackModerationStatus moderationStatus
    ) {
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
            rs.getString("moderation_reason"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null,
            rs.getObject("version", Long.class));

    private static final RowMapper<FeedbackCommentView> COMMENT_VIEW_ROW_MAPPER = (rs, _) -> new FeedbackCommentView(
            rs.getObject("id", Long.class),
            rs.getObject("feedback_id", Long.class),
            rs.getObject("author_id", Long.class),
            rs.getObject("content_id", Long.class),
            rs.getString("content_text"),
            FeedbackModerationStatus.valueOf(rs.getString("moderation_status")),
            rs.getString("moderation_reason"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null,
            rs.getObject("version", Long.class));

    private static final RowMapper<ModerationQueueRow> MODERATION_QUEUE_ROW_MAPPER = (rs, _) -> new ModerationQueueRow(
            rs.getString("item_type"),
            rs.getObject("id", Long.class),
            rs.getObject("entity_id", Long.class),
            rs.getString("content_text"),
            rs.getObject("rating", Integer.class),
            rs.getString("moderation_reason"),
            rs.getObject("author_id", Long.class),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
            FeedbackModerationStatus.valueOf(rs.getString("moderation_status")));

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
                   c.content_text, c.moderation_status, c.moderation_reason, c.created_at, c.updated_at, c.version
            FROM feedback f
            JOIN feedback_content c ON c.id = f.content_id
            JOIN feedback_rating r ON r.feedback_id = f.id
            """;

    private static final String COMMENT_VIEW_SELECT = """
            SELECT fc.id, fc.feedback_id, fc.author_id, fc.content_id,
                   c.content_text, c.moderation_status, c.moderation_reason, c.created_at, c.updated_at, c.version
            FROM feedback_comment fc
            JOIN feedback_content c ON c.id = fc.content_id
            """;

    // 'FEEDBACK' branch joins feedback_rating for its own rating; 'COMMENT' branch joins feedback
    // (aliased ff) ONLY to resolve entity_id here in SQL -- this JOIN is the actual N+1 fix (previously
    // one findFeedbackById() call per comment, from the orchestrator layer).
    private static final String MODERATION_QUEUE_CTE = """
            WITH moderation_queue AS (
                SELECT 'FEEDBACK' AS item_type, f.id, f.entity_id, c.content_text, r.rating,
                       c.moderation_reason, f.author_id, c.created_at, c.moderation_status
                FROM feedback f
                JOIN feedback_content c ON c.id = f.content_id
                JOIN feedback_rating r ON r.feedback_id = f.id
                UNION ALL
                SELECT 'COMMENT' AS item_type, fc.id, ff.entity_id, c.content_text, NULL::smallint AS rating,
                       c.moderation_reason, fc.author_id, c.created_at, c.moderation_status
                FROM feedback_comment fc
                JOIN feedback_content c ON c.id = fc.content_id
                JOIN feedback ff ON ff.id = fc.feedback_id
            )
            """;

    private static final SqlFilterBuilder<ModerationQueueFilterDto> MODERATION_QUEUE_FILTER = new SqlFilterBuilder<>(List.of(
            SqlBoundFilter.of(ModerationQueueFilterDto.Fields.types, "item_type",
                    (m, v) -> SqlCondition.inSet(m, v.getTypes())),
            SqlBoundFilter.of(ModerationQueueFilterDto.Fields.reasonText, "moderation_reason",
                    (m, v) -> SqlCondition.like(m, v.getReasonText())),
            SqlBoundFilter.of(ModerationQueueFilterDto.Fields.createdAtStart, "created_at",
                    (m, v) -> SqlCondition.after(m, v.getCreatedAtStart())),
            SqlBoundFilter.of(ModerationQueueFilterDto.Fields.createdAtEnd, "created_at",
                    (m, v) -> SqlCondition.before(m, v.getCreatedAtEnd()))
    ));

    private static final SqlFilterMapping AUTHOR_IDS_MAPPING = new SqlFilterMapping() {
        @Override public String filterProperty() { return "authorIds"; }
        @Override public String sqlExpression() { return "author_id"; }
    };

    private static final SqlFilterMapping STATUSES_MAPPING = new SqlFilterMapping() {
        @Override public String filterProperty() { return "statuses"; }
        @Override public String sqlExpression() { return "moderation_status"; }
    };

    // Tiebreaker must be (item_type, id) together, never id alone -- id is NOT globally unique
    // across this UNION ALL (feedback and feedback_comment each have their own independent
    // sequence), unlike every other *SortMeta in this app which sorts a single real table.
    private static final List<SortField> MODERATION_QUEUE_SORT_FIELDS = List.of(
            SortField.of(ModerationQueueItemDto.Fields.createdAt, "created_at", Sort.Direction.DESC,
                    SortField.of("itemType", "item_type"), SortField.of("rowId", "id")),
            SortField.of(ModerationQueueItemDto.Fields.type, "item_type",
                    SortField.of(ModerationQueueItemDto.Fields.createdAt, "created_at"), SortField.of("rowId", "id"))
    );

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
                        JOIN feedback_content fc ON fc.id = f.content_id
                        WHERE f.entity_type = :entityType AND f.entity_id = :entityId
                          AND fc.moderation_status = 'NEW'
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

    /** The single reply to one feedback entry, if any. */
    public Optional<FeedbackCommentView> findCommentByFeedback(@NonNull Long feedbackId) {
        return jdbcClient.sql(COMMENT_VIEW_SELECT + " WHERE fc.feedback_id = :feedbackId")
                .paramSource(new MapSqlParameterSource().addValue("feedbackId", feedbackId))
                .query(COMMENT_VIEW_ROW_MAPPER)
                .optional();
    }

    public Optional<FeedbackCommentView> findCommentViewById(@NonNull Long id) {
        return jdbcClient.sql(COMMENT_VIEW_SELECT + " WHERE fc.id = :id")
                .paramSource(new MapSqlParameterSource().addValue("id", id))
                .query(COMMENT_VIEW_ROW_MAPPER)
                .optional();
    }

    public FeedbackComment saveCommentLink(@NonNull FeedbackComment comment) {
        return feedbackCommentCrud.save(comment);
    }

    /** Deletes the reply and its own content row -- no FK cascade configured, so both run explicitly. */
    public void deleteCommentHard(@NonNull Long commentId, @NonNull Long contentId) {
        jdbcClient.sql("DELETE FROM feedback_comment WHERE id = :commentId")
                .paramSource(new MapSqlParameterSource().addValue("commentId", commentId))
                .update();
        jdbcClient.sql("DELETE FROM feedback_content WHERE id = :contentId")
                .paramSource(new MapSqlParameterSource().addValue("contentId", contentId))
                .update();
    }

    /** Moves the shared content row from one of the {@code from} statuses to {@code to}; the guard is in the WHERE, zero means the entry was not in an allowed source status. */
    public int transitionModerationStatus(@NonNull Long contentId, @NonNull Set<FeedbackModerationStatus> from, @NonNull FeedbackModerationStatus to) {
        String[] fromNames = from.stream().map(Enum::name).toArray(String[]::new);
        return jdbcClient.sql("UPDATE feedback_content SET moderation_status = :to WHERE id = :contentId AND moderation_status = ANY(:from)")
                .paramSource(new MapSqlParameterSource()
                        .addValue("contentId", contentId)
                        .addValue("to", to.name())
                        .addValue("from", fromNames))
                .update();
    }

    /** Hides a content row still in NEW and records why -- zero rows means the entry was already moderated. */
    public int flagContent(@NonNull Long contentId, @NonNull String reason) {
        return jdbcClient.sql("UPDATE feedback_content SET moderation_status = 'HIDDEN', moderation_reason = :reason WHERE id = :contentId AND moderation_status = 'NEW'")
                .paramSource(new MapSqlParameterSource()
                        .addValue("contentId", contentId)
                        .addValue("reason", reason))
                .update();
    }

    private List<FeedbackView> findFeedbackByStatus(FeedbackModerationStatus status, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String pageClause = PaginationSqlBuilder.pageLimit(params, pageable);
        params.addValue("status", status.name());
        return jdbcClient.sql(VIEW_SELECT + " WHERE c.moderation_status = :status ORDER BY c.created_at ASC" + pageClause)
                .paramSource(params)
                .query(VIEW_ROW_MAPPER)
                .list();
    }

    private List<FeedbackCommentView> findCommentsByStatus(FeedbackModerationStatus status, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String pageClause = PaginationSqlBuilder.pageLimit(params, pageable);
        params.addValue("status", status.name());
        return jdbcClient.sql(COMMENT_VIEW_SELECT + " WHERE c.moderation_status = :status ORDER BY c.created_at ASC" + pageClause)
                .paramSource(params)
                .query(COMMENT_VIEW_ROW_MAPPER)
                .list();
    }

    /** Hidden feedback entries awaiting moderation review, oldest first. */
    public List<FeedbackView> findHiddenFeedback(@NonNull Pageable pageable) {
        return findFeedbackByStatus(FeedbackModerationStatus.HIDDEN, pageable);
    }

    /** Hidden comments awaiting moderation review, oldest first, across all feedback entries. */
    public List<FeedbackCommentView> findHiddenComments(@NonNull Pageable pageable) {
        return findCommentsByStatus(FeedbackModerationStatus.HIDDEN, pageable);
    }

    /** Feedback entries already confirmed as a genuine violation, oldest first. */
    public List<FeedbackView> findConfirmedViolationFeedback(@NonNull Pageable pageable) {
        return findFeedbackByStatus(FeedbackModerationStatus.CONFIRMED_HIDDEN, pageable);
    }

    /** Replies already confirmed as a genuine violation, oldest first, across all feedback entries. */
    public List<FeedbackCommentView> findConfirmedViolationComments(@NonNull Pageable pageable) {
        return findCommentsByStatus(FeedbackModerationStatus.CONFIRMED_HIDDEN, pageable);
    }

    /** Cascades a feedback entry's own hard delete across its reply, rating, and content row -- no FK cascade configured. */
    public void deleteFeedbackHard(@NonNull Long feedbackId, @NonNull Long contentId) {
        findCommentByFeedback(feedbackId).ifPresent(comment -> deleteCommentHard(comment.id(), comment.contentId()));
        jdbcClient.sql("DELETE FROM feedback_rating WHERE feedback_id = :feedbackId")
                .paramSource(new MapSqlParameterSource().addValue("feedbackId", feedbackId))
                .update();
        jdbcClient.sql("DELETE FROM feedback WHERE id = :feedbackId")
                .paramSource(new MapSqlParameterSource().addValue("feedbackId", feedbackId))
                .update();
        jdbcClient.sql("DELETE FROM feedback_content WHERE id = :contentId")
                .paramSource(new MapSqlParameterSource().addValue("contentId", contentId))
                .update();
    }

    private String buildModerationQueueWhere(MapSqlParameterSource params, ModerationQueueFilterDto filter, Set<Long> authorIds) {
        StringBuilder where = new StringBuilder(" WHERE ");
        if (filter.getStatuses() == null || filter.getStatuses().isEmpty()) {
            params.addValue("hiddenStatus", FeedbackModerationStatus.HIDDEN.name());
            where.append("moderation_status = :hiddenStatus");
        } else {
            SqlCondition<Collection<String>> statusCondition = SqlCondition.inSet(STATUSES_MAPPING, filter.getStatuses());
            params.addValue(statusCondition.filterProperty(), statusCondition.value());
            where.append(statusCondition.getConditionClause());
        }
        where.append(MODERATION_QUEUE_FILTER.build(params, filter, " AND "));
        SqlCondition<Long[]> authorCondition = SqlCondition.anyOf(AUTHOR_IDS_MAPPING, authorIds);
        if (authorCondition != null) {
            params.addValue(authorCondition.filterProperty(), authorCondition.value());
            where.append(" AND ").append(authorCondition.getConditionClause());
        }
        return where.toString();
    }

    public List<ModerationQueueRow> findModerationQueuePage(@NonNull ModerationQueueFilterDto filter, @NonNull Sort sort, @NonNull Pageable pageable, Set<Long> authorIds) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildModerationQueueWhere(params, filter, authorIds);
        String orderBy = OrderByBuilder.build(sort, MODERATION_QUEUE_SORT_FIELDS);
        String limitOffset = PaginationSqlBuilder.pageLimit(params, pageable);
        return jdbcClient.sql(MODERATION_QUEUE_CTE + "SELECT * FROM moderation_queue" + where + orderBy + limitOffset)
                .paramSource(params)
                .query(MODERATION_QUEUE_ROW_MAPPER)
                .list();
    }

    public long countModerationQueue(@NonNull ModerationQueueFilterDto filter, Set<Long> authorIds) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildModerationQueueWhere(params, filter, authorIds);
        return jdbcClient.sql(MODERATION_QUEUE_CTE + "SELECT COUNT(*) FROM moderation_queue" + where)
                .paramSource(params)
                .query(Long.class)
                .single();
    }
}
