package org.ost.advertisement.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class RepositoryCustom {

	protected final NamedParameterJdbcTemplate jdbc;

	protected RepositoryCustom(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public static Timestamp toTimestamp(Instant instant) {
		return instant != null ? Timestamp.from(instant) : null;
	}

	public static Instant toInstant(Timestamp ts) {
		return ts != null ? ts.toInstant() : null;
	}

	public <T> List<T> findByFilter(String source, Map<String, String> fieldMap,
									Function<MapSqlParameterSource, String> conditions,
									Pageable pageable, RowMapper<T> rowMapper) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		StringBuilder sql = prepareSelectTemplate(source, applyFields(fieldMap), conditions.apply(params),
			applySorting(fieldMap, pageable.getSort()), applyPagination(params, pageable));
		return jdbc.query(sql.toString(), params, rowMapper);
	}

	protected Long countByFilter(String source, Function<MapSqlParameterSource, String> conditions) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		StringBuilder sql = prepareSelectTemplate(source, "COUNT(*)", conditions.apply(params), null, null);
		return jdbc.queryForObject(sql.toString(), params, Long.class);
	}

	protected StringBuilder prepareSelectTemplate(String source, String fields, String conditions, String sorting,
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

	protected String applyPagination(MapSqlParameterSource params, Pageable pageable) {
		String sql = " LIMIT :limit OFFSET :offset ";
		params.addValue("limit", pageable.getPageSize())
			.addValue("offset", pageable.getOffset());
		return sql;
	}

	protected String applyFields(Map<String, String> fieldMap) {
		return fieldMap.entrySet().stream().map(v -> v.getValue() + " AS " + v.getKey())
			.collect(Collectors.joining(", "));
	}

	protected String applySorting(Map<String, String> fieldMap, Sort sort) {
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

	private String applyOrdering(Map<String, String> fieldMap, Order order) {
		String column = fieldMap.get(order.getProperty());
		return column == null ? null : column + " " + order.getDirection().name();
	}

}
