package org.ost.audit.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.audit.dto.SnapshotContent;
import org.ost.platform.audit.dto.SnapshotPayload;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.EntityDisplayNameResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class AuditLogRepository {

    private static final String TABLE = "audit_log";

    // ── Read SQL ──────────────────────────────────────────────────────────────

    private static final String SELECT_SNAPSHOT_DATA_BY_ID =
            "SELECT snapshot_data::text FROM " + TABLE +
            " WHERE id = :id AND entity_type = :entityType";

    private static final String SELECT_LAST_SNAPSHOT_DATA =
            "SELECT snapshot_data::text FROM " + TABLE +
            " WHERE entity_type = :entityType AND entity_id = :entityId" +
            " ORDER BY created_at DESC LIMIT 1";

    private static final String SELECT_SNAPSHOT_CONTENT_BY_ID = """
            SELECT a.snapshot_data::text AS snapshot_data,
                   (SELECT COUNT(*) FROM audit_log b
                    WHERE b.entity_type = a.entity_type
                      AND b.entity_id   = a.entity_id
                      AND b.created_at <= a.created_at)::int AS version
            FROM audit_log a
            WHERE a.id = :id AND a.entity_type = :entityType
            """;

    private static final String SELECT_LAST_SNAPSHOT_ID =
            "SELECT id FROM " + TABLE +
            " WHERE entity_type = :entityType AND entity_id = :entityId" +
            " ORDER BY created_at DESC LIMIT 1";

    private static final String SELECT_CHANGES_SUMMARY =
            "SELECT changes_summary::text FROM " + TABLE + " WHERE id = :id";

    private static final String SELECT_PREVIOUS_SNAPSHOT_CONTENT = """
            SELECT prev.snapshot_data::text AS snapshot_data,
                   (SELECT COUNT(*) FROM audit_log b
                    WHERE b.entity_type = prev.entity_type
                      AND b.entity_id   = prev.entity_id
                      AND b.created_at <= prev.created_at)::int AS version
            FROM audit_log cur
            JOIN LATERAL (
                SELECT entity_id, entity_type, snapshot_data, created_at
                FROM audit_log
                WHERE entity_type = :entityType
                  AND entity_id = cur.entity_id
                  AND created_at < cur.created_at
                ORDER BY created_at DESC LIMIT 1
            ) prev ON true
            WHERE cur.id = :snapshotId AND cur.entity_type = :entityType
            """;

    private static final String ACTIVITY_QUERY =
            "SELECT al.id AS snapshot_id, al.entity_id, al.entity_type, al.action_type," +
            " al.created_at, FALSE AS entity_exists," +
            " al.changes_summary::text AS changes_summary," +
            " al.actor_id, NULL::text AS changed_by_name," +
            " al.snapshot_data::text AS snapshot_data" +
            " FROM " + TABLE + " al" +
            " WHERE al.actor_id = :actorId" +
            " ORDER BY al.created_at DESC LIMIT 20";

    private static final String HISTORY_QUERY = """
            WITH numbered AS (
                SELECT id,
                       ROW_NUMBER()              OVER (PARTITION BY entity_type, entity_id ORDER BY created_at)        AS version,
                       LAG(id)                   OVER (PARTITION BY entity_type, entity_id ORDER BY created_at)        AS prev_id,
                       LAG(snapshot_data::text)  OVER (PARTITION BY entity_type, entity_id ORDER BY created_at)        AS prev_snapshot_data,
                       snapshot_data::text                                                                              AS snapshot_data,
                       action_type,
                       changes_summary::text                                                                            AS changes_summary,
                       actor_id,
                       created_at
                FROM audit_log
                WHERE entity_type = :entityType AND entity_id = :entityId
            )
            SELECT al.id, al.version::int, al.action_type,
                   al.actor_id,
                   NULL::text AS changed_by_name,
                   al.created_at,
                   al.snapshot_data,
                   al.changes_summary,
                   al.prev_id,
                   al.prev_snapshot_data
            FROM numbered al
            WHERE CAST(:filterUserId AS BIGINT) IS NULL OR al.actor_id = :filterUserId
            ORDER BY al.version DESC
            LIMIT 100
            """;

    // ── Write SQL ─────────────────────────────────────────────────────────────

    private static final String INSERT =
            "INSERT INTO " + TABLE +
            " (entity_type, entity_id, action_type, snapshot_data, changes_summary, actor_id)" +
            " VALUES (:entityType, :entityId, :actionType, CAST(:snapshot_data AS JSONB), CAST(:changes_summary AS JSONB), :actorId)";

    private static final String UPDATE_CHANGES_SUMMARY =
            "UPDATE " + TABLE + " SET changes_summary = CAST(:changesSummary AS JSONB) WHERE id = :id";

    private static final String DELETE_OLDER_THAN =
            "DELETE FROM " + TABLE + " WHERE created_at < NOW() - MAKE_INTERVAL(days => :days)";

    // ── Fields ────────────────────────────────────────────────────────────────

    private final JdbcClient jdbcClient;
    private final RowMapper<ActivityItemDto> activityMapper;
    private final RowMapper<EntityHistoryDto> historyMapper;

    public AuditLogRepository(JdbcClient jdbcClient,
                              @Qualifier("auditObjectMapper") ObjectMapper objectMapper,
                              List<EntityDisplayNameResolver> resolvers) {
        this.jdbcClient     = jdbcClient;
        this.activityMapper = new ActivityMapper(objectMapper, resolvers);
        this.historyMapper  = new HistoryMapper(objectMapper);
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    public void insert(EntityType entityType, Long entityId, ActionType actionType,
                       String snapshotData, String changesSummary, Long actorId) {
        jdbcClient.sql(INSERT)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("entityType",      entityType.name())
                          .addValue("entityId",        entityId)
                          .addValue("actionType",      actionType.name())
                          .addValue("snapshot_data",   snapshotData)
                          .addValue("changes_summary", changesSummary)
                          .addValue("actorId",         actorId))
                  .update();
    }

    public void updateChangesSummary(Long snapshotId, String json) {
        jdbcClient.sql(UPDATE_CHANGES_SUMMARY)
                  .paramSource(new MapSqlParameterSource().addValue("id", snapshotId).addValue("changesSummary", json))
                  .update();
    }

    public void deleteOlderThan(int days) {
        jdbcClient.sql(DELETE_OLDER_THAN).paramSource(new MapSqlParameterSource("days", days)).update();
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public Optional<String> getLastSnapshotData(EntityType entityType, Long entityId) {
        return jdbcClient.sql(SELECT_LAST_SNAPSHOT_DATA)
                         .paramSource(entityParams(entityType, entityId))
                         .query(String.class)
                         .optional();
    }

    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId, EntityType entityType) {
        return jdbcClient.sql(SELECT_SNAPSHOT_CONTENT_BY_ID)
                         .paramSource(new MapSqlParameterSource().addValue("id", snapshotId).addValue("entityType", entityType.name()))
                         .query((rs, _) -> mapSnapshotContent(rs))
                         .optional();
    }

    public Optional<SnapshotContent> getPreviousSnapshotContent(Long snapshotId, EntityType entityType) {
        return jdbcClient.sql(SELECT_PREVIOUS_SNAPSHOT_CONTENT)
                         .paramSource(new MapSqlParameterSource().addValue("snapshotId", snapshotId).addValue("entityType", entityType.name()))
                         .query((rs, _) -> mapSnapshotContent(rs))
                         .optional();
    }

    public Optional<Long> findLastSnapshotId(EntityType entityType, Long entityId) {
        return jdbcClient.sql(SELECT_LAST_SNAPSHOT_ID)
                         .paramSource(entityParams(entityType, entityId))
                         .query(Long.class)
                         .optional();
    }

    public String getChangesSummary(Long snapshotId) {
        return jdbcClient.sql(SELECT_CHANGES_SUMMARY)
                         .paramSource(new MapSqlParameterSource("id", snapshotId))
                         .query(String.class)
                         .optional()
                         .orElseThrow();
    }

    public List<EntityHistoryDto> getEntityHistory(EntityType entityType, Long entityId, Long filterUserId) {
        return jdbcClient.sql(HISTORY_QUERY)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType",   entityType.name())
                                 .addValue("entityId",     entityId)
                                 .addValue("filterUserId", filterUserId))
                         .query(historyMapper)
                         .list();
    }

    public List<ActivityItemDto> findActivityByActor(Long actorId) {
        return jdbcClient.sql(ACTIVITY_QUERY)
                         .paramSource(new MapSqlParameterSource("actorId", actorId))
                         .query(activityMapper)
                         .list();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static MapSqlParameterSource entityParams(EntityType entityType, Long entityId) {
        return new MapSqlParameterSource().addValue("entityType", entityType.name()).addValue("entityId", entityId);
    }

    private static SnapshotContent mapSnapshotContent(ResultSet rs) throws SQLException {
        return new SnapshotContent(new SnapshotPayload(rs.getString("snapshot_data")), rs.getInt("version"));
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toInstant() : null;
    }

    private static List<ChangeEntry> parseChanges(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception _) {
            return List.of();
        }
    }

    // ── Inner RowMappers ──────────────────────────────────────────────────────

    private final class ActivityMapper implements RowMapper<ActivityItemDto> {
        private final List<EntityDisplayNameResolver> resolvers;
        private final ObjectMapper objectMapper;

        ActivityMapper(ObjectMapper objectMapper, List<EntityDisplayNameResolver> resolvers) {
            this.objectMapper = objectMapper;
            this.resolvers    = resolvers;
        }

        @Override
        public ActivityItemDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
            EntityType entityType = EntityType.valueOf(rs.getString("entity_type"));
            SnapshotPayload payload = new SnapshotPayload(rs.getString("snapshot_data"));
            String displayName = resolvers.stream()
                    .filter(r -> r.supports(entityType))
                    .findFirst()
                    .map(r -> r.resolveDisplayName(entityType, payload))
                    .orElse("");
            return new ActivityItemDto(
                    rs.getObject("snapshot_id", Long.class),
                    rs.getObject("entity_id",   Long.class),
                    entityType,
                    displayName,
                    ActionType.valueOf(rs.getString("action_type")),
                    instant(rs, "created_at"),
                    rs.getObject("entity_exists", Boolean.class),
                    parseChanges(objectMapper, rs.getString("changes_summary")),
                    rs.getObject("actor_id",      Long.class),
                    rs.getString("changed_by_name"),
                    payload
            );
        }
    }

    private final class HistoryMapper implements RowMapper<EntityHistoryDto> {
        private final ObjectMapper objectMapper;

        HistoryMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public EntityHistoryDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
            return new EntityHistoryDto(
                    rs.getObject("id",      Long.class),
                    rs.getInt("version"),
                    ActionType.valueOf(rs.getString("action_type")),
                    rs.getObject("actor_id", Long.class),
                    rs.getString("changed_by_name"),
                    instant(rs, "created_at"),
                    parseChanges(objectMapper, rs.getString("changes_summary")),
                    rs.getObject("prev_id", Long.class),
                    new SnapshotPayload(rs.getString("snapshot_data")),
                    new SnapshotPayload(rs.getString("prev_snapshot_data"))
            );
        }
    }
}
