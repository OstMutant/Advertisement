package org.ost.advertisement.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.dto.filter.AdvertisementFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdvertisementRepositoryCustomImpl implements AdvertisementRepositoryCustom {

	private final NamedParameterJdbcTemplate jdbc;

	@Override
	public List<AdvertisementView> findByFilter(AdvertisementFilter filter, Pageable pageable) {
		StringBuilder sql = new StringBuilder("""
			    SELECT
			        a.id AS id,
			        a.title AS title,
			        a.description AS description,
			        a.created_at AS createdAt,
			        a.updated_at AS updatedAt,
			        u.id AS userId,
			        u.name AS userName,
			        u.email AS userEmail
			    FROM advertisement a
			    LEFT JOIN user_information u ON a.user_id = u.id
			""");
		MapSqlParameterSource params = new MapSqlParameterSource();
		applyConditions(sql, params, filter);
		applySorting(sql, SORT_PROPERTY_MAP, pageable.getSort());
		applyPagination(sql, params, pageable);

		return jdbc.query(sql.toString(), params, (rs, rowNum) -> new AdvertisementView(
			rs.getLong("id"),
			rs.getString("title"),
			rs.getString("description"),
			rs.getTimestamp("createdAt").toInstant(),
			rs.getTimestamp("updatedAt").toInstant(),
			rs.getLong("userId"),
			rs.getString("userName"),
			rs.getString("userEmail")
		));
	}

	@Override
	public Long countByFilter(AdvertisementFilter filter) {
		StringBuilder sql = new StringBuilder("""
			    SELECT COUNT(*)
			    FROM advertisement a
			    LEFT JOIN user_information u ON a.user_id = u.id
			""");
		MapSqlParameterSource params = new MapSqlParameterSource();
		applyConditions(sql, params, filter);
		return jdbc.queryForObject(sql.toString(), params, Long.class);
	}

	private void applyConditions(StringBuilder sql, MapSqlParameterSource params, AdvertisementFilter filter) {
		sql.append("""
			    WHERE (:title::varchar IS NULL OR a.title ILIKE '%' || :title || '%')
			       AND (:createdAt_start::timestamp IS NULL OR a.created_at >= :createdAt_start)
			       AND (:createdAt_end::timestamp IS NULL OR a.created_at <= :createdAt_end)
			       AND (:updatedAt_start::timestamp IS NULL OR a.updated_at >= :updatedAt_start)
			       AND (:updatedAt_end::timestamp IS NULL OR a.updated_at <= :updatedAt_end)
			""");
		params.addValue("title", filter.getTitle())
			.addValue("createdAt_start", toTimestamp(filter.getCreatedAtStart()))
			.addValue("createdAt_end", toTimestamp(filter.getCreatedAtEnd()))
			.addValue("updatedAt_start", toTimestamp(filter.getUpdatedAtStart()))
			.addValue("updatedAt_end", toTimestamp(filter.getUpdatedAtEnd()));
	}

	private void applyPagination(StringBuilder sql, MapSqlParameterSource params, Pageable pageable) {
		sql.append(" LIMIT :limit OFFSET :offset ");
		params.addValue("limit", pageable.getPageSize())
			.addValue("offset", pageable.getOffset());
	}

	private void applySorting(StringBuilder sql, Map<String, String> sortPropertyMap, Sort sort) {
		if (sort.isSorted()) {
			sql.append(" ORDER BY ").append(
				sort.stream()
					.map(order -> {
							String column = sortPropertyMap.get(order.getProperty());
							if (column == null) {
								throw new IllegalArgumentException("Invalid sort property: " + order.getProperty());
							}
							return column + " " + order.getDirection().name();
						}
					)
					.collect(Collectors.joining(", "))
			);
		}
	}

	private static final Map<String, String> SORT_PROPERTY_MAP = Map.of(
		"id", "a.id",
		"title", "a.title",
		"createdAt", "a.created_at",
		"updatedAt", "a.updated_at",
		"userName", "u.name"
	);

	private static Timestamp toTimestamp(Instant instant) {
		return instant != null ? Timestamp.from(instant) : null;
	}
}

