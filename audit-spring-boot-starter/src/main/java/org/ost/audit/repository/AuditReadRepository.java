package org.ost.audit.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.audit.dto.SnapshotContent;
import org.ost.platform.audit.dto.SnapshotPayload;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

public class AuditReadRepository extends AuditLogRepository {

    private final EntityHistoryProjection historyQuery;

    public AuditReadRepository(JdbcClient jdbcClient,
                               @Qualifier("auditObjectMapper") ObjectMapper objectMapper) {
        super(jdbcClient);
        this.historyQuery = new EntityHistoryProjection(objectMapper);
    }

    public Optional<SnapshotContent> getPreviousSnapshotContent(Long snapshotId, EntityType entityType) {
        return jdbcClient().sql("""
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
                .paramSource(new MapSqlParameterSource()
                        .addValue("snapshotId", snapshotId)
                        .addValue("entityType", entityType.name()))
                .query((rs, row) -> new SnapshotContent(
                        new SnapshotPayload(rs.getString("snapshot_data")),
                        rs.getInt("version")
                ))
                .optional();
    }

    public List<EntityHistoryDto> getEntityHistory(EntityType entityType, Long entityId, Long filterUserId) {
        return historyQuery.queryAll(jdbcClient(),
                new MapSqlParameterSource()
                        .addValue("entityType",   entityType.name())
                        .addValue("entityId",     entityId)
                        .addValue("filterUserId", filterUserId));
    }
}
