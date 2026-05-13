package org.ost.advertisement.repository.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ost.advertisement.audit.repository.AuditLogDescriptor;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.events.dto.AdvertisementHistoryDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * App-level repository that extends the starter's generic AuditLogRepository
 * with advertisement-specific history queries and user-snapshot state methods.
 */
@Repository
public class AuditLogRepository extends org.ost.advertisement.audit.repository.AuditLogRepository {

    public record UserSnapshotState(Long userId, String name, Role role) {}

    private final AdvertisementHistoryProjection historyQuery;

    public AuditLogRepository(JdbcClient jdbcClient,
                               @Qualifier("userSettingsObjectMapper") ObjectMapper objectMapper) {
        super(jdbcClient);
        this.historyQuery = new AdvertisementHistoryProjection(objectMapper);
    }

    public Optional<UserSnapshotState> getUserStateAt(Long snapshotId) {
        return jdbcClient().sql(
                "SELECT entity_id, snapshot_data->>'name' AS name, snapshot_data->>'role' AS role" +
                " FROM " + AuditLogDescriptor.Write.TABLE + " WHERE id = :id AND entity_type = :type")
                .paramSource(new MapSqlParameterSource().addValue("id", snapshotId).addValue("type", AuditLogDescriptor.EntityType.USER))
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

    public List<AdvertisementHistoryDto> getAdvertisementHistory(Long adId, Long filterUserId) {
        return historyQuery.queryAll(jdbcClient(),
                new MapSqlParameterSource()
                        .addValue("adId",         adId)
                        .addValue("filterUserId", filterUserId));
    }
}
