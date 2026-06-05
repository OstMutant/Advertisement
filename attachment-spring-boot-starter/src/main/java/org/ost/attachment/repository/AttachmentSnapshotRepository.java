package org.ost.attachment.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class AttachmentSnapshotRepository {

    @Qualifier("attachmentObjectMapper") private final ObjectMapper objectMapper;
    private final JdbcClient                                       jdbcClient;

    @SneakyThrows
    public void insert(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String[] urls, @NonNull List<AttachmentMediaChange> changes, @NonNull Long actorId) {
        jdbcClient.sql("""
                        INSERT INTO attachment_snapshot
                            (entity_type, entity_id, attachment_urls, changes_summary, changed_by_actor_id, created_at)
                        VALUES (:entityType, :entityId, :urls, :changes, :actorId, NOW())
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("entityType", entityType.name())
                          .addValue("entityId",   entityId)
                          .addValue("urls",        urls)
                          .addValue("changes",     objectMapper.writeValueAsString(changes), Types.OTHER)
                          .addValue("actorId",     actorId))
                  .update();
    }

    public List<String> getPrevUrls(@NonNull EntityType entityType, @NonNull Long entityId) {
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

    public String[] getUrlsAtVersion(@NonNull EntityType entityType, @NonNull Long entityId, int version) {
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

    public Optional<List<String>> getUrlsForSnapshot(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long snapshotId) {
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

    @SneakyThrows
    public Optional<List<AttachmentMediaChange>> findChangesBySnapshotId(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long snapshotId) {
        Optional<String> json = jdbcClient.sql("""
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
        return json.isEmpty() ? Optional.empty()
                : Optional.of(objectMapper.readValue(json.get(), new TypeReference<>() {}));
    }

    @SneakyThrows
    public Optional<List<AttachmentMediaChange>> findChangesByVersion(@NonNull EntityType entityType, @NonNull Long entityId, int version) {
        Optional<String> json = jdbcClient.sql("""
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
        return json.isEmpty() ? Optional.empty()
                : Optional.of(objectMapper.readValue(json.get(), new TypeReference<>() {}));
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
