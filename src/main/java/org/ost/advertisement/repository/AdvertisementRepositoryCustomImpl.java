package org.ost.advertisement.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.dto.filter.AdvertisementFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdvertisementRepositoryCustomImpl implements AdvertisementRepositoryCustom {

	private final NamedParameterJdbcTemplate jdbc;

	@Override
	public List<AdvertisementView> findByFilter(AdvertisementFilter filter, Pageable pageable) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		StringBuilder sql = prepareSelectTemplate(applyFields(FIELD_MAP), applyConditions(params, filter),
			applySorting(FIELD_MAP, pageable.getSort()), applyPagination(params, pageable));

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
		MapSqlParameterSource params = new MapSqlParameterSource();
		StringBuilder sql = prepareSelectTemplate("COUNT(*)", applyConditions(params, filter), null, null);
		return jdbc.queryForObject(sql.toString(), params, Long.class);
	}

	private StringBuilder prepareSelectTemplate(String fields, String conditions, String sorting, String pagination) {
		return prepareSelectTemplate("""
			    advertisement a
			    LEFT JOIN user_information u ON a.user_id = u.id
			""", fields, conditions, sorting, pagination);
	}

	private StringBuilder prepareSelectTemplate(String source, String fields, String conditions, String sorting,
												String pagination) {
		return new StringBuilder()
			.append("SELECT ")
			.append(fields == null || fields.isBlank() ? "*" : fields)
			.append(" FROM ")
			.append(source)
			.append(conditions == null || conditions.isBlank() ? "" : " WHERE " + conditions)
			.append(sorting == null || sorting.isBlank() ? "" : sorting)
			.append(pagination == null || pagination.isBlank() ? "" : pagination);
	}

	private String applyConditions(MapSqlParameterSource params, AdvertisementFilter filter) {
		String title = filter.getTitle();
		Timestamp createdAtStart = toTimestamp(filter.getCreatedAtStart());
		Timestamp createdAtEnd = toTimestamp(filter.getCreatedAtEnd());
		Timestamp updatedAtStart = toTimestamp(filter.getUpdatedAtStart());
		Timestamp updatedAtEnd = toTimestamp(filter.getUpdatedAtEnd());
		List<String> sqlConditions = new ArrayList<>();
		if (title != null && !title.isBlank()) {
			params.addValue("title", filter.getTitle());
			sqlConditions.add("a.title ILIKE '%' || :title || '%'");
		}
		if (createdAtStart != null) {
			params.addValue("createdAt_start", createdAtStart);
			sqlConditions.add("a.created_at >= :createdAt_start");
		}
		if (createdAtEnd != null) {
			params.addValue("createdAt_end", createdAtEnd);
			sqlConditions.add("a.created_at <= :createdAt_end");
		}
		if (updatedAtStart != null) {
			params.addValue("updatedAt_start", updatedAtStart);
			sqlConditions.add("a.updated_at >= :updatedAt_start");
		}
		if (updatedAtEnd != null) {
			params.addValue("updatedAt_end", updatedAtEnd);
			sqlConditions.add("a.updated_at <= :updatedAt_end");
		}
		return String.join(" AND ", sqlConditions);
	}

	private String applyPagination(MapSqlParameterSource params, Pageable pageable) {
		String sql = " LIMIT :limit OFFSET :offset ";
		params.addValue("limit", pageable.getPageSize())
			.addValue("offset", pageable.getOffset());
		return sql;
	}

	private String applySorting(Map<String, String> fieldMap, Sort sort) {
		StringBuilder sql = new StringBuilder();
		if (sort.isSorted()) {
			sql.append(" ORDER BY ")
				.append(
					sort.stream()
						.map(order -> applyOrdering(fieldMap, order))
						.filter(Objects::nonNull)
						.collect(Collectors.joining(", "))
				);
		}
		return sql.toString();
	}

	private String applyOrdering(Map<String, String> sortPropertyMap, Order order) {
		String column = sortPropertyMap.get(order.getProperty());
		return column == null ? null : column + " " + order.getDirection().name();
	}

	private static final Map<String, String> FIELD_MAP = Map.of(
		"id", "a.id",
		"title", "a.title",
		"description", "a.description",
		"createdAt", "a.created_at",
		"updatedAt", "a.updated_at",
		"userId", "u.id",
		"userName", "u.name",
		"userEmail", "u.email"
	);


	enum FieldRelations {
		ID("a.id", "id"),
		TITLE("a.title", "title"),
		DESCRIPTION("a.description", "description"),
		CREATED_AT("a.created_at", "createdAt"),
		UPDATED_AT("a.updated_at", "updatedAt"),
		USER_ID("u.id", "userId"),
		USER_NAME("u.name", "userName"),
		USER_EMAIL("u.email", "userEmail");
		@Getter
		String fieldPath;
		@Getter
		String alias;

		FieldRelations(String fieldPath, String alias) {
			this.fieldPath = fieldPath;
			this.alias = alias;
		}
	}


	private static String applyFields(Map<String, String> fieldMap) {
		return fieldMap.entrySet().stream().map(v -> v.getValue() + " AS " + v.getKey())
			.collect(Collectors.joining(", "));
	}

	private static Timestamp toTimestamp(Instant instant) {
		return instant != null ? Timestamp.from(instant) : null;
	}
}

