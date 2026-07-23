package org.ost.audit.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.audit.dto.AuditTimelineFilterDto;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;
import org.ost.query.filter.SqlBoundFilter;
import org.ost.query.filter.SqlFilterBuilder;
import org.ost.query.sort.OrderByBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
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
import java.util.Map;
import java.util.Optional;

import static org.ost.platform.audit.dto.AuditTimelineFilterDto.Fields.actionTypes;
import static org.ost.platform.audit.dto.AuditTimelineFilterDto.Fields.actorIds;
import static org.ost.platform.audit.dto.AuditTimelineFilterDto.Fields.entityTypes;
import static org.ost.platform.audit.dto.AuditTimelineFilterDto.Fields.fromDate;
import static org.ost.platform.audit.dto.AuditTimelineFilterDto.Fields.toDate;
import static org.ost.query.filter.SqlCondition.after;
import static org.ost.query.filter.SqlCondition.anyOf;
import static org.ost.query.filter.SqlCondition.before;
import static org.ost.query.filter.SqlCondition.inSet;

/**
 * Persistence layer for the audit subsystem. All reads and writes go through {@code audit_log} table.
 *
 * <p>Write side: {@link #save} appends a new snapshot row; {@link #deleteOlderThan} is called by the
 * cleanup scheduler.
 *
 * <p>Read side: {@link #findRows} queries by entity (with optional actor filter); {@link #findTimeline}
 * queries a filtered, paginated cross-entity feed. Both return {@link AuditLogProjection} with SQL window-function columns
 * ({@code version}, {@code prev_id}, {@code prev_snapshot_data}) pre-computed at query time — correct
 * for future pagination. Services map rows to their specific DTOs and apply limits via streams.
 *
 * <p>Snapshot-specific queries ({@link #getLastSnapshot}, {@link #getSnapshotContent}) are used
 * by {@code DefaultAuditPort} for restore flows and return typed results directly.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class AuditLogRepository {

    private static final Map<String, String> SORT_ALIASES = Map.of(AuditTimelineItemDto.Fields.createdAt, "al.created_at");

    private static final SqlFilterBuilder<AuditTimelineFilterDto> FILTER = new SqlFilterBuilder<>(List.of(
            SqlBoundFilter.of(actorIds,    "al.actor_id",    (m, v) -> anyOf(m, v.getActorIds())),
            SqlBoundFilter.of(entityTypes, "al.entity_type", (m, v) -> inSet(m, v.getEntityTypes())),
            SqlBoundFilter.of(actionTypes, "al.action_type", (m, v) -> inSet(m, v.getActionTypes())),
            SqlBoundFilter.of(fromDate,    "al.created_at",  (m, v) -> after(m, v.getFromDate())),
            SqlBoundFilter.of(toDate,      "al.created_at",  (m, v) -> before(m, v.getToDate()))
    ));

    @Qualifier("auditObjectMapper") private final ObjectMapper objectMapper;
    private final JdbcClient                                   jdbcClient;
    private final ProjectionMapper                             projectionMapper;

    // ── Write ─────────────────────────────────────────────────────────────────

    public void save(EntityType entityType, Long entityId, ActionType actionType,
                     AuditableSnapshot snapshotData, Long actorId) {
        jdbcClient.sql("""
                        INSERT INTO audit_log (entity_type, entity_id, action_type, snapshot_data, actor_id)
                        VALUES (:entityType, :entityId, :actionType, :snapshotData, :actorId)
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("entityType",   entityType.name())
                          .addValue("entityId",     entityId)
                          .addValue("actionType",   actionType.name())
                          .addValue("snapshotData", toSnapshotJson(snapshotData), Types.OTHER)
                          .addValue("actorId",      actorId))
                  .update();
    }

    public int deleteOlderThan(int days) {
        return jdbcClient.sql("DELETE FROM audit_log WHERE created_at < NOW() - MAKE_INTERVAL(days => :days)")
                  .paramSource(new MapSqlParameterSource("days", days)).update();
    }

    // ── Read — generic rows ───────────────────────────────────────────────────

    public List<AuditLogProjection> findRows(EntityType entityType, Long entityId, Long filterActorId, int limit) {
        return jdbcClient.sql("""
                        WITH numbered AS (
                            SELECT id, entity_type, entity_id, action_type, actor_id, created_at,
                                   snapshot_data::text                                                                       AS snapshot_data,
                                   ROW_NUMBER() OVER (PARTITION BY entity_type, entity_id ORDER BY created_at, id)          AS version,
                                   LAG(id)                  OVER (PARTITION BY entity_type, entity_id ORDER BY created_at, id) AS prev_id,
                                   LAG(snapshot_data::text) OVER (PARTITION BY entity_type, entity_id ORDER BY created_at, id) AS prev_snapshot_data
                            FROM audit_log
                            WHERE entity_type = :entityType AND entity_id = :entityId
                        )
                        SELECT * FROM numbered
                        WHERE CAST(:filterActorId AS BIGINT) IS NULL OR actor_id = :filterActorId
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType",    entityType.name())
                                 .addValue("entityId",      entityId)
                                 .addValue("filterActorId", filterActorId)
                                 .addValue("limit",         limit))
                         .query(projectionMapper)
                         .list();
    }

    public List<AuditLogProjection> findTimeline(AuditTimelineFilterDto filter, Sort sort, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit",  size)
                .addValue("offset", (long) page * size);
        String orderBy = OrderByBuilder.build(sort, SORT_ALIASES);
        if (orderBy.isBlank()) orderBy = " ORDER BY al.created_at DESC";
        String sql = """
                        WITH numbered AS (
                            SELECT id, entity_type, entity_id, action_type, actor_id, created_at,
                                   snapshot_data::text                                                                       AS snapshot_data,
                                   ROW_NUMBER() OVER (PARTITION BY entity_type, entity_id ORDER BY created_at, id)          AS version,
                                   LAG(id)                  OVER (PARTITION BY entity_type, entity_id ORDER BY created_at, id) AS prev_id,
                                   LAG(snapshot_data::text) OVER (PARTITION BY entity_type, entity_id ORDER BY created_at, id) AS prev_snapshot_data
                            FROM audit_log
                        )
                        SELECT * FROM numbered al
                        WHERE 1=1%s
                        %s
                        LIMIT :limit OFFSET :offset
                        """.formatted(FILTER.build(params, filter, " AND "), orderBy);
        return jdbcClient.sql(sql).paramSource(params).query(projectionMapper).list();
    }

    public int countTimeline(AuditTimelineFilterDto filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) FROM audit_log al WHERE 1=1%s"
                .formatted(FILTER.build(params, filter, " AND "));
        return jdbcClient.sql(sql).paramSource(params).query(Integer.class).single();
    }

    // ── Read — snapshot-specific (used by DefaultAuditPort for restore flows) ─

    public Optional<AuditableSnapshot> getLastSnapshot(EntityType entityType, Long entityId) {
        return jdbcClient.sql("""
                        SELECT snapshot_data::text FROM audit_log
                        WHERE entity_type = :entityType AND entity_id = :entityId
                        ORDER BY created_at DESC, id DESC LIMIT 1
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId))
                         .query(String.class)
                         .optional()
                         .map(json -> ProjectionMapper.parseSnapshot(objectMapper, json));
    }

    public Optional<AuditSnapshotContentDto<? extends AuditableSnapshot>> getSnapshotContent(Long snapshotId, EntityType entityType) {
        return jdbcClient.sql("""
                        SELECT a.snapshot_data::text AS snapshot_data,
                               (SELECT COUNT(*) FROM audit_log b
                                WHERE b.entity_type = a.entity_type
                                  AND b.entity_id   = a.entity_id
                                  AND (b.created_at, b.id) <= (a.created_at, a.id))::int AS version
                        FROM audit_log a
                        WHERE a.id = :id AND a.entity_type = :entityType
                        """)
                         .paramSource(new MapSqlParameterSource().addValue("id", snapshotId).addValue("entityType", entityType.name()))
                         .query(snapshotContentMapper())
                         .optional();
    }


    // ── Helpers ───────────────────────────────────────────────────────────────

    private RowMapper<AuditSnapshotContentDto<? extends AuditableSnapshot>> snapshotContentMapper() {
        return (rs, _) -> new AuditSnapshotContentDto<>(ProjectionMapper.parseSnapshot(objectMapper, rs.getString("snapshot_data")), rs.getInt("version"));
    }

    private String toSnapshotJson(AuditableSnapshot snapshot) {
        if (snapshot == null) return null;
        try {
            return objectMapper.writerFor(AuditableSnapshot.class).writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize snapshot: " + snapshot.getClass().getSimpleName(), e);
        }
    }

    @Slf4j
    @Component
    @RequiredArgsConstructor
    static class ProjectionMapper implements RowMapper<AuditLogProjection> {

        @Qualifier("auditObjectMapper") private final ObjectMapper objectMapper;

        @Override
        public AuditLogProjection mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
            return new AuditLogProjection(
                    rs.getObject("id",          Long.class),
                    EntityType.valueOf(rs.getString("entity_type")),
                    rs.getObject("entity_id",   Long.class),
                    ActionType.valueOf(rs.getString("action_type")),
                    parseSnapshot(objectMapper, rs.getString("snapshot_data")),
                    rs.getObject("actor_id",    Long.class),
                    instant(rs, "created_at"),
                    rs.getInt("version"),
                    rs.getObject("prev_id",     Long.class),
                    parseSnapshot(objectMapper, rs.getString("prev_snapshot_data"))
            );
        }

        private static Instant instant(ResultSet rs, String col) throws SQLException {
            Timestamp ts = rs.getTimestamp(col);
            return ts != null ? ts.toInstant() : null;
        }

        static AuditableSnapshot parseSnapshot(ObjectMapper objectMapper, String json) {
            if (json == null || json.isBlank()) return null;
            try {
                return objectMapper.readValue(json, AuditableSnapshot.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize snapshot: {}", json.substring(0, Math.min(json.length(), 120)), e);
                return null;
            }
        }
    }

}
