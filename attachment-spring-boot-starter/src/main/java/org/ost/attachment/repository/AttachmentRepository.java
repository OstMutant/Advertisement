package org.ost.attachment.repository;

import lombok.RequiredArgsConstructor;
import org.ost.attachment.entities.Attachment;
import org.ost.platform.attachment.storage.ConditionalOnStorageEnabled;
import org.ost.platform.core.model.EntityType;
import org.ost.sqlengine.writer.SqlWriteCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@ConditionalOnStorageEnabled
@RequiredArgsConstructor
public class AttachmentRepository {

    public record MediaStats(String mainUrl, String mainContentType, int count) {}

    private static final AttachmentDescriptor PROJECTION = new AttachmentDescriptor();

    private static final SqlWriteCommand INSERT = SqlWriteCommand.of(
            "INSERT INTO " + AttachmentDescriptor.Write.TABLE +
            " (entity_type, entity_id, url, filename, content_type, size, created_at)" +
            " VALUES (:entityType, :entityId, :url, :filename, :contentType, :size, NOW())" +
            " RETURNING " + AttachmentDescriptor.ID.columnName()
    );

    private static final String WHERE_ACTIVE_BY_ENTITY =
            " WHERE entity_type = :entityType AND entity_id = :entityId AND deleted_at IS NULL";

    private static final String UPDATE_TABLE =
            "UPDATE " + AttachmentDescriptor.Write.TABLE;

    private static final String SET_DELETED_NOW =
            " SET " + AttachmentDescriptor.Write.DELETED_AT + " = NOW()," +
            " "     + AttachmentDescriptor.Write.DELETED_BY_ACTOR_ID;

    private static final String SQL_SELECT = "SELECT ";
    private static final String SQL_FROM   = " FROM ";

    private static final String SELECT_URL_FROM_TABLE =
            SQL_SELECT + AttachmentDescriptor.Write.URL + SQL_FROM + AttachmentDescriptor.TABLE;

    private static final String SELECT_URL_FROM_WRITE_TABLE =
            SQL_SELECT + AttachmentDescriptor.Write.URL + SQL_FROM + AttachmentDescriptor.Write.TABLE;

    private static final SqlWriteCommand SOFT_DELETE = SqlWriteCommand.of(
            UPDATE_TABLE + SET_DELETED_NOW + " = :actorId" +
            " WHERE " + AttachmentDescriptor.ID.columnName() + " = :id"
    );

    private static final SqlWriteCommand SOFT_DELETE_ALL = SqlWriteCommand.of(
            UPDATE_TABLE + SET_DELETED_NOW + " = :actorId" +
            " WHERE " + AttachmentDescriptor.Write.ENTITY_TYPE + " = :entityType" +
            " AND "   + AttachmentDescriptor.Write.ENTITY_ID + " = :entityId" +
            " AND "   + AttachmentDescriptor.Write.DELETED_AT + " IS NULL"
    );

    private static final SqlWriteCommand RESTORE_DELETE_ALL = SqlWriteCommand.of(
            UPDATE_TABLE + SET_DELETED_NOW + " = :actorId" +
            " WHERE " + AttachmentDescriptor.Write.ENTITY_TYPE + " = :entityType" +
            " AND "   + AttachmentDescriptor.Write.ENTITY_ID + " = :entityId" +
            " AND "   + AttachmentDescriptor.Write.DELETED_AT + " IS NULL"
    );

    private static final SqlWriteCommand RESTORE_UNDELETE = SqlWriteCommand.of(
            UPDATE_TABLE +
            " SET "   + AttachmentDescriptor.Write.DELETED_AT + " = NULL," +
            " "       + AttachmentDescriptor.Write.DELETED_BY_ACTOR_ID + " = NULL" +
            " WHERE " + AttachmentDescriptor.Write.ENTITY_TYPE + " = :entityType" +
            " AND "   + AttachmentDescriptor.Write.ENTITY_ID + " = :entityId" +
            " AND "   + AttachmentDescriptor.Write.URL + " = ANY(:urls)"
    );

    private static final SqlWriteCommand RESTORE_MARK_DELETED = SqlWriteCommand.of(
            UPDATE_TABLE + SET_DELETED_NOW + " = :actorId" +
            " WHERE " + AttachmentDescriptor.Write.ENTITY_TYPE + " = :entityType" +
            " AND "   + AttachmentDescriptor.Write.ENTITY_ID + " = :entityId" +
            " AND "   + AttachmentDescriptor.Write.DELETED_AT + " IS NULL" +
            " AND NOT (" + AttachmentDescriptor.Write.URL + " = ANY(:urls))"
    );

    private final JdbcClient jdbcClient;

    public Attachment insert(EntityType entityType, Long entityId, String url, String filename, String contentType, long size) {
        Long id = INSERT.executeAndReturnKey(jdbcClient,
                new MapSqlParameterSource()
                        .addValue(Attachment.Fields.entityType,  entityType.name())
                        .addValue(Attachment.Fields.entityId,    entityId)
                        .addValue(Attachment.Fields.url,         url)
                        .addValue(Attachment.Fields.filename,    filename)
                        .addValue(Attachment.Fields.contentType, contentType)
                        .addValue(Attachment.Fields.size,        size));
        return Attachment.builder()
                .id(id).entityType(entityType).entityId(entityId).url(url).filename(filename)
                .contentType(contentType).size(size).build();
    }

    public void softDelete(Long id, Long actorId) {
        SOFT_DELETE.execute(jdbcClient,
                new MapSqlParameterSource().addValue(Attachment.Fields.id, id).addValue("actorId", actorId));
    }

    public void softDeleteAll(EntityType entityType, Long entityId, Long actorId) {
        SOFT_DELETE_ALL.execute(jdbcClient,
                entityParams(entityType, entityId).addValue("actorId", actorId));
    }

    public void restoreDeleteAll(EntityType entityType, Long entityId, Long actorId) {
        RESTORE_DELETE_ALL.execute(jdbcClient,
                entityParams(entityType, entityId).addValue("actorId", actorId));
    }

    public void restoreUndelete(EntityType entityType, Long entityId, String[] urls) {
        RESTORE_UNDELETE.execute(jdbcClient,
                entityParams(entityType, entityId).addValue("urls", urls));
    }

    public void restoreMarkDeleted(EntityType entityType, Long entityId, Long actorId, String[] urls) {
        RESTORE_MARK_DELETED.execute(jdbcClient,
                entityParams(entityType, entityId).addValue("actorId", actorId).addValue("urls", urls));
    }

    public Attachment findById(Long id) {
        return jdbcClient.sql("SELECT * FROM " + AttachmentDescriptor.TABLE + " WHERE id = :id")
                .paramSource(new MapSqlParameterSource(Attachment.Fields.id, id))
                .query(PROJECTION).optional().orElse(null);
    }

    public List<Attachment> getByEntityId(EntityType entityType, Long entityId) {
        return jdbcClient.sql(
                "SELECT * FROM " + AttachmentDescriptor.TABLE + WHERE_ACTIVE_BY_ENTITY)
                .paramSource(entityParams(entityType, entityId))
                .query(PROJECTION).list();
    }

    public List<String> getActiveUrls(EntityType entityType, Long entityId) {
        return jdbcClient.sql(SELECT_URL_FROM_TABLE + WHERE_ACTIVE_BY_ENTITY)
                .paramSource(entityParams(entityType, entityId))
                .query(String.class).list();
    }

    public List<String> findUrlsDeletedOlderThan(int days) {
        return jdbcClient.sql(
                SELECT_URL_FROM_WRITE_TABLE +
                " WHERE deleted_at < NOW() - MAKE_INTERVAL(days => :days)" +
                " AND content_type NOT IN ('video/youtube', 'video/embed')")
                .paramSource(new MapSqlParameterSource("days", days))
                .query(String.class).list();
    }

    public int deleteByUrls(List<String> urls) {
        return jdbcClient.sql(
                "DELETE FROM " + AttachmentDescriptor.Write.TABLE + " WHERE url IN (:urls)")
                .paramSource(new MapSqlParameterSource("urls", urls))
                .update();
    }

    public MediaStats loadMediaStats(EntityType entityType, Long entityId) {
        record Row(String url, String contentType) {}
        var params = entityParams(entityType, entityId);
        var main = jdbcClient.sql(
                SQL_SELECT + AttachmentDescriptor.Write.URL + ", " + AttachmentDescriptor.Write.CONTENT_TYPE +
                SQL_FROM + AttachmentDescriptor.TABLE + WHERE_ACTIVE_BY_ENTITY +
                " ORDER BY created_at ASC LIMIT 1")
                .paramSource(params)
                .query((rs, n) -> new Row(rs.getString(AttachmentDescriptor.Write.URL),
                                          rs.getString(AttachmentDescriptor.Write.CONTENT_TYPE)))
                .optional();
        Integer countVal = jdbcClient.sql(
                "SELECT COUNT(*) FROM " + AttachmentDescriptor.TABLE + WHERE_ACTIVE_BY_ENTITY)
                .paramSource(params)
                .query(Integer.class).single();
        int count = countVal != null ? countVal : 0;
        return main
                .map(m -> new MediaStats(m.url(), m.contentType(), count))
                .orElse(new MediaStats(null, null, count));
    }

    private static MapSqlParameterSource entityParams(EntityType entityType, Long entityId) {
        return new MapSqlParameterSource()
                .addValue(Attachment.Fields.entityType, entityType.name())
                .addValue(Attachment.Fields.entityId,   entityId);
    }
}
