package org.ost.advertisement.audit.repository;

import org.ost.sqlengine.writer.SqlWriteCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class AuditLogRepository {

    public record SnapshotContent(String title, String description, int version) {}

    private static final SqlWriteCommand INSERT = SqlWriteCommand.of(
            "INSERT INTO " + AuditLogDescriptor.Write.TABLE +
            " (" + AuditLogDescriptor.Write.ENTITY_TYPE + ", " +
                   AuditLogDescriptor.Write.ENTITY_ID + ", " +
                   AuditLogDescriptor.Write.ACTION_TYPE + ", " +
                   AuditLogDescriptor.Write.SNAPSHOT_DATA + ", " +
                   AuditLogDescriptor.Write.CHANGES_SUMMARY + ", " +
                   AuditLogDescriptor.Write.ACTOR_ID + ")" +
            " VALUES (:entityType, :entityId, :actionType," +
            " CAST(:snapshotData AS JSONB), CAST(:changes AS JSONB), :actorId)"
    );

    private static final SqlWriteCommand UPDATE_CHANGES_SUMMARY = SqlWriteCommand.of(
            "UPDATE " + AuditLogDescriptor.Write.TABLE +
            " SET " + AuditLogDescriptor.Write.CHANGES_SUMMARY + " = CAST(:s AS JSONB) WHERE id = :id"
    );

    private final JdbcClient jdbcClient;

    public AuditLogRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    protected JdbcClient jdbcClient() {
        return jdbcClient;
    }

    public void insert(String entityType, Long entityId, String actionType,
                       String snapshotData, String changesSummary, Long actorId) {
        INSERT.execute(jdbcClient,
                new MapSqlParameterSource()
                        .addValue("entityType",   entityType)
                        .addValue("entityId",     entityId)
                        .addValue("actionType",   actionType)
                        .addValue("snapshotData", snapshotData)
                        .addValue("changes",      changesSummary)
                        .addValue("actorId",      actorId));
    }

    public Optional<String> getSnapshotData(Long snapshotId, String entityType) {
        return jdbcClient.sql(
                "SELECT " + AuditLogDescriptor.Write.SNAPSHOT_DATA + "::text" +
                " FROM " + AuditLogDescriptor.Write.TABLE + " WHERE id = :id AND entity_type = :type")
                .paramSource(new MapSqlParameterSource().addValue("id", snapshotId).addValue("type", entityType))
                .query((rs, row) -> rs.getString(1))
                .optional();
    }

    public Optional<String> getLastSnapshotData(String entityType, Long entityId) {
        return jdbcClient.sql(
                "SELECT " + AuditLogDescriptor.Write.SNAPSHOT_DATA + "::text" +
                " FROM " + AuditLogDescriptor.Write.TABLE +
                " WHERE entity_type = :type AND entity_id = :id ORDER BY created_at DESC LIMIT 1")
                .paramSource(new MapSqlParameterSource().addValue("type", entityType).addValue("id", entityId))
                .query((rs, row) -> rs.getString(1))
                .optional();
    }

    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId) {
        return jdbcClient.sql("""
                SELECT a.snapshot_data->>'title'       AS title,
                       a.snapshot_data->>'description' AS description,
                       (SELECT COUNT(*) FROM audit_log b
                        WHERE b.entity_type = 'ADVERTISEMENT' AND b.entity_id = a.entity_id
                          AND b.created_at <= a.created_at)::int AS version
                FROM audit_log a
                WHERE a.id = :id AND a.entity_type = 'ADVERTISEMENT'
                """)
                .paramSource(new MapSqlParameterSource("id", snapshotId))
                .query((rs, row) -> new SnapshotContent(
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("version")
                ))
                .optional();
    }

    public Optional<Long> findLastSnapshotId(Long adId) {
        return jdbcClient.sql(
                "SELECT id FROM audit_log WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId ORDER BY created_at DESC LIMIT 1")
                .paramSource(new MapSqlParameterSource("adId", adId))
                .query(Long.class)
                .optional();
    }

    public String getChangesSummary(Long snapshotId) {
        return jdbcClient.sql(
                "SELECT changes_summary::text FROM audit_log WHERE id = :id")
                .paramSource(new MapSqlParameterSource("id", snapshotId))
                .query(String.class)
                .single();
    }

    public void updateChangesSummary(Long snapshotId, String json) {
        UPDATE_CHANGES_SUMMARY.execute(jdbcClient,
                new MapSqlParameterSource().addValue("id", snapshotId).addValue("s", json));
    }

    public int deleteOlderThan(int days) {
        return jdbcClient.sql(
                "DELETE FROM " + AuditLogDescriptor.Write.TABLE +
                " WHERE created_at < NOW() - MAKE_INTERVAL(days => :days)")
                .paramSource(new MapSqlParameterSource("days", days))
                .update();
    }
}
