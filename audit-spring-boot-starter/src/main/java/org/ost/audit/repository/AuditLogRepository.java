package org.ost.audit.repository;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.ost.audit.services.AuditJsonSerializationService;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence layer for the audit subsystem. All reads and writes go through {@code audit_log} table.
 *
 * <p>Write side: {@link #save} appends a new snapshot row; {@link #deleteOlderThan} is called by the
 * cleanup scheduler.
 *
 * <p>Read side returns {@link AuditLogRow} — a generic record with SQL window-function columns
 * ({@code version}, {@code prev_id}, {@code prev_snapshot_data}) pre-computed at query time.
 * Callers ({@code AuditHistoryService}, {@code AuditActivityService}) map rows to their specific DTOs.
 *
 * <p>Snapshot-specific queries ({@link #getLastSnapshot}, {@link #getSnapshotContent},
 * {@link #getPreviousSnapshotContent}) are used by {@code DefaultAuditPort} for restore flows
 * and return typed results directly.
 */
@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class AuditLogRepository {

    private final JdbcClient                    jdbcClient;
    private final AuditJsonSerializationService mapper;
    private final RowMapper<AuditLogRow>        rowMapper;

    // ── Write ─────────────────────────────────────────────────────────────────

    public void save(EntityType entityType, Long entityId, ActionType actionType,
                     AuditableSnapshot snapshotData, List<ChangeEntry> changesSummary, Long actorId) {
        jdbcClient.sql("""
                        INSERT INTO audit_log (entity_type, entity_id, action_type, snapshot_data, changes_summary, actor_id)
                        VALUES (:entityType, :entityId, :actionType, :snapshotData, :changesSummary, :actorId)
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("entityType",     entityType.name())
                          .addValue("entityId",       entityId)
                          .addValue("actionType",     actionType.name())
                          .addValue("snapshotData",   mapper.toSnapshotJson(snapshotData),   Types.OTHER)
                          .addValue("changesSummary", mapper.toChangesJson(changesSummary),  Types.OTHER)
                          .addValue("actorId",        actorId))
                  .update();
    }

    public void deleteOlderThan(int days) {
        jdbcClient.sql("DELETE FROM audit_log WHERE created_at < NOW() - MAKE_INTERVAL(days => :days)")
                  .paramSource(new MapSqlParameterSource("days", days)).update();
    }

    // ── Read — generic rows ───────────────────────────────────────────────────

    public List<AuditLogRow> getEntityHistory(EntityType entityType, Long entityId, Long filterUserId) {
        return jdbcClient.sql("""
                        WITH numbered AS (
                            SELECT id, entity_type, entity_id, action_type, actor_id, created_at,
                                   snapshot_data::text                                                                 AS snapshot_data,
                                   changes_summary::text                                                               AS changes_summary,
                                   ROW_NUMBER() OVER (PARTITION BY entity_type, entity_id ORDER BY created_at)        AS version,
                                   LAG(id)                  OVER (PARTITION BY entity_type, entity_id ORDER BY created_at) AS prev_id,
                                   LAG(snapshot_data::text) OVER (PARTITION BY entity_type, entity_id ORDER BY created_at) AS prev_snapshot_data
                            FROM audit_log
                            WHERE entity_type = :entityType AND entity_id = :entityId
                        )
                        SELECT * FROM numbered
                        WHERE CAST(:filterUserId AS BIGINT) IS NULL OR actor_id = :filterUserId
                        ORDER BY version DESC
                        LIMIT 100
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType",   entityType.name())
                                 .addValue("entityId",     entityId)
                                 .addValue("filterUserId", filterUserId))
                         .query(rowMapper)
                         .list();
    }

    public List<AuditLogRow> findActivityForProfile(Long userId) {
        return jdbcClient.sql("""
                        WITH combined AS (
                            SELECT id, entity_type, entity_id, action_type, actor_id, created_at,
                                   snapshot_data::text   AS snapshot_data,
                                   changes_summary::text AS changes_summary
                            FROM audit_log
                            WHERE actor_id = :userId
                            UNION
                            SELECT id, entity_type, entity_id, action_type, actor_id, created_at,
                                   snapshot_data::text   AS snapshot_data,
                                   changes_summary::text AS changes_summary
                            FROM audit_log
                            WHERE entity_id = :userId
                              AND entity_type IN ('USER', 'USER_SETTINGS')
                              AND actor_id != :userId
                        ),
                        numbered AS (
                            SELECT c.*,
                                   ROW_NUMBER() OVER (PARTITION BY entity_type, entity_id ORDER BY created_at)        AS version,
                                   LAG(id)                  OVER (PARTITION BY entity_type, entity_id ORDER BY created_at) AS prev_id,
                                   LAG(snapshot_data)       OVER (PARTITION BY entity_type, entity_id ORDER BY created_at) AS prev_snapshot_data
                            FROM combined c
                        )
                        SELECT * FROM numbered
                        ORDER BY created_at DESC
                        LIMIT 20
                        """)
                         .paramSource(new MapSqlParameterSource("userId", userId))
                         .query(rowMapper)
                         .list();
    }

    // ── Read — snapshot-specific (used by DefaultAuditPort for restore flows) ─

    public Optional<AuditableSnapshot> getLastSnapshot(EntityType entityType, Long entityId) {
        return jdbcClient.sql("""
                        SELECT snapshot_data::text FROM audit_log
                        WHERE entity_type = :entityType AND entity_id = :entityId
                        ORDER BY created_at DESC LIMIT 1
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId))
                         .query(String.class)
                         .optional()
                         .map(mapper::fromSnapshot);
    }

    public Optional<AuditSnapshotContentDto> getSnapshotContent(Long snapshotId, EntityType entityType) {
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

    public Optional<AuditSnapshotContentDto> getPreviousSnapshotContent(Long snapshotId, EntityType entityType) {
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuditSnapshotContentDto mapSnapshotContent(ResultSet rs) throws SQLException {
        return new AuditSnapshotContentDto(mapper.fromSnapshot(rs.getString("snapshot_data")), rs.getInt("version"));
    }

    // ── Inner RowMapper ───────────────────────────────────────────────────────

    @Component
    public static final class AuditLogRowMapper implements RowMapper<AuditLogRow> {

        @Override
        public AuditLogRow mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
            return new AuditLogRow(
                    rs.getObject("id",          Long.class),
                    EntityType.valueOf(rs.getString("entity_type")),
                    rs.getObject("entity_id",   Long.class),
                    ActionType.valueOf(rs.getString("action_type")),
                    rs.getString("snapshot_data"),
                    rs.getString("changes_summary"),
                    rs.getObject("actor_id",    Long.class),
                    instant(rs, "created_at"),
                    rs.getInt("version"),
                    rs.getObject("prev_id",     Long.class),
                    rs.getString("prev_snapshot_data")
            );
        }
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toInstant() : null;
    }
}
