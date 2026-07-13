package org.ost.advertisement.repository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entity.Advertisement;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.attachment.dto.AttachmentMediaSummaryDto;
import org.ost.query.filter.SqlBoundFilter;
import org.ost.query.filter.SqlFilterBuilder;
import org.ost.query.sort.OrderByBuilder;
import org.ost.query.sort.PaginationSqlBuilder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.ost.platform.advertisement.dto.AdvertisementFilterDto.Fields.*;
import static org.ost.query.filter.SqlCondition.*;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class AdvertisementRepository {

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
                .version(rs.getObject("version", Long.class))
                .build();
    };

    private static final SqlFilterBuilder<AdvertisementFilterDto> FILTER = new SqlFilterBuilder<>(List.of(
            SqlBoundFilter.of(title,          "a.title",      (m, v) -> like(m, v.getTitle())),
            SqlBoundFilter.of(createdAtStart, "a.created_at", (m, v) -> after(m, v.getCreatedAtStart())),
            SqlBoundFilter.of(createdAtEnd,   "a.created_at", (m, v) -> before(m, v.getCreatedAtEnd())),
            SqlBoundFilter.of(updatedAtStart, "a.updated_at", (m, v) -> after(m, v.getUpdatedAtStart())),
            SqlBoundFilter.of(updatedAtEnd,   "a.updated_at", (m, v) -> before(m, v.getUpdatedAtEnd()))
    ));

    private final JdbcClient jdbcClient;
    private final AdvertisementCrudRepository crud;

    public Advertisement save(@NonNull Advertisement ad)      { return crud.save(ad); }
    public Optional<Advertisement> findById(@NonNull Long id) { return crud.findById(id); }

    public Optional<AdvertisementInfoDto> findAdvertisementById(@NonNull Long id) {
        return jdbcClient.sql("""
                        SELECT a.id, a.title, a.description, a.created_at, a.updated_at,
                               u.id AS created_by_user_id, u.name AS created_by_user_name, u.email AS created_by_user_email,
                               a.media_url, a.media_content_type, a.media_count, a.version
                        FROM advertisement a LEFT JOIN user_information u ON a.created_by_user_id = u.id
                        WHERE a.id = :id AND a.deleted_at IS NULL
                        """)
                .paramSource(new MapSqlParameterSource("id", id))
                .query(ROW_MAPPER).optional();
    }

    public List<AdvertisementInfoDto> findByFilter(@NonNull AdvertisementFilterDto filter, @NonNull Pageable pageable,
                                                    Set<Long> allowedIds) {
        var params = new MapSqlParameterSource();
        String orderBy = OrderByBuilder.build(pageable.getSort(), Map.ofEntries(
                Map.entry("id",                    "a.id"),
                Map.entry("title",                 "a.title"),
                Map.entry("description",           "a.description"),
                Map.entry("created_at",            "a.created_at"),
                Map.entry("updated_at",            "a.updated_at"),
                Map.entry("media_url",             "a.media_url"),
                Map.entry("media_content_type",    "a.media_content_type"),
                Map.entry("media_count",           "a.media_count"),
                Map.entry("created_by_user_id",    "u.id"),
                Map.entry("created_by_user_name",  "u.name"),
                Map.entry("created_by_user_email", "u.email")));
        String sql = ("""
                        SELECT a.id, a.title, a.description, a.created_at, a.updated_at,
                               u.id AS created_by_user_id, u.name AS created_by_user_name, u.email AS created_by_user_email,
                               a.media_url, a.media_content_type, a.media_count, a.version
                        FROM advertisement a LEFT JOIN user_information u ON a.created_by_user_id = u.id
                        WHERE a.deleted_at IS NULL%s%s%s%s""")
                .formatted(buildIdClause(params, allowedIds), FILTER.build(params, filter, " AND "), orderBy, PaginationSqlBuilder.pageLimit(params, pageable));
        return jdbcClient.sql(sql).paramSource(params).query(ROW_MAPPER).list();
    }

    public Long countByFilter(@NonNull AdvertisementFilterDto filter, Set<Long> allowedIds) {
        var params = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) FROM advertisement a WHERE a.deleted_at IS NULL%s%s"
                .formatted(buildIdClause(params, allowedIds), FILTER.build(params, filter, " AND "));
        return jdbcClient.sql(sql).paramSource(params).query(Long.class).single();
    }

    private static String buildIdClause(MapSqlParameterSource params, Set<Long> ids) {
        if (ids == null) return "";
        params.addValue("allowedIds", ids);
        return " AND a.id IN (:allowedIds)";
    }

    public void softDelete(@NonNull Long id, @NonNull Long deletedByUserId, Long version) {
        int updated = jdbcClient.sql("""
                        UPDATE advertisement SET deleted_at = NOW(), deleted_by_user_id = :deletedBy, version = version + 1
                        WHERE id = :id AND version = :version
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("id",        id)
                          .addValue("deletedBy", deletedByUserId)
                          .addValue("version",   version))
                  .update();
        if (updated == 0) {
            throw new OptimisticLockingFailureException("Advertisement " + id + " was modified by another session");
        }
    }

    public int deleteOlderThan(int days) {
        return jdbcClient.sql("DELETE FROM advertisement WHERE deleted_at < NOW() - MAKE_INTERVAL(days => :days)")
                  .paramSource(new MapSqlParameterSource("days", days)).update();
    }

    public List<Long> findExistingIds(@NonNull Long[] ids) {
        return jdbcClient.sql("SELECT id FROM advertisement WHERE id = ANY(:ids) AND deleted_at IS NULL")
                .paramSource(new MapSqlParameterSource("ids", ids))
                .query(Long.class)
                .list();
    }

    public void updateMedia(@NonNull Long entityId, @NonNull AttachmentMediaSummaryDto summary) {
        jdbcClient.sql("UPDATE advertisement SET media_url = :media_url, media_content_type = :media_content_type, media_count = :media_count WHERE id = :id")
                  .paramSource(new MapSqlParameterSource()
                          .addValue("media_url",          summary.displayUrl())
                          .addValue("media_content_type", summary.contentType())
                          .addValue("media_count",        summary.count())
                          .addValue("id",                 entityId))
                  .update();
    }
}
