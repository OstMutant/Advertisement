package org.ost.audit.repository;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.ost.audit.services.AuditJsonSerializationService;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.audit.dto.SnapshotContentDto;
import org.ost.platform.audit.dto.SnapshotPayloadDto;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.EntityNameHook;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class AuditLogRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<ActivityItemDto> activityMapper;
    private final RowMapper<EntityHistoryDto> historyMapper;

    // ── Write ─────────────────────────────────────────────────────────────────

    public void insert(EntityType entityType, Long entityId, ActionType actionType,
                       String snapshotData, String changesSummary, Long actorId) {
        jdbcClient.sql("""
                        INSERT INTO audit_log (entity_type, entity_id, action_type, snapshot_data, changes_summary, actor_id)
                        VALUES (:entityType, :entityId, :actionType, CAST(:snapshot_data AS JSONB), CAST(:changes_summary AS JSONB), :actorId)
                        """)
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
        jdbcClient.sql("UPDATE audit_log SET changes_summary = CAST(:changesSummary AS JSONB) WHERE id = :id")
                  .paramSource(new MapSqlParameterSource().addValue("id", snapshotId).addValue("changesSummary", json))
                  .update();
    }

    public void deleteOlderThan(int days) {
        jdbcClient.sql("DELETE FROM audit_log WHERE created_at < NOW() - MAKE_INTERVAL(days => :days)")
                  .paramSource(new MapSqlParameterSource("days", days)).update();
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public Optional<String> getLastSnapshotData(EntityType entityType, Long entityId) {
        return jdbcClient.sql("""
                        SELECT snapshot_data::text FROM audit_log
                        WHERE entity_type = :entityType AND entity_id = :entityId
                        ORDER BY created_at DESC LIMIT 1
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId))
                         .query(String.class)
                         .optional();
    }

    public Optional<SnapshotContentDto> getSnapshotContent(Long snapshotId, EntityType entityType) {
        return jdbcClient.sql("""
                        SELECT a.snapshot_data::text AS snapshot_data,
                               (SELECT COUNT(*) FROM audit_log b
                                WHERE b.entity_type = a.entity_type
                                  AND b.entity_id   = a.entity_id
                                  AND b.created_at <= a.created_at)::int AS version
                        FROM audit_log a
                        WHERE a.id = :id AND a.entity_type = :entityType
                        """)
                         .paramSource(new MapSqlParameterSource().addValue("id", snapshotId).addValue("entityType", entityType.name()))
                         .query((rs, _) -> mapSnapshotContent(rs))
                         .optional();
    }

    public Optional<SnapshotContentDto> getPreviousSnapshotContent(Long snapshotId, EntityType entityType) {
        return jdbcClient.sql("""
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
                        """)
                         .paramSource(new MapSqlParameterSource().addValue("snapshotId", snapshotId).addValue("entityType", entityType.name()))
                         .query((rs, _) -> mapSnapshotContent(rs))
                         .optional();
    }

    public Optional<Long> findLastSnapshotId(EntityType entityType, Long entityId) {
        return jdbcClient.sql("""
                        SELECT id FROM audit_log
                        WHERE entity_type = :entityType AND entity_id = :entityId
                        ORDER BY created_at DESC LIMIT 1
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId))
                         .query(Long.class)
                         .optional();
    }

    public String getChangesSummary(Long snapshotId) {
        return jdbcClient.sql("SELECT changes_summary::text FROM audit_log WHERE id = :id")
                         .paramSource(new MapSqlParameterSource("id", snapshotId))
                         .query(String.class)
                         .optional()
                         .orElseThrow();
    }

    public List<EntityHistoryDto> getEntityHistory(EntityType entityType, Long entityId, Long filterUserId) {
        return jdbcClient.sql("""
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
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType",   entityType.name())
                                 .addValue("entityId",     entityId)
                                 .addValue("filterUserId", filterUserId))
                         .query(historyMapper)
                         .list();
    }

    public List<ActivityItemDto> findActivityByActor(Long actorId) {
        return jdbcClient.sql("""
                        SELECT al.id AS snapshot_id, al.entity_id, al.entity_type, al.action_type,
                               al.created_at, FALSE AS entity_exists,
                               al.changes_summary::text AS changes_summary,
                               al.actor_id, NULL::text AS changed_by_name,
                               al.snapshot_data::text AS snapshot_data
                        FROM audit_log al
                        WHERE al.actor_id = :actorId
                        ORDER BY al.created_at DESC LIMIT 20
                        """)
                         .paramSource(new MapSqlParameterSource("actorId", actorId))
                         .query(activityMapper)
                         .list();
    }

    public List<ActivityItemDto> findActivityForProfile(Long userId) {
        return jdbcClient.sql("""
                        SELECT al.id AS snapshot_id, al.entity_id, al.entity_type, al.action_type,
                               al.created_at, FALSE AS entity_exists,
                               al.changes_summary::text AS changes_summary,
                               al.actor_id, NULL::text AS changed_by_name,
                               al.snapshot_data::text AS snapshot_data
                        FROM audit_log al
                        WHERE al.actor_id = :userId
                        UNION
                        SELECT al.id AS snapshot_id, al.entity_id, al.entity_type, al.action_type,
                               al.created_at, FALSE AS entity_exists,
                               al.changes_summary::text AS changes_summary,
                               al.actor_id, NULL::text AS changed_by_name,
                               al.snapshot_data::text AS snapshot_data
                        FROM audit_log al
                        WHERE al.entity_id = :userId
                          AND al.entity_type IN ('USER', 'USER_SETTINGS')
                          AND al.actor_id != :userId
                        ORDER BY created_at DESC
                        LIMIT 20
                        """)
                         .paramSource(new MapSqlParameterSource("userId", userId))
                         .query(activityMapper)
                         .list();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static SnapshotContentDto mapSnapshotContent(ResultSet rs) throws SQLException {
        return new SnapshotContentDto(new SnapshotPayloadDto(rs.getString("snapshot_data")), rs.getInt("version"));
    }

    // ── Inner RowMappers ──────────────────────────────────────────────────────

    @Component
    @RequiredArgsConstructor
    public static final class ActivityMapper implements RowMapper<ActivityItemDto> {
        private final AuditJsonSerializationService mapper;
        private final List<EntityNameHook> resolvers;

        @Override
        public ActivityItemDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
            EntityType entityType = EntityType.valueOf(rs.getString("entity_type"));
            SnapshotPayloadDto payload = new SnapshotPayloadDto(rs.getString("snapshot_data"));
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
                    mapper.fromJsonList(rs.getString("changes_summary")),
                    rs.getObject("actor_id",      Long.class),
                    rs.getString("changed_by_name"),
                    payload
            );
        }
    }

    @Component
    @RequiredArgsConstructor
    public static final class HistoryMapper implements RowMapper<EntityHistoryDto> {
        private final AuditJsonSerializationService mapper;

        @Override
        public EntityHistoryDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
            return new EntityHistoryDto(
                    rs.getObject("id",      Long.class),
                    rs.getInt("version"),
                    ActionType.valueOf(rs.getString("action_type")),
                    rs.getObject("actor_id", Long.class),
                    rs.getString("changed_by_name"),
                    instant(rs, "created_at"),
                    mapper.fromJsonList(rs.getString("changes_summary")),
                    rs.getObject("prev_id", Long.class),
                    new SnapshotPayloadDto(rs.getString("snapshot_data")),
                    new SnapshotPayloadDto(rs.getString("prev_snapshot_data"))
            );
        }
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toInstant() : null;
    }
}
