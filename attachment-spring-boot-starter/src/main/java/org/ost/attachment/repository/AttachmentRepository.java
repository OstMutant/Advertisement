package org.ost.attachment.repository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.attachment.entities.Attachment;
import org.ost.platform.core.model.EntityType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class AttachmentRepository {

    public record MediaStats(String mainUrl, String mainContentType, int count) {}

    public record DeletableAttachment(String url, String contentType) {}

    private static final RowMapper<Attachment> ROW_MAPPER = (rs, _) -> {
        String typeName = rs.getString("entity_type");
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        return Attachment.builder()
                .id(rs.getObject("id", Long.class))
                .entityType(typeName == null ? null : EntityType.valueOf(typeName))
                .entityId(rs.getObject("entity_id", Long.class))
                .url(rs.getString("url"))
                .filename(rs.getString("filename"))
                .contentType(rs.getString("content_type"))
                .size(rs.getObject("size", Long.class))
                .createdAt(createdAt != null ? createdAt.toInstant() : null)
                .deletedAt(deletedAt != null ? deletedAt.toInstant() : null)
                .deletedByActorId(rs.getObject("deleted_by_actor_id", Long.class))
                .build();
    };

    private final JdbcClient jdbcClient;
    private final AttachmentCrudRepository crud;

    public Attachment save(@NonNull Attachment attachment) {
        return crud.save(attachment);
    }

    public Iterable<Attachment> saveAll(@NonNull Iterable<Attachment> attachments) {
        return crud.saveAll(attachments);
    }

    public Optional<Attachment> findById(@NonNull Long id) {
        return crud.findById(id);
    }

    public void softDelete(@NonNull Long id, @NonNull Long actorId) {
        jdbcClient.sql("UPDATE attachment SET deleted_at = NOW(), deleted_by_actor_id = :actorId WHERE id = :id")
                  .paramSource(new MapSqlParameterSource().addValue("id", id).addValue("actorId", actorId))
                  .update();
    }

    public void softDeleteAll(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long actorId) {
        jdbcClient.sql("""
                        UPDATE attachment SET deleted_at = NOW(), deleted_by_actor_id = :actorId
                        WHERE entity_type = :entityType AND entity_id = :entityId AND deleted_at IS NULL
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("entityType", entityType.name())
                          .addValue("entityId",   entityId)
                          .addValue("actorId",        actorId))
                  .update();
    }

    public void restoreUndelete(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String[] urls) {
        jdbcClient.sql("""
                        UPDATE attachment SET deleted_at = NULL, deleted_by_actor_id = NULL
                        WHERE entity_type = :entityType AND entity_id = :entityId AND url = ANY(:urls)
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("entityType", entityType.name())
                          .addValue("entityId",   entityId)
                          .addValue("urls",            urls))
                  .update();
    }

    public void restoreMarkDeleted(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long actorId, @NonNull String[] urls) {
        jdbcClient.sql("""
                        UPDATE attachment SET deleted_at = NOW(), deleted_by_actor_id = :actorId
                        WHERE entity_type = :entityType AND entity_id = :entityId
                          AND deleted_at IS NULL AND NOT (url = ANY(:urls))
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("entityType", entityType.name())
                          .addValue("entityId",   entityId)
                          .addValue("actorId",        actorId)
                          .addValue("urls",            urls))
                  .update();
    }

    public List<Attachment> findByEntityAndUrls(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String[] urls) {
        return jdbcClient.sql("""
                        SELECT id, entity_type, entity_id, url, filename, content_type, size,
                               created_at, deleted_at, deleted_by_actor_id
                        FROM attachment
                        WHERE entity_type = :entityType AND entity_id = :entityId AND url = ANY(:urls)
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId)
                                 .addValue("urls",       urls))
                         .query(ROW_MAPPER)
                         .list();
    }

    public List<Attachment> getByEntityId(@NonNull EntityType entityType, @NonNull Long entityId) {
        return jdbcClient.sql("""
                        SELECT id, entity_type, entity_id, url, filename, content_type, size,
                               created_at, deleted_at, deleted_by_actor_id
                        FROM attachment
                        WHERE entity_type = :entityType AND entity_id = :entityId AND deleted_at IS NULL
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId))
                         .query(ROW_MAPPER)
                         .list();
    }

    public List<String> getActiveUrls(@NonNull EntityType entityType, @NonNull Long entityId) {
        return jdbcClient.sql("""
                        SELECT url FROM attachment
                        WHERE entity_type = :entityType AND entity_id = :entityId AND deleted_at IS NULL
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId))
                         .query(String.class)
                         .list();
    }

    // includes videos too -- cleanup service decides which get an S3 delete
    public List<DeletableAttachment> findUrlsDeletedOlderThan(int days) {
        return jdbcClient.sql("""
                        SELECT url, content_type FROM attachment
                        WHERE deleted_at < NOW() - MAKE_INTERVAL(days => :days)
                        """)
                         .paramSource(new MapSqlParameterSource("days", days))
                         .query((rs, _) -> new DeletableAttachment(rs.getString("url"), rs.getString("content_type")))
                         .list();
    }

    // re-checks deleted_at + RETURNING url so a concurrently-restored row survives
    public List<String> deleteByUrls(@NonNull List<String> urls) {
        // Array bind, not a List -- avoids IN(:list)'s unbounded placeholder expansion (improvement-054).
        return jdbcClient.sql("DELETE FROM attachment WHERE url = ANY(:urls) AND deleted_at IS NOT NULL RETURNING url")
                         .paramSource(new MapSqlParameterSource("urls", urls.toArray(new String[0])))
                         .query(String.class)
                         .list();
    }

    // Subset of urls that have a matching row (active or soft-deleted) -- used by the orphan sweep.
    public Set<String> findExistingUrls(@NonNull Collection<String> urls) {
        return new HashSet<>(jdbcClient.sql("SELECT url FROM attachment WHERE url = ANY(:urls)")
                         .paramSource(new MapSqlParameterSource("urls", urls.toArray(new String[0])))
                         .query(String.class)
                         .list());
    }

    public MediaStats loadMediaStats(@NonNull EntityType entityType, @NonNull Long entityId) {
        return jdbcClient.sql("""
                        SELECT url, content_type, COUNT(*) OVER () AS total_count
                        FROM attachment
                        WHERE entity_type = :entityType AND entity_id = :entityId AND deleted_at IS NULL
                        ORDER BY created_at ASC, id ASC
                        LIMIT 1
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId))
                         .query((rs, _) -> new MediaStats(rs.getString("url"), rs.getString("content_type"), rs.getInt("total_count")))
                         .optional()
                         .orElse(new MediaStats(null, null, 0));
    }

    public Map<Long, MediaStats> loadMediaStats(@NonNull EntityType entityType, @NonNull Set<Long> entityIds) {
        return jdbcClient.sql("""
                        SELECT entity_id, url, content_type, cnt FROM (
                            SELECT entity_id, url, content_type,
                                   COUNT(*) OVER (PARTITION BY entity_id) AS cnt,
                                   ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at ASC, id ASC) AS rn
                            FROM attachment
                            WHERE entity_type = :entityType AND entity_id = ANY(:entityIds) AND deleted_at IS NULL
                        ) ranked
                        WHERE rn = 1
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityIds", entityIds.toArray(new Long[0])))
                         .query((rs, _) -> Map.entry(rs.getObject("entity_id", Long.class),
                                 new MediaStats(rs.getString("url"), rs.getString("content_type"), rs.getInt("cnt"))))
                         .list()
                         .stream()
                         .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
