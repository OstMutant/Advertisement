package org.ost.attachment.repository;

import org.ost.attachment.entities.Attachment;
import org.ost.platform.core.model.EntityType;
import org.ost.sqlengine.SqlEntityDescriptor;
import static org.ost.sqlengine.SqlEntityDescriptor.Params;
import org.ost.sqlengine.read.SqlEntityProjection;
import org.ost.sqlengine.common.SqlDescriptorField;
import org.ost.sqlengine.common.SqlCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.common.SqlDescriptorFieldFactory.*;

public final class AttachmentDescriptor implements SqlEntityDescriptor {

    public static final String TABLE  = "attachment";

    public static final SqlDescriptorField<Long>    ID                  = longVal("id",                  "id");
    public static final SqlDescriptorField<String>  ENTITY_TYPE         = str("entity_type",             "entity_type");
    public static final SqlDescriptorField<Long>    ENTITY_ID           = longVal("entity_id",           "entity_id");
    public static final SqlDescriptorField<String>  URL                 = str("url",                     "url");
    public static final SqlDescriptorField<String>  FILENAME            = str("filename",                "filename");
    public static final SqlDescriptorField<String>  CONTENT_TYPE        = str("content_type",            "content_type");
    public static final SqlDescriptorField<Long>    SIZE                = longVal("size",                "size");
    public static final SqlDescriptorField<Instant> CREATED_AT          = instant("created_at",          "created_at");
    public static final SqlDescriptorField<Instant> DELETED_AT          = instant("deleted_at",          "deleted_at");
    public static final SqlDescriptorField<Long>    DELETED_BY_ACTOR_ID = longVal("deleted_by_actor_id", "deleted_by_actor_id");

    private static final String WHERE_ACTIVE_BY_ENTITY =
            " WHERE " + ENTITY_TYPE.columnName() + " = :entityType" +
            " AND "   + ENTITY_ID.columnName()   + " = :entityId" +
            " AND "   + DELETED_AT.columnName()  + " IS NULL";

    private static final String SET_DELETED_NOW =
            " SET " + DELETED_AT.columnName()          + " = NOW()," +
            " "     + DELETED_BY_ACTOR_ID.columnName() + " = :actorId";

    public static final class Read {
        private Read() {}

        public static final SqlEntityProjection<Attachment> PROJECTION = SqlEntityProjection.of(
                List.of(ID, ENTITY_TYPE, ENTITY_ID, URL, FILENAME, CONTENT_TYPE, SIZE,
                        CREATED_AT, DELETED_AT, DELETED_BY_ACTOR_ID), TABLE,
                (rs, rowNum) -> {
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
                });

        public static final SqlCommand SELECT_ACTIVE_BY_ENTITY = SqlCommand.of(
                "SELECT * FROM " + TABLE + WHERE_ACTIVE_BY_ENTITY);

        public static final SqlCommand SELECT_ACTIVE_URLS = SqlCommand.of(
                "SELECT " + URL.columnName() + " FROM " + TABLE + WHERE_ACTIVE_BY_ENTITY);

        public static final SqlCommand SELECT_MAIN_MEDIA = SqlCommand.of(
                "SELECT " + URL.columnName() + ", " + CONTENT_TYPE.columnName() +
                " FROM " + TABLE + WHERE_ACTIVE_BY_ENTITY +
                " ORDER BY created_at ASC LIMIT 1");

        public static final SqlCommand COUNT_ACTIVE = SqlCommand.of(
                "SELECT COUNT(*) FROM " + TABLE + WHERE_ACTIVE_BY_ENTITY);

        public static final SqlCommand FIND_URLS_DELETED_OLDER_THAN = SqlCommand.of(
                "SELECT " + URL.columnName() + " FROM " + TABLE +
                " WHERE " + DELETED_AT.columnName() + " < NOW() - MAKE_INTERVAL(days => :days)" +
                " AND "   + CONTENT_TYPE.columnName() + " NOT IN ('video/youtube', 'video/embed')");

        public static MapSqlParameterSource entityParams(EntityType entityType, Long entityId) {
            return Params.with("entityType", entityType.name()).add("entityId", entityId);
        }

        public static MapSqlParameterSource findUrlsDeletedOlderThanParams(int days) {
            return Params.of("days", days);
        }
    }

    public static final class Write {
        private Write() {}

        public static final SqlCommand SOFT_DELETE = SqlCommand.of(
                "UPDATE " + TABLE + SET_DELETED_NOW +
                " WHERE " + ID.columnName() + " = :id");

        public static final SqlCommand SOFT_DELETE_ALL = SqlCommand.of(
                "UPDATE " + TABLE + SET_DELETED_NOW +
                " WHERE " + ENTITY_TYPE.columnName() + " = :entityType" +
                " AND "   + ENTITY_ID.columnName() + " = :entityId" +
                " AND "   + DELETED_AT.columnName() + " IS NULL");

        public static final SqlCommand RESTORE_UNDELETE = SqlCommand.of(
                "UPDATE " + TABLE +
                " SET "   + DELETED_AT.columnName() + " = NULL," +
                " "       + DELETED_BY_ACTOR_ID.columnName() + " = NULL" +
                " WHERE " + ENTITY_TYPE.columnName() + " = :entityType" +
                " AND "   + ENTITY_ID.columnName() + " = :entityId" +
                " AND "   + URL.columnName() + " = ANY(:urls)");

        public static final SqlCommand RESTORE_MARK_DELETED = SqlCommand.of(
                "UPDATE " + TABLE + SET_DELETED_NOW +
                " WHERE " + ENTITY_TYPE.columnName() + " = :entityType" +
                " AND "   + ENTITY_ID.columnName() + " = :entityId" +
                " AND "   + DELETED_AT.columnName() + " IS NULL" +
                " AND NOT (" + URL.columnName() + " = ANY(:urls))");

        public static final SqlCommand DELETE_BY_URLS = SqlCommand.of(
                "DELETE FROM " + TABLE + " WHERE " + URL.columnName() + " IN (:urls)");

        public static MapSqlParameterSource softDeleteParams(Long id, Long actorId) {
            return Params.with("id", id).add("actorId", actorId);
        }

        public static MapSqlParameterSource softDeleteAllParams(EntityType entityType, Long entityId, Long actorId) {
            return Read.entityParams(entityType, entityId).addValue("actorId", actorId);
        }

        public static MapSqlParameterSource restoreUndeleteParams(EntityType entityType, Long entityId, String[] urls) {
            return Read.entityParams(entityType, entityId).addValue("urls", urls);
        }

        public static MapSqlParameterSource restoreMarkDeletedParams(EntityType entityType, Long entityId,
                                                                     Long actorId, String[] urls) {
            return Read.entityParams(entityType, entityId)
                    .addValue("actorId", actorId)
                    .addValue("urls",    urls);
        }

        public static MapSqlParameterSource deleteByUrlsParams(List<String> urls) {
            return Params.of("urls", urls);
        }
    }

    private AttachmentDescriptor() {}
}
