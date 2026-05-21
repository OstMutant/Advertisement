package org.ost.attachment.repository;

import lombok.RequiredArgsConstructor;
import org.ost.attachment.storage.ConditionalOnAttachmentEnabled;
import org.ost.platform.core.model.EntityType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnAttachmentEnabled
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class AttachmentSnapshotRepository {

    private final JdbcClient jdbcClient;

    public void insert(EntityType entityType, Long entityId, String[] urls, String changesJson, Long actorId) {
        jdbcClient.sql("""
                        INSERT INTO attachment_snapshot
                            (entity_type, entity_id, attachment_urls, changes_summary, changed_by_actor_id, created_at)
                        VALUES (:entityType, :entityId, :urls, CAST(:changes AS JSONB), :actorId, NOW())
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("entityType", entityType.name())
                          .addValue("entityId",   entityId)
                          .addValue("urls",        urls)
                          .addValue("changes",     changesJson)
                          .addValue("actorId",     actorId))
                  .update();
    }

    public List<String> getPrevUrls(EntityType entityType, Long entityId) {
        return jdbcClient.sql("""
                        SELECT attachment_urls FROM attachment_snapshot
                        WHERE entity_type = :entityType AND entity_id = :entityId
                        ORDER BY created_at DESC LIMIT 1
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId))
                         .query((rs, _) -> extractUrls(rs))
                         .optional()
                         .orElse(List.of());
    }

    public String[] getUrlsAtVersion(EntityType entityType, Long entityId, int version) {
        return jdbcClient.sql("""
                        SELECT attachment_urls FROM attachment_snapshot
                        WHERE entity_type = :entityType AND entity_id = :entityId
                          AND created_at < COALESCE((
                              SELECT al.created_at FROM (
                                  SELECT created_at,
                                         ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at) AS rn
                                  FROM audit_log WHERE entity_type = :entityType AND entity_id = :entityId
                              ) al WHERE al.rn = :version + 1
                          ), 'infinity'::timestamptz)
                        ORDER BY created_at DESC LIMIT 1
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId)
                                 .addValue("version",    version))
                         .query((rs, _) -> extractUrls(rs))
                         .optional()
                         .map(l -> l.toArray(new String[0]))
                         .orElse(new String[0]);
    }

    public Optional<List<String>> getUrlsForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return jdbcClient.sql("""
                        SELECT attachment_urls FROM attachment_snapshot
                        WHERE entity_type = :entityType AND entity_id = :entityId
                          AND created_at < COALESCE((
                              SELECT created_at FROM audit_log
                              WHERE entity_type = :entityType AND entity_id = :entityId
                                AND created_at > (SELECT created_at FROM audit_log WHERE id = :snapshotId)
                              ORDER BY created_at ASC LIMIT 1
                          ), 'infinity'::timestamptz)
                        ORDER BY created_at DESC LIMIT 1
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId)
                                 .addValue("snapshotId", snapshotId))
                         .query((rs, _) -> extractUrls(rs))
                         .optional();
    }

    public Optional<String> getChangesJsonForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return jdbcClient.sql("""
                        SELECT changes_summary::text AS changes_summary FROM attachment_snapshot
                        WHERE entity_type = :entityType AND entity_id = :entityId
                          AND created_at >= (SELECT created_at FROM audit_log WHERE id = :snapshotId)
                          AND created_at < COALESCE((
                              SELECT created_at FROM audit_log
                              WHERE entity_type = :entityType AND entity_id = :entityId
                                AND created_at > (SELECT created_at FROM audit_log WHERE id = :snapshotId)
                              ORDER BY created_at ASC LIMIT 1
                          ), 'infinity'::timestamptz)
                          AND changes_summary IS NOT NULL
                        ORDER BY created_at ASC LIMIT 1
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId)
                                 .addValue("snapshotId", snapshotId))
                         .query(String.class)
                         .optional();
    }

    public Optional<String> getChangesJson(EntityType entityType, Long entityId, int version) {
        return jdbcClient.sql("""
                        SELECT changes_summary::text AS changes_summary FROM attachment_snapshot
                        WHERE entity_type = :entityType AND entity_id = :entityId
                          AND created_at >= (
                              SELECT al.created_at FROM (
                                  SELECT created_at,
                                         ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at) AS rn
                                  FROM audit_log WHERE entity_type = :entityType AND entity_id = :entityId
                              ) al WHERE al.rn = :version
                          )
                          AND created_at < COALESCE((
                              SELECT al.created_at FROM (
                                  SELECT created_at,
                                         ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at) AS rn
                                  FROM audit_log WHERE entity_type = :entityType AND entity_id = :entityId
                              ) al WHERE al.rn = :version + 1
                          ), 'infinity'::timestamptz)
                          AND changes_summary IS NOT NULL
                        ORDER BY created_at ASC LIMIT 1
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId)
                                 .addValue("version",    version))
                         .query(String.class)
                         .optional();
    }

    private static List<String> extractUrls(ResultSet rs) {
        try {
            java.sql.Array arr = rs.getArray("attachment_urls");
            if (arr == null) return List.of();
            String[] arr2 = (String[]) arr.getArray();
            return arr2 == null ? List.of() : List.of(arr2);
        } catch (Exception _) {
            return List.of();
        }
    }
}
