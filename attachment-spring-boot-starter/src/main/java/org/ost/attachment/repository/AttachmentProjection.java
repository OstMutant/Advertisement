package org.ost.attachment.repository;

import org.jetbrains.annotations.NotNull;
import org.ost.attachment.entity.Attachment;
import org.ost.sqlengine.projection.SqlFieldDefinition;
import org.ost.sqlengine.projection.SqlProjection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.projection.SqlFieldBuilder.*;

public class AttachmentProjection extends SqlProjection<Attachment> {

    public static final String TABLE  = "attachment";
    public static final String SOURCE = TABLE;

    public static final SqlFieldDefinition<Long>    ID                  = id("id",                     "id");
    public static final SqlFieldDefinition<Long>    ENTITY_ID           = longVal("entity_id",          "entity_id");
    public static final SqlFieldDefinition<String>  URL                 = str("url",                    "url");
    public static final SqlFieldDefinition<String>  FILENAME            = str("filename",                "filename");
    public static final SqlFieldDefinition<String>  CONTENT_TYPE        = str("content_type",            "content_type");
    public static final SqlFieldDefinition<Long>    SIZE                = longVal("size",                "size");
    public static final SqlFieldDefinition<Instant> CREATED_AT          = instant("created_at",          "created_at");
    public static final SqlFieldDefinition<Instant> DELETED_AT          = instant("deleted_at",          "deleted_at");
    public static final SqlFieldDefinition<Long>    DELETED_BY_USER_ID  = longVal("deleted_by_user_id",  "deleted_by_user_id");

    public static final class Write {
        private Write() {}
        public static final String TABLE              = AttachmentProjection.TABLE;
        public static final String ENTITY_ID          = AttachmentProjection.ENTITY_ID.columnName();
        public static final String URL                = AttachmentProjection.URL.columnName();
        public static final String FILENAME           = AttachmentProjection.FILENAME.columnName();
        public static final String CONTENT_TYPE       = AttachmentProjection.CONTENT_TYPE.columnName();
        public static final String SIZE               = AttachmentProjection.SIZE.columnName();
        public static final String DELETED_AT         = AttachmentProjection.DELETED_AT.columnName();
        public static final String DELETED_BY_USER_ID = AttachmentProjection.DELETED_BY_USER_ID.columnName();
    }

    public AttachmentProjection() {
        super(List.of(ID, ENTITY_ID, URL, FILENAME, CONTENT_TYPE, SIZE,
                      CREATED_AT, DELETED_AT, DELETED_BY_USER_ID), SOURCE);
    }

    @Override
    public Attachment mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        return Attachment.builder()
                .id(ID.extract(rs))
                .entityId(ENTITY_ID.extract(rs))
                .url(URL.extract(rs))
                .filename(FILENAME.extract(rs))
                .contentType(CONTENT_TYPE.extract(rs))
                .size(SIZE.extract(rs))
                .createdAt(CREATED_AT.extract(rs))
                .deletedAt(DELETED_AT.extract(rs))
                .deletedByUserId(DELETED_BY_USER_ID.extract(rs))
                .build();
    }
}
