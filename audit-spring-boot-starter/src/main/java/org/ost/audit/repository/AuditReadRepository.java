package org.ost.audit.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.audit.dto.UserSnapshotState;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.model.Role;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

public class AuditReadRepository extends AuditLogRepository {

    private final EntityHistoryProjection historyQuery;

    public AuditReadRepository(JdbcClient jdbcClient,
                               @Qualifier("userSettingsObjectMapper") ObjectMapper objectMapper) {
        super(jdbcClient);
        this.historyQuery = new EntityHistoryProjection(objectMapper);
    }

    public Optional<UserSnapshotState> getUserStateAt(Long snapshotId) {
        return jdbcClient().sql(
                "SELECT entity_id, snapshot_data->>'name' AS name, snapshot_data->>'role' AS role" +
                " FROM " + AuditLogDescriptor.Write.TABLE + " WHERE id = :id AND entity_type = :type")
                .paramSource(new MapSqlParameterSource()
                        .addValue("id", snapshotId)
                        .addValue("type", EntityType.USER.name()))
                .query((rs, row) -> new UserSnapshotState(
                        rs.getLong("entity_id"),
                        rs.getString("name"),
                        Role.valueOf(rs.getString("role"))
                ))
                .optional();
    }

    public Optional<UserSnapshotState> getUserStateBefore(Long snapshotId) {
        return jdbcClient().sql("""
                SELECT prev.entity_id, prev.snapshot_data->>'name' AS name, prev.snapshot_data->>'role' AS role
                FROM audit_log cur
                JOIN LATERAL (
                    SELECT entity_id, snapshot_data
                    FROM audit_log
                    WHERE entity_type = 'USER' AND entity_id = cur.entity_id AND created_at < cur.created_at
                    ORDER BY created_at DESC LIMIT 1
                ) prev ON true
                WHERE cur.id = :snapshotId AND cur.entity_type = 'USER'
                """)
                .paramSource(new MapSqlParameterSource("snapshotId", snapshotId))
                .query((rs, row) -> new UserSnapshotState(
                        rs.getLong("entity_id"),
                        rs.getString("name"),
                        Role.valueOf(rs.getString("role"))
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
