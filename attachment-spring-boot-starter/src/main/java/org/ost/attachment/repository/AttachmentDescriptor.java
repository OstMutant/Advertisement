package org.ost.attachment.repository;

import org.jetbrains.annotations.NotNull;
import org.ost.attachment.entities.Attachment;
import org.ost.platform.core.model.EntityType;
import org.ost.sqlengine.projection.SqlEntityProjection;
import org.ost.sqlengine.projection.SqlSelectField;
import org.ost.sqlengine.writer.SqlWriteCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.projection.SqlSelectFieldFactory.*;

public class AttachmentDescriptor extends SqlEntityProjection<Attachment> {

    public static final String TABLE  = "attachment";
    public static final String SOURCE = TABLE;

    public static final SqlSelectField<Long>    ID                  = longVal("id",                  "id");
    public static final SqlSelectField<String>  ENTITY_TYPE         = str("entity_type",             "entity_type");
    public static final SqlSelectField<Long>    ENTITY_ID           = longVal("entity_id",           "entity_id");
    public static final SqlSelectField<String>  URL                 = str("url",                     "url");
    public static final SqlSelectField<String>  FILENAME            = str("filename",                "filename");
    public static final SqlSelectField<String>  CONTENT_TYPE        = str("content_type",            "content_type");
    public static final SqlSelectField<Long>    SIZE                = longVal("size",                "size");
    public static final SqlSelectField<Instant> CREATED_AT          = instant("created_at",          "created_at");
    public static final SqlSelectField<Instant> DELETED_AT          = instant("deleted_at",          "deleted_at");
    public static final SqlSelectField<Long>    DELETED_BY_ACTOR_ID = longVal("deleted_by_actor_id", "deleted_by_actor_id");

    public static final class Write {
        private Write() {}
        public static final String TABLE               = AttachmentDescriptor.TABLE;
        public static final String ENTITY_TYPE         = AttachmentDescriptor.ENTITY_TYPE.columnName();
        public static final String ENTITY_ID           = AttachmentDescriptor.ENTITY_ID.columnName();
        public static final String URL                 = AttachmentDescriptor.URL.columnName();
        public static final String FILENAME            = AttachmentDescriptor.FILENAME.columnName();
        public static final String CONTENT_TYPE        = AttachmentDescriptor.CONTENT_TYPE.columnName();
        public static final String SIZE                = AttachmentDescriptor.SIZE.columnName();
        public static final String DELETED_AT          = AttachmentDescriptor.DELETED_AT.columnName();
        public static final String DELETED_BY_ACTOR_ID = AttachmentDescriptor.DELETED_BY_ACTOR_ID.columnName();
    }

    // -------- SQL --------

    private static final String WHERE_ACTIVE_BY_ENTITY =
            " WHERE " + Write.ENTITY_TYPE + " = :entityType" +
            " AND "   + Write.ENTITY_ID   + " = :entityId" +
            " AND "   + Write.DELETED_AT  + " IS NULL";

    private static final String SET_DELETED_NOW =
            " SET " + Write.DELETED_AT + " = NOW()," +
            " "     + Write.DELETED_BY_ACTOR_ID + " = :actorId";

    public static final SqlWriteCommand INSERT = SqlWriteCommand.of(
            "INSERT INTO " + Write.TABLE +
            " (" + Write.ENTITY_TYPE + ", " + Write.ENTITY_ID + ", " + Write.URL + ", " +
            Write.FILENAME + ", " + Write.CONTENT_TYPE + ", " + Write.SIZE + ", created_at)" +
            " VALUES (:entityType, :entityId, :url, :filename, :contentType, :size, NOW())" +
            " RETURNING " + ID.columnName());

    public static final SqlWriteCommand SOFT_DELETE = SqlWriteCommand.of(
            "UPDATE " + Write.TABLE + SET_DELETED_NOW +
            " WHERE " + ID.columnName() + " = :id");

    public static final SqlWriteCommand SOFT_DELETE_ALL = SqlWriteCommand.of(
            "UPDATE " + Write.TABLE + SET_DELETED_NOW +
            " WHERE " + Write.ENTITY_TYPE + " = :entityType" +
            " AND "   + Write.ENTITY_ID + " = :entityId" +
            " AND "   + Write.DELETED_AT + " IS NULL");

    /** Identical to SOFT_DELETE_ALL today; kept as a separate name for caller-side clarity. */
    public static final SqlWriteCommand RESTORE_DELETE_ALL = SOFT_DELETE_ALL;

    public static final SqlWriteCommand RESTORE_UNDELETE = SqlWriteCommand.of(
            "UPDATE " + Write.TABLE +
            " SET "   + Write.DELETED_AT + " = NULL," +
            " "       + Write.DELETED_BY_ACTOR_ID + " = NULL" +
            " WHERE " + Write.ENTITY_TYPE + " = :entityType" +
            " AND "   + Write.ENTITY_ID + " = :entityId" +
            " AND "   + Write.URL + " = ANY(:urls)");

    public static final SqlWriteCommand RESTORE_MARK_DELETED = SqlWriteCommand.of(
            "UPDATE " + Write.TABLE + SET_DELETED_NOW +
            " WHERE " + Write.ENTITY_TYPE + " = :entityType" +
            " AND "   + Write.ENTITY_ID + " = :entityId" +
            " AND "   + Write.DELETED_AT + " IS NULL" +
            " AND NOT (" + Write.URL + " = ANY(:urls))");

    public static final String FIND_BY_ID_SQL =
            "SELECT * FROM " + TABLE + " WHERE " + ID.columnName() + " = :id";

    public static final String SELECT_ACTIVE_BY_ENTITY_SQL =
            "SELECT * FROM " + TABLE + WHERE_ACTIVE_BY_ENTITY;

    public static final String SELECT_ACTIVE_URLS_SQL =
            "SELECT " + Write.URL + " FROM " + TABLE + WHERE_ACTIVE_BY_ENTITY;

    public static final String FIND_URLS_DELETED_OLDER_THAN_SQL =
            "SELECT " + Write.URL + " FROM " + Write.TABLE +
            " WHERE " + Write.DELETED_AT + " < NOW() - MAKE_INTERVAL(days => :days)" +
            " AND "   + Write.CONTENT_TYPE + " NOT IN ('video/youtube', 'video/embed')";

    public static final String DELETE_BY_URLS_SQL =
            "DELETE FROM " + Write.TABLE + " WHERE " + Write.URL + " IN (:urls)";

    public static final String SELECT_MAIN_MEDIA_SQL =
            "SELECT " + Write.URL + ", " + Write.CONTENT_TYPE +
            " FROM " + TABLE + WHERE_ACTIVE_BY_ENTITY +
            " ORDER BY created_at ASC LIMIT 1";

    public static final String COUNT_ACTIVE_SQL =
            "SELECT COUNT(*) FROM " + TABLE + WHERE_ACTIVE_BY_ENTITY;

    // -------- Param factories --------

    public static MapSqlParameterSource entityParams(EntityType entityType, Long entityId) {
        return new MapSqlParameterSource()
                .addValue("entityType", entityType.name())
                .addValue("entityId",   entityId);
    }

    public static MapSqlParameterSource insertParams(EntityType entityType, Long entityId,
                                                     String url, String filename,
                                                     String contentType, long size) {
        return new MapSqlParameterSource()
                .addValue("entityType",  entityType.name())
                .addValue("entityId",    entityId)
                .addValue("url",         url)
                .addValue("filename",    filename)
                .addValue("contentType", contentType)
                .addValue("size",        size);
    }

    public static MapSqlParameterSource softDeleteParams(Long id, Long actorId) {
        return new MapSqlParameterSource()
                .addValue("id",      id)
                .addValue("actorId", actorId);
    }

    public static MapSqlParameterSource softDeleteAllParams(EntityType entityType, Long entityId, Long actorId) {
        return entityParams(entityType, entityId).addValue("actorId", actorId);
    }

    public static MapSqlParameterSource restoreUndeleteParams(EntityType entityType, Long entityId, String[] urls) {
        return entityParams(entityType, entityId).addValue("urls", urls);
    }

    public static MapSqlParameterSource restoreMarkDeletedParams(EntityType entityType, Long entityId,
                                                                 Long actorId, String[] urls) {
        return entityParams(entityType, entityId)
                .addValue("actorId", actorId)
                .addValue("urls",    urls);
    }

    public static MapSqlParameterSource findByIdParams(Long id) {
        return new MapSqlParameterSource("id", id);
    }

    public static MapSqlParameterSource findUrlsDeletedOlderThanParams(int days) {
        return new MapSqlParameterSource("days", days);
    }

    public static MapSqlParameterSource deleteByUrlsParams(List<String> urls) {
        return new MapSqlParameterSource("urls", urls);
    }

    public AttachmentDescriptor() {
        super(List.of(ID, ENTITY_TYPE, ENTITY_ID, URL, FILENAME, CONTENT_TYPE, SIZE,
                      CREATED_AT, DELETED_AT, DELETED_BY_ACTOR_ID), SOURCE);
    }

    @Override
    public Attachment mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        String typeName = ENTITY_TYPE.extract(rs);
        return Attachment.builder()
                .id(ID.extract(rs))
                .entityType(typeName == null ? null : EntityType.valueOf(typeName))
                .entityId(ENTITY_ID.extract(rs))
                .url(URL.extract(rs))
                .filename(FILENAME.extract(rs))
                .contentType(CONTENT_TYPE.extract(rs))
                .size(SIZE.extract(rs))
                .createdAt(CREATED_AT.extract(rs))
                .deletedAt(DELETED_AT.extract(rs))
                .deletedByActorId(DELETED_BY_ACTOR_ID.extract(rs))
                .build();
    }
}
