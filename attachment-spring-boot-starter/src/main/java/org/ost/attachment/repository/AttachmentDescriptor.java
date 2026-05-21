package org.ost.attachment.repository;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.attachment.entities.Attachment;
import org.ost.platform.core.model.EntityType;
import org.ost.sqlengine.SqlEntityDescriptor;
import org.ost.sqlengine.common.SqlCommand;
import org.ost.sqlengine.common.SqlDescriptorField;
import org.ost.sqlengine.read.SqlEntityProjection;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.common.SqlCommand.sql;
import static org.ost.sqlengine.common.SqlDescriptorFieldFactory.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AttachmentDescriptor implements SqlEntityDescriptor {

    public static final String TABLE  = "attachment";

    public static final SqlDescriptorField<Long>    ID                  = longCol("id");
    public static final SqlDescriptorField<String>  ENTITY_TYPE         = strCol("entity_type");
    public static final SqlDescriptorField<Long>    ENTITY_ID           = longCol("entity_id");
    public static final SqlDescriptorField<String>  URL                 = strCol("url");
    public static final SqlDescriptorField<String>  FILENAME            = strCol("filename");
    public static final SqlDescriptorField<String>  CONTENT_TYPE        = strCol("content_type");
    public static final SqlDescriptorField<Long>    SIZE                = longCol("size");
    public static final SqlDescriptorField<Instant> CREATED_AT          = instantCol("created_at");
    public static final SqlDescriptorField<Instant> DELETED_AT          = instantCol("deleted_at");
    public static final SqlDescriptorField<Long>    DELETED_BY_ACTOR_ID = longCol("deleted_by_actor_id");

    private static final String WHERE_ACTIVE_BY_ENTITY = sql(
            " WHERE {entityType} = :entityType AND {entityId} = :entityId AND {deletedAt} IS NULL",
            "entityType", ENTITY_TYPE.columnName(),
            "entityId",   ENTITY_ID.columnName(),
            "deletedAt",  DELETED_AT.columnName());

    private static final String SET_DELETED_NOW = sql(
            " SET {deletedAt} = NOW(), {deletedByActorId} = :actorId",
            "deletedAt",        DELETED_AT.columnName(),
            "deletedByActorId", DELETED_BY_ACTOR_ID.columnName());

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Read {

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
                "SELECT * FROM {table}{where}",
                "table", TABLE,
                "where", WHERE_ACTIVE_BY_ENTITY);

        public static final SqlCommand SELECT_ACTIVE_URLS = SqlCommand.of(
                "SELECT {url} FROM {table}{where}",
                "url",   URL.columnName(),
                "table", TABLE,
                "where", WHERE_ACTIVE_BY_ENTITY);

        public static final SqlCommand SELECT_MAIN_MEDIA = SqlCommand.of(
                "SELECT {url}, {contentType} FROM {table}{where} ORDER BY created_at ASC LIMIT 1",
                "url",         URL.columnName(),
                "contentType", CONTENT_TYPE.columnName(),
                "table",       TABLE,
                "where",       WHERE_ACTIVE_BY_ENTITY);

        public static final SqlCommand COUNT_ACTIVE = SqlCommand.of(
                "SELECT COUNT(*) FROM {table}{where}",
                "table", TABLE,
                "where", WHERE_ACTIVE_BY_ENTITY);

        public static final SqlCommand FIND_URLS_DELETED_OLDER_THAN = SqlCommand.of(
                "SELECT {url} FROM {table} WHERE {deletedAt} < NOW() - MAKE_INTERVAL(days => :days)" +
                " AND {contentType} NOT IN ('video/youtube', 'video/embed')",
                "url",         URL.columnName(),
                "table",       TABLE,
                "deletedAt",   DELETED_AT.columnName(),
                "contentType", CONTENT_TYPE.columnName());

        public static MapSqlParameterSource entityParams(EntityType entityType, Long entityId) {
            return Params.with("entityType", entityType.name()).add("entityId", entityId);
        }

        public static MapSqlParameterSource findUrlsDeletedOlderThanParams(int days) {
            return Params.of("days", days);
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Write {

        public static final SqlCommand SOFT_DELETE = SqlCommand.of(
                "UPDATE {table}{set} WHERE {id} = :id",
                "table", TABLE,
                "set",   SET_DELETED_NOW,
                "id",    ID.columnName());

        public static final SqlCommand SOFT_DELETE_ALL = SqlCommand.of(
                "UPDATE {table}{set}{where}",
                "table", TABLE,
                "set",   SET_DELETED_NOW,
                "where", WHERE_ACTIVE_BY_ENTITY);

        public static final SqlCommand RESTORE_UNDELETE = SqlCommand.of(
                "UPDATE {table} SET {deletedAt} = NULL, {deletedByActorId} = NULL" +
                " WHERE {entityType} = :entityType AND {entityId} = :entityId AND {url} = ANY(:urls)",
                "table",            TABLE,
                "deletedAt",        DELETED_AT.columnName(),
                "deletedByActorId", DELETED_BY_ACTOR_ID.columnName(),
                "entityType",       ENTITY_TYPE.columnName(),
                "entityId",         ENTITY_ID.columnName(),
                "url",              URL.columnName());

        public static final SqlCommand RESTORE_MARK_DELETED = SqlCommand.of(
                "UPDATE {table}{set} WHERE {entityType} = :entityType AND {entityId} = :entityId" +
                " AND {deletedAt} IS NULL AND NOT ({url} = ANY(:urls))",
                "table",      TABLE,
                "set",        SET_DELETED_NOW,
                "entityType", ENTITY_TYPE.columnName(),
                "entityId",   ENTITY_ID.columnName(),
                "deletedAt",  DELETED_AT.columnName(),
                "url",        URL.columnName());

        public static final SqlCommand DELETE_BY_URLS = SqlCommand.of(
                "DELETE FROM {table} WHERE {url} IN (:urls)",
                "table", TABLE,
                "url",   URL.columnName());

        public static MapSqlParameterSource softDeleteParams(Long id, Long actorId) {
            return Params.with(ID.columnName(), id).add("actorId", actorId);
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

}
