package org.ost.marketplace.repository.advertisement;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.dto.AdvertisementInfoDto;
import org.ost.marketplace.dto.filter.AdvertisementFilterDto;
import org.ost.marketplace.entities.Advertisement;
import org.ost.platform.attachment.dto.MediaSummaryDto;
import org.ost.sqlengine.filter.SqlBoundFilter;
import org.ost.sqlengine.filter.SqlFilterBuilder;
import org.ost.sqlengine.sort.OrderByBuilder;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.ost.marketplace.dto.filter.AdvertisementFilterDto.Fields.*;
import static org.ost.sqlengine.filter.SqlCondition.*;

@Repository
@RequiredArgsConstructor
public class AdvertisementRepository {

    private static final String TABLE        = "advertisement";
    private static final String ALIAS        = "a";
    private static final String SOURCE       = TABLE + " " + ALIAS + " LEFT JOIN user_information u ON " + ALIAS + ".created_by_user_id = u.id";
    private static final String COUNT_SOURCE = TABLE + " " + ALIAS;

    private static final String SELECT =
            "SELECT a.id, a.title, a.description, a.created_at, a.updated_at," +
            " u.id AS created_by_user_id, u.name AS created_by_user_name, u.email AS created_by_user_email," +
            " a.media_url, a.media_content_type, a.media_count" +
            " FROM " + SOURCE;
    private static final String COUNT  = "SELECT COUNT(*) FROM " + COUNT_SOURCE;

    private static final String SOFT_DELETE =
            "UPDATE " + TABLE + " SET deleted_at = NOW(), deleted_by_user_id = :deletedBy WHERE id = :id";
    private static final String DELETE_OLDER_THAN =
            "DELETE FROM " + TABLE + " WHERE deleted_at < NOW() - MAKE_INTERVAL(days => :days)";
    private static final String UPDATE_MEDIA =
            "UPDATE " + TABLE + " SET media_url = :media_url, media_content_type = :media_content_type, media_count = :media_count WHERE id = :id";
    private static final String SELECT_EXISTING_IDS =
            "SELECT id FROM " + TABLE + " WHERE id = ANY(:ids) AND deleted_at IS NULL";
    private static final String BY_ID_WHERE =
            ALIAS + ".id = :id AND " + ALIAS + ".deleted_at IS NULL";

    private static final Map<String, String> SORT = Map.ofEntries(
            Map.entry("id",                    ALIAS + ".id"),
            Map.entry("title",                 ALIAS + ".title"),
            Map.entry("description",           ALIAS + ".description"),
            Map.entry("created_at",            ALIAS + ".created_at"),
            Map.entry("updated_at",            ALIAS + ".updated_at"),
            Map.entry("media_url",             ALIAS + ".media_url"),
            Map.entry("media_content_type",    ALIAS + ".media_content_type"),
            Map.entry("media_count",           ALIAS + ".media_count"),
            Map.entry("created_by_user_id",    "u.id"),
            Map.entry("created_by_user_name",  "u.name"),
            Map.entry("created_by_user_email", "u.email"));

    private static final RowMapper<AdvertisementInfoDto> ROW_MAPPER = (rs, _) -> {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return AdvertisementInfoDto.builder()
                .id(rs.getObject("id", Long.class))
                .title(rs.getString("title"))
                .description(rs.getString("description"))
                .createdAt(createdAt != null ? createdAt.toInstant() : null)
                .updatedAt(updatedAt != null ? updatedAt.toInstant() : null)
                .createdByUserId(rs.getObject("created_by_user_id", Long.class))
                .createdByUserName(rs.getString("created_by_user_name"))
                .createdByUserEmail(rs.getString("created_by_user_email"))
                .mediaUrl(rs.getString("media_url"))
                .mediaContentType(rs.getString("media_content_type"))
                .mediaCount(rs.getObject("media_count", Integer.class))
                .build();
    };

    private static final SqlFilterBuilder<AdvertisementFilterDto> FILTER = new SqlFilterBuilder<>(List.of(
            SqlBoundFilter.of(title,          ALIAS + ".title",      (m, v) -> like(m, v.getTitle())),
            SqlBoundFilter.of(createdAtStart, ALIAS + ".created_at", (m, v) -> after(m, v.getCreatedAtStart())),
            SqlBoundFilter.of(createdAtEnd,   ALIAS + ".created_at", (m, v) -> before(m, v.getCreatedAtEnd())),
            SqlBoundFilter.of(updatedAtStart, ALIAS + ".updated_at", (m, v) -> after(m, v.getUpdatedAtStart())),
            SqlBoundFilter.of(updatedAtEnd,   ALIAS + ".updated_at", (m, v) -> before(m, v.getUpdatedAtEnd()))
    )) {
        @Override
        public String build(MapSqlParameterSource params, AdvertisementFilterDto filter) {
            String dynamic = super.build(params, filter);
            String base    = ALIAS + ".deleted_at IS NULL";
            return dynamic.isEmpty() ? base : base + " AND " + dynamic;
        }
    };

    private final JdbcClient jdbcClient;
    private final AdvertisementCrudRepository crud;

    public Advertisement save(Advertisement ad)    { return crud.save(ad); }
    public Optional<Advertisement> findById(Long id) { return crud.findById(id); }

    public Optional<AdvertisementInfoDto> findAdvertisementById(Long id) {
        String sql = SELECT + " WHERE " + BY_ID_WHERE;
        return jdbcClient.sql(sql)
                .paramSource(new MapSqlParameterSource("id", id))
                .query(ROW_MAPPER).optional();
    }

    public List<AdvertisementInfoDto> findByFilter(AdvertisementFilterDto filter, Pageable pageable) {
        var params = new MapSqlParameterSource();
        String where   = FILTER.build(params, filter);
        String orderBy = OrderByBuilder.build(pageable.getSort(), SORT);
        String sql = SELECT + " WHERE " + where
                + (orderBy.isBlank() ? "" : " " + orderBy)
                + pageLimit(params, pageable);
        return jdbcClient.sql(sql).paramSource(params).query(ROW_MAPPER).list();
    }

    public Long countByFilter(AdvertisementFilterDto filter) {
        var params = new MapSqlParameterSource();
        String where = FILTER.build(params, filter);
        String sql = COUNT + " WHERE " + where;
        return jdbcClient.sql(sql).paramSource(params).query(Long.class).single();
    }

    public void softDelete(Long id, Long deletedByUserId) {
        jdbcClient.sql(SOFT_DELETE)
                  .paramSource(new MapSqlParameterSource().addValue("id", id).addValue("deletedBy", deletedByUserId))
                  .update();
    }

    public void deleteOlderThan(int days) {
        jdbcClient.sql(DELETE_OLDER_THAN).paramSource(new MapSqlParameterSource("days", days)).update();
    }

    public List<Long> findExistingIds(Long[] ids) {
        return jdbcClient.sql(SELECT_EXISTING_IDS)
                .paramSource(new MapSqlParameterSource("ids", ids))
                .query(Long.class)
                .list();
    }

    public void updateMedia(Long entityId, MediaSummaryDto summary) {
        jdbcClient.sql(UPDATE_MEDIA)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("media_url",          summary.displayUrl())
                          .addValue("media_content_type", summary.contentType())
                          .addValue("media_count",        summary.count())
                          .addValue("id",                 entityId))
                  .update();
    }

    private static String pageLimit(MapSqlParameterSource params, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) return "";
        params.addValue("limit",  pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());
        return " LIMIT :limit OFFSET :offset";
    }
}
