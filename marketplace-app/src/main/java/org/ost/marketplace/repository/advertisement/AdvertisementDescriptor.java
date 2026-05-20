package org.ost.marketplace.repository.advertisement;

import org.ost.marketplace.dto.AdvertisementInfoDto;
import org.ost.marketplace.dto.filter.AdvertisementFilterDto;
import org.ost.platform.attachment.dto.MediaSummaryDto;
import org.ost.sqlengine.SqlEntityDescriptor;
import static org.ost.sqlengine.SqlEntityDescriptor.Params;
import org.ost.sqlengine.filter.SqlFilterBuilder;
import org.ost.sqlengine.read.SqlEntityProjection;
import org.ost.sqlengine.common.SqlDescriptorField;
import org.ost.sqlengine.common.SqlCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.Instant;
import java.util.List;

import static org.ost.marketplace.dto.AdvertisementInfoDto.Fields.*;
import static org.ost.sqlengine.filter.SqlBoundFilter.of;
import static org.ost.sqlengine.filter.SqlCondition.after;
import static org.ost.sqlengine.filter.SqlCondition.before;
import static org.ost.sqlengine.filter.SqlCondition.like;
import static org.ost.sqlengine.common.SqlDescriptorFieldFactory.*;

public final class AdvertisementDescriptor implements SqlEntityDescriptor {

    public static final String TABLE        = "advertisement";
    public static final String ALIAS        = "a";
    public static final String SOURCE       = TABLE + " " + ALIAS +
            " LEFT JOIN user_information u ON " + ALIAS + ".created_by_user_id = u.id";
    public static final String COUNT_SOURCE = TABLE + " " + ALIAS;

    public static final SqlDescriptorField<Long>    ID                 = longVal(ALIAS + ".id",                id);
    public static final SqlDescriptorField<String>  TITLE              = str(ALIAS + ".title",             title);
    public static final SqlDescriptorField<String>  DESCRIPTION        = str(ALIAS + ".description",       description);
    public static final SqlDescriptorField<Instant> CREATED_AT         = instant(ALIAS + ".created_at",    createdAt);
    public static final SqlDescriptorField<Instant> UPDATED_AT         = instant(ALIAS + ".updated_at",    updatedAt);
    public static final SqlDescriptorField<Instant> DELETED_AT         = instant(ALIAS + ".deleted_at",    "deleted_at");
    public static final SqlDescriptorField<String>  MEDIA_URL          = str(ALIAS + ".media_url",          mediaUrl);
    public static final SqlDescriptorField<String>  MEDIA_CONTENT_TYPE = str(ALIAS + ".media_content_type", mediaContentType);
    public static final SqlDescriptorField<Integer> MEDIA_COUNT        = intVal(ALIAS + ".media_count",      mediaCount);
    public static final SqlDescriptorField<Long>    USER_ID            = longVal("u.id",                   createdByUserId);
    public static final SqlDescriptorField<String>  USER_NAME          = str("u.name",                     createdByUserName);
    public static final SqlDescriptorField<String>  USER_EMAIL         = str("u.email",                    createdByUserEmail);

    public static final class Read {
        private Read() {}

        public static final SqlEntityProjection<AdvertisementInfoDto> PROJECTION = SqlEntityProjection.of(
                List.of(ID, TITLE, DESCRIPTION, CREATED_AT, UPDATED_AT,
                        USER_ID, USER_NAME, USER_EMAIL, MEDIA_URL, MEDIA_CONTENT_TYPE, MEDIA_COUNT),
                SOURCE, COUNT_SOURCE,
                (rs, rowNum) -> AdvertisementInfoDto.builder()
                        .id(ID.extract(rs))
                        .title(TITLE.extract(rs))
                        .description(DESCRIPTION.extract(rs))
                        .createdAt(CREATED_AT.extract(rs))
                        .updatedAt(UPDATED_AT.extract(rs))
                        .createdByUserId(USER_ID.extract(rs))
                        .createdByUserName(USER_NAME.extract(rs))
                        .createdByUserEmail(USER_EMAIL.extract(rs))
                        .mediaUrl(MEDIA_URL.extract(rs))
                        .mediaContentType(MEDIA_CONTENT_TYPE.extract(rs))
                        .mediaCount(MEDIA_COUNT.extract(rs))
                        .build());

        public static final SqlFilterBuilder<AdvertisementFilterDto> FILTER = new SqlFilterBuilder<>(List.of(
                of(AdvertisementFilterDto.Fields.title,           TITLE,      (m, v) -> like(m, v.getTitle())),
                of(AdvertisementFilterDto.Fields.createdAtStart,  CREATED_AT, (m, v) -> after(m, v.getCreatedAtStart())),
                of(AdvertisementFilterDto.Fields.createdAtEnd,    CREATED_AT, (m, v) -> before(m, v.getCreatedAtEnd())),
                of(AdvertisementFilterDto.Fields.updatedAtStart,  UPDATED_AT, (m, v) -> after(m, v.getUpdatedAtStart())),
                of(AdvertisementFilterDto.Fields.updatedAtEnd,    UPDATED_AT, (m, v) -> before(m, v.getUpdatedAtEnd()))
        )) {
            @Override
            public String build(MapSqlParameterSource params, AdvertisementFilterDto filter) {
                String dynamic = super.build(params, filter);
                String base    = DELETED_AT.sqlExpression() + " IS NULL";
                return dynamic.isEmpty() ? base : base + " AND " + dynamic;
            }
        };

        public static final SqlCommand SELECT_EXISTING_IDS = SqlCommand.of(
                "SELECT " + ID.columnName() + " FROM " + TABLE +
                " WHERE " + ID.columnName() + " = ANY(:ids)" +
                " AND "   + DELETED_AT.columnName() + " IS NULL");

        public static final String BY_ID_ACTIVE_WHERE =
                ALIAS + ".id = :id AND " + DELETED_AT.sqlExpression() + " IS NULL";

        public static MapSqlParameterSource byIdParams(Long id) {
            return Params.of("id", id);
        }

        public static MapSqlParameterSource existingIdsParams(Long[] ids) {
            return Params.of("ids", ids);
        }
    }

    public static final class Write {
        private Write() {}
        public static final String TABLE              = AdvertisementDescriptor.TABLE;
        public static final String DELETED_AT         = AdvertisementDescriptor.DELETED_AT.columnName();
        public static final String DELETED_BY_USER_ID = "deleted_by_user_id";
        public static final String MEDIA_URL          = AdvertisementDescriptor.MEDIA_URL.columnName();
        public static final String MEDIA_CONTENT_TYPE = AdvertisementDescriptor.MEDIA_CONTENT_TYPE.columnName();
        public static final String MEDIA_COUNT        = AdvertisementDescriptor.MEDIA_COUNT.columnName();

        public static final SqlCommand SOFT_DELETE = SqlCommand.of(
                "UPDATE " + TABLE +
                " SET "   + DELETED_AT + " = NOW(), " +
                " "       + DELETED_BY_USER_ID + " = :deletedBy" +
                " WHERE id = :id");

        public static final SqlCommand DELETE_OLDER_THAN = SqlCommand.of(
                "DELETE FROM " + TABLE +
                " WHERE " + DELETED_AT + " < NOW() - MAKE_INTERVAL(days => :days)");

        public static final SqlCommand UPDATE_MEDIA = SqlCommand.of(
                "UPDATE " + TABLE +
                " SET " + MEDIA_URL          + " = :url," +
                " "     + MEDIA_CONTENT_TYPE + " = :contentType," +
                " "     + MEDIA_COUNT        + " = :count" +
                " WHERE id = :id");

        public static MapSqlParameterSource softDeleteParams(Long id, Long deletedByUserId) {
            return Params.with("id", id).add("deletedBy", deletedByUserId);
        }

        public static MapSqlParameterSource deleteOlderThanParams(int days) {
            return Params.of("days", days);
        }

        public static MapSqlParameterSource updateMediaParams(Long entityId, MediaSummaryDto summary) {
            return Params.with("url",         summary.displayUrl())
                            .add("contentType", summary.contentType())
                            .add("count",       summary.count())
                            .add("id",          entityId);
        }
    }

    private AdvertisementDescriptor() {}
}
