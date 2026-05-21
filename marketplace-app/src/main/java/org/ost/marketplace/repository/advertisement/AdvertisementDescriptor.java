package org.ost.marketplace.repository.advertisement;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.marketplace.dto.AdvertisementInfoDto;
import org.ost.marketplace.dto.filter.AdvertisementFilterDto;
import org.ost.platform.attachment.dto.MediaSummaryDto;
import org.ost.sqlengine.SqlEntityDescriptor;
import org.ost.sqlengine.common.SqlCommand;
import org.ost.sqlengine.common.SqlDescriptorField;
import org.ost.sqlengine.filter.SqlFilterBuilder;
import org.ost.sqlengine.read.SqlEntityProjection;
import org.ost.sqlengine.write.SqlEntityWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import static org.ost.sqlengine.write.SqlWriteFieldFactory.field;

import java.time.Instant;
import java.util.List;

import static org.ost.marketplace.dto.AdvertisementInfoDto.Fields.*;
import static org.ost.sqlengine.common.SqlCommand.sql;
import static org.ost.sqlengine.common.SqlDescriptorFieldFactory.*;
import static org.ost.sqlengine.filter.SqlBoundFilter.of;
import static org.ost.sqlengine.filter.SqlCondition.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdvertisementDescriptor implements SqlEntityDescriptor {

    public static final String TABLE        = "advertisement";
    public static final String ALIAS        = "a";
    public static final String SOURCE = sql(
            "{table} {alias} LEFT JOIN user_information u ON {alias}.created_by_user_id = u.id",
            "table", TABLE,
            "alias", ALIAS);
    public static final String COUNT_SOURCE = sql(
            "{table} {alias}",
            "table", TABLE,
            "alias", ALIAS);

    public static final SqlDescriptorField<Long>    ID                 = longCol(ALIAS, id);
    public static final SqlDescriptorField<String>  TITLE              = strCol(ALIAS, title);
    public static final SqlDescriptorField<String>  DESCRIPTION        = strCol(ALIAS, description);
    public static final SqlDescriptorField<Instant> CREATED_AT         = instantCol(ALIAS, createdAt);
    public static final SqlDescriptorField<Instant> UPDATED_AT         = instantCol(ALIAS, updatedAt);
    public static final SqlDescriptorField<Instant> DELETED_AT         = instantCol(ALIAS, "deleted_at");
    public static final SqlDescriptorField<String>  MEDIA_URL          = strCol(ALIAS, mediaUrl);
    public static final SqlDescriptorField<String>  MEDIA_CONTENT_TYPE = strCol(ALIAS, mediaContentType);
    public static final SqlDescriptorField<Integer> MEDIA_COUNT        = intCol(ALIAS, mediaCount);
    public static final SqlDescriptorField<Long>    USER_ID            = longVal("u.id",                   createdByUserId);
    public static final SqlDescriptorField<String>  USER_NAME          = str("u.name",                     createdByUserName);
    public static final SqlDescriptorField<String>  USER_EMAIL         = str("u.email",                    createdByUserEmail);

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Read {

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
                "SELECT {id} FROM {table} WHERE {id} = ANY(:ids) AND {deletedAt} IS NULL",
                "id",        ID.columnName(),
                "table",     TABLE,
                "deletedAt", DELETED_AT.columnName());

        public static final String BY_ID_ACTIVE_WHERE = sql(
                "{id} = :id AND {deletedAt} IS NULL",
                "id",        ID.sqlExpression(),
                "deletedAt", DELETED_AT.sqlExpression());

        public static MapSqlParameterSource byIdParams(Long id) {
            return Params.of(ID.columnName(), id);
        }

        public static MapSqlParameterSource existingIdsParams(Long[] ids) {
            return Params.of("ids", ids);
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Write {

        public static final SqlCommand SOFT_DELETE = SqlCommand.of(
                "UPDATE {table} SET {deletedAt} = NOW(), {deletedByUserId} = :deletedBy WHERE id = :id",
                "table",           TABLE,
                "deletedAt",       DELETED_AT.columnName(),
                "deletedByUserId", "deleted_by_user_id");

        public static final SqlCommand DELETE_OLDER_THAN = SqlCommand.of(
                "DELETE FROM {table} WHERE {deletedAt} < NOW() - MAKE_INTERVAL(days => :days)",
                "table",     TABLE,
                "deletedAt", DELETED_AT.columnName());

        public static final SqlEntityWriter<MediaSummaryDto> MEDIA_WRITER = SqlEntityWriter.of(
                TABLE,
                field(MEDIA_URL,          MediaSummaryDto::displayUrl),
                field(MEDIA_CONTENT_TYPE, MediaSummaryDto::contentType),
                field(MEDIA_COUNT,        MediaSummaryDto::count));

        public static final SqlCommand UPDATE_MEDIA = SqlCommand.of(MEDIA_WRITER.updateWhere("id = :id"));

        public static MapSqlParameterSource softDeleteParams(Long id, Long deletedByUserId) {
            return Params.with(ID.columnName(), id).add("deletedBy", deletedByUserId);
        }

        public static MapSqlParameterSource deleteOlderThanParams(int days) {
            return Params.of("days", days);
        }

        public static MapSqlParameterSource updateMediaParams(Long entityId, MediaSummaryDto summary) {
            return MEDIA_WRITER.params(summary).addValue(ID.columnName(), entityId);
        }
    }

}
