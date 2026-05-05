package org.ost.attachment.repository;

import lombok.RequiredArgsConstructor;
import org.ost.attachment.entity.Attachment;
import org.ost.sqlengine.writer.SqlWriteCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AttachmentRepository {

    public record MediaStats(String mainUrl, int count) {}

    private static final AttachmentDescriptor PROJECTION = new AttachmentDescriptor();

    private static final SqlWriteCommand INSERT = SqlWriteCommand.of(
            "INSERT INTO " + AttachmentDescriptor.Write.TABLE +
            " (entity_type, entity_id, url, filename, content_type, size, created_at)" +
            " VALUES ('ADVERTISEMENT', :entityId, :url, :filename, :contentType, :size, NOW())" +
            " RETURNING " + AttachmentDescriptor.ID.columnName()
    );

    private static final SqlWriteCommand SOFT_DELETE = SqlWriteCommand.of(
            "UPDATE " + AttachmentDescriptor.Write.TABLE +
            " SET "   + AttachmentDescriptor.Write.DELETED_AT + " = NOW()," +
            " "       + AttachmentDescriptor.Write.DELETED_BY_USER_ID + " = :deletedBy" +
            " WHERE " + AttachmentDescriptor.ID.columnName() + " = :id"
    );

    private static final SqlWriteCommand SOFT_DELETE_ALL = SqlWriteCommand.of(
            "UPDATE " + AttachmentDescriptor.Write.TABLE +
            " SET "   + AttachmentDescriptor.Write.DELETED_AT + " = NOW()," +
            " "       + AttachmentDescriptor.Write.DELETED_BY_USER_ID + " = :deletedBy" +
            " WHERE entity_type = 'ADVERTISEMENT'" +
            " AND "  + AttachmentDescriptor.Write.ENTITY_ID + " = :entityId" +
            " AND "  + AttachmentDescriptor.Write.DELETED_AT + " IS NULL"
    );

    private static final SqlWriteCommand RESTORE_DELETE_ALL = SqlWriteCommand.of(
            "UPDATE " + AttachmentDescriptor.Write.TABLE +
            " SET "   + AttachmentDescriptor.Write.DELETED_AT + " = NOW()," +
            " "       + AttachmentDescriptor.Write.DELETED_BY_USER_ID + " = :userId" +
            " WHERE entity_type = 'ADVERTISEMENT'" +
            " AND "  + AttachmentDescriptor.Write.ENTITY_ID + " = :adId" +
            " AND "  + AttachmentDescriptor.Write.DELETED_AT + " IS NULL"
    );

    private static final SqlWriteCommand RESTORE_UNDELETE = SqlWriteCommand.of(
            "UPDATE " + AttachmentDescriptor.Write.TABLE +
            " SET "   + AttachmentDescriptor.Write.DELETED_AT + " = NULL," +
            " "       + AttachmentDescriptor.Write.DELETED_BY_USER_ID + " = NULL" +
            " WHERE entity_type = 'ADVERTISEMENT'" +
            " AND "  + AttachmentDescriptor.Write.ENTITY_ID + " = :adId" +
            " AND "  + AttachmentDescriptor.Write.URL + " = ANY(:urls)"
    );

    private static final SqlWriteCommand RESTORE_MARK_DELETED = SqlWriteCommand.of(
            "UPDATE " + AttachmentDescriptor.Write.TABLE +
            " SET "   + AttachmentDescriptor.Write.DELETED_AT + " = NOW()," +
            " "       + AttachmentDescriptor.Write.DELETED_BY_USER_ID + " = :userId" +
            " WHERE entity_type = 'ADVERTISEMENT'" +
            " AND "  + AttachmentDescriptor.Write.ENTITY_ID + " = :adId" +
            " AND "  + AttachmentDescriptor.Write.DELETED_AT + " IS NULL" +
            " AND NOT (" + AttachmentDescriptor.Write.URL + " = ANY(:urls))"
    );

    private final JdbcClient jdbcClient;

    public Attachment insert(Long entityId, String url, String filename, String contentType, long size) {
        Long id = INSERT.executeAndReturnKey(jdbcClient,
                new MapSqlParameterSource()
                        .addValue(Attachment.Fields.entityId,    entityId)
                        .addValue(Attachment.Fields.url,         url)
                        .addValue(Attachment.Fields.filename,    filename)
                        .addValue(Attachment.Fields.contentType, contentType)
                        .addValue(Attachment.Fields.size,        size));
        return Attachment.builder()
                .id(id).entityId(entityId).url(url).filename(filename)
                .contentType(contentType).size(size).build();
    }

    public void softDelete(Long id, Long deletedBy) {
        SOFT_DELETE.execute(jdbcClient,
                new MapSqlParameterSource().addValue(Attachment.Fields.id, id).addValue("deletedBy", deletedBy));
    }

    public void softDeleteAll(Long entityId, Long deletedByUserId) {
        SOFT_DELETE_ALL.execute(jdbcClient,
                new MapSqlParameterSource().addValue("deletedBy", deletedByUserId).addValue(Attachment.Fields.entityId, entityId));
    }

    public void restoreDeleteAll(Long adId, Long userId) {
        RESTORE_DELETE_ALL.execute(jdbcClient,
                new MapSqlParameterSource().addValue("userId", userId).addValue("adId", adId));
    }

    public void restoreUndelete(Long adId, String[] urls) {
        RESTORE_UNDELETE.execute(jdbcClient,
                new MapSqlParameterSource().addValue("adId", adId).addValue("urls", urls));
    }

    public void restoreMarkDeleted(Long adId, Long userId, String[] urls) {
        RESTORE_MARK_DELETED.execute(jdbcClient,
                new MapSqlParameterSource().addValue("adId", adId).addValue("userId", userId).addValue("urls", urls));
    }

    public Attachment findById(Long id) {
        return jdbcClient.sql("SELECT * FROM " + AttachmentDescriptor.TABLE + " WHERE id = :id")
                .paramSource(new MapSqlParameterSource(Attachment.Fields.id, id))
                .query(PROJECTION).optional().orElse(null);
    }

    public List<Attachment> getByEntityId(Long entityId) {
        return jdbcClient.sql(
                "SELECT * FROM " + AttachmentDescriptor.TABLE +
                " WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :entityId AND deleted_at IS NULL")
                .paramSource(new MapSqlParameterSource(Attachment.Fields.entityId, entityId))
                .query(PROJECTION).list();
    }

    public List<String> getActiveUrls(Long entityId) {
        return jdbcClient.sql(
                "SELECT " + AttachmentDescriptor.Write.URL +
                " FROM " + AttachmentDescriptor.TABLE +
                " WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :entityId AND deleted_at IS NULL")
                .paramSource(new MapSqlParameterSource(Attachment.Fields.entityId, entityId))
                .query(String.class).list();
    }

    public MediaStats loadMediaStats(Long entityId) {
        String mainUrl = jdbcClient.sql(
                "SELECT " + AttachmentDescriptor.Write.URL +
                " FROM " + AttachmentDescriptor.TABLE +
                " WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :entityId" +
                " AND deleted_at IS NULL ORDER BY created_at ASC LIMIT 1")
                .paramSource(new MapSqlParameterSource(Attachment.Fields.entityId, entityId))
                .query((rs, n) -> rs.getString(AttachmentDescriptor.Write.URL))
                .optional().orElse(null);
        int count = jdbcClient.sql(
                "SELECT COUNT(*) FROM " + AttachmentDescriptor.TABLE +
                " WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :entityId AND deleted_at IS NULL")
                .paramSource(new MapSqlParameterSource(Attachment.Fields.entityId, entityId))
                .query(Integer.class).single();
        return new MediaStats(mainUrl, count);
    }
}
