package org.ost.attachment.repository;

import org.ost.attachment.entities.Attachment;
import org.ost.attachment.storage.ConditionalOnAttachmentEnabled;
import org.ost.platform.core.model.EntityType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnAttachmentEnabled
public class AttachmentRepository {

    public record MediaStats(String mainUrl, String mainContentType, int count) {}

    private static final String TABLE = "attachment";

    private static final String WHERE_ACTIVE_BY_ENTITY =
            " WHERE entity_type = :entityType AND entity_id = :entityId AND deleted_at IS NULL";

    private static final String SET_DELETED_NOW =
            " SET deleted_at = NOW(), deleted_by_actor_id = :actorId";

    private static final String SELECT_ACTIVE_BY_ENTITY =
            "SELECT * FROM " + TABLE + WHERE_ACTIVE_BY_ENTITY;

    private static final String SELECT_ACTIVE_URLS =
            "SELECT url FROM " + TABLE + WHERE_ACTIVE_BY_ENTITY;

    private static final String SELECT_MAIN_MEDIA =
            "SELECT url, content_type FROM " + TABLE + WHERE_ACTIVE_BY_ENTITY + " ORDER BY created_at ASC LIMIT 1";

    private static final String COUNT_ACTIVE =
            "SELECT COUNT(*) FROM " + TABLE + WHERE_ACTIVE_BY_ENTITY;

    private static final String FIND_URLS_DELETED_OLDER_THAN =
            "SELECT url FROM " + TABLE +
            " WHERE deleted_at < NOW() - MAKE_INTERVAL(days => :days)" +
            " AND content_type NOT IN ('video/youtube', 'video/embed')";

    private static final String SOFT_DELETE =
            "UPDATE " + TABLE + SET_DELETED_NOW + " WHERE id = :id";

    private static final String SOFT_DELETE_ALL =
            "UPDATE " + TABLE + SET_DELETED_NOW + WHERE_ACTIVE_BY_ENTITY;

    private static final String RESTORE_UNDELETE =
            "UPDATE " + TABLE + " SET deleted_at = NULL, deleted_by_actor_id = NULL" +
            " WHERE entity_type = :entityType AND entity_id = :entityId AND url = ANY(:urls)";

    private static final String RESTORE_MARK_DELETED =
            "UPDATE " + TABLE + SET_DELETED_NOW +
            " WHERE entity_type = :entityType AND entity_id = :entityId" +
            " AND deleted_at IS NULL AND NOT (url = ANY(:urls))";

    private static final String DELETE_BY_URLS =
            "DELETE FROM " + TABLE + " WHERE url IN (:urls)";

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

    AttachmentRepository(JdbcClient jdbcClient, AttachmentCrudRepository crud) {
        this.jdbcClient = jdbcClient;
        this.crud = crud;
    }

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
        jdbcClient.sql(SOFT_DELETE)
                  .paramSource(new MapSqlParameterSource().addValue("id", id).addValue("actorId", actorId))
                  .update();
    }

    public void softDeleteAll(EntityType entityType, Long entityId, Long actorId) {
        jdbcClient.sql(SOFT_DELETE_ALL).paramSource(entityActorParams(entityType, entityId, actorId)).update();
    }

    public void restoreDeleteAll(EntityType entityType, Long entityId, Long actorId) {
        jdbcClient.sql(SOFT_DELETE_ALL).paramSource(entityActorParams(entityType, entityId, actorId)).update();
    }

    public void restoreUndelete(EntityType entityType, Long entityId, String[] urls) {
        jdbcClient.sql(RESTORE_UNDELETE)
                  .paramSource(entityParams(entityType, entityId).addValue("urls", urls))
                  .update();
    }

    public void restoreMarkDeleted(EntityType entityType, Long entityId, Long actorId, String[] urls) {
        jdbcClient.sql(RESTORE_MARK_DELETED)
                  .paramSource(entityActorParams(entityType, entityId, actorId).addValue("urls", urls))
                  .update();
    }

    public List<Attachment> getByEntityId(EntityType entityType, Long entityId) {
        return jdbcClient.sql(SELECT_ACTIVE_BY_ENTITY)
                         .paramSource(entityParams(entityType, entityId))
                         .query(ROW_MAPPER)
                         .list();
    }

    public List<String> getActiveUrls(EntityType entityType, Long entityId) {
        return jdbcClient.sql(SELECT_ACTIVE_URLS)
                         .paramSource(entityParams(entityType, entityId))
                         .query(String.class)
                         .list();
    }

    public List<String> findUrlsDeletedOlderThan(int days) {
        return jdbcClient.sql(FIND_URLS_DELETED_OLDER_THAN)
                         .paramSource(new MapSqlParameterSource("days", days))
                         .query(String.class)
                         .list();
    }

    public int deleteByUrls(List<String> urls) {
        return jdbcClient.sql(DELETE_BY_URLS)
                         .paramSource(new MapSqlParameterSource("urls", urls))
                         .update();
    }

    public MediaStats loadMediaStats(EntityType entityType, Long entityId) {
        record Row(String url, String contentType) {}
        var params = entityParams(entityType, entityId);
        var main = jdbcClient.sql(SELECT_MAIN_MEDIA)
                             .paramSource(params)
                             .query((rs, _) -> new Row(rs.getString("url"), rs.getString("content_type")))
                             .optional();
        int count = jdbcClient.sql(COUNT_ACTIVE)
                              .paramSource(params)
                              .query(Integer.class)
                              .optional()
                              .orElse(0);
        return main
                .map(m -> new MediaStats(m.url(), m.contentType(), count))
                .orElse(new MediaStats(null, null, count));
    }

    private static MapSqlParameterSource entityParams(EntityType entityType, Long entityId) {
        return new MapSqlParameterSource().addValue("entityType", entityType.name()).addValue("entityId", entityId);
    }

    private static MapSqlParameterSource entityActorParams(EntityType entityType, Long entityId, Long actorId) {
        return entityParams(entityType, entityId).addValue("actorId", actorId);
    }
}
