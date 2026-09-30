package org.ost.feedback.repository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.feedback.entity.Feedback;
import org.ost.feedback.entity.FeedbackAggregate;
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

import java.util.List;
import java.util.Optional;

/** Bespoke {@code JdbcClient} queries for {@code feedback}/{@code feedback_aggregate}; trivial CRUD delegates to {@link FeedbackCrudRepository}. */
@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class FeedbackRepository {

    private static final RowMapper<Feedback> ROW_MAPPER = (rs, _) -> Feedback.builder()
            .id(rs.getObject("id", Long.class))
            .entityType(EntityType.valueOf(rs.getString("entity_type")))
            .entityId(rs.getObject("entity_id", Long.class))
            .authorId(rs.getObject("author_id", Long.class))
            .rating(rs.getInt("rating"))
            .feedbackText(rs.getString("feedback_text"))
            .moderationStatus(FeedbackModerationStatus.valueOf(rs.getString("moderation_status")))
            .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null)
            .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null)
            .version(rs.getObject("version", Long.class))
            .build();

    private static final RowMapper<FeedbackAggregate> AGGREGATE_ROW_MAPPER = (rs, _) -> FeedbackAggregate.builder()
            .id(rs.getObject("id", Long.class))
            .entityType(EntityType.valueOf(rs.getString("entity_type")))
            .entityId(rs.getObject("entity_id", Long.class))
            .avgRating(rs.getDouble("avg_rating"))
            .reviewCount(rs.getInt("review_count"))
            .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null)
            .build();

    private final JdbcClient jdbcClient;
    private final FeedbackCrudRepository feedbackCrud;

    public Feedback save(@NonNull Feedback feedback) {
        try {
            return feedbackCrud.save(feedback);
        } catch (OptimisticLockingFailureException e) {
            throw new StaleWriteException("Feedback " + feedback.getId() + " was modified by another session", e);
        }
    }

    public Optional<Feedback> findByAuthorAndEntity(@NonNull Long authorId, @NonNull EntityType entityType, @NonNull Long entityId) {
        return jdbcClient.sql("""
                        SELECT id, entity_type, entity_id, author_id, rating, feedback_text, moderation_status, created_at, updated_at, version
                        FROM feedback
                        WHERE author_id = :authorId AND entity_type = :entityType AND entity_id = :entityId
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("authorId", authorId)
                        .addValue("entityType", entityType.name())
                        .addValue("entityId", entityId))
                .query(ROW_MAPPER)
                .optional();
    }

    public List<Feedback> findByEntity(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("entityType", entityType.name())
                .addValue("entityId", entityId);
        String pageClause = PaginationSqlBuilder.pageLimit(params, pageable);
        return jdbcClient.sql("""
                        SELECT id, entity_type, entity_id, author_id, rating, feedback_text, moderation_status, created_at, updated_at, version
                        FROM feedback
                        WHERE entity_type = :entityType AND entity_id = :entityId
                        ORDER BY created_at DESC
                        """ + pageClause)
                .paramSource(params)
                .query(ROW_MAPPER)
                .list();
    }

    public int count(@NonNull EntityType entityType, @NonNull Long entityId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM feedback WHERE entity_type = :entityType AND entity_id = :entityId")
                .paramSource(new MapSqlParameterSource()
                        .addValue("entityType", entityType.name())
                        .addValue("entityId", entityId))
                .query(Integer.class)
                .single();
    }

    /** Recomputes {@code avg_rating}/{@code review_count} from {@code feedback} and upserts the one {@code feedback_aggregate} row -- caller runs this in the same transaction as the triggering feedback write. */
    public void upsertAggregate(@NonNull EntityType entityType, @NonNull Long entityId) {
        jdbcClient.sql("""
                        INSERT INTO feedback_aggregate (entity_type, entity_id, avg_rating, review_count, updated_at)
                        SELECT :entityType, :entityId, COALESCE(AVG(rating), 0), COUNT(*), NOW()
                        FROM feedback
                        WHERE entity_type = :entityType AND entity_id = :entityId
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
}
