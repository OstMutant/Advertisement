package org.ost.attachment.repository;

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
public class AttachmentSnapshotRepository {

    private static final String TABLE = "attachment_snapshot";

    private static final String WHERE_BY_ENTITY =
            " WHERE entity_type = :entityType AND entity_id = :entityId";

    private static final String NEXT_AUDIT_TS_BY_VERSION =
            "SELECT al.created_at FROM (" +
            "    SELECT created_at," +
            "           ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at) AS rn" +
            "    FROM audit_log WHERE entity_type = :entityType AND entity_id = :entityId" +
            ") al WHERE al.rn = :version + 1";

    private static final String THIS_AUDIT_TS_BY_VERSION =
            "SELECT al.created_at FROM (" +
            "    SELECT created_at," +
            "           ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at) AS rn" +
            "    FROM audit_log WHERE entity_type = :entityType AND entity_id = :entityId" +
            ") al WHERE al.rn = :version";

    private static final String NEXT_AUDIT_TS_AFTER_SNAPSHOT =
            "SELECT created_at FROM audit_log" +
            " WHERE entity_type = :entityType AND entity_id = :entityId" +
            "   AND created_at > (SELECT created_at FROM audit_log WHERE id = :snapshotId)" +
            " ORDER BY created_at ASC LIMIT 1";

    private static final String SELECT_PREV_URLS =
            "SELECT attachment_urls FROM " + TABLE + WHERE_BY_ENTITY + " ORDER BY created_at DESC LIMIT 1";

    private static final String SELECT_URLS_AT_VERSION =
            "SELECT attachment_urls FROM " + TABLE + WHERE_BY_ENTITY +
            "   AND created_at < COALESCE((" + NEXT_AUDIT_TS_BY_VERSION + "), 'infinity'::timestamptz)" +
            " ORDER BY created_at DESC LIMIT 1";

    private static final String SELECT_URLS_FOR_SNAPSHOT =
            "SELECT attachment_urls FROM " + TABLE + WHERE_BY_ENTITY +
            "   AND created_at < COALESCE((" + NEXT_AUDIT_TS_AFTER_SNAPSHOT + "), 'infinity'::timestamptz)" +
            " ORDER BY created_at DESC LIMIT 1";

    private static final String SELECT_CHANGES_JSON_FOR_SNAPSHOT =
            "SELECT changes_summary::text AS changes_summary FROM " + TABLE + WHERE_BY_ENTITY +
            "   AND created_at >= (SELECT created_at FROM audit_log WHERE id = :snapshotId)" +
            "   AND created_at < COALESCE((" + NEXT_AUDIT_TS_AFTER_SNAPSHOT + "), 'infinity'::timestamptz)" +
            "   AND changes_summary IS NOT NULL ORDER BY created_at ASC LIMIT 1";

    private static final String SELECT_CHANGES_JSON_AT_VERSION =
            "SELECT changes_summary::text AS changes_summary FROM " + TABLE + WHERE_BY_ENTITY +
            "   AND created_at >= (" + THIS_AUDIT_TS_BY_VERSION + ")" +
            "   AND created_at < COALESCE((" + NEXT_AUDIT_TS_BY_VERSION + "), 'infinity'::timestamptz)" +
            "   AND changes_summary IS NOT NULL ORDER BY created_at ASC LIMIT 1";

    private static final String INSERT =
            "INSERT INTO " + TABLE +
            " (entity_type, entity_id, attachment_urls, changes_summary, changed_by_actor_id, created_at)" +
            " VALUES (:entityType, :entityId, :urls, CAST(:changes AS JSONB), :actorId, NOW())";

    private final JdbcClient jdbcClient;

    public AttachmentSnapshotRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(EntityType entityType, Long entityId, String[] urls, String changesJson, Long actorId) {
        jdbcClient.sql(INSERT)
                  .paramSource(entityParams(entityType, entityId)
                          .addValue("urls",    urls)
                          .addValue("changes", changesJson)
                          .addValue("actorId", actorId))
                  .update();
    }

    public List<String> getPrevUrls(EntityType entityType, Long entityId) {
        return jdbcClient.sql(SELECT_PREV_URLS)
                         .paramSource(entityParams(entityType, entityId))
                         .query((rs, _) -> extractUrls(rs))
                         .optional()
                         .orElse(List.of());
    }

    public String[] getUrlsAtVersion(EntityType entityType, Long entityId, int version) {
        return jdbcClient.sql(SELECT_URLS_AT_VERSION)
                         .paramSource(versionParams(entityType, entityId, version))
                         .query((rs, _) -> extractUrls(rs))
                         .optional()
                         .map(l -> l.toArray(new String[0]))
                         .orElse(new String[0]);
    }

    public Optional<List<String>> getUrlsForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return jdbcClient.sql(SELECT_URLS_FOR_SNAPSHOT)
                         .paramSource(snapshotParams(entityType, entityId, snapshotId))
                         .query((rs, _) -> extractUrls(rs))
                         .optional();
    }

    public Optional<String> getChangesJsonForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return jdbcClient.sql(SELECT_CHANGES_JSON_FOR_SNAPSHOT)
                         .paramSource(snapshotParams(entityType, entityId, snapshotId))
                         .query(String.class)
                         .optional();
    }

    public Optional<String> getChangesJson(EntityType entityType, Long entityId, int version) {
        return jdbcClient.sql(SELECT_CHANGES_JSON_AT_VERSION)
                         .paramSource(versionParams(entityType, entityId, version))
                         .query(String.class)
                         .optional();
    }

    private static MapSqlParameterSource entityParams(EntityType entityType, Long entityId) {
        return new MapSqlParameterSource().addValue("entityType", entityType.name()).addValue("entityId", entityId);
    }

    private static MapSqlParameterSource versionParams(EntityType entityType, Long entityId, int version) {
        return entityParams(entityType, entityId).addValue("version", version);
    }

    private static MapSqlParameterSource snapshotParams(EntityType entityType, Long entityId, Long snapshotId) {
        return entityParams(entityType, entityId).addValue("snapshotId", snapshotId);
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
