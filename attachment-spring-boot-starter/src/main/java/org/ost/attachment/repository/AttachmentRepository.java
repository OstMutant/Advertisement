package org.ost.attachment.repository;

import lombok.RequiredArgsConstructor;
import org.ost.attachment.entities.Attachment;
import org.ost.platform.core.model.EntityType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class AttachmentRepository {

    public record MediaStats(String mainUrl, String mainContentType, int count) {}

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

    public Attachment save(Attachment attachment) {
        return crud.save(attachment);
    }

    public Iterable<Attachment> saveAll(Iterable<Attachment> attachments) {
        return crud.saveAll(attachments);
    }

    public Optional<Attachment> findById(Long id) {
        return crud.findById(id);
    }

    public void softDelete(Long id, Long actorId) {
        jdbcClient.sql("UPDATE attachment SET deleted_at = NOW(), deleted_by_actor_id = :actorId WHERE id = :id")
                  .paramSource(new MapSqlParameterSource().addValue("id", id).addValue("actorId", actorId))
                  .update();
    }

    public void softDeleteAll(EntityType entityType, Long entityId, Long actorId) {
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

    public void restoreDeleteAll(EntityType entityType, Long entityId) {
        jdbcClient.sql("""
                        UPDATE attachment SET deleted_at = NULL, deleted_by_actor_id = NULL
                        WHERE entity_type = :entityType AND entity_id = :entityId AND deleted_at IS NOT NULL
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("entityType", entityType.name())
                          .addValue("entityId",   entityId))
                  .update();
    }

    public void restoreUndelete(EntityType entityType, Long entityId, String[] urls) {
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

    public void restoreMarkDeleted(EntityType entityType, Long entityId, Long actorId, String[] urls) {
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

    public List<Attachment> getByEntityId(EntityType entityType, Long entityId) {
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

    public List<String> getActiveUrls(EntityType entityType, Long entityId) {
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

    public List<String> findUrlsDeletedOlderThan(int days) {
        return jdbcClient.sql("""
                        SELECT url FROM attachment
                        WHERE deleted_at < NOW() - MAKE_INTERVAL(days => :days)
                          AND content_type NOT IN ('video/youtube', 'video/embed')
                        """)
                         .paramSource(new MapSqlParameterSource("days", days))
                         .query(String.class)
                         .list();
    }

    public int deleteByUrls(List<String> urls) {
        return jdbcClient.sql("DELETE FROM attachment WHERE url IN (:urls)")
                         .paramSource(new MapSqlParameterSource("urls", urls))
                         .update();
    }

    public MediaStats loadMediaStats(EntityType entityType, Long entityId) {
        record Row(String url, String contentType) {}
        var params = new MapSqlParameterSource()
                .addValue("entityType", entityType.name())
                .addValue("entityId",   entityId);
        var main = jdbcClient.sql("""
                        SELECT url, content_type FROM attachment
                        WHERE entity_type = :entityType AND entity_id = :entityId AND deleted_at IS NULL
                        ORDER BY created_at ASC LIMIT 1
                        """)
                             .paramSource(params)
                             .query((rs, _) -> new Row(rs.getString("url"), rs.getString("content_type")))
                             .optional();
        int count = jdbcClient.sql("""
                        SELECT COUNT(*) FROM attachment
                        WHERE entity_type = :entityType AND entity_id = :entityId AND deleted_at IS NULL
                        """)
                              .paramSource(params)
                              .query(Integer.class)
                              .optional()
                              .orElse(0);
        return main
                .map(m -> new MediaStats(m.url(), m.contentType(), count))
                .orElse(new MediaStats(null, null, count));
    }
}
